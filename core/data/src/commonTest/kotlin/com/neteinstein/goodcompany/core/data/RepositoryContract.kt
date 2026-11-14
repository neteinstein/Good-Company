package com.neteinstein.goodcompany.core.data

import com.neteinstein.goodcompany.core.data.repository.BackupRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.FriendRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.GroupRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.HangoutRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.IdeaRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.InteractionRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.PlanRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.ReminderRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.SettingsRepositoryImpl
import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.data.source.LogEntry
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.ContactChannel
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.CustomField
import com.neteinstein.goodcompany.core.domain.model.FeedItem
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendEmail
import com.neteinstein.goodcompany.core.domain.model.FriendFeed
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.Introducer
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.MetInfo
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.PhotoPlatform
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.Relative
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.repository.BackupRepository
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.usecase.ExportBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreResult
import com.neteinstein.goodcompany.core.domain.usecase.SampleData
import com.neteinstein.goodcompany.core.testing.FakeContactsWriter
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Behaviour every storage implementation must share (issue #14: JSON documents today, SQLite next). Each subclass
 * opens repositories over storage that lives as long as the test instance, so [open] twice = an app restart.
 */
abstract class RepositoryContract {
    class Stores(
        val friends: FriendRepository,
        val hangouts: HangoutRepository,
        val reminders: ReminderRepository,
        val interactions: InteractionRepository,
        val ideas: IdeaRepository,
        val groups: GroupRepository,
        val plans: PlanRepository,
        val settings: SettingsRepository,
        val backup: BackupRepository,
    )

    protected val clock = FixedClock()

    protected abstract fun open(
        contacts: ContactsSource = ContactsSource { emptyList() },
        writer: ContactsWriter = ContactsWriter.None,
        logs: CommunicationLogSource = CommunicationLogSource.None,
    ): Stores

    private class StubContacts(var records: List<ContactRecord>, override val importsNewContacts: Boolean = true) : ContactsSource {
        override suspend fun readContacts(withPhotos: Boolean) = records
    }

    private val ana = FriendId("ana")
    private val rui = FriendId("rui")

    /** A friend with every field set to something other than its default. */
    protected fun richFriend(id: String = "ana") = Friend(
        id = FriendId(id),
        name = "Ana Sousa",
        contactId = "c-$id",
        phoneNumbers = listOf("+351 912 345 678", "21 000 0000"),
        birthday = Birthday(3, 9, 1990),
        isCloseFriend = true,
        notifyBirthday = false,
        likes = listOf("jazz", "sushi"),
        dislikes = listOf("cilantro"),
        notes = "Met at uni\nLoves hiking",
        importantEvents = listOf(ImportantEvent("e1", "Wedding", LocalDate(2026, 6, 20), remindDaysBefore = 7, addedAt = at(2026, 1, 3))),
        meetEveryDays = 14,
        suggestEvents = true,
        isOrphaned = true,
        closeness = Closeness.INNER,
        snoozedUntil = LocalDate(2026, 4, 1),
        gifts = listOf(
            Gift("g1", "Book", "Murakami", givenOn = LocalDate(2025, 12, 25), occasion = GiftOccasion.CHRISTMAS),
            Gift("g2", "Lego set", url = "https://shop.example/lego", price = "€35"),
        ),
        catchUpPaused = true,
        contactCountsAsCatchUp = true,
        occasions = listOf(Occasion.DINNER, Occasion.COFFEE),
        lifeUpdates = listOf(LifeUpdate("u1", "New job", at(2026, 2, 1))),
        likesAddedAt = mapOf("sushi" to at(2026, 2, 2)),
        feeds = listOf(
            FriendFeed(
                url = "https://ana.example/feed.xml",
                title = "Ana's blog",
                siteUrl = "https://ana.example",
                checkedAt = at(2026, 3, 1),
                items = listOf(FeedItem("i1", "Hello", "https://ana.example/hello", at(2026, 2, 28)), FeedItem("i2", "Draft")),
            ),
        ),
        photo = ContactPhoto.of(byteArrayOf(-1, -40, -1, -32, 1, 2, 3)),
        job = "Designer",
        livesIn = "Porto",
        met = MetInfo("Erasmus in Ghent", LocalDate(2011, 9, 1), dayKnown = false),
        introducedBy = Introducer(FriendId("rui"), "Rui"),
        preferredChannel = ContactChannel.WHATSAPP,
        emails = listOf(FriendEmail("ana@work.example", fromContact = true), FriendEmail("ana@home.example")),
        relatives = listOf(
            Relative(
                "r1",
                Relation.PARTNER,
                "Tiago",
                Birthday(7, 2, 1988),
                listOf("padel", "F1"),
                "",
                notifyBirthday = true,
                addedAt = at(2026, 2, 3),
            ),
            Relative("r2", Relation.PET, "Bolacha", note = "beagle"),
        ),
        food = listOf("sushi", "pastéis de nata"),
        drinks = listOf("Alvarinho"),
        interests = listOf("ceramics", "trail running"),
        foodAddedAt = mapOf("pastéis de nata" to at(2026, 2, 4)),
        drinksAddedAt = mapOf("Alvarinho" to at(2026, 2, 5)),
        interestsAddedAt = mapOf("trail running" to at(2026, 2, 6)),
        customFields = listOf(CustomField("f1", "Shoe size", "38"), CustomField("f2", "Club", "Benfica")),
        relationToMe = LinkType.COUSIN,
        pronouns = Pronouns.THEY,
        customName = "Aninhas",
        customPhoto = ContactPhoto.of(byteArrayOf(-1, -40, -1, -32, 9, 9)),
        showInMyList = false,
    )

    // --- Friends ---

    @Test
    fun aFriendWithEveryFieldSet_comesBackExactlyAfterARestart() = runTest {
        val friend = richFriend()
        open().friends.upsert(friend)
        val again = open().friends
        assertEquals(friend, again.get(friend.id))
        assertEquals(listOf(friend), again.friends.first())
    }

    @Test
    fun aHowWeMetDate_keepsWhetherTheDayIsKnown() = runTest {
        val repo = open().friends
        repo.upsert(friend("a") { copy(met = MetInfo("", LocalDate(2011, 9, 4))) })
        repo.upsert(friend("b") { copy(met = MetInfo("Uni", null)) })
        val back = open().friends.friends.first().associateBy { it.id.value }
        assertEquals(MetInfo("", LocalDate(2011, 9, 4), dayKnown = true), back.getValue("a").met)
        assertEquals(MetInfo("Uni", null), back.getValue("b").met)
    }

    @Test
    fun friends_keepTheirOrder_andAnEditKeepsItsPlace() = runTest {
        val repo = open().friends
        repo.upsert(friend("ana", "Ana"))
        repo.upsert(friend("rui", "Rui"))
        repo.upsert(friend("eva", "Eva"))
        repo.upsert(friend("ana", "Ana 2") { copy(likes = listOf("jazz")) })
        assertEquals(listOf("Ana 2", "Rui", "Eva"), open().friends.friends.first().map { it.name })

        repo.delete(rui)
        assertEquals(listOf("ana", "eva"), repo.friends.first().map { it.id.value })
        assertNull(repo.get(rui))
    }

    @Test
    fun readingTheContactList_changesNoFriend_andPhotosComeOnlyForTheAsked() = runTest {
        val photo = ContactPhoto.of(byteArrayOf(4, 5, 6))!!
        val contacts = StubContacts(
            listOf(ContactRecord("1", "Ana", emptyList(), null, photo), ContactRecord("2", "Rui", emptyList(), null, photo)),
            importsNewContacts = false,
        )
        val repo = open(contacts).friends
        assertEquals(listOf("Ana", "Rui"), repo.readContacts().map { it.name })
        assertTrue(repo.friends.first().isEmpty())
        assertEquals(mapOf("2" to photo), repo.contactPhotos(setOf("2", "nobody")))
        assertTrue(repo.contactPhotos(emptySet()).isEmpty())
    }

    @Test
    fun anEmptyContactRead_neverWipesFriends() = runTest {
        val contacts = StubContacts(listOf(ContactRecord("1", "Ana", emptyList(), null)))
        val repo = open(contacts).friends
        assertEquals(1, repo.syncContacts())
        contacts.records = emptyList()
        assertEquals(0, repo.syncContacts())
        assertEquals(1, repo.friends.first().size)
    }

    @Test
    fun aPickerOnlySource_refreshesKnownFriendsButNeverImportsTheAddressBook() = runTest {
        val contacts = StubContacts(
            listOf(ContactRecord("1", "Ana", listOf("912345678"), Birthday(1, 2)), ContactRecord("2", "Bruno", emptyList(), null)),
            importsNewContacts = false,
        )
        val repo = open(contacts).friends
        repo.syncContacts()
        assertTrue(repo.friends.first().isEmpty())

        repo.upsert(friend("contact-1") { copy(contactId = "1", name = "Old", likes = listOf("jazz")) })
        repo.syncContacts()
        val synced = open(contacts).friends.friends.first().single()
        assertEquals("Ana", synced.name)
        assertEquals(listOf("912345678"), synced.phoneNumbers)
        assertEquals(listOf("jazz"), synced.likes)
    }

    @Test
    fun contactPhotos_followTheContact_butAContactWithoutOneNeverClearsIt() = runTest {
        val first = ContactPhoto.of(byteArrayOf(1, 2, 3))
        val second = ContactPhoto.of(byteArrayOf(4, 5, 6))
        val contacts = StubContacts(listOf(ContactRecord("1", "Ana", emptyList(), null, first)), importsNewContacts = false)
        val repo = open(contacts).friends
        repo.upsert(friend("contact-1") { copy(contactId = "1") })

        repo.syncContacts()
        assertEquals(first, open(contacts).friends.friends.first().single().photo)

        contacts.records = listOf(ContactRecord("1", "Ana", emptyList(), null, second))
        repo.syncContacts()
        assertEquals(second, open(contacts).friends.friends.first().single().photo)

        contacts.records = listOf(ContactRecord("1", "Ana", emptyList(), null, photo = null))
        repo.syncContacts()
        assertEquals(second, open(contacts).friends.friends.first().single().photo)

        repo.upsert(repo.friends.first().single().copy(photo = null))
        assertNull(open(contacts).friends.friends.first().single().photo)
    }

    // --- Contact write-back ---

    @Test
    fun writeBack_onlyWhenWhatTheContactHoldsChanges() = runTest {
        val writer = FakeContactsWriter()
        val repo = open(writer = writer).friends
        val first = friend("ana") { copy(contactId = "c-ana", likes = listOf("jazz")) }
        repo.upsert(first)
        repo.upsert(first.copy(isCloseFriend = true, meetEveryDays = 14))
        assertEquals(1, writer.writes)

        repo.upsert(first.copy(likes = listOf("jazz", "sushi")))
        assertEquals(2, writer.writes)
        assertEquals(listOf("jazz", "sushi"), writer.written.getValue(ana).likes)

        repo.upsert(friend("manual") { copy(notes = "x") })
        assertEquals(2, writer.writes)
    }

    @Test
    fun writeBack_syncBackfillsDetails_butNotOrphanedOrBirthdayOnlyFriends() = runTest {
        val writer = FakeContactsWriter()
        val contacts = StubContacts(
            listOf(ContactRecord("c-kid", "Kid", emptyList(), null), ContactRecord("c-bday", "Bday", emptyList(), Birthday(1, 2))),
        )
        val stores = open(contacts, writer)
        // Saved while the contact wasn't writable (a restore never writes to contacts).
        stores.backup.restore(
            Backup(
                friends = listOf(
                    friend("kid") { copy(contactId = "c-kid", likes = listOf("Maria")) },
                    friend("bday") { copy(contactId = "c-bday", birthday = Birthday(1, 2)) },
                    friend("gone") { copy(contactId = "c-gone", notes = "x") },
                ),
            ),
        )
        assertEquals(0, writer.writes)

        stores.friends.syncContacts()
        assertEquals(setOf(FriendId("kid")), writer.written.keys)
        assertEquals(1, writer.writes)
    }

    @Test
    fun writeBack_aFailingContactStoreNeverBlocksSaving_butCancellationPropagates() = runTest {
        val failing = object : ContactsWriter {
            override suspend fun write(contactId: String, friendId: FriendId, card: ContactCard) = error("provider crashed")
        }
        val repo = open(writer = failing).friends
        val f = friend("ana") { copy(contactId = "c-ana", notes = "keep me") }
        repo.upsert(f)
        assertEquals("keep me", repo.get(ana)?.notes)

        val cancelling = object : ContactsWriter {
            override suspend fun write(contactId: String, friendId: FriendId, card: ContactCard) = throw CancellationException("stop")
        }
        assertFailsWith<CancellationException> { open(writer = cancelling).friends.upsert(f.copy(notes = "changed")) }
    }

    // --- Hangouts, reminders, groups, ideas, plans ---

    @Test
    fun hangoutsAndReminders_persistInOrderAndCanBeDeleted() = runTest {
        val s = open()
        s.friends.upsert(friend("ana"))
        s.friends.upsert(friend("rui"))
        val h1 = Hangout("h1", listOf(rui, ana), at(2026, 3, 1), Place("Café", 38.7, -9.1), "Pastéis")
        val h2 = Hangout("h2", listOf(ana), at(2026, 2, 1))
        val h3 = Hangout("h3", listOf(ana), at(2026, 3, 2), Place(latitude = 1.0, longitude = 2.0))
        s.hangouts.add(h1)
        s.hangouts.add(h2)
        s.hangouts.add(h3)
        s.hangouts.delete("h2")
        assertEquals(listOf(h1, h3), open().hangouts.hangouts.first())

        val r1 = Reminder("r1", "Tickets", listOf(ana, rui), at(2026, 3, 5), done = false, createdAt = at(2026, 3, 1))
        val r2 = Reminder("r2", "Call", createdAt = at(2026, 3, 2))
        s.reminders.upsert(r1)
        s.reminders.upsert(r2)
        s.reminders.upsert(r1.copy(done = true))
        assertEquals(listOf(r1.copy(done = true), r2), open().reminders.reminders.first())
        s.reminders.delete("r1")
        assertEquals(listOf(r2), s.reminders.reminders.first())
    }

    @Test
    fun namedPlaces_persist_renameAndMergeRelinkMeetups_deleteLeavesThem() = runTest {
        val s = open()
        s.friends.upsert(friend("ana"))
        val mum = NamedPlace("mum", "Mum’s", 38.7139, -9.1394)
        val graca = NamedPlace("graca", "Graça’s place", 38.7143, -9.1394, radiusM = 150)
        s.hangouts.upsertPlace(mum)
        s.hangouts.upsertPlace(graca)
        s.hangouts.upsertPlace(mum.copy(latitude = 38.714)) // an update keeps its place in the list
        assertEquals(listOf(mum.copy(latitude = 38.714), graca), open().hangouts.places.first())

        s.hangouts.add(Hangout("h1", listOf(ana), at(2026, 3, 1), Place("Mum’s", 38.7139, -9.1394, "mum")))
        s.hangouts.add(Hangout("h2", listOf(ana), at(2026, 3, 2), Place("Graça’s place", 38.7143, -9.1394, "graca")))
        s.hangouts.add(Hangout("h3", listOf(ana), at(2026, 3, 3), Place("Mum’s")))
        s.hangouts.add(Hangout("h4", listOf(ana), at(2026, 3, 4), Place(placeId = "graca")))
        assertEquals("graca", open().hangouts.hangouts.first().last().place?.placeId)

        s.hangouts.renamePlace("mum", "Mum & Dad’s")
        s.hangouts.renamePlace("nowhere", "x") // unknown: nothing changes
        assertEquals(listOf("Mum & Dad’s", "Graça’s place", "Mum’s", null), open().hangouts.hangouts.first().map { it.place?.name })
        assertEquals("Mum & Dad’s", open().hangouts.places.first().first().name)

        s.hangouts.mergePlaces("graca", "mum")
        val merged = open().hangouts.hangouts.first()
        assertEquals(listOf("mum", "mum", null, "mum"), merged.map { it.place?.placeId })
        assertEquals(listOf("Mum & Dad’s", "Mum & Dad’s", "Mum’s", "Mum & Dad’s"), merged.map { it.place?.name })
        assertEquals(listOf(38.7143, -9.1394), merged[1].place.let { listOf(it?.latitude, it?.longitude) }) // coordinates kept
        assertEquals(listOf("mum"), open().hangouts.places.first().map { it.id })

        s.hangouts.deletePlace("mum")
        assertTrue(open().hangouts.places.first().isEmpty())
        assertEquals(listOf("mum", "mum", null, "mum"), open().hangouts.hangouts.first().map { it.place?.placeId })
    }

    @Test
    fun groups_persistAndUpdateInPlace() = runTest {
        val s = open()
        s.friends.upsert(friend("ana"))
        s.friends.upsert(friend("rui"))
        val crew = FriendGroup("g", "Crew", "🎲", listOf(ana, rui), 30, LocalDate(2026, 4, 1))
        s.groups.upsert(crew)
        s.groups.upsert(FriendGroup("h", "Family"))
        s.groups.upsert(crew.copy(name = "Crew 2", memberIds = listOf(rui)))
        assertEquals(listOf(crew.copy(name = "Crew 2", memberIds = listOf(rui)), FriendGroup("h", "Family")), open().groups.groups.first())
        s.groups.delete("g")
        assertEquals(listOf("h"), s.groups.groups.first().map { it.id })
    }

    @Test
    fun ideas_dismissMarkProposedAndKeepTheLatest200() = runTest {
        val s = open()
        s.friends.upsert(friend("ana"))
        val jazz = Idea("i", listOf(ana), IdeaKind.EVENT, "Jazz", "At the park", "https://x.example", "Saturday", at(2026, 1, 1))
        s.ideas.addAll(listOf(jazz))
        s.ideas.dismiss("i")
        s.ideas.markProposed("i", at(2026, 1, 2))
        assertEquals(listOf(jazz.copy(dismissed = true, proposedAt = at(2026, 1, 2))), open().ideas.ideas.first())

        s.ideas.addAll((1..205).map { Idea("n$it", emptyList(), IdeaKind.EVENT, "Idea $it", "", createdAt = at(2026, 2, 1)) })
        val kept = s.ideas.ideas.first()
        assertEquals(200, kept.size)
        assertEquals("n6", kept.first().id)
        assertEquals("n205", kept.last().id)
    }

    @Test
    fun plans_persistAndOldAnsweredOnesAreTrimmed() = runTest {
        val s = open()
        s.friends.upsert(friend("ana"))
        s.ideas.addAll(listOf(Idea("i", listOf(ana), IdeaKind.EVENT, "Jazz", "", createdAt = at(2026, 1, 1))))
        val lunch = Plan("p", listOf(ana), "Lunch", LocalDate(2026, 3, 12), "Tasca", "i", createdAt = at(2026, 3, 1))
        s.plans.upsert(lunch)
        assertEquals(listOf(lunch), open().plans.plans.first())

        repeat(205) { i ->
            s.plans.upsert(
                Plan(
                    "old$i",
                    emptyList(),
                    "x",
                    LocalDate(2025, 1, 1).plus(i, DateTimeUnit.DAY),
                    status = PlanStatus.SKIPPED,
                    createdAt = at(2025, 1, 1),
                ),
            )
        }
        val all = s.plans.plans.first()
        assertEquals(201, all.size)
        assertEquals("p", all.first().id)
        assertTrue(all.none { it.id == "old0" })
        s.plans.delete("p")
        assertTrue(s.plans.plans.first().none { it.id == "p" })
    }

    // --- Interactions and chat lines ---

    @Test
    fun interactions_matchTheCallLogByPhone_andSkipDuplicates() = runTest {
        val logs = object : CommunicationLogSource {
            override suspend fun read(since: Instant) = listOf(
                LogEntry("+351 912 345 678", InteractionType.CALL, at(2026, 3, 9), durationSeconds = 840),
                LogEntry("000", InteractionType.CALL, at(2026, 3, 9)),
            )
        }
        val s = open(logs = logs)
        val friendAna = friend("ana") { copy(phoneNumbers = listOf("912345678")) }
        s.friends.upsert(friendAna)
        assertEquals(1, s.interactions.syncCommunicationLogs(listOf(friendAna)))
        s.interactions.syncCommunicationLogs(listOf(friendAna))
        // A row from a backup older than the timeline (no id) gets its stable automatic id and source.
        s.interactions.addAll(listOf(Interaction(ana, InteractionType.WHATSAPP, at(2026, 3, 1))))
        assertEquals(
            listOf(
                Interaction(ana, InteractionType.WHATSAPP, at(2026, 3, 1)).withId(),
                Interaction(
                    ana,
                    InteractionType.CALL,
                    at(2026, 3, 9),
                    id = Interaction.automaticId(ana, InteractionType.CALL, at(2026, 3, 9)),
                    source = InteractionSource.CALL_LOG,
                    durationSeconds = 840,
                ),
            ),
            open().interactions.interactions.first(),
        )
        assertEquals(InteractionSource.CHAT_IMPORT, open().interactions.interactions.first().first().source)
    }

    @Test
    fun interactions_dedupeAutomaticRowsOnly_andDelete() = runTest {
        val s = open()
        s.friends.upsert(friend("ana"))
        s.friends.upsert(friend("rui"))
        val t = at(2026, 3, 9)
        val imported = Interaction(ana, InteractionType.WHATSAPP, t, id = "a", source = InteractionSource.CHAT_IMPORT, messageCount = 12)
        // The same chat day under another id is still the same chat day.
        val again = imported.copy(id = "b", messageCount = 3)
        val manual1 = Interaction(ana, InteractionType.MESSAGE, t, id = "m1", note = "hi", batchId = "x")
        val manual2 = Interaction(rui, InteractionType.MESSAGE, t, id = "m2", note = "hi", batchId = "x")
        val manual3 = Interaction(ana, InteractionType.MESSAGE, t, id = "m3")
        s.interactions.addAll(listOf(imported, again, manual1, manual2, manual3))
        s.interactions.addAll(listOf(manual1))
        val stored = open().interactions.interactions.first()
        assertEquals(setOf("a", "m1", "m2", "m3"), stored.map { it.id }.toSet())
        assertEquals(imported, stored.single { it.id == "a" })
        assertEquals(manual2, stored.single { it.id == "m2" })

        s.interactions.delete("m1")
        assertEquals(setOf("a", "m2", "m3"), open().interactions.interactions.first().map { it.id }.toSet())
    }

    @Test
    fun journal_upsertAndDelete() = runTest {
        val s = open()
        s.friends.upsert(friend("ana"))
        val entry = JournalEntry("j1", ana, at(2026, 3, 2), "Talked about Porto")
        s.interactions.upsertJournal(entry)
        s.interactions.upsertJournal(entry.copy(text = "Talked about Porto and Leo"))
        s.interactions.upsertJournal(JournalEntry("j2", ana, at(2026, 3, 5), "Race abroad"))
        assertEquals(listOf("Talked about Porto and Leo", "Race abroad"), open().interactions.journal.first().map { it.text })
        s.interactions.deleteJournal("j1")
        assertEquals(listOf("j2"), open().interactions.journal.first().map { it.id })
        assertTrue(open().interactions.interactions.first().isEmpty(), "a journal entry is never an interaction")
    }

    @Test
    fun attachments_upsertDeleteAndKeepEveryField() = runTest {
        val s = open()
        s.friends.upsert(friend("ana"))
        s.groups.upsert(FriendGroup("crew", "Crew", memberIds = listOf(ana)))
        val photo = Attachment(
            id = "a1",
            friendId = ana,
            kind = AttachmentKind.PHOTO,
            photoRef = PhotoRef(PhotoPlatform.ANDROID, "content://media/picker/0/1"),
            thumbnail = ContactPhoto("AQID"),
            caption = "Lunch at Ramiro",
            at = at(2026, 3, 2),
            countsAsInteraction = true,
            interactionType = InteractionType.MEETUP,
            logId = "h1",
            createdAt = at(2026, 3, 3),
        )
        val link = Attachment(
            id = "a2",
            circleId = "crew",
            kind = AttachmentKind.LINK,
            url = "https://wikiloc.com/trails/1",
            title = "Gerês trail",
            at = at(2026, 3, 4),
        )
        val text = Attachment("a3", friendId = ana, kind = AttachmentKind.TEXT, text = "See you\nsoon", at = at(2026, 3, 5))
        s.interactions.upsertAttachment(photo)
        s.interactions.upsertAttachment(link)
        s.interactions.upsertAttachment(text.copy(caption = "old"))
        s.interactions.upsertAttachment(text)
        assertEquals(listOf(photo, link, text), open().interactions.attachments.first())
        s.interactions.deleteAttachment("a2")
        assertEquals(listOf("a1", "a3"), open().interactions.attachments.first().map { it.id })
        assertTrue(open().interactions.interactions.first().isEmpty(), "saving an attachment logs nothing by itself")
    }

    @Test
    fun messages_keepTheLatest60PerFriend() = runTest {
        val s = open()
        s.friends.upsert(friend("ana"))
        s.friends.upsert(friend("rui"))
        s.interactions.addMessages((1..80).map { ConversationMessage(ana, Instant.fromEpochSeconds(it.toLong()), it % 2 == 0, "m$it") })
        s.interactions.addMessages(listOf(ConversationMessage(rui, Instant.fromEpochSeconds(5), true, "hi")))
        s.interactions.addMessages(listOf(ConversationMessage(rui, Instant.fromEpochSeconds(5), true, "hi")))

        val kept = open().interactions.messages.first().groupBy { it.friendId }
        assertEquals(60, kept.getValue(ana).size)
        assertEquals("m21", kept.getValue(ana).first().text)
        assertEquals("m80", kept.getValue(ana).last().text)
        assertTrue(kept.getValue(ana).last().fromMe)
        assertEquals(listOf("hi"), kept.getValue(rui).map { it.text })
    }

    // --- Settings ---

    @Test
    fun everySetting_survivesARestart() = runTest {
        val all = AppSettings(
            aiApiKey = "sk-test",
            weeklyIdeasEnabled = false,
            lastIdeasRunAt = at(2026, 3, 1),
            homeArea = "Lisbon",
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            reminderHour = 20,
            onboardingDone = true,
            innerCircleDays = 5,
            closeCircleDays = 20,
            goodFriendsDays = 60,
            weeklyDigest = false,
            digestDay = 1,
            privateNotifications = true,
            appLock = true,
            showWarmth = false,
            lastOpenedAt = at(2026, 3, 2),
            lastDriveBackupAt = at(2026, 3, 3),
        )
        val s = open()
        assertEquals(AppSettings(), s.settings.settings.first())
        s.settings.update { all }
        assertEquals(all, open().settings.settings.first())
        s.settings.update { it.copy(homeArea = "Porto", themeMode = ThemeMode.LIGHT) }
        assertEquals(all.copy(homeArea = "Porto", themeMode = ThemeMode.LIGHT), open().settings.settings.first())
    }

    // --- Backup ---

    @Test
    fun exportThenRestoreOnAnotherDevice_replacesEverythingButTheApiKey() = runTest {
        val from = open()
        from.friends.upsert(richFriend())
        from.hangouts.add(Hangout("h", listOf(ana), at(2026, 3, 1), Place("Mum’s", 38.7, -9.1, "pl")))
        from.hangouts.upsertPlace(NamedPlace("pl", "Mum’s", 38.7, -9.1))
        from.reminders.upsert(Reminder("r", "Tickets", listOf(ana), createdAt = at(2026, 3, 1)))
        from.interactions.addAll(listOf(Interaction(ana, InteractionType.CALL, at(2026, 3, 2))))
        from.interactions.addMessages(listOf(ConversationMessage(ana, at(2026, 3, 2), fromMe = true, text = "hi")))
        from.ideas.addAll(listOf(Idea("i", listOf(ana), IdeaKind.EVENT, "Jazz", "", createdAt = at(2026, 3, 1))))
        from.groups.upsert(FriendGroup("g", "Crew", memberIds = listOf(ana)))
        from.interactions.upsertAttachment(
            Attachment("a", friendId = ana, kind = AttachmentKind.PHOTO, thumbnail = ContactPhoto("AQID"), at = at(2026, 3, 2)),
        )
        from.interactions.upsertAttachment(Attachment("b", circleId = "g", kind = AttachmentKind.TEXT, text = "hi", at = at(2026, 3, 3)))
        from.plans.upsert(Plan("p", listOf(ana), "Lunch", LocalDate(2026, 3, 12), ideaId = "i", createdAt = at(2026, 3, 1)))
        from.settings.update { it.copy(aiApiKey = "sk-secret", homeArea = "Lisbon") }
        val exported = from.backup.snapshot()

        val json = ExportBackupUseCase(from.backup, clock)()
        assertFalse("sk-secret" in json)

        val to = open()
        to.friends.upsert(friend("zed"))
        to.hangouts.add(Hangout("old", listOf(FriendId("zed")), at(2025, 1, 1)))
        to.hangouts.upsertPlace(NamedPlace("old-place", "Old", 1.0, 2.0))
        to.settings.update { it.copy(aiApiKey = "sk-mine") }
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))

        val restored = to.backup.snapshot()
        assertEquals(exported.copy(settings = exported.settings.copy(aiApiKey = "sk-mine", onboardingDone = true)), restored)
        // Flows see the restored data straight away.
        assertEquals(listOf(richFriend()), to.friends.friends.first())
        assertEquals(listOf("h"), to.hangouts.hangouts.first().map { it.id })
        assertEquals(listOf("pl"), to.hangouts.places.first().map { it.id })
    }

    @Test
    fun theSampleCircle_roundTripsThroughABackup() = runTest {
        val sample = SampleData.build(clock)
        val s = open()
        val backup = Backup(
            friends = sample.friends,
            hangouts = sample.hangouts,
            reminders = sample.reminders,
            ideas = sample.ideas,
            groups = sample.groups,
            plans = sample.plans,
        )
        s.backup.restore(backup)
        val snap = open().backup.snapshot()
        assertEquals(sample.friends, snap.friends)
        assertEquals(sample.hangouts, snap.hangouts)
        assertEquals(sample.reminders, snap.reminders)
        assertEquals(sample.ideas, snap.ideas)
        assertEquals(sample.groups, snap.groups)
        assertEquals(sample.plans.sortedBy { it.id }, snap.plans.sortedBy { it.id })
    }
}

/** The JSON-document repositories (today's storage). */
class JsonRepositoryContractTest : RepositoryContract() {
    private val settings = MapSettings()

    override fun open(contacts: ContactsSource, writer: ContactsWriter, logs: CommunicationLogSource): Stores {
        val friends = FriendRepositoryImpl(settings, contacts, writer)
        val hangouts = HangoutRepositoryImpl(settings)
        val reminders = ReminderRepositoryImpl(settings)
        val interactions = InteractionRepositoryImpl(settings, logs, clock)
        val ideas = IdeaRepositoryImpl(settings)
        val groups = GroupRepositoryImpl(settings)
        val plans = PlanRepositoryImpl(settings)
        val appSettings = SettingsRepositoryImpl(settings)
        val backup = BackupRepositoryImpl(friends, hangouts, reminders, interactions, ideas, groups, plans, appSettings)
        return Stores(friends, hangouts, reminders, interactions, ideas, groups, plans, appSettings, backup)
    }
}
