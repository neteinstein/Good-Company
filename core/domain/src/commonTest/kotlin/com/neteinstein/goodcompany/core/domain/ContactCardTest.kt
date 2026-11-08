package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.CardEvent
import com.neteinstein.goodcompany.core.domain.model.CardKid
import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContactCardTest {
    @Test
    fun plainFriend_hasNothingToPutOnTheContact() {
        val card = ContactCard.from(friend("ana"))
        assertEquals("Ana", card.name)
        assertTrue(card.isEmpty)
        assertFalse(card.hasExtras)
    }

    @Test
    fun birthdayAlone_isContentButNotAnExtra() {
        // Birthdays normally come *from* the contact, so a birthday on its own must not trigger backfills.
        val card = ContactCard.from(friend("ana") { copy(birthday = Birthday(5, 4, 1990)) })
        assertFalse(card.isEmpty)
        assertFalse(card.hasExtras)
    }

    @Test
    fun keepsOnlyWhatAContactCanHold() {
        val f = friend("ana") {
            copy(
                kids = listOf(Kid("k1", "Maria", Birthday(3, 9, 2018), likes = listOf("lego")), Kid("k2", "  ")),
                importantEvents = listOf(ImportantEvent("e1", "Surgery", LocalDate(2026, 12, 1), remindDaysBefore = 3)),
                meetEveryDays = 30,
                isCloseFriend = true,
            )
        }
        val card = ContactCard.from(f)
        // Nameless kids are dropped; a kid's likes and an event's reminder lead time have no slot on a contact.
        assertEquals(listOf(CardKid("Maria", Birthday(3, 9, 2018))), card.kids)
        assertEquals(listOf(CardEvent("Surgery", LocalDate(2026, 12, 1))), card.events)
        assertTrue(card.hasExtras)
    }

    @Test
    fun changesThatDontShowOnTheContact_leaveTheCardEqual() {
        val base = friend("ana") { copy(kids = listOf(Kid("k1", "Maria"))) }
        val tweaked = base.copy(isCloseFriend = true, meetEveryDays = 14, kids = listOf(Kid("k1", "Maria", likes = listOf("lego"))))
        assertEquals(ContactCard.from(base), ContactCard.from(tweaked))
    }

    @Test
    fun noteText_putsNotesThenTastes() {
        val card = ContactCard.from(
            friend("ana") { copy(notes = "  Met at uni  ", likes = listOf("jazz", " sushi "), dislikes = listOf("", "cilantro")) },
        )
        assertEquals("Met at uni\nLoves: jazz, sushi\nNot a fan of: cilantro", card.noteText("Loves", "Not a fan of"))
    }

    @Test
    fun noteText_isNullWhenThereIsNothingToSay() {
        assertNull(ContactCard.from(friend("ana") { copy(notes = "   ") }).noteText("Loves", "Not a fan of"))
        assertEquals("Loves: jazz", ContactCard.from(friend("ana") { copy(likes = listOf("jazz")) }).noteText("Loves", "Not a fan of"))
    }
}
