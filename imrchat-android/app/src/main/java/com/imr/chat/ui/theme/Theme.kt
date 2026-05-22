package com.imr.chat.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF07C160),       // WeChat green
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFB8F5D0),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF002114),
    secondary = androidx.compose.ui.graphics.Color(0xFF4CAF50),
    surface = androidx.compose.ui.graphics.Color(0xFFF7F7F7),
    onSurface = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
    background = androidx.compose.ui.graphics.Color(0xFFEDEDED),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
    surfaceVariant = androidx.compose.ui.graphics.Color.White,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF666666),
)

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF07C160),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF005236),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFB8F5D0),
    secondary = androidx.compose.ui.graphics.Color(0xFF81C784),
    surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
    background = androidx.compose.ui.graphics.Color(0xFF121212),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE0E0E0),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2C2C2C),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFAAAAAA),
)

@Composable
fun IMRChatTheme(
    darkMode: String = "system",
    content: @Composable () -> Unit
) {
    val darkTheme = when (darkMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
