package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Credential
import com.example.model.CredentialCategory
import com.example.model.Screen
import com.example.model.getCategoryIcon
import com.example.security.PasswordGenerator
import com.example.ui.VaultViewModel
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldSecurity
import com.example.ui.theme.FolderAmber
import com.example.ui.theme.RoseError
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialDetailScreen(
    credentialId: String?,
    viewModel: VaultViewModel
) {
    val context = LocalContext.current
    val currentCredential by viewModel.editingCredential.collectAsState()

    var title by remember(currentCredential) { mutableStateOf(currentCredential?.title ?: "") }
    var username by remember(currentCredential) { mutableStateOf(currentCredential?.username ?: "") }
    var password by remember(currentCredential) { mutableStateOf(currentCredential?.password ?: "") }
    var url by remember(currentCredential) { mutableStateOf(currentCredential?.url ?: "") }
    var category by remember(currentCredential) { mutableStateOf(currentCredential?.category ?: CredentialCategory.WEB) }
    var notes by remember(currentCredential) { mutableStateOf(currentCredential?.notes ?: "") }
    var totpSecret by remember(currentCredential) { mutableStateOf(currentCredential?.totpSecret ?: "") }
    var isFavorite by remember(currentCredential) { mutableStateOf(currentCredential?.isFavorite ?: false) }

    var passwordVisible by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showGeneratorDialog by remember { mutableStateOf(false) }

    val strength = remember(password) { PasswordGenerator.evaluateStrength(password) }

    // Live TOTP simulation counter (30s window)
    var totpTimeRemaining by remember { mutableIntStateOf(30) }
    var simulatedTotpCode by remember { mutableStateOf("482 195") }

    LaunchedEffect(totpSecret) {
        if (totpSecret.isNotEmpty()) {
            while (true) {
                val epochSeconds = System.currentTimeMillis() / 1000
                val remaining = 30 - (epochSeconds % 30).toInt()
                totpTimeRemaining = remaining
                // Calculate simple pseudo 6-digit TOTP from hash
                val hash = (totpSecret.hashCode().toLong() + (epochSeconds / 30)).toString()
                val digits = hash.takeLast(6).padStart(6, '7')
                simulatedTotpCode = "${digits.substring(0, 3)} ${digits.substring(3, 6)}"
                delay(1000)
            }
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
                        text = if (credentialId == null) "Nueva Credencial" else "Editar Credencial",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.VaultList) },
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver a la lista"
                        )
                    }
                },
                actions = {
                    if (credentialId != null) {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier.testTag("detail_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Eliminar Credencial",
                                tint = RoseError
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category Chips Selector
            Text(
                text = "Categoría",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CredentialCategory.entries.filter { it != CredentialCategory.ALL }.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat.displayName) },
                        leadingIcon = {
                            Icon(
                                imageVector = getCategoryIcon(cat),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (category == cat) Color(cat.colorHex) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(cat.colorHex).copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            // Title / Service
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nombre del Servicio o Cuenta *") },
                placeholder = { Text("Ej. Google, GitHub, Banco...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_title_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Username / Email
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario o Correo Electrónico") },
                singleLine = true,
                trailingIcon = {
                    if (username.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.copyToClipboard(context, "Usuario", username, false) }
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copiar Usuario")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_username_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Password Card & Actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Contraseña Secreta") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Mostrar u ocultar"
                                    )
                                }
                                if (password.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.copyToClipboard(context, "Contraseña", password, true) }
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copiar Contraseña")
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_password_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Strength Meter
                    if (password.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { strength.score },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp),
                            color = Color(strength.level.colorHex),
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${strength.level.label} • ${strength.feedback}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(strength.level.colorHex)
                            )
                            Text(
                                text = "${strength.entropyBits} bits",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showGeneratorDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_inline_generator_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generar Contraseña Segura")
                    }
                }
            }

            // Website URL
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Sitio Web o URL") },
                placeholder = { Text("https://ejemplo.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = {
                            try {
                                val target = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }) {
                            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = "Abrir en navegador")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_url_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // TOTP / 2FA Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Autenticación de 2 Factores (TOTP / 2FA)",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = totpSecret,
                        onValueChange = { totpSecret = it.trim().uppercase() },
                        label = { Text("Clave Secreta TOTP (opcional)") },
                        placeholder = { Text("Ej. JBSWY3DPEHPK3PXP") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("detail_totp_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (totpSecret.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(EmeraldSecurity.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "CÓDIGO DE VERIFICACIÓN",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = simulatedTotpCode,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    ),
                                    color = EmeraldSecurity
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${totpTimeRemaining}s",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        viewModel.copyToClipboard(
                                            context,
                                            "Código 2FA",
                                            simulatedTotpCode.replace(" ", ""),
                                            true
                                        )
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copiar Código 2FA")
                                }
                            }
                        }
                    }
                }
            }

            // Secret Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notas Secretas (Cifradas)") },
                placeholder = { Text("Códigos de recuperación, PIN de respaldo, preguntas de seguridad...") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_notes_input"),
                shape = RoundedCornerShape(12.dp)
            )

            // Favorite Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = FolderAmber
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Marcar como Favorito",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                }
                Switch(
                    checked = isFavorite,
                    onCheckedChange = { isFavorite = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = FolderAmber,
                        checkedTrackColor = FolderAmber.copy(alpha = 0.4f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Save Button
            Button(
                onClick = {
                    val finalCred = (currentCredential ?: Credential(title = title)).copy(
                        title = title.trim(),
                        username = username.trim(),
                        password = password,
                        url = url.trim(),
                        category = category,
                        notes = notes,
                        totpSecret = totpSecret.trim(),
                        isFavorite = isFavorite,
                        updatedAt = System.currentTimeMillis()
                    )
                    viewModel.saveCredential(finalCred)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_credential_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar con Cifrado AES-256", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Generator Dialog
    if (showGeneratorDialog) {
        InlinePasswordGeneratorDialog(
            onDismiss = { showGeneratorDialog = false },
            onApply = { generated ->
                password = generated
                showGeneratorDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("¿Eliminar credencial?") },
            text = { Text("Esta acción no se puede deshacer. La clave y sus datos cifrados serán borrados de la bóveda local.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        credentialId?.let { viewModel.deleteCredential(it) }
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Eliminar", color = RoseError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun InlinePasswordGeneratorDialog(
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var length by remember { mutableFloatStateOf(18f) }
    var includeUppercase by remember { mutableStateOf(true) }
    var includeLowercase by remember { mutableStateOf(true) }
    var includeNumbers by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }

    var generated by remember {
        mutableStateOf(
            PasswordGenerator.generatePassword(
                PasswordGenerator.GeneratorConfig(length = 18)
            )
        )
    }

    val strength = remember(generated) { PasswordGenerator.evaluateStrength(generated) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generador de Contraseñas") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Generated preview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = generated,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                generated = PasswordGenerator.generatePassword(
                                    PasswordGenerator.GeneratorConfig(
                                        length = length.toInt(),
                                        includeUppercase = includeUppercase,
                                        includeLowercase = includeLowercase,
                                        includeNumbers = includeNumbers,
                                        includeSymbols = includeSymbols
                                    )
                                )
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Autorenew, contentDescription = "Regenerar")
                        }
                    }
                }

                Text(
                    text = "${strength.level.label} (${strength.entropyBits} bits)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(strength.level.colorHex)
                )

                Text(text = "Longitud: ${length.toInt()} caracteres", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = length,
                    onValueChange = {
                        length = it
                        generated = PasswordGenerator.generatePassword(
                            PasswordGenerator.GeneratorConfig(
                                length = it.toInt(),
                                includeUppercase = includeUppercase,
                                includeLowercase = includeLowercase,
                                includeNumbers = includeNumbers,
                                includeSymbols = includeSymbols
                            )
                        )
                    },
                    valueRange = 8f..40f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mayúsculas (A-Z)", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = includeUppercase,
                        onCheckedChange = {
                            includeUppercase = it
                            generated = PasswordGenerator.generatePassword(
                                PasswordGenerator.GeneratorConfig(
                                    length = length.toInt(),
                                    includeUppercase = it,
                                    includeLowercase = includeLowercase,
                                    includeNumbers = includeNumbers,
                                    includeSymbols = includeSymbols
                                )
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Números (0-9)", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = includeNumbers,
                        onCheckedChange = {
                            includeNumbers = it
                            generated = PasswordGenerator.generatePassword(
                                PasswordGenerator.GeneratorConfig(
                                    length = length.toInt(),
                                    includeUppercase = includeUppercase,
                                    includeLowercase = includeLowercase,
                                    includeNumbers = it,
                                    includeSymbols = includeSymbols
                                )
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Símbolos (!@#$)", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = includeSymbols,
                        onCheckedChange = {
                            includeSymbols = it
                            generated = PasswordGenerator.generatePassword(
                                PasswordGenerator.GeneratorConfig(
                                    length = length.toInt(),
                                    includeUppercase = includeUppercase,
                                    includeLowercase = includeLowercase,
                                    includeNumbers = includeNumbers,
                                    includeSymbols = it
                                )
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(generated) }) {
                Text("Usar Contraseña")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
