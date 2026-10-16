package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WarmLightColorScheme = lightColorScheme(
    primary = TextPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = CyberBlueLight,
    onPrimaryContainer = CyberBlue,
    secondary = EmeraldSecurity,
    onSecondary = Color.White,
    secondaryContainer = EmeraldLight.copy(alpha = 0.2f),
    onSecondaryContainer = EmeraldDark,
    tertiary = FolderAmber,
    background = CanvasBgWarm,
    onBackground = TextPrimaryDark,
    surface = SurfaceCardWhite,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceCardDashed,
    onSurfaceVariant = TextSecondaryMuted,
    outline = BorderSubtleWarm,
    outlineVariant = BorderDashedWarm,
    error = RoseError,
    errorContainer = RoseErrorLight,
    onError = Color.White
)

private val CyberDarkColorScheme = darkColorScheme(
    primary = TextPrimaryLight,
    onPrimary = CanvasBgDark,
    primaryContainer = SurfaceCardElevated,
    onPrimaryContainer = EmeraldLight,
    secondary = EmeraldPrimary,
    onSecondary = CanvasBgDark,
    secondaryContainer = EmeraldDark.copy(alpha = 0.3f),
    onSecondaryContainer = EmeraldLight,
    tertiary = FolderAmber,
    background = CanvasBgDark,
    onBackground = TextPrimaryLight,
    surface = SurfaceCardDark,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceCardElevated,
    onSurfaceVariant = TextSecondaryDarkMuted,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = RoseError,
    onError = Color.White
)

@Composable
fun LogitPassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CyberDarkColorScheme else WarmLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
