package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.NotificationAction
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.usecase.OccasionMatcher
import com.neteinstein.goodcompany.core.domain.usecase.OfflineTopics
import com.neteinstein.goodcompany.core.domain.usecase.RestoreResult
import com.neteinstein.goodcompany.core.domain.usecase.TopicHint
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewUseCasesTest {
    private val ana = FriendId("ana")
    private val bob = FriendId("bob")

    private fun graph(settings: AppSettings = AppSettings()) = TestGraph(friends = listOf(friend("ana"), friend("bob")), settings = settings)

    @Test
    fun plansAreSavedRescheduledAndConfirmed() = runTest {
        val g = graph()
        g.ideaRepo.addAll(listOf(Idea("i1", listOf(ana), IdeaKind.EVENT, "Jazz", "", createdAt = g.clock.now())))
        val plan = g.savePlan(null, " Jazz night ", listOf(ana, ana), LocalDate(2026, 3, 12), place = " ", ideaId = "i1")
        assertEquals("Jazz night", plan.title)
        assertNull(plan.place)
        assertEquals(listOf(ana), plan.friendIds)
        assertTrue(g.ideaRepo.ideas.value.single().proposedAt != null)

        val moved = g.savePlan(plan.id, "Jazz night", listOf(ana, bob), LocalDate(2026, 3, 13), place = "Hot Clube")
        assertEquals(plan.createdAt, moved.createdAt)
        assertEquals("i1", moved.ideaId)
        assertEquals(listOf(moved), g.observePlans.forFriend(bob).first())

        // Past plan confirmed: a check-in on the plan's day, at its place.
        g.clock.instant = at(2026, 3, 15)
        g.confirmPlan(plan.id, happened = true)
        val hangout = g.hangoutRepo.hangouts.value.single()
        assertEquals(at(2026, 3, 13), hangout.at)
        assertEquals("Hot Clube", hangout.place?.name)
        assertEquals(PlanStatus.HAPPENED, g.planRepo.plans.value.single().status)
        assertTrue(g.observePlans().first().isEmpty())

        // Answering twice does nothing.
        g.confirmPlan(plan.id, happened = true)
        assertEquals(1, g.hangoutRepo.hangouts.value.size)

        assertFailsWith<IllegalArgumentException> { g.savePlan(null, " ", listOf(ana), LocalDate(2026, 3, 20)) }
        assertFailsWith<IllegalArgumentException> { g.savePlan(null, "x", emptyList(), LocalDate(2026, 3, 20)) }
    }

    @Test
    fun skippedPlansAndNotificationAnswersAndUndo() = runTest {
        val g = graph()
        val p = g.savePlan(null, "Coffee", listOf(ana), LocalDate(2026, 3, 9))
        g.handleNotificationAction(NotificationAction(NotificationAction.Kind.PLAN_SKIPPED, p.id))
        assertEquals(PlanStatus.SKIPPED, g.planRepo.plans.value.single().status)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())

        val q = g.savePlan(null, "Lunch", listOf(ana), LocalDate(2026, 3, 10))
        g.handleNotificationAction(NotificationAction(NotificationAction.Kind.PLAN_HAPPENED, q.id))
        assertEquals(g.clock.now(), g.hangoutRepo.hangouts.value.single().at)

        val r = g.savePlan(null, "Dinner", listOf(ana), LocalDate(2026, 3, 20))
        g.cancelPlan(r.id)
        assertTrue(g.planRepo.plans.value.none { it.id == r.id })
        g.restorePlan(r)
        assertEquals(r, g.planRepo.plans.value.last())
        g.confirmPlan("missing", happened = true)
    }

    @Test
    fun pauseAndAppOpened() = runTest {
        val g = graph()
        g.updateFriend(ana) { it.copy(snoozedUntil = LocalDate(2026, 3, 20)) }
        g.setCatchUpPaused(ana, true)
        assertTrue(g.friendRepo.get(ana)!!.catchUpPaused)
        assertNull(g.friendRepo.get(ana)!!.snoozedUntil)
        g.setCatchUpPaused(ana, false)
        assertFalse(g.friendRepo.get(ana)!!.catchUpPaused)
        g.setCatchUpPaused(FriendId("nope"), true)

        g.recordAppOpened()
        assertEquals(g.clock.now(), g.settingsRepo.settings.value.lastOpenedAt)
    }

    @Test
    fun pickedContactsBecomeRealFriendsWithoutDuplicates() = runTest {
        val g = graph()
        val result = g.importPickedContacts(
            listOf(
                ContactRecord("42", "Carla Dias", listOf("+351 900"), Birthday(5, 1)),
                ContactRecord("42", "Carla Dias", emptyList(), null),
                ContactRecord("7", "ana", listOf("+351 911"), null),
                ContactRecord("8", " ", emptyList(), null),
            ),
        )
        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        val carla = g.friendRepo.get(FriendId("contact-42"))!!
        assertTrue(carla.isCloseFriend)
        assertEquals(Circle.CLOSE, carla.circle)
        assertEquals("42", carla.contactId)
        val updatedAna = g.friendRepo.get(ana)!!
        assertEquals("7", updatedAna.contactId)
        assertEquals(listOf("+351 911"), updatedAna.phoneNumbers)

        // Picking the same people again changes nothing.
        val again = g.importPickedContacts(listOf(ContactRecord("42", "Carla Dias", listOf("+351 900"), null)))
        assertEquals(0, again.added + again.updated)
    }

    @Test
    fun backupRoundTripNeverCarriesTheApiKey() = runTest {
        val g = graph(AppSettings(aiApiKey = "sk-secret", homeArea = "Lisbon", appLock = true))
        g.savePlan(null, "Lunch", listOf(ana), LocalDate(2026, 3, 12))
        val json = g.exportBackup()
        assertFalse("sk-secret" in json)
        assertTrue("good-company-backup" in json)
        assertEquals("good-company-2026-03-10.json", g.exportBackup.fileName())

        val other = TestGraph(settings = AppSettings(aiApiKey = "sk-other", appLock = false))
        val restored = assertIs<RestoreResult.Restored>(other.restoreBackup(json))
        assertEquals(2, restored.friends)
        assertEquals(listOf("Ana", "Bob"), other.friendRepo.friends.value.map { it.name })
        assertEquals(1, other.planRepo.plans.value.size)
        val settings = other.settingsRepo.settings.value
        assertEquals("sk-other", settings.aiApiKey)
        assertEquals("Lisbon", settings.homeArea)
        assertFalse(settings.appLock)
        assertTrue(settings.onboardingDone)

        assertEquals(RestoreResult.NotABackup, other.restoreBackup("{\"friends\":[]}"))
        assertEquals(RestoreResult.NotABackup, other.restoreBackup("not json"))
        assertEquals(RestoreResult.NotABackup, other.restoreBackup("{\"format\":\"good-company-backup\",\"version\":99}"))
    }

    @Test
    fun occasionMatchesPutDueFriendsFirst() {
        val zone = TimeZone.UTC
        val today = LocalDate(2026, 3, 10)
        val friends = listOf(
            friend("ana") { copy(occasions = listOf(Occasion.WEEKDAY_LUNCH), meetEveryDays = 30) },
            friend("bob") { copy(occasions = listOf(Occasion.WEEKDAY_LUNCH, Occasion.OUTDOORS)) },
            friend("cat") { copy(occasions = listOf(Occasion.WEEKDAY_LUNCH), meetEveryDays = 7) },
            friend("dan") { copy(occasions = listOf(Occasion.WEEKDAY_LUNCH), catchUpPaused = true) },
            friend("eve"),
        )
        val hangouts = listOf(
            Hangout("1", listOf(FriendId("ana")), at(2026, 3, 1)),
            Hangout("2", listOf(FriendId("bob")), at(2026, 1, 1)),
            Hangout("3", listOf(FriendId("cat")), at(2026, 3, 1)),
        )
        val lunch = OccasionMatcher.match(Occasion.WEEKDAY_LUNCH, friends, hangouts, emptyList(), today, zone)
        assertEquals(listOf("cat", "bob", "ana"), lunch.map { it.friend.id.value })
        assertTrue(lunch.first().catchUpDue)
        assertEquals(68, lunch[1].daysSinceLastSeen)
        assertTrue(OccasionMatcher.match(Occasion.DINNER, friends, hangouts, emptyList(), today, zone).isEmpty())
    }

    @Test
    fun occasionMatchesFlowOnlyListsUsedOccasions() = runTest {
        val g = TestGraph(friends = listOf(friend("ana") { copy(occasions = listOf(Occasion.COFFEE)) }))
        assertEquals(setOf(Occasion.COFFEE), g.observeOccasionMatches().first().keys)
    }

    @Test
    fun offlineTopicsComeFromWhatYouAlreadyKnow() {
        val zone = TimeZone.UTC
        val today = LocalDate(2026, 3, 10)
        val ana = friend("ana") {
            copy(
                likes = listOf("jazz", "sushi", "F1"),
                importantEvents = listOf(
                    ImportantEvent("e1", "Knee surgery", LocalDate(2026, 3, 6)),
                    ImportantEvent("e2", "Job interview", LocalDate(2026, 3, 12)),
                    ImportantEvent("e3", "Old thing", LocalDate(2025, 12, 1)),
                ),
                kids = listOf(Kid("k", "Leo", Birthday(3, 25, 2020)), Kid("m", "Mia", Birthday(9, 1))),
            )
        }
        val hangouts = listOf(
            Hangout("1", listOf(FriendId("ana")), at(2026, 3, 1), Place("Hot Clube"), note = "She's moving to Porto"),
            Hangout("2", listOf(FriendId("ana")), at(2026, 2, 1), note = " "),
            Hangout("3", listOf(FriendId("bob")), at(2026, 3, 2), note = "Not hers"),
        )
        val topics = OfflineTopics.build(ana, hangouts, today, zone)
        assertEquals(OfflineTopics.MAX, topics.size)
        assertEquals(TopicHint.EventFollowUp("Job interview", LocalDate(2026, 3, 12), isPast = false), topics[0])
        assertEquals(TopicHint.EventFollowUp("Knee surgery", LocalDate(2026, 3, 6), isPast = true), topics[1])
        assertEquals(TopicHint.FromNote("She's moving to Porto", LocalDate(2026, 3, 1), "Hot Clube"), topics[2])
        assertEquals(TopicHint.KidBirthday("Leo", LocalDate(2026, 3, 25), 6), topics[3])
        assertIs<TopicHint.Like>(topics[4])
        assertTrue(OfflineTopics.build(friend("bob"), emptyList(), today, zone).isEmpty())
    }

    @Test
    fun privateNotificationsCarryNeutralLockScreenText() = runTest {
        val g = TestGraph(friends = listOf(friend("ana") { copy(meetEveryDays = 7) }), settings = AppSettings(privateNotifications = true))
        val job = launch { g.keepNotificationsScheduled() }
        advanceUntilIdle()
        assertTrue(g.scheduler.scheduled.last().all { it.publicText == "PRIVATE" })
        g.settingsRepo.update { it.copy(privateNotifications = false) }
        advanceUntilIdle()
        assertTrue(g.scheduler.scheduled.last().all { it.publicText == null })
        job.cancel()
    }

    @Test
    fun friendDetailIncludesTopicsAndPlans() = runTest {
        val g = TestGraph(friends = listOf(friend("ana") { copy(likes = listOf("jazz")) }))
        g.savePlan(null, "Lunch", listOf(ana), LocalDate(2026, 3, 12))
        val detail = g.observeFriendDetail(ana).first()!!
        assertEquals(listOf(TopicHint.Like("jazz")), detail.topics)
        assertEquals("Lunch", detail.plans.single().title)
    }
}

class HomeWidgetTest {
    @Test
    fun showsNextThingAndMostOverdueFriend() = kotlinx.coroutines.test.runTest {
        val g = TestGraph(
            friends = listOf(
                friend("ana") { copy(meetEveryDays = 7) },
                friend("bob") { copy(notifyBirthday = true, birthday = Birthday(3, 12)) },
            ),
        )
        val widget = com.neteinstein.goodcompany.core.domain.usecase.ObserveHomeWidgetUseCase(
            g.observeAgenda,
            g.observeMeetups,
            g.settingsRepo,
            g.clock,
        )
        val summary = widget().first()
        assertIs<com.neteinstein.goodcompany.core.domain.model.AgendaItem.FriendBirthday>(summary.next)
        assertEquals("ana", summary.catchUp?.friend?.id?.value)
        assertFalse(summary.isPrivate)
        g.settingsRepo.update { it.copy(appLock = true) }
        assertTrue(widget().first().isPrivate)
        val empty = com.neteinstein.goodcompany.core.domain.usecase.HomeWidget.summarize(emptyList(), emptyList(), LocalDate(2026, 3, 10), false)
        assertNull(empty.next)
        assertNull(empty.catchUp)
    }
}
