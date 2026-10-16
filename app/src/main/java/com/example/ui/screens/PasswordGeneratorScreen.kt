package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Screen
import com.example.security.PasswordGenerator
import com.example.ui.VaultViewModel
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldSecurity
import com.example.ui.theme.FolderAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen(
    viewModel: VaultViewModel
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Password, 1: Passphrase

    var length by remember { mutableFloatStateOf(20f) }
    var includeUpper by remember { mutableStateOf(true) }
    var includeLower by remember { mutableStateOf(true) }
    var includeNumbers by remember { mutableStateOf(true) }
    var includeSymbols by remember { mutableStateOf(true) }
    var excludeAmbiguous by remember { mutableStateOf(true) }

    var passphraseWords by remember { mutableFloatStateOf(4f) }
    var separator by remember { mutableStateOf("-") }
    var includeNumberInPassphrase by remember { mutableStateOf(true) }

    var generatedValue by remember {
        mutableStateOf(
            PasswordGenerator.generatePassword(
                PasswordGenerator.GeneratorConfig(length = 20)
            )
        )
    }

    val strength = remember(generatedValue) { PasswordGenerator.evaluateStrength(generatedValue) }

    fun refreshPassword() {
        if (selectedTab == 0) {
            generatedValue = PasswordGenerator.generatePassword(
                PasswordGenerator.GeneratorConfig(
                    length = length.toInt(),
                    includeUppercase = includeUpper,
                    includeLowercase = includeLower,
                    includeNumbers = includeNumbers,
                    includeSymbols = includeSymbols,
                    excludeAmbiguous = excludeAmbiguous
                )
            )
        } else {
            generatedValue = PasswordGenerator.generatePassphrase(
                wordCount = passphraseWords.toInt(),
                separator = separator,
                includeNumber = includeNumberInPassphrase
            )
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
                        text = "Generador de Contraseñas",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.VaultList) },
                        modifier = Modifier.testTag("generator_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
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
            // Mode Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        refreshPassword()
                    },
                    text = { Text("Contraseña") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        refreshPassword()
                    },
                    text = { Text("Frase de Paso") }
                )
            }

            // Big Preview Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = generatedValue,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            lineHeight = 26.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .testTag("generated_password_display")
                    )

                    // Strength Progress
                    LinearProgressIndicator(
                        progress = { strength.score },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = Color(strength.level.colorHex),
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

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
                            text = "${strength.entropyBits} bits de entropía",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.copyToClipboard(context, "Contraseña Generada", generatedValue, true)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("copy_generated_password_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSecurity),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copiar Clave")
                        }

                        OutlinedButton(
                            onClick = { refreshPassword() },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("regenerate_password_button"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Regenerar")
                        }
                    }
                }
            }

            // Presets Row
            Text(
                text = "Plantillas Rápidas",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        selectedTab = 0
                        length = 16f
                        includeUpper = true
                        includeLower = true
                        includeNumbers = true
                        includeSymbols = true
                        refreshPassword()
                    },
                    label = { Text("Estándar (16c)") }
                )
                AssistChip(
                    onClick = {
                        selectedTab = 0
                        length = 28f
                        includeUpper = true
                        includeLower = true
                        includeNumbers = true
                        includeSymbols = true
                        refreshPassword()
                    },
                    label = { Text("Militar (28c)") }
                )
                AssistChip(
                    onClick = {
                        selectedTab = 0
                        length = 6f
                        includeUpper = false
                        includeLower = false
                        includeNumbers = true
                        includeSymbols = false
                        refreshPassword()
                    },
                    label = { Text("PIN Numérico (6d)") }
                )
                AssistChip(
                    onClick = {
                        selectedTab = 1
                        passphraseWords = 5f
                        separator = "-"
                        includeNumberInPassphrase = true
                        refreshPassword()
                    },
                    label = { Text("Frase 5 Palabras") }
                )
            }

            // Options based on Tab
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (selectedTab == 0) {
                        // Password Sliders & Toggles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Longitud de Caracteres", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text("${length.toInt()}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Slider(
                            value = length,
                            onValueChange = {
                                length = it
                                refreshPassword()
                            },
                            valueRange = 8f..64f
                        )

                        ToggleOptionRow(
                            title = "Mayúsculas (A-Z)",
                            checked = includeUpper,
                            onCheckedChange = {
                                includeUpper = it
                                refreshPassword()
                            }
                        )

                        ToggleOptionRow(
                            title = "Minúsculas (a-z)",
                            checked = includeLower,
                            onCheckedChange = {
                                includeLower = it
                                refreshPassword()
                            }
                        )

                        ToggleOptionRow(
                            title = "Números (0-9)",
                            checked = includeNumbers,
                            onCheckedChange = {
                                includeNumbers = it
                                refreshPassword()
                            }
                        )

                        ToggleOptionRow(
                            title = "Símbolos Especiales (!@#$%^&*)",
                            checked = includeSymbols,
                            onCheckedChange = {
                                includeSymbols = it
                                refreshPassword()
                            }
                        )

                        ToggleOptionRow(
                            title = "Evitar confusos (l, 1, I, O, 0)",
                            checked = excludeAmbiguous,
                            onCheckedChange = {
                                excludeAmbiguous = it
                                refreshPassword()
                            }
                        )
                    } else {
                        // Passphrase Options
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cantidad de Palabras", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text("${passphraseWords.toInt()}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Slider(
                            value = passphraseWords,
                            onValueChange = {
                                passphraseWords = it
                                refreshPassword()
                            },
                            valueRange = 3f..8f
                        )

                        ToggleOptionRow(
                            title = "Incluir Número Aleatorio",
                            checked = includeNumberInPassphrase,
                            onCheckedChange = {
                                includeNumberInPassphrase = it
                                refreshPassword()
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Separador", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("-", "_", ".", " ").forEach { sep ->
                                    AssistChip(
                                        onClick = {
                                            separator = sep
                                            refreshPassword()
                                        },
                                        label = { Text(if (sep == " ") "Espacio" else sep) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleOptionRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = EmeraldSecurity,
                checkedTrackColor = EmeraldSecurity.copy(alpha = 0.4f)
            )
        )
    }
}
