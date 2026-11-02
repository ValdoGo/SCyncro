package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonEmerald,
    onPrimary = Color.Black,
    primaryContainer = DarkEmerald,
    onPrimaryContainer = Color.White,
    secondary = ElectricCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0E7490),
    onSecondaryContainer = Color.White,
    tertiary = AmberAlert,
    error = CrimsonBlocked,
    onError = Color.White,
    background = SlateBackgroundDark,
    onBackground = TextPrimary,
    surface = SlateSurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SlateCardDark,
    onSurfaceVariant = TextSecondary,
    outline = SlateCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = DarkEmerald,
    onPrimary = Color.White,
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    error = CrimsonBlocked,
    background = LightBackground,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightCard,
    onSurfaceVariant = Color(0xFF475569),
    outline = LightBorder
)

@Composable
fun FocusNetTheme(
    darkTheme: Boolean = true, // Default to dark cyber-modern theme for security & battery
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
