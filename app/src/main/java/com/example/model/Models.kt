package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.UUID

enum class CredentialCategory(
    val id: String,
    val displayName: String,
    val iconType: String,
    val colorHex: Long
) {
    ALL("all", "Todas", "all", 0xFF64748B),
    WEB("web", "Web & Cuentas", "web", 0xFF0284C7),
    SOCIAL("social", "Redes Sociales", "social", 0xFF8B5CF6),
    FINANCE("finance", "Finanzas & Bancos", "finance", 0xFF10B981),
    WORK("work", "Trabajo & Dev", "work", 0xFFF59E0B),
    EMAIL("email", "Correos", "email", 0xFFEC4899),
    NOTE("note", "Notas Secretas", "note", 0xFF6366F1);

    companion object {
        fun fromId(id: String): CredentialCategory {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: WEB
        }
    }
}

fun getCategoryIcon(cat: CredentialCategory): ImageVector {
    return when (cat) {
        CredentialCategory.ALL -> Icons.Default.FilterList
        CredentialCategory.WEB -> Icons.Default.Language
        CredentialCategory.SOCIAL -> Icons.Default.Share
        CredentialCategory.FINANCE -> Icons.Default.CreditCard
        CredentialCategory.WORK -> Icons.Default.Work
        CredentialCategory.EMAIL -> Icons.Default.Email
        CredentialCategory.NOTE -> Icons.Default.Note
    }
}

data class Credential(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val category: CredentialCategory = CredentialCategory.WEB,
    val notes: String = "",
    val totpSecret: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class VaultStats(
    val totalCount: Int = 0,
    val weakCount: Int = 0,
    val reusedCount: Int = 0,
    val favoritesCount: Int = 0,
    val securityScore: Int = 100 // 0 to 100
)

sealed interface Screen {
    data object VaultList : Screen
    data class CredentialDetail(val credentialId: String?) : Screen
    data object PasswordGenerator : Screen
    data object SecurityAudit : Screen
    data object Settings : Screen
}
