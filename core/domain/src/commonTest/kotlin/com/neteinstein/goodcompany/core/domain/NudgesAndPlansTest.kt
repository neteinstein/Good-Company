package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.NotificationAction
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.usecase.AgendaBuilder
import com.neteinstein.goodcompany.core.domain.usecase.NotificationPlanner
import com.neteinstein.goodcompany.core.domain.usecase.NudgePolicy
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NudgesAndPlansTest {
    // Tuesday 10 March 2026, noon UTC.
    private val now = at(2026, 3, 10, 12)
    private val today = LocalDate(2026, 3, 10)
    private val zone = TimeZone.UTC
    private val overdue = listOf(
        // Answered on the last-seen card a week ago (issue #68), so all three are due today.
        friend("ana") { copy(meetEveryDays = 7, approxLastSeen = LocalDate(2026, 3, 3)) },
        friend("bob") { copy(meetEveryDays = 7, approxLastSeen = LocalDate(2026, 3, 3)) },
        friend("cat") { copy(meetEveryDays = 7, approxLastSeen = LocalDate(2026, 3, 3)) },
    )

    private fun plan(policy: NudgePolicy, friends: List<Friend> = overdue, plans: List<Plan> = emptyList()) =
        NotificationPlanner.plan(friends, emptyList(), emptyList(), now, zone, 9, horizonDays = 20, plans = plans, policy = policy)

    @Test
    fun perFriendNudgesAreCappedAtOneADay() {
        val planned = plan(NudgePolicy(weeklyDigest = false)).filter { it.id.startsWith("meet:") }
        assertEquals(listOf("meet:ana:2026-03-11", "meet:bob:2026-03-12", "meet:cat:2026-03-13"), planned.map { it.id })
    }

    @Test
    fun weeklyDigestGathersEveryoneOnTheChosenDay() {
        val planned = plan(NudgePolicy(weeklyDigest = true, digestDay = 6))
        assertTrue(planned.none { it.id.startsWith("meet:") })
        assertEquals(listOf("digest:2026-03-14", "digest:2026-03-21", "digest:2026-03-28"), planned.map { it.id })
        val content = assertIs<NotificationContent.CatchUpDigest>(planned.first().content)
        assertEquals(3, content.total)
        assertEquals(listOf("Ana", "Bob", "Cat"), content.names)
    }

    @Test
    fun ignoredAppMakesNudgesQuieterThenSilent() {
        val quiet = plan(NudgePolicy(weeklyDigest = true, digestDay = 6, lastOpenedAt = at(2026, 2, 1)))
        assertEquals(listOf("digest:2026-03-14", "digest:2026-03-28"), quiet.map { it.id })

        val quietDaily = plan(NudgePolicy(weeklyDigest = false, lastOpenedAt = at(2026, 2, 1))).map { it.id }
        assertEquals(listOf("meet:ana:2026-03-11", "meet:bob:2026-03-14", "meet:cat:2026-03-17"), quietDaily)

        assertTrue(plan(NudgePolicy(weeklyDigest = true, lastOpenedAt = at(2025, 11, 1))).isEmpty())
        assertEquals(NudgePolicy.Quietness.NORMAL, NudgePolicy(lastOpenedAt = at(2026, 3, 9)).quietness(today, zone))
    }

    @Test
    fun digestDayRollsToNextWeekOnceItsHourHasPassed() {
        // Saturday 14 March at 10:00: today's 09:00 digest has gone, next is the 21st.
        val planned = NotificationPlanner.plan(
            overdue,
            emptyList(),
            emptyList(),
            at(2026, 3, 14, 10),
            zone,
            9,
            horizonDays = 10,
            policy = NudgePolicy(),
        )
        assertEquals("digest:2026-03-21", planned.first().id)
        assertEquals(LocalDate(2026, 3, 16), NudgePolicy(digestDay = 1).nextDigestDay(today))
    }

    @Test
    fun pausedFriendsGetNoNudgesAndChatsCanCountAsCatchingUp() {
        val friends = listOf(
            friend("ana") { copy(meetEveryDays = 7, catchUpPaused = true) },
            friend("bob") { copy(meetEveryDays = 7, contactCountsAsCatchUp = true) },
            friend("cat") { copy(meetEveryDays = 7, approxLastSeen = LocalDate(2026, 3, 3)) },
        )
        val chats = listOf(
            Interaction(FriendId("bob"), InteractionType.WHATSAPP, at(2026, 3, 9)),
            Interaction(FriendId("cat"), InteractionType.WHATSAPP, at(2026, 3, 9)),
        )
        val statuses = Relationships.meetupStatuses(friends, emptyList(), today, zone, interactions = chats)
        assertEquals(listOf("cat", "bob"), statuses.map { it.friend.id.value })
        assertEquals(LocalDate(2026, 3, 16), statuses.single { it.friend.id.value == "bob" }.dueDate)
        assertTrue(statuses.single { it.friend.id.value == "cat" }.isOverdue(today))
    }

    @Test
    fun plansGetADayOfReminderAndAFollowUpQuestion() {
        val upcoming = Plan("p1", listOf(FriendId("ana")), "Lunch", LocalDate(2026, 3, 12), createdAt = now)
        val past = Plan("p2", listOf(FriendId("bob")), "Hike", LocalDate(2026, 3, 8), createdAt = now)
        val stale = Plan("p3", listOf(FriendId("bob")), "Old", LocalDate(2026, 2, 1), createdAt = now)
        val done = upcoming.copy(id = "p4", status = PlanStatus.HAPPENED)
        val planned = plan(NudgePolicy(weeklyDigest = false), friends = overdue.take(2), plans = listOf(upcoming, past, stale, done))
            .filter { it.id.startsWith("plan:") }
        assertEquals(listOf("plan:p2:ask", "plan:p1:day", "plan:p1:ask"), planned.map { it.id })
        assertEquals(at(2026, 3, 11, 9), planned.first().at)
        assertEquals(NotificationContent.PlanToday("p1", "Lunch", listOf("Ana")), planned[1].content)
    }

    @Test
    fun agendaShowsUpcomingPlansAndAsksAboutPastOnes() {
        val friends = listOf(friend("ana"))
        val plans = listOf(
            Plan("p1", listOf(FriendId("ana")), "Lunch", LocalDate(2026, 3, 12), createdAt = now),
            Plan("p2", listOf(FriendId("ana")), "Hike", LocalDate(2026, 3, 8), createdAt = now),
            Plan("p3", listOf(FriendId("ana")), "Done", LocalDate(2026, 3, 8), status = PlanStatus.SKIPPED, createdAt = now),
        )
        val items = AgendaBuilder.build(friends, emptyList(), listOf(Hangout("h", listOf(FriendId("ana")), now)), today, zone, plans = plans)
        assertEquals(listOf("plan-confirm-p2", "plan-p1"), items.map { it.key })
        assertIs<AgendaItem.PlanToConfirm>(items.first())
        assertEquals(today, items.first().date)
    }

    @Test
    fun planActionsRoundTrip() {
        val action = NotificationAction(NotificationAction.Kind.PLAN_HAPPENED, "p1")
        assertEquals(action, NotificationAction.decode(action.encode()))
        assertTrue(action.isPlan)
    }
}

class CalendarFileTest {
    @Test
    fun allDayEventIsValidIcs() {
        val ics = com.neteinstein.goodcompany.core.domain.usecase.CalendarFile.allDayEvent(
            uid = "p1",
            title = "Jazz, wine; fun",
            date = LocalDate(2026, 12, 31),
            location = "Hot Clube",
            notes = "Line 1\nLine 2",
            stamp = at(2026, 3, 10, 8, 5),
        )
        val lines = ics.split("\r\n")
        assertEquals("BEGIN:VCALENDAR", lines.first())
        assertTrue("DTSTART;VALUE=DATE:20261231" in lines)
        assertTrue("DTEND;VALUE=DATE:20270101" in lines)
        assertTrue("DTSTAMP:20260310T080500Z" in lines)
        assertTrue("SUMMARY:Jazz\\, wine\\; fun" in lines)
        assertTrue("DESCRIPTION:Line 1\\nLine 2" in lines)
        assertTrue("LOCATION:Hot Clube" in lines)
        assertEquals("jazz-wine-fun.ics", com.neteinstein.goodcompany.core.domain.usecase.CalendarFile.fileName("Jazz, wine; fun!"))
        assertEquals("plan.ics", com.neteinstein.goodcompany.core.domain.usecase.CalendarFile.fileName("!!"))
    }
}
