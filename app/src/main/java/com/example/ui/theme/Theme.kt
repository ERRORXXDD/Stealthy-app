package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val EditorialDarkColorScheme = darkColorScheme(
  primary = PrimaryPurpleLimit,
  onPrimary = OnPrimaryPurpleLimit,
  primaryContainer = PrimaryContainerPurpleLimit,
  onPrimaryContainer = OnPrimaryContainerPurpleLimit,
  secondary = SurfaceDarkPurpleLimit,
  onSecondary = SoftOffWhiteText,
  background = BackgroundDarkPurpleLimit,
  onBackground = SoftOffWhiteText,
  surface = DarkBackgroundPurpleLimit,
  onSurface = SoftOffWhiteText,
  surfaceVariant = SurfaceDarkPurpleLimit,
  onSurfaceVariant = MutedPurpleGrayText,
  outline = PrimaryContainerPurpleLimit
)

private val DarkColorScheme = EditorialDarkColorScheme

private val LightColorScheme = EditorialDarkColorScheme

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force premium editorial dark mode by default
  dynamicColor: Boolean = false, // Preserve the custom crafted palette
  content: @Composable () -> Unit,
) {
  val colorScheme = EditorialDarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
