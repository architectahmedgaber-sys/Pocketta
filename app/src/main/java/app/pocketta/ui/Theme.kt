package app.pocketta.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.pocketta.R
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.pocketta.i18n.Tr

// Design tokens of the new look: one deep green, one gold accent used sparingly, white cards with a hairline border.
// Muted text is a little darker than the mock-up's #7B827D so it stays at 4.5:1 or more on every surface.
private val LightFallback = lightColorScheme(
    primary = Color(0xFF17493A), onPrimary = Color(0xFFF4EFE3),
    primaryContainer = Color(0xFFEFEADD), onPrimaryContainer = Color(0xFF17493A),
    secondary = Color(0xFF17493A), onSecondary = Color(0xFFF4EFE3),
    secondaryContainer = Color(0xFFEFEADD), onSecondaryContainer = Color(0xFF17493A),
    tertiary = Color(0xFF8A6A2C), tertiaryContainer = Color(0xFFFBF5E8), onTertiaryContainer = Color(0xFF4F3B12),
    background = Color(0xFFF7F5F0), onBackground = Color(0xFF1B1F1D),
    surface = Color(0xFFF7F5F0), onSurface = Color(0xFF1B1F1D),
    surfaceVariant = Color(0xFFEFEADD), onSurfaceVariant = Color(0xFF5C645F),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFFFFFFF), surfaceContainerHighest = Color(0xFFEFEADD),
    outline = Color(0xFF8C8F86), outlineVariant = Color(0xFFE8E4DA),
    error = Color(0xFFB3402F), errorContainer = Color(0xFFF8E3DE), onErrorContainer = Color(0xFF6B1F14),
)
private val DarkFallback = darkColorScheme(
    primary = Color(0xFF8DD5B3), onPrimary = Color(0xFF0B2A20),
    primaryContainer = Color(0xFF1C5946), onPrimaryContainer = Color(0xFFE9E5DA),
    secondary = Color(0xFFB4CCBF), onSecondary = Color(0xFF20352B),
    secondaryContainer = Color(0xFF1F2924), onSecondaryContainer = Color(0xFFD0E8DB),
    tertiary = Color(0xFFE6C98C), tertiaryContainer = Color(0xFF2A2314), onTertiaryContainer = Color(0xFFE6C98C),
    background = Color(0xFF0B100E), onBackground = Color(0xFFE9E5DA),
    surface = Color(0xFF0B100E), onSurface = Color(0xFFE9E5DA),
    surfaceVariant = Color(0xFF1F2924), onSurfaceVariant = Color(0xFF9AA59F),
    outline = Color(0xFF5E6B64), outlineVariant = Color(0xFF1F2924),
    surfaceContainer = Color(0xFF131A17), surfaceContainerHigh = Color(0xFF131A17), surfaceContainerHighest = Color(0xFF1F2924),
    error = Color(0xFFE88A7B), errorContainer = Color(0xFF4A1F18), onErrorContainer = Color(0xFFF8E3DE),
)

private val Shapes24 = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
/** Cairo (SIL Open Font License), bundled as a variable font: one file, every weight, Arabic and Latin. */
val CairoFamily = FontFamily(
    listOf(300, 400, 500, 600, 700).map { w ->
        Font(R.font.cairo, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    }
)

/** Lighter weights (300 for the hero number, 400 body, 500 emphasis) and tabular figures so numbers line up. */
private fun calmTypography(): Typography {
    val base = Typography()
    fun TextStyle.calm(w: FontWeight) = copy(fontFamily = CairoFamily, fontWeight = w, fontFeatureSettings = "tnum")
    return base.copy(
        displayLarge = base.displayLarge.calm(FontWeight.Light), displayMedium = base.displayMedium.calm(FontWeight.Light),
        displaySmall = base.displaySmall.calm(FontWeight.Light),
        headlineLarge = base.headlineLarge.calm(FontWeight.Normal), headlineMedium = base.headlineMedium.calm(FontWeight.Medium),
        headlineSmall = base.headlineSmall.calm(FontWeight.Medium),
        titleLarge = base.titleLarge.calm(FontWeight.Medium), titleMedium = base.titleMedium.calm(FontWeight.Medium),
        titleSmall = base.titleSmall.calm(FontWeight.Medium),
        bodyLarge = base.bodyLarge.calm(FontWeight.Normal), bodyMedium = base.bodyMedium.calm(FontWeight.Normal),
        bodySmall = base.bodySmall.calm(FontWeight.Normal),
        labelLarge = base.labelLarge.calm(FontWeight.Medium), labelMedium = base.labelMedium.calm(FontWeight.Normal),
        labelSmall = base.labelSmall.calm(FontWeight.Normal),
    )
}

/** True while the dark theme is showing (the phone's setting, or the choice made in Settings). */
val LocalDark = staticCompositionLocalOf { false }

val Warn @Composable get() = if (LocalDark.current) Color(0xFFFFB871) else Color(0xFF9A5B00)
/** Soft chip fill, and the notice fill with its border. */
val NoticeBorder = Color(0xFFE6D3A6)
val GoldLight = Color(0xFFE6C98C)
/** Cream text on the green hero card. */
val HeroText = Color(0xFFF4EFE3)
val HeroGradient @Composable get() = if (LocalDark.current) listOf(Color(0xFF1C5946), Color(0xFF0D2A22)) else listOf(Color(0xFF17493A), Color(0xFF0F332A))
/** The gold accent of the new look: hero number, progress bars, the add button. */
val GoldAccent = Color(0xFFB8914A)
val Good @Composable get() = if (LocalDark.current) Color(0xFF8FD8A4) else Color(0xFF2E7D4F)

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
        MaterialTheme(colorScheme = scheme, shapes = Shapes24, typography = calmTypography(), content = content)
    }
}
