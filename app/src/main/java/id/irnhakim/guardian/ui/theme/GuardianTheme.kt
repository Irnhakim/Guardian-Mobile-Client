package id.irnhakim.guardian.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GuardianColorScheme = darkColorScheme(
    primary = Color(0xFF5C7CFA),
    onPrimary = Color.White,
    secondary = Color(0xFFA78BFA),
    onSecondary = Color.White,
    background = Color(0xFF0F1117),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1C2333),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF161B27),
    error = Color(0xFFEF4444),
)

@Composable
fun GuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GuardianColorScheme,
        content = content,
    )
}
