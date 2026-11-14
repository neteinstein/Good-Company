package com.neteinstein.goodcompany.core.data

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.neteinstein.goodcompany.core.data.db.AppDatabase
import com.neteinstein.goodcompany.core.data.db.DatabaseDriverFactory
import com.neteinstein.goodcompany.core.data.db.OpenedDriver
import com.neteinstein.goodcompany.core.data.repository.BackupRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.DeviceSettings
import com.neteinstein.goodcompany.core.data.repository.FriendRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.GroupRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.HangoutRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.IdeaRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.InteractionRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.LegacyJsonImport
import com.neteinstein.goodcompany.core.data.repository.PlanRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.ReminderRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.SettingsRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.SqlBackupRepository
import com.neteinstein.goodcompany.core.data.repository.SqlFriendRepository
import com.neteinstein.goodcompany.core.data.repository.SqlSettingsRepository
import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.data.storage.Keys
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.usecase.SampleData
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The one-off move from the JSON documents into SQLite (issue #14, step 3). */
class LegacyJsonImportTest {
    private val clock = FixedClock()
    private val settings = MapSettings()
    private val allLegacyKeys = listOf(
        Keys.FRIENDS, Keys.HANGOUTS, Keys.REMINDERS, Keys.INTERACTIONS, Keys.MESSAGES, Keys.IDEAS, Keys.SETTINGS, Keys.GROUPS, Keys.PLANS,
    )

    /** The keys [writeLegacyData] actually wrote (the sample circle has no call log or chat lines). */
    private lateinit var legacyKeys: List<String>

    /** One in-memory database for the whole test, so a second [AppDatabase] over it is an app restart. */
    private class Factory(var persistent: Boolean = true, private val setup: (SqlDriver) -> Unit = {}) : DatabaseDriverFactory {
        private var driver: SqlDriver? = null

        override suspend fun open(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): OpenedDriver {
            val d = driver ?: JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") }).also {
                schema.create(it).await()
                setup(it)
                driver = it
            }
            return OpenedDriver(d, persistent)
        }
    }

    /** What an installed app has today: everything written by the JSON repositories. */
    private suspend fun writeLegacyData(): Backup {
        val friends = FriendRepositoryImpl(settings, ContactsSource { emptyList() })
        val hangouts = HangoutRepositoryImpl(settings)
        val reminders = ReminderRepositoryImpl(settings)
        val interactions = InteractionRepositoryImpl(settings, CommunicationLogSource.None, clock)
        val ideas = IdeaRepositoryImpl(settings)
        val groups = GroupRepositoryImpl(settings)
        val plans = PlanRepositoryImpl(settings)
        val appSettings = SettingsRepositoryImpl(settings)
        val backup = BackupRepositoryImpl(friends, hangouts, reminders, interactions, ideas, groups, plans, appSettings)
        val sample = SampleData.build(clock)
        backup.restore(
            Backup(
                friends = sample.friends,
                hangouts = sample.hangouts,
                reminders = sample.reminders,
                ideas = sample.ideas,
                groups = sample.groups,
                plans = sample.plans,
            ),
        )
        appSettings.update {
            it.copy(aiApiKey = "sk-test", themeMode = ThemeMode.DARK, onboardingDone = true, homeArea = "Lisbon", reminderHour = 20)
        }
        legacyKeys = allLegacyKeys.filter(settings::hasKey)
        assertTrue(legacyKeys.size >= 7)
        return backup.snapshot()
    }

    private class Sql(val db: AppDatabase, val friends: SqlFriendRepository, val settings: SqlSettingsRepository, val backup: SqlBackupRepository)

    /** Builds the SQLite graph the way AppModule does: settings repository first, database opened on first use. */
    private fun openSql(factory: Factory): Sql {
        val db = AppDatabase(factory, clock, prepare = LegacyJsonImport(settings)::run)
        val appSettings = SqlSettingsRepository(db, settings)
        return Sql(db, SqlFriendRepository(db, ContactsSource { emptyList() }), appSettings, SqlBackupRepository(db, appSettings))
    }

    private suspend fun AppDatabase.meta(key: String) = access { it.dbMetaQueries.get(key).awaitAsOneOrNull() }

    @Test
    fun everythingMovesIntoTheDatabase_andTheJsonIsDeleted() = runTest {
        val legacy = writeLegacyData()
        val sql = openSql(Factory())

        val moved = sql.backup.snapshot()
        assertEquals(legacy.friends, moved.friends)
        assertEquals(legacy.hangouts, moved.hangouts)
        assertEquals(legacy.reminders, moved.reminders)
        assertEquals(legacy.ideas, moved.ideas)
        assertEquals(legacy.groups, moved.groups)
        assertEquals(legacy.plans.sortedBy { it.id }, moved.plans.sortedBy { it.id })
        // The JSON predates the AI switch: it comes on because a key was saved (issue #37).
        val expected = legacy.settings.copy(aiEnabled = true)
        assertEquals(expected, moved.settings)
        assertEquals(expected, sql.settings.settings.first())

        legacyKeys.forEach { assertFalse(settings.hasKey(it), "$it should be deleted") }
        assertNotNull(sql.db.meta(LegacyJsonImport.KEY_IMPORTED))
    }

    @Test
    fun deviceSettings_areReadableBeforeTheDatabaseOpens() = runTest {
        writeLegacyData()
        val factory = Factory()
        openSql(factory) // constructing the settings repository is enough
        val device = settings.getStringOrNull(DeviceSettings.KEY)
        assertNotNull(device)
        assertTrue("sk-test" in device && "DARK" in device)
        assertTrue(settings.hasKey(Keys.SETTINGS), "kept until the preferences are in the database")
    }

    @Test
    fun deviceSettingsSurvive_whenAnotherRepositoryOpensTheDatabaseFirst() = runTest {
        writeLegacyData()
        val db = AppDatabase(Factory(), clock, prepare = LegacyJsonImport(settings)::run)
        SqlFriendRepository(db, ContactsSource { emptyList() }).friends.first() // opens and imports
        assertFalse(settings.hasKey(Keys.SETTINGS))

        val restored = SqlSettingsRepository(db, settings).settings.first()
        assertEquals("sk-test", restored.aiApiKey)
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertTrue(restored.onboardingDone)
        assertEquals("Lisbon", restored.homeArea)
        assertEquals(20, restored.reminderHour)
    }

    @Test
    fun itRunsOnce_soDeletedFriendsDontComeBack() = runTest {
        writeLegacyData()
        val factory = Factory()
        val first = openSql(factory)
        val someone = first.friends.friends.first().first().id
        first.friends.delete(someone)

        // Leftover JSON (say the app stopped before the cleanup) must not be imported again: only deleted.
        settings.putString(Keys.FRIENDS, """[{"id":"${someone.value}","name":"Back"}]""")
        first.db.close()
        val again = openSql(factory)
        assertTrue(again.friends.friends.first().none { it.id == someone })
        assertFalse(settings.hasKey(Keys.FRIENDS))
    }

    @Test
    fun aTemporaryDatabase_importsButKeepsTheJson() = runTest {
        val legacy = writeLegacyData()
        val sql = openSql(Factory(persistent = false))
        assertEquals(legacy.friends, sql.friends.friends.first())
        legacyKeys.forEach { assertTrue(settings.hasKey(it), "$it should be kept") }
    }

    @Test
    fun unreadableJson_isKeptAndTheRestStillMoves() = runTest {
        writeLegacyData()
        settings.putString(Keys.HANGOUTS, "{not json")
        val sql = openSql(Factory())
        assertTrue(sql.friends.friends.first().isNotEmpty())
        assertTrue(sql.backup.snapshot().hangouts.isEmpty())
        assertTrue(settings.hasKey(Keys.HANGOUTS))
        assertFalse(settings.hasKey(Keys.FRIENDS))
    }

    @Test
    fun aFreshInstall_hasNothingToImport() = runTest {
        val sql = openSql(Factory())
        assertTrue(sql.friends.friends.first().isEmpty())
        assertNotNull(sql.db.meta(LegacyJsonImport.KEY_IMPORTED))
        assertNull(settings.getStringOrNull(Keys.SETTINGS))
    }

    @Test
    fun aFailedImport_keepsTheJson_andTheAppStillOpens() = runTest {
        writeLegacyData()
        val factory = Factory { driver ->
            driver.execute(null, "CREATE TRIGGER no_friends BEFORE INSERT ON friend BEGIN SELECT RAISE(ABORT, 'boom'); END", 0)
        }
        val sql = openSql(factory)

        assertTrue(sql.friends.friends.first().isEmpty())
        assertTrue(sql.db.meta(LegacyJsonImport.KEY_FAILED).orEmpty().contains("boom"))
        assertNull(sql.db.meta(LegacyJsonImport.KEY_IMPORTED))
        legacyKeys.forEach { assertTrue(settings.hasKey(it), "$it should be kept") }
        // And it isn't retried on the next start.
        sql.db.close()
        openSql(factory).friends.friends.first()
        legacyKeys.forEach { assertTrue(settings.hasKey(it)) }
    }
}
