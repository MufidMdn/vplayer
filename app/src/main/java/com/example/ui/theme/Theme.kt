package com.example.ui.theme

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
    primary = CinemaPrimary,
    secondary = CinemaSecondary,
    tertiary = CinemaTertiary,
    background = SlateBackground,
    surface = SlateBackground,
    surfaceVariant = SlateSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color.White
)

// Clean bright fallback theme
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0288D1),
    secondary = Color(0xFF0097A7),
    tertiary = Color(0xFFE65100),
    background = Color(0xFFF9FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F3F5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force cinematic dark theme for rich video-center styling
    dynamicColor: Boolean = false, // Use our highly customized cinema color tokens instead
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
