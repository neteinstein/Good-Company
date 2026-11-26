package com.neteinstein.goodcompany.core.ui.platform

import com.neteinstein.goodcompany.core.domain.model.AppLanguage
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

/** The key the iOS Settings app keeps an app's own language under, in the app's defaults. */
private const val APPLE_LANGUAGES = "AppleLanguages"

internal actual fun platformLanguageChoice(): AppLanguage? {
    val bundleId = NSBundle.mainBundle.bundleIdentifier ?: return null
    val own = NSUserDefaults.standardUserDefaults.persistentDomainForName(bundleId)?.get(APPLE_LANGUAGES) as? List<*>
    return AppLanguage.fromTag(own?.firstOrNull() as? String)
}

/**
 * The app's own AppleLanguages: the iOS Settings app shows it as this app's language, and `NSLocale.preferredLanguages`
 * (what Compose and Compose resources read) follows it at once. The permission prompts and the widget follow from the
 * next launch. System default removes the app's own value, so the device's languages show through again.
 */
internal actual fun applyPlatformLanguage(language: AppLanguage) {
    val defaults = NSUserDefaults.standardUserDefaults
    val tag = language.tag
    if (tag == null) defaults.removeObjectForKey(APPLE_LANGUAGES) else defaults.setObject(listOf(tag), APPLE_LANGUAGES)
}
