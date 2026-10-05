package com.localmed.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF165D54),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDECE1),
    secondary = Color(0xFF4D5F7C),
    secondaryContainer = Color(0xFFDDE4F7),
    tertiary = Color(0xFF806000),
    tertiaryContainer = Color(0xFFFFE28A),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFF7FAF8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ED7CC),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005047),
    secondary = Color(0xFFB9C7E3),
    secondaryContainer = Color(0xFF35445F),
    tertiary = Color(0xFFE9C35A),
    tertiaryContainer = Color(0xFF5D4600),
    error = Color(0xFFFFB4AB)
)

@Composable
fun LocalMedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
