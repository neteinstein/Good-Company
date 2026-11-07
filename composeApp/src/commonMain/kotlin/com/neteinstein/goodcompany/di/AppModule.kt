package com.neteinstein.goodcompany.di

import com.neteinstein.goodcompany.AppViewModel
import com.neteinstein.goodcompany.ApplyLaunchOptions
import com.neteinstein.goodcompany.core.data.ai.AnthropicIdeaGenerator
import com.neteinstein.goodcompany.core.data.platform.createCommunicationLogSource
import com.neteinstein.goodcompany.core.data.platform.createContactsSource
import com.neteinstein.goodcompany.core.data.platform.createHttpClient
import com.neteinstein.goodcompany.core.data.platform.createLocationProvider
import com.neteinstein.goodcompany.core.data.platform.createNotificationScheduler
import com.neteinstein.goodcompany.core.data.repository.FriendRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.HangoutRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.IdeaRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.InteractionRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.ReminderRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.SettingsRepositoryImpl
import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaGenerator
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.usecase.AddFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DismissIdeaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GenerateTalkingPointsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GetCurrentLocationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HasSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportChatUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KeepNotificationsScheduledUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAgendaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendDetailUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveHangoutHistoryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMeetupStatusUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveRemindersUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RunWeeklyIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetReminderDoneUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import com.neteinstein.goodcompany.feature.agenda.ui.screens.AgendaViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.FriendDetailViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendsViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.HangoutsViewModel
import com.neteinstein.goodcompany.feature.ideas.ui.screens.IdeasViewModel
import com.neteinstein.goodcompany.feature.onboarding.ui.screens.OnboardingViewModel
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsViewModel
import com.neteinstein.goodcompany.startup.AppStartup
import com.russhwolf.settings.Settings
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * The one and only Koin module. Every dependency on every platform is registered here.
 * Platform differences are confined to the `create*()` factories in :core:data (expect/actual);
 * they're *called* here, never registered per platform.
 */
val appModule = module {
    // --- Infrastructure ---
    single<AppClock> { AppClock.System }
    single<IdGenerator> { IdGenerator.Random }
    single<Settings> { Settings() }
    single { createHttpClient() }

    // --- Platform data sources ---
    single<ContactsSource> { createContactsSource() }
    single<CommunicationLogSource> { createCommunicationLogSource() }
    single<LocationProvider> { createLocationProvider() }
    single<NotificationScheduler> { createNotificationScheduler() }
    single<IdeaGenerator> { AnthropicIdeaGenerator(get()) }

    // --- Repositories ---
    singleOf(::FriendRepositoryImpl) bind FriendRepository::class
    singleOf(::HangoutRepositoryImpl) bind HangoutRepository::class
    singleOf(::ReminderRepositoryImpl) bind ReminderRepository::class
    singleOf(::InteractionRepositoryImpl) bind InteractionRepository::class
    singleOf(::IdeaRepositoryImpl) bind IdeaRepository::class
    singleOf(::SettingsRepositoryImpl) bind SettingsRepository::class

    // --- Use cases ---
    factoryOf(::ObserveFriendsUseCase)
    factoryOf(::ObserveFriendDetailUseCase)
    factoryOf(::UpdateFriendUseCase)
    factoryOf(::AddFriendUseCase)
    factoryOf(::DeleteFriendUseCase)
    factoryOf(::SyncContactsUseCase)
    factoryOf(::ImportChatUseCase)
    factoryOf(::CheckInUseCase)
    factoryOf(::DeleteHangoutUseCase)
    factoryOf(::RestoreHangoutUseCase)
    factoryOf(::GetCurrentLocationUseCase)
    factoryOf(::ObserveHangoutHistoryUseCase)
    factoryOf(::ObserveMeetupStatusUseCase)
    factoryOf(::ObserveRemindersUseCase)
    factoryOf(::AddReminderUseCase)
    factoryOf(::SetReminderDoneUseCase)
    factoryOf(::DeleteReminderUseCase)
    factoryOf(::RestoreReminderUseCase)
    factoryOf(::ObserveAgendaUseCase)
    factoryOf(::KeepNotificationsScheduledUseCase)
    factoryOf(::ObserveIdeasUseCase)
    factoryOf(::DismissIdeaUseCase)
    factoryOf(::GenerateTalkingPointsUseCase)
    factoryOf(::RunWeeklyIdeasUseCase)
    factoryOf(::ObserveSettingsUseCase)
    factoryOf(::UpdateSettingsUseCase)
    factoryOf(::LoadSampleDataUseCase)
    factoryOf(::RemoveSampleDataUseCase)
    factoryOf(::HasSampleDataUseCase)
    singleOf(::AppStartup)
    factoryOf(::ApplyLaunchOptions)

    // --- ViewModels ---
    viewModelOf(::AppViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::FriendsViewModel)
    viewModel { (id: FriendId) ->
        FriendDetailViewModel(id, get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModelOf(::HangoutsViewModel)
    viewModelOf(::AgendaViewModel)
    viewModelOf(::IdeasViewModel)
    viewModelOf(::SettingsViewModel)
}
