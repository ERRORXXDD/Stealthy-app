package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.DisguiseNotesScreen
import com.example.ui.GestureUnlockScreen
import com.example.ui.OnboardingSetupScreen
import com.example.ui.VaultDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VaultViewModel
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainVaultApp()
            }
        }
    }
}

@Composable
fun MainVaultApp() {
    val viewModel: VaultViewModel = viewModel()
    
    val notes by viewModel.normalNotes.collectAsState()
    val photos by viewModel.secretPhotos.collectAsState()
    val files by viewModel.secretFiles.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val hasSetup by viewModel.hasCompletedOnboarding.collectAsState()
    val statusMsg by viewModel.operationStatus.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()
    val isStealthModeEnabled by viewModel.isStealthModeEnabled.collectAsState()
    val hasCompletedFakeOnboarding by viewModel.hasCompletedFakeOnboarding.collectAsState()
    
    // Backup & Cloud sync states
    val selectedBackupProvider by viewModel.selectedBackupProvider.collectAsState()
    val isAutoBackupLogEnabled by viewModel.isAutoBackupLogEnabled.collectAsState()
    val autoBackupFrequency by viewModel.autoBackupFrequency.collectAsState()
    val cloudLinkedAccount by viewModel.cloudLinkedAccount.collectAsState()
    val lastBackupInfoState by viewModel.lastBackupInfoState.collectAsState()
    
    val context = LocalContext.current
    
    // Active Screen state coordinator: "NORMAL", "ONBOARDING", "UNLOCK", "VAULT"
    var activeScreen by remember { mutableStateOf("NORMAL") }

    // Synchronize lock state updates with routing navigation
    LaunchedEffect(isLocked) {
        if (isLocked && hasSetup) {
            activeScreen = "NORMAL"
        } else if (!isLocked && hasSetup && activeScreen == "UNLOCK") {
            activeScreen = "VAULT"
        }
    }

    // Displays feedback notices inside temporary animated banners
    LaunchedEffect(statusMsg) {
        if (statusMsg != null) {
            Toast.makeText(context, statusMsg, Toast.LENGTH_SHORT).show()
            delay(3000)
            viewModel.clearOperationStatus()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = activeScreen,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                "NORMAL" -> {
                    DisguiseNotesScreen(
                        notes = notes,
                        hasSetup = hasSetup,
                        onAddNote = { title, content ->
                            viewModel.addNormalNote(title, content)
                        },
                        onDeleteNote = { note ->
                            viewModel.deleteNormalNote(note)
                        },
                        onTriggerUnlock = {
                            if (!hasSetup) {
                                activeScreen = "ONBOARDING"
                            } else {
                                activeScreen = "UNLOCK"
                            }
                        }
                    )
                }
                "ONBOARDING" -> {
                    OnboardingSetupScreen(
                        onSetupComplete = { pattern, pin ->
                            viewModel.setPatternGesture(pattern)
                            viewModel.setBackupPin(pin)
                            viewModel.completeOnboarding()
                            activeScreen = "VAULT"
                        },
                        onCancel = {
                            activeScreen = "NORMAL"
                        }
                    )
                }
                "UNLOCK" -> {
                    val activity = LocalContext.current as? androidx.fragment.app.FragmentActivity
                    GestureUnlockScreen(
                        onVerifyGesture = { pattern ->
                            viewModel.verifyPatternGesture(pattern)
                        },
                        onVerifyPin = { pin ->
                            viewModel.verifyBackupPin(pin)
                        },
                        isBiometricEnabled = isBiometricEnabled,
                        onTriggerBiometric = {
                            if (activity != null) {
                                com.example.auth.BiometricAuthHelper.showBiometricPrompt(
                                    activity = activity,
                                    onSuccess = {
                                        viewModel.unlockViaBiometric()
                                    },
                                    onError = { error ->
                                        Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        },
                        onCancel = {
                            activeScreen = "NORMAL"
                        }
                    )
                }
                "VAULT" -> {
                    val intruderLogs by viewModel.intruderLogs.collectAsState()
                    VaultDashboardScreen(
                        photos = photos,
                        files = files,
                        intruderLogs = intruderLogs,
                        backupProvider = selectedBackupProvider,
                        isAutoBackupEnabled = isAutoBackupLogEnabled,
                        autoBackupFrequency = autoBackupFrequency,
                        cloudLinkedAccount = cloudLinkedAccount,
                        lastBackupMetadata = lastBackupInfoState,
                        onDeleteIntruderLog = { log -> viewModel.deleteIntruderLog(log) },
                        onClearAllIntruderLogs = { viewModel.clearAllIntruderLogs() },
                        onImportPhoto = { uri -> viewModel.importPhoto(uri) },
                        onDeletePhoto = { photo -> viewModel.deletePhoto(photo) },
                        onImportFile = { uri -> viewModel.importFile(uri) },
                        onDeleteFile = { file -> viewModel.deleteFile(file) },
                        onLockExit = {
                            viewModel.lock()
                            activeScreen = "NORMAL"
                        },
                        onUpdateGesture = { pattern -> viewModel.setPatternGesture(pattern) },
                        onUpdatePin = { pin -> viewModel.setBackupPin(pin) },
                        isBiometricEnabled = isBiometricEnabled,
                        onSetBiometric = { enabled -> viewModel.setBiometricToggle(enabled) },
                        isStealthModeEnabled = isStealthModeEnabled,
                        onSetStealthMode = { enabled -> viewModel.setStealthModeToggle(enabled) },
                        hasCompletedFakeOnboarding = hasCompletedFakeOnboarding,
                        onUpdateFakeGesture = { pattern -> viewModel.setPatternGesture(pattern, isDecoy = true) },
                        onUpdateFakePin = { pin -> viewModel.setBackupPin(pin, isDecoy = true) },
                        onSetBackupProvider = { provider -> viewModel.setBackupProvider(provider) },
                        onSetAutoBackupEnabled = { enabled -> viewModel.setAutoBackupToggle(enabled) },
                        onSetAutoBackupFrequency = { freq -> viewModel.setAutoBackupFrequency(freq) },
                        onSetCloudLinkedAccount = { email -> viewModel.setCloudLinkedAccount(email) },
                        onTriggerBackup = { viewModel.performManualBackup() },
                        onTriggerRestore = { viewModel.performManualRestore(context) }
                    )
                }
            }
        }
    }
}
