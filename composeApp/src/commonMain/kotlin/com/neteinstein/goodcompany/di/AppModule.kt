package com.neteinstein.goodcompany.di

import com.neteinstein.goodcompany.AppViewModel
import com.neteinstein.goodcompany.ApplyLaunchOptions
import com.neteinstein.goodcompany.core.data.ai.AnthropicAssistant
import com.neteinstein.goodcompany.core.data.ai.AnthropicClient
import com.neteinstein.goodcompany.core.data.ai.AnthropicIdeaGenerator
import com.neteinstein.goodcompany.core.data.cloud.GoogleDriveBackupStore
import com.neteinstein.goodcompany.core.data.db.AppDatabase
import com.neteinstein.goodcompany.core.data.db.DatabaseDriverFactory
import com.neteinstein.goodcompany.core.data.feeds.KtorFeedFetcher
import com.neteinstein.goodcompany.core.data.platform.createCalendarReader
import com.neteinstein.goodcompany.core.data.platform.createCommunicationLogSource
import com.neteinstein.goodcompany.core.data.platform.createContactsSource
import com.neteinstein.goodcompany.core.data.platform.createContactsWriter
import com.neteinstein.goodcompany.core.data.platform.createDatabaseDriverFactory
import com.neteinstein.goodcompany.core.data.platform.createHttpClient
import com.neteinstein.goodcompany.core.data.platform.createLocationProvider
import com.neteinstein.goodcompany.core.data.platform.createNotificationScheduler
import com.neteinstein.goodcompany.core.data.repository.LegacyJsonImport
import com.neteinstein.goodcompany.core.data.repository.SqlBackupRepository
import com.neteinstein.goodcompany.core.data.repository.SqlCalendarDecisionRepository
import com.neteinstein.goodcompany.core.data.repository.SqlFriendRepository
import com.neteinstein.goodcompany.core.data.repository.SqlGiftListRepository
import com.neteinstein.goodcompany.core.data.repository.SqlGroupRepository
import com.neteinstein.goodcompany.core.data.repository.SqlHangoutRepository
import com.neteinstein.goodcompany.core.data.repository.SqlIdeaRepository
import com.neteinstein.goodcompany.core.data.repository.SqlInteractionRepository
import com.neteinstein.goodcompany.core.data.repository.SqlPersonLinkRepository
import com.neteinstein.goodcompany.core.data.repository.SqlPlanRepository
import com.neteinstein.goodcompany.core.data.repository.SqlReminderRepository
import com.neteinstein.goodcompany.core.data.repository.SqlSettingsRepository
import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.data.update.GitHubReleaseUpdateSource
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.PlanOccurrence
import com.neteinstein.goodcompany.core.domain.repository.AiAssistant
import com.neteinstein.goodcompany.core.domain.repository.AppUpdateSource
import com.neteinstein.goodcompany.core.domain.repository.BackupRepository
import com.neteinstein.goodcompany.core.domain.repository.BuiltInCircleNames
import com.neteinstein.goodcompany.core.domain.repository.CalendarDecisionRepository
import com.neteinstein.goodcompany.core.domain.repository.CalendarReader
import com.neteinstein.goodcompany.core.domain.repository.CloudBackupStore
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import com.neteinstein.goodcompany.core.domain.repository.FeedFetcher
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GiftListRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaGenerator
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.KinshipVocabulary
import com.neteinstein.goodcompany.core.domain.repository.LanguageProvider
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import com.neteinstein.goodcompany.core.domain.repository.NotificationRenderer
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import com.neteinstein.goodcompany.core.domain.repository.PersonLinkRepository
import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.repository.StorageStatus
import com.neteinstein.goodcompany.core.domain.usecase.AddFeedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddGiftToListUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddKidUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddLifeUpdateUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddPeopleToGiftListUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddRelativeAsPersonUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AdvanceGiftStatusUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AnswerLastSeenUseCase
import com.neteinstein.goodcompany.core.domain.usecase.BackupToDriveUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CancelPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckForUpdateUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmKinshipUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CreateGiftListUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DecideCalendarSuggestionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteAttachmentUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteGiftListUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteLinkUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeletePlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteTagUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteTimelineEntryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DismissIdeaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DismissKinshipUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DraftBirthdayMessageUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DuplicateGiftListUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ExportBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FetchDriveBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GenerateTalkingPointsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GetCurrentLocationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GiftListUseCases
import com.neteinstein.goodcompany.core.domain.usecase.GroupsWithFamilyCircle
import com.neteinstein.goodcompany.core.domain.usecase.HandleNotificationActionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HasSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HideLastSeenCardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportChatUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportContactsFileUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportPickedContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.IsStorageTemporaryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KeepNotificationsScheduledUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KeepTagSpellingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ListContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogCallUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogInteractionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoggedMeetupInbox
import com.neteinstein.goodcompany.core.domain.usecase.MarkIdeaProposedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.MergePlacesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.NextCircleColorUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveActivitiesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAgendaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAttachmentsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveCalendarEventsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveCalendarSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveCalendarSuggestionsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveCatchUpStoryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendDetailUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGiftListUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGiftListsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveHangoutHistoryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveHomeWidgetUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveKinshipProposalsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveLastSeenCardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMeetupStatusUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveNetworkMapUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveOccasionMatchesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePeopleDashboardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePersonLinksUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlaceBookUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlacesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlansUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveRemindersUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveShareTargetsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveShortcutsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveStoryBubblesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveTagDuplicatesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveTagsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveTimelineUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveYearInReviewUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveYearReviewCardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ParseKinshipQueryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PersonLinkUseCases
import com.neteinstein.goodcompany.core.domain.usecase.QuickEntryInbox
import com.neteinstein.goodcompany.core.domain.usecase.RecordAppOpenedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RefreshFeedsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveFriendPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveFromGiftListUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveGiftFromListUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RenameFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RenamePlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RenameTagUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreAttachmentUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreGiftListUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreLinkUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestorePlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestorePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreTagUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreTimelineEntryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RunWeeklyIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SampleTextsProvider
import com.neteinstein.goodcompany.core.domain.usecase.SaveChosenContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveLinkUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SavePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveSharedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetActivitiesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCalendarChosenUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCalendarSuggestionsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCatchUpPausedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCircleMemberUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetClosenessUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetFriendPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetGiftBudgetUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetHomeCircleFilterUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetPronounsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetRealFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetRelationToMeUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetReminderDoneUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetShowInMyListUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ShareInbox
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeCatchUpUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SuggestGiftsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SuggestUpdateFromSharedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UndoCalendarDecisionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UndoLogUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UndoSaveShareUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateGiftListUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import com.neteinstein.goodcompany.core.ui.l10n.CircleNamesFromResources
import com.neteinstein.goodcompany.core.ui.l10n.ComposeLanguageProvider
import com.neteinstein.goodcompany.core.ui.l10n.KinshipWordsFromResources
import com.neteinstein.goodcompany.core.ui.l10n.ResourceNotificationRenderer
import com.neteinstein.goodcompany.core.ui.l10n.SampleTextsFromResources
import com.neteinstein.goodcompany.core.ui.platform.createPhotoShrinker
import com.neteinstein.goodcompany.feature.agenda.ui.screens.AgendaViewModel
import com.neteinstein.goodcompany.feature.agenda.ui.screens.calendar.CalendarSuggestionsViewModel
import com.neteinstein.goodcompany.feature.agenda.ui.screens.today.TodayViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.choose.ChooseContactsViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.FriendDetailViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.family.SuggestedFamilyViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendsViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.log.LogInteractionViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.log.LogPrefill
import com.neteinstein.goodcompany.feature.friends.ui.screens.share.ShareToFriendViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.story.CatchUpStoryViewModel
import com.neteinstein.goodcompany.feature.friends.ui.screens.timeline.FriendTimelineViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.HangoutsViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.circle.CircleEditorViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.free.WhosFreeViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.gifts.GiftListViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.gifts.GiftListsViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.met.LoggedMeetupViewModel
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.met.WeMetViewModel
import com.neteinstein.goodcompany.feature.ideas.ui.screens.IdeasViewModel
import com.neteinstein.goodcompany.feature.ideas.ui.screens.plans.PlansViewModel
import com.neteinstein.goodcompany.feature.onboarding.ui.screens.OnboardingViewModel
import com.neteinstein.goodcompany.feature.review.ui.screens.YearReviewViewModel
import com.neteinstein.goodcompany.feature.review.ui.screens.network.NetworkMapViewModel
import com.neteinstein.goodcompany.feature.review.ui.screens.network.PeopleDashboardViewModel
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsViewModel
import com.neteinstein.goodcompany.feature.settings.ui.screens.activities.ActivitiesViewModel
import com.neteinstein.goodcompany.feature.settings.ui.screens.calendar.CalendarSettingsViewModel
import com.neteinstein.goodcompany.feature.settings.ui.screens.places.PlacesViewModel
import com.neteinstein.goodcompany.feature.settings.ui.screens.tags.TagsViewModel
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
    // SQLite (SQLDelight), opened lazily on first use. The first open moves the old JSON documents in (issue #14).
    single<DatabaseDriverFactory> { createDatabaseDriverFactory() }
    single { LegacyJsonImport(get()) }
    single {
        val legacy: LegacyJsonImport = get()
        AppDatabase(get(), get(), prepare = legacy::run)
    } bind StorageStatus::class
    single { createHttpClient() }

    // --- Platform data sources ---
    single<ContactsSource> { createContactsSource() }
    single<ContactsWriter> { createContactsWriter() }
    single<CommunicationLogSource> { createCommunicationLogSource() }
    single<PhotoShrinker> { createPhotoShrinker() }
    single<LocationProvider> { createLocationProvider() }
    single<NotificationScheduler> { createNotificationScheduler() }
    single<CalendarReader> { createCalendarReader() }
    single { AnthropicClient(get()) }
    single<IdeaGenerator> { AnthropicIdeaGenerator(get()) }
    single<AiAssistant> { AnthropicAssistant(get()) }
    single<CloudBackupStore> { GoogleDriveBackupStore(get()) }
    single<FeedFetcher> { KtorFeedFetcher(get()) }
    single<AppUpdateSource> { GitHubReleaseUpdateSource(get()) }
    single { ShareInbox() }
    single { QuickEntryInbox() }
    single { LoggedMeetupInbox() }

    // --- Localisation (strings live in compose resources; the domain only sees these ports) ---
    single<NotificationRenderer> { ResourceNotificationRenderer }
    single<LanguageProvider> { ComposeLanguageProvider }
    single<SampleTextsProvider> { SampleTextsFromResources }
    single<BuiltInCircleNames> { CircleNamesFromResources }
    single<KinshipVocabulary> { KinshipWordsFromResources }

    // --- Repositories ---
    singleOf(::SqlFriendRepository) bind FriendRepository::class
    singleOf(::SqlHangoutRepository) bind HangoutRepository::class
    singleOf(::SqlReminderRepository) bind ReminderRepository::class
    singleOf(::SqlInteractionRepository) bind InteractionRepository::class
    singleOf(::SqlIdeaRepository) bind IdeaRepository::class
    singleOf(::SqlSettingsRepository) bind SettingsRepository::class
    // Circles as the app sees them: the stored ones plus the built-in Family circle (F6).
    single<GroupRepository> { GroupsWithFamilyCircle(SqlGroupRepository(get()), get(), get()) }
    singleOf(::SqlPersonLinkRepository) bind PersonLinkRepository::class
    singleOf(::SqlPlanRepository) bind PlanRepository::class
    singleOf(::SqlBackupRepository) bind BackupRepository::class
    singleOf(::SqlCalendarDecisionRepository) bind CalendarDecisionRepository::class
    singleOf(::SqlGiftListRepository) bind GiftListRepository::class

    // --- Use cases ---
    factoryOf(::ObserveFriendsUseCase)
    factoryOf(::ObserveNetworkMapUseCase)
    factoryOf(::ObservePeopleDashboardUseCase)
    factoryOf(::ObserveFriendDetailUseCase)
    factoryOf(::UpdateFriendUseCase)
    factoryOf(::ObservePersonLinksUseCase)
    factoryOf(::SaveLinkUseCase)
    factoryOf(::DeleteLinkUseCase)
    factoryOf(::RestoreLinkUseCase)
    factoryOf(::SetRelationToMeUseCase)
    factoryOf(::SetPronounsUseCase)
    factoryOf(::AddRelativeAsPersonUseCase)
    factoryOf(::AddKidUseCase)
    factoryOf(::SetShowInMyListUseCase)
    factoryOf(::ObserveKinshipProposalsUseCase)
    factoryOf(::ConfirmKinshipUseCase)
    factoryOf(::DismissKinshipUseCase)
    factoryOf(::ParseKinshipQueryUseCase)
    factoryOf(::PersonLinkUseCases)
    factoryOf(::ObserveGiftListsUseCase)
    factoryOf(::ObserveGiftListUseCase)
    factoryOf(::CreateGiftListUseCase)
    factoryOf(::UpdateGiftListUseCase)
    factoryOf(::DeleteGiftListUseCase)
    factoryOf(::RestoreGiftListUseCase)
    factoryOf(::DuplicateGiftListUseCase)
    factoryOf(::AddPeopleToGiftListUseCase)
    factoryOf(::RemoveFromGiftListUseCase)
    factoryOf(::SetGiftBudgetUseCase)
    factoryOf(::AddGiftToListUseCase)
    factoryOf(::RemoveGiftFromListUseCase)
    factoryOf(::AdvanceGiftStatusUseCase)
    factoryOf(::GiftListUseCases)
    factoryOf(::ObserveTagsUseCase)
    factoryOf(::ObserveTagDuplicatesUseCase)
    factoryOf(::KeepTagSpellingsUseCase)
    factoryOf(::RenameTagUseCase)
    factoryOf(::DeleteTagUseCase)
    factoryOf(::RestoreTagUseCase)
    factoryOf(::AddFriendUseCase)
    factoryOf(::DeleteFriendUseCase)
    factoryOf(::SyncContactsUseCase)
    factoryOf(::ImportChatUseCase)
    factoryOf(::CheckInUseCase)
    factoryOf(::LogInteractionUseCase)
    factoryOf(::UndoLogUseCase)
    factoryOf(::ObserveActivitiesUseCase)
    factoryOf(::SetActivitiesUseCase)
    factoryOf(::ObserveTimelineUseCase)
    factoryOf(::DeleteTimelineEntryUseCase)
    factoryOf(::RestoreTimelineEntryUseCase)
    factoryOf(::DeleteHangoutUseCase)
    factoryOf(::RestoreHangoutUseCase)
    factoryOf(::ObservePlaceBookUseCase)
    factoryOf(::ObservePlacesUseCase)
    factoryOf(::RenamePlaceUseCase)
    factoryOf(::DeletePlaceUseCase)
    factoryOf(::RestorePlaceUseCase)
    factoryOf(::MergePlacesUseCase)
    factoryOf(::GetCurrentLocationUseCase)
    factoryOf(::ObserveHangoutHistoryUseCase)
    factoryOf(::ObserveMeetupStatusUseCase)
    factoryOf(::ObserveLastSeenCardUseCase)
    factoryOf(::AnswerLastSeenUseCase)
    factoryOf(::HideLastSeenCardUseCase)
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
    factoryOf(::IsStorageTemporaryUseCase)
    factoryOf(::CheckForUpdateUseCase)
    factoryOf(::LoadSampleDataUseCase)
    factoryOf(::RemoveSampleDataUseCase)
    factoryOf(::HasSampleDataUseCase)
    factoryOf(::SetRealFriendUseCase)
    factoryOf(::SetClosenessUseCase)
    factoryOf(::SnoozeCatchUpUseCase)
    factoryOf(::LogCallUseCase)
    factoryOf(::HandleNotificationActionUseCase)
    factoryOf(::ImportContactsFileUseCase)
    factoryOf(::SetFriendPhotoUseCase)
    factoryOf(::RemoveFriendPhotoUseCase)
    factoryOf(::RenameFriendUseCase)
    factoryOf(::DraftBirthdayMessageUseCase)
    factoryOf(::SuggestGiftsUseCase)
    factoryOf(::MarkIdeaProposedUseCase)
    factoryOf(::ObserveGroupsUseCase)
    factoryOf(::SaveGroupUseCase)
    factoryOf(::DeleteGroupUseCase)
    factoryOf(::RestoreGroupUseCase)
    factoryOf(::SetCircleMemberUseCase)
    factoryOf(::NextCircleColorUseCase)
    factoryOf(::SetHomeCircleFilterUseCase)
    factoryOf(::ObserveYearInReviewUseCase)
    factoryOf(::ObserveYearReviewCardUseCase)
    factoryOf(::BackupToDriveUseCase)
    factoryOf(::FetchDriveBackupUseCase)
    factoryOf(::ObservePlansUseCase)
    factoryOf(::SavePlanUseCase)
    factoryOf(::ConfirmPlanUseCase)
    factoryOf(::CancelPlanUseCase)
    factoryOf(::RestorePlanUseCase)
    factoryOf(::SetCatchUpPausedUseCase)
    factoryOf(::RecordAppOpenedUseCase)
    factoryOf(::ImportPickedContactsUseCase)
    factoryOf(::ListContactsUseCase)
    factoryOf(::SaveChosenContactsUseCase)
    factoryOf(::ObserveOccasionMatchesUseCase)
    factoryOf(::ExportBackupUseCase)
    factoryOf(::RestoreBackupUseCase)
    factoryOf(::ObserveHomeWidgetUseCase)
    factoryOf(::ObserveShortcutsUseCase)
    factoryOf(::ObserveCatchUpStoryUseCase)
    factoryOf(::ObserveStoryBubblesUseCase)
    factoryOf(::ObserveCalendarEventsUseCase)
    factoryOf(::ObserveCalendarSuggestionsUseCase)
    factoryOf(::DecideCalendarSuggestionUseCase)
    factoryOf(::UndoCalendarDecisionUseCase)
    factoryOf(::ObserveCalendarSettingsUseCase)
    factoryOf(::SetCalendarSuggestionsUseCase)
    factoryOf(::SetCalendarChosenUseCase)
    factoryOf(::AddFeedUseCase)
    factoryOf(::RefreshFeedsUseCase)
    factoryOf(::AddLifeUpdateUseCase)
    factoryOf(::SaveSharedUseCase)
    factoryOf(::UndoSaveShareUseCase)
    factoryOf(::ObserveShareTargetsUseCase)
    factoryOf(::ObserveAttachmentsUseCase)
    factoryOf(::DeleteAttachmentUseCase)
    factoryOf(::RestoreAttachmentUseCase)
    factoryOf(::SuggestUpdateFromSharedUseCase)
    singleOf(::AppStartup)
    factoryOf(::ApplyLaunchOptions)

    // --- ViewModels ---
    viewModelOf(::AppViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::FriendsViewModel)
    viewModelOf(::ChooseContactsViewModel)
    viewModel { (id: FriendId) ->
        FriendDetailViewModel(
            friendId = id,
            observeDetail = get(),
            observeSettings = get(),
            updateFriend = get(),
            deleteFriend = get(),
            setRealFriend = get(),
            setCloseness = get(),
            snooze = get(),
            logCall = get(),
            generateTalkingPoints = get(),
            dismissIdea = get(),
            draftBirthdayMessage = get(),
            suggestGifts = get(),
            addReminder = get(),
            setCatchUpPaused = get(),
            savePlan = get(),
            confirmPlan = get(),
            cancelPlan = get(),
            restorePlan = get(),
            addFeed = get(),
            setPhoto = get(),
            removePhoto = get(),
            renameFriend = get(),
            setHomeCircleFilter = get(),
            setCircleMember = get(),
            restoreGroup = get(),
            observeAttachments = get(),
            observeTags = get(),
            deleteAttachment = get(),
            restoreAttachment = get(),
            shareInbox = get(),
            links = get(),
            clock = get(),
            ids = get(),
        )
    }
    viewModel { (id: FriendId) -> FriendTimelineViewModel(id, get(), get(), get(), get(), get(), get()) }
    viewModelOf(::SuggestedFamilyViewModel)
    viewModel { (ids: List<FriendId>, type: String?, circleId: String?, plan: PlanOccurrence?, prefill: LogPrefill) ->
        LogInteractionViewModel(ids, type, circleId, plan, prefill, get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModel { (id: String?, withFriend: FriendId?) -> CircleEditorViewModel(id, withFriend, get(), get(), get(), get(), get(), get()) }
    viewModelOf(::GiftListsViewModel)
    viewModel { (id: String) -> GiftListViewModel(id, get(), get(), get(), get(), get(), get()) }
    viewModelOf(::HangoutsViewModel)
    viewModelOf(::WhosFreeViewModel)
    viewModel { (ids: List<FriendId>, circleId: String?, plan: PlanOccurrence?) ->
        WeMetViewModel(ids, circleId, plan, get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModelOf(::LoggedMeetupViewModel)
    viewModelOf(::AgendaViewModel)
    viewModelOf(::TodayViewModel)
    viewModelOf(::CalendarSuggestionsViewModel)
    viewModelOf(::CalendarSettingsViewModel)
    viewModelOf(::IdeasViewModel)
    viewModelOf(::PlansViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::PlacesViewModel)
    viewModelOf(::TagsViewModel)
    viewModelOf(::ActivitiesViewModel)
    viewModel { (year: Int?) -> YearReviewViewModel(year, get(), get()) }
    viewModel { NetworkMapViewModel(get()) }
    viewModel { PeopleDashboardViewModel(get()) }
    viewModel { (id: FriendId) -> CatchUpStoryViewModel(id, get(), get()) }
    viewModelOf(::ShareToFriendViewModel)
}
