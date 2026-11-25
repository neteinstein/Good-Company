package com.neteinstein.goodcompany.core.ui.platform

import com.neteinstein.goodcompany.core.domain.model.AppLanguage

/** Browsers have no per-site language setting. */
internal actual fun platformLanguageChoice(): AppLanguage? = null

/**
 * Compose and Compose resources read `navigator.languages` / `navigator.language`: this page's copy of them is set to
 * the chosen language (removed again for System default), and so is the page's `lang`.
 */
internal actual fun applyPlatformLanguage(language: AppLanguage) = setPageLanguage(language.tag ?: "")

private fun setPageLanguage(tag: String): Unit = js(
    """{
        try {
            if (tag) {
                Object.defineProperty(navigator, "language", { configurable: true, get: function () { return tag; } });
                Object.defineProperty(navigator, "languages", { configurable: true, get: function () { return [tag]; } });
            } else {
                delete navigator.language;
                delete navigator.languages;
            }
        } catch (e) {}
        document.documentElement.lang = navigator.language || "en";
    }""",
)
