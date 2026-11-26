package org.goodcompany.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.goodcompany.app.core.domain.model.AppLanguage
import org.goodcompany.app.core.domain.model.ContactRecord
import org.goodcompany.app.core.domain.usecase.KeepLanguageInStepUseCase
import org.goodcompany.app.core.domain.usecase.KeepNotificationsScheduledUseCase
import org.goodcompany.app.core.domain.usecase.ObserveCalendarEventsUseCase
import org.goodcompany.app.core.domain.usecase.RecordAppOpenedUseCase
import org.goodcompany.app.core.domain.usecase.RefreshFeedsUseCase
import org.goodcompany.app.core.domain.usecase.RunWeeklyIdeasUseCase
import org.goodcompany.app.core.domain.usecase.SyncContactsUseCase
import org.goodcompany.app.core.testing.FakeAppLanguagePlatform
import org.goodcompany.app.core.testing.FakeCalendarDecisionRepository
import org.goodcompany.app.core.testing.FakeCalendarReader
import org.goodcompany.app.core.testing.FakeFeedFetcher
import org.goodcompany.app.core.testing.FakeFriendRepository
import org.goodcompany.app.core.testing.FakeGroupRepository
import org.goodcompany.app.core.testing.FakeHangoutRepository
import org.goodcompany.app.core.testing.FakeIdeaGenerator
import org.goodcompany.app.core.testing.FakeIdeaRepository
import org.goodcompany.app.core.testing.FakeInteractionRepository
import org.goodcompany.app.core.testing.FakeLocationProvider
import org.goodcompany.app.core.testing.FakeNotificationRenderer
import org.goodcompany.app.core.testing.FakeNotificationScheduler
import org.goodcompany.app.core.testing.FakePersonLinkRepository
import org.goodcompany.app.core.testing.FakePlanRepository
import org.goodcompany.app.core.testing.FakeReminderRepository
import org.goodcompany.app.core.testing.FakeSettingsRepository
import org.goodcompany.app.core.testing.FixedClock
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.SequentialIds
import org.goodcompany.app.startup.AppStartup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStartupTest {
    private val friends = FakeFriendRepository(listOf(friend("ana") { copy(meetEveryDays = 7) }))
    private val scheduler = FakeNotificationScheduler()
    private val clock = FixedClock()
    private val interactions = FakeInteractionRepository()
    private val settings = FakeSettingsRepository()
    private val language = FakeAppLanguagePlatform()

    private fun startup() = AppStartup(
        KeepNotificationsScheduledUseCase(
            friends,
            FakeReminderRepository(),
            FakeHangoutRepository(),
            settings,
            FakeGroupRepository(),
            FakePlanRepository(),
            interactions,
            FakePersonLinkRepository(),
            ObserveCalendarEventsUseCase(settings, FakeCalendarReader(), clock),
            FakeCalendarDecisionRepository(),
            scheduler,
            FakeNotificationRenderer,
            clock,
        ),
        SyncContactsUseCase(friends, interactions),
        RunWeeklyIdeasUseCase(
            friends,
            interactions,
            FakeHangoutRepository(),
            settings,
            FakeIdeaRepository(),
            FakeIdeaGenerator(),
            FakeLocationProvider(),
            clock,
            SequentialIds(),
        ),
        RecordAppOpenedUseCase(settings, clock),
        RefreshFeedsUseCase(friends, FakeFeedFetcher(), clock),
        KeepLanguageInStepUseCase(settings, language),
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
        assertEquals(clock.now(), settings.settings.value.lastOpenedAt)
        assertEquals(listOf(AppLanguage.SYSTEM), language.applied)
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
