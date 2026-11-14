package com.neteinstein.goodcompany.core.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.neteinstein.goodcompany.core.data.db.AppDatabase
import com.neteinstein.goodcompany.core.data.db.DatabaseDriverFactory
import com.neteinstein.goodcompany.core.data.db.OpenedDriver
import com.neteinstein.goodcompany.core.data.repository.SqlBackupRepository
import com.neteinstein.goodcompany.core.data.repository.SqlCalendarDecisionRepository
import com.neteinstein.goodcompany.core.data.repository.SqlFriendRepository
import com.neteinstein.goodcompany.core.data.repository.SqlGiftListRepository
import com.neteinstein.goodcompany.core.data.repository.SqlGroupRepository
import com.neteinstein.goodcompany.core.data.repository.SqlHangoutRepository
import com.neteinstein.goodcompany.core.data.repository.SqlIdeaRepository
import com.neteinstein.goodcompany.core.data.repository.SqlInteractionRepository
import com.neteinstein.goodcompany.core.data.repository.SqlPersonLinkRepository
import com.neteinstein.goodcompany.core.data.repository.SqlPlanRepository
import com.neteinstein.goodcompany.core.data.repository.SqlReminderRepository
import com.neteinstein.goodcompany.core.data.repository.SqlSettingsRepository
import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.CalendarDecision
import com.neteinstein.goodcompany.core.domain.model.CalendarDecisionKind
import com.neteinstein.goodcompany.core.domain.model.CardKid
import com.neteinstein.goodcompany.core.domain.model.CardLink
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftList
import com.neteinstein.goodcompany.core.domain.model.GiftListEntry
import com.neteinstein.goodcompany.core.domain.model.GiftListItem
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.GiftStatus
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import com.neteinstein.goodcompany.core.testing.FakeContactsWriter
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

    // --- Gift lists (F8) ---

    @Test
    fun giftLists_saveWhole_skipUnknownPeople_goWithAFriend_andTravelInBackups() = runTest {
        val s = open()
        s.friends.upsert(friend("ana") { copy(gifts = listOf(Gift("lego", "LEGO", price = "€35"), Gift("book", "Book"))) })
        s.friends.upsert(friend("rui"))
        val lists = SqlGiftListRepository(db)
        val list = GiftList(
            id = "xmas",
            title = "Christmas 2026",
            occasionDate = LocalDate(2026, 12, 25),
            createdAt = at(2026, 11, 1),
            occasion = GiftOccasion.CHRISTMAS,
            entries = listOf(
                GiftListEntry(ana, 40.5, listOf(GiftListItem("lego", GiftStatus.BOUGHT), GiftListItem("book"))),
                GiftListEntry(rui),
                GiftListEntry(FriendId("ghost"), 10.0, listOf(GiftListItem("x"))),
            ),
        )
        lists.upsert(list)
        val stored = list.copy(entries = list.entries.take(2))
        assertEquals(listOf(stored), lists.lists.first())
        assertEquals(stored, lists.get("xmas"))

        // Saved whole: a smaller list replaces the bigger one.
        val smaller = stored.copy(title = "Natal 2026", occasion = null, occasionDate = null, entries = listOf(GiftListEntry(rui, 20.0)))
        lists.upsert(smaller)
        assertEquals(listOf(smaller), SqlGiftListRepository(db).lists.first())

        // Saving a friend (their gifts are rewritten) keeps their place on the list; deleting them takes it away.
        lists.upsert(stored)
        s.friends.upsert(s.friends.get(ana)!!.copy(notes = "likes dinosaurs"))
        assertEquals(stored, lists.get("xmas"))
        val snapshot = s.backup.snapshot()
        assertEquals(listOf(stored), snapshot.giftLists)
        s.friends.delete(rui)
        assertEquals(listOf(ana), lists.get("xmas")!!.entries.map { it.friendId })

        s.backup.restore(snapshot)
        assertEquals(listOf(stored), open().let { SqlGiftListRepository(db).lists.first() })
        lists.delete("xmas")
        assertNull(lists.get("xmas"))
    }

    // --- Calendar suggestions (D3) ---

    @Test
    fun calendarDecisions_persistAndTravelInBackupsButTheCalendarChoicesStayOnTheDevice() = runTest {
        val s = open()
        val decisions = SqlCalendarDecisionRepository(db)
        val no = CalendarDecision("event:1:1700@1700", CalendarDecisionKind.NO, at(2026, 3, 1))
        val never = CalendarDecision("series:bookclub", CalendarDecisionKind.NEVER, at(2026, 3, 2))
        decisions.upsert(no)
        decisions.upsert(never)
        decisions.upsert(no.copy(decidedAt = at(2026, 3, 3)))
        s.settings.update { it.copy(calendarSuggestions = true, calendarIds = listOf("7"), calendarOnSince = at(2026, 3, 1)) }

        // A restart keeps both.
        val again = open()
        assertEquals(listOf("series:bookclub", "event:1:1700@1700"), SqlCalendarDecisionRepository(db).decisions.first().map { it.key })
        val settings = again.settings.settings.first()
        assertTrue(settings.calendarSuggestions)
        assertEquals(listOf("7"), settings.calendarIds)

        val snapshot = again.backup.snapshot()
        assertEquals(2, snapshot.calendarDecisions.size)
        decisions.delete(never.key)
        assertEquals(listOf(no.key), decisions.decisions.first().map { it.key })

        again.backup.restore(snapshot)
        assertEquals(2, decisions.decisions.first().size)
        again.backup.restore(Backup())
        assertTrue(decisions.decisions.first().isEmpty())
    }

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
        s.interactions.upsertJournal(JournalEntry("j", ana, at(2026, 3, 2), "Porto"))

        s.friends.delete(ana)

        assertEquals(listOf(rui), s.hangouts.hangouts.first().single().friendIds)
        assertEquals(listOf(rui), s.reminders.reminders.first().single().friendIds)
        assertEquals(listOf(rui), s.groups.groups.first().single().memberIds)
        assertEquals(emptyList(), s.ideas.ideas.first().single().friendIds)
        assertEquals(listOf(rui), s.plans.plans.first().single().friendIds)
        assertEquals(listOf(rui), s.interactions.interactions.first().map { it.friendId })
        assertTrue(s.interactions.messages.first().isEmpty())
        assertTrue(s.interactions.journal.first().isEmpty())
    }

    @Test
    fun referencesToThingsThatDontExist_areDroppedNotStored() = runTest {
        val s = open()
        // An old-style gift for a kid (issue #40) isn't stored with its kid: kids are people now.
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

    // --- Links between people (issue #39): SQL only (the JSON stores never had them) ---

    @Test
    fun links_areStoredOnce_skipUnknownPeople_goWithAPerson_andSurviveABackup() = runTest {
        val s = open()
        val links = SqlPersonLinkRepository(db)
        s.friends.upsert(friend("ana") { copy(relationToMe = LinkType.SIBLING, pronouns = Pronouns.SHE) })
        s.friends.upsert(friend("rui"))
        val couple =
            PersonLink(
                "l1",
                rui,
                ana,
                LinkType.DATING,
                LocalDate(2019, 6, 1),
                note = "met at uni",
                remindAnniversary = true,
                addedAt = at(2026, 3, 1),
            )
        links.upsert(couple)
        links.upsert(PersonLink("ghost", rui, FriendId("ghost"), LinkType.FRIEND))
        links.upsert(PersonLink("self", rui, rui, LinkType.FRIEND))
        assertEquals(listOf(couple), links.links.first())
        val ex = couple.copy(type = LinkType.EX, endedOn = LocalDate(2024, 1, 1), remindAnniversary = false)
        links.upsert(ex)
        assertEquals(listOf(ex), SqlPersonLinkRepository(db).links.first())

        val friendAna = s.friends.get(ana)!!
        assertEquals(LinkType.SIBLING, friendAna.relationToMe)
        assertEquals(Pronouns.SHE, friendAna.pronouns)

        val backup = s.backup.snapshot()
        assertEquals(listOf(ex), backup.links)
        s.friends.delete(ana)
        assertTrue(links.links.first().isEmpty())
        s.backup.restore(backup)
        assertEquals(listOf(ex), links.links.first())
        links.delete("l1")
        assertTrue(links.links.first().isEmpty())
    }

    @Test
    fun aLinkIsWrittenOntoBothContacts_andARenameReachesTheOtherContact() = runTest {
        val writer = FakeContactsWriter()
        val s = open(writer = writer)
        val links = SqlPersonLinkRepository(db, writer)
        s.friends.upsert(friend("ana", "Ana Silva") { copy(contactId = "c-ana", pronouns = Pronouns.SHE) })
        s.friends.upsert(friend("rui") { copy(contactId = "c-rui") })
        s.friends.upsert(friend("leo")) // no contact: never written
        links.upsert(PersonLink("l1", rui, ana, LinkType.DATING))
        links.upsert(PersonLink("l2", rui, FriendId("leo"), LinkType.CHILD))
        // A kid is written once, as a child row, not also as a relation.
        assertEquals(listOf(CardLink("Ana Silva", LinkType.DATING, Pronouns.SHE)), writer.written[rui]!!.links)
        assertEquals(listOf(CardKid("Leo")), writer.written[rui]!!.kids)
        assertEquals(listOf(CardLink("Rui", LinkType.DATING)), writer.written[ana]!!.links)
        assertNull(writer.written[FriendId("leo")])

        val before = writer.writes
        links.upsert(PersonLink("l1", rui, ana, LinkType.DATING)) // nothing changed
        assertEquals(before, writer.writes)

        s.friends.upsert(s.friends.get(ana)!!.copy(name = "Ana Costa"))
        assertEquals("Ana Costa", writer.written[rui]!!.links.first().name)
    }
}
