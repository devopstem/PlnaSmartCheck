package com.plna.smartcheck.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = PlnaDarkPrimary,
    onPrimary = PlnaNavyDark,
    primaryContainer = Color(0xFF123A2E),
    onPrimaryContainer = PlnaMint,
    secondary = PlnaDarkSecondary,
    onSecondary = PlnaNavyDark,
    tertiary = PlnaDarkTertiary,
    background = PlnaNavyDark,
    onBackground = PlnaOnNavy,
    surface = PlnaNavy,
    onSurface = PlnaOnNavy,
    surfaceContainer = Color(0xFF122946),
)

private val LightColorScheme = lightColorScheme(
    primary = PlnaLightPrimary,
    onPrimary = PlnaOnNavy,
    primaryContainer = Color(0xFFD7E4F5),
    onPrimaryContainer = PlnaNavy,
    secondary = PlnaLightSecondary,
    onSecondary = PlnaOnNavy,
    tertiary = PlnaLightTertiary,
    onTertiary = PlnaNavyDark,
    background = Color(0xFFF5F7FA),
    onBackground = PlnaNavy,
    surface = Color(0xFFF5F7FA),
    onSurface = PlnaNavy,
    surfaceContainer = Color(0xFFE8EEF6),
)

@Composable
fun PlnaSmartCheckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Brand first for Play Store identity; dynamic color optional
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
