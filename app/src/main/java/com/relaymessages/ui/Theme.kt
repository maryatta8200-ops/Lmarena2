package com.relaymessages.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF245C49)
private val LightGreen = Color(0xFFD8EFE2)
private val Cream = Color(0xFFF6F7F4)
private val Ink = Color(0xFF17211D)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = LightGreen,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF53665D),
    background = Cream,
    surface = Color(0xFFFCFCFA),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7ECE8),
    onSurfaceVariant = Color(0xFF48534D),
    error = Color(0xFFBA1A1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD4B8),
    onPrimary = Color(0xFF073A26),
    primaryContainer = Color(0xFF245C49),
    onPrimaryContainer = Color(0xFFD8EFE2),
    secondary = Color(0xFFB8CCBF),
    background = Color(0xFF111714),
    surface = Color(0xFF171E1A),
    onSurface = Color(0xFFE3EAE5),
    surfaceVariant = Color(0xFF28312C),
    onSurfaceVariant = Color(0xFFC1CCC4)
)

@Composable
fun RelayTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
