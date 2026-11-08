package com.neteinstein.goodcompany.core.data

import com.neteinstein.goodcompany.core.data.repository.FriendRepositoryImpl
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import com.neteinstein.goodcompany.core.testing.FakeContactsWriter
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Friends from the address book are mirrored onto their contact whenever data the contact can hold changes. */
class ContactWriteBackTest {
    private val writer = FakeContactsWriter()
    private var records = emptyList<ContactRecord>()
    private val repo = FriendRepositoryImpl(MapSettings(), { records }, writer)

    private fun contactFriend(id: String = "ana", block: Friend.() -> Friend = { this }) =
        friend(id) { copy(contactId = "c-$id") }.block()

    @Test
    fun addingKidsEventsAndNotes_writesThemToTheContact() = runTest {
        repo.upsert(contactFriend { copy(kids = listOf(Kid("k1", "Maria", Birthday(3, 9, 2018))), notes = "Met at uni") })

        val card = writer.written.getValue(FriendId("ana"))
        assertEquals(listOf("Maria"), card.kids.map { it.name })
        assertEquals(Birthday(3, 9, 2018), card.kids.single().birthday)
        assertEquals("Met at uni", card.notes)
        assertEquals("c-ana", writer.contactIds[FriendId("ana")])
    }

    @Test
    fun editsThatDontChangeTheContact_doNotWriteAgain() = runTest {
        val first = contactFriend { copy(likes = listOf("jazz")) }
        repo.upsert(first)
        assertEquals(1, writer.writes)

        repo.upsert(first.copy(isCloseFriend = true, meetEveryDays = 14))
        assertEquals(1, writer.writes)

        repo.upsert(first.copy(likes = listOf("jazz", "sushi")))
        assertEquals(2, writer.writes)
        assertEquals(listOf("jazz", "sushi"), writer.written.getValue(FriendId("ana")).likes)
    }

    @Test
    fun clearingEverything_removesTheCopy() = runTest {
        val withKid = contactFriend { copy(kids = listOf(Kid("k1", "Maria"))) }
        repo.upsert(withKid)
        repo.upsert(withKid.copy(kids = emptyList()))

        assertEquals(listOf(FriendId("ana")), writer.removed)
        assertNull(writer.written[FriendId("ana")])
    }

    @Test
    fun aFriendWithNothingToMirror_isNeverTouched() = runTest {
        repo.upsert(contactFriend())
        repo.upsert(contactFriend { copy(isCloseFriend = true) })
        assertEquals(0, writer.writes)
        assertTrue(writer.removed.isEmpty())
    }

    @Test
    fun manualFriends_haveNoContactToWriteTo() = runTest {
        repo.upsert(friend("manual") { copy(kids = listOf(Kid("k1", "Maria"))) })
        assertEquals(0, writer.writes)
    }

    @Test
    fun anInAppBirthday_isWritten() = runTest {
        repo.upsert(contactFriend { copy(birthday = Birthday(5, 4)) })
        assertEquals(Birthday(5, 4), writer.written.getValue(FriendId("ana")).birthday)
    }

    @Test
    fun deletingAFriend_leavesTheirDataOnTheContact() = runTest {
        val f = contactFriend { copy(kids = listOf(Kid("k1", "Maria"))) }
        repo.upsert(f)
        repo.delete(f.id)

        assertTrue(repo.friends.first().isEmpty())
        assertTrue(writer.removed.isEmpty())
        assertEquals(listOf("Maria"), writer.written.getValue(f.id).kids.map { it.name })
    }

    @Test
    fun deletingAManualFriend_doesNotTouchContacts() = runTest {
        repo.upsert(friend("manual"))
        repo.delete(FriendId("manual"))
        assertTrue(writer.removed.isEmpty())
    }

    @Test
    fun sync_backfillsExistingDetails_butNotBirthdayOnlyOrOrphanedFriends() = runTest {
        // Details saved while the contact wasn't writable (e.g. before the permission was granted).
        repo.replaceAll(
            listOf(
                contactFriend("kid") { copy(kids = listOf(Kid("k1", "Maria"))) },
                contactFriend("bday") { copy(birthday = Birthday(1, 2)) },
                contactFriend("gone") { copy(notes = "x", isOrphaned = true) },
                friend("manual") { copy(notes = "x") },
            ),
        )
        records = listOf(ContactRecord("c-kid", "Kid", emptyList(), null), ContactRecord("c-bday", "Bday", emptyList(), Birthday(1, 2)))

        repo.syncContacts()

        // "gone" has no contact in the snapshot, so the merge flags it orphaned and it is skipped.
        assertEquals(setOf(FriendId("kid")), writer.written.keys)
        assertEquals(1, writer.writes)
    }

    @Test
    fun sync_withAnEmptyAddressBook_writesNothing() = runTest {
        repo.replaceAll(listOf(contactFriend { copy(notes = "x") }))
        records = emptyList()
        repo.syncContacts()
        assertEquals(0, writer.writes)
    }

    @Test
    fun aFailingContactStore_neverBlocksSaving() = runTest {
        val failing = object : ContactsWriter {
            override suspend fun write(contactId: String, friendId: FriendId, card: ContactCard) = error("provider crashed")

            override suspend fun remove(friendId: FriendId) = error("provider crashed")
        }
        val r = FriendRepositoryImpl(MapSettings(), { emptyList() }, failing)
        val f = contactFriend { copy(notes = "keep me") }
        r.upsert(f)
        assertEquals("keep me", r.get(f.id)?.notes)
        r.delete(f.id)
        assertNull(r.get(f.id))
    }

    @Test
    fun cancellation_isNotSwallowed() = runTest {
        val cancelling = object : ContactsWriter {
            override suspend fun write(
                contactId: String,
                friendId: FriendId,
                card: ContactCard,
            ) = throw kotlinx.coroutines.CancellationException("stop")

            override suspend fun remove(friendId: FriendId) = Unit
        }
        val r = FriendRepositoryImpl(MapSettings(), { emptyList() }, cancelling)
        assertFailsWith<kotlinx.coroutines.CancellationException> { r.upsert(contactFriend { copy(notes = "x") }) }
    }
}
