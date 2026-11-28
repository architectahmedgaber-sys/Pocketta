package app.pocketta.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.pocketta.i18n.Tr

private val LightFallback = lightColorScheme(
    primary = Color(0xFF1F6B4F), onPrimary = Color.White,
    primaryContainer = Color(0xFFA9F0CF), onPrimaryContainer = Color(0xFF002116),
    tertiary = Color(0xFF3D6373), tertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFFF3F6F4), surface = Color(0xFFF3F6F4), surfaceContainer = Color(0xFFFFFFFF),
)
private val DarkFallback = darkColorScheme(
    primary = Color(0xFF8DD5B3), onPrimary = Color(0xFF003826),
    primaryContainer = Color(0xFF005139), onPrimaryContainer = Color(0xFFA9F0CF),
    secondary = Color(0xFFB4CCBF), onSecondary = Color(0xFF20352B),
    secondaryContainer = Color(0xFF364B41), onSecondaryContainer = Color(0xFFD0E8DB),
    tertiary = Color(0xFFA5CCE0), tertiaryContainer = Color(0xFF244C5B), onTertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFF0F1512), onBackground = Color(0xFFDFE4E0),
    surface = Color(0xFF0F1512), onSurface = Color(0xFFDFE4E0),
    surfaceVariant = Color(0xFF3F4944), onSurfaceVariant = Color(0xFFBFC9C3),
    outline = Color(0xFF89938D), outlineVariant = Color(0xFF3F4944),
    surfaceContainer = Color(0xFF1B211E),
)

/** True while the dark theme is showing (the phone's setting, or the choice made in Settings). */
val LocalDark = staticCompositionLocalOf { false }

val Warn @Composable get() = if (LocalDark.current) Color(0xFFFFB871) else Color(0xFF9A5B00)
/** The gold accent of the new look: hero number, progress bars, the add button. */
val GoldAccent = Color(0xFFB8914A)
val Good @Composable get() = if (LocalDark.current) Color(0xFF8FD8A4) else Color(0xFF1F7A3F)

/** The big Today card keeps its own strong colours in both themes, with white text on top. */
val HeroGreen = Color(0xFF1F6B4F)
val HeroAmber = Color(0xFF9A5B00)
val HeroRed = Color(0xFFB3261E)

@Composable
fun PockettaTheme(mode: String = "AUTO", lang: String = Tr.EN, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    // A fixed Pocketta look (green) instead of the wallpaper colours, so the app has its own identity.
    val scheme = if (dark) DarkFallback else LightFallback
    CompositionLocalProvider(
        LocalDark provides dark,
        LocalLang provides lang,
        LocalLayoutDirection provides if (lang == Tr.AR) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
