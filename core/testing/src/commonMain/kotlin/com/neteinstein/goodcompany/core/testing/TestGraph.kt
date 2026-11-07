package com.neteinstein.goodcompany.core.testing

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.repository.LanguageProvider
import com.neteinstein.goodcompany.core.domain.usecase.AddFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CancelPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DismissIdeaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DraftBirthdayMessageUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ExportBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GenerateTalkingPointsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GetCurrentLocationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HandleNotificationActionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HasSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportChatUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportContactsFileUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportPickedContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KeepNotificationsScheduledUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogCallUseCase
import com.neteinstein.goodcompany.core.domain.usecase.MarkIdeaProposedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAgendaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendDetailUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveHangoutHistoryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMeetupStatusUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveOccasionMatchesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlansUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveRemindersUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveYearInReviewUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RecordAppOpenedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestorePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RunWeeklyIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SavePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCatchUpPausedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCircleUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetRealFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetReminderDoneUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeCatchUpUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SuggestGiftsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase

/**
 * Every repository as a fake plus every use case wired to them, so ViewModel tests can build their subject in one
 * line (`FooViewModel(g.observeFoo, g.doBar, g.clock)`) and assert against the same fakes.
 */
class TestGraph(
    friends: List<Friend> = emptyList(),
    settings: AppSettings = AppSettings(),
    hangouts: List<Hangout> = emptyList(),
    val clock: FixedClock = FixedClock(),
    val ids: SequentialIds = SequentialIds(),
) {
    val friendRepo = FakeFriendRepository(friends)
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
    val backupRepo = FakeBackupRepository(friendRepo, hangoutRepo, reminderRepo, interactionRepo, ideaRepo, groupRepo, planRepo, settingsRepo)

    val observeFriends = ObserveFriendsUseCase(friendRepo, hangoutRepo, interactionRepo, settingsRepo, clock)
    val observeFriendDetail = ObserveFriendDetailUseCase(friendRepo, hangoutRepo, interactionRepo, ideaRepo, settingsRepo, groupRepo, planRepo, clock)
    val updateFriend = UpdateFriendUseCase(friendRepo)
    val addFriend = AddFriendUseCase(friendRepo, ids)
    val deleteFriend = DeleteFriendUseCase(friendRepo)
    val syncContacts = SyncContactsUseCase(friendRepo, interactionRepo)
    val importChat = ImportChatUseCase(friendRepo, interactionRepo, clock)
    val importContactsFile = ImportContactsFileUseCase(friendRepo, ids)
    val setRealFriend = SetRealFriendUseCase(friendRepo)
    val setCircle = SetCircleUseCase(friendRepo)
    val snooze = SnoozeCatchUpUseCase(friendRepo, groupRepo, clock)
    val logCall = LogCallUseCase(interactionRepo, snooze, clock)
    val checkIn = CheckInUseCase(hangoutRepo, clock, ids)
    val deleteHangout = DeleteHangoutUseCase(hangoutRepo)
    val restoreHangout = RestoreHangoutUseCase(hangoutRepo)
    val confirmPlan = ConfirmPlanUseCase(planRepo, friendRepo, checkIn, clock)
    val savePlan = SavePlanUseCase(planRepo, ideaRepo, clock, ids)
    val cancelPlan = CancelPlanUseCase(planRepo)
    val restorePlan = RestorePlanUseCase(planRepo)
    val observePlans = ObservePlansUseCase(planRepo)
    val handleNotificationAction = HandleNotificationActionUseCase(checkIn, logCall, snooze, groupRepo, confirmPlan)
    val setCatchUpPaused = SetCatchUpPausedUseCase(friendRepo)
    val recordAppOpened = RecordAppOpenedUseCase(settingsRepo, clock)
    val importPickedContacts = ImportPickedContactsUseCase(friendRepo)
    val exportBackup = ExportBackupUseCase(backupRepo, clock)
    val restoreBackup = RestoreBackupUseCase(backupRepo)
    val currentLocation = GetCurrentLocationUseCase(location)
    val observeHistory = ObserveHangoutHistoryUseCase(friendRepo, hangoutRepo)
    val observeMeetups = ObserveMeetupStatusUseCase(friendRepo, hangoutRepo, interactionRepo, settingsRepo, clock)
    val observeOccasionMatches = ObserveOccasionMatchesUseCase(friendRepo, hangoutRepo, interactionRepo, settingsRepo, clock)
    val observeGroups = ObserveGroupsUseCase(groupRepo, friendRepo, hangoutRepo, clock)
    val saveGroup = SaveGroupUseCase(groupRepo, ids)
    val deleteGroup = DeleteGroupUseCase(groupRepo)
    val observeYearInReview = ObserveYearInReviewUseCase(friendRepo, hangoutRepo, reminderRepo, clock)
    val observeIdeas = ObserveIdeasUseCase(ideaRepo)
    val dismissIdea = DismissIdeaUseCase(ideaRepo)
    val markIdeaProposed = MarkIdeaProposedUseCase(ideaRepo, clock)
    val generateTalkingPoints = GenerateTalkingPointsUseCase(friendRepo, interactionRepo, settingsRepo, ideaRepo, generator, clock, ids, language)
    val runWeeklyIdeas = RunWeeklyIdeasUseCase(friendRepo, interactionRepo, settingsRepo, ideaRepo, generator, location, clock, ids, language)
    val draftBirthdayMessage = DraftBirthdayMessageUseCase(friendRepo, interactionRepo, settingsRepo, assistant, language)
    val suggestGifts = SuggestGiftsUseCase(friendRepo, interactionRepo, settingsRepo, assistant, language)
    val observeReminders = ObserveRemindersUseCase(reminderRepo)
    val addReminder = AddReminderUseCase(reminderRepo, clock, ids)
    val setReminderDone = SetReminderDoneUseCase(reminderRepo)
    val deleteReminder = DeleteReminderUseCase(reminderRepo)
    val restoreReminder = RestoreReminderUseCase(reminderRepo)
    val observeAgenda = ObserveAgendaUseCase(friendRepo, reminderRepo, hangoutRepo, settingsRepo, groupRepo, planRepo, interactionRepo, clock)
    val keepNotificationsScheduled = KeepNotificationsScheduledUseCase(
        friendRepo,
        reminderRepo,
        hangoutRepo,
        settingsRepo,
        groupRepo,
        planRepo,
        interactionRepo,
        scheduler,
        FakeNotificationRenderer,
        clock,
    )
    val loadSampleData = LoadSampleDataUseCase(friendRepo, hangoutRepo, reminderRepo, ideaRepo, groupRepo, clock)
    val removeSampleData = RemoveSampleDataUseCase(friendRepo, hangoutRepo, reminderRepo, ideaRepo, groupRepo)
    val hasSampleData = HasSampleDataUseCase(friendRepo)
    val observeSettings = ObserveSettingsUseCase(settingsRepo)
    val updateSettings = UpdateSettingsUseCase(settingsRepo)
}
