package com.lmarena.agent.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCBBAFF),
    onPrimary = Color(0xFF352A55),
    secondary = Color(0xFFCCBFEF),
    background = Color(0xFF16121F),
    surface = Color(0xFF1E192B),
    surfaceVariant = Color(0xFF2A2340),
    onBackground = Color(0xFFEDE6FF),
    onSurface = Color(0xFFEDE6FF)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6B4FBB),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF6B5A86),
    background = Color(0xFFFBF7FF),
    surface = Color(0xFFF3EEFB),
    surfaceVariant = Color(0xFFE6DDF5),
    onBackground = Color(0xFF1B1730),
    onSurface = Color(0xFF1B1730)
)

@Composable
fun LmArenaAgentTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
