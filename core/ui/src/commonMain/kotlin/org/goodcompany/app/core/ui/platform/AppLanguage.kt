package org.goodcompany.app.core.ui.platform

import org.goodcompany.app.core.domain.model.AppLanguage

/**
 * The language the platform's own per-app setting holds (issue #174): Android 13+'s app language, the iOS Settings
 * app's; null where there is no such setting (Android 12L, the web).
 */
internal expect fun platformLanguageChoice(): AppLanguage?

/** Hands the app's language to the platform, so its per-app setting, widgets and system text show the same one. */
internal expect fun applyPlatformLanguage(language: AppLanguage)
