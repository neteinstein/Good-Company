package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.usecase.ContactMerger
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContactMergerTest {
    @Test
    fun newContactsBecomeFriendsThatAreNotCloseByDefault() {
        val merged = ContactMerger.merge(emptyList(), listOf(ContactRecord("1", " Ana Silva ", listOf("+351"), Birthday(1, 2))))
        assertEquals(1, merged.size)
        assertEquals("Ana Silva", merged[0].name)
        assertEquals("1", merged[0].contactId)
        assertFalse(merged[0].isCloseFriend)
    }

    @Test
    fun syncUpdatesContactFieldsButKeepsAppData() {
        val existing = friend("contact-1", "Old") { copy(contactId = "1", isCloseFriend = true, likes = listOf("jazz")) }
        val merged = ContactMerger.merge(listOf(existing), listOf(ContactRecord("1", "Ana", listOf("+1", "+1"), null)))
        val ana = merged.single()
        assertEquals("Ana", ana.name)
        assertEquals(listOf("+1"), ana.phoneNumbers)
        assertTrue(ana.isCloseFriend)
        assertEquals(listOf("jazz"), ana.likes)
    }

    @Test
    fun withoutAddNewOnlyKnownFriendsAreRefreshed() {
        val ana = friend("contact-1", "Old") { copy(contactId = "1", likes = listOf("jazz")) }
        val merged = ContactMerger.merge(
            listOf(ana),
            listOf(ContactRecord("1", "Ana", listOf("+1"), null), ContactRecord("2", "Bruno", emptyList(), null)),
            addNew = false,
        )
        assertEquals(listOf("Ana"), merged.map { it.name })
        assertEquals(listOf("jazz"), merged.single().likes)
    }

    @Test
    fun contactWithoutBirthdayDoesNotEraseAppBirthday() {
        val existing = friend("contact-1") { copy(contactId = "1", birthday = Birthday(4, 4)) }
        val merged = ContactMerger.merge(listOf(existing), listOf(ContactRecord("1", "Ana", emptyList(), null)))
        assertEquals(Birthday(4, 4), merged.single().birthday)
    }

    @Test
    fun vanishedContactIsDroppedUnlessItHasAppData() {
        val plain = friend("contact-1") { copy(contactId = "1") }
        val loved = friend("contact-2") { copy(contactId = "2", isCloseFriend = true) }
        val manual = friend("manual-x")
        val merged = ContactMerger.merge(listOf(plain, loved, manual), emptyList())
        assertEquals(listOf("contact-2", "manual-x"), merged.map { it.id.value })
        assertTrue(merged.first { it.id.value == "contact-2" }.isOrphaned)
    }

    @Test
    fun duplicateAndBlankContactsAreIgnored() {
        val merged = ContactMerger.merge(
            emptyList(),
            listOf(
                ContactRecord("1", "Ana", emptyList(), null),
                ContactRecord("1", "Ana", emptyList(), null),
                ContactRecord("2", " ", emptyList(), null),
            ),
        )
        assertEquals(1, merged.size)
    }
}
