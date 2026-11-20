package com.neteinstein.goodcompany.feature.agenda.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanAnswer
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.model.ReminderSnooze
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AnswerLastSeenUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ClosePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CompleteReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DismissNotificationPrimerUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DraftBirthdayMessageUseCase
import com.neteinstein.goodcompany.core.domain.usecase.EditReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.domain.usecase.HideLastSeenCardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LastSeenAnswer
import com.neteinstein.goodcompany.core.domain.usecase.LastSeenCard
import com.neteinstein.goodcompany.core.domain.usecase.LogCallUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAgendaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveLastSeenCardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMeetupStatusUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveStoryBubblesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveYearReviewCardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.RestorePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeCatchUpUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.StoryBubble
import com.neteinstein.goodcompany.core.domain.usecase.Warmth
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.ui.components.ReminderDraft
import com.neteinstein.goodcompany.core.ui.components.ReminderNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlin.time.Instant

/** One-off feedback for Today's snackbar; the screen turns it into localised text. */
sealed interface TodayMessage {
    data object ReminderSaved : TodayMessage

    /** Done, snoozed or deleted (issue #99); Done and Delete offer Undo. */
    data class Reminder(val notice: ReminderNotice) : TodayMessage

    data class CallLogged(val name: String) : TodayMessage

    data class Snoozed(val name: String, val until: LocalDate) : TodayMessage

    /** The "I'm with…" check-in was saved with these people (first names). */
    data class CheckedIn(val names: List<String>) : TodayMessage

    /** Answered from "How did it go?" (issue #100); Cancel and Delete carry the plan as it was, for Undo. */
    data class PlanAnswered(val answer: PlanAnswer, val previous: Plan? = null) : TodayMessage

    data object PlanSkipped : TodayMessage

    data class AiError(val error: Throwable) : TodayMessage

    /** The last-seen card stored [answer] for [id] (issue #68); Undo puts back [previous]. */
    data class LastSeenAnswered(val id: FriendId, val name: String, val answer: LastSeenAnswer, val previous: LocalDate?) : TodayMessage
}

/**
 * Today (issue #65): what needs you this week, without listing everyone. The "I'm with…" check-in, the next
 * [WEEK_DAYS] days of the agenda, at most [MAX_CATCH_UPS] catch-ups (the rest on the full agenda), "Before you meet"
 * and who is drifting apart.
 */
data class TodayUiState(
    /** Agenda items in the next 7 days (today included); overdue catch-ups are in [catchUp] instead. */
    val week: List<AgendaItem> = emptyList(),
    /** At most [MAX_CATCH_UPS] people you're overdue to see, most overdue first. */
    val catchUp: List<Relationships.MeetupStatus> = emptyList(),
    /** Everyone overdue; "All N" shows when it is more than [catchUp]. */
    val catchUpTotal: Int = 0,
    /** "Before you meet": people you're seeing today or tomorrow, each with a catch-up story. */
    val stories: List<StoryBubble> = emptyList(),
    /** Real friends whose warmth is fading and who aren't overdue already (at most three). */
    val drifting: List<FriendSummary> = emptyList(),
    /** Everyone, for the reminder editor and the birthday-wish sheet. */
    val friends: List<Friend> = emptyList(),
    /** Every circle, Family included: the people picker's filter row in the reminder editor (issue #89). */
    val circles: List<FriendGroup> = emptyList(),
    val isLoading: Boolean = true,
    val showReminderEditor: Boolean = false,
    /** The reminder the editor changes; null = a new one. */
    val editingReminder: Reminder? = null,
    /** The reminder as it was before Done or Delete, for the snackbar's Undo. */
    val reminderUndo: Reminder? = null,
    val message: TodayMessage? = null,
    /** AI is on and a key is saved (F1): every AI button checks this. */
    val hasAiKey: Boolean = false,
    /** Friend whose birthday-wish sheet is open. */
    val wishFor: Friend? = null,
    val wishDraft: String? = null,
    val isDraftingWish: Boolean = false,
    /** "Your year in friendship" in December (issue #67), else null. */
    val reviewYear: Int? = null,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
    val now: Instant = Instant.fromEpochMilliseconds(0),
    /** "When did you last see…?" (issue #68): the person asked about now; null hides the card. */
    val lastSeenAsk: Friend? = null,
    /** Where [lastSeenAsk] is in this round ("1 of 12"): answers given since Today opened, plus one. */
    val lastSeenPosition: Int = 0,
    /** Answers given since Today opened plus everyone still to ask. */
    val lastSeenTotal: Int = 0,
    /** Answers given since Today opened (Undo takes one back). */
    val lastSeenAnswered: Int = 0,
    /**
     * Issue #98: notifications are on in Settings and "Not now" wasn't tapped, so the "Turn on notifications" card may
     * show (the screen shows it only while the system doesn't allow them).
     */
    val askForNotifications: Boolean = false,
) {
    /** There is someone to check in with: the "I'm with…" card shows. */
    val showCheckIn: Boolean get() = friends.isNotEmpty()

    /** No people yet: Today points to the People tab instead. */
    val isEmpty: Boolean get() = !isLoading && friends.isEmpty()

    /** [week] grouped by day, in order. */
    val weekByDay: List<Pair<LocalDate, List<AgendaItem>>> get() = week.groupBy { it.date }.toList()

    companion object {
        const val WEEK_DAYS = 7
        const val MAX_CATCH_UPS = 3
        const val MAX_DRIFTING = 3
    }
}

class TodayViewModel(
    observeAgenda: ObserveAgendaUseCase,
    observeMeetups: ObserveMeetupStatusUseCase,
    observeStories: ObserveStoryBubblesUseCase,
    observeFriends: ObserveFriendsUseCase,
    observeSettings: ObserveSettingsUseCase,
    observeReviewYear: ObserveYearReviewCardUseCase,
    observeLastSeen: ObserveLastSeenCardUseCase,
    private val addReminder: AddReminderUseCase,
    private val editReminder: EditReminderUseCase,
    private val completeReminder: CompleteReminderUseCase,
    private val snoozeReminder: SnoozeReminderUseCase,
    private val deleteReminder: DeleteReminderUseCase,
    private val restoreReminder: RestoreReminderUseCase,
    private val confirmPlan: ConfirmPlanUseCase,
    private val closePlan: ClosePlanUseCase,
    private val restorePlan: RestorePlanUseCase,
    private val logCall: LogCallUseCase,
    private val snooze: SnoozeCatchUpUseCase,
    private val draftBirthdayMessage: DraftBirthdayMessageUseCase,
    private val answerLastSeen: AnswerLastSeenUseCase,
    private val hideLastSeenCard: HideLastSeenCardUseCase,
    private val dismissNotificationPrimer: DismissNotificationPrimerUseCase,
    private val clock: AppClock,
    observeGroups: ObserveGroupsUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(TodayUiState())

    val state: StateFlow<TodayUiState> = combine(
        observeAgenda(),
        observeMeetups(),
        observeStories(),
        combine(observeFriends(), observeSettings(), observeReviewYear(), observeLastSeen(), observeGroups(), ::Extras),
        local,
    ) { agenda, meetups, stories, extras, ui ->
        build(ui, agenda, meetups, stories, extras.friends, extras.settings).copy(
            reviewYear = extras.reviewYear,
            circles = extras.circles.map {
                it.group
            },
        )
            .withLastSeen(extras.lastSeen)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private fun build(
        ui: TodayUiState,
        agenda: List<AgendaItem>,
        meetups: List<Relationships.MeetupStatus>,
        stories: List<StoryBubble>,
        friends: List<FriendSummary>,
        settings: AppSettings,
    ): TodayUiState {
        val today = clock.today()
        val lastDay = today.plus(TodayUiState.WEEK_DAYS - 1, DateTimeUnit.DAY)
        val overdue = meetups.filter { it.isOverdue(today) }.sortedBy { it.dueDate }
        val overdueIds = overdue.map { it.friend.id }.toSet()
        return ui.copy(
            week = agenda.filter { it !is AgendaItem.MeetupOverdue && it.date <= lastDay },
            catchUp = overdue.take(TodayUiState.MAX_CATCH_UPS),
            catchUpTotal = overdue.size,
            stories = stories,
            // The same rule as Home's "Drifting apart": real friends only, softer view off, not snoozed.
            drifting = friends
                .filter { settings.showWarmth }
                .filter { it.friend.isCloseFriend && !it.friend.catchUpPaused }
                .filter { it.warmth?.level == Warmth.Level.FADING && it.friend.id !in overdueIds }
                .filter { s -> s.friend.snoozedUntil?.let { it < today } ?: true }
                .sortedBy { it.warmth?.value ?: 0 }
                .take(TodayUiState.MAX_DRIFTING),
            friends = friends.map { it.friend },
            hasAiKey = settings.usableAiKey != null,
            askForNotifications = settings.notifications.enabled && !settings.notificationPrimerDismissed,
            isLoading = false,
            today = today,
            zone = clock.timeZone,
            now = clock.now(),
        )
    }

    private class Extras(
        val friends: List<FriendSummary>,
        val settings: AppSettings,
        val reviewYear: Int?,
        val lastSeen: LastSeenCard,
        val circles: List<Relationships.GroupStatus>,
    )

    private fun TodayUiState.withLastSeen(card: LastSeenCard): TodayUiState {
        val ask = card.current
        return copy(
            lastSeenAsk = ask,
            lastSeenPosition = if (ask == null) 0 else lastSeenAnswered + 1,
            lastSeenTotal = if (ask == null) 0 else lastSeenAnswered + card.pending.size,
        )
    }

    /** An answer on the last-seen card: stores the approximate date and moves on to the next person, with Undo. */
    fun onLastSeenAnswer(id: FriendId, answer: LastSeenAnswer) {
        viewModelScope.launch {
            val previous = state.value.friends.firstOrNull { it.id == id }?.approxLastSeen
            answerLastSeen(id, answer) ?: return@launch
            local.update {
                it.copy(
                    lastSeenAnswered = it.lastSeenAnswered + 1,
                    message = TodayMessage.LastSeenAnswered(id, firstName(id), answer, previous),
                )
            }
        }
    }

    /** Undo on the answer's snackbar: the person is asked again. */
    fun onUndoLastSeen(message: TodayMessage.LastSeenAnswered) {
        viewModelScope.launch {
            answerLastSeen.undo(message.id, message.previous)
            local.update { it.copy(lastSeenAnswered = (it.lastSeenAnswered - 1).coerceAtLeast(0), message = null) }
        }
    }

    /** "Not now": the card goes away until tomorrow. */
    fun onLastSeenNotNow() {
        viewModelScope.launch { hideLastSeenCard() }
    }

    /** "Not now" on the "Turn on notifications" card (issue #98): it doesn't come back; Settings → Notifications can. */
    fun onNotificationsNotNow() {
        viewModelScope.launch { dismissNotificationPrimer() }
    }

    private fun firstName(id: FriendId): String = state.value.friends.firstOrNull { it.id == id }?.displayName?.substringBefore(' ').orEmpty()

    fun onNewReminder() = local.update { it.copy(showReminderEditor = true, editingReminder = null) }

    /** "Edit" on a reminder in the week (issue #99). */
    fun onEditReminder(reminder: Reminder) = local.update { it.copy(showReminderEditor = true, editingReminder = reminder) }

    fun onDismissReminderEditor() = local.update { it.copy(showReminderEditor = false, editingReminder = null) }

    /** A new reminder, or the one being edited. */
    fun onSaveReminder(draft: ReminderDraft) {
        if (draft.title.isBlank()) return
        val editing = local.value.editingReminder
        viewModelScope.launch {
            val due = draft.dueAt(clock.timeZone)
            if (editing == null) {
                addReminder(draft.title, draft.friendIds, due, draft.circleId, draft.repeat, draft.important)
            } else {
                editReminder(editing, draft.title, draft.friendIds, due, draft.circleId, draft.repeat, draft.important)
            }
            local.update { it.copy(showReminderEditor = false, editingReminder = null, message = TodayMessage.ReminderSaved) }
        }
    }

    /** "Done": closes it (a repeating one rolls on). Never asks "did you meet?" (issue #99). */
    fun onReminderDone(reminder: Reminder) {
        viewModelScope.launch {
            val saved = completeReminder(reminder)
            val notice = ReminderNotice.Done(saved.dueAt.takeIf { !saved.done })
            local.update { it.copy(reminderUndo = reminder, message = TodayMessage.Reminder(notice)) }
        }
    }

    fun onSnoozeReminder(reminder: Reminder, snooze: ReminderSnooze) {
        viewModelScope.launch {
            val until = snoozeReminder(reminder, snooze).snoozedUntil ?: return@launch
            local.update { it.copy(reminderUndo = null, message = TodayMessage.Reminder(ReminderNotice.Snoozed(until))) }
        }
    }

    fun onDeleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            deleteReminder(reminder.id)
            local.update { it.copy(reminderUndo = reminder, message = TodayMessage.Reminder(ReminderNotice.Deleted)) }
        }
    }

    /** Undo on the Done or Delete snackbar: puts the reminder back as it was. */
    fun onUndoReminder() {
        val reminder = local.value.reminderUndo ?: return
        viewModelScope.launch {
            restoreReminder(reminder)
            local.update { it.copy(reminderUndo = null) }
        }
    }

    fun onCalled(id: FriendId) {
        viewModelScope.launch {
            logCall(id)
            local.update { it.copy(message = TodayMessage.CallLogged(firstName(id))) }
        }
    }

    fun onSnooze(id: FriendId, option: SnoozeOption) {
        viewModelScope.launch {
            snooze.friend(id, option)
            local.update { it.copy(message = TodayMessage.Snoozed(firstName(id), option.until(clock.today()))) }
        }
    }

    /** The "I'm with…" sheet saved a meetup with these people. */
    fun onCheckedIn(names: List<String>) = local.update { it.copy(message = TodayMessage.CheckedIn(names)) }

    /**
     * An answer from "How did it go?" on a plan to confirm (issue #100), for that occurrence. "It happened" opens the
     * "We met" panel through navigation instead.
     */
    fun onAnswerPlan(item: AgendaItem.PlanToConfirm, answer: PlanAnswer, wholeSeries: Boolean = false) {
        if (answer == PlanAnswer.HAPPENED) return
        viewModelScope.launch {
            val before = closePlan(item.plan.id, answer, day = item.occurrence, wholeSeries = wholeSeries)
            val undo = before.takeIf { answer == PlanAnswer.CANCEL || answer == PlanAnswer.DELETE }
            local.update { it.copy(message = TodayMessage.PlanAnswered(answer, undo)) }
        }
    }

    /** Undo on a plan cancelled or deleted a moment ago. */
    fun onUndoPlan(message: TodayMessage.PlanAnswered) {
        val plan = message.previous ?: return
        viewModelScope.launch { restorePlan(plan) }
    }

    /** "Skip this one": takes one date out of a recurring plan; the series goes on. */
    fun onSkipPlan(item: AgendaItem.PlanUpcoming) {
        viewModelScope.launch {
            confirmPlan(item.plan.id, happened = false, day = item.occurrence)
            local.update { it.copy(message = TodayMessage.PlanSkipped) }
        }
    }

    fun onOpenWish(id: FriendId) {
        val friend = state.value.friends.firstOrNull { it.id == id } ?: return
        local.update { it.copy(wishFor = friend, wishDraft = null) }
    }

    fun onDismissWish() = local.update { it.copy(wishFor = null, wishDraft = null) }

    fun onPersonaliseWish() {
        val friend = local.value.wishFor ?: return
        if (local.value.isDraftingWish || !state.value.hasAiKey) return
        viewModelScope.launch {
            local.update { it.copy(isDraftingWish = true) }
            val result = draftBirthdayMessage(friend.id)
            local.update {
                result.fold(
                    { text -> it.copy(isDraftingWish = false, wishDraft = text) },
                    { e -> it.copy(isDraftingWish = false, message = TodayMessage.AiError(e)) },
                )
            }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null, reminderUndo = null) }
}
