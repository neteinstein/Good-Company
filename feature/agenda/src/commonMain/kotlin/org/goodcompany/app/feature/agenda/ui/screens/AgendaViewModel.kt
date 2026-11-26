package org.goodcompany.app.feature.agenda.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import org.goodcompany.app.core.domain.model.AgendaItem
import org.goodcompany.app.core.domain.model.AgendaView
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Plan
import org.goodcompany.app.core.domain.model.PlanAnswer
import org.goodcompany.app.core.domain.model.Reminder
import org.goodcompany.app.core.domain.model.ReminderSnooze
import org.goodcompany.app.core.domain.usecase.AddReminderUseCase
import org.goodcompany.app.core.domain.usecase.AgendaBuilder
import org.goodcompany.app.core.domain.usecase.ClosePlanUseCase
import org.goodcompany.app.core.domain.usecase.CompleteReminderUseCase
import org.goodcompany.app.core.domain.usecase.ConfirmPlanUseCase
import org.goodcompany.app.core.domain.usecase.DeleteReminderUseCase
import org.goodcompany.app.core.domain.usecase.DraftBirthdayMessageUseCase
import org.goodcompany.app.core.domain.usecase.EditReminderUseCase
import org.goodcompany.app.core.domain.usecase.MonthCalendar
import org.goodcompany.app.core.domain.usecase.ObserveAgendaUseCase
import org.goodcompany.app.core.domain.usecase.ObserveCalendarSuggestionsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveFriendsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveGroupsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveRemindersUseCase
import org.goodcompany.app.core.domain.usecase.ObserveSettingsUseCase
import org.goodcompany.app.core.domain.usecase.PlanInput
import org.goodcompany.app.core.domain.usecase.ReminderTarget
import org.goodcompany.app.core.domain.usecase.ReopenReminderUseCase
import org.goodcompany.app.core.domain.usecase.RestorePlanUseCase
import org.goodcompany.app.core.domain.usecase.RestoreReminderUseCase
import org.goodcompany.app.core.domain.usecase.SavePlanUseCase
import org.goodcompany.app.core.domain.usecase.SetAgendaViewUseCase
import org.goodcompany.app.core.domain.usecase.SnoozeReminderUseCase
import org.goodcompany.app.core.domain.util.AppClock
import org.goodcompany.app.core.ui.components.ReminderDraft
import org.goodcompany.app.core.ui.components.ReminderNotice
import kotlin.time.Instant

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface AgendaMessage {
    data object ReminderSaved : AgendaMessage

    /** Done, snoozed or deleted (issue #99); Done and Delete offer Undo. */
    data class Reminder(val notice: ReminderNotice) : AgendaMessage

    data class AiError(val error: Throwable) : AgendaMessage

    data object PlanLogged : AgendaMessage

    data object PlanClosed : AgendaMessage

    /** Answered from "How did it go?" (issue #100); Cancel and Delete carry the plan as it was, for Undo. */
    data class PlanAnswered(val answer: PlanAnswer, val previous: Plan? = null) : AgendaMessage

    /** One occurrence of a recurring plan skipped; the series goes on. */
    data object PlanSkipped : AgendaMessage

    /** Edited from the ⋮ of an upcoming plan (issue #136). */
    data object PlanSaved : AgendaMessage
}

data class AgendaUiState(
    val agenda: List<AgendaItem> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
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
    val message: AgendaMessage? = null,
    /** AI is on and a key is saved (F1): every AI button checks this. */
    val hasAiKey: Boolean = false,
    /** Friend whose birthday-wish sheet is open. */
    val wishFor: Friend? = null,
    val wishDraft: String? = null,
    val isDraftingWish: Boolean = false,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
    val now: Instant = Instant.fromEpochMilliseconds(0),
    /** Past meetups from the chosen calendars waiting to be logged (D3); the "From your calendar" card shows when > 0. */
    val calendarCount: Int = 0,
    /** Issue #138: Calendar (the default) or List, remembered on this device. */
    val view: AgendaView = AgendaView.CALENDAR,
    /** The month the calendar shows, in months from today's month. */
    val monthOffset: Int = 0,
    /** The day picked in the calendar; null = today. */
    val pickedDay: LocalDate? = null,
    /** Everything up to the end of the month the calendar shows (the list keeps its usual window, [agenda]). */
    val calendarAgenda: List<AgendaItem> = emptyList(),
) {
    /** The first day of the month the calendar shows. */
    val month: LocalDate get() = MonthCalendar.plusMonths(today, monthOffset)

    /** The day whose entries are listed under the grid. */
    val selectedDay: LocalDate get() = pickedDay ?: today

    /** The calendar's entries by day; overdue ones sit on today, as in the list. */
    val calendarByDay: Map<LocalDate, List<AgendaItem>> get() = MonthCalendar.byDay(calendarAgenda)

    /** The entries of [selectedDay], in agenda order. */
    val selectedItems: List<AgendaItem> get() = calendarByDay[selectedDay].orEmpty()

    /** Agenda grouped by day, preserving order. */
    val agendaByDay: List<Pair<LocalDate, List<AgendaItem>>>
        get() = agenda.groupBy { it.date }.toList()

    /** Next 7 days with how many agenda items fall on each — the week strip. */
    val week: List<Pair<LocalDate, Int>>
        get() = (0 until 7).map { offset ->
            val day = today.plus(offset, kotlinx.datetime.DateTimeUnit.DAY)
            day to agenda.count { it.date == day }
        }

    val openReminders: Int get() = reminders.count { !it.done }

    /** Open reminders, soonest first; the done ones go in the collapsed "Done" group (issue #99). */
    val open: List<Reminder> get() = reminders.filterNot { it.done }
    val done: List<Reminder> get() = reminders.filter { it.done }
}

class AgendaViewModel(
    observeAgenda: ObserveAgendaUseCase,
    private val observeReminders: ObserveRemindersUseCase,
    observeFriends: ObserveFriendsUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val draftBirthdayMessage: DraftBirthdayMessageUseCase,
    private val addReminder: AddReminderUseCase,
    private val editReminder: EditReminderUseCase,
    private val completeReminder: CompleteReminderUseCase,
    private val reopenReminder: ReopenReminderUseCase,
    private val snoozeReminder: SnoozeReminderUseCase,
    private val deleteReminder: DeleteReminderUseCase,
    private val restoreReminder: RestoreReminderUseCase,
    private val confirmPlan: ConfirmPlanUseCase,
    private val closePlan: ClosePlanUseCase,
    private val restorePlan: RestorePlanUseCase,
    private val savePlan: SavePlanUseCase,
    private val setAgendaView: SetAgendaViewUseCase,
    private val clock: AppClock,
    observeCalendarSuggestions: ObserveCalendarSuggestionsUseCase,
    observeGroups: ObserveGroupsUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(AgendaUiState())

    // Issue #138: the calendar can look past the list's window, so the agenda reaches the end of the month it shows.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val wideAgenda = local.map { it.monthOffset }.distinctUntilChanged().flatMapLatest { offset ->
        val today = clock.today()
        observeAgenda(MonthCalendar.horizonFor(MonthCalendar.plusMonths(today, offset), today))
    }

    val state: StateFlow<AgendaUiState> = combine(
        wideAgenda,
        observeReminders(),
        observeFriends(),
        combine(observeSettings(), observeCalendarSuggestions(), observeGroups()) { settings, calendar, groups ->
            Triple(settings, calendar.size, groups.map { it.group })
        },
        local,
    ) { agenda, reminders, friends, (settings, calendarCount, circles), ui ->
        val listEnd = clock.today().plus(AgendaBuilder.DEFAULT_HORIZON_DAYS, DateTimeUnit.DAY)
        ui.copy(
            view = settings.agendaView,
            calendarAgenda = agenda,
            circles = circles,
            calendarCount = calendarCount,
            hasAiKey = settings.usableAiKey != null,
            agenda = agenda.filter { it.date <= listEnd },
            reminders = reminders,
            friends = friends.map { it.friend },
            isLoading = false,
            today = clock.today(),
            zone = clock.timeZone,
            now = clock.now(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState())

    // --- Calendar (issue #138) ---

    /** The Calendar / List toggle in the top bar; remembered on this device. */
    fun onSetView(view: AgendaView) {
        viewModelScope.launch { setAgendaView(view) }
    }

    /** ‹ or a swipe to the right: the month before, its first day picked (today, in today's month). */
    fun onPreviousMonth() = showMonth(local.value.monthOffset - 1)

    /** › or a swipe to the left: the month after. */
    fun onNextMonth() = showMonth(local.value.monthOffset + 1)

    /** "Today": back to this month, today picked. */
    fun onToday() = local.update { it.copy(monthOffset = 0, pickedDay = null) }

    /** A day cell: lists its entries; a day of the month before or after moves the calendar there. */
    fun onSelectDay(day: LocalDate) {
        val today = clock.today()
        val offset = (day.year - today.year) * 12 + (day.month.ordinal - today.month.ordinal)
        local.update { it.copy(monthOffset = offset, pickedDay = day.takeIf { d -> d != today }) }
    }

    private fun showMonth(offset: Int) {
        val today = clock.today()
        val first = MonthCalendar.plusMonths(today, offset)
        local.update { it.copy(monthOffset = offset, pickedDay = if (offset == 0) null else first) }
    }

    fun onNewReminder() = local.update { it.copy(showReminderEditor = true, editingReminder = null) }

    /** "Edit" on a reminder (issue #99). */
    fun onEditReminder(reminder: Reminder) = local.update { it.copy(showReminderEditor = true, editingReminder = reminder) }

    /** A reminder notification asked for this reminder's editor (issue #135); an unknown id opens nothing. */
    fun onEditReminderById(id: String) {
        viewModelScope.launch { observeReminders().first().firstOrNull { it.id == id }?.let(::onEditReminder) }
    }

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
            local.update { it.copy(showReminderEditor = false, editingReminder = null, message = AgendaMessage.ReminderSaved) }
        }
    }

    /** "Done": closes it (a repeating one rolls on). Never asks "did you meet?" (issue #99). */
    fun onReminderDone(reminder: Reminder) {
        viewModelScope.launch {
            val saved = completeReminder(reminder)
            val notice = ReminderNotice.Done(saved.dueAt.takeIf { !saved.done })
            local.update { it.copy(reminderUndo = reminder, message = AgendaMessage.Reminder(notice)) }
        }
    }

    /** "Not done" in the Done group. */
    fun onReminderReopen(reminder: Reminder) {
        viewModelScope.launch { reopenReminder(reminder) }
    }

    fun onSnoozeReminder(reminder: Reminder, snooze: ReminderSnooze) {
        viewModelScope.launch {
            val saved = snoozeReminder(reminder, snooze)
            val until = saved.snoozedUntil ?: return@launch
            local.update { it.copy(reminderUndo = null, message = AgendaMessage.Reminder(ReminderNotice.Snoozed(until))) }
        }
    }

    fun onDeleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            deleteReminder(reminder.id)
            local.update { it.copy(reminderUndo = reminder, message = AgendaMessage.Reminder(ReminderNotice.Deleted)) }
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

    fun onOpenWish(id: FriendId) {
        val friend = state.value.friends.firstOrNull { it.id == id } ?: return
        local.update { it.copy(wishFor = friend, wishDraft = null) }
    }

    fun onDismissWish() = local.update { it.copy(wishFor = null, wishDraft = null) }

    fun onPersonaliseWish() {
        val friend = local.value.wishFor ?: return
        if (local.value.isDraftingWish) return
        viewModelScope.launch {
            local.update { it.copy(isDraftingWish = true) }
            val result = draftBirthdayMessage(friend.id)
            local.update {
                result.fold(
                    { text -> it.copy(isDraftingWish = false, wishDraft = text) },
                    { e -> it.copy(isDraftingWish = false, message = AgendaMessage.AiError(e)) },
                )
            }
        }
    }

    /**
     * "Did you meet?" answered from the agenda, for one occurrence ([day]; the open one when null). A yes from the screen
     * opens the "We met" panel instead (issue #47).
     */
    fun onConfirmPlan(id: String, happened: Boolean, day: LocalDate? = null) {
        viewModelScope.launch {
            confirmPlan(id, happened, day)
            local.update { it.copy(message = if (happened) AgendaMessage.PlanLogged else AgendaMessage.PlanClosed) }
        }
    }

    /**
     * An answer from "How did it go?" on a plan to confirm (issue #100), for that occurrence. "It happened" opens the
     * "We met" panel through navigation instead.
     */
    fun onAnswerPlan(item: AgendaItem.PlanToConfirm, answer: PlanAnswer, wholeSeries: Boolean = false) {
        if (answer == PlanAnswer.HAPPENED) return
        viewModelScope.launch {
            val before = closePlan(item.plan.id, answer, day = item.occurrence, wholeSeries = wholeSeries)
            val undo = before.takeIf { answer == PlanAnswer.CANCEL || answer == PlanAnswer.DELETE }
            local.update { it.copy(message = AgendaMessage.PlanAnswered(answer, undo)) }
        }
    }

    /** Undo on a plan cancelled or deleted a moment ago. */
    fun onUndoPlan(message: AgendaMessage.PlanAnswered) {
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
            local.update { it.copy(editingPlan = null, message = AgendaMessage.PlanSaved) }
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
            local.update { it.copy(message = AgendaMessage.PlanAnswered(answer, before)) }
        }
    }

    /** "Skip this one": takes one date out of a recurring plan; the series goes on. */
    fun onSkipPlan(item: AgendaItem.PlanUpcoming) {
        viewModelScope.launch {
            confirmPlan(item.plan.id, happened = false, day = item.occurrence)
            local.update { it.copy(message = AgendaMessage.PlanSkipped) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null, reminderUndo = null) }
}
