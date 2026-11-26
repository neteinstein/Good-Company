package org.goodcompany.app

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.goodcompany.app.core.data.db.AppDatabase
import org.goodcompany.app.core.data.db.DatabaseDriverFactory
import org.goodcompany.app.core.data.source.CommunicationLogSource
import org.goodcompany.app.core.data.source.ContactsSource
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.HangoutActivity
import org.goodcompany.app.core.domain.model.PlanOccurrence
import org.goodcompany.app.core.domain.repository.CalendarReader
import org.goodcompany.app.core.domain.repository.ContactsWriter
import org.goodcompany.app.core.domain.repository.DriveTokenSource
import org.goodcompany.app.core.domain.repository.LocationProvider
import org.goodcompany.app.core.domain.repository.NotificationScheduler
import org.goodcompany.app.core.domain.usecase.HandleNotificationActionUseCase
import org.goodcompany.app.core.domain.usecase.KeepNotificationsScheduledUseCase
import org.goodcompany.app.core.domain.usecase.ObserveShortcutsUseCase
import org.goodcompany.app.core.domain.usecase.QuickEntryInbox
import org.goodcompany.app.core.domain.usecase.RunAutoBackupUseCase
import org.goodcompany.app.core.domain.usecase.RunWeeklyIdeasUseCase
import org.goodcompany.app.core.testing.FakeCalendarReader
import org.goodcompany.app.core.testing.FakeContactsWriter
import org.goodcompany.app.core.testing.FakeDriveTokenSource
import org.goodcompany.app.core.testing.FakeLocationProvider
import org.goodcompany.app.core.testing.FakeNotificationScheduler
import org.goodcompany.app.di.appModule
import org.goodcompany.app.feature.agenda.ui.screens.AgendaViewModel
import org.goodcompany.app.feature.agenda.ui.screens.calendar.CalendarSuggestionsViewModel
import org.goodcompany.app.feature.agenda.ui.screens.today.TodayViewModel
import org.goodcompany.app.feature.friends.ui.screens.choose.ChooseContactsViewModel
import org.goodcompany.app.feature.friends.ui.screens.detail.FriendDetailViewModel
import org.goodcompany.app.feature.friends.ui.screens.family.SuggestedFamilyViewModel
import org.goodcompany.app.feature.friends.ui.screens.list.FriendsViewModel
import org.goodcompany.app.feature.friends.ui.screens.share.ShareToFriendViewModel
import org.goodcompany.app.feature.friends.ui.screens.story.CatchUpStoryViewModel
import org.goodcompany.app.feature.friends.ui.screens.timeline.FriendTimelineViewModel
import org.goodcompany.app.feature.hangouts.ui.screens.HangoutsViewModel
import org.goodcompany.app.feature.hangouts.ui.screens.circle.CircleEditorViewModel
import org.goodcompany.app.feature.hangouts.ui.screens.free.WhosFreeViewModel
import org.goodcompany.app.feature.hangouts.ui.screens.met.LogPrefill
import org.goodcompany.app.feature.hangouts.ui.screens.met.LoggedMeetupViewModel
import org.goodcompany.app.feature.hangouts.ui.screens.met.WeMetViewModel
import org.goodcompany.app.feature.hangouts.ui.screens.page.CirclePageViewModel
import org.goodcompany.app.feature.ideas.ui.screens.IdeasViewModel
import org.goodcompany.app.feature.ideas.ui.screens.gifts.GiftListViewModel
import org.goodcompany.app.feature.ideas.ui.screens.plans.PlansViewModel
import org.goodcompany.app.feature.review.ui.screens.YearReviewViewModel
import org.goodcompany.app.feature.review.ui.screens.network.NetworkMapViewModel
import org.goodcompany.app.feature.review.ui.screens.network.PeopleDashboardViewModel
import org.goodcompany.app.feature.settings.ui.screens.SettingsViewModel
import org.goodcompany.app.feature.settings.ui.screens.activities.ActivitiesViewModel
import org.goodcompany.app.feature.settings.ui.screens.calendar.CalendarSettingsViewModel
import org.goodcompany.app.feature.settings.ui.screens.how.HowOptionsViewModel
import org.goodcompany.app.feature.settings.ui.screens.notifications.NotificationSettingsViewModel
import org.goodcompany.app.feature.settings.ui.screens.places.PlacesViewModel
import org.goodcompany.app.feature.settings.ui.screens.restore.RestoreBackupViewModel
import org.goodcompany.app.feature.settings.ui.screens.tags.TagsViewModel
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
        single<CalendarReader> { FakeCalendarReader() }
        single<DriveTokenSource> { FakeDriveTokenSource() }
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
        assertNotNull(koin.get<SuggestedFamilyViewModel>())
        assertNotNull(koin.get<CircleEditorViewModel> { parametersOf(null, null) })
        assertNotNull(koin.get<CircleEditorViewModel> { parametersOf("circle-1", null) })
        assertNotNull(koin.get<CircleEditorViewModel> { parametersOf(null, FriendId("ana")) })
        assertNotNull(koin.get<GiftListViewModel> { parametersOf("list-1") })
        assertNotNull(koin.get<CirclePageViewModel> { parametersOf("circle-1") })
        assertNotNull(koin.get<HangoutsViewModel>())
        assertNotNull(koin.get<WhosFreeViewModel>())
        assertNotNull(koin.get<WeMetViewModel> { parametersOf(listOf(FriendId("x")), null, null, LogPrefill()) })
        assertNotNull(koin.get<WeMetViewModel> { parametersOf(emptyList<FriendId>(), "circle-1", PlanOccurrence("plan-1", null), LogPrefill()) })
        assertNotNull(
            koin.get<WeMetViewModel> {
                parametersOf(listOf(FriendId("x")), null, null, LogPrefill(type = "JOURNAL", activity = HangoutActivity.LUNCH))
            },
        )
        assertNotNull(koin.get<LoggedMeetupViewModel>())
        assertNotNull(koin.get<AgendaViewModel>())
        assertNotNull(koin.get<TodayViewModel>())
        assertNotNull(koin.get<IdeasViewModel>())
        assertNotNull(koin.get<PlansViewModel>())
        assertNotNull(koin.get<SettingsViewModel>())
        assertNotNull(koin.get<RestoreBackupViewModel>())
        assertNotNull(koin.get<RunAutoBackupUseCase>())
        assertNotNull(koin.get<PlacesViewModel>())
        assertNotNull(koin.get<CalendarSettingsViewModel>())
        assertNotNull(koin.get<NotificationSettingsViewModel>())
        assertNotNull(koin.get<CalendarSuggestionsViewModel>())
        assertNotNull(koin.get<TagsViewModel>())
        assertNotNull(koin.get<ActivitiesViewModel>())
        assertNotNull(koin.get<HowOptionsViewModel>())
        assertNotNull(koin.get<RunWeeklyIdeasUseCase>())
        assertNotNull(koin.get<org.goodcompany.app.feature.onboarding.ui.screens.OnboardingViewModel>())
        assertNotNull(koin.get<ApplyLaunchOptions>())
        assertNotNull(koin.get<CatchUpStoryViewModel> { parametersOf(FriendId("x")) })
        assertNotNull(koin.get<ShareToFriendViewModel>())
        assertNotNull(koin.get<ChooseContactsViewModel>())
        assertNotNull(koin.get<ObserveShortcutsUseCase>())
        assertSame(koin.get<QuickEntryInbox>(), koin.get<QuickEntryInbox>())
        assertNotNull(koin.get<YearReviewViewModel> { parametersOf(null) })
        assertNotNull(koin.get<YearReviewViewModel> { parametersOf(2025) })
        assertNotNull(koin.get<NetworkMapViewModel>())
        assertNotNull(koin.get<PeopleDashboardViewModel>())
        assertNotNull(koin.get<HandleNotificationActionUseCase>())
        assertNotNull(koin.get<KeepNotificationsScheduledUseCase>())
    }
}
