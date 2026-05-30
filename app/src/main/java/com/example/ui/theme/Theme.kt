package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberPrimaryCyan,
    secondary = CyberSecondaryGreen,
    tertiary = CyberWarningAmber,
    background = CyberBg,
    surface = CyberSurface,
    surfaceVariant = CyberSurfaceCard,
    error = CyberAlertRed,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = CyberTextHighLight,
    onSurface = CyberTextHighLight,
    onSurfaceVariant = CyberTextMuted,
    outline = CyberGridLine
)

private val BentoLightColorScheme = lightColorScheme(
    primary = BentoPrimaryPurple,
    secondary = BentoAccentPurple,
    tertiary = BentoAlertRedPrimary,
    background = BentoBg,
    surface = BentoCardPurpleMedium,
    surfaceVariant = BentoCardPurpleLight,
    error = BentoAlertRedPrimary,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = BentoTextPrimary,
    onSurface = BentoTextPrimary,
    onSurfaceVariant = BentoTextSecondary,
    outline = BentoOutlineColor
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Elegant, clean M3 light Bento Grid theme matches the user request.
    MaterialTheme(
        colorScheme = BentoLightColorScheme,
        typography = Typography,
        content = content
    )
}

