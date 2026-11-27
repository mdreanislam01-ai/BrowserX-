package com.browserx.app

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun BrowserXTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    accentColor: Color,
    isPrivate: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    } || isPrivate

    val context = LocalContext.current
    val baseScheme = when {
        isPrivate -> darkColorScheme(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            background = Color(0xFF121018),
            surface = Color(0xFF191622),
            surfaceVariant = Color(0xFF282336),
            onSurface = Color(0xFFE6E1E5)
        )
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(
            primary = accentColor,
            secondary = accentColor.copy(alpha = 0.85f),
            background = Color(0xFF111216),
            surface = Color(0xFF191B20),
            surfaceVariant = Color(0xFF252830)
        )
        else -> lightColorScheme(
            primary = accentColor,
            secondary = accentColor.copy(alpha = 0.85f),
            background = Color(0xFFF8F9FC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEEF1F7)
        )
    }

    MaterialTheme(
        colorScheme = baseScheme,
        shapes = ExpressiveShapes,
        content = content
    )
}
