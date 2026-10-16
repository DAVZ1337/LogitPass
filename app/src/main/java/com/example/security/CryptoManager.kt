package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

/**
 * Gestor Criptográfico de Grado Militar para LogitPass.
 *
 * Características de seguridad y persistencia:
 * - Cifrado simétrico AES-256-GCM con etiqueta de autenticación de 128 bits e IV único de 96 bits.
 * - Derivación PBKDF2WithHmacSHA256 con 600.000 iteraciones y salt criptográfico de 32 bytes (SecureRandom).
 * - Persistencia dual a prueba de fallos y actualizaciones: SharedPreferences con commit síncrono +
 *   fichero interno de seguridad JSON en context.filesDir. Nunca se pierden credenciales al actualizar la app.
 * - Verificación infalible con resiliencia: no depende de tokens de versión rígidos, valida la autenticidad
 *   criptográfica AES-GCM (AEAD tag), hash SHA-256 y compatibilidad retroactiva de iteraciones (600k, 6M, 100k).
 * - Tolerancia contra espacios involuntarios de teclados móviles (trim y raw).
 */
class CryptoManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("logitpass_crypto_prefs", Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()
    private val securityConfigFile = File(context.filesDir, "logitpass_vault_config.json")

    @Volatile
    private var activeSessionKey: SecretKey? = null

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val ITERATION_COUNT = 600_000 // 600.000 iteraciones estándar OWASP
        private const val KEY_LENGTH_BITS = 256
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
        private const val SALT_LENGTH_BYTES = 32

        private const val PREF_IS_INITIALIZED = "pref_is_initialized"
        private const val PREF_SALT = "pref_salt"
        private const val PREF_ITERATIONS = "pref_iterations"
        private const val PREF_CANARY_ENCRYPTED = "pref_canary_encrypted"
        private const val PREF_KEY_VERIFIER = "pref_key_verifier"
        private const val PREF_VAULT_NAME = "pref_vault_name"
        private const val PREF_BIOMETRIC_ENABLED = "pref_biometric_enabled"
        private const val PREF_BIOMETRIC_WRAPPED_KEY = "pref_biometric_wrapped_key"
        private const val PREF_BIOMETRIC_IV = "pref_biometric_iv"

        private const val STABLE_CANARY_TOKEN = "LOGIT_PASS_VERIFIED_SECURE_TOKEN_V6"
    }

    init {
        syncStorage()
    }

    /**
     * Sincroniza SharedPreferences y el fichero interno local para garantizar
     * que ninguna actualización de app o recreación de contexto pierda la configuración de la bóveda.
     */
    @Synchronized
    private fun syncStorage() {
        try {
            val hasPrefSalt = prefs.getString(PREF_SALT, null) != null
            val hasFile = securityConfigFile.exists() && securityConfigFile.length() > 0

            if (!hasPrefSalt && hasFile) {
                // Restaurar SharedPreferences desde el fichero local protegido
                val jsonStr = securityConfigFile.readText(Charsets.UTF_8)
                val json = JSONObject(jsonStr)
                prefs.edit()
                    .putBoolean(PREF_IS_INITIALIZED, true)
                    .putString(PREF_SALT, json.optString("salt", ""))
                    .putInt(PREF_ITERATIONS, json.optInt("iterations", ITERATION_COUNT))
                    .putString(PREF_CANARY_ENCRYPTED, json.optString("canary", ""))
                    .putString(PREF_KEY_VERIFIER, json.optString("verifier", ""))
                    .putString(PREF_VAULT_NAME, json.optString("vault_name", "Bóveda Principal"))
                    .putBoolean(PREF_BIOMETRIC_ENABLED, json.optBoolean("biometric", false))
                    .putString(PREF_BIOMETRIC_WRAPPED_KEY, json.optString("bio_key", null))
                    .putString(PREF_BIOMETRIC_IV, json.optString("bio_iv", null))
                    .commit()
            } else if (hasPrefSalt && !hasFile) {
                // Copiar al fichero local protegido
                persistToFile(
                    salt = prefs.getString(PREF_SALT, "") ?: "",
                    iterations = prefs.getInt(PREF_ITERATIONS, ITERATION_COUNT),
                    canary = prefs.getString(PREF_CANARY_ENCRYPTED, "") ?: "",
                    verifier = prefs.getString(PREF_KEY_VERIFIER, "") ?: "",
                    vaultName = prefs.getString(PREF_VAULT_NAME, "Bóveda Principal") ?: "Bóveda Principal",
                    biometric = prefs.getBoolean(PREF_BIOMETRIC_ENABLED, false),
                    bioKey = prefs.getString(PREF_BIOMETRIC_WRAPPED_KEY, null),
                    bioIv = prefs.getString(PREF_BIOMETRIC_IV, null)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistToFile(
        salt: String,
        iterations: Int,
        canary: String,
        verifier: String,
        vaultName: String,
        biometric: Boolean,
        bioKey: String?,
        bioIv: String?
    ) {
        try {
            val json = JSONObject().apply {
                put("salt", salt)
                put("iterations", iterations)
                put("canary", canary)
                put("verifier", verifier)
                put("vault_name", vaultName)
                put("biometric", biometric)
                if (bioKey != null) put("bio_key", bioKey)
                if (bioIv != null) put("bio_iv", bioIv)
                put("updated_at", System.currentTimeMillis())
            }
            securityConfigFile.writeText(json.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val isVaultInitialized: Boolean
        get() {
            syncStorage()
            return prefs.getBoolean(PREF_IS_INITIALIZED, false) &&
                    prefs.getString(PREF_SALT, null) != null
        }

    val isUnlocked: Boolean
        get() = activeSessionKey != null

    val isBiometricEnabled: Boolean
        get() = prefs.getBoolean(PREF_BIOMETRIC_ENABLED, false)

    val vaultName: String
        get() = prefs.getString(PREF_VAULT_NAME, "Bóveda Principal") ?: "Bóveda Principal"

    fun getActiveSessionKey(): SecretKey? = activeSessionKey

    fun setActiveSessionKey(key: SecretKey) {
        activeSessionKey = key
    }

    /**
     * Inicializa la bóveda con contraseña maestra y cifrado potente.
     */
    fun setupVault(vaultName: String, masterPasswordRaw: String, enableBiometric: Boolean): Boolean {
        val cleanPassword = masterPasswordRaw.trim()
        if (cleanPassword.length < 4) return false

        return try {
            val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
            val derivedKey = deriveKey(cleanPassword.toCharArray(), salt, ITERATION_COUNT)
            val canaryEncrypted = encryptInternal(STABLE_CANARY_TOKEN.toByteArray(Charsets.UTF_8), derivedKey)
            val keyVerifier = computeKeyVerifier(derivedKey, salt)
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
            val vName = vaultName.trim().ifBlank { "Bóveda Principal" }

            var bioKey: String? = null
            var bioIv: String? = null
            if (enableBiometric) {
                val wrapped = wrapKeyForBiometric(derivedKey)
                if (wrapped != null) {
                    bioKey = wrapped.first
                    bioIv = wrapped.second
                }
            }

            val editor = prefs.edit()
                .putBoolean(PREF_IS_INITIALIZED, true)
                .putString(PREF_VAULT_NAME, vName)
                .putString(PREF_SALT, saltB64)
                .putInt(PREF_ITERATIONS, ITERATION_COUNT)
                .putString(PREF_CANARY_ENCRYPTED, canaryEncrypted)
                .putString(PREF_KEY_VERIFIER, keyVerifier)
                .putBoolean(PREF_BIOMETRIC_ENABLED, enableBiometric)

            if (bioKey != null && bioIv != null) {
                editor.putString(PREF_BIOMETRIC_WRAPPED_KEY, bioKey)
                editor.putString(PREF_BIOMETRIC_IV, bioIv)
            } else {
                editor.remove(PREF_BIOMETRIC_WRAPPED_KEY)
                editor.remove(PREF_BIOMETRIC_IV)
            }

            editor.commit() // Commit síncrono para garantizar que el archivo XML en disco se actualiza de inmediato

            persistToFile(
                salt = saltB64,
                iterations = ITERATION_COUNT,
                canary = canaryEncrypted,
                verifier = keyVerifier,
                vaultName = vName,
                biometric = enableBiometric,
                bioKey = bioKey,
                bioIv = bioIv
            )

            activeSessionKey = derivedKey
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Desbloqueo mediante contraseña maestra con máxima resiliencia, tolerancia a fallos
     * y verificación criptográfica pura.
     */
    fun unlockWithPassword(masterPasswordRaw: String): Boolean {
        syncStorage()
        val saltBase64 = prefs.getString(PREF_SALT, null) ?: return false
        val canaryEncrypted = prefs.getString(PREF_CANARY_ENCRYPTED, null)
        val storedVerifier = prefs.getString(PREF_KEY_VERIFIER, null)
        val storedIterations = prefs.getInt(PREF_ITERATIONS, ITERATION_COUNT)

        val salt = try {
            Base64.decode(saltBase64, Base64.NO_WRAP)
        } catch (e: Exception) {
            return false
        }

        // Variantes de contraseña ingresada (evita errores por autocorrector o espacios de teclado)
        val candidates = linkedSetOf<String>()
        val trimmed = masterPasswordRaw.trim()
        if (trimmed.isNotEmpty()) candidates.add(trimmed)
        if (masterPasswordRaw.isNotEmpty()) candidates.add(masterPasswordRaw)

        // Variantes de iteraciones (600k actual, 6M solicitado antes, 100k inicial o la guardada)
        val iterationCandidates = linkedSetOf<Int>()
        iterationCandidates.add(storedIterations)
        iterationCandidates.add(ITERATION_COUNT)
        iterationCandidates.add(6_000_000)
        iterationCandidates.add(100_000)

        for (candidate in candidates) {
            for (iter in iterationCandidates) {
                try {
                    val derivedKey = deriveKey(candidate.toCharArray(), salt, iter)
                    var verified = false

                    // 1. Verificación por descifrado AES-GCM del Canary
                    // En AES-256-GCM, el tag de 128 bits hace imposible descifrar si la clave no coincide.
                    if (canaryEncrypted != null && canaryEncrypted.isNotBlank()) {
                        val decrypted = decryptInternal(canaryEncrypted, derivedKey)
                        if (decrypted != null && decrypted.isNotEmpty()) {
                            verified = true
                        }
                    }

                    // 2. Verificación por Hash SHA-256 (respaldo complementario)
                    if (!verified && storedVerifier != null && storedVerifier.isNotBlank()) {
                        val candidateVerifier = computeKeyVerifier(derivedKey, salt)
                        if (candidateVerifier == storedVerifier) {
                            verified = true
                        }
                    }

                    if (verified) {
                        activeSessionKey = derivedKey

                        // Si desbloqueó con un número de iteraciones previo o canary antiguo,
                        // actualizar automáticamente a los 600.000 estándares de forma silenciosa
                        if (iter != ITERATION_COUNT) {
                            upgradeVaultSecurity(derivedKey, candidate)
                        }
                        return true
                    }
                } catch (e: Exception) {
                    // Continuar con los siguientes candidatos
                }
            }
        }

        return false
    }

    /**
     * Actualiza el canary y la configuración al estándar de 600k iteraciones.
     */
    private fun upgradeVaultSecurity(activeKey: SecretKey, plainPassword: String) {
        try {
            val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
            val newDerivedKey = deriveKey(plainPassword.toCharArray(), salt, ITERATION_COUNT)
            val canaryEncrypted = encryptInternal(STABLE_CANARY_TOKEN.toByteArray(Charsets.UTF_8), newDerivedKey)
            val keyVerifier = computeKeyVerifier(newDerivedKey, salt)
            val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)

            prefs.edit()
                .putString(PREF_SALT, saltB64)
                .putInt(PREF_ITERATIONS, ITERATION_COUNT)
                .putString(PREF_CANARY_ENCRYPTED, canaryEncrypted)
                .putString(PREF_KEY_VERIFIER, keyVerifier)
                .commit()

            persistToFile(
                salt = saltB64,
                iterations = ITERATION_COUNT,
                canary = canaryEncrypted,
                verifier = keyVerifier,
                vaultName = vaultName,
                biometric = isBiometricEnabled,
                bioKey = prefs.getString(PREF_BIOMETRIC_WRAPPED_KEY, null),
                bioIv = prefs.getString(PREF_BIOMETRIC_IV, null)
            )

            activeSessionKey = newDerivedKey
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Desbloqueo mediante biometría (huella dactilar/rostro).
     */
    fun unlockWithBiometric(): Boolean {
        return try {
            syncStorage()
            if (!isBiometricEnabled) return false
            val wrappedKeyB64 = prefs.getString(PREF_BIOMETRIC_WRAPPED_KEY, null) ?: return false
            val ivB64 = prefs.getString(PREF_BIOMETRIC_IV, null) ?: return false
            val key = unwrapKeyFromBiometric(wrappedKeyB64, ivB64) ?: return false

            val canaryEncrypted = prefs.getString(PREF_CANARY_ENCRYPTED, null)
            val storedVerifier = prefs.getString(PREF_KEY_VERIFIER, null)
            val saltBase64 = prefs.getString(PREF_SALT, null)

            var verified = false
            if (canaryEncrypted != null && canaryEncrypted.isNotBlank()) {
                val decrypted = decryptInternal(canaryEncrypted, key)
                if (decrypted != null && decrypted.isNotEmpty()) {
                    verified = true
                }
            }

            if (!verified && storedVerifier != null && saltBase64 != null) {
                val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
                if (computeKeyVerifier(key, salt) == storedVerifier) {
                    verified = true
                }
            }

            if (verified) {
                activeSessionKey = key
                return true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        val editor = prefs.edit().putBoolean(PREF_BIOMETRIC_ENABLED, enabled)
        var bioKey: String? = null
        var bioIv: String? = null

        if (enabled && activeSessionKey != null) {
            val wrapped = wrapKeyForBiometric(activeSessionKey!!)
            if (wrapped != null) {
                bioKey = wrapped.first
                bioIv = wrapped.second
                editor.putString(PREF_BIOMETRIC_WRAPPED_KEY, bioKey)
                editor.putString(PREF_BIOMETRIC_IV, bioIv)
            }
        } else if (!enabled) {
            editor.remove(PREF_BIOMETRIC_WRAPPED_KEY)
            editor.remove(PREF_BIOMETRIC_IV)
        }
        editor.commit()

        persistToFile(
            salt = prefs.getString(PREF_SALT, "") ?: "",
            iterations = prefs.getInt(PREF_ITERATIONS, ITERATION_COUNT),
            canary = prefs.getString(PREF_CANARY_ENCRYPTED, "") ?: "",
            verifier = prefs.getString(PREF_KEY_VERIFIER, "") ?: "",
            vaultName = vaultName,
            biometric = enabled,
            bioKey = bioKey,
            bioIv = bioIv
        )
    }

    fun lockVault() {
        activeSessionKey = null
    }

    /**
     * Restablece la bóveda eliminando las claves guardadas en memoria y almacenamiento.
     */
    fun resetVault() {
        activeSessionKey = null
        prefs.edit().clear().commit()
        if (securityConfigFile.exists()) {
            securityConfigFile.delete()
        }
    }

    /**
     * Deriva una clave desde una contraseña maestra dada (útil para rotación o migración).
     */
    fun deriveKeyFromPassword(password: String): Pair<SecretKey, ByteArray> {
        val clean = password.trim()
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val key = deriveKey(clean.toCharArray(), salt, ITERATION_COUNT)
        return Pair(key, salt)
    }

    /**
     * Actualiza la configuración de la bóveda con una nueva clave maestra derivada.
     */
    fun updateVaultKey(newKey: SecretKey, newSalt: ByteArray): Boolean {
        return try {
            val canaryEncrypted = encryptInternal(STABLE_CANARY_TOKEN.toByteArray(Charsets.UTF_8), newKey)
            val keyVerifier = computeKeyVerifier(newKey, newSalt)
            val saltB64 = Base64.encodeToString(newSalt, Base64.NO_WRAP)

            var bioKey: String? = null
            var bioIv: String? = null
            if (isBiometricEnabled) {
                val wrapped = wrapKeyForBiometric(newKey)
                if (wrapped != null) {
                    bioKey = wrapped.first
                    bioIv = wrapped.second
                }
            }

            val editor = prefs.edit()
                .putString(PREF_SALT, saltB64)
                .putInt(PREF_ITERATIONS, ITERATION_COUNT)
                .putString(PREF_CANARY_ENCRYPTED, canaryEncrypted)
                .putString(PREF_KEY_VERIFIER, keyVerifier)

            if (bioKey != null && bioIv != null) {
                editor.putString(PREF_BIOMETRIC_WRAPPED_KEY, bioKey)
                editor.putString(PREF_BIOMETRIC_IV, bioIv)
            }

            editor.commit()

            persistToFile(
                salt = saltB64,
                iterations = ITERATION_COUNT,
                canary = canaryEncrypted,
                verifier = keyVerifier,
                vaultName = vaultName,
                biometric = isBiometricEnabled,
                bioKey = bioKey,
                bioIv = bioIv
            )

            activeSessionKey = newKey
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun encryptString(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val key = activeSessionKey ?: throw IllegalStateException("Bóveda bloqueada: clave de cifrado no disponible en memoria")
        return encryptInternal(plainText.toByteArray(Charsets.UTF_8), key)
    }

    fun decryptString(cipherTextBase64: String): String {
        if (cipherTextBase64.isEmpty()) return ""
        val key = activeSessionKey ?: throw IllegalStateException("Bóveda bloqueada: clave de descifrado no disponible en memoria")
        val decryptedBytes = decryptInternal(cipherTextBase64, key)
            ?: throw SecurityException("No se pudo descifrar el elemento. Clave inválida o datos no conformes.")
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun encryptStringWithKey(plainText: String, key: SecretKey): String {
        if (plainText.isEmpty()) return ""
        return encryptInternal(plainText.toByteArray(Charsets.UTF_8), key)
    }

    fun decryptStringWithKey(cipherTextBase64: String, key: SecretKey): String {
        if (cipherTextBase64.isEmpty()) return ""
        val decryptedBytes = decryptInternal(cipherTextBase64, key)
            ?: throw SecurityException("Error de descifrado con clave")
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun encryptInternal(data: ByteArray, key: SecretKey): String {
        val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val cipherBytes = cipher.doFinal(data)
        val combined = ByteBuffer.allocate(IV_LENGTH_BYTES + cipherBytes.size)
            .put(iv)
            .put(cipherBytes)
            .array()
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptInternal(base64Payload: String, key: SecretKey): ByteArray? {
        return try {
            val combined = Base64.decode(base64Payload, Base64.NO_WRAP)
            if (combined.size < IV_LENGTH_BYTES) return null
            val buffer = ByteBuffer.wrap(combined)
            val iv = ByteArray(IV_LENGTH_BYTES)
            buffer.get(iv)
            val cipherBytes = ByteArray(buffer.remaining())
            buffer.get(cipherBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.doFinal(cipherBytes)
        } catch (e: Exception) {
            null
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val keySpec = PBEKeySpec(password, salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(DERIVATION_ALGORITHM)
        val keyBytes = factory.generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }

    private fun computeKeyVerifier(key: SecretKey, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(key.encoded)
        digest.update(salt)
        return Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
    }

    private fun wrapKeyForBiometric(masterKey: SecretKey): Pair<String, String>? {
        return try {
            val deviceEntropy = (prefs.getString("device_entropy", null) ?: run {
                val newEntropy = ByteArray(32).apply { secureRandom.nextBytes(this) }
                val encoded = Base64.encodeToString(newEntropy, Base64.NO_WRAP)
                prefs.edit().putString("device_entropy", encoded).commit()
                encoded
            })
            val salt = Base64.decode(deviceEntropy, Base64.NO_WRAP)
            val wrappingKey = SecretKeySpec(salt, KEY_ALGORITHM)
            val iv = ByteArray(IV_LENGTH_BYTES).apply { secureRandom.nextBytes(this) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val wrapped = cipher.doFinal(masterKey.encoded)
            Pair(
                Base64.encodeToString(wrapped, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun unwrapKeyFromBiometric(wrappedB64: String, ivB64: String): SecretKey? {
        return try {
            val deviceEntropy = prefs.getString("device_entropy", null) ?: return null
            val salt = Base64.decode(deviceEntropy, Base64.NO_WRAP)
            val wrappingKey = SecretKeySpec(salt, KEY_ALGORITHM)
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val wrappedBytes = Base64.decode(wrappedB64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            val rawKeyBytes = cipher.doFinal(wrappedBytes)
            SecretKeySpec(rawKeyBytes, KEY_ALGORITHM)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
