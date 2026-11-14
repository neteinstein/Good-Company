package com.neteinstein.goodcompany

import com.neteinstein.goodcompany.core.data.db.AppDatabase
import com.neteinstein.goodcompany.core.data.db.DatabaseDriverFactory
import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import com.neteinstein.goodcompany.core.domain.usecase.HandleNotificationActionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KeepNotificationsScheduledUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveShortcutsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.QuickEntryInbox
import com.neteinstein.goodcompany.core.domain.usecase.RunWeeklyIdeasUseCase
import com.neteinstein.goodcompany.core.testing.FakeContactsWriter
import com.neteinstein.goodcompany.core.testing.FakeLocationProvider
import com.neteinstein.goodcompany.core.testing.FakeNotificationScheduler
import com.neteinstein.goodcompany.di.appModule
import com.neteinstein.goodcompany.feature.agenda.ui.screens.AgendaViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.choose.ChooseContactsViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.FriendDetailViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendsViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.log.LogInteractionViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.share.ShareToFriendViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.story.CatchUpStoryViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.timeline.FriendTimelineViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.HangoutsViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.circle.CircleEditorViewModel
import com.neteinstein.goodcompany.feature.ideas.ui.screens.IdeasViewModel
import com.neteinstein.goodcompany.feature.review.ui.screens.YearReviewViewModel
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsViewModel
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/** Resolves the whole graph from the single shared [appModule]; only OS-bound sources are swapped for fakes. */
class AppModuleTest {
    private val platformFakes = module {
        single<Settings> { MapSettings() }
        single<ContactsSource> { ContactsSource { emptyList() } }
        single<ContactsWriter> { FakeContactsWriter() }
        single<CommunicationLogSource> { CommunicationLogSource.None }
        single<LocationProvider> { FakeLocationProvider() }
        single<NotificationScheduler> { FakeNotificationScheduler() }
        single<DatabaseDriverFactory> { DatabaseDriverFactory { error("The database isn't opened in this test") } }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun everyViewModelAndUseCaseResolves() {
        val koin = startKoin {
            allowOverride(true)
            modules(appModule, platformFakes)
        }.koin
        assertNotNull(koin.get<AppViewModel>())
        assertNotNull(koin.get<AppDatabase>())
        assertNotNull(koin.get<FriendsViewModel>())
        assertNotNull(koin.get<FriendDetailViewModel> { parametersOf(FriendId("x")) })
        assertNotNull(koin.get<FriendTimelineViewModel> { parametersOf(FriendId("x")) })
        assertNotNull(koin.get<LogInteractionViewModel> { parametersOf(listOf(FriendId("x")), "CALL", null) })
        assertNotNull(koin.get<LogInteractionViewModel> { parametersOf(emptyList<FriendId>(), null, "circle-1") })
        assertNotNull(koin.get<CircleEditorViewModel> { parametersOf(null) })
        assertNotNull(koin.get<CircleEditorViewModel> { parametersOf("circle-1") })
        assertNotNull(koin.get<HangoutsViewModel>())
        assertNotNull(koin.get<AgendaViewModel>())
        assertNotNull(koin.get<IdeasViewModel>())
        assertNotNull(koin.get<SettingsViewModel>())
        assertNotNull(koin.get<RunWeeklyIdeasUseCase>())
        assertNotNull(koin.get<com.neteinstein.goodcompany.feature.onboarding.ui.screens.OnboardingViewModel>())
        assertNotNull(koin.get<ApplyLaunchOptions>())
        assertNotNull(koin.get<CatchUpStoryViewModel> { parametersOf(FriendId("x")) })
        assertNotNull(koin.get<ShareToFriendViewModel>())
        assertNotNull(koin.get<ChooseContactsViewModel>())
        assertNotNull(koin.get<ObserveShortcutsUseCase>())
        assertSame(koin.get<QuickEntryInbox>(), koin.get<QuickEntryInbox>())
        assertNotNull(koin.get<YearReviewViewModel> { parametersOf(null) })
        assertNotNull(koin.get<YearReviewViewModel> { parametersOf(2025) })
        assertNotNull(koin.get<HandleNotificationActionUseCase>())
        assertNotNull(koin.get<KeepNotificationsScheduledUseCase>())
    }
}
