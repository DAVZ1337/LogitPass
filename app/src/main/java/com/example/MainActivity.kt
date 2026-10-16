package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.Screen
import com.example.ui.VaultViewModel
import com.example.ui.screens.CredentialDetailScreen
import com.example.ui.screens.LockScreen
import com.example.ui.screens.PasswordGeneratorScreen
import com.example.ui.screens.SecurityAuditScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VaultListScreen
import com.example.ui.theme.LogitPassTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Nota: FLAG_SECURE bloquea la captura de pantalla en emuladores y transmisiones remotas,
        // provocando pantalla negra en el streaming emulator. Por eso se mantiene desactivado en este entorno.

        setContent {
            val viewModel: VaultViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            LogitPassTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    LogitPassApp(activity = this@MainActivity, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun LogitPassApp(
    activity: FragmentActivity,
    viewModel: VaultViewModel
) {
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()

    if (!isUnlocked) {
        LockScreen(activity = activity, viewModel = viewModel)
    } else {
        Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
            when (screen) {
                is Screen.VaultList -> VaultListScreen(viewModel = viewModel)
                is Screen.CredentialDetail -> CredentialDetailScreen(
                    credentialId = screen.credentialId,
                    viewModel = viewModel
                )
                is Screen.PasswordGenerator -> PasswordGeneratorScreen(viewModel = viewModel)
                is Screen.SecurityAudit -> SecurityAuditScreen(viewModel = viewModel)
                is Screen.Settings -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
