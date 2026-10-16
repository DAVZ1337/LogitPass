package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de credencial cifrada en reposo con AES-256-GCM.
 * Ningún secreto (contraseña, usuario, notas o url) se almacena en texto plano en la base de datos SQLite.
 */
@Entity(
    tableName = "credentials",
    indices = [
        Index(value = ["category"]),
        Index(value = ["isFavorite"]),
        Index(value = ["updatedAt"])
    ]
)
data class CredentialEntity(
    @PrimaryKey
    val id: String,
    val encryptedTitle: String,
    val encryptedUsername: String,
    val encryptedPassword: String,
    val encryptedUrl: String,
    val encryptedNotes: String,
    val encryptedTotpSecret: String,
    val category: String,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
