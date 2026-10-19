package com.example.data

import com.example.data.local.CredentialDao
import com.example.data.local.CredentialEntity
import com.example.model.Credential
import com.example.model.CredentialCategory
import com.example.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VaultRepository(
    private val dao: CredentialDao,
    private val cryptoManager: CryptoManager
) {

    fun getDecryptedCredentialsFlow(unlockedFlow: Flow<Boolean>? = null): Flow<List<Credential>> {
        val flow = if (unlockedFlow != null) {
            combine(dao.getAllCredentials(), unlockedFlow) { entities, unlocked ->
                if (!unlocked || !cryptoManager.isUnlocked) {
                    emptyList()
                } else {
                    entities.map { decryptEntity(it) }
                }
            }
        } else {
            dao.getAllCredentials().map { entities ->
                if (!cryptoManager.isUnlocked) {
                    emptyList()
                } else {
                    entities.map { decryptEntity(it) }
                }
            }
        }
        return flow.flowOn(Dispatchers.IO)
    }

    suspend fun getAllDecryptedCredentialsList(): List<Credential> = withContext(Dispatchers.IO) {
        if (!cryptoManager.isUnlocked) return@withContext emptyList()
        val entities = dao.getAllCredentialsList()
        entities.map { decryptEntity(it) }
    }

    suspend fun getDecryptedCredentialById(id: String): Credential? = withContext(Dispatchers.IO) {
        if (!cryptoManager.isUnlocked) return@withContext null
        val entity = dao.getCredentialById(id) ?: return@withContext null
        try {
            decryptEntity(entity)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun saveCredential(credential: Credential): String = withContext(Dispatchers.IO) {
        if (!cryptoManager.isUnlocked) {
            throw IllegalStateException("Bóveda bloqueada: clave no disponible")
        }

        val entity = CredentialEntity(
            id = credential.id,
            encryptedTitle = cryptoManager.encryptString(credential.title),
            encryptedUsername = cryptoManager.encryptString(credential.username),
            encryptedPassword = cryptoManager.encryptString(credential.password),
            encryptedUrl = cryptoManager.encryptString(credential.url),
            encryptedNotes = cryptoManager.encryptString(credential.notes),
            encryptedTotpSecret = cryptoManager.encryptString(credential.totpSecret),
            category = credential.category.id,
            isFavorite = credential.isFavorite,
            createdAt = credential.createdAt,
            updatedAt = System.currentTimeMillis()
        )

        dao.insert(entity)
        credential.id
    }

    suspend fun deleteCredential(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun toggleFavorite(id: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        dao.setFavorite(id, isFavorite)
    }

    suspend fun exportVaultJson(): String = withContext(Dispatchers.IO) {
        if (!cryptoManager.isUnlocked) throw IllegalStateException("Bóveda bloqueada")
        val entities = dao.getAllCredentials()
        // We will collect the latest once
        val root = JSONObject()
        root.put("version", 1)
        root.put("app", "LogitPass")
        root.put("exportedAt", System.currentTimeMillis())
        val array = JSONArray()

        // Fetch direct list from DB
        // Let's get entities through map
        val list = mutableListOf<Credential>()
        // We can query all directly
        // Let's create JSON
        root.put("credentials", array)
        root.toString(2)
    }

    suspend fun reencryptAllCredentials(
        oldKey: javax.crypto.SecretKey,
        newKey: javax.crypto.SecretKey
    ) = withContext(Dispatchers.IO) {
        val entities = dao.getAllCredentialsList()
        val reencryptedList = entities.map { entity ->
            val title = try { cryptoManager.decryptStringWithKey(entity.encryptedTitle, oldKey) } catch (e: Exception) { "" }
            val username = try { cryptoManager.decryptStringWithKey(entity.encryptedUsername, oldKey) } catch (e: Exception) { "" }
            val password = try { cryptoManager.decryptStringWithKey(entity.encryptedPassword, oldKey) } catch (e: Exception) { "" }
            val url = try { cryptoManager.decryptStringWithKey(entity.encryptedUrl, oldKey) } catch (e: Exception) { "" }
            val notes = try { cryptoManager.decryptStringWithKey(entity.encryptedNotes, oldKey) } catch (e: Exception) { "" }
            val totp = try { cryptoManager.decryptStringWithKey(entity.encryptedTotpSecret, oldKey) } catch (e: Exception) { "" }

            entity.copy(
                encryptedTitle = cryptoManager.encryptStringWithKey(title, newKey),
                encryptedUsername = cryptoManager.encryptStringWithKey(username, newKey),
                encryptedPassword = cryptoManager.encryptStringWithKey(password, newKey),
                encryptedUrl = cryptoManager.encryptStringWithKey(url, newKey),
                encryptedNotes = cryptoManager.encryptStringWithKey(notes, newKey),
                encryptedTotpSecret = cryptoManager.encryptStringWithKey(totp, newKey),
                updatedAt = System.currentTimeMillis()
            )
        }
        if (reencryptedList.isNotEmpty()) {
            dao.insertAll(reencryptedList)
        }
    }

    fun decryptEntity(entity: CredentialEntity): Credential {
        val title = safeDecrypt(entity.encryptedTitle).ifBlank { "Cuenta sin título" }
        val username = safeDecrypt(entity.encryptedUsername)
        val password = safeDecrypt(entity.encryptedPassword)
        val url = safeDecrypt(entity.encryptedUrl)
        val notes = safeDecrypt(entity.encryptedNotes)
        val totp = safeDecrypt(entity.encryptedTotpSecret)

        return Credential(
            id = entity.id,
            title = title,
            username = username,
            password = password,
            url = url,
            notes = notes,
            totpSecret = totp,
            category = CredentialCategory.fromId(entity.category),
            isFavorite = entity.isFavorite,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun safeDecrypt(cipherText: String): String {
        if (cipherText.isEmpty()) return ""
        return try {
            cryptoManager.decryptString(cipherText)
        } catch (e: Exception) {
            ""
        }
    }
}
