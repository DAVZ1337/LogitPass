package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Credential
import com.example.model.Screen
import com.example.security.PasswordGenerator
import com.example.ui.VaultViewModel
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldSecurity
import com.example.ui.theme.FolderAmber
import com.example.ui.theme.RoseError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen(
    viewModel: VaultViewModel
) {
    val stats by viewModel.vaultStats.collectAsState()
    val allCredentials by viewModel.allCredentialsFlow.collectAsState(initial = emptyList())

    // Group passwords to identify reuse
    val passwordCounts = remember(allCredentials) {
        val map = mutableMapOf<String, Int>()
        for (c in allCredentials) {
            if (c.password.isNotEmpty()) {
                map[c.password] = (map[c.password] ?: 0) + 1
            }
        }
        map
    }

    // List of vulnerable credentials
    val vulnerableCredentials = remember(allCredentials, passwordCounts) {
        allCredentials.filter { c ->
            val strength = PasswordGenerator.evaluateStrength(c.password)
            val isWeak = strength.level == PasswordGenerator.StrengthLevel.VERY_WEAK ||
                    strength.level == PasswordGenerator.StrengthLevel.WEAK
            val isReused = (passwordCounts[c.password] ?: 0) > 1
            isWeak || isReused
        }
    }

    BackHandler {
        viewModel.navigateTo(Screen.VaultList)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Auditoría de Seguridad",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.VaultList) },
                        modifier = Modifier.testTag("audit_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overall Score Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val scoreColor = when {
                                stats.securityScore >= 80 -> EmeraldSecurity
                                stats.securityScore >= 50 -> FolderAmber
                                else -> RoseError
                            }
                            CircularProgressIndicator(
                                progress = { stats.securityScore / 100f },
                                modifier = Modifier.size(80.dp),
                                color = scoreColor,
                                strokeWidth = 8.dp,
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                            Text(
                                text = "${stats.securityScore}%",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = scoreColor
                            )
                        }

                        Spacer(modifier = Modifier.width(20.dp))

                        Column {
                            Text(
                                text = "Salud de la Bóveda",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    stats.securityScore >= 85 -> "Excelente nivel de protección criptográfica."
                                    stats.securityScore >= 60 -> "Buen nivel. Corrige contraseñas repetidas."
                                    else -> "Atención requerida: muchas claves débiles o reutilizadas."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Summary Stats Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AuditMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Débiles",
                        count = stats.weakCount,
                        color = if (stats.weakCount > 0) RoseError else EmeraldSecurity
                    )
                    AuditMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Reutilizadas",
                        count = stats.reusedCount,
                        color = if (stats.reusedCount > 0) FolderAmber else EmeraldSecurity
                    )
                    AuditMetricCard(
                        modifier = Modifier.weight(1f),
                        title = "Total Cuentas",
                        count = stats.totalCount,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Recommendations Section
            item {
                Text(
                    text = "Vulnerabilidades Detectadas",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (vulnerableCredentials.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = EmeraldSecurity.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldSecurity,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Sin vulnerabilidades críticas",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = EmeraldSecurity
                                )
                                Text(
                                    text = "Todas tus contraseñas tienen longitud adecuada y no están repetidas.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                items(vulnerableCredentials, key = { it.id }) { item ->
                    val strength = PasswordGenerator.evaluateStrength(item.password)
                    val isWeak = strength.level == PasswordGenerator.StrengthLevel.VERY_WEAK ||
                            strength.level == PasswordGenerator.StrengthLevel.WEAK
                    val isReused = (passwordCounts[item.password] ?: 0) > 1

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateTo(Screen.CredentialDetail(item.id)) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isWeak) RoseError else FolderAmber,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        if (isWeak) append("Contraseña débil (${strength.entropyBits} bits) • ")
                                        if (isReused) append("Reutilizada en ${passwordCounts[item.password]} cuentas")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isWeak) RoseError else FolderAmber
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.navigateTo(Screen.CredentialDetail(item.id)) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Reparar", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AuditMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    count: Int,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
