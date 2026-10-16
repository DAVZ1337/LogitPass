package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.security.BiometricHelper
import com.example.security.PasswordGenerator
import com.example.ui.VaultViewModel
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldSecurity
import com.example.ui.theme.FolderAmber
import com.example.ui.theme.RoseError

@Composable
fun LockScreen(
    activity: FragmentActivity,
    viewModel: VaultViewModel
) {
    val isInitialized by viewModel.isVaultInitialized.collectAsState()
    val unlockError by viewModel.unlockError.collectAsState()
    val isDerivingKey by viewModel.isDerivingKey.collectAsState()
    val isBiometricAvailable = viewModel.isBiometricAvailable
    val isBiometricEnabled = viewModel.isBiometricEnabled

    if (!isInitialized) {
        SetupVaultContent(
            isBiometricAvailable = isBiometricAvailable,
            isDeriving = isDerivingKey,
            onSetup = { name, pass, bio ->
                viewModel.setupVault(name, pass, bio)
            },
            errorMessage = unlockError
        )
    } else {
        UnlockVaultContent(
            activity = activity,
            isBiometricAvailable = isBiometricAvailable,
            isBiometricEnabled = isBiometricEnabled,
            isDeriving = isDerivingKey,
            onUnlockWithPassword = { pass ->
                viewModel.unlockWithPassword(pass)
            },
            onUnlockWithBiometric = {
                viewModel.unlockWithBiometric()
            },
            errorMessage = unlockError,
            onClearError = { viewModel.clearUnlockError() }
        )
    }
}

@Composable
private fun SetupVaultContent(
    isBiometricAvailable: Boolean,
    isDeriving: Boolean,
    onSetup: (name: String, masterPass: String, enableBio: Boolean) -> Unit,
    errorMessage: String?
) {
    var vaultName by remember { mutableStateOf("Bóveda Personal") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var enableBiometric by remember { mutableStateOf(isBiometricAvailable) }
    var localError by remember { mutableStateOf<String?>(null) }

    val strength = remember(password) { PasswordGenerator.evaluateStrength(password) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(EmeraldSecurity.copy(alpha = 0.12f), CircleShape)
                .border(1.5.dp, EmeraldPrimary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Seguridad LogitPass",
                tint = EmeraldSecurity,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Crear Bóveda Segura",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "AES-256-GCM | 600.000 Iteraciones PBKDF2 | 100% Local",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = vaultName,
                    onValueChange = { vaultName = it },
                    label = { Text("Nombre de la Bóveda") },
                    singleLine = true,
                    enabled = !isDeriving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_vault_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        localError = null
                    },
                    label = { Text("Contraseña Maestra") },
                    singleLine = true,
                    enabled = !isDeriving,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Mostrar u ocultar contraseña"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_master_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (password.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { strength.score },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = Color(strength.level.colorHex),
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strength.level.label,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(strength.level.colorHex)
                        )
                        Text(
                            text = "${strength.entropyBits} bits",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        localError = null
                    },
                    label = { Text("Confirmar Contraseña") },
                    singleLine = true,
                    enabled = !isDeriving,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup_confirm_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (isBiometricAvailable) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = EmeraldSecurity,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Desbloqueo Biométrico",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Huella dactilar o rostro",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = enableBiometric,
                            onCheckedChange = { enableBiometric = it },
                            enabled = !isDeriving,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EmeraldSecurity,
                                checkedTrackColor = EmeraldSecurity.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        }

        val displayError = localError ?: errorMessage
        if (displayError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = RoseError.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = RoseError)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = displayError, color = RoseError, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = FolderAmber.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = FolderAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Aviso Zero-Knowledge: Esta contraseña deriva la clave AES de 256 bits mediante 600.000 iteraciones (Estándar OWASP). Memorízala bien.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isDeriving) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = EmeraldSecurity, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Derivando clave con 600.000 iteraciones...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmeraldSecurity
                )
            }
        } else {
            Button(
                onClick = {
                    if (password.trim().length < 4) {
                        localError = "La contraseña debe tener mínimo 4 caracteres"
                    } else if (password.trim() != confirmPassword.trim()) {
                        localError = "Las contraseñas no coinciden"
                    } else {
                        onSetup(vaultName.trim(), password.trim(), enableBiometric)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("create_vault_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crear Bóveda Cifrada", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun UnlockVaultContent(
    activity: FragmentActivity,
    isBiometricAvailable: Boolean,
    isBiometricEnabled: Boolean,
    isDeriving: Boolean,
    onUnlockWithPassword: (password: String) -> Unit,
    onUnlockWithBiometric: () -> Unit,
    errorMessage: String?,
    onClearError: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isBiometricAvailable && isBiometricEnabled) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                onSuccess = onUnlockWithBiometric,
                onError = {}
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(EmeraldSecurity.copy(alpha = 0.12f), CircleShape)
                .border(2.dp, EmeraldPrimary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Bóveda Bloqueada",
                tint = EmeraldSecurity,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "LogitPass",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Bóveda Cifrada (AES-256-GCM | 600K Iteraciones)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearError()
                    },
                    label = { Text("Contraseña Maestra") },
                    singleLine = true,
                    enabled = !isDeriving,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (password.isNotEmpty() && !isDeriving) onUnlockWithPassword(password)
                    }),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Mostrar u ocultar contraseña"
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldSecurity,
                        focusedLabelColor = EmeraldSecurity
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("unlock_password_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = RoseError,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                if (isDeriving) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = EmeraldSecurity, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Descifrando con 600K de iteraciones...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldSecurity
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (password.isNotEmpty()) {
                                onUnlockWithPassword(password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("unlock_vault_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Desbloquear Bóveda")
                    }

                    if (isBiometricAvailable && isBiometricEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                BiometricHelper.showBiometricPrompt(
                                    activity = activity,
                                    onSuccess = onUnlockWithBiometric,
                                    onError = {}
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("biometric_unlock_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = EmeraldSecurity
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Acceso Biométrico")
                        }
                    }
                }
            }
        }
    }
}
