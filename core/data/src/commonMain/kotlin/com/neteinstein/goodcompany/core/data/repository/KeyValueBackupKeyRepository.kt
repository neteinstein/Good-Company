package com.neteinstein.goodcompany.core.data.repository

import com.neteinstein.goodcompany.core.data.storage.PersistentState
import com.neteinstein.goodcompany.core.domain.model.BackupProtection
import com.neteinstein.goodcompany.core.domain.repository.BackupKeyRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow

/**
 * D4 (issue #165): the backup key, kept on this device in key-value storage like the API key (Android leaves it out
 * of cloud backup: `data_extraction_rules.xml` excludes all shared preferences). Never in the database, so never in a
 * backup either.
 */
class KeyValueBackupKeyRepository(settings: Settings) : BackupKeyRepository {
    private val state = PersistentState(settings, KEY, BackupProtection.serializer(), BackupProtection())

    override val protection: Flow<BackupProtection> = state.flow

    override suspend fun current(): BackupProtection = state.value

    override suspend fun update(transform: (BackupProtection) -> BackupProtection) = state.update(transform)

    companion object {
        const val KEY = "backup_key.v1"
    }
}
