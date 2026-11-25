package com.neteinstein.goodcompany.core.ui.platform

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.neteinstein.goodcompany.core.domain.model.AppLanguage
import java.util.Locale

private var languageContext: Context? = null

/** Lets the app's language reach Android 13+'s per-app language setting. Called from the Application's onCreate. */
fun initAppLanguage(context: Context) {
    languageContext = context.applicationContext
}

private fun localeManager(): LocaleManager? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) languageContext?.getSystemService(LocaleManager::class.java) else null

internal actual fun platformLanguageChoice(): AppLanguage? {
    val locales = localeManager()?.applicationLocales ?: return null
    return if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].toLanguageTag())
}

/**
 * Android 13+: the system's per-app language, so Settings → Apps → Language shows the same choice; Android re-creates
 * the activity in it, and the widget, shortcuts and notification channels follow. Android 12L has no such setting: the
 * process's default locale changes, which the app's screens and notifications read.
 */
internal actual fun applyPlatformLanguage(language: AppLanguage) {
    val manager = localeManager()
    if (manager != null) {
        val wanted = language.localeConfigTag()?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
        if (manager.applicationLocales != wanted) manager.applicationLocales = wanted
        return
    }
    val wanted = language.tag?.let(Locale::forLanguageTag) ?: Resources.getSystem().configuration.locales[0]
    if (Locale.getDefault() != wanted) Locale.setDefault(wanted)
}

/** The tags `generateLocaleConfig` lists (English is `en-US`, from `resources.properties`; the rest have no region). */
internal fun AppLanguage.localeConfigTag(): String? = when (this) {
    AppLanguage.SYSTEM -> null
    AppLanguage.ENGLISH -> "en-US"
    else -> language
}
