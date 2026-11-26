package org.goodcompany.app.core.testing

import org.goodcompany.app.core.domain.model.AppSettings
import org.goodcompany.app.core.domain.model.ContactPhoto
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.Hangout
import org.goodcompany.app.core.domain.model.PersonLink
import org.goodcompany.app.core.domain.repository.LanguageProvider
import org.goodcompany.app.core.domain.repository.PhotoShrinker
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
import org.goodcompany.app.core.domain.usecase.KeepNotificationsScheduledUseCase
import org.goodcompany.app.core.domain.usecase.KeepOldDriveBackupUseCase
import org.goodcompany.app.core.domain.usecase.KeepTagSpellingsUseCase
import org.goodcompany.app.core.domain.usecase.KidsAsPeople
import org.goodcompany.app.core.domain.usecase.ListContactsUseCase
import org.goodcompany.app.core.domain.usecase.LoadSampleDataUseCase
import org.goodcompany.app.core.domain.usecase.LogCallUseCase
import org.goodcompany.app.core.domain.usecase.LogInteractionUseCase
import org.goodcompany.app.core.domain.usecase.LoggedMeetupInbox
import org.goodcompany.app.core.domain.usecase.MarkIdeaProposedUseCase
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
import org.goodcompany.app.core.domain.usecase.ObserveYearInReviewUseCase
import org.goodcompany.app.core.domain.usecase.ObserveYearReviewCardUseCase
import org.goodcompany.app.core.domain.usecase.OpenBackupUseCase
import org.goodcompany.app.core.domain.usecase.ParseKinshipQueryUseCase
import org.goodcompany.app.core.domain.usecase.PersonBackgroundUseCases
import org.goodcompany.app.core.domain.usecase.PersonLinkUseCases
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
import org.goodcompany.app.core.domain.usecase.SaveChosenContactsUseCase
import org.goodcompany.app.core.domain.usecase.SaveGroupUseCase
import org.goodcompany.app.core.domain.usecase.SaveLinkUseCase
import org.goodcompany.app.core.domain.usecase.SavePlanUseCase
import org.goodcompany.app.core.domain.usecase.SaveSharedUseCase
import org.goodcompany.app.core.domain.usecase.SendTestNotificationUseCase
import org.goodcompany.app.core.domain.usecase.SetActivitiesUseCase
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

/**
 * Every repository as a fake plus every use case wired to them, so ViewModel tests can build their subject in one
 * line (`FooViewModel(g.observeFoo, g.doBar, g.clock)`) and assert against the same fakes.
 *
 * Friends given with old-style kids ([Friend.kids]) start as the database migration leaves them: each kid a person
 * linked to their parent ([KidsAsPeople.convert], issue #40).
 */
class TestGraph(
    friends: List<Friend> = emptyList(),
    settings: AppSettings = AppSettings(),
    hangouts: List<Hangout> = emptyList(),
    val clock: FixedClock = FixedClock(),
    val ids: SequentialIds = SequentialIds(),
    links: List<PersonLink> = emptyList(),
) {
    private val seeded = KidsAsPeople.convert(friends, links)
    val friendRepo = FakeFriendRepository(seeded.first)
    val hangoutRepo = FakeHangoutRepository(hangouts)
    val reminderRepo = FakeReminderRepository()
    val interactionRepo = FakeInteractionRepository()
    val ideaRepo = FakeIdeaRepository()
    val groupRepo = FakeGroupRepository()
    val planRepo = FakePlanRepository()
    val settingsRepo = FakeSettingsRepository(settings)
    val generator = FakeIdeaGenerator()
    val assistant = FakeAiAssistant()
    val location = FakeLocationProvider()
    val scheduler = FakeNotificationScheduler()
    val language = LanguageProvider { "en" }
    val linkRepo = FakePersonLinkRepository(seeded.second, friends = friendRepo)
    val calendarDecisionRepo = FakeCalendarDecisionRepository()
    val giftListRepo = FakeGiftListRepository()
    val myPhotoRepo = FakeMyPhotoRepository()
    val coverRepo = FakeCircleCoverRepository()
    val backgroundRepo = FakePersonBackgroundRepository(friends = friendRepo)
    val calendarReader = FakeCalendarReader()
    val observeCalendarEvents = ObserveCalendarEventsUseCase(settingsRepo, calendarReader, clock)
    val observeCalendarSuggestions =
        ObserveCalendarSuggestionsUseCase(observeCalendarEvents, friendRepo, hangoutRepo, calendarDecisionRepo, settingsRepo, planRepo, clock)
    val observeCalendarQuestions =
        ObserveCalendarQuestionsUseCase(observeCalendarEvents, friendRepo, hangoutRepo, calendarDecisionRepo, settingsRepo, planRepo, clock)
    val answerCalendarQuestion = AnswerCalendarQuestionUseCase(calendarDecisionRepo, clock)
    val decideCalendarSuggestion = DecideCalendarSuggestionUseCase(calendarDecisionRepo, clock)
    val undoCalendarDecision = UndoCalendarDecisionUseCase(calendarDecisionRepo)
    val observeCalendarSettings = ObserveCalendarSettingsUseCase(settingsRepo, calendarReader)
    val setCalendarSuggestions = SetCalendarSuggestionsUseCase(settingsRepo, clock)
    val setCalendarChosen = SetCalendarChosenUseCase(settingsRepo)

    /** The stored circles plus the built-in Family circle, as the app sees them. Writes go to [groupRepo]. */
    val circles = GroupsWithFamilyCircle(groupRepo, friendRepo, coverRepo) { "Family" }
    val backupRepo = FakeBackupRepository(
        friendRepo,
        hangoutRepo,
        reminderRepo,
        interactionRepo,
        ideaRepo,
        groupRepo,
        planRepo,
        settingsRepo,
        linkRepo,
        calendarDecisionRepo,
        giftListRepo,
        myPhotoRepo,
        coverRepo,
        backgroundRepo,
    )

    val observeFriends = ObserveFriendsUseCase(friendRepo, hangoutRepo, interactionRepo, settingsRepo, linkRepo, clock)
    val observeFriendDetail =
        ObserveFriendDetailUseCase(friendRepo, hangoutRepo, interactionRepo, ideaRepo, settingsRepo, circles, planRepo, linkRepo, clock)
    val updateFriend = UpdateFriendUseCase(friendRepo)
    val observeTags = ObserveTagsUseCase(friendRepo)
    val observeTagDuplicates = ObserveTagDuplicatesUseCase(friendRepo, settingsRepo)
    val keepTagSpellings = KeepTagSpellingsUseCase(settingsRepo)
    val renameTag = RenameTagUseCase(friendRepo)
    val deleteTag = DeleteTagUseCase(friendRepo)
    val restoreTag = RestoreTagUseCase(friendRepo)
    val observeCatchUpStory = ObserveCatchUpStoryUseCase(observeFriendDetail, reminderRepo, clock)
    val feedFetcher = FakeFeedFetcher()
    val addFeed = AddFeedUseCase(friendRepo, feedFetcher, clock)
    val refreshFeeds = RefreshFeedsUseCase(friendRepo, feedFetcher, clock)
    val shareInbox = ShareInbox()
    val quickEntryInbox = QuickEntryInbox()
    val loggedMeetups = LoggedMeetupInbox()
    val observeShortcuts = ObserveShortcutsUseCase(friendRepo, hangoutRepo, interactionRepo, settingsRepo)
    val addLifeUpdate = AddLifeUpdateUseCase(friendRepo, clock, ids)
    val observeStoryBubbles =
        ObserveStoryBubblesUseCase(friendRepo, planRepo, hangoutRepo, linkRepo, observeCalendarEvents, calendarDecisionRepo, clock)
    val addFriend = AddFriendUseCase(friendRepo, ids)
    val deleteFriend = DeleteFriendUseCase(friendRepo)
    val syncContacts = SyncContactsUseCase(friendRepo, interactionRepo)
    val importChat = ImportChatUseCase(friendRepo, interactionRepo, clock)
    val prepareNoteMedia = org.goodcompany.app.core.domain.usecase.PrepareNoteMediaUseCase(PhotoShrinker.None, ids)
    val saveNote = org.goodcompany.app.core.domain.usecase.SaveNoteUseCase(interactionRepo, clock, ids)
    val markMediaExplained = org.goodcompany.app.core.domain.usecase.MarkMediaExplainedUseCase(settingsRepo)
    val importContactsFile = ImportContactsFileUseCase(friendRepo, ids)

    /** Keeps small pictures as they are and can't shrink large ones, like the real one with an unreadable image. */
    val setFriendPhoto = SetFriendPhotoUseCase(friendRepo, PhotoShrinker.None)
    val removeFriendPhoto = RemoveFriendPhotoUseCase(friendRepo)
    val observeMyPhoto = ObserveMyPhotoUseCase(myPhotoRepo)
    val setMyPhoto = SetMyPhotoUseCase(myPhotoRepo, PhotoShrinker.None)
    val removeMyPhoto = RemoveMyPhotoUseCase(myPhotoRepo)
    val renameFriend = RenameFriendUseCase(friendRepo)
    val setCloseness = SetClosenessUseCase(friendRepo)
    val snooze = SnoozeCatchUpUseCase(friendRepo, circles, clock)
    val logCall = LogCallUseCase(interactionRepo, snooze, clock, ids)
    val checkIn = CheckInUseCase(hangoutRepo, clock, ids)
    val observePlaceBook = ObservePlaceBookUseCase(hangoutRepo)
    val observePlaces = ObservePlacesUseCase(friendRepo, hangoutRepo)
    val renamePlace = RenamePlaceUseCase(hangoutRepo)
    val deletePlace = DeletePlaceUseCase(hangoutRepo)
    val observeTypedPlaces = org.goodcompany.app.core.domain.usecase.ObserveTypedPlacesUseCase(friendRepo, hangoutRepo, planRepo)
    val renameTypedPlace = org.goodcompany.app.core.domain.usecase.RenameTypedPlaceUseCase(hangoutRepo, planRepo)
    val pinTypedPlace = org.goodcompany.app.core.domain.usecase.PinTypedPlaceUseCase(hangoutRepo, ids)
    val restorePlace = RestorePlaceUseCase(hangoutRepo)
    val mergePlaces = MergePlacesUseCase(hangoutRepo)
    val logInteraction = LogInteractionUseCase(checkIn, interactionRepo, clock, ids)
    val undoLog = UndoLogUseCase(hangoutRepo, interactionRepo, planRepo)

    /** Turns any picture into its first 16 bytes, so a test can tell a thumbnail from the shared image. */
    val thumbnailShrinker = PhotoShrinker { it.copyOf(minOf(it.size, 16)) }
    val saveShared = SaveSharedUseCase(friendRepo, circles, interactionRepo, logInteraction, thumbnailShrinker, clock, ids, hangoutRepo)
    val undoSaveShare = UndoSaveShareUseCase(friendRepo, interactionRepo, undoLog, hangoutRepo)
    val observeShareTargets = ObserveShareTargetsUseCase(friendRepo, hangoutRepo, interactionRepo, circles)
    val observeAttachments = ObserveAttachmentsUseCase(interactionRepo, circles)
    val deleteAttachment = DeleteAttachmentUseCase(interactionRepo)
    val restoreAttachment = RestoreAttachmentUseCase(interactionRepo)
    val observeActivities = ObserveActivitiesUseCase(hangoutRepo, settingsRepo)
    val observeLogKinds = ObserveLogKindsUseCase(hangoutRepo, interactionRepo, settingsRepo)
    val setLogKinds = SetLogKindsUseCase(settingsRepo)
    val observeCatchUp = ObserveCatchUpPolicyUseCase(settingsRepo)
    val setCatchUpKind = SetCatchUpKindUseCase(settingsRepo)
    val customLogKinds = CustomLogKindsUseCase(settingsRepo)
    val setActivities = SetActivitiesUseCase(settingsRepo)
    val observeTimeline = ObserveTimelineUseCase(friendRepo, hangoutRepo, interactionRepo, clock)
    val deleteTimelineEntry = DeleteTimelineEntryUseCase(friendRepo, hangoutRepo, interactionRepo)
    val restoreTimelineEntry = RestoreTimelineEntryUseCase(friendRepo, hangoutRepo, interactionRepo)
    val editTimelineEntry = EditTimelineEntryUseCase(friendRepo, hangoutRepo, interactionRepo, checkIn, logInteraction, clock, ids)
    val deleteHangout = DeleteHangoutUseCase(hangoutRepo)
    val restoreHangout = RestoreHangoutUseCase(hangoutRepo)
    val confirmPlan = ConfirmPlanUseCase(planRepo, friendRepo, checkIn, clock)
    val savePlan = SavePlanUseCase(planRepo, ideaRepo, clock, ids)
    val cancelPlan = CancelPlanUseCase(planRepo)
    val deletePlan = DeletePlanUseCase(planRepo)
    val closePlan = ClosePlanUseCase(confirmPlan, cancelPlan, deletePlan)
    val restorePlan = RestorePlanUseCase(planRepo)
    val observePlans = ObservePlansUseCase(planRepo, circles)
    val completeReminder = CompleteReminderUseCase(reminderRepo, clock)
    val snoozeReminder = SnoozeReminderUseCase(reminderRepo, clock)
    val handleNotificationAction =
        HandleNotificationActionUseCase(checkIn, logCall, snooze, circles, confirmPlan, completeReminder, snoozeReminder)
    val setCatchUpPaused = SetCatchUpPausedUseCase(friendRepo)
    val recordAppOpened = RecordAppOpenedUseCase(settingsRepo, clock)
    val importPickedContacts = ImportPickedContactsUseCase(friendRepo)
    val listContacts = ListContactsUseCase(friendRepo)
    val saveChosenContacts = SaveChosenContactsUseCase(friendRepo, importPickedContacts)
    val exportBackup = ExportBackupUseCase(backupRepo, clock)
    val restoreBackup = RestoreBackupUseCase(backupRepo, clock)
    val cloudStore = FakeCloudBackupStore()
    val backupKeyRepo = FakeBackupKeyRepository()
    val backupCrypto = FakeBackupCrypto()
    val observeBackupProtection = ObserveBackupProtectionUseCase(backupKeyRepo)
    val setBackupPassword = SetBackupPasswordUseCase(backupCrypto, backupKeyRepo)
    val changeBackupPassword = ChangeBackupPasswordUseCase(backupCrypto, backupKeyRepo)
    val encryptBackup = EncryptBackupUseCase(exportBackup, backupCrypto, backupKeyRepo)
    val openBackup = OpenBackupUseCase(backupCrypto)
    val adoptBackupKey = AdoptBackupKeyUseCase(backupKeyRepo)
    val backupToDrive = BackupToDriveUseCase(encryptBackup, cloudStore, backupKeyRepo, settingsRepo, clock)
    val fetchDriveBackup = FetchDriveBackupUseCase(cloudStore)
    val trashOldDriveBackup = TrashOldDriveBackupUseCase(cloudStore)
    val keepOldDriveBackup = KeepOldDriveBackupUseCase(backupKeyRepo)
    val driveTokens = FakeDriveTokenSource()
    val runAutoBackup = RunAutoBackupUseCase(backupToDrive, driveTokens, backupKeyRepo, settingsRepo, clock)
    val setAutoBackupFrequency = SetAutoBackupFrequencyUseCase(settingsRepo)
    val forgetDriveToken = ForgetDriveTokenUseCase(driveTokens)
    val currentLocation = GetCurrentLocationUseCase(location)
    val observeMeetups = ObserveMeetupStatusUseCase(friendRepo, hangoutRepo, interactionRepo, settingsRepo, clock)
    val observeLastSeenCard = ObserveLastSeenCardUseCase(friendRepo, hangoutRepo, interactionRepo, settingsRepo, clock, circles)
    val answerLastSeen = AnswerLastSeenUseCase(friendRepo, clock)
    val hideLastSeenCard = HideLastSeenCardUseCase(settingsRepo, clock)
    val observeOccasionMatches = ObserveOccasionMatchesUseCase(friendRepo, hangoutRepo, interactionRepo, settingsRepo, clock)
    val observeGroups = ObserveGroupsUseCase(circles, friendRepo, hangoutRepo, clock)
    val saveGroup = SaveGroupUseCase(circles, ids)
    val deleteGroup = DeleteGroupUseCase(circles, settingsRepo, coverRepo)
    val restoreGroup = RestoreGroupUseCase(circles, coverRepo)
    val answerLastMet = AnswerLastMetUseCase(circles, clock)
    val turnOffCircleReminders = TurnOffCircleRemindersUseCase(circles)

    /** Covers keep the first 16 bytes, like [thumbnailShrinker], so tests can tell a shrunk cover. */
    val circleCovers = CircleCoverUseCases(coverRepo) { it.copyOf(minOf(it.size, 16)) }

    /** Backgrounds of people's pages (issue #144) shrink like covers: the first 16 bytes. */
    val personBackgrounds = PersonBackgroundUseCases(backgroundRepo) { it.copyOf(minOf(it.size, 16)) }
    val setCircleMember = SetCircleMemberUseCase(circles)
    val nextCircleColor = NextCircleColorUseCase(circles)
    val setHomeCircleFilter = SetHomeCircleFilterUseCase(settingsRepo)
    val customActivities = org.goodcompany.app.core.domain.usecase.CustomActivitiesUseCase(settingsRepo)
    val dismissPeopleLegend = org.goodcompany.app.core.domain.usecase.DismissPeopleLegendUseCase(settingsRepo)
    val setAgendaView = org.goodcompany.app.core.domain.usecase.SetAgendaViewUseCase(settingsRepo)
    val languagePlatform = FakeAppLanguagePlatform()
    val keepLanguageInStep = org.goodcompany.app.core.domain.usecase.KeepLanguageInStepUseCase(settingsRepo, languagePlatform)
    val observeYearInReview = ObserveYearInReviewUseCase(friendRepo, hangoutRepo, reminderRepo, interactionRepo, clock)
    val observeYearReviewCard = ObserveYearReviewCardUseCase(hangoutRepo, clock)
    val observeIdeas = ObserveIdeasUseCase(ideaRepo)
    val dismissIdea = DismissIdeaUseCase(ideaRepo)
    val markIdeaProposed = MarkIdeaProposedUseCase(ideaRepo, clock)
    val generateTalkingPoints =
        GenerateTalkingPointsUseCase(friendRepo, interactionRepo, hangoutRepo, settingsRepo, ideaRepo, generator, clock, ids, language, linkRepo)
    val runWeeklyIdeas =
        RunWeeklyIdeasUseCase(friendRepo, interactionRepo, hangoutRepo, settingsRepo, ideaRepo, generator, location, clock, ids, language, linkRepo)
    val draftBirthdayMessage = DraftBirthdayMessageUseCase(friendRepo, interactionRepo, hangoutRepo, settingsRepo, assistant, language, linkRepo)
    val suggestGifts = SuggestGiftsUseCase(friendRepo, interactionRepo, hangoutRepo, settingsRepo, assistant, language, linkRepo)
    val suggestUpdateFromShared =
        SuggestUpdateFromSharedUseCase(friendRepo, interactionRepo, hangoutRepo, settingsRepo, assistant, language, linkRepo)
    val observeReminders = ObserveRemindersUseCase(reminderRepo)
    val addReminder = AddReminderUseCase(reminderRepo, clock, ids)
    val editReminder = EditReminderUseCase(reminderRepo)
    val observeCirclePage = ObserveCirclePageUseCase(circles, friendRepo, hangoutRepo, planRepo, reminderRepo, interactionRepo, clock)
    val reopenReminder = ReopenReminderUseCase(reminderRepo)
    val deleteReminder = DeleteReminderUseCase(reminderRepo)
    val restoreReminder = RestoreReminderUseCase(reminderRepo)
    val observeAgenda = ObserveAgendaUseCase(friendRepo, reminderRepo, hangoutRepo, settingsRepo, circles, planRepo, interactionRepo, linkRepo, clock)
    val keepNotificationsScheduled = KeepNotificationsScheduledUseCase(
        friendRepo,
        reminderRepo,
        hangoutRepo,
        settingsRepo,
        circles,
        planRepo,
        interactionRepo,
        linkRepo,
        observeCalendarEvents,
        calendarDecisionRepo,
        scheduler,
        FakeNotificationRenderer,
        clock,
    )
    val sendTestNotification = SendTestNotificationUseCase(settingsRepo, scheduler, FakeNotificationRenderer, clock)
    val dismissNotificationPrimer = DismissNotificationPrimerUseCase(settingsRepo)
    val updateNotificationSettings = UpdateNotificationSettingsUseCase(settingsRepo)
    val loadSampleData = LoadSampleDataUseCase(
        friendRepo,
        hangoutRepo,
        reminderRepo,
        ideaRepo,
        circles,
        planRepo,
        interactionRepo,
        clock,
        links = linkRepo,
        giftLists = giftListRepo,
        covers = coverRepo,
        sampleCover = { sampleCover },
    )

    /** What [loadSampleData] puts on the sample surf circle. */
    var sampleCover: ContactPhoto? = ContactPhoto("c2FtcGxl")
    val removeSampleData =
        RemoveSampleDataUseCase(friendRepo, hangoutRepo, reminderRepo, ideaRepo, circles, planRepo, interactionRepo, giftListRepo, coverRepo)
    val hasSampleData = HasSampleDataUseCase(friendRepo)
    val updateSource = FakeAppUpdateSource()
    val checkForUpdate = CheckForUpdateUseCase(updateSource)
    val observePersonLinks = ObservePersonLinksUseCase(linkRepo, friendRepo)
    val observeNetworkMap = ObserveNetworkMapUseCase(friendRepo, linkRepo, circles, hangoutRepo, interactionRepo, settingsRepo, clock)
    val observePeopleDashboard = ObservePeopleDashboardUseCase(friendRepo, circles, hangoutRepo, interactionRepo, settingsRepo, clock)
    val saveLink = SaveLinkUseCase(linkRepo, clock, ids)
    val deleteLink = DeleteLinkUseCase(linkRepo)
    val restoreLink = RestoreLinkUseCase(linkRepo)
    val setRelationToMe = SetRelationToMeUseCase(friendRepo)
    val setPronouns = SetPronounsUseCase(friendRepo)
    val storyViews = StoryViews()
    val addRelativeAsPerson = AddRelativeAsPersonUseCase(friendRepo, linkRepo, interactionRepo, clock, ids)
    val addKid = AddKidUseCase(friendRepo, linkRepo, clock, ids)
    val setShowInMyList = SetShowInMyListUseCase(friendRepo)
    val observeKinship = ObserveKinshipProposalsUseCase(friendRepo, linkRepo)
    val confirmKinship = ConfirmKinshipUseCase(friendRepo)
    val dismissKinship = DismissKinshipUseCase(friendRepo)
    val parseKinshipQuery = ParseKinshipQueryUseCase { EnglishKinshipWords }
    val personLinks = PersonLinkUseCases(
        observePersonLinks,
        saveLink,
        deleteLink,
        restoreLink,
        setRelationToMe,
        setPronouns,
        addRelativeAsPerson,
        addKid,
        setShowInMyList,
        confirmKinship,
        dismissKinship,
    )
    val observeSettings = ObserveSettingsUseCase(settingsRepo)
    val updateSettings = UpdateSettingsUseCase(settingsRepo)

    val observeGiftLists = ObserveGiftListsUseCase(giftListRepo, friendRepo, clock)
    val observeGiftList = ObserveGiftListUseCase(giftListRepo, friendRepo, clock)
    val giftLists = GiftListUseCases(
        create = CreateGiftListUseCase(giftListRepo, ids, clock),
        update = UpdateGiftListUseCase(giftListRepo),
        delete = DeleteGiftListUseCase(giftListRepo),
        restore = RestoreGiftListUseCase(giftListRepo),
        duplicate = DuplicateGiftListUseCase(giftListRepo, ids, clock),
        addPeople = AddPeopleToGiftListUseCase(giftListRepo),
        removePerson = RemoveFromGiftListUseCase(giftListRepo),
        setBudget = SetGiftBudgetUseCase(giftListRepo),
        addGift = AddGiftToListUseCase(giftListRepo, friendRepo, ids),
        removeGift = RemoveGiftFromListUseCase(giftListRepo),
        advance = AdvanceGiftStatusUseCase(giftListRepo, friendRepo, clock),
    )
}
