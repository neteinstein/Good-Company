package org.goodcompany.app.di

import com.russhwolf.settings.Settings
import org.goodcompany.app.AppViewModel
import org.goodcompany.app.ApplyLaunchOptions
import org.goodcompany.app.core.data.ai.AnthropicAssistant
import org.goodcompany.app.core.data.ai.AnthropicClient
import org.goodcompany.app.core.data.ai.AnthropicIdeaGenerator
import org.goodcompany.app.core.data.cloud.GoogleDriveBackupStore
import org.goodcompany.app.core.data.crypto.CryptographyBackupCrypto
import org.goodcompany.app.core.data.db.AppDatabase
import org.goodcompany.app.core.data.db.DatabaseDriverFactory
import org.goodcompany.app.core.data.feeds.KtorFeedFetcher
import org.goodcompany.app.core.data.platform.createCalendarReader
import org.goodcompany.app.core.data.platform.createCommunicationLogSource
import org.goodcompany.app.core.data.platform.createContactsSource
import org.goodcompany.app.core.data.platform.createContactsWriter
import org.goodcompany.app.core.data.platform.createDatabaseDriverFactory
import org.goodcompany.app.core.data.platform.createDriveTokenSource
import org.goodcompany.app.core.data.platform.createHttpClient
import org.goodcompany.app.core.data.platform.createLocationProvider
import org.goodcompany.app.core.data.platform.createNotificationScheduler
import org.goodcompany.app.core.data.repository.KeyValueBackupKeyRepository
import org.goodcompany.app.core.data.repository.LegacyJsonImport
import org.goodcompany.app.core.data.repository.SqlBackupRepository
import org.goodcompany.app.core.data.repository.SqlCalendarDecisionRepository
import org.goodcompany.app.core.data.repository.SqlCircleCoverRepository
import org.goodcompany.app.core.data.repository.SqlFriendRepository
import org.goodcompany.app.core.data.repository.SqlGiftListRepository
import org.goodcompany.app.core.data.repository.SqlGroupRepository
import org.goodcompany.app.core.data.repository.SqlHangoutRepository
import org.goodcompany.app.core.data.repository.SqlIdeaRepository
import org.goodcompany.app.core.data.repository.SqlInteractionRepository
import org.goodcompany.app.core.data.repository.SqlMyPhotoRepository
import org.goodcompany.app.core.data.repository.SqlPersonBackgroundRepository
import org.goodcompany.app.core.data.repository.SqlPersonLinkRepository
import org.goodcompany.app.core.data.repository.SqlPlanRepository
import org.goodcompany.app.core.data.repository.SqlReminderRepository
import org.goodcompany.app.core.data.repository.SqlSettingsRepository
import org.goodcompany.app.core.data.source.CommunicationLogSource
import org.goodcompany.app.core.data.source.ContactsSource
import org.goodcompany.app.core.data.update.GitHubReleaseUpdateSource
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.PlanOccurrence
import org.goodcompany.app.core.domain.repository.AiAssistant
import org.goodcompany.app.core.domain.repository.AppLanguagePlatform
import org.goodcompany.app.core.domain.repository.AppUpdateSource
import org.goodcompany.app.core.domain.repository.BackupCrypto
import org.goodcompany.app.core.domain.repository.BackupKeyRepository
import org.goodcompany.app.core.domain.repository.BackupRepository
import org.goodcompany.app.core.domain.repository.BuiltInCircleNames
import org.goodcompany.app.core.domain.repository.CalendarDecisionRepository
import org.goodcompany.app.core.domain.repository.CalendarReader
import org.goodcompany.app.core.domain.repository.CircleCoverRepository
import org.goodcompany.app.core.domain.repository.CloudBackupStore
import org.goodcompany.app.core.domain.repository.ContactsWriter
import org.goodcompany.app.core.domain.repository.CoverShrinker
import org.goodcompany.app.core.domain.repository.DriveTokenSource
import org.goodcompany.app.core.domain.repository.FeedFetcher
import org.goodcompany.app.core.domain.repository.FriendRepository
import org.goodcompany.app.core.domain.repository.GiftListRepository
import org.goodcompany.app.core.domain.repository.GroupRepository
import org.goodcompany.app.core.domain.repository.HangoutRepository
import org.goodcompany.app.core.domain.repository.IdeaGenerator
import org.goodcompany.app.core.domain.repository.IdeaRepository
import org.goodcompany.app.core.domain.repository.InteractionRepository
import org.goodcompany.app.core.domain.repository.KinshipVocabulary
import org.goodcompany.app.core.domain.repository.LanguageProvider
import org.goodcompany.app.core.domain.repository.LocationProvider
import org.goodcompany.app.core.domain.repository.MyPhotoRepository
import org.goodcompany.app.core.domain.repository.NotificationRenderer
import org.goodcompany.app.core.domain.repository.NotificationScheduler
import org.goodcompany.app.core.domain.repository.PersonBackgroundRepository
import org.goodcompany.app.core.domain.repository.PersonLinkRepository
import org.goodcompany.app.core.domain.repository.PhotoShrinker
import org.goodcompany.app.core.domain.repository.PlanRepository
import org.goodcompany.app.core.domain.repository.ReminderRepository
import org.goodcompany.app.core.domain.repository.SampleCoverSource
import org.goodcompany.app.core.domain.repository.SettingsRepository
import org.goodcompany.app.core.domain.repository.StorageStatus
import org.goodcompany.app.core.domain.usecase.AddFeedUseCase
import org.goodcompany.app.core.domain.usecase.AddFriendUseCase
import org.goodcompany.app.core.domain.usecase.AddGiftToListUseCase
import org.goodcompany.app.core.domain.usecase.AddKidUseCase
import org.goodcompany.app.core.domain.usecase.AddLifeUpdateUseCase
import org.goodcompany.app.core.domain.usecase.AddPeopleToGiftListUseCase
import org.goodcompany.app.core.domain.usecase.AddRelativeAsPersonUseCase
import org.goodcompany.app.core.domain.usecase.AddReminderUseCase
import org.goodcompany.app.core.domain.usecase.AdoptBackupKeyUseCase
import org.goodcompany.app.core.domain.usecase.AdvanceGiftStatusUseCase
import org.goodcompany.app.core.domain.usecase.AnswerCalendarQuestionUseCase
import org.goodcompany.app.core.domain.usecase.AnswerLastMetUseCase
import org.goodcompany.app.core.domain.usecase.AnswerLastSeenUseCase
import org.goodcompany.app.core.domain.usecase.BackupToDriveUseCase
import org.goodcompany.app.core.domain.usecase.CancelPlanUseCase
import org.goodcompany.app.core.domain.usecase.ChangeBackupPasswordUseCase
import org.goodcompany.app.core.domain.usecase.CheckForUpdateUseCase
import org.goodcompany.app.core.domain.usecase.CheckInUseCase
import org.goodcompany.app.core.domain.usecase.CircleCoverUseCases
import org.goodcompany.app.core.domain.usecase.ClosePlanUseCase
import org.goodcompany.app.core.domain.usecase.CompleteReminderUseCase
import org.goodcompany.app.core.domain.usecase.ConfirmKinshipUseCase
import org.goodcompany.app.core.domain.usecase.ConfirmPlanUseCase
import org.goodcompany.app.core.domain.usecase.CreateGiftListUseCase
import org.goodcompany.app.core.domain.usecase.CustomActivitiesUseCase
import org.goodcompany.app.core.domain.usecase.CustomLogKindsUseCase
import org.goodcompany.app.core.domain.usecase.DecideCalendarSuggestionUseCase
import org.goodcompany.app.core.domain.usecase.DeleteAttachmentUseCase
import org.goodcompany.app.core.domain.usecase.DeleteFriendUseCase
import org.goodcompany.app.core.domain.usecase.DeleteGiftListUseCase
import org.goodcompany.app.core.domain.usecase.DeleteGroupUseCase
import org.goodcompany.app.core.domain.usecase.DeleteHangoutUseCase
import org.goodcompany.app.core.domain.usecase.DeleteLinkUseCase
import org.goodcompany.app.core.domain.usecase.DeletePlaceUseCase
import org.goodcompany.app.core.domain.usecase.DeletePlanUseCase
import org.goodcompany.app.core.domain.usecase.DeleteReminderUseCase
import org.goodcompany.app.core.domain.usecase.DeleteTagUseCase
import org.goodcompany.app.core.domain.usecase.DeleteTimelineEntryUseCase
import org.goodcompany.app.core.domain.usecase.DismissIdeaUseCase
import org.goodcompany.app.core.domain.usecase.DismissKinshipUseCase
import org.goodcompany.app.core.domain.usecase.DismissNotificationPrimerUseCase
import org.goodcompany.app.core.domain.usecase.DismissPeopleLegendUseCase
import org.goodcompany.app.core.domain.usecase.DraftBirthdayMessageUseCase
import org.goodcompany.app.core.domain.usecase.DuplicateGiftListUseCase
import org.goodcompany.app.core.domain.usecase.EditReminderUseCase
import org.goodcompany.app.core.domain.usecase.EditTimelineEntryUseCase
import org.goodcompany.app.core.domain.usecase.EncryptBackupUseCase
import org.goodcompany.app.core.domain.usecase.ExportBackupUseCase
import org.goodcompany.app.core.domain.usecase.FetchDriveBackupUseCase
import org.goodcompany.app.core.domain.usecase.ForgetDriveTokenUseCase
import org.goodcompany.app.core.domain.usecase.GenerateTalkingPointsUseCase
import org.goodcompany.app.core.domain.usecase.GetCurrentLocationUseCase
import org.goodcompany.app.core.domain.usecase.GiftListUseCases
import org.goodcompany.app.core.domain.usecase.GroupsWithFamilyCircle
import org.goodcompany.app.core.domain.usecase.HandleNotificationActionUseCase
import org.goodcompany.app.core.domain.usecase.HasSampleDataUseCase
import org.goodcompany.app.core.domain.usecase.HideLastSeenCardUseCase
import org.goodcompany.app.core.domain.usecase.ImportChatUseCase
import org.goodcompany.app.core.domain.usecase.ImportContactsFileUseCase
import org.goodcompany.app.core.domain.usecase.ImportPickedContactsUseCase
import org.goodcompany.app.core.domain.usecase.IsStorageTemporaryUseCase
import org.goodcompany.app.core.domain.usecase.KeepLanguageInStepUseCase
import org.goodcompany.app.core.domain.usecase.KeepNotificationsScheduledUseCase
import org.goodcompany.app.core.domain.usecase.KeepOldDriveBackupUseCase
import org.goodcompany.app.core.domain.usecase.KeepTagSpellingsUseCase
import org.goodcompany.app.core.domain.usecase.ListContactsUseCase
import org.goodcompany.app.core.domain.usecase.LoadSampleDataUseCase
import org.goodcompany.app.core.domain.usecase.LogCallUseCase
import org.goodcompany.app.core.domain.usecase.LogInteractionUseCase
import org.goodcompany.app.core.domain.usecase.LoggedMeetupInbox
import org.goodcompany.app.core.domain.usecase.MarkIdeaProposedUseCase
import org.goodcompany.app.core.domain.usecase.MarkMediaExplainedUseCase
import org.goodcompany.app.core.domain.usecase.MergePlacesUseCase
import org.goodcompany.app.core.domain.usecase.NextCircleColorUseCase
import org.goodcompany.app.core.domain.usecase.ObserveActivitiesUseCase
import org.goodcompany.app.core.domain.usecase.ObserveAgendaUseCase
import org.goodcompany.app.core.domain.usecase.ObserveAttachmentsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveBackupProtectionUseCase
import org.goodcompany.app.core.domain.usecase.ObserveCalendarEventsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveCalendarQuestionsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveCalendarSettingsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveCalendarSuggestionsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveCatchUpPolicyUseCase
import org.goodcompany.app.core.domain.usecase.ObserveCatchUpStoryUseCase
import org.goodcompany.app.core.domain.usecase.ObserveCirclePageUseCase
import org.goodcompany.app.core.domain.usecase.ObserveFriendDetailUseCase
import org.goodcompany.app.core.domain.usecase.ObserveFriendsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveGiftListUseCase
import org.goodcompany.app.core.domain.usecase.ObserveGiftListsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveGroupsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveHomeWidgetUseCase
import org.goodcompany.app.core.domain.usecase.ObserveIdeasUseCase
import org.goodcompany.app.core.domain.usecase.ObserveKinshipProposalsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveLastSeenCardUseCase
import org.goodcompany.app.core.domain.usecase.ObserveLogKindsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveMeetupStatusUseCase
import org.goodcompany.app.core.domain.usecase.ObserveMyPhotoUseCase
import org.goodcompany.app.core.domain.usecase.ObserveNetworkMapUseCase
import org.goodcompany.app.core.domain.usecase.ObserveOccasionMatchesUseCase
import org.goodcompany.app.core.domain.usecase.ObservePeopleDashboardUseCase
import org.goodcompany.app.core.domain.usecase.ObservePersonLinksUseCase
import org.goodcompany.app.core.domain.usecase.ObservePlaceBookUseCase
import org.goodcompany.app.core.domain.usecase.ObservePlacesUseCase
import org.goodcompany.app.core.domain.usecase.ObservePlansUseCase
import org.goodcompany.app.core.domain.usecase.ObserveRemindersUseCase
import org.goodcompany.app.core.domain.usecase.ObserveSettingsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveShareTargetsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveShortcutsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveStoryBubblesUseCase
import org.goodcompany.app.core.domain.usecase.ObserveTagDuplicatesUseCase
import org.goodcompany.app.core.domain.usecase.ObserveTagsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveTimelineUseCase
import org.goodcompany.app.core.domain.usecase.ObserveTypedPlacesUseCase
import org.goodcompany.app.core.domain.usecase.ObserveYearInReviewUseCase
import org.goodcompany.app.core.domain.usecase.ObserveYearReviewCardUseCase
import org.goodcompany.app.core.domain.usecase.OpenBackupUseCase
import org.goodcompany.app.core.domain.usecase.OpenInbox
import org.goodcompany.app.core.domain.usecase.ParseKinshipQueryUseCase
import org.goodcompany.app.core.domain.usecase.PersonBackgroundUseCases
import org.goodcompany.app.core.domain.usecase.PersonLinkUseCases
import org.goodcompany.app.core.domain.usecase.PinTypedPlaceUseCase
import org.goodcompany.app.core.domain.usecase.PrepareNoteMediaUseCase
import org.goodcompany.app.core.domain.usecase.QuickEntryInbox
import org.goodcompany.app.core.domain.usecase.RecordAppOpenedUseCase
import org.goodcompany.app.core.domain.usecase.RefreshFeedsUseCase
import org.goodcompany.app.core.domain.usecase.RemoveFriendPhotoUseCase
import org.goodcompany.app.core.domain.usecase.RemoveFromGiftListUseCase
import org.goodcompany.app.core.domain.usecase.RemoveGiftFromListUseCase
import org.goodcompany.app.core.domain.usecase.RemoveMyPhotoUseCase
import org.goodcompany.app.core.domain.usecase.RemoveSampleDataUseCase
import org.goodcompany.app.core.domain.usecase.RenameFriendUseCase
import org.goodcompany.app.core.domain.usecase.RenamePlaceUseCase
import org.goodcompany.app.core.domain.usecase.RenameTagUseCase
import org.goodcompany.app.core.domain.usecase.RenameTypedPlaceUseCase
import org.goodcompany.app.core.domain.usecase.ReopenReminderUseCase
import org.goodcompany.app.core.domain.usecase.RestoreAttachmentUseCase
import org.goodcompany.app.core.domain.usecase.RestoreBackupUseCase
import org.goodcompany.app.core.domain.usecase.RestoreGiftListUseCase
import org.goodcompany.app.core.domain.usecase.RestoreGroupUseCase
import org.goodcompany.app.core.domain.usecase.RestoreHangoutUseCase
import org.goodcompany.app.core.domain.usecase.RestoreLinkUseCase
import org.goodcompany.app.core.domain.usecase.RestorePlaceUseCase
import org.goodcompany.app.core.domain.usecase.RestorePlanUseCase
import org.goodcompany.app.core.domain.usecase.RestoreReminderUseCase
import org.goodcompany.app.core.domain.usecase.RestoreTagUseCase
import org.goodcompany.app.core.domain.usecase.RestoreTimelineEntryUseCase
import org.goodcompany.app.core.domain.usecase.RunAutoBackupUseCase
import org.goodcompany.app.core.domain.usecase.RunWeeklyIdeasUseCase
import org.goodcompany.app.core.domain.usecase.SampleTextsProvider
import org.goodcompany.app.core.domain.usecase.SaveChosenContactsUseCase
import org.goodcompany.app.core.domain.usecase.SaveGroupUseCase
import org.goodcompany.app.core.domain.usecase.SaveLinkUseCase
import org.goodcompany.app.core.domain.usecase.SaveNoteUseCase
import org.goodcompany.app.core.domain.usecase.SavePlanUseCase
import org.goodcompany.app.core.domain.usecase.SaveSharedUseCase
import org.goodcompany.app.core.domain.usecase.SendTestNotificationUseCase
import org.goodcompany.app.core.domain.usecase.SetActivitiesUseCase
import org.goodcompany.app.core.domain.usecase.SetAgendaViewUseCase
import org.goodcompany.app.core.domain.usecase.SetAutoBackupFrequencyUseCase
import org.goodcompany.app.core.domain.usecase.SetBackupPasswordUseCase
import org.goodcompany.app.core.domain.usecase.SetCalendarChosenUseCase
import org.goodcompany.app.core.domain.usecase.SetCalendarSuggestionsUseCase
import org.goodcompany.app.core.domain.usecase.SetCatchUpKindUseCase
import org.goodcompany.app.core.domain.usecase.SetCatchUpPausedUseCase
import org.goodcompany.app.core.domain.usecase.SetCircleMemberUseCase
import org.goodcompany.app.core.domain.usecase.SetClosenessUseCase
import org.goodcompany.app.core.domain.usecase.SetFriendPhotoUseCase
import org.goodcompany.app.core.domain.usecase.SetGiftBudgetUseCase
import org.goodcompany.app.core.domain.usecase.SetHomeCircleFilterUseCase
import org.goodcompany.app.core.domain.usecase.SetLogKindsUseCase
import org.goodcompany.app.core.domain.usecase.SetMyPhotoUseCase
import org.goodcompany.app.core.domain.usecase.SetPronounsUseCase
import org.goodcompany.app.core.domain.usecase.SetRelationToMeUseCase
import org.goodcompany.app.core.domain.usecase.SetShowInMyListUseCase
import org.goodcompany.app.core.domain.usecase.ShareInbox
import org.goodcompany.app.core.domain.usecase.SnoozeCatchUpUseCase
import org.goodcompany.app.core.domain.usecase.SnoozeReminderUseCase
import org.goodcompany.app.core.domain.usecase.StoryViews
import org.goodcompany.app.core.domain.usecase.SuggestGiftsUseCase
import org.goodcompany.app.core.domain.usecase.SuggestUpdateFromSharedUseCase
import org.goodcompany.app.core.domain.usecase.SyncContactsUseCase
import org.goodcompany.app.core.domain.usecase.TrashOldDriveBackupUseCase
import org.goodcompany.app.core.domain.usecase.TurnOffCircleRemindersUseCase
import org.goodcompany.app.core.domain.usecase.UndoCalendarDecisionUseCase
import org.goodcompany.app.core.domain.usecase.UndoLogUseCase
import org.goodcompany.app.core.domain.usecase.UndoSaveShareUseCase
import org.goodcompany.app.core.domain.usecase.UpdateFriendUseCase
import org.goodcompany.app.core.domain.usecase.UpdateGiftListUseCase
import org.goodcompany.app.core.domain.usecase.UpdateNotificationSettingsUseCase
import org.goodcompany.app.core.domain.usecase.UpdateSettingsUseCase
import org.goodcompany.app.core.domain.util.AppClock
import org.goodcompany.app.core.domain.util.IdGenerator
import org.goodcompany.app.core.ui.l10n.AppLocale
import org.goodcompany.app.core.ui.l10n.CircleNamesFromResources
import org.goodcompany.app.core.ui.l10n.ComposeLanguageProvider
import org.goodcompany.app.core.ui.l10n.KinshipWordsFromResources
import org.goodcompany.app.core.ui.l10n.ResourceNotificationRenderer
import org.goodcompany.app.core.ui.l10n.SampleCoverFromResources
import org.goodcompany.app.core.ui.l10n.SampleTextsFromResources
import org.goodcompany.app.core.ui.platform.createCoverShrinker
import org.goodcompany.app.core.ui.platform.createPhotoShrinker
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
import org.goodcompany.app.feature.onboarding.ui.screens.OnboardingViewModel
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
import org.goodcompany.app.startup.AppStartup
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
    single { LegacyJsonImport(get(), get()) }
    // Issue #103: when each catch-up story was last opened, in memory only.
    single { StoryViews() }
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
    single<CoverShrinker> { createCoverShrinker() }
    single<LocationProvider> { createLocationProvider() }
    single<NotificationScheduler> { createNotificationScheduler() }
    single<CalendarReader> { createCalendarReader() }
    single { AnthropicClient(get()) }
    single<IdeaGenerator> { AnthropicIdeaGenerator(get()) }
    single<AiAssistant> { AnthropicAssistant(get()) }
    single<CloudBackupStore> { GoogleDriveBackupStore(get()) }
    // Issue #166: Drive tokens with no screen, for the automatic backup (Play services on Android; none elsewhere).
    single<DriveTokenSource> { createDriveTokenSource() }
    // Encrypted backups (D4): the platform's own crypto; the key stays in key-value storage on this device.
    single<BackupCrypto> { CryptographyBackupCrypto() }
    single<BackupKeyRepository> { KeyValueBackupKeyRepository(get()) }
    single<FeedFetcher> { KtorFeedFetcher(get()) }
    single<AppUpdateSource> { GitHubReleaseUpdateSource(get()) }
    single { ShareInbox() }
    single { QuickEntryInbox() }
    single { OpenInbox() }
    single { LoggedMeetupInbox() }

    // --- Localisation (strings live in compose resources; the domain only sees these ports) ---
    single<NotificationRenderer> { ResourceNotificationRenderer }
    single<LanguageProvider> { ComposeLanguageProvider }
    single<AppLanguagePlatform> { AppLocale }
    single<SampleTextsProvider> { SampleTextsFromResources }
    single<SampleCoverSource> { SampleCoverFromResources }
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
    singleOf(::SqlCircleCoverRepository) bind CircleCoverRepository::class
    singleOf(::SqlPersonBackgroundRepository) bind PersonBackgroundRepository::class
    single<GroupRepository> { GroupsWithFamilyCircle(SqlGroupRepository(get()), friends = get(), covers = get(), names = get()) }
    singleOf(::SqlPersonLinkRepository) bind PersonLinkRepository::class
    singleOf(::SqlPlanRepository) bind PlanRepository::class
    singleOf(::SqlBackupRepository) bind BackupRepository::class
    singleOf(::SqlCalendarDecisionRepository) bind CalendarDecisionRepository::class
    singleOf(::SqlGiftListRepository) bind GiftListRepository::class
    singleOf(::SqlMyPhotoRepository) bind MyPhotoRepository::class

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
    factoryOf(::PrepareNoteMediaUseCase)
    factoryOf(::SaveNoteUseCase)
    factoryOf(::MarkMediaExplainedUseCase)
    factoryOf(::CheckInUseCase)
    factoryOf(::LogInteractionUseCase)
    factoryOf(::UndoLogUseCase)
    factoryOf(::ObserveActivitiesUseCase)
    factoryOf(::ObserveLogKindsUseCase)
    factoryOf(::SetLogKindsUseCase)
    factoryOf(::ObserveCatchUpPolicyUseCase)
    factoryOf(::SetCatchUpKindUseCase)
    factoryOf(::SetActivitiesUseCase)
    factoryOf(::ObserveTimelineUseCase)
    factoryOf(::DeleteTimelineEntryUseCase)
    factoryOf(::RestoreTimelineEntryUseCase)
    factoryOf(::EditTimelineEntryUseCase)
    factoryOf(::DeleteHangoutUseCase)
    factoryOf(::RestoreHangoutUseCase)
    factoryOf(::ObservePlaceBookUseCase)
    factoryOf(::ObservePlacesUseCase)
    factoryOf(::RenamePlaceUseCase)
    factoryOf(::ObserveTypedPlacesUseCase)
    factoryOf(::RenameTypedPlaceUseCase)
    factoryOf(::PinTypedPlaceUseCase)
    factoryOf(::DeletePlaceUseCase)
    factoryOf(::RestorePlaceUseCase)
    factoryOf(::MergePlacesUseCase)
    factoryOf(::GetCurrentLocationUseCase)
    factoryOf(::ObserveMeetupStatusUseCase)
    factoryOf(::ObserveLastSeenCardUseCase)
    factoryOf(::AnswerLastSeenUseCase)
    factoryOf(::AnswerLastMetUseCase)
    factoryOf(::TurnOffCircleRemindersUseCase)
    factoryOf(::HideLastSeenCardUseCase)
    factoryOf(::ObserveRemindersUseCase)
    factoryOf(::AddReminderUseCase)
    factoryOf(::EditReminderUseCase)
    factoryOf(::CompleteReminderUseCase)
    factoryOf(::ReopenReminderUseCase)
    factoryOf(::SnoozeReminderUseCase)
    factoryOf(::DeleteReminderUseCase)
    factoryOf(::RestoreReminderUseCase)
    factoryOf(::ObserveAgendaUseCase)
    factoryOf(::KeepNotificationsScheduledUseCase)
    factoryOf(::SendTestNotificationUseCase)
    factoryOf(::DismissNotificationPrimerUseCase)
    factoryOf(::UpdateNotificationSettingsUseCase)
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
    factoryOf(::SetClosenessUseCase)
    factoryOf(::SnoozeCatchUpUseCase)
    factoryOf(::LogCallUseCase)
    factoryOf(::HandleNotificationActionUseCase)
    factoryOf(::ImportContactsFileUseCase)
    factoryOf(::SetFriendPhotoUseCase)
    factoryOf(::RemoveFriendPhotoUseCase)
    factoryOf(::ObserveMyPhotoUseCase)
    factoryOf(::SetMyPhotoUseCase)
    factoryOf(::RemoveMyPhotoUseCase)
    factoryOf(::RenameFriendUseCase)
    factoryOf(::DraftBirthdayMessageUseCase)
    factoryOf(::SuggestGiftsUseCase)
    factoryOf(::MarkIdeaProposedUseCase)
    factoryOf(::ObserveGroupsUseCase)
    factoryOf(::SaveGroupUseCase)
    // Explicit: their cover store has a default (None) that must not be picked here (issue #88).
    factory { DeleteGroupUseCase(get(), get(), covers = get()) }
    factory { RestoreGroupUseCase(get(), covers = get()) }
    factoryOf(::CircleCoverUseCases)
    factoryOf(::PersonBackgroundUseCases)
    factoryOf(::ObserveCirclePageUseCase)
    factoryOf(::SetCircleMemberUseCase)
    factoryOf(::NextCircleColorUseCase)
    factoryOf(::SetHomeCircleFilterUseCase)
    factoryOf(::DismissPeopleLegendUseCase)
    factoryOf(::SetAgendaViewUseCase)
    factoryOf(::KeepLanguageInStepUseCase)
    factoryOf(::CustomActivitiesUseCase)
    factoryOf(::CustomLogKindsUseCase)
    factoryOf(::ObserveYearInReviewUseCase)
    factoryOf(::ObserveYearReviewCardUseCase)
    factoryOf(::BackupToDriveUseCase)
    factoryOf(::FetchDriveBackupUseCase)
    factoryOf(::TrashOldDriveBackupUseCase)
    factoryOf(::KeepOldDriveBackupUseCase)
    factoryOf(::RunAutoBackupUseCase)
    factoryOf(::SetAutoBackupFrequencyUseCase)
    factoryOf(::ForgetDriveTokenUseCase)
    factoryOf(::ObserveBackupProtectionUseCase)
    factoryOf(::SetBackupPasswordUseCase)
    factoryOf(::ChangeBackupPasswordUseCase)
    factoryOf(::EncryptBackupUseCase)
    factoryOf(::OpenBackupUseCase)
    factoryOf(::AdoptBackupKeyUseCase)
    factoryOf(::ObservePlansUseCase)
    factoryOf(::SavePlanUseCase)
    factoryOf(::ConfirmPlanUseCase)
    factoryOf(::CancelPlanUseCase)
    factoryOf(::DeletePlanUseCase)
    factoryOf(::ClosePlanUseCase)
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
    factoryOf(::ObserveCalendarQuestionsUseCase)
    factoryOf(::AnswerCalendarQuestionUseCase)
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
            setCloseness = get(),
            snooze = get(),
            generateTalkingPoints = get(),
            dismissIdea = get(),
            draftBirthdayMessage = get(),
            suggestGifts = get(),
            addReminder = get(),
            editReminder = get(),
            completeReminder = get(),
            snoozeReminder = get(),
            deleteReminder = get(),
            restoreReminder = get(),
            observeReminders = get(),
            setCatchUpPaused = get(),
            savePlan = get(),
            confirmPlan = get(),
            closePlan = get(),
            restorePlan = get(),
            addFeed = get(),
            setPhoto = get(),
            removePhoto = get(),
            backgrounds = get(),
            renameFriend = get(),
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
            deleteEntry = get(),
            restoreEntry = get(),
            storyViews = get(),
            importChat = get(),
            prepareNoteMedia = get(),
            saveNote = get(),
            markMediaExplained = get(),
        )
    }
    viewModel { (id: FriendId) -> FriendTimelineViewModel(id, get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModelOf(::SuggestedFamilyViewModel)
    viewModel { (id: String?, withFriend: FriendId?) -> CircleEditorViewModel(id, withFriend, get(), get(), get(), get(), get(), get(), get()) }
    viewModel { (id: String) ->
        CirclePageViewModel(id, get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    // A gift list opened in place on the Plans tab (issue #104).
    viewModel { (id: String) -> GiftListViewModel(id, get(), get(), get(), get(), get(), get()) }
    viewModelOf(::HangoutsViewModel)
    viewModelOf(::WhosFreeViewModel)
    viewModel { (ids: List<FriendId>, circleId: String?, plan: PlanOccurrence?, prefill: LogPrefill) ->
        WeMetViewModel(ids, circleId, plan, prefill, get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModelOf(::LoggedMeetupViewModel)
    viewModelOf(::AgendaViewModel)
    // More parameters than viewModelOf takes (22).
    viewModel {
        TodayViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
        )
    }
    viewModelOf(::CalendarSuggestionsViewModel)
    viewModelOf(::CalendarSettingsViewModel)
    viewModelOf(::NotificationSettingsViewModel)
    viewModelOf(::IdeasViewModel)
    viewModelOf(::PlansViewModel)
    // 24 dependencies: past viewModelOf's 22-argument limit.
    viewModel {
        SettingsViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }
    viewModelOf(::RestoreBackupViewModel)
    viewModelOf(::PlacesViewModel)
    viewModelOf(::TagsViewModel)
    viewModelOf(::ActivitiesViewModel)
    viewModelOf(::HowOptionsViewModel)
    viewModel { (year: Int?) -> YearReviewViewModel(year, get(), get()) }
    viewModel { NetworkMapViewModel(get()) }
    viewModel { PeopleDashboardViewModel(get()) }
    viewModel { (id: FriendId) -> CatchUpStoryViewModel(id, get(), get(), get()) }
    viewModelOf(::ShareToFriendViewModel)
}
