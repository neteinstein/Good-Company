package org.goodcompany.app.core.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.goodcompany.app.core.domain.model.Attachment
import org.goodcompany.app.core.domain.model.AttachmentKind
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.GeoPoint
import org.goodcompany.app.core.domain.model.Gift
import org.goodcompany.app.core.domain.model.Hangout
import org.goodcompany.app.core.domain.model.HangoutActivity
import org.goodcompany.app.core.domain.model.Interaction
import org.goodcompany.app.core.domain.model.InteractionSource
import org.goodcompany.app.core.domain.model.InteractionType
import org.goodcompany.app.core.domain.model.JournalEntry
import org.goodcompany.app.core.domain.model.LifeUpdate
import org.goodcompany.app.core.domain.model.Place
import org.goodcompany.app.core.domain.usecase.LogKind
import org.goodcompany.app.core.domain.usecase.LogRequest
import org.goodcompany.app.core.domain.usecase.TimelineEntry
import org.goodcompany.app.core.domain.usecase.asLogRequest
import org.goodcompany.app.core.domain.usecase.editsInLogSheet
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Issue #102: every timeline entry can be edited (date, kind, people, place, activity, note). */
class TimelineEditTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val pedro = FriendId("pedro")
    private val friends = listOf(
        friend("ana", "Ana") { copy(lifeUpdates = listOf(LifeUpdate("u1", "Promoted", at(2026, 3, 2)))) },
        friend("rui", "Rui"),
        friend("pedro", "Pedro"),
    )
    private val lunch = Hangout(
        "h1",
        listOf(ana, rui),
        at(2026, 3, 9, 13, 30),
        Place("Time Out", 38.7, -9.1, placeId = "p1"),
        "Lunch",
        HangoutActivity.LUNCH,
    )

    private suspend fun TestGraph.entry(key: String, of: FriendId = ana) = observeTimeline(of).first().first { it.key == key }

    private fun graph(hangouts: List<Hangout> = listOf(lunch)) = TestGraph(friends = friends, hangouts = hangouts)

    @Test
    fun aMeetupKeepsItsTimeOnTheSameDay_andItsNamedPlaceWhenThePlaceIsUnchanged() = runTest {
        val g = graph()
        val entry = g.entry("hangout-h1")
        val request = entry.asLogRequest(ana, TimeZone.UTC)!!
        g.editTimelineEntry(entry, request.copy(friendIds = listOf(ana, pedro), note = " Long lunch ", activity = HangoutActivity.DRINKS))
        val h = g.hangoutRepo.hangouts.value.single()
        assertEquals("h1", h.id)
        assertEquals(listOf(ana, pedro), h.friendIds)
        assertEquals(lunch.at, h.at)
        assertEquals(lunch.place, h.place)
        assertEquals("Long lunch", h.note)
        assertEquals(HangoutActivity.DRINKS, h.activity)
    }

    @Test
    fun aMeetupMovedToAnotherDayIsAtNoon_aNewPlaceNameDropsTheLink_andTheFutureIsRefused() = runTest {
        val g = graph()
        val entry = g.entry("hangout-h1")
        val request = entry.asLogRequest(ana, TimeZone.UTC)!!
        g.editTimelineEntry(entry, request.copy(date = LocalDate(2026, 3, 1), placeName = "Home", location = null))
        val h = g.hangoutRepo.hangouts.value.single()
        assertEquals(at(2026, 3, 1), h.at)
        assertEquals(Place("Home"), h.place)
        assertFailsWith<IllegalArgumentException> { g.editTimelineEntry(entry, request.copy(date = LocalDate(2026, 3, 11))) }
        assertFailsWith<IllegalArgumentException> { g.editTimelineEntry(entry, request.copy(friendIds = emptyList())) }
    }

    @Test
    fun aMeetupMovedToTodayIsNow() = runTest {
        val g = graph()
        val entry = g.entry("hangout-h1")
        g.editTimelineEntry(entry, entry.asLogRequest(ana, TimeZone.UTC)!!.copy(date = LocalDate(2026, 3, 10)))
        assertEquals(g.clock.now(), g.hangoutRepo.hangouts.value.single().at)
    }

    @Test
    fun aMeetupThatBecomesACallIsOneRowPerPersonAtTheSameTime_andItsShareFollows() = runTest {
        val g = graph()
        val share = Attachment(
            "a1",
            friendId = ana,
            kind = AttachmentKind.TEXT,
            text = "x",
            at = lunch.at,
            countsAsInteraction = true,
            interactionType = InteractionType.MEETUP,
            logId = "h1",
        )
        g.interactionRepo.upsertAttachment(share)
        val entry = g.entry("hangout-h1")
        g.editTimelineEntry(entry, entry.asLogRequest(ana, TimeZone.UTC)!!.copy(kind = LogKind.CALL))
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        val rows = g.interactionRepo.interactions.value
        assertEquals(setOf(ana, rui), rows.map { it.friendId }.toSet())
        assertTrue(rows.all { it.type == InteractionType.CALL && it.at == lunch.at && it.note == "Lunch" })
        val batch = rows.first().batchId
        assertEquals(1, rows.map { it.batchId }.distinct().size)
        val moved = g.interactionRepo.attachments.first().single()
        assertEquals(batch, moved.logId)
        assertEquals(InteractionType.CALL, moved.interactionType)
        // The timeline now shows the share on the call.
        assertEquals(moved, (g.entry("interaction-${rows.first { it.friendId == ana }.id}") as TimelineEntry.Contact).attachment)
    }

    @Test
    fun aBatchKeepsItsRows_losesThePeopleTakenOut_andGainsTheOnesAdded() = runTest {
        val g = graph(emptyList())
        g.interactionRepo.addAll(
            listOf(
                Interaction(ana, InteractionType.EMAIL, at(2026, 2, 27), id = "e1", batchId = "b"),
                Interaction(pedro, InteractionType.EMAIL, at(2026, 2, 27), id = "e2", batchId = "b"),
            ),
        )
        val entry = g.entry("interaction-e1")
        val request = entry.asLogRequest(ana, TimeZone.UTC)!!
        assertEquals(listOf(ana, pedro), request.friendIds)
        g.editTimelineEntry(entry, request.copy(friendIds = listOf(ana, rui), kind = LogKind.LONG_CHAT, note = "Catch-up"))
        val rows = g.interactionRepo.interactions.value.associateBy { it.friendId }
        assertEquals(setOf(ana, rui), rows.keys)
        assertEquals("e1", rows.getValue(ana).id)
        assertTrue(rows.values.all { it.batchId == "b" && it.type == InteractionType.LONG_CHAT && it.note == "Catch-up" })
        assertEquals(at(2026, 2, 27), rows.getValue(rui).at)
    }

    @Test
    fun aSingleRowThatGainsPeopleStartsABatchUnderItsOwnId() = runTest {
        val g = graph(emptyList())
        g.interactionRepo.addAll(listOf(Interaction(ana, InteractionType.CALL, at(2026, 3, 8), id = "c1")))
        val entry = g.entry("interaction-c1")
        g.editTimelineEntry(entry, entry.asLogRequest(ana, TimeZone.UTC)!!.copy(friendIds = listOf(ana, rui)))
        assertTrue(g.interactionRepo.interactions.value.all { it.batchId == "c1" })
        assertEquals(listOf(rui), (g.entry("interaction-c1") as TimelineEntry.Contact).others.map { it.id })
    }

    @Test
    fun anAutomaticCallKeepsItsIdAndSource_andAWhatsAppDayStaysOne() = runTest {
        val g = graph(emptyList())
        g.interactionRepo.addAll(
            listOf(
                Interaction(ana, InteractionType.CALL, at(2026, 3, 8), id = "auto", source = InteractionSource.CALL_LOG, durationSeconds = 600),
                Interaction(ana, InteractionType.WHATSAPP, at(2026, 3, 3), id = "w1", source = InteractionSource.CHAT_IMPORT, messageCount = 4),
            ),
        )
        val call = g.entry("interaction-auto")
        g.editTimelineEntry(call, call.asLogRequest(ana, TimeZone.UTC)!!.copy(date = LocalDate(2026, 3, 7), note = "Birthday"))
        val chat = g.entry("interaction-w1")
        val chatRequest = chat.asLogRequest(ana, TimeZone.UTC)!!
        assertEquals(LogKind.MESSAGE, chatRequest.kind)
        g.editTimelineEntry(chat, chatRequest.copy(note = "Plans"))
        val rows = g.interactionRepo.interactions.value.associateBy { it.id }
        assertEquals(InteractionSource.CALL_LOG, rows.getValue("auto").source)
        assertEquals(600, rows.getValue("auto").durationSeconds)
        assertEquals(at(2026, 3, 7), rows.getValue("auto").at)
        assertEquals(InteractionType.WHATSAPP, rows.getValue("w1").type)
        assertEquals(4, rows.getValue("w1").messageCount)
    }

    @Test
    fun otherKeepsItsLabel_andAnotherKindDropsIt() = runTest {
        val g = graph(emptyList())
        g.interactionRepo.addAll(listOf(Interaction(ana, InteractionType.OTHER, at(2026, 3, 8), id = "o1", label = "Postcard")))
        val entry = g.entry("interaction-o1")
        val request = entry.asLogRequest(ana, TimeZone.UTC)!!
        assertEquals("Postcard", request.label)
        g.editTimelineEntry(entry, request.copy(label = " Letter "))
        assertEquals("Letter", g.interactionRepo.interactions.value.single().label)
        g.editTimelineEntry(g.entry("interaction-o1"), request.copy(kind = LogKind.GAME))
        assertEquals("", g.interactionRepo.interactions.value.single().label)
    }

    @Test
    fun aContactThatBecomesAJournalEntryIsANoteForEachPerson() = runTest {
        val g = graph(emptyList())
        g.interactionRepo.addAll(listOf(Interaction(ana, InteractionType.CALL, at(2026, 3, 8), id = "c1")))
        val entry = g.entry("interaction-c1")
        g.editTimelineEntry(entry, entry.asLogRequest(ana, TimeZone.UTC)!!.copy(kind = LogKind.JOURNAL, note = "Moving"))
        assertTrue(g.interactionRepo.interactions.value.isEmpty())
        val note = g.interactionRepo.journal.value.single()
        assertEquals(ana, note.friendId)
        assertEquals(at(2026, 3, 8), note.at)
        // A journal entry needs text.
        assertFailsWith<IllegalArgumentException> {
            g.editTimelineEntry(g.entry("journal-${note.id}"), LogRequest(listOf(ana), LogKind.JOURNAL, note = " "))
        }
    }

    @Test
    fun aJournalEntryKeepsItsRow_addsOneForEachNewPerson_andGoesWhenItsPersonIsTakenOut() = runTest {
        val g = graph(emptyList())
        g.interactionRepo.upsertJournal(JournalEntry("j1", ana, at(2026, 3, 5), "Porto"))
        val entry = g.entry("journal-j1")
        g.editTimelineEntry(entry, entry.asLogRequest(ana, TimeZone.UTC)!!.copy(friendIds = listOf(ana, rui), note = "Porto trip"))
        assertEquals(setOf(ana, rui), g.interactionRepo.journal.value.map { it.friendId }.toSet())
        assertEquals("Porto trip", g.interactionRepo.journal.value.first { it.id == "j1" }.text)
        g.editTimelineEntry(g.entry("journal-j1"), LogRequest(listOf(pedro), LogKind.JOURNAL, note = "For Pedro"))
        val left = g.interactionRepo.journal.value
        assertFalse(left.any { it.id == "j1" })
        assertEquals(setOf(rui, pedro), left.map { it.friendId }.toSet())
    }

    @Test
    fun aJournalEntryThatBecomesAMeetupIsAHangout() = runTest {
        val g = graph(emptyList())
        g.interactionRepo.upsertJournal(JournalEntry("j1", ana, at(2026, 3, 5, 9), "Coffee"))
        val entry = g.entry("journal-j1")
        g.editTimelineEntry(entry, entry.asLogRequest(ana, TimeZone.UTC)!!.copy(kind = LogKind.MEETUP, activity = HangoutActivity.COFFEE))
        assertTrue(g.interactionRepo.journal.value.isEmpty())
        val h = g.hangoutRepo.hangouts.value.single()
        assertEquals(at(2026, 3, 5, 9), h.at)
        assertEquals(HangoutActivity.COFFEE, h.activity)
        assertEquals("Coffee", h.note)
    }

    @Test
    fun whatsNewChangesItsTextAndDate() = runTest {
        val g = graph(emptyList())
        val entry = g.entry("update-u1")
        g.editTimelineEntry(entry, entry.asLogRequest(ana, TimeZone.UTC)!!.copy(note = " Promoted again ", date = LocalDate(2026, 3, 4)))
        val update = g.friendRepo.get(ana)!!.lifeUpdates.single()
        assertEquals("Promoted again", update.text)
        assertEquals(at(2026, 3, 4), update.addedAt)
    }

    @Test
    fun giftsAndSharesAreNotEditedInTheLogSheet() = runTest {
        val gift = TimelineEntry.GiftGiven(Gift("g1", "Book", givenOn = LocalDate(2026, 2, 1)), LocalDate(2026, 2, 1), at(2026, 2, 1))
        assertFalse(gift.editsInLogSheet)
        assertEquals(null, gift.asLogRequest(ana, TimeZone.UTC))
        assertTrue(TimelineEntry.Journal(JournalEntry("j", ana, at(2026, 1, 1), "x")).editsInLogSheet)
        assertFalse(TimelineEntry.WhatsNew(ana, LifeUpdate("u", "x", at(2026, 1, 1))).editsInLogSheet)
        // Editing one is a no-op.
        val g = graph()
        g.editTimelineEntry(gift, LogRequest(listOf(ana), LogKind.MEETUP))
        assertEquals(listOf(lunch), g.hangoutRepo.hangouts.value)
    }

    @Test
    fun aMeetupAtAFixKeepsTheFixWhenOnlyTheNoteChanges() = runTest {
        val g = graph()
        val entry = g.entry("hangout-h1")
        val request = entry.asLogRequest(ana, TimeZone.UTC)!!
        assertEquals(GeoPoint(38.7, -9.1), request.location)
        assertEquals("Time Out", request.placeName)
        g.editTimelineEntry(entry, request.copy(note = "x"))
        assertEquals("p1", g.hangoutRepo.hangouts.value.single().place?.placeId)
    }
}
