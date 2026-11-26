package org.goodcompany.app.core.ui.l10n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.goodcompany.app.core.domain.model.AppLanguage
import org.goodcompany.app.core.domain.repository.AppLanguagePlatform
import org.goodcompany.app.core.ui.platform.applyPlatformLanguage
import org.goodcompany.app.core.ui.platform.platformLanguageChoice

/**
 * The app's language (issue #174). [apply] hands it to the platform ([applyPlatformLanguage]): the locale Compose and
 * Compose resources read (Android's default locale and, on 13+, its per-app language; iOS's AppleLanguages; the
 * browser's `navigator.languages` for this page). Strings read outside composition (notifications, iOS shortcut items)
 * follow at once; screens follow through [ProvideAppLanguage]. [AppLanguage.SYSTEM] is the device's language.
 */
object AppLocale : AppLanguagePlatform {
    /** The language applied last; [AppLanguage.SYSTEM] until [apply] runs. */
    var language: AppLanguage by mutableStateOf(AppLanguage.SYSTEM)
        private set

    override fun systemChoice(): AppLanguage? = platformLanguageChoice()

    override fun apply(language: AppLanguage) {
        applyPlatformLanguage(language)
        this.language = language
    }
}

/**
 * Re-reads every string under [content] when [AppLocale.language] changes, without losing any screen state.
 * Compose resources look the locale up again whenever they recompose and read [LocalDensity], so a fresh (but equal)
 * density for each language is enough to make them pick up the new one.
 */
@Composable
fun ProvideAppLanguage(content: @Composable () -> Unit) {
    val language = AppLocale.language
    val density = LocalDensity.current
    val perLanguage = remember(language, density) { LanguageDensity(density) }
    CompositionLocalProvider(LocalDensity provides perLanguage, content = content)
}

/** The same density, but a new instance per language (no `equals`), so [LocalDensity] readers recompose. */
private class LanguageDensity(base: Density) : Density by base
