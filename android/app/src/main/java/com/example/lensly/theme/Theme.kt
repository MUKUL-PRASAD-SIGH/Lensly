package com.example.lensly.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LenslyDarkColorScheme = darkColorScheme(
    primary = Color(0xFF6C63FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3D35CC),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF00D4A3),
    onSecondary = Color.Black,
    tertiary = Color(0xFFFF6B35),
    background = Color(0xFF1A1A2E),
    onBackground = Color.White,
    surface = Color(0xFF16213E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF0F3460),
    onSurfaceVariant = Color(0xFFB0B3C6),
    error = Color(0xFFFF4757),
    onError = Color.White
)

@Composable
fun LenslyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LenslyDarkColorScheme,
        typography = Typography,
        content = content
    )
}
