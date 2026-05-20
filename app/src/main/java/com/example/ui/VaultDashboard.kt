package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.SecretFile
import com.example.data.SecretPhoto
import com.example.data.IntruderLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDashboardScreen(
    photos: List<SecretPhoto>,
    files: List<SecretFile>,
    intruderLogs: List<IntruderLog>,
    backupProvider: String,
    isAutoBackupEnabled: Boolean,
    autoBackupFrequency: String,
    cloudLinkedAccount: String,
    lastBackupMetadata: com.example.backup.BackupMetadata?,
    onDeleteIntruderLog: (IntruderLog) -> Unit,
    onClearAllIntruderLogs: () -> Unit,
    onImportPhoto: (Uri) -> Unit,
    onDeletePhoto: (SecretPhoto) -> Unit,
    onImportFile: (Uri) -> Unit,
    onDeleteFile: (SecretFile) -> Unit,
    onLockExit: () -> Unit,
    onUpdateGesture: (List<Int>) -> Unit,
    onUpdatePin: (String) -> Unit,
    isBiometricEnabled: Boolean,
    onSetBiometric: (Boolean) -> Unit,
    isStealthModeEnabled: Boolean,
    onSetStealthMode: (Boolean) -> Unit,
    hasCompletedFakeOnboarding: Boolean,
    onUpdateFakeGesture: (List<Int>) -> Unit,
    onUpdateFakePin: (String) -> Unit,
    onSetBackupProvider: (String) -> Unit,
    onSetAutoBackupEnabled: (Boolean) -> Unit,
    onSetAutoBackupFrequency: (String) -> Unit,
    onSetCloudLinkedAccount: (String) -> Unit,
    onTriggerBackup: () -> Unit,
    onTriggerRestore: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var fullScreenPhoto: SecretPhoto? by remember { mutableStateOf(null) }
    var activeWebUrl: String? by remember { mutableStateOf(null) }
    
    val context = LocalContext.current
    
    // Custom isolated Dark Theme for the vault matching the Editorial Aesthetic
    val vaultDarkColorScheme = darkColorScheme(
        primary = Color(0xFFD0BCFF),
        secondary = Color(0xFF2B2930),
        background = Color(0xFF1C1B1F),
        surface = Color(0xFF211F26),
        onPrimary = Color(0xFF381E72),
        onBackground = Color(0xFFE6E1E5),
        onSurface = Color(0xFFE6E1E5),
        surfaceVariant = Color(0xFF2B2930),
        onSurfaceVariant = Color(0xFFCAC4D0),
        primaryContainer = Color(0xFF4A4458),
        onPrimaryContainer = Color(0xFFEADDFF)
    )

    MaterialTheme(colorScheme = vaultDarkColorScheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (activeWebUrl != null) {
                PrivateWebView(
                     url = activeWebUrl!!,
                     onClose = { activeWebUrl = null }
                )
            } else if (fullScreenPhoto != null) {
                FullScreenPhotoViewer(
                    photo = fullScreenPhoto!!,
                    onClose = { fullScreenPhoto = null },
                    onShare = { shareFile(context, fullScreenPhoto!!.internalPath, "image/*") },
                    onDelete = {
                        onDeletePhoto(fullScreenPhoto!!)
                        fullScreenPhoto = null
                    }
                )
            } else {
                Scaffold(
                    topBar = {
                        LargeTopAppBar(
                            title = {
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = "Secret Vault",
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Light,
                                        letterSpacing = (-1).sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Device status: Encrypted & Stealth",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = onLockExit,
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Lock & Exit",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.largeTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Pictures") },
                                label = { Text("Pictures") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Default.List, contentDescription = "Files") },
                                label = { Text("Files") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Default.Menu, contentDescription = "Secret Web") },
                                label = { Text("Secret Web") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                icon = { Icon(Icons.Default.Info, contentDescription = "App Hider") },
                                label = { Text("Hide Guide") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 4,
                                onClick = { selectedTab = 4 },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (selectedTab) {
                            0 -> PicturesTab(photos, onImportPhoto, onDeletePhoto) { fullScreenPhoto = it }
                            1 -> FilesTab(files, onImportFile, onDeleteFile)
                            2 -> SecretWebTab { activeWebUrl = it }
                            3 -> AppHiderTab()
                            4 -> SettingsTab(
                                intruderLogs = intruderLogs,
                                backupProvider = backupProvider,
                                isAutoBackupEnabled = isAutoBackupEnabled,
                                autoBackupFrequency = autoBackupFrequency,
                                cloudLinkedAccount = cloudLinkedAccount,
                                lastBackupMetadata = lastBackupMetadata,
                                onDeleteIntruderLog = onDeleteIntruderLog,
                                onClearAllIntruderLogs = onClearAllIntruderLogs,
                                onUpdateGesture = onUpdateGesture,
                                onUpdatePin = onUpdatePin,
                                isBiometricEnabled = isBiometricEnabled,
                                onSetBiometric = onSetBiometric,
                                isStealthModeEnabled = isStealthModeEnabled,
                                onSetStealthMode = onSetStealthMode,
                                hasCompletedFakeOnboarding = hasCompletedFakeOnboarding,
                                onUpdateFakeGesture = onUpdateFakeGesture,
                                onUpdateFakePin = onUpdateFakePin,
                                onSetBackupProvider = onSetBackupProvider,
                                onSetAutoBackupEnabled = onSetAutoBackupEnabled,
                                onSetAutoBackupFrequency = onSetAutoBackupFrequency,
                                onSetCloudLinkedAccount = onSetCloudLinkedAccount,
                                onTriggerBackup = onTriggerBackup,
                                onTriggerRestore = onTriggerRestore
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------- PICTURES TAB ----------------------
@Composable
fun PicturesTab(
    photos: List<SecretPhoto>,
    onImportPhoto: (Uri) -> Unit,
    onDeletePhoto: (SecretPhoto) -> Unit,
    onOpenPhoto: (SecretPhoto) -> Unit
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let { onImportPhoto(it) }
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        if (photos.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.Home,
                title = "No Hidden Pictures",
                description = "Import sensitive photos or captures from your gallery. They will be stored securely inside the vault."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(photos) { photo ->
                    val bitmap = remember(photo.internalPath) {
                        decodeSampledBitmap(photo.internalPath, 200, 200)
                    }
                    Card(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onOpenPhoto(photo) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = photo.originalName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.DarkGray),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Failed to load",
                                        tint = Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Picture")
        }
    }
}

// ---------------------- FILES TAB ----------------------
@Composable
fun FilesTab(
    files: List<SecretFile>,
    onImportFile: (Uri) -> Unit,
    onDeleteFile: (SecretFile) -> Unit
) {
    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let { onImportFile(it) }
        }
    )

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (files.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.List,
                title = "No Encrypted Files",
                description = "Hide documents, PDFs, audios, ZIPs, or any other secret file. You can access and export them cleanly."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(files) { file ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "File Icon",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.originalName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatSize(file.sizeBytes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = dateFormat.format(Date(file.addedTimestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            IconButton(onClick = { shareFile(context, file.internalPath, file.mimeType) }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Export file",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            IconButton(onClick = { onDeleteFile(file) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete secret file",
                                    tint = Color.Red.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { filePickerLauncher.launch("*/*") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Import file")
        }
    }
}

// ---------------------- SECRET WEB TAB ----------------------
@Composable
fun SecretWebTab(onSelectWeb: (String) -> Unit) {
    var customUrl by remember { mutableStateOf("") }
    val popularApps = listOf(
        SocialWeb(name = "Instagram", url = "https://www.instagram.com"),
        SocialWeb(name = "Facebook", url = "https://www.facebook.com"),
        SocialWeb(name = "X / Twitter", url = "https://x.com"),
        SocialWeb(name = "Reddit", url = "https://www.reddit.com"),
        SocialWeb(name = "LinkedIn", url = "https://www.linkedin.com")
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Launch Private Web Sandbox",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Browse completely anonymously inside an isolated sandbox browser. Cookies and logins are entirely separated from your main phone browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        placeholder = { Text("https://example.com") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (customUrl.isNotBlank()) {
                                var validUrl = customUrl.trim()
                                if (!validUrl.startsWith("http://") && !validUrl.startsWith("https://")) {
                                    validUrl = "https://$validUrl"
                                }
                                onSelectWeb(validUrl)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Open")
                    }
                }
            }
        }

        Text(
            text = "Pre-Configured Encrypted Channels",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(popularApps) { app ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectWeb(app.url) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.name.take(1),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = app.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = app.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

data class SocialWeb(val name: String, val url: String)

// ---------------------- APP HIDER TAB ----------------------
@Composable
fun AppHiderTab() {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Hider Warning",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Hiding Physical Apps on Android",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Because Google restricts third-party store applications from freezing, disabling, or erasing launch icons of other system applications to prevent malware, you should use native OS capabilities. Follow the brand steps below for 100% security.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }

        item {
            HiderGuideItem(
                brand = "Google Pixel & Android 15+",
                guide = "Google built the ultimate native hidden container called 'Private Space'.\n\nTo configure:\n1. Open Settings -> Security & Privacy.\n2. Tap 'Private Space' and enter credentials.\n3. Customize a secret PIN to make Private Space invisible on the home screen.\n4. You can hide any app here, completely invisible until searched!"
            )
        }

        item {
            HiderGuideItem(
                brand = "Samsung Devices (Secure Folder)",
                guide = "Samsung custom vaults are powered by hardware Knox integration.\n\nTo configure:\n1. Go to Settings -> Security and Privacy -> Secure Folder.\n2. Create or unlock with account.\n3. Inside the folder, tap (+) to clone any app.\n4. You can rename the Secure Folder and change its launch icon to looks like a calculator, entirely hiding it from your drawer!"
            )
        }

        item {
            HiderGuideItem(
                brand = "OnePlus, Oppo & Realme Devices",
                guide = "ColorOS supports absolute app vanishing with secret dialing access codes.\n\nTo configure:\n1. Go to Settings -> Privacy -> Hide Apps.\n2. Set up a security pin and enter an access code (e.g. #7777#).\n3. Toggle any app to hide it.\n4. The apps are completely erased from lists. To see them, enter your code (#7777#) in the native Phone Dialer app!"
            )
        }

        item {
            HiderGuideItem(
                brand = "Xiaomi / Redmi (Second Space)",
                guide = "MIUI supports Second Space which creates a separate virtual system.\n\nTo configure:\n1. Go to Settings -> Additional Settings -> Second Space.\n2. Enable it and bind a dedicated finger scanner profile.\n3. Logging in with your second-screen fingerprint instantly boots a clean private workspace containing hidden social networks!"
            )
        }
    }
}

@Composable
fun HiderGuideItem(brand: String, guide: String) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = brand,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = guide,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
        }
    }
}

// ---------------------- SETTINGS TAB ----------------------
@Composable
fun SettingsTab(
    intruderLogs: List<IntruderLog>,
    backupProvider: String,
    isAutoBackupEnabled: Boolean,
    autoBackupFrequency: String,
    cloudLinkedAccount: String,
    lastBackupMetadata: com.example.backup.BackupMetadata?,
    onDeleteIntruderLog: (IntruderLog) -> Unit,
    onClearAllIntruderLogs: () -> Unit,
    onUpdateGesture: (List<Int>) -> Unit,
    onUpdatePin: (String) -> Unit,
    isBiometricEnabled: Boolean,
    onSetBiometric: (Boolean) -> Unit,
    isStealthModeEnabled: Boolean,
    onSetStealthMode: (Boolean) -> Unit,
    hasCompletedFakeOnboarding: Boolean,
    onUpdateFakeGesture: (List<Int>) -> Unit,
    onUpdateFakePin: (String) -> Unit,
    onSetBackupProvider: (String) -> Unit,
    onSetAutoBackupEnabled: (Boolean) -> Unit,
    onSetAutoBackupFrequency: (String) -> Unit,
    onSetCloudLinkedAccount: (String) -> Unit,
    onTriggerBackup: () -> Unit,
    onTriggerRestore: () -> Unit
) {
    var isEditingGesture by remember { mutableStateOf(false) }
    var isEditingPin by remember { mutableStateOf(false) }
    var isEditingFakeGesture by remember { mutableStateOf(false) }
    var isEditingFakePin by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var inputPin: String by remember { mutableStateOf("") }
    var inputFakePin: String by remember { mutableStateOf("") }
    
    // Cloud backup dialog toggle states
    var showLinkDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (statusMessage != null) {
            item {
                Snackbar(
                    action = {
                        TextButton(onClick = { statusMessage = null }) {
                            Text("Dismiss")
                        }
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(statusMessage!!)
                }
            }
        }

        // Card 1: Primary Credentials Control
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Primary Credentials Control",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (isEditingGesture) {
                        Text(
                            text = "Draw your new 3x3 pattern below (connect at least 3 dots):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        
                        com.example.ui.components.PatternCanvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) { pattern ->
                            onUpdateGesture(pattern)
                            statusMessage = "Pattern gesture updated successfully!"
                            isEditingGesture = false
                        }

                        TextButton(onClick = { isEditingGesture = false }) {
                            Text("Cancel")
                        }
                    } else {
                        Button(
                            onClick = { isEditingGesture = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Edit Gesture")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Swipe Pattern Gesture")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isEditingPin) {
                        Text(
                            text = "Enter a new 4-digit backup PIN:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = inputPin,
                            onValueChange = { if (it.length <= 4) inputPin = it },
                            placeholder = { Text("4 Digits PIN") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isEditingPin = false }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    if (inputPin.length >= 4) {
                                        onUpdatePin(inputPin)
                                        statusMessage = "Backup security PIN updated!"
                                        isEditingPin = false
                                        inputPin = ""
                                    } else {
                                        statusMessage = "PIN must be exactly 4 digits."
                                    }
                                }
                            ) {
                                Text("Save PIN")
                            }
                        }
                    } else {
                        Button(
                            onClick = { isEditingPin = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Edit PIN")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update 4-Digit Backup PIN")
                        }
                    }
                }
            }
        }

        // Card 2: Decoy Vault Configuration (harmless / fake separate PIN/Pattern setup)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Decoy Vault Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    Text(
                        text = "Set up a separate alternate credential. When entered at the lock screen, this decoy credential boots the app into a decoy vault featuring harmless sample data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Decoy Status",
                            tint = if (hasCompletedFakeOnboarding) MaterialTheme.colorScheme.primary else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasCompletedFakeOnboarding) "Decoy Workspace status: Configured & Active" else "Decoy Workspace: Not configured yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasCompletedFakeOnboarding) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    if (isEditingFakeGesture) {
                        Text(
                            text = "Draw separate Decoy Pattern (must not match primary pattern):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        
                        com.example.ui.components.PatternCanvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) { pattern ->
                            onUpdateFakeGesture(pattern)
                            statusMessage = "Decoy swipe gesture pattern set!"
                            isEditingFakeGesture = false
                        }

                        TextButton(onClick = { isEditingFakeGesture = false }) {
                            Text("Cancel")
                        }
                    } else {
                        Button(
                            onClick = { isEditingFakeGesture = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Edit Decoy Gesture")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Configure Decoy Gesture Pattern")
                        }
                    }

                    if (isEditingFakePin) {
                        Text(
                            text = "Enter a separate 4-digit decoy backup PIN:",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = inputFakePin,
                            onValueChange = { if (it.length <= 4) inputFakePin = it },
                            placeholder = { Text("4 Digits Decoy PIN") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isEditingFakePin = false }) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    if (inputFakePin.length >= 4) {
                                        onUpdateFakePin(inputFakePin)
                                        statusMessage = "Decoy PIN set successfully!"
                                        isEditingFakePin = false
                                        inputFakePin = ""
                                    } else {
                                        statusMessage = "PIN must be exactly 4 digits."
                                    }
                                }
                            ) {
                                Text("Save Decoy PIN")
                            }
                        }
                    } else {
                        Button(
                            onClick = { isEditingFakePin = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Edit Decoy PIN")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Configure Decoy Backup PIN")
                        }
                    }
                }
            }
        }

        // Card 3: Advanced Hardware Security & Stealth Settings
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Device Preference Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Biometric Option toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Biometric Lock",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Instantly unlock the secret vault using your fingerprint scanner or face recognition.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { onSetBiometric(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                    // Stealth launch indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Stealth Dial Launch Code",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Launches \"errorify\" anywhere when *#7777# or #7777# is inputted in the native system dialer app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = isStealthModeEnabled,
                            onCheckedChange = { onSetStealthMode(it) }
                        )
                    }
                }
            }
        }

        // Card 4: Intruder Selfie Alert Logs
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Security Audit & Intruder Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Intruder Selfie Logs",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val logCount = intruderLogs.size
                            Text(
                                text = if (logCount == 0) "No break-in attempts detected. Your vault is secure." else "$logCount security breach attempt(s) logged.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (logCount == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    var showLogsSheet by remember { mutableStateOf(false) }

                    Button(
                        onClick = { showLogsSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (intruderLogs.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (intruderLogs.isNotEmpty()) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Security Alert Logs")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (intruderLogs.isNotEmpty()) "View Intruder Selfies (${intruderLogs.size})" else "View Empty Security Logs")
                    }

                    if (showLogsSheet) {
                        IntruderLogsDialog(
                            logs = intruderLogs,
                            onDelete = onDeleteIntruderLog,
                            onClearAll = onClearAllIntruderLogs,
                            onDismiss = { showLogsSheet = false }
                        )
                    }
                }
            }
        }

        // Card 5: Encrypted Cloud Backup & Sync
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Encrypted Cloud Backup & Sync",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Back up your hidden files and photos into secure, AES-256 encrypted archive packages, preserved on your private cloud drive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Choose Storage Provider
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Cloud Storage Provider",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf("Google Drive", "Dropbox").forEach { provider ->
                                val selected = backupProvider == provider
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSetBackupProvider(provider) },
                                    label = { Text(provider) },
                                    leadingIcon = if (selected) {
                                        { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Linked Account
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Account Connection",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (cloudLinkedAccount.isEmpty()) "Authorization required" else "Linked as $cloudLinkedAccount",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (cloudLinkedAccount.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF4CAF50),
                                fontWeight = if (cloudLinkedAccount.isEmpty()) FontWeight.Normal else FontWeight.Bold
                            )
                        }

                        if (cloudLinkedAccount.isEmpty()) {
                            Button(
                                onClick = { showLinkDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.heightIn(max = 38.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Link", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            TextButton(
                                onClick = { onSetCloudLinkedAccount("") },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.heightIn(max = 38.dp)
                            ) {
                                Text("Disconnect", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // Auto Backup Settings
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatic Backup",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Silently backs up your vault package when entering local session",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAutoBackupEnabled,
                                onCheckedChange = { onSetAutoBackupEnabled(it) }
                            )
                        }

                        if (isAutoBackupEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Sync Frequency",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Daily", "Weekly").forEach { freq ->
                                        val isSelected = autoBackupFrequency == freq
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onSetAutoBackupFrequency(freq) },
                                            label = { Text(freq, style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Real manual backup controls & status
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val hasCloudBackup = lastBackupMetadata != null
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Cloud Backup Package Status",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (hasCloudBackup) {
                                        "${lastBackupMetadata?.formattedSize} | ${lastBackupMetadata?.formattedDate}"
                                    } else {
                                        "No encrypted backup detected"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasCloudBackup) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                    fontWeight = if (hasCloudBackup) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Backup Now button
                            Button(
                                onClick = { onTriggerBackup() },
                                modifier = Modifier.weight(1f),
                                enabled = cloudLinkedAccount.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Back up Now", style = MaterialTheme.typography.bodyMedium)
                            }

                            // Restore button
                            Button(
                                onClick = { showRestoreConfirmDialog = true },
                                modifier = Modifier.weight(1f),
                                enabled = hasCloudBackup,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLinkDialog) {
        CloudLinkDialog(
            provider = backupProvider,
            onLink = { email -> onSetCloudLinkedAccount(email) },
            onDismiss = { showLinkDialog = false }
        )
    }

    if (showRestoreConfirmDialog) {
        CloudRestoreConfirmDialog(
            provider = backupProvider,
            onConfirm = { onTriggerRestore() },
            onDismiss = { showRestoreConfirmDialog = false }
        )
    }
}

// ---------------------- COMMON INTERNAL HELPERS ----------------------
@Composable
fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun FullScreenPhotoViewer(
    photo: SecretPhoto,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val bitmap = remember(photo.internalPath) {
        decodeSampledBitmap(photo.internalPath, 1080, 1920)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = photo.originalName,
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .padding(8.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = "Failed to load image",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Top Controls Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp)
                .statusBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close full screen",
                    tint = Color.White
                )
            }

            Text(
                text = photo.originalName,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            )

            Row {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Export share image",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete image permanently",
                        tint = Color.Red
                    )
                }
            }
        }
    }
}

// Share helper function to cleanly export files out of internal storage
fun shareFile(context: Context, absolutePath: String, mimeType: String) {
    try {
        val file = File(absolutePath)
        val uri = FileProvider.getUriForFile(
            context,
            "com.aistudio.simplenotes.hzqjxv.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share/Export Vault Item"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.toDouble())).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.toDouble(), digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun IntruderLogsDialog(
    logs: List<IntruderLog>,
    onDelete: (IntruderLog) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            if (logs.isNotEmpty()) {
                TextButton(
                    onClick = {
                        onClearAll()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All Logs")
                }
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (logs.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Text("Intruder Selfie Logs")
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
            ) {
                if (logs.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Safe",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Break-In Attempts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Every login attempt has been verified successfully.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(logs) { log ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Selfie Preview
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val bitmap = remember(log.imagePath) {
                                            if (log.imagePath != null) {
                                                decodeSampledBitmap(log.imagePath, 150, 150)
                                            } else null
                                        }

                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Intruder photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Face,
                                                contentDescription = "No Photo",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }

                                    // Attempt Details
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "Breach Method: ${log.attemptType}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        val formattedDate = remember(log.timestamp) {
                                            val sdf = SimpleDateFormat("MMM dd, yyyy | hh:mm a", Locale.getDefault())
                                            sdf.format(Date(log.timestamp))
                                        }
                                        
                                        Text(
                                            text = formattedDate,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        if (log.imagePath == null) {
                                            Text(
                                                text = "No photo captured (no camera permission)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.error,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    // Delete Single Log
                                    IconButton(
                                        onClick = { onDelete(log) },
                                        colors = IconButtonDefaults.iconButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete log entry"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun CloudLinkDialog(
    provider: String,
    onLink: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Link to $provider")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.contains("@") && email.contains(".")) {
                        onLink(email)
                        onDismiss()
                    } else {
                        linkError = "Please enter a valid account email."
                    }
                }
            ) {
                Text("Authorize")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Grant Vault permission to securely store its highly encrypted binary packages inside your personal $provider storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                TextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        linkError = null
                    },
                    label = { Text("Account Email Address") },
                    placeholder = { Text("e.g. user@gmail.com") },
                    isError = linkError != null,
                    supportingText = linkError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    text = "Important: The synced backup package is fully end-to-end encrypted with your local primary PIN/Pattern. Neither $provider nor Vault staff can ever decrypt your data.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 14.sp
                )
            }
        }
    )
}

@Composable
fun CloudRestoreConfirmDialog(
    provider: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text("Overwrite Local Vault Data?")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Restore & Reload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "You are about to restore the backup package from $provider.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Warning: This will overwrite ALL your current hidden photos, files, and database notes with the decrypted cloud state. This action is irreversible. The app will restart automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)
        
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        
        BitmapFactory.decodeFile(path, options)
    } catch (t: Throwable) {
        t.printStackTrace()
        null
    }
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}


