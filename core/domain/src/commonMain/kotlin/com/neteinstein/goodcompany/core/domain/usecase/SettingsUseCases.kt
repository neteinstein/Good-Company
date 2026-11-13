package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.repository.StorageStatus
import kotlinx.coroutines.flow.Flow

class ObserveSettingsUseCase(private val settings: SettingsRepository) {
    operator fun invoke(): Flow<AppSettings> = settings.settings
}

class UpdateSettingsUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(transform: (AppSettings) -> AppSettings) = settings.update(transform)
}

/** True when changes made now will be lost on reload (the web app open in another tab, or no browser storage). */
class IsStorageTemporaryUseCase(private val storage: StorageStatus) {
    suspend operator fun invoke(): Boolean = !storage.isPersistent()
}
