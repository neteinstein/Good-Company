package com.neteinstein.goodcompany.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Brand: warm coral (connection), deep teal (calm), sunflower (celebration).
internal val Coral = Color(0xFFE5574F)
internal val CoralDeep = Color(0xFFB3261E)
internal val Teal = Color(0xFF00897B)
internal val Sunflower = Color(0xFFFFB300)

val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFFB4372F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF3F6560),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5EBE6),
    onSecondaryContainer = Color(0xFF0B2E2A),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF261900),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F6),
    onBackground = Color(0xFF231918),
    surface = Color(0xFFFFF8F6),
    onSurface = Color(0xFF231918),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    outline = Color(0xFF857371),
    outlineVariant = Color(0xFFD8C2BF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0EE),
    surfaceContainer = Color(0xFFFCEAE7),
    surfaceContainerHigh = Color(0xFFF6E4E2),
    surfaceContainerHighest = Color(0xFFF1DEDC),
    inverseSurface = Color(0xFF392E2D),
    inverseOnSurface = Color(0xFFFFEDEA),
    inversePrimary = Color(0xFFFFB4A9),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4A9),
    onPrimary = Color(0xFF690003),
    primaryContainer = Color(0xFF8F1F1A),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFA6CEC8),
    onSecondary = Color(0xFF0E3632),
    secondaryContainer = Color(0xFF284B47),
    onSecondaryContainer = Color(0xFFD5EBE6),
    tertiary = Color(0xFFF5BF48),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFDEA6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1110),
    onBackground = Color(0xFFF1DEDC),
    surface = Color(0xFF1A1110),
    onSurface = Color(0xFFF1DEDC),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BF),
    outline = Color(0xFFA08C8A),
    outlineVariant = Color(0xFF534341),
    surfaceContainerLowest = Color(0xFF140C0B),
    surfaceContainerLow = Color(0xFF231918),
    surfaceContainer = Color(0xFF271D1C),
    surfaceContainerHigh = Color(0xFF322827),
    surfaceContainerHighest = Color(0xFF3D3231),
    inverseSurface = Color(0xFFF1DEDC),
    inverseOnSurface = Color(0xFF392E2D),
    inversePrimary = Color(0xFFB4372F),
)

/**
 * Pleasant, stable avatar gradients picked by name hash. White initials and white card text sit on them,
 * so every stop must keep WCAG AA contrast with white (see [OnColorGradients]).
 */
internal val AvatarGradients: List<Pair<Color, Color>> = listOf(
    Color(0xFFB23A2F) to Color(0xFF9A2A22),
    Color(0xFF00695C) to Color(0xFF004D40),
    Color(0xFFA04500) to Color(0xFF8A4B00),
    Color(0xFF6A3DE8) to Color(0xFF4527A0),
    Color(0xFF1565C0) to Color(0xFF0D47A1),
    Color(0xFF2E7032) to Color(0xFF1B5E20),
    Color(0xFFAD1457) to Color(0xFF880E4F),
    Color(0xFFB23C17) to Color(0xFFA33A12),
)

/**
 * Fixed gradients for cards that carry **white** text or icons. They never come from `MaterialTheme.colorScheme`:
 * dark and dynamic schemes make `primary`/`tertiary` pale, which turns white text invisible.
 * Every stop keeps at least 4.5:1 contrast with white at 85 % opacity (guarded by `ColorContrastTest`).
 */
object OnColorGradients {
    /** Brand red, the only foreground colour that must read on a white chip inside these cards. */
    val BrandRed = Color(0xFFB4372F)
    val Violet = Color(0xFF6A3DE8)
    val Raspberry = Color(0xFFAD1457)

    /** Coral → raspberry → violet: the "I'm with…" hero and the year-in-review opener. */
    val Hero: List<Color> = listOf(BrandRed, Raspberry, Violet)

    /** Violet → raspberry → red: AI ideas. */
    val Ideas: List<Color> = listOf(Violet, Raspberry, BrandRed)
    val Teal: List<Color> = listOf(Color(0xFF00695C), Color(0xFF006064))
    val Indigo: List<Color> = listOf(Violet, Color(0xFF3949AB))
    val Sunset: List<Color> = listOf(Color(0xFFB23C17), Color(0xFFA0410D))
    val Amber: List<Color> = listOf(Color(0xFFA04500), Color(0xFF8D3A00))

    val all: List<List<Color>> = listOf(Hero, Ideas, Teal, Indigo, Sunset, Amber)
}
