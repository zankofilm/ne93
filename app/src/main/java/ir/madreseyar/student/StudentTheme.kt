package ir.madreseyar.student

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF163A70),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FF),
    onPrimaryContainer = Color(0xFF0B2852),
    secondary = Color(0xFF1D7A73),
    secondaryContainer = Color(0xFFDDF4F0),
    tertiary = Color(0xFFC28A2C),
    background = Color(0xFFF4F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF1F6),
    outlineVariant = Color(0xFFDDE2EA),
    error = Color(0xFFB3261E)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC6F5),
    primaryContainer = Color(0xFF193B6A),
    secondary = Color(0xFF80D4CA),
    tertiary = Color(0xFFE7BD70),
    background = Color(0xFF0D1118),
    surface = Color(0xFF151B24),
    surfaceVariant = Color(0xFF202936),
    outlineVariant = Color(0xFF354052)
)

@Composable
fun MadreseyarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
