package com.zhexian.douyingallery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandRed = Color(0xFFFE2C55)
private val BrandCyan = Color(0xFF25F4EE)

private val DarkColors = darkColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B0A16),
    onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = BrandCyan,
    onSecondary = Color(0xFF00332F),
    background = Color(0xFF101012),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF141418),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF1E1E24),
    onSurfaceVariant = Color(0xFFB9B9C2),
    outline = Color(0xFF3A3A42)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD81B45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E0),
    onPrimaryContainer = Color(0xFF3B0A16),
    secondary = Color(0xFF008782),
    onSecondary = Color.White,
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFF1EEF3),
    onSurfaceVariant = Color(0xFF4A4A52),
    outline = Color(0xFFCFC7D0)
)

@Composable
fun GalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
