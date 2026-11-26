package org.goodcompany.app.core.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.goodcompany.app.core.domain.model.AppSettings
import org.goodcompany.app.core.domain.repository.AppLanguagePlatform
import org.goodcompany.app.core.domain.repository.SettingsRepository
import org.goodcompany.app.core.domain.repository.StorageStatus

class ObserveSettingsUseCase(private val settings: SettingsRepository) {
    operator fun invoke(): Flow<AppSettings> = settings.settings
}

class UpdateSettingsUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(transform: (AppSettings) -> AppSettings) = settings.update(transform)
}

/**
 * Keeps the app's language (issue #174) and the platform's own per-app language setting showing the same choice.
 * [reconcile] takes a change made in the platform's settings (Android 13+, iOS) into the app; [invoke] does that once,
 * then applies every stored choice as it changes, while the app runs.
 */
class KeepLanguageInStepUseCase(private val settings: SettingsRepository, private val platform: AppLanguagePlatform) {
    suspend operator fun invoke() {
        reconcile()
        settings.settings.map { it.language }.distinctUntilChanged().collect { platform.apply(it) }
    }

    suspend fun reconcile() {
        val chosen = platform.systemChoice() ?: return
        settings.update { if (it.language == chosen) it else it.copy(language = chosen) }
    }
}

/** True when changes made now will be lost on reload (the web app open in another tab, or no browser storage). */
class IsStorageTemporaryUseCase(private val storage: StorageStatus) {
    suspend operator fun invoke(): Boolean = !storage.isPersistent()
}
