package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.ContactChoice
import com.neteinstein.goodcompany.core.domain.usecase.ContactList
import com.neteinstein.goodcompany.core.domain.usecase.ContactListFilter
import com.neteinstein.goodcompany.core.domain.usecase.ImportPickedContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ListContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PickedContactsResult
import com.neteinstein.goodcompany.core.domain.usecase.SaveChosenContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContactListTest {
    private val photo = ContactPhoto.of(byteArrayOf(1, 2, 3))!!

    private fun rec(id: String, name: String, phones: List<String> = emptyList(), birthday: Birthday? = null, starred: Boolean = false) =
        ContactRecord(id, name, phones, birthday, photo = photo, starred = starred)

    private val contacts = listOf(
        rec("1", "Rui Costa", starred = true),
        rec("2", "Ana Silva", listOf("+351 912 345 678"), Birthday(11, 11), starred = true),
        rec("3", "Zé", listOf("21 000 1111")),
        rec("4", "Álvaro Pires"),
        rec("5", "beatriz Rocha", birthday = Birthday(2, 2)),
        rec("6", "Afonso Canalizador"),
        rec("7", "  "),
        rec("2", "Ana Silva (dup)"),
    )

    @Test
    fun favouritesComeFirst_thenAToZIgnoringCaseAndAccents() = runTest {
        val choices = ListContactsUseCase(FakeFriendRepository(contacts = contacts))()
        assertEquals(
            listOf("Ana Silva", "Rui Costa", "Afonso Canalizador", "Álvaro Pires", "beatriz Rocha", "Zé"),
            choices.map { it.record.name },
        )
    }

    @Test
    fun theListIsReadWithoutPhotos_andChangesNoFriend() = runTest {
        val repo = FakeFriendRepository(contacts = contacts)
        val choices = ListContactsUseCase(repo)()
        assertEquals(listOf(false), repo.contactReads)
        assertTrue(choices.all { it.record.photo == null })
        assertTrue(repo.friends.value.isEmpty())
    }

    @Test
    fun peopleAlreadyFriends_areFlagged() = runTest {
        val repo = FakeFriendRepository(listOf(Friend(FriendId("contact-3"), "Zé", contactId = "3")), contacts = contacts)
        val choices = ListContactsUseCase(repo)().associateBy { it.record.contactId }
        assertTrue(choices.getValue("3").alreadyFriend)
        assertFalse(choices.getValue("1").alreadyFriend)
    }

    @Test
    fun noPermission_meansAnEmptyList() = runTest {
        assertTrue(ListContactsUseCase(FakeFriendRepository())().isEmpty())
    }

    private val all = contacts.filter { it.name.isNotBlank() }.distinctBy { it.contactId }.map { ContactChoice(it, false) }

    @Test
    fun search_matchesNamesWithoutAccents_andPhoneDigits() {
        fun names(q: String) = ContactList.filter(all, q, ContactListFilter.FAVOURITES_FIRST).map { it.record.contactId }
        assertEquals(listOf("4"), names("alva"))
        assertEquals(listOf("3"), names("ze"))
        assertEquals(listOf("2"), names("912 345"))
        assertEquals(listOf("2"), names("+351"))
        assertEquals(listOf("3"), names("0001111"))
        // Two digits aren't a number search, and a word with digits is a name search.
        assertEquals(emptyList(), names("91"))
        assertEquals(listOf("5"), names("ROCHA"))
    }

    @Test
    fun filters() {
        assertEquals(
            listOf("2", "5"),
            ContactList.filter(all, "", ContactListFilter.WITH_BIRTHDAY).map { it.record.contactId },
        )
        assertEquals(
            listOf("6", "4", "2", "5", "1", "3"),
            ContactList.filter(all, "", ContactListFilter.ALL).map { it.record.contactId },
        )
        assertEquals(listOf("2", "1"), ContactList.filter(all, "", ContactListFilter.FAVOURITES_FIRST).take(2).map { it.record.contactId })
    }

    @Test
    fun indexLetters() {
        assertEquals("A", ContactList.indexLetter("Álvaro"))
        assertEquals("Z", ContactList.indexLetter("zé"))
        assertEquals("#", ContactList.indexLetter("+351 Office"))
        assertEquals("#", ContactList.indexLetter(""))
        assertEquals("ss", ContactList.sortKey("ß"))
    }

    @Test
    fun saving_addsOnlyTheChosen_withPhotosReadForThemOnly() = runTest {
        val repo = FakeFriendRepository(contacts = contacts)
        val choices = ListContactsUseCase(repo)()
        val chosen = choices.filter { it.record.contactId in setOf("2", "5") }.map { it.record }
        val result = SaveChosenContactsUseCase(repo, ImportPickedContactsUseCase(repo))(chosen)
        assertEquals(PickedContactsResult(2, 0), result)
        assertEquals(listOf(setOf("2", "5")), repo.photoRequests)
        val friends = repo.friends.value
        assertEquals(setOf("2", "5"), friends.mapNotNull { it.contactId }.toSet())
        assertTrue(friends.all { it.photo == photo && it.closeness == Closeness.ACQUAINTANCE })
    }

    @Test
    fun savingNobody_readsNothing() = runTest {
        val repo = FakeFriendRepository(contacts = contacts)
        assertEquals(PickedContactsResult(0, 0), SaveChosenContactsUseCase(repo, ImportPickedContactsUseCase(repo))(emptyList()))
        assertTrue(repo.photoRequests.isEmpty())
    }

    @Test
    fun aSyncThatDoesNotImportNewContacts_addsNobody_andRefreshesLinkedFriends() = runTest {
        // iOS since D2 (and Android): people are chosen; a sync only refreshes friends already linked to a contact.
        val repo = FakeFriendRepository(
            listOf(Friend(FriendId("contact-2"), "Ana", contactId = "2")),
            contacts = contacts,
            importsNewContacts = false,
        )
        SyncContactsUseCase(repo, FakeInteractionRepository())()
        val friends = repo.friends.value
        assertEquals(listOf("2"), friends.map { it.contactId })
        assertEquals("Ana Silva", friends.single().name)
        assertNull(friends.firstOrNull { it.contactId == "1" })
    }
}
