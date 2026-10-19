package com.example.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MainActivity
import com.example.R
import com.example.model.Credential
import com.example.security.BiometricHelper
import com.example.ui.VaultViewModel
import com.example.ui.theme.EmeraldSecurity
import com.example.ui.theme.LogitPassTheme
import com.example.ui.theme.RoseError

@RequiresApi(Build.VERSION_CODES.O)
class AutofillPickerActivity : FragmentActivity() {

    private var usernameId: AutofillId? = null
    private var passwordId: AutofillId? = null
    private var targetPackage: String? = null
    private var targetDomain: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        usernameId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("extra_username_id", AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("extra_username_id")
        }

        passwordId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("extra_password_id", AutofillId::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("extra_password_id")
        }

        targetPackage = intent.getStringExtra("extra_target_package")
        targetDomain = intent.getStringExtra("extra_target_domain")

        setContent {
            val viewModel: VaultViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            LogitPassTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AutofillPickerContent(
                        activity = this@AutofillPickerActivity,
                        viewModel = viewModel,
                        targetPackage = targetPackage,
                        targetDomain = targetDomain,
                        onCredentialSelected = { credential ->
                            deliverAutofill(credential)
                        },
                        onCancel = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun deliverAutofill(credential: Credential) {
        val datasetBuilder = android.service.autofill.Dataset.Builder()

        val presentation = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
            setTextViewText(R.id.autofill_title, credential.title)
            setTextViewText(R.id.autofill_subtitle, credential.username.ifBlank { "Credencial lista" })
        }

        var valuesAdded = 0
        usernameId?.let { id ->
            val value = credential.username.ifBlank { credential.title }
            datasetBuilder.setValue(id, AutofillValue.forText(value), presentation)
            valuesAdded++
        }

        passwordId?.let { id ->
            if (credential.password.isNotEmpty()) {
                datasetBuilder.setValue(id, AutofillValue.forText(credential.password), presentation)
                valuesAdded++
            }
        }

        if (valuesAdded == 0) {
            val fallbackId = usernameId ?: passwordId
            if (fallbackId != null) {
                val value = credential.password.ifEmpty { credential.username }
                datasetBuilder.setValue(fallbackId, AutofillValue.forText(value), presentation)
                valuesAdded++
            }
        }

        if (valuesAdded > 0) {
            val dataset = datasetBuilder.build()
            val fillResponse = android.service.autofill.FillResponse.Builder().addDataset(dataset).build()
            val replyIntent = Intent().apply {
                putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, fillResponse)
                putExtra("extra_dataset", dataset)
            }
            setResult(Activity.RESULT_OK, replyIntent)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }
}

@Composable
fun AutofillPickerContent(
    activity: FragmentActivity,
    viewModel: VaultViewModel,
    targetPackage: String?,
    targetDomain: String?,
    onCredentialSelected: (Credential) -> Unit,
    onCancel: () -> Unit
) {
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val isVaultInitialized by viewModel.isVaultInitialized.collectAsState()
    val allCredentials by viewModel.allCredentialsFlow.collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    val unlockError by viewModel.unlockError.collectAsState()

    // Auto-prompt biometría si está disponible
    LaunchedEffect(Unit) {
        if (!isUnlocked && viewModel.isBiometricAvailable && viewModel.isBiometricEnabled) {
            BiometricHelper.showBiometricPrompt(
                activity = activity,
                title = "Autorrellenar con LogitPass",
                subtitle = "Usa tu huella o rostro para desbloquear tus contraseñas",
                onSuccess = { viewModel.unlockWithBiometric() },
                onError = { /* fallback manual */ }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        // Cabecera superior
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🔑", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Autorrellenar con LogitPass",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (!targetPackage.isNullOrBlank()) {
                        Text(
                            text = "Para: ${targetPackage.substringAfterLast('.')}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(onClick = onCancel) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isUnlocked) {
            // Vista de bloqueo con contraseña o huella
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(EmeraldSecurity.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = EmeraldSecurity,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Bóveda Bloqueada",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Introduce tu clave maestra para seleccionar una cuenta",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Contraseña Maestra") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (unlockError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = unlockError!!, color = RoseError, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (passwordInput.isNotEmpty()) {
                                viewModel.unlockWithPassword(passwordInput)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Desbloquear y Ver Cuentas")
                    }

                    if (viewModel.isBiometricAvailable && viewModel.isBiometricEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                BiometricHelper.showBiometricPrompt(
                                    activity = activity,
                                    onSuccess = { viewModel.unlockWithBiometric() },
                                    onError = {}
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = EmeraldSecurity)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Usar Huella Dactilar")
                        }
                    }
                }
            }
        } else {
            // Desbloqueado: Selector de credenciales
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar entre tus contraseñas...") },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val targetPkg = targetPackage?.lowercase() ?: ""
            val targetDom = targetDomain?.lowercase() ?: ""

            val filtered = remember(allCredentials, searchQuery) {
                if (searchQuery.isBlank()) {
                    allCredentials
                } else {
                    allCredentials.filter {
                        it.title.contains(searchQuery, ignoreCase = true) ||
                                it.username.contains(searchQuery, ignoreCase = true) ||
                                it.url.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            if (!isVaultInitialized) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "La bóveda aún no está configurada",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                activity.startActivity(Intent(activity, MainActivity::class.java))
                                activity.finish()
                            }
                        ) {
                            Text("Abrir LogitPass")
                        }
                    }
                }
            } else if (allCredentials.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Aún no tienes cuentas guardadas en tu bóveda",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                activity.startActivity(Intent(activity, MainActivity::class.java))
                                activity.finish()
                            }
                        ) {
                            Text("Abrir LogitPass para añadir cuentas")
                        }
                    }
                }
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se encontraron contraseñas que coincidan con \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Separar sugeridas de la app actual y el resto
                val suggested = remember(filtered, targetPkg, targetDom) {
                    if (targetPkg.isEmpty() && targetDom.isEmpty()) emptyList() else {
                        filtered.filter {
                            val combined = "${it.title} ${it.username} ${it.url}".lowercase()
                            (targetDom.isNotEmpty() && combined.contains(targetDom)) ||
                                    (targetPkg.isNotEmpty() && (combined.contains(targetPkg) || targetPkg.contains(it.title.lowercase())))
                        }
                    }
                }

                val others = remember(filtered, suggested) {
                    filtered.filter { item -> suggested.none { it.id == item.id } }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (suggested.isNotEmpty()) {
                        item {
                            Text(
                                text = "✨ Coincidencias sugeridas",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldSecurity,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(suggested, key = { "sug_${it.id}" }) { item ->
                            CredentialPickerCard(item = item, onSelect = { onCredentialSelected(item) }, isSuggested = true)
                        }
                        if (others.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Todas tus contraseñas",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }

                    items(others, key = { it.id }) { item ->
                        CredentialPickerCard(item = item, onSelect = { onCredentialSelected(item) }, isSuggested = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialPickerCard(
    item: Credential,
    onSelect: () -> Unit,
    isSuggested: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuggested) EmeraldSecurity.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isSuggested) EmeraldSecurity.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(EmeraldSecurity.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = EmeraldSecurity,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                if (item.username.isNotEmpty()) {
                    Text(
                        text = item.username,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onSelect,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Rellenar", fontSize = 12.sp)
            }
        }
    }
}
