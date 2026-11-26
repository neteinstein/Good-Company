package org.goodcompany.app.feature.agenda.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import org.goodcompany.app.core.domain.model.AgendaItem
import org.goodcompany.app.core.domain.model.AppSettings
import org.goodcompany.app.core.domain.model.CalendarDecision
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Plan
import org.goodcompany.app.core.domain.model.PlanAnswer
import org.goodcompany.app.core.domain.model.Reminder
import org.goodcompany.app.core.domain.model.ReminderSnooze
import org.goodcompany.app.core.domain.usecase.AddReminderUseCase
import org.goodcompany.app.core.domain.usecase.AnswerCalendarQuestionUseCase
import org.goodcompany.app.core.domain.usecase.AnswerLastMetUseCase
import org.goodcompany.app.core.domain.usecase.AnswerLastSeenUseCase
import org.goodcompany.app.core.domain.usecase.CalendarQuestion
import org.goodcompany.app.core.domain.usecase.ClosePlanUseCase
import org.goodcompany.app.core.domain.usecase.CompleteReminderUseCase
import org.goodcompany.app.core.domain.usecase.ConfirmPlanUseCase
import org.goodcompany.app.core.domain.usecase.DeleteReminderUseCase
import org.goodcompany.app.core.domain.usecase.DismissNotificationPrimerUseCase
import org.goodcompany.app.core.domain.usecase.DraftBirthdayMessageUseCase
import org.goodcompany.app.core.domain.usecase.EditReminderUseCase
import org.goodcompany.app.core.domain.usecase.FriendSummary
import org.goodcompany.app.core.domain.usecase.HideLastSeenCardUseCase
import org.goodcompany.app.core.domain.usecase.LastSeenAnswer
import org.goodcompany.app.core.domain.usecase.LastSeenCard
import org.goodcompany.app.core.domain.usecase.LogCallUseCase
import org.goodcompany.app.core.domain.usecase.ObserveAgendaUseCase
import org.goodcompany.app.core.domain.usecase.ObserveCalendarQuestionsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveFriendsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveGroupsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveLastSeenCardUseCase
import org.goodcompany.app.core.domain.usecase.ObserveMeetupStatusUseCase
import org.goodcompany.app.core.domain.usecase.ObserveSettingsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveStoryBubblesUseCase
import org.goodcompany.app.core.domain.usecase.ObserveYearReviewCardUseCase
import org.goodcompany.app.core.domain.usecase.PlanInput
import org.goodcompany.app.core.domain.usecase.Relationships
import org.goodcompany.app.core.domain.usecase.ReminderTarget
import org.goodcompany.app.core.domain.usecase.RestoreGroupUseCase
import org.goodcompany.app.core.domain.usecase.RestorePlanUseCase
import org.goodcompany.app.core.domain.usecase.RestoreReminderUseCase
import org.goodcompany.app.core.domain.usecase.SavePlanUseCase
import org.goodcompany.app.core.domain.usecase.SnoozeCatchUpUseCase
import org.goodcompany.app.core.domain.usecase.SnoozeOption
import org.goodcompany.app.core.domain.usecase.SnoozeReminderUseCase
import org.goodcompany.app.core.domain.usecase.StoryBubble
import org.goodcompany.app.core.domain.usecase.TurnOffCircleRemindersUseCase
import org.goodcompany.app.core.domain.usecase.UndoCalendarDecisionUseCase
import org.goodcompany.app.core.domain.usecase.Warmth
import org.goodcompany.app.core.domain.util.AppClock
import org.goodcompany.app.core.ui.components.ReminderDraft
import org.goodcompany.app.core.ui.components.ReminderNotice
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

    /** Edited from the ⋮ of an upcoming plan (issue #136). */
    data object PlanSaved : TodayMessage

    data class AiError(val error: Throwable) : TodayMessage

    /** The last-seen card stored [answer] for [id] (issue #68); Undo puts back [previous]. */
    data class LastSeenAnswered(val id: FriendId, val name: String, val answer: LastSeenAnswer, val previous: LocalDate?) : TodayMessage

    /** The last-seen card stored [answer] for the circle [id] (issue #150); Undo puts back [previous]. */
    data class LastMetAnswered(val id: String, val name: String, val answer: LastSeenAnswer, val previous: LocalDate?) : TodayMessage

    /** "Turn off reminders for this circle" from its row (issue #150); Undo puts [previous] back as it was. */
    data class CircleRemindersOff(val previous: FriendGroup) : TodayMessage

    /** "Who's Ana?" answered (2026-11-26) with [names] (empty = none of them); Undo asks again. */
    data class CalendarAnswered(val decision: CalendarDecision, val title: String, val names: List<String>) : TodayMessage
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
    /** "Who's Ana?" (2026-11-26): a calendar event naming a first name several people share; null hides the card. */
    val calendarQuestion: CalendarQuestion? = null,
    /** Questions put off with "Not now" since Today opened. */
    val calendarNotNow: Set<String> = emptySet(),
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

    /** The plan open in the plan editor from Edit in its ⋮ (issue #136). */
    val editingPlan: Plan? = null,
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
    /** "When did the circle last meet?" (issue #150): asked once no person is left ([lastSeenAsk] is null). */
    val lastMetAsk: FriendGroup? = null,
    /** The members of [lastMetAsk], for its avatars. */
    val lastMetMembers: List<Friend> = emptyList(),
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
    private val savePlan: SavePlanUseCase,
    private val logCall: LogCallUseCase,
    private val snooze: SnoozeCatchUpUseCase,
    private val draftBirthdayMessage: DraftBirthdayMessageUseCase,
    private val answerLastSeen: AnswerLastSeenUseCase,
    private val hideLastSeenCard: HideLastSeenCardUseCase,
    private val dismissNotificationPrimer: DismissNotificationPrimerUseCase,
    private val clock: AppClock,
    observeGroups: ObserveGroupsUseCase,
    private val answerLastMet: AnswerLastMetUseCase,
    private val turnOffCircleReminders: TurnOffCircleRemindersUseCase,
    private val restoreGroup: RestoreGroupUseCase,
    observeCalendarQuestions: ObserveCalendarQuestionsUseCase,
    private val answerCalendarQuestion: AnswerCalendarQuestionUseCase,
    private val undoCalendarDecision: UndoCalendarDecisionUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(TodayUiState())

    val state: StateFlow<TodayUiState> = combine(
        observeAgenda(),
        observeMeetups(),
        combine(observeStories(), observeCalendarQuestions(), ::Pair),
        combine(observeFriends(), observeSettings(), observeReviewYear(), observeLastSeen(), observeGroups(), ::Extras),
        local,
    ) { agenda, meetups, (stories, questions), extras, ui ->
        build(ui, agenda, meetups, stories, extras.friends, extras.settings).copy(
            calendarQuestion = questions.firstOrNull { it.key !in ui.calendarNotNow },
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
        val circle = card.currentCircle
        val asking = ask != null || circle != null
        return copy(
            lastSeenAsk = ask,
            lastMetAsk = circle,
            lastMetMembers = circle?.let { c -> c.memberIds.mapNotNull { id -> friends.firstOrNull { it.id == id } } }.orEmpty(),
            lastSeenPosition = if (asking) lastSeenAnswered + 1 else 0,
            lastSeenTotal = if (asking) lastSeenAnswered + card.pendingCount else 0,
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

    /** An answer about a circle (issue #150): it only starts the circle's rhythm; the card moves on, with Undo. */
    fun onLastMetAnswer(id: String, answer: LastSeenAnswer) {
        viewModelScope.launch {
            val circle = state.value.circles.firstOrNull { it.id == id }
            answerLastMet(id, answer) ?: return@launch
            local.update {
                it.copy(
                    lastSeenAnswered = it.lastSeenAnswered + 1,
                    message = TodayMessage.LastMetAnswered(id, circle?.name.orEmpty(), answer, circle?.approxLastMet),
                )
            }
        }
    }

    /** Undo on a circle's answer: the circle is asked again. */
    fun onUndoLastMet(message: TodayMessage.LastMetAnswered) {
        viewModelScope.launch {
            answerLastMet.undo(message.id, message.previous)
            local.update { it.copy(lastSeenAnswered = (it.lastSeenAnswered - 1).coerceAtLeast(0), message = null) }
        }
    }

    /** Snooze in the ⋮ of a circle's row (issue #150): the same choices as people. */
    fun onSnoozeCircle(id: String, option: SnoozeOption) {
        viewModelScope.launch {
            snooze.group(id, option)
            val name = state.value.circles.firstOrNull { it.id == id }?.name.orEmpty()
            local.update { it.copy(message = TodayMessage.Snoozed(name, option.until(clock.today()))) }
        }
    }

    /** "Turn off reminders for this circle" in the ⋮ of its row (issue #150), with Undo. */
    fun onCircleRemindersOff(id: String) {
        viewModelScope.launch {
            val before = turnOffCircleReminders(id) ?: return@launch
            local.update { it.copy(message = TodayMessage.CircleRemindersOff(before)) }
        }
    }

    /** Undo on "Reminders off": the circle's reminder (and snooze) come back. */
    fun onUndoCircleRemindersOff(message: TodayMessage.CircleRemindersOff) {
        viewModelScope.launch { restoreGroup(message.previous) }
    }

    /** "Who's Ana?" answered: [friendIds] are the ones it is (empty = none of them), with Undo. */
    fun onAnswerCalendarQuestion(question: CalendarQuestion, friendIds: Set<FriendId>) {
        viewModelScope.launch {
            val decision = answerCalendarQuestion(question, friendIds)
            val names = question.candidates.filter { it.id in decision.friendIds }.map { it.displayName }
            local.update { it.copy(message = TodayMessage.CalendarAnswered(decision, question.event.title, names)) }
        }
    }

    /** Undo on that answer: the question comes back. */
    fun onUndoCalendarAnswer(message: TodayMessage.CalendarAnswered) {
        viewModelScope.launch { undoCalendarDecision(message.decision) }
    }

    /** "Not now" on "Who's Ana?": the next question (if any) shows instead, until Today opens again. */
    fun onCalendarQuestionNotNow(question: CalendarQuestion) = local.update { it.copy(calendarNotNow = it.calendarNotNow + question.key) }

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

    /**
     * A tap on a reminder (issue #135): returns where it goes ([ReminderTarget]); for one with several people or nobody
     * the editor opens here, the screen opens a circle or a person.
     */
    fun onOpenReminder(reminder: Reminder): ReminderTarget = ReminderTarget.of(reminder).also {
        if (it is ReminderTarget.Edit) onEditReminder(reminder)
    }

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

    /** "Edit" in the ⋮ of an upcoming plan (issue #136): the plan editor on it. */
    fun onEditPlan(plan: Plan) = local.update { it.copy(editingPlan = plan) }

    fun onDismissPlanEditor() = local.update { it.copy(editingPlan = null) }

    /** The plan editor saved: the plan changes in place (a series as a whole), as on the Plans tab. */
    fun onSavePlan(input: PlanInput) {
        val plan = local.value.editingPlan ?: return
        if (input.title.isBlank() || (input.friendIds.isEmpty() && input.circleId == null)) return
        viewModelScope.launch {
            savePlan.save(plan.id, input, plan.ideaId)
            local.update { it.copy(editingPlan = null, message = TodayMessage.PlanSaved) }
        }
    }

    /**
     * "Cancel plan" or "Delete" in the ⋮ of an upcoming plan (issue #136), for that occurrence or, on a series, the
     * whole series. Both offer Undo, like the answers on a past plan.
     */
    fun onAnswerUpcomingPlan(item: AgendaItem.PlanUpcoming, answer: PlanAnswer, wholeSeries: Boolean = false) {
        if (answer != PlanAnswer.CANCEL && answer != PlanAnswer.DELETE) return
        viewModelScope.launch {
            val before = closePlan(item.plan.id, answer, day = item.occurrence, wholeSeries = wholeSeries)
            local.update { it.copy(message = TodayMessage.PlanAnswered(answer, before)) }
        }
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
