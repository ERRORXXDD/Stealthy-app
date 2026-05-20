package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.*
import com.example.camera.SecretCameraHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    
    private val db = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "secure_vault.db"
    ).fallbackToDestructiveMigration().build()
    
    private val repository = VaultRepository(application, db)
    
    val normalNotes: StateFlow<List<NormalNote>> = repository.normalNotes
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val intruderLogs: StateFlow<List<IntruderLog>> = repository.intruderLogs
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val failedAttempts = MutableStateFlow(0)
        
    val isFakeMode = MutableStateFlow(false)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val secretPhotos: StateFlow<List<SecretPhoto>> = isFakeMode
        .flatMapLatest { isFake ->
            db.vaultDao().getSecretPhotos(isDecoy = isFake)
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val secretFiles: StateFlow<List<SecretFile>> = isFakeMode
        .flatMapLatest { isFake ->
            db.vaultDao().getSecretFiles(isDecoy = isFake)
                .catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isLocked = MutableStateFlow(false)
    val hasCompletedOnboarding = MutableStateFlow(false)
    val configuredGesture = MutableStateFlow("")
    val configuredPin = MutableStateFlow("")
    
    // Fake decoy vault settings state
    val hasCompletedFakeOnboarding = MutableStateFlow(false)
    val configuredFakeGesture = MutableStateFlow("")
    val configuredFakePin = MutableStateFlow("")

    // Security Options states togglers
    val isBiometricEnabled = MutableStateFlow(false)
    val isStealthModeEnabled = MutableStateFlow(false)

    // Cloud Backup Option states
    val selectedBackupProvider = MutableStateFlow("Google Drive")
    val isAutoBackupLogEnabled = MutableStateFlow(false)
    val autoBackupFrequency = MutableStateFlow("Daily")
    val cloudLinkedAccount = MutableStateFlow("")
    val lastBackupInfoState = MutableStateFlow<com.example.backup.BackupMetadata?>(null)
    
    val operationStatus = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val setup = repository.getSetting("has_setup") == "true"
            hasCompletedOnboarding.value = setup
            
            val fakeSetup = repository.getSetting("has_fake_setup") == "true"
            hasCompletedFakeOnboarding.value = fakeSetup
            
            configuredGesture.value = repository.getSetting("gesture_path") ?: ""
            configuredPin.value = repository.getSetting("backup_pin") ?: ""
            
            configuredFakeGesture.value = repository.getSetting("fake_gesture_path") ?: ""
            configuredFakePin.value = repository.getSetting("fake_backup_pin") ?: ""

            isBiometricEnabled.value = repository.getSetting("is_biometric_enabled") == "true"
            isStealthModeEnabled.value = repository.getSetting("is_stealth_mode_enabled") == "true"

            // Load backup and cloud sync states
            val provider = repository.getSetting("backup_provider") ?: "Google Drive"
            selectedBackupProvider.value = provider
            isAutoBackupLogEnabled.value = repository.getSetting("is_auto_backup_enabled") == "true"
            autoBackupFrequency.value = repository.getSetting("auto_backup_frequency") ?: "Daily"
            cloudLinkedAccount.value = repository.getSetting("cloud_linked_account") ?: ""
            refreshBackupInfo()

            // The app launches locked if setup is complete
            isLocked.value = setup

            // Handle silent background auto-backup if scheduled and due
            if (setup && isAutoBackupLogEnabled.value) {
                val lastTimeStr = repository.getSetting("last_backup_timestamp")
                val freq = autoBackupFrequency.value
                val threshold = if (freq == "Daily") 24 * 60 * 60 * 1000L else 7 * 24 * 60 * 60 * 1000L
                val lastTime = lastTimeStr?.toLongOrNull() ?: 0L
                if (System.currentTimeMillis() - lastTime > threshold) {
                    val pass = getEncryptionPassphrase()
                    com.example.backup.BackupManager.performBackup(
                        context = getApplication(),
                        provider = provider,
                        passphrase = pass,
                        onSuccess = { ts, size ->
                            viewModelScope.launch {
                                repository.saveSetting("last_backup_timestamp", ts.toString())
                                repository.saveSetting("last_backup_size", size)
                                refreshBackupInfo()
                            }
                        },
                        onError = { /* Quietly ignore for background automatic backups */ }
                    )
                }
            }
        }
    }

    fun addNormalNote(title: String, content: String) {
        viewModelScope.launch {
            repository.insertNormalNote(NormalNote(title = title, content = content))
        }
    }

    fun deleteNormalNote(note: NormalNote) {
        viewModelScope.launch {
            repository.deleteNormalNote(note)
        }
    }

    fun setPatternGesture(pattern: List<Int>, isDecoy: Boolean = false) {
        viewModelScope.launch {
            val gestureStr = pattern.joinToString(",")
            val key = if (isDecoy) "fake_gesture_path" else "gesture_path"
            repository.saveSetting(key, gestureStr)
            if (isDecoy) {
                configuredFakeGesture.value = gestureStr
                repository.saveSetting("has_fake_setup", "true")
                hasCompletedFakeOnboarding.value = true
                operationStatus.value = "Decoy Gesture saved successfully!"
            } else {
                configuredGesture.value = gestureStr
            }
        }
    }

    fun setBackupPin(pin: String, isDecoy: Boolean = false) {
        viewModelScope.launch {
            val hashedPin = pin.sha256()
            val key = if (isDecoy) "fake_backup_pin" else "backup_pin"
            repository.saveSetting(key, hashedPin)
            if (isDecoy) {
                configuredFakePin.value = hashedPin
                repository.saveSetting("has_fake_setup", "true")
                hasCompletedFakeOnboarding.value = true
                operationStatus.value = "Decoy Security PIN saved!"
            } else {
                configuredPin.value = hashedPin
            }
        }
    }

    fun setBiometricToggle(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("is_biometric_enabled", enabled.toString())
            isBiometricEnabled.value = enabled
            operationStatus.value = if (enabled) "Biometric validation active!" else "Biometric validation disabled."
        }
    }

    fun setStealthModeToggle(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("is_stealth_mode_enabled", enabled.toString())
            isStealthModeEnabled.value = enabled
            operationStatus.value = if (enabled) "Stealth Mode active! Icon disguised." else "Stealth Mode off."
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.saveSetting("has_setup", "true")
            hasCompletedOnboarding.value = true
            isLocked.value = false // start unlocked after setup
        }
    }

    private fun triggerIntruderSelfie(type: String) {
        SecretCameraHelper.takeSecretSelfie(
            context = getApplication(),
            onPhotoCaptured = { photoPath ->
                viewModelScope.launch {
                    repository.insertIntruderLog(
                        IntruderLog(
                            imagePath = photoPath,
                            attemptType = type,
                            enteredValue = "3 Incorrect Attempts"
                        )
                    )
                }
            },
            onError = { error ->
                android.util.Log.e("VaultViewModel", "Selfie capture failed: $error")
                viewModelScope.launch {
                    repository.insertIntruderLog(
                        IntruderLog(
                            imagePath = null,
                            attemptType = type,
                            enteredValue = "3 Incorrect Attempts (No Camera: $error)"
                        )
                    )
                }
            }
        )
    }

    fun deleteIntruderLog(log: IntruderLog) {
        viewModelScope.launch {
            repository.deleteIntruderLog(log)
            operationStatus.value = "Log record deleted."
        }
    }

    fun clearAllIntruderLogs() {
        viewModelScope.launch {
            repository.clearAllIntruderLogs()
            operationStatus.value = "All intruder logs cleared safely."
        }
    }

    fun verifyPatternGesture(pattern: List<Int>): Boolean {
        val inputStr = pattern.joinToString(",")
        return when {
            inputStr == configuredGesture.value -> {
                failedAttempts.value = 0
                isFakeMode.value = false
                isLocked.value = false
                true
            }
            hasCompletedFakeOnboarding.value && inputStr == configuredFakeGesture.value -> {
                failedAttempts.value = 0
                isFakeMode.value = true
                isLocked.value = false
                operationStatus.value = "Decoy secure area entered."
                true
            }
            else -> {
                val current = failedAttempts.value + 1
                if (current >= 3) {
                    failedAttempts.value = 0
                    triggerIntruderSelfie("Pattern Swipe")
                } else {
                    failedAttempts.value = current
                }
                false
            }
        }
    }

    fun verifyBackupPin(pin: String): Boolean {
        val inputHashed = pin.sha256()
        return when {
            inputHashed == configuredPin.value -> {
                failedAttempts.value = 0
                isFakeMode.value = false
                isLocked.value = false
                true
            }
            hasCompletedFakeOnboarding.value && inputHashed == configuredFakePin.value -> {
                failedAttempts.value = 0
                isFakeMode.value = true
                isLocked.value = false
                operationStatus.value = "Decoy secure area entered."
                true
            }
            else -> {
                val current = failedAttempts.value + 1
                if (current >= 3) {
                    failedAttempts.value = 0
                    triggerIntruderSelfie("Backup PIN")
                } else {
                    failedAttempts.value = current
                }
                false
            }
        }
    }

    fun unlockViaBiometric() {
        isFakeMode.value = false
        isLocked.value = false
        operationStatus.value = "Biometric validation successful!"
    }

    fun lock() {
        isLocked.value = true
    }

    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            operationStatus.value = "Importing image..."
            val success = repository.importPhoto(uri, isDecoy = isFakeMode.value)
            operationStatus.value = if (success) "Image imported successfully!" else "Failed to import image."
        }
    }

    fun deletePhoto(photo: SecretPhoto) {
        viewModelScope.launch {
            repository.deletePhoto(photo)
            operationStatus.value = "Image deleted from vault."
        }
    }

    fun importFile(uri: Uri) {
        viewModelScope.launch {
            operationStatus.value = "Importing file..."
            val success = repository.importFile(uri, isDecoy = isFakeMode.value)
            operationStatus.value = if (success) "File imported successfully!" else "Failed to import file."
        }
    }

    fun deleteFile(file: SecretFile) {
        viewModelScope.launch {
            repository.deleteFile(file)
            operationStatus.value = "File deleted from vault."
        }
    }

    // -------------- SECURE CLOUD BACKUP UTILITIES --------------
    fun refreshBackupInfo() {
        val provider = selectedBackupProvider.value
        lastBackupInfoState.value = com.example.backup.BackupManager.getLatestBackupInfo(getApplication(), provider)
    }

    fun setBackupProvider(provider: String) {
        viewModelScope.launch {
            repository.saveSetting("backup_provider", provider)
            selectedBackupProvider.value = provider
            refreshBackupInfo()
            operationStatus.value = "Selected Provider: $provider"
        }
    }

    fun setAutoBackupToggle(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveSetting("is_auto_backup_enabled", enabled.toString())
            isAutoBackupLogEnabled.value = enabled
            operationStatus.value = if (enabled) "Auto cloud sync active!" else "Auto cloud sync disabled."
        }
    }

    fun setAutoBackupFrequency(frequency: String) {
        viewModelScope.launch {
            repository.saveSetting("auto_backup_frequency", frequency)
            autoBackupFrequency.value = frequency
            operationStatus.value = "Frequency updated to $frequency"
        }
    }

    fun setCloudLinkedAccount(email: String) {
        viewModelScope.launch {
            repository.saveSetting("cloud_linked_account", email)
            cloudLinkedAccount.value = email
            operationStatus.value = "Linked account: $email"
        }
    }

    fun performManualBackup() {
        val passphrase = getEncryptionPassphrase()
        val provider = selectedBackupProvider.value
        operationStatus.value = "Encrypting and syncing with $provider..."
        
        com.example.backup.BackupManager.performBackup(
            context = getApplication(),
            provider = provider,
            passphrase = passphrase,
            onSuccess = { timestamp, size ->
                viewModelScope.launch {
                    repository.saveSetting("last_backup_timestamp", timestamp.toString())
                    repository.saveSetting("last_backup_size", size)
                    refreshBackupInfo()
                    operationStatus.value = "Backup successfully encrypted & uploaded to $provider!"
                }
            },
            onError = { err ->
                operationStatus.value = "Cloud backup failed: $err"
            }
        )
    }

    fun performManualRestore(context: android.content.Context) {
        val passphrase = getEncryptionPassphrase()
        val provider = selectedBackupProvider.value
        operationStatus.value = "Downloading and decrypting $provider backup..."
        
        viewModelScope.launch {
            db.close() // Close DB in main scope before zip-swap
            
            com.example.backup.BackupManager.performRestore(
                context = getApplication(),
                provider = provider,
                passphrase = passphrase,
                onSuccess = {
                    operationStatus.value = "Decryption succeeded! Reinstate secure area..."
                    
                    // Relaunch app cleanly so the restored room DB initializes afresh
                    val pm = context.packageManager
                    val intent = pm.getLaunchIntentForPackage(context.packageName)
                    if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(intent)
                    }
                    android.os.Process.killProcess(android.os.Process.myPid())
                },
                onError = { err ->
                    // Reopen the database if decrypt failed so UI remains responsive
                    operationStatus.value = "Restore failed: $err"
                }
            )
        }
    }

    private fun getEncryptionPassphrase(): String {
        val pin = configuredPin.value
        val gesture = configuredGesture.value
        return when {
            pin.isNotEmpty() -> pin
            gesture.isNotEmpty() -> gesture
            else -> "default_secure_vault_key_pass"
        }
    }

    fun clearOperationStatus() {
        operationStatus.value = null
    }

    private fun String.sha256(): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(this.toByteArray(Charsets.UTF_8))
            hash.fold("") { str, it -> str + "%02x".format(it) }
        } catch (e: Exception) {
            this // fallback
        }
    }
}
