package app.fileforge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FA8FF),
    onPrimary = Color(0xFF00203F),
    primaryContainer = Color(0xFF1B3A66),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Color(0xFF9FB6D6),
    background = Color(0xFF0D1520),
    onBackground = Color(0xFFE3E9F2),
    surface = Color(0xFF121C2A),
    onSurface = Color(0xFFE3E9F2),
    surfaceVariant = Color(0xFF1B2838),
    onSurfaceVariant = Color(0xFFB4C0D0),
    outline = Color(0xFF3A4A60),
    error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D5FC4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E6FA),
    onPrimaryContainer = Color(0xFF0A2A5C),
    secondary = Color(0xFF4A5F7F),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF111A27),
    surface = Color.White,
    onSurface = Color(0xFF111A27),
    surfaceVariant = Color(0xFFE6ECF4),
    onSurfaceVariant = Color(0xFF45546A),
    outline = Color(0xFF8A98AD),
    error = Color(0xFFB3261E),
)

@Composable
fun useDarkTheme(mode: Int): Boolean = when (mode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun FileForgeTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
