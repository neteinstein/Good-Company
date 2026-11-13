package com.neteinstein.goodcompany.core.data.repository

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.neteinstein.goodcompany.core.data.db.GoodCompanyDatabase
import com.neteinstein.goodcompany.core.data.storage.Keys
import com.neteinstein.goodcompany.core.data.storage.PersistentState
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * One-off move of the JSON documents (`friends.v1`, …, `settings.v1`) into SQLite (issue #14, step 3).
 *
 * Runs while the database opens, before anything can read it. All of it lands in one transaction together with the
 * [KEY_IMPORTED] mark, so it happens exactly once. Then the JSON keys are deleted (owner decision 2026-11-13: delete
 * the old JSON once it's in the database), but only when the database really is stored on the device: a web tab
 * that only got an in-memory database (another tab has it open) imports for itself and leaves the JSON alone.
 *
 * Keys whose JSON can't be read are imported as empty and never deleted, so nothing is thrown away that a later fix
 * could still recover. If the import itself fails, the JSON stays and the failure is recorded in [KEY_FAILED]
 * instead of crashing on every start.
 */
class LegacyJsonImport(private val settings: Settings) {
    init {
        // Before the database can open (it's built with this import), whichever repository Koin happens to create first.
        takeOverDeviceSettings(settings)
    }

    suspend fun run(database: GoodCompanyDatabase, isPersistent: Boolean) {
        val meta = database.dbMetaQueries
        if (meta.get(KEY_FAILED).awaitAsOneOrNull() != null) return
        if (meta.get(KEY_IMPORTED).awaitAsOneOrNull() == null) {
            val legacy = read()
            try {
                database.transaction {
                    if (legacy.hasData) database.replaceAllData(legacy.backup)
                    meta.put(KEY_IMPORTED, legacy.summary)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                meta.put(KEY_FAILED, e.message ?: e::class.simpleName ?: "error")
                return
            }
            if (isPersistent) legacy.readable.forEach(settings::remove)
        } else if (isPersistent) {
            // Imported on an earlier start that stopped before the cleanup.
            val legacy = read()
            legacy.readable.forEach(settings::remove)
        }
    }

    private class Legacy(val backup: Backup, val readable: List<String>, val hasData: Boolean, val summary: String)

    private fun read(): Legacy {
        val readable = mutableListOf<String>()
        var present = 0

        fun <T> load(key: String, serializer: KSerializer<T>, default: T): T {
            val raw = settings.getStringOrNull(key) ?: return default
            present++
            val value = runCatching { PersistentState.DefaultJson.decodeFromString(serializer, raw) }.getOrNull()
            if (value != null) readable += key
            return value ?: default
        }

        val backup = Backup(
            friends = load(Keys.FRIENDS, ListSerializer(Friend.serializer()), emptyList()),
            hangouts = load(Keys.HANGOUTS, ListSerializer(Hangout.serializer()), emptyList()),
            reminders = load(Keys.REMINDERS, ListSerializer(Reminder.serializer()), emptyList()),
            interactions = load(Keys.INTERACTIONS, ListSerializer(Interaction.serializer()), emptyList()),
            messages = load(Keys.MESSAGES, ListSerializer(ConversationMessage.serializer()), emptyList()),
            ideas = load(Keys.IDEAS, ListSerializer(Idea.serializer()), emptyList()),
            groups = load(Keys.GROUPS, ListSerializer(FriendGroup.serializer()), emptyList()),
            plans = load(Keys.PLANS, ListSerializer(Plan.serializer()), emptyList()),
            settings = load(Keys.SETTINGS, AppSettings.serializer(), AppSettings()),
        )
        val summary = "friends=${backup.friends.size} hangouts=${backup.hangouts.size} keys=${readable.size}/$present"
        return Legacy(backup, readable, hasData = readable.isNotEmpty(), summary = summary)
    }

    companion object {
        /** db_meta key: set (with a short summary) in the import's transaction. */
        const val KEY_IMPORTED = "legacy_json_imported"

        /** db_meta key: why the import failed; while set, the import isn't retried and the JSON is kept. */
        const val KEY_FAILED = "legacy_json_import_failed"

        /**
         * Device settings (theme, app lock, onboarding, API key, …) must be readable before the database opens, so
         * they move from `settings.v1` to their own key straight away, synchronously. `settings.v1` itself stays
         * until [run] has put the rest of it (the preferences) in the database.
         */
        internal fun takeOverDeviceSettings(settings: Settings) {
            if (settings.hasKey(DeviceSettings.KEY)) return
            val raw = settings.getStringOrNull(Keys.SETTINGS) ?: return
            val old = runCatching { PersistentState.DefaultJson.decodeFromString(AppSettings.serializer(), raw) }.getOrNull() ?: return
            settings.putString(DeviceSettings.KEY, PersistentState.DefaultJson.encodeToString(DeviceSettings.serializer(), DeviceSettings.of(old)))
        }
    }
}
