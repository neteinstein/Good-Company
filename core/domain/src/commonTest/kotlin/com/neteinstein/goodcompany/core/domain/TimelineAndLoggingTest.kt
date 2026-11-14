package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.LogRequest
import com.neteinstein.goodcompany.core.domain.usecase.LogResult
import com.neteinstein.goodcompany.core.domain.usecase.LoggedMeetup
import com.neteinstein.goodcompany.core.domain.usecase.LoggedMeetupInbox
import com.neteinstein.goodcompany.core.domain.usecase.ObserveActivitiesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.Timeline
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.domain.usecase.TimelineFilter
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimelineAndLoggingTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val pedro = FriendId("pedro")
    private val friends = listOf(
        friend("ana", "Ana") {
            copy(
                lifeUpdates = listOf(LifeUpdate("u1", "Promoted", at(2026, 3, 2))),
                gifts = listOf(Gift("g1", "Voucher", givenOn = LocalDate(2026, 2, 12)), Gift("g2", "Idea only")),
            )
        },
        friend("rui", "Rui"),
        friend("pedro", "Pedro"),
    )
    private val hangouts = listOf(
        Hangout("h1", listOf(ana, rui, pedro), at(2026, 3, 9), activity = HangoutActivity.LUNCH),
        Hangout("h2", listOf(rui), at(2026, 3, 1)),
    )
    private val interactions = listOf(
        Interaction(ana, InteractionType.CALL, at(2026, 3, 8), id = "c1", source = InteractionSource.CALL_LOG, durationSeconds = 840),
        Interaction(ana, InteractionType.EMAIL, at(2026, 2, 27), id = "e1", batchId = "b"),
        Interaction(pedro, InteractionType.EMAIL, at(2026, 2, 27), id = "e2", batchId = "b"),
        Interaction(ana, InteractionType.WHATSAPP, at(2026, 2, 3), id = "w1", source = InteractionSource.CHAT_IMPORT, messageCount = 48),
        Interaction(ana, InteractionType.VIDEO_CALL, at(2026, 3, 4), id = "v1"),
    )
    private val journal = listOf(JournalEntry("j1", ana, at(2026, 3, 5), "Porto"), JournalEntry("j2", rui, at(2026, 3, 6), "Race"))

    private fun anaTimeline() = Timeline.build(ana, friends, hangouts, interactions, journal, TimeZone.UTC)

    @Test
    fun buildsEveryKindNewestFirst() {
        val keys = anaTimeline().map { it.key }
        assertEquals(
            listOf("hangout-h1", "interaction-c1", "journal-j1", "interaction-v1", "update-u1", "interaction-e1", "gift-g1", "interaction-w1"),
            keys,
        )
    }

    @Test
    fun sharesThatCountedShowOnTheirEntry_andIdeasDont() {
        fun shared(id: String, logId: String?, counts: Boolean, type: InteractionType? = null) = Attachment(
            id = id,
            friendId = ana,
            kind = AttachmentKind.TEXT,
            text = id,
            at = at(2026, 3, 9),
            countsAsInteraction = counts,
            interactionType = type,
            logId = logId,
        )
        val attachments = listOf(
            shared("photo", "h1", true, InteractionType.MEETUP),
            shared("mail", "b", true, InteractionType.EMAIL),
            shared("call", "c1", true, InteractionType.CALL),
            shared("idea", "v1", false),
        )
        val entries = Timeline.build(ana, friends, hangouts, interactions, journal, TimeZone.UTC, attachments)
        assertEquals(entries.size, anaTimeline().size, "attachments never add entries")
        assertEquals("photo", entries.filterIsInstance<TimelineEntry.Meetup>().single().attachment?.id)
        val contacts = entries.filterIsInstance<TimelineEntry.Contact>().associateBy { it.interaction.id }
        assertEquals("mail", contacts.getValue("e1").attachment?.id, "a batch is found by its batch id")
        assertEquals("call", contacts.getValue("c1").attachment?.id)
        assertNull(contacts.getValue("v1").attachment)
    }

    @Test
    fun aGroupMeetupIsOnEveryMembersTimeline_withTheOthers() {
        val forAna = anaTimeline().filterIsInstance<TimelineEntry.Meetup>().single()
        assertEquals(listOf("Rui", "Pedro"), forAna.others.map { it.name })
        val forPedro = Timeline.build(pedro, friends, hangouts, interactions, journal, TimeZone.UTC)
        assertEquals(listOf("hangout-h1", "interaction-e2"), forPedro.map { it.key })
        assertEquals(listOf("Ana"), (forPedro[1] as TimelineEntry.Contact).others.map { it.name })
        assertEquals(
            listOf("Pedro"),
            anaTimeline().filterIsInstance<TimelineEntry.Contact>().single {
                it.interaction.id == "e1"
            }.others.map { it.name },
        )
    }

    @Test
    fun filters() {
        val all = anaTimeline()
        fun keys(f: TimelineFilter) = Timeline.filter(all, f).map { it.key }
        assertEquals(all, Timeline.filter(all, TimelineFilter.ALL))
        assertEquals(listOf("hangout-h1"), keys(TimelineFilter.TOGETHER))
        assertEquals(listOf("interaction-c1", "interaction-v1"), keys(TimelineFilter.CALLS))
        assertEquals(listOf("interaction-e1", "interaction-w1"), keys(TimelineFilter.MESSAGES))
        assertEquals(listOf("journal-j1", "update-u1"), keys(TimelineFilter.NOTES))
        assertEquals(listOf("gift-g1"), keys(TimelineFilter.GIFTS))
    }

    @Test
    fun groupsByMonthNewestFirst() {
        val months = Timeline.byMonth(anaTimeline(), TimeZone.UTC)
        assertEquals(listOf(Month.MARCH, Month.FEBRUARY), months.map { it.month })
        assertEquals(5, months[0].entries.size)
        assertEquals(listOf("interaction-e1", "gift-g1", "interaction-w1"), months[1].entries.map { it.key })
    }

    @Test
    fun anUnknownFriendHasNoTimeline() {
        assertTrue(Timeline.build(FriendId("ghost"), friends, hangouts, interactions, journal, TimeZone.UTC).isEmpty())
    }

    @Test
    fun logKindParsesRouteTypes() {
        assertEquals(LogKind.MEETUP, LogKind.parse("MEETUP"))
        assertEquals(LogKind.MESSAGE, LogKind.parse("WHATSAPP"))
        assertEquals(LogKind.JOURNAL, LogKind.parse("JOURNAL"))
        assertNull(LogKind.parse(null))
        assertNull(LogKind.parse("nope"))
    }

    @Test
    fun loggingAMeetupWritesAHangoutWithPlaceActivityAndNote() = runTest {
        val g = TestGraph(friends = friends)
        val result = g.logInteraction(
            LogRequest(listOf(ana, rui), LogKind.MEETUP, note = " Lunch ", placeName = "Time Out", activity = HangoutActivity.LUNCH),
        )
        val hangout = assertIs<LogResult.Met>(result).hangout
        assertEquals(listOf(ana, rui), hangout.friendIds)
        assertEquals(g.clock.now(), hangout.at)
        assertEquals("Time Out", hangout.place?.name)
        assertEquals(HangoutActivity.LUNCH, hangout.activity)
        assertEquals("Lunch", hangout.note)
        assertEquals(listOf(hangout), g.hangoutRepo.hangouts.value)
        assertTrue(g.interactionRepo.interactions.value.isEmpty())
    }

    @Test
    fun undoingAMeetupThatAnsweredAPlanPutsThePlanBack() = runTest {
        val g = TestGraph(friends = friends)
        val plan = g.savePlan(null, "Lunch at Time Out", listOf(ana), LocalDate(2026, 3, 6))
        val logged = assertIs<LogResult.Met>(g.logInteraction(LogRequest(listOf(ana), LogKind.MEETUP, date = LocalDate(2026, 3, 6))))
        assertNull(logged.plan) // the use case never touches plans; the sheet that answered one closes it
        g.confirmPlan(plan.id, happened = true, day = plan.date, logCheckIn = false)
        assertEquals(PlanStatus.HAPPENED, g.planRepo.plans.value.single().status)
        assertEquals(1, g.hangoutRepo.hangouts.value.size)

        g.undoLog(logged.copy(plan = plan))
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        assertEquals(PlanStatus.PLANNED, g.planRepo.plans.value.single().status)
    }

    @Test
    fun theLoggedMeetupInboxKeepsANewerMeetup() = runTest {
        val g = TestGraph(friends = friends)
        val first = LoggedMeetup(assertIs<LogResult.Met>(g.logInteraction(LogRequest(listOf(ana), LogKind.MEETUP))), emptyList())
        val second = LoggedMeetup(assertIs<LogResult.Met>(g.logInteraction(LogRequest(listOf(rui), LogKind.MEETUP))), emptyList())
        val inbox = LoggedMeetupInbox()
        inbox.offer(first)
        inbox.offer(second)
        inbox.clear(first)
        assertEquals(second, inbox.pending.value)
        inbox.clear(second)
        assertNull(inbox.pending.value)
    }

    @Test
    fun loggingAContactWithSeveralPeopleSharesABatch_andAPastDateIsNoon() = runTest {
        val g = TestGraph(friends = friends)
        val result = g.logInteraction(LogRequest(listOf(ana, pedro, ana), LogKind.EMAIL, date = LocalDate(2026, 3, 1), note = "listings"))
        val rows = assertIs<LogResult.Contacts>(result).interactions
        assertEquals(listOf(ana, pedro), rows.map { it.friendId })
        assertTrue(rows.all { it.type == InteractionType.EMAIL && it.note == "listings" && it.source == InteractionSource.MANUAL })
        assertEquals(1, rows.map { it.batchId }.distinct().size)
        assertTrue(rows.first().batchId != null)
        assertEquals(at(2026, 3, 1), rows.first().at)
        assertEquals(rows.toSet(), g.interactionRepo.interactions.value.toSet())

        val single = assertIs<LogResult.Contacts>(g.logInteraction(LogRequest(listOf(rui), LogKind.VIDEO_CALL, source = InteractionSource.SHARE)))
        assertNull(single.interactions.single().batchId)
        assertEquals(InteractionSource.SHARE, single.interactions.single().source)
        assertEquals(g.clock.now(), single.interactions.single().at)
    }

    @Test
    fun eachContactKindWritesItsType() = runTest {
        val g = TestGraph(friends = friends)
        listOf(LogKind.CALL, LogKind.MESSAGE, LogKind.EMAIL, LogKind.VIDEO_CALL).forEach { kind ->
            val row = assertIs<LogResult.Contacts>(g.logInteraction(LogRequest(listOf(ana), kind))).interactions.single()
            assertEquals(kind.interactionType, row.type)
        }
    }

    @Test
    fun aJournalEntryIsANoteNotContact() = runTest {
        val g = TestGraph(friends = friends)
        val entries = assertIs<LogResult.Journal>(g.logInteraction(LogRequest(listOf(ana, rui), LogKind.JOURNAL, note = "Porto"))).entries
        assertEquals(listOf(ana, rui), entries.map { it.friendId })
        assertEquals(entries.toSet(), g.interactionRepo.journal.value.toSet())
        assertTrue(g.interactionRepo.interactions.value.isEmpty())
        val summary = g.observeFriends().first().single { it.friend.id == ana }
        assertNull(summary.lastContact)
        assertEquals(0, summary.warmth.value)
        assertFailsWith<IllegalArgumentException> { g.logInteraction(LogRequest(listOf(ana), LogKind.JOURNAL, note = " ")) }
    }

    @Test
    fun noPeopleOrAFutureDateIsRejected() = runTest {
        val g = TestGraph(friends = friends)
        assertFailsWith<IllegalArgumentException> { g.logInteraction(LogRequest(emptyList(), LogKind.CALL)) }
        assertFailsWith<IllegalArgumentException> { g.logInteraction(LogRequest(listOf(ana), LogKind.CALL, date = LocalDate(2026, 3, 11))) }
    }

    @Test
    fun undoRemovesWhatALogWrote() = runTest {
        val g = TestGraph(friends = friends)
        g.undoLog(g.logInteraction(LogRequest(listOf(ana), LogKind.MEETUP)))
        g.undoLog(g.logInteraction(LogRequest(listOf(ana, rui), LogKind.CALL)))
        g.undoLog(g.logInteraction(LogRequest(listOf(ana), LogKind.JOURNAL, note = "x")))
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        assertTrue(g.interactionRepo.interactions.value.isEmpty())
        assertTrue(g.interactionRepo.journal.value.isEmpty())
    }

    @Test
    fun deleteAndRestoreEachKindOfEntry() = runTest {
        val g = TestGraph(friends = friends, hangouts = hangouts)
        g.interactionRepo.addAll(interactions)
        journal.forEach { g.interactionRepo.upsertJournal(it) }
        val before = g.observeTimeline(ana).first()
        before.filter { it.deletable }.forEach { g.deleteTimelineEntry(it) }
        assertEquals(listOf("gift-g1"), g.observeTimeline(ana).first().map { it.key })
        assertEquals(listOf("h2"), g.hangoutRepo.hangouts.value.map { it.id }) // the group meetup went for everyone
        before.forEach { g.restoreTimelineEntry(it) }
        assertEquals(before.map { it.key }, g.observeTimeline(ana).first().map { it.key })
    }

    @Test
    fun theFriendPageGetsTheTimelineAndTheLastJournalEntry() = runTest {
        val g = TestGraph(friends = friends, hangouts = hangouts)
        g.interactionRepo.addAll(interactions)
        journal.forEach { g.interactionRepo.upsertJournal(it) }
        val detail = g.observeFriendDetail(ana).first()!!
        assertEquals(anaTimeline().map { it.key }, detail.timeline.map { it.key })
        assertEquals("j1", detail.lastJournal?.id)
        assertEquals(at(2026, 2, 27), detail.lastContactByType[InteractionType.EMAIL])
        assertEquals(at(2026, 3, 4), detail.lastContactByType[InteractionType.VIDEO_CALL])
    }

    @Test
    fun activitiesAreRankedByUse() {
        val ranked = ObserveActivitiesUseCase.ranked(
            listOf(
                Hangout("a", listOf(ana), at(2026, 3, 1), activity = HangoutActivity.WALK),
                Hangout("b", listOf(ana), at(2026, 3, 2), activity = HangoutActivity.WALK),
                Hangout("c", listOf(ana), at(2026, 3, 3), activity = HangoutActivity.MUSIC),
                Hangout("d", listOf(ana), at(2026, 3, 4)),
            ),
        )
        assertEquals(listOf(HangoutActivity.WALK, HangoutActivity.MUSIC, HangoutActivity.LUNCH), ranked.take(3))
        assertEquals(HangoutActivity.entries.size, ranked.size)
    }

    @Test
    fun onlyTheActivitiesTurnedOnAreOffered_mostUsedFirst_thenTheUsersOrder() = runTest {
        val g = TestGraph(friends = friends)
        g.hangoutRepo.add(Hangout("a", listOf(ana), at(2026, 3, 1), activity = HangoutActivity.MUSIC))
        g.hangoutRepo.add(Hangout("b", listOf(ana), at(2026, 3, 2), activity = HangoutActivity.TRIP))
        g.setActivities(listOf(HangoutActivity.WALK, HangoutActivity.COFFEE, HangoutActivity.MUSIC))
        // TRIP is off now: it isn't offered, although an old meetup still has it.
        assertEquals(listOf(HangoutActivity.MUSIC, HangoutActivity.WALK, HangoutActivity.COFFEE), g.observeActivities().first())
        assertEquals(HangoutActivity.TRIP, g.hangoutRepo.hangouts.value.single { it.id == "b" }.activity)

        g.setActivities(emptyList()) // never none
        assertEquals(3, g.settingsRepo.settings.value.activities.size)
    }

    @Test
    fun notificationCalledIsLoggedAsSuch_andTheButtonAsManual() = runTest {
        val g = TestGraph(friends = friends)
        g.logCall(ana)
        g.handleNotificationAction(
            com.neteinstein.goodcompany.core.domain.model.NotificationAction(
                com.neteinstein.goodcompany.core.domain.model.NotificationAction.Kind.CALLED,
                "rui",
            ),
        )
        assertEquals(
            mapOf(ana to InteractionSource.MANUAL, rui to InteractionSource.NOTIFICATION),
            g.interactionRepo.interactions.value.associate { it.friendId to it.source },
        )
    }

    @Test
    fun aChatImportIsOneEntryPerDayWithTheMessageCount_andReimportingAddsNothing() = runTest {
        val g = TestGraph(friends = friends)
        val chat = """
            01/03/2026, 10:00 - Ana: hi
            01/03/2026, 10:05 - Me: hello
            02/03/2026, 09:00 - Ana: again
        """.trimIndent()
        g.importChat(ana, chat)
        g.importChat(ana, chat)
        val rows = g.interactionRepo.interactions.value
        assertEquals(listOf(2, 1), rows.sortedBy { it.at }.map { it.messageCount })
        assertTrue(rows.all { it.source == InteractionSource.CHAT_IMPORT })
    }
}
