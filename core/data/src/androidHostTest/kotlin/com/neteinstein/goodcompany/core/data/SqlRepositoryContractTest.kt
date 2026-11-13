package com.neteinstein.goodcompany.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.neteinstein.goodcompany.core.data.db.AppDatabase
import com.neteinstein.goodcompany.core.data.db.DatabaseDriverFactory
import com.neteinstein.goodcompany.core.data.db.OpenedDriver
import com.neteinstein.goodcompany.core.data.repository.SqlBackupRepository
import com.neteinstein.goodcompany.core.data.repository.SqlFriendRepository
import com.neteinstein.goodcompany.core.data.repository.SqlGroupRepository
import com.neteinstein.goodcompany.core.data.repository.SqlHangoutRepository
import com.neteinstein.goodcompany.core.data.repository.SqlIdeaRepository
import com.neteinstein.goodcompany.core.data.repository.SqlInteractionRepository
import com.neteinstein.goodcompany.core.data.repository.SqlPlanRepository
import com.neteinstein.goodcompany.core.data.repository.SqlReminderRepository
import com.neteinstein.goodcompany.core.data.repository.SqlSettingsRepository
import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The SQLite repositories, on an in-memory database that stays open for the whole test, so [open] (new repositories
 * over the same data) is a restart. One [AppDatabase] for all of them, as in the app.
 */
class SqlRepositoryContractTest : RepositoryContract() {
    private val settings = MapSettings()
    private val factory = SharedInMemoryFactory()

    private class SharedInMemoryFactory : DatabaseDriverFactory {
        private var driver: SqlDriver? = null

        override suspend fun open(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): OpenedDriver {
            val existing = driver
            if (existing != null) return OpenedDriver(existing, isPersistent = true)
            val created = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
            schema.create(created).await()
            driver = created
            return OpenedDriver(created, isPersistent = true)
        }
    }

    private val db by lazy { AppDatabase(factory, clock) }

    override fun open(contacts: ContactsSource, writer: ContactsWriter, logs: CommunicationLogSource): Stores {
        val appSettings = SqlSettingsRepository(db, settings)
        return Stores(
            friends = SqlFriendRepository(db, contacts, writer),
            hangouts = SqlHangoutRepository(db),
            reminders = SqlReminderRepository(db),
            interactions = SqlInteractionRepository(db, logs, clock),
            ideas = SqlIdeaRepository(db),
            groups = SqlGroupRepository(db),
            plans = SqlPlanRepository(db),
            settings = appSettings,
            backup = SqlBackupRepository(db, appSettings),
        )
    }

    private val ana = FriendId("ana")
    private val rui = FriendId("rui")

    // --- Rules only the relational schema enforces ---

    @Test
    fun deletingAFriend_removesThemFromEverythingThatListsThem() = runTest {
        val s = open()
        s.friends.upsert(friend("ana"))
        s.friends.upsert(friend("rui"))
        s.hangouts.add(Hangout("h", listOf(ana, rui), at(2026, 3, 1)))
        s.reminders.upsert(Reminder("r", "Tickets", listOf(rui, ana), createdAt = at(2026, 3, 1)))
        s.groups.upsert(FriendGroup("g", "Crew", memberIds = listOf(ana, rui)))
        s.ideas.addAll(listOf(Idea("i", listOf(ana), IdeaKind.EVENT, "Jazz", "", createdAt = at(2026, 3, 1))))
        s.plans.upsert(Plan("p", listOf(ana, rui), "Lunch", LocalDate(2026, 3, 12), createdAt = at(2026, 3, 1)))
        s.interactions.addAll(listOf(Interaction(ana, InteractionType.CALL, at(2026, 3, 2)), Interaction(rui, InteractionType.CALL, at(2026, 3, 2))))
        s.interactions.addMessages(listOf(ConversationMessage(ana, at(2026, 3, 2), true, "hi")))

        s.friends.delete(ana)

        assertEquals(listOf(rui), s.hangouts.hangouts.first().single().friendIds)
        assertEquals(listOf(rui), s.reminders.reminders.first().single().friendIds)
        assertEquals(listOf(rui), s.groups.groups.first().single().memberIds)
        assertEquals(emptyList(), s.ideas.ideas.first().single().friendIds)
        assertEquals(listOf(rui), s.plans.plans.first().single().friendIds)
        assertEquals(listOf(rui), s.interactions.interactions.first().map { it.friendId })
        assertTrue(s.interactions.messages.first().isEmpty())
    }

    @Test
    fun referencesToThingsThatDontExist_areDroppedNotStored() = runTest {
        val s = open()
        s.friends.upsert(friend("ana") { copy(gifts = listOf(Gift("g", "Lego", forKidId = "no-such-kid"))) })
        s.hangouts.add(Hangout("h", listOf(ana, FriendId("ghost")), at(2026, 3, 1)))
        s.plans.upsert(Plan("p", listOf(ana), "Lunch", LocalDate(2026, 3, 12), ideaId = "no-such-idea", createdAt = at(2026, 3, 1)))
        s.interactions.addAll(listOf(Interaction(FriendId("ghost"), InteractionType.CALL, at(2026, 3, 2))))

        assertNull(s.friends.get(ana)!!.gifts.single().forKidId)
        assertEquals(listOf(ana), s.hangouts.hangouts.first().single().friendIds)
        assertNull(s.plans.plans.first().single().ideaId)
        assertTrue(s.interactions.interactions.first().isEmpty())
    }

    @Test
    fun aPlansIdea_isUnlinkedWhenTheIdeaIsTrimmed() = runTest {
        val s = open()
        s.ideas.addAll(listOf(Idea("i", emptyList(), IdeaKind.EVENT, "Jazz", "", createdAt = at(2026, 1, 1))))
        s.plans.upsert(Plan("p", emptyList(), "Jazz night", LocalDate(2026, 3, 12), ideaId = "i", createdAt = at(2026, 3, 1)))
        s.ideas.addAll((1..200).map { Idea("n$it", emptyList(), IdeaKind.EVENT, "Idea $it", "", createdAt = at(2026, 2, 1)) })

        assertNull(s.plans.plans.first().single().ideaId)
    }

    @Test
    fun observers_seeWritesFromAnotherRepository() = runTest {
        val s = open()
        s.friends.upsert(friend("ana"))
        val other = open()
        assertEquals(listOf(ana), other.friends.friends.first().map { it.id })
        s.backup.restore(Backup(friends = listOf(friend("rui"))))
        assertEquals(listOf(rui), other.friends.friends.first().map { it.id })
    }

    @Test
    fun aReadRightAfterAWrite_seesIt_evenWhileTheQueryIsShared() = runTest {
        val s = open()
        val watching = backgroundScope.launch(Dispatchers.Default) { s.friends.friends.collect { } }
        repeat(50) { i ->
            s.friends.upsert(friend("f$i"))
            assertEquals(i + 1, s.friends.friends.first().size)
            s.hangouts.add(Hangout("h$i", listOf(FriendId("f$i")), at(2026, 3, 1)))
            assertEquals(i + 1, s.hangouts.hangouts.first().size)
        }
        s.friends.delete(FriendId("f0"))
        assertTrue(s.hangouts.hangouts.first().first().friendIds.isEmpty())
        watching.cancel()
    }
}
