package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PatternCanvas

// ---------------------- SECURITY ONBOARDING SETUP ----------------------
@Composable
fun OnboardingSetupScreen(
    onSetupComplete: (List<Int>, String) -> Unit,
    onCancel: () -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1: Draw Gesture, 2: Confirm Gesture, 3: Set PIN
    var firstAttemptPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var confirmedPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var enteredPin by remember { mutableStateOf("") }
    var helperText by remember { mutableStateOf("Draw a unique secret swipe gesture by connecting at least 3 dots.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp), // `rounded-[28px]` from template design directives
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when (step) {
                        1 -> "1. Draw Secret Gesture"
                        2 -> "2. Confirm Secret Gesture"
                        else -> "3. Set Backup Security PIN"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (step) {
                1 -> {
                    PatternCanvas(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f)
                    ) { pattern ->
                        if (pattern.size >= 3) {
                            firstAttemptPattern = pattern
                            step = 2
                            helperText = "Draw the identical gesture pattern again to confirm your secret entry."
                        } else {
                            helperText = "Connecting dots was too brief. Select at least 3 points!"
                        }
                    }
                }
                2 -> {
                    PatternCanvas(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f)
                    ) { pattern ->
                        if (pattern == firstAttemptPattern) {
                            confirmedPattern = pattern
                            step = 3
                            helperText = "Enter a 4-digit numeric PIN as a backup lock."
                        } else {
                            helperText = "Pattern did not match. Please draw your initial swipe gesture carefully."
                        }
                    }
                }
                3 -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Visual dots for passcode digits
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            for (i in 1..4) {
                                val active = enteredPin.length >= i
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                            CircleShape
                                        )
                                )
                            }
                        }

                        // Local Numeric Keypad entries
                        NumericKeypad(
                            onDigitClick = { digit ->
                                if (enteredPin.length < 4) {
                                    enteredPin += digit
                                    if (enteredPin.length == 4) {
                                        helperText = "Pin entered complete. Press Save Setup to lock and initiate the vault!"
                                    }
                                }
                            },
                            onBackspace = {
                                if (enteredPin.isNotEmpty()) {
                                    enteredPin = enteredPin.dropLast(1)
                                    helperText = "Enter a 4-digit numeric PIN as a backup lock."
                                }
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel Setup")
            }

            if (step == 3) {
                Button(
                    onClick = {
                        if (enteredPin.length == 4) {
                            onSetupComplete(confirmedPattern, enteredPin)
                        }
                    },
                    enabled = enteredPin.length == 4
                ) {
                    Text("Save Setup")
                }
            }
        }
    }
}


// ---------------------- SECURE GESTURE UNLOCK GATEWAY ----------------------
@Composable
fun GestureUnlockScreen(
    onVerifyGesture: (List<Int>) -> Boolean,
    onVerifyPin: (String) -> Boolean,
    isBiometricEnabled: Boolean,
    onTriggerBiometric: () -> Unit,
    onCancel: () -> Unit
) {
    var isUsingPin by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var feedbackText by remember { mutableStateOf("Swipe your secret gesture to proceed inside.") }
    var errorState by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isBiometricEnabled) {
            // Ensure screen animations finish and Activity is fully RESUMED before launching biometrics
            kotlinx.coroutines.delay(400)
            onTriggerBiometric()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onCancel) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close Vault Gateway")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Graphic Header
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    if (errorState) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = if (errorState) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Security Key Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = feedbackText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (errorState) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (!isUsingPin) {
                PatternCanvas(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f)
                ) { pattern ->
                    val success = onVerifyGesture(pattern)
                    if (success) {
                        feedbackText = "Secure unlock successful!"
                        errorState = false
                    } else {
                        feedbackText = "Incorrect gesture key pattern drawn. Try again."
                        errorState = true
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        for (i in 1..4) {
                            val active = enteredPin.length >= i
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        CircleShape
                                    )
                            )
                        }
                    }

                    NumericKeypad(
                        onDigitClick = { digit ->
                            if (enteredPin.length < 4) {
                                enteredPin += digit
                                if (enteredPin.length == 4) {
                                    val success = onVerifyPin(enteredPin)
                                    if (success) {
                                        feedbackText = "PIN validated!"
                                        errorState = false
                                    } else {
                                        feedbackText = "Incorrect backup security PIN."
                                        errorState = true
                                        enteredPin = ""
                                    }
                                }
                            }
                        },
                        onBackspace = {
                            if (enteredPin.isNotEmpty()) {
                                enteredPin = enteredPin.dropLast(1)
                                feedbackText = "Enter your 4-digit backup PIN."
                                errorState = false
                            }
                        }
                    )
                }
            }
        }

        if (isBiometricEnabled) {
            Button(
                onClick = onTriggerBiometric,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .fillMaxWidth(0.85f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Scan Biometrics",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Fingerprint / Face")
            }
        }

        TextButton(
            onClick = {
                isUsingPin = !isUsingPin
                enteredPin = ""
                errorState = false
                feedbackText = if (isUsingPin) "Enter your 4-digit backup PIN." else "Swipe your secret gesture to proceed inside."
            }
        ) {
            Text(if (isUsingPin) "Unlock with Secret Swipe Pattern" else "Unlock with Backup Security PIN")
        }
    }
}


// Helper custom high-fidelity numeric keypad Composable
@Composable
fun NumericKeypad(
    onDigitClick: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "<")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.85f)
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(modifier = Modifier.size(64.dp))
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .clickable {
                                    if (key == "<") onBackspace() else onDigitClick(key)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
