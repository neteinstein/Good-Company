package org.goodcompany.app.core.data.repository

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import org.goodcompany.app.core.data.storage.PersistentState
import org.goodcompany.app.core.domain.model.BackupProtection
import org.goodcompany.app.core.domain.repository.BackupKeyRepository

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
