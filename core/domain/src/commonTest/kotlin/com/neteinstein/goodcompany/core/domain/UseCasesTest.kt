package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.repository.GeneratedIdea
import com.neteinstein.goodcompany.core.domain.repository.IdeaRequest
import com.neteinstein.goodcompany.core.domain.usecase.AddFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GenerateTalkingPointsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportChatUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KeepNotificationsScheduledUseCase
import com.neteinstein.goodcompany.core.domain.usecase.MissingApiKeyException
import com.neteinstein.goodcompany.core.domain.usecase.ObserveCalendarEventsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendDetailUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveHangoutHistoryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveRemindersUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RunWeeklyIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.WeeklyIdeasOutcome
import com.neteinstein.goodcompany.core.testing.FakeCalendarDecisionRepository
import com.neteinstein.goodcompany.core.testing.FakeCalendarReader
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeGroupRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeIdeaGenerator
import com.neteinstein.goodcompany.core.testing.FakeIdeaRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import com.neteinstein.goodcompany.core.testing.FakeLocationProvider
import com.neteinstein.goodcompany.core.testing.FakeNotificationRenderer
import com.neteinstein.goodcompany.core.testing.FakeNotificationScheduler
import com.neteinstein.goodcompany.core.testing.FakePersonLinkRepository
import com.neteinstein.goodcompany.core.testing.FakePlanRepository
import com.neteinstein.goodcompany.core.testing.FakeReminderRepository
import com.neteinstein.goodcompany.core.testing.FakeSettingsRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.SequentialIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class UseCasesTest {
    private val clock = FixedClock(at(2026, 3, 10))
    private val ids = SequentialIds()
    private val friends = FakeFriendRepository(
        listOf(
            friend("bob"),
            friend("ana") { copy(isCloseFriend = true, likes = listOf("jazz"), meetEveryDays = 7) },
            friend("cat") { copy(birthday = Birthday(4, 1)) },
        ),
    )
    private val hangouts = FakeHangoutRepository()
    private val interactions = FakeInteractionRepository()
    private val ideas = FakeIdeaRepository()
    private val settings = FakeSettingsRepository()
    private val reminders = FakeReminderRepository()
    private val generator = FakeIdeaGenerator()

    @Test
    fun observeFriends_sortsCloseFirstAndFlagsOverdue() = runTest {
        hangouts.add(Hangout("h", listOf(FriendId("bob")), at(2026, 3, 1)))
        val list = ObserveFriendsUseCase(friends, hangouts, interactions, settings, FakePersonLinkRepository(), clock)().first()
        assertEquals(listOf("ana", "bob", "cat"), list.map { it.friend.id.value })
        assertTrue(list[0].meetupOverdue)
        assertEquals(at(2026, 3, 1), list[1].lastSeen)
        assertEquals(kotlinx.datetime.LocalDate(2026, 4, 1), list[2].nextBirthday)
    }

    @Test
    fun checkIn_recordsHangoutAndRequiresFriends() = runTest {
        val checkIn = CheckInUseCase(hangouts, clock, ids)
        val h = checkIn(listOf(FriendId("ana"), FriendId("ana"), FriendId("bob")), "  Café  ", GeoPoint(38.7, -9.1))
        assertEquals(listOf(FriendId("ana"), FriendId("bob")), h.friendIds)
        assertEquals("Café", h.place?.name)
        assertEquals(clock.now(), h.at)
        assertEquals(1, hangouts.hangouts.value.size)
        assertFailsWith<IllegalArgumentException> { checkIn(emptyList()) }
    }

    @Test
    fun checkIn_withoutPlaceStoresNoPlace() = runTest {
        assertNull(CheckInUseCase(hangouts, clock, ids)(listOf(FriendId("ana"))).place)
    }

    @Test
    fun hangoutHistory_filtersByEveryoneSelected() = runTest {
        hangouts.add(Hangout("1", listOf(FriendId("ana")), at(2026, 1, 1)))
        hangouts.add(Hangout("2", listOf(FriendId("ana"), FriendId("bob")), at(2026, 2, 1)))
        val result = ObserveHangoutHistoryUseCase(friends, hangouts)(flowOf(setOf(FriendId("ana"), FriendId("bob")))).first()
        assertEquals(listOf("2"), result.map { it.hangout.id })
        assertEquals(listOf("Ana", "Bob"), result.single().friends.map { it.name })
    }

    @Test
    fun updateAndAddFriend() = runTest {
        UpdateFriendUseCase(friends)(FriendId("bob")) { it.copy(isCloseFriend = true) }
        assertTrue(friends.get(FriendId("bob"))!!.isCloseFriend)
        val id = AddFriendUseCase(friends, ids)("  Zoe ")
        assertEquals("Zoe", friends.get(id)!!.name)
        assertFailsWith<IllegalArgumentException> { AddFriendUseCase(friends, ids)(" ") }
    }

    @Test
    fun syncContacts_mergesAndPullsLogs() = runTest {
        friends.contacts = listOf(ContactRecord("9", "Nina", emptyList(), null))
        interactions.logEntries = listOf(Interaction(FriendId("ana"), InteractionType.CALL, at(2026, 3, 9)))
        val result = SyncContactsUseCase(friends, interactions)()
        assertEquals(1, result.contacts)
        assertEquals(1, result.logEntries)
        assertTrue(friends.friends.value.any { it.name == "Nina" })
    }

    @Test
    fun importChat_addsOneInteractionPerDayAndMessages() = runTest {
        val raw = "01/03/2026, 10:00 - Ana: hi\n01/03/2026, 10:05 - Me: hey\n02/03/2026, 09:00 - Ana: jazz tonight?"
        val result = ImportChatUseCase(friends, interactions, clock)(FriendId("ana"), raw)
        assertEquals(3, result.messages)
        assertEquals("Ana", result.friendSender)
        assertEquals(2, interactions.interactions.value.size)
        val msgs = interactions.messages.value
        assertEquals(listOf(false, true, false), msgs.map { it.fromMe })
    }

    @Test
    fun friendDetail_combinesEverything() = runTest {
        hangouts.add(Hangout("1", listOf(FriendId("ana"), FriendId("bob")), at(2026, 3, 1)))
        interactions.addAll(listOf(Interaction(FriendId("ana"), InteractionType.CALL, at(2026, 3, 5))))
        val detail = ObserveFriendDetailUseCase(
            friends,
            hangouts,
            interactions,
            ideas,
            settings,
            FakeGroupRepository(),
            FakePlanRepository(),
            FakePersonLinkRepository(),
            clock,
        )(FriendId("ana")).first()!!
        assertEquals(at(2026, 3, 1), detail.lastSeen)
        assertEquals(at(2026, 3, 5), detail.lastContactByType[InteractionType.CALL])
        assertEquals(at(2026, 3, 1), detail.lastContactByType[InteractionType.MEETUP])
        assertEquals(listOf("Ana", "Bob"), detail.hangouts.single().friends.map { it.name })
        assertNull(
            ObserveFriendDetailUseCase(
                friends,
                hangouts,
                interactions,
                ideas,
                settings,
                FakeGroupRepository(),
                FakePlanRepository(),
                FakePersonLinkRepository(),
                clock,
            )(FriendId("nope")).first(),
        )
    }

    @Test
    fun reminders_sortOpenByDueThenDone() = runTest {
        val add = AddReminderUseCase(reminders, clock, ids)
        val later = add("later", emptyList(), at(2026, 4, 1))
        add("undated", emptyList(), null)
        add("sooner", listOf(FriendId("ana")), at(2026, 3, 20))
        reminders.upsert(later.copy(done = true))
        val sorted = ObserveRemindersUseCase(reminders)().first()
        assertEquals(listOf("sooner", "undated", "later"), sorted.map { it.title })
    }

    @Test
    fun talkingPoints_requireApiKey() = runTest {
        val useCase = GenerateTalkingPointsUseCase(friends, interactions, FakeHangoutRepository(), settings, ideas, generator, clock, ids)
        assertIs<MissingApiKeyException>(useCase(FriendId("ana")).exceptionOrNull())
        assertTrue(generator.requests.isEmpty())
    }

    @Test
    fun talkingPoints_storeIdeasForTheFriend() = runTest {
        settings.update { it.copy(aiApiKey = "sk-test", aiEnabled = true) }
        generator.result = Result.success(
            listOf(
                GeneratedIdea(emptyList(), "news", "Jazz fest lineup", "Big names announced", "https://example.com", null),
                GeneratedIdea(emptyList(), "talking_point", " ", "blank titles are dropped", null, null),
            ),
        )
        val count = GenerateTalkingPointsUseCase(
            friends,
            interactions,
            FakeHangoutRepository(),
            settings,
            ideas,
            generator,
            clock,
            ids,
        )(FriendId("ana")).getOrThrow()
        assertEquals(1, count)
        val idea = ideas.ideas.value.single()
        assertEquals(IdeaKind.NEWS, idea.kind)
        assertEquals(listOf(FriendId("ana")), idea.friendIds)
        assertEquals(listOf("jazz"), generator.requests.single().friends.single().likes)
    }

    @Test
    fun weeklyIdeas_skipReasonsAndCadence() = runTest {
        val useCase =
            RunWeeklyIdeasUseCase(
                friends, interactions, FakeHangoutRepository(), settings, ideas, generator,
                FakeLocationProvider(
                    GeoPoint(1.0, 2.0),
                ),
                clock, ids,
            )
        // AI off (the default): the job never runs, not even forced, whatever the key (F1).
        settings.update { it.copy(aiApiKey = "k") }
        assertEquals(WeeklyIdeasOutcome.Skipped(WeeklyIdeasOutcome.Reason.AI_OFF), useCase())
        assertEquals(WeeklyIdeasOutcome.Skipped(WeeklyIdeasOutcome.Reason.AI_OFF), useCase(force = true))
        settings.update { it.copy(aiApiKey = null, aiEnabled = true) }
        assertEquals(WeeklyIdeasOutcome.Skipped(WeeklyIdeasOutcome.Reason.NO_API_KEY), useCase())
        settings.update { it.copy(aiApiKey = "k", aiEnabled = true) }
        assertEquals(WeeklyIdeasOutcome.Skipped(WeeklyIdeasOutcome.Reason.NO_FRIENDS_OPTED_IN), useCase())

        friends.upsert(friends.get(FriendId("ana"))!!.copy(suggestEvents = true))
        generator.result = Result.success(listOf(GeneratedIdea(listOf("ana", "ghost"), "event", "Jazz night", "Fri 21h", null, "Friday")))
        assertEquals(WeeklyIdeasOutcome.Done(1), useCase())
        assertEquals(IdeaRequest.Mode.WEEKLY_EVENTS, generator.requests.last().mode)
        assertEquals("near 1.0, 2.0", generator.requests.last().area)
        assertEquals(listOf(FriendId("ana")), ideas.ideas.value.single().friendIds)

        assertEquals(WeeklyIdeasOutcome.Skipped(WeeklyIdeasOutcome.Reason.NOT_DUE), useCase())
        clock.advanceBy(7.days)
        assertIs<WeeklyIdeasOutcome.Done>(useCase())

        settings.update { it.copy(weeklyIdeasEnabled = false) }
        assertEquals(WeeklyIdeasOutcome.Skipped(WeeklyIdeasOutcome.Reason.DISABLED), useCase())
        assertIs<WeeklyIdeasOutcome.Done>(useCase(force = true))
    }

    @Test
    fun weeklyIdeas_failureDoesNotAdvanceLastRun() = runTest {
        settings.update { AppSettings(aiApiKey = "k", homeArea = "Lisbon", aiEnabled = true) }
        friends.upsert(friends.get(FriendId("ana"))!!.copy(suggestEvents = true))
        generator.result = Result.failure(RuntimeException("boom"))
        val useCase =
            RunWeeklyIdeasUseCase(friends, interactions, FakeHangoutRepository(), settings, ideas, generator, FakeLocationProvider(), clock, ids)
        assertIs<WeeklyIdeasOutcome.Failed>(useCase())
        assertNull(settings.settings.value.lastIdeasRunAt)
        assertEquals("Lisbon", generator.requests.single().area)
    }

    @Test
    fun keepNotificationsScheduled_replansOnChange() = runTest {
        val scheduler = FakeNotificationScheduler()
        val job =
            launch {
                KeepNotificationsScheduledUseCase(
                    friends,
                    reminders,
                    hangouts,
                    settings,
                    FakeGroupRepository(),
                    FakePlanRepository(),
                    FakeInteractionRepository(),
                    FakePersonLinkRepository(),
                    ObserveCalendarEventsUseCase(settings, FakeCalendarReader(), clock),
                    FakeCalendarDecisionRepository(),
                    scheduler,
                    FakeNotificationRenderer,
                    clock,
                )()
            }
        advanceUntilIdle()
        val first = scheduler.scheduled.last()
        // Catch-ups arrive in the weekly digest by default.
        assertTrue(first.any { it.id.startsWith("digest:") && "Ana" in it.body })
        friends.upsert(friends.get(FriendId("cat"))!!.copy(notifyBirthday = true))
        advanceUntilIdle()
        assertTrue(scheduler.scheduled.last().any { it.id == "bday:cat:2026-04-01" })
        job.cancel()
    }
}
