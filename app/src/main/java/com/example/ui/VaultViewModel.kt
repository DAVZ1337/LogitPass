package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VaultRepository
import com.example.data.local.VaultDatabase
import com.example.model.Credential
import com.example.model.CredentialCategory
import com.example.model.Screen
import com.example.model.VaultStats
import com.example.security.BiometricHelper
import com.example.security.CryptoManager
import com.example.security.PasswordGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val cryptoManager = CryptoManager(application)
    private val database = VaultDatabase.getInstance(application)
    private val repository = VaultRepository(database.credentialDao(), cryptoManager)
    private val prefs = application.getSharedPreferences("logitpass_ui_prefs", Context.MODE_PRIVATE)

    private val _isVaultInitialized = MutableStateFlow(cryptoManager.isVaultInitialized)
    val isVaultInitialized: StateFlow<Boolean> = _isVaultInitialized.asStateFlow()

    private val _isUnlocked = MutableStateFlow(cryptoManager.isUnlocked)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _unlockError = MutableStateFlow<String?>(null)
    val unlockError: StateFlow<String?> = _unlockError.asStateFlow()

    private val _isDerivingKey = MutableStateFlow(false)
    val isDerivingKey: StateFlow<Boolean> = _isDerivingKey.asStateFlow()

    val isBiometricAvailable: Boolean = BiometricHelper.isBiometricAvailable(application)
    val isBiometricEnabled: Boolean get() = cryptoManager.isBiometricEnabled

    private val _currentScreen = MutableStateFlow<Screen>(Screen.VaultList)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(CredentialCategory.ALL)
    val selectedCategory: StateFlow<CredentialCategory> = _selectedCategory.asStateFlow()

    private val _showOnlyFavorites = MutableStateFlow(false)
    val showOnlyFavorites: StateFlow<Boolean> = _showOnlyFavorites.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("pref_dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _editingCredential = MutableStateFlow<Credential?>(null)
    val editingCredential: StateFlow<Credential?> = _editingCredential.asStateFlow()

    // Configuración del generador de contraseñas
    private val _generatorConfig = MutableStateFlow(PasswordGenerator.GeneratorConfig())
    val generatorConfig: StateFlow<PasswordGenerator.GeneratorConfig> = _generatorConfig.asStateFlow()

    private val _generatedPassword = MutableStateFlow(
        PasswordGenerator.generatePassword(PasswordGenerator.GeneratorConfig())
    )
    val generatedPassword: StateFlow<String> = _generatedPassword.asStateFlow()

    val allCredentialsFlow = repository.getDecryptedCredentialsFlow()

    val filteredCredentials: StateFlow<List<Credential>> = combine(
        allCredentialsFlow,
        _searchQuery,
        _selectedCategory,
        _showOnlyFavorites
    ) { credentials, query, category, onlyFavorites ->
        credentials.filter { item ->
            val matchesCategory = (category == CredentialCategory.ALL) || (item.category == category)
            val matchesFavorite = !onlyFavorites || item.isFavorite
            val matchesQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.username.contains(query, ignoreCase = true) ||
                    item.url.contains(query, ignoreCase = true) ||
                    item.notes.contains(query, ignoreCase = true)
            matchesCategory && matchesFavorite && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultStats: StateFlow<VaultStats> = allCredentialsFlow.combine(_searchQuery) { credentials, _ ->
        val total = credentials.size
        var weak = 0
        var reused = 0
        var favs = 0

        val passwordsCount = mutableMapOf<String, Int>()
        for (item in credentials) {
            if (item.isFavorite) favs++
            if (item.password.isNotEmpty()) {
                val strength = PasswordGenerator.evaluateStrength(item.password)
                if (strength.level == PasswordGenerator.StrengthLevel.VERY_WEAK ||
                    strength.level == PasswordGenerator.StrengthLevel.WEAK
                ) {
                    weak++
                }
                passwordsCount[item.password] = (passwordsCount[item.password] ?: 0) + 1
            }
        }

        reused = passwordsCount.values.count { it > 1 }

        val score = if (total == 0) 100 else {
            val penaltyWeak = (weak * 25) / total
            val penaltyReused = (reused * 25) / total
            (100 - penaltyWeak - penaltyReused).coerceIn(10, 100)
        }

        VaultStats(
            totalCount = total,
            weakCount = weak,
            reusedCount = reused,
            favoritesCount = favs,
            securityScore = score
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VaultStats())

    fun setupVault(name: String, masterPassword: String, enableBiometric: Boolean) {
        val clean = masterPassword.trim()
        if (clean.length < 4) {
            _unlockError.value = "La contraseña maestra debe tener al menos 4 caracteres"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isDerivingKey.value = true
            val success = cryptoManager.setupVault(name, clean, enableBiometric)
            withContext(Dispatchers.Main) {
                _isDerivingKey.value = false
                if (success) {
                    _isVaultInitialized.value = true
                    _isUnlocked.value = true
                    _unlockError.value = null
                    _currentScreen.value = Screen.VaultList
                } else {
                    _unlockError.value = "Error al inicializar la bóveda"
                }
            }
        }
    }

    fun unlockWithPassword(masterPassword: String) {
        val clean = masterPassword.trim()
        viewModelScope.launch(Dispatchers.IO) {
            _isDerivingKey.value = true
            val success = cryptoManager.unlockWithPassword(clean)
            withContext(Dispatchers.Main) {
                _isDerivingKey.value = false
                if (success) {
                    _isUnlocked.value = true
                    _unlockError.value = null
                    _currentScreen.value = Screen.VaultList
                } else {
                    _unlockError.value = "Contraseña maestra incorrecta"
                }
            }
        }
    }

    fun resetVault() {
        viewModelScope.launch(Dispatchers.IO) {
            cryptoManager.resetVault()
            database.credentialDao().deleteAll()
            withContext(Dispatchers.Main) {
                _isVaultInitialized.value = false
                _isUnlocked.value = false
                _unlockError.value = null
                _currentScreen.value = Screen.VaultList
                _statusMessage.value = "Bóveda restablecida. Ahora puedes crear una nueva."
            }
        }
    }

    fun unlockWithBiometric() {
        viewModelScope.launch(Dispatchers.IO) {
            val success = cryptoManager.unlockWithBiometric()
            withContext(Dispatchers.Main) {
                if (success) {
                    _isUnlocked.value = true
                    _unlockError.value = null
                    _currentScreen.value = Screen.VaultList
                } else {
                    _unlockError.value = "No se pudo autenticar con biometría"
                }
            }
        }
    }

    fun lockVault() {
        cryptoManager.lockVault()
        _isUnlocked.value = false
        _currentScreen.value = Screen.VaultList
        _editingCredential.value = null
    }

    fun clearUnlockError() {
        _unlockError.value = null
    }

    fun navigateTo(screen: Screen) {
        if (screen is Screen.CredentialDetail) {
            loadCredentialForEdit(screen.credentialId)
        }
        _currentScreen.value = screen
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: CredentialCategory) {
        _selectedCategory.value = category
    }

    fun toggleFavoritesOnly() {
        _showOnlyFavorites.value = !_showOnlyFavorites.value
    }

    fun toggleFavorite(id: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(id, !isFavorite)
        }
    }

    fun loadCredentialForEdit(credentialId: String?) {
        if (credentialId == null) {
            _editingCredential.value = Credential(title = "")
        } else {
            viewModelScope.launch {
                val cred = repository.getDecryptedCredentialById(credentialId)
                _editingCredential.value = cred ?: Credential(title = "")
            }
        }
    }

    fun saveCredential(credential: Credential) {
        if (credential.title.isBlank()) {
            _statusMessage.value = "El título del servicio no puede estar vacío"
            return
        }
        viewModelScope.launch {
            try {
                repository.saveCredential(credential)
                _statusMessage.value = "Credencial guardada con cifrado AES-256"
                _currentScreen.value = Screen.VaultList
                _editingCredential.value = null
            } catch (e: Exception) {
                _statusMessage.value = "Error al guardar: ${e.localizedMessage}"
            }
        }
    }

    fun deleteCredential(id: String) {
        viewModelScope.launch {
            repository.deleteCredential(id)
            _statusMessage.value = "Credencial eliminada"
            _currentScreen.value = Screen.VaultList
            _editingCredential.value = null
        }
    }

    fun updateGeneratorConfig(newConfig: PasswordGenerator.GeneratorConfig) {
        _generatorConfig.value = newConfig
        _generatedPassword.value = PasswordGenerator.generatePassword(newConfig)
    }

    fun regeneratePassword() {
        _generatedPassword.value = PasswordGenerator.generatePassword(_generatorConfig.value)
    }

    fun generatePassphrase(words: Int = 4) {
        _generatedPassword.value = PasswordGenerator.generatePassphrase(wordCount = words)
    }

    fun copyToClipboard(context: Context, label: String, text: String, isSensitive: Boolean = true) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (isSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
        clipboard.setPrimaryClip(clip)
        _statusMessage.value = "$label copiado al portapapeles"
    }

    fun changeMasterPassword(oldPass: String, newPass: String): Boolean {
        val cleanNew = newPass.trim()
        if (cleanNew.length < 6) {
            _statusMessage.value = "La nueva contraseña debe tener al menos 6 caracteres"
            return false
        }
        val oldClean = oldPass.trim()
        val oldValid = cryptoManager.unlockWithPassword(oldClean)
        if (!oldValid) {
            _statusMessage.value = "Contraseña actual incorrecta"
            return false
        }

        val oldKey = cryptoManager.getActiveSessionKey() ?: return false

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (newKey, newSalt) = cryptoManager.deriveKeyFromPassword(cleanNew)
                repository.reencryptAllCredentials(oldKey, newKey)
                val updated = cryptoManager.updateVaultKey(newKey, newSalt)
                withContext(Dispatchers.Main) {
                    if (updated) {
                        _statusMessage.value = "Contraseña maestra actualizada y credenciales recifradas"
                    } else {
                        _statusMessage.value = "Error al actualizar la contraseña de la bóveda"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _statusMessage.value = "Error al recifrar las credenciales: ${e.message}"
                }
            }
        }
        return true
    }

    fun toggleBiometricSetting(enabled: Boolean) {
        cryptoManager.setBiometricEnabled(enabled)
        _statusMessage.value = if (enabled) "Desbloqueo biométrico activado" else "Desbloqueo biométrico desactivado"
    }

    fun toggleDarkMode() {
        val next = !_isDarkMode.value
        _isDarkMode.value = next
        prefs.edit().putBoolean("pref_dark_mode", next).apply()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
