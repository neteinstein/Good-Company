package com.neteinstein.goodcompany.core.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.ui.platform.platformDynamicColorScheme

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * App theme. Light/dark follows [themeMode]; on Android 12+ [dynamicColor] uses the wallpaper palette.
 * Colour changes animate so switching theme in Settings feels smooth instead of flashing.
 */
@Composable
fun GoodCompanyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val target = platformDynamicColorScheme(dark).takeIf { dynamicColor } ?: if (dark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = target.animated(),
        typography = appTypography(),
        shapes = AppShapes,
        content = content,
    )
}

@Composable
private fun ColorScheme.animated(): ColorScheme {
    val spec = tween<androidx.compose.ui.graphics.Color>(durationMillis = 450)
    val primary by animateColorAsState(primary, spec)
    val primaryContainer by animateColorAsState(primaryContainer, spec)
    val secondaryContainer by animateColorAsState(secondaryContainer, spec)
    val tertiaryContainer by animateColorAsState(tertiaryContainer, spec)
    val background by animateColorAsState(background, spec)
    val surface by animateColorAsState(surface, spec)
    val surfaceContainer by animateColorAsState(surfaceContainer, spec)
    val surfaceContainerLow by animateColorAsState(surfaceContainerLow, spec)
    val surfaceContainerHigh by animateColorAsState(surfaceContainerHigh, spec)
    val onSurface by animateColorAsState(onSurface, spec)
    val onBackground by animateColorAsState(onBackground, spec)
    return copy(
        primary = primary,
        primaryContainer = primaryContainer,
        secondaryContainer = secondaryContainer,
        tertiaryContainer = tertiaryContainer,
        background = background,
        surface = surface,
        surfaceContainer = surfaceContainer,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerHigh = surfaceContainerHigh,
        onSurface = onSurface,
        onBackground = onBackground,
    )
}
