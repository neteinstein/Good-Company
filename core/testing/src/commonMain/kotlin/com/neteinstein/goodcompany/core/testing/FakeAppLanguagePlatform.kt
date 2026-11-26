package com.neteinstein.goodcompany.core.testing

import com.neteinstein.goodcompany.core.domain.model.AppLanguage
import com.neteinstein.goodcompany.core.domain.repository.AppLanguagePlatform

/** The platform's per-app language setting (issue #174): [system] is what it holds; [applied] every language applied. */
class FakeAppLanguagePlatform(var system: AppLanguage? = null) : AppLanguagePlatform {
    val applied = mutableListOf<AppLanguage>()

    override fun systemChoice(): AppLanguage? = system

    override fun apply(language: AppLanguage) {
        applied += language
        if (system != null) system = language
    }
}
