package com.neteinstein.goodcompany.core.testing

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.repository.LanguageProvider
import com.neteinstein.goodcompany.core.domain.repository.PhotoShrinker
import com.neteinstein.goodcompany.core.domain.usecase.AddFeedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddKidUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddLifeUpdateUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddRelativeAsPersonUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.BackupToDriveUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CancelPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckForUpdateUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DecideCalendarSuggestionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteAttachmentUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteLinkUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeletePlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteTagUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteTimelineEntryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DismissIdeaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DraftBirthdayMessageUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ExportBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FetchDriveBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GenerateTalkingPointsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GetCurrentLocationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GroupsWithFamilyCircle
import com.neteinstein.goodcompany.core.domain.usecase.HandleNotificationActionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HasSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportChatUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportContactsFileUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportPickedContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KeepNotificationsScheduledUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KeepTagSpellingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KidsAsPeople
import com.neteinstein.goodcompany.core.domain.usecase.ListContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogCallUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogInteractionUseCase
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
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveHangoutHistoryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMeetupStatusUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveOccasionMatchesUseCase
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
import com.neteinstein.goodcompany.core.domain.usecase.PersonLinkUseCases
import com.neteinstein.goodcompany.core.domain.usecase.QuickEntryInbox
import com.neteinstein.goodcompany.core.domain.usecase.RecordAppOpenedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RefreshFeedsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveFriendPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RenameFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RenamePlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RenameTagUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreAttachmentUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreLinkUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestorePlaceUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestorePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreTagUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreTimelineEntryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RunWeeklyIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveChosenContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveLinkUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SavePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveSharedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCalendarChosenUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCalendarSuggestionsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCatchUpPausedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetClosenessUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetFriendPhotoUseCase
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
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase

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
    val calendarReader = FakeCalendarReader()
    val observeCalendarEvents = ObserveCalendarEventsUseCase(settingsRepo, calendarReader, clock)
    val observeCalendarSuggestions =
        ObserveCalendarSuggestionsUseCase(observeCalendarEvents, friendRepo, hangoutRepo, calendarDecisionRepo, settingsRepo, clock)
    val decideCalendarSuggestion = DecideCalendarSuggestionUseCase(calendarDecisionRepo, clock)
    val undoCalendarDecision = UndoCalendarDecisionUseCase(calendarDecisionRepo)
    val observeCalendarSettings = ObserveCalendarSettingsUseCase(settingsRepo, calendarReader)
    val setCalendarSuggestions = SetCalendarSuggestionsUseCase(settingsRepo, clock)
    val setCalendarChosen = SetCalendarChosenUseCase(settingsRepo)

    /** The stored circles plus the built-in Family circle, as the app sees them. Writes go to [groupRepo]. */
    val circles = GroupsWithFamilyCircle(groupRepo, friendRepo) { "Family" }
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
    val observeShortcuts = ObserveShortcutsUseCase(friendRepo, hangoutRepo, interactionRepo, settingsRepo)
    val addLifeUpdate = AddLifeUpdateUseCase(friendRepo, clock, ids)
    val observeStoryBubbles =
        ObserveStoryBubblesUseCase(friendRepo, planRepo, hangoutRepo, linkRepo, observeCalendarEvents, calendarDecisionRepo, clock)
    val addFriend = AddFriendUseCase(friendRepo, ids)
    val deleteFriend = DeleteFriendUseCase(friendRepo)
    val syncContacts = SyncContactsUseCase(friendRepo, interactionRepo)
    val importChat = ImportChatUseCase(friendRepo, interactionRepo, clock)
    val importContactsFile = ImportContactsFileUseCase(friendRepo, ids)

    /** Keeps small pictures as they are and can't shrink large ones, like the real one with an unreadable image. */
    val setFriendPhoto = SetFriendPhotoUseCase(friendRepo, PhotoShrinker.None)
    val removeFriendPhoto = RemoveFriendPhotoUseCase(friendRepo)
    val renameFriend = RenameFriendUseCase(friendRepo)
    val setRealFriend = SetRealFriendUseCase(friendRepo)
    val setCloseness = SetClosenessUseCase(friendRepo)
    val snooze = SnoozeCatchUpUseCase(friendRepo, circles, clock)
    val logCall = LogCallUseCase(interactionRepo, snooze, clock, ids)
    val checkIn = CheckInUseCase(hangoutRepo, clock, ids)
    val observePlaceBook = ObservePlaceBookUseCase(hangoutRepo)
    val observePlaces = ObservePlacesUseCase(friendRepo, hangoutRepo)
    val renamePlace = RenamePlaceUseCase(hangoutRepo)
    val deletePlace = DeletePlaceUseCase(hangoutRepo)
    val restorePlace = RestorePlaceUseCase(hangoutRepo)
    val mergePlaces = MergePlacesUseCase(hangoutRepo)
    val logInteraction = LogInteractionUseCase(checkIn, interactionRepo, clock, ids)
    val undoLog = UndoLogUseCase(hangoutRepo, interactionRepo)

    /** Turns any picture into its first 16 bytes, so a test can tell a thumbnail from the shared image. */
    val thumbnailShrinker = PhotoShrinker { it.copyOf(minOf(it.size, 16)) }
    val saveShared = SaveSharedUseCase(friendRepo, circles, interactionRepo, logInteraction, thumbnailShrinker, clock, ids)
    val undoSaveShare = UndoSaveShareUseCase(friendRepo, interactionRepo, undoLog)
    val observeShareTargets = ObserveShareTargetsUseCase(friendRepo, hangoutRepo, interactionRepo, circles)
    val observeAttachments = ObserveAttachmentsUseCase(interactionRepo, circles)
    val deleteAttachment = DeleteAttachmentUseCase(interactionRepo)
    val restoreAttachment = RestoreAttachmentUseCase(interactionRepo)
    val observeActivities = ObserveActivitiesUseCase(hangoutRepo)
    val observeTimeline = ObserveTimelineUseCase(friendRepo, hangoutRepo, interactionRepo, clock)
    val deleteTimelineEntry = DeleteTimelineEntryUseCase(friendRepo, hangoutRepo, interactionRepo)
    val restoreTimelineEntry = RestoreTimelineEntryUseCase(friendRepo, hangoutRepo, interactionRepo)
    val deleteHangout = DeleteHangoutUseCase(hangoutRepo)
    val restoreHangout = RestoreHangoutUseCase(hangoutRepo)
    val confirmPlan = ConfirmPlanUseCase(planRepo, friendRepo, checkIn, clock)
    val savePlan = SavePlanUseCase(planRepo, ideaRepo, clock, ids)
    val cancelPlan = CancelPlanUseCase(planRepo)
    val restorePlan = RestorePlanUseCase(planRepo)
    val observePlans = ObservePlansUseCase(planRepo, circles)
    val handleNotificationAction = HandleNotificationActionUseCase(checkIn, logCall, snooze, circles, confirmPlan)
    val setCatchUpPaused = SetCatchUpPausedUseCase(friendRepo)
    val recordAppOpened = RecordAppOpenedUseCase(settingsRepo, clock)
    val importPickedContacts = ImportPickedContactsUseCase(friendRepo)
    val listContacts = ListContactsUseCase(friendRepo)
    val saveChosenContacts = SaveChosenContactsUseCase(friendRepo, importPickedContacts)
    val exportBackup = ExportBackupUseCase(backupRepo, clock)
    val restoreBackup = RestoreBackupUseCase(backupRepo)
    val cloudStore = FakeCloudBackupStore()
    val backupToDrive = BackupToDriveUseCase(exportBackup, cloudStore, settingsRepo, clock)
    val fetchDriveBackup = FetchDriveBackupUseCase(cloudStore)
    val currentLocation = GetCurrentLocationUseCase(location)
    val observeHistory = ObserveHangoutHistoryUseCase(friendRepo, hangoutRepo)
    val observeMeetups = ObserveMeetupStatusUseCase(friendRepo, hangoutRepo, interactionRepo, settingsRepo, clock)
    val observeOccasionMatches = ObserveOccasionMatchesUseCase(friendRepo, hangoutRepo, interactionRepo, settingsRepo, clock)
    val observeGroups = ObserveGroupsUseCase(circles, friendRepo, hangoutRepo, clock)
    val saveGroup = SaveGroupUseCase(circles, ids)
    val deleteGroup = DeleteGroupUseCase(circles, settingsRepo)
    val restoreGroup = RestoreGroupUseCase(circles)
    val nextCircleColor = NextCircleColorUseCase(circles)
    val setHomeCircleFilter = SetHomeCircleFilterUseCase(settingsRepo)
    val observeYearInReview = ObserveYearInReviewUseCase(friendRepo, hangoutRepo, reminderRepo, interactionRepo, clock)
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
    val setReminderDone = SetReminderDoneUseCase(reminderRepo)
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
    val loadSampleData =
        LoadSampleDataUseCase(friendRepo, hangoutRepo, reminderRepo, ideaRepo, circles, planRepo, interactionRepo, clock, links = linkRepo)
    val removeSampleData = RemoveSampleDataUseCase(friendRepo, hangoutRepo, reminderRepo, ideaRepo, circles, planRepo, interactionRepo)
    val hasSampleData = HasSampleDataUseCase(friendRepo)
    val updateSource = FakeAppUpdateSource()
    val checkForUpdate = CheckForUpdateUseCase(updateSource)
    val observePersonLinks = ObservePersonLinksUseCase(linkRepo, friendRepo)
    val saveLink = SaveLinkUseCase(linkRepo, clock, ids)
    val deleteLink = DeleteLinkUseCase(linkRepo)
    val restoreLink = RestoreLinkUseCase(linkRepo)
    val setRelationToMe = SetRelationToMeUseCase(friendRepo)
    val setPronouns = SetPronounsUseCase(friendRepo)
    val addRelativeAsPerson = AddRelativeAsPersonUseCase(friendRepo, linkRepo, clock, ids)
    val addKid = AddKidUseCase(friendRepo, linkRepo, clock, ids)
    val setShowInMyList = SetShowInMyListUseCase(friendRepo)
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
    )
    val observeSettings = ObserveSettingsUseCase(settingsRepo)
    val updateSettings = UpdateSettingsUseCase(settingsRepo)
}
