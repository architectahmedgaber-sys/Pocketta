package app.pocketta.ui

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

private val LightFallback = lightColorScheme(
    primary = Color(0xFF1F6B4F), onPrimary = Color.White,
    primaryContainer = Color(0xFFA9F0CF), onPrimaryContainer = Color(0xFF002116),
    tertiary = Color(0xFF3D6373), tertiaryContainer = Color(0xFFC1E8FB),
)
private val DarkFallback = darkColorScheme(
    primary = Color(0xFF8DD5B3), onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139), onPrimaryContainer = Color(0xFFA9F0CF),
    tertiary = Color(0xFFA5CCE0), tertiaryContainer = Color(0xFF244C5B),
)

val Warn @Composable get() = if (isSystemInDarkTheme()) Color(0xFFFFB871) else Color(0xFF9A5B00)
val Good @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8FD8A4) else Color(0xFF1F7A3F)

@Composable
fun PockettaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
