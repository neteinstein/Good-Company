package com.neteinstein.goodcompany

import com.neteinstein.goodcompany.core.testing.FakePlanRepository
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.usecase.KeepNotificationsScheduledUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RunWeeklyIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeGroupRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeIdeaGenerator
import com.neteinstein.goodcompany.core.testing.FakeIdeaRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import com.neteinstein.goodcompany.core.testing.FakeLocationProvider
import com.neteinstein.goodcompany.core.testing.FakeNotificationRenderer
import com.neteinstein.goodcompany.core.testing.FakeNotificationScheduler
import com.neteinstein.goodcompany.core.testing.FakeReminderRepository
import com.neteinstein.goodcompany.core.testing.FakeSettingsRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.SequentialIds
import com.neteinstein.goodcompany.startup.AppStartup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStartupTest {
    private val friends = FakeFriendRepository(listOf(friend("ana") { copy(meetEveryDays = 7) }))
    private val scheduler = FakeNotificationScheduler()
    private val clock = FixedClock()
    private val interactions = FakeInteractionRepository()
    private val settings = FakeSettingsRepository()

    private fun startup() = AppStartup(
        KeepNotificationsScheduledUseCase(
            friends,
            FakeReminderRepository(),
            FakeHangoutRepository(),
            settings,
            FakeGroupRepository(),
            FakePlanRepository(),
            interactions,
            scheduler,
            FakeNotificationRenderer,
            clock,
        ),
        SyncContactsUseCase(friends, interactions),
        RunWeeklyIdeasUseCase(
            friends,
            interactions,
            settings,
            FakeIdeaRepository(),
            FakeIdeaGenerator(),
            FakeLocationProvider(),
            clock,
            SequentialIds(),
        ),
    )

    @Test
    fun schedulesNotificationsSyncsContactsAndRestartsAfterScopeDies() = runTest {
        friends.contacts = listOf(ContactRecord("1", "Nina", emptyList(), null))
        val startup = startup()

        val first = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        startup.start(first)
        startup.start(first) // no duplicate run
        advanceUntilIdle()
        assertTrue(scheduler.scheduled.isNotEmpty())
        assertTrue(friends.friends.value.any { it.name == "Nina" })
        val runs = scheduler.scheduled.size

        // Activity destroyed → root ViewModel scope cancelled → a new UI must start the work again.
        first.cancel()
        val second = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        startup.start(second)
        advanceUntilIdle()
        assertEquals(runs + 1, scheduler.scheduled.size)
        second.cancel()
    }
}
