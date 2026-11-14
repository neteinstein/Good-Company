package com.neteinstein.goodcompany.feature.hangouts.ui.screens.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CircleCoverUseCases
import com.neteinstein.goodcompany.core.domain.usecase.CirclePage
import com.neteinstein.goodcompany.core.domain.usecase.DeleteTimelineEntryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.EditReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveCirclePageUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.domain.usecase.RestoreTimelineEntryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SavePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeCatchUpUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.ui.components.ReminderDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** One-off feedback for the circle page's snackbar; the screen turns it into localised text. */
sealed interface CirclePageMessage {
    data class Snoozed(val name: String, val until: LocalDate) : CirclePageMessage

    data object PlanSaved : CirclePageMessage

    data object ReminderSaved : CirclePageMessage

    /** A meetup or a share taken off the timeline (Undo puts it back). */
    data object EntryDeleted : CirclePageMessage

    data object CoverRemoved : CirclePageMessage

    /** The picked picture couldn't be read; the cover stays as it was. */
    data object CoverUnreadable : CirclePageMessage
}

/** What the snackbar's Undo puts back. */
sealed interface CirclePageUndo {
    data class Entry(val entry: TimelineEntry) : CirclePageUndo

    data class Cover(val cover: ContactPhoto) : CirclePageUndo
}

/** The plan editor on the page: a new plan with the circle picked, or one from "Coming up". */
data class CirclePlanEditor(val plan: Plan? = null)

/** The reminder editor on the page: a new circle reminder, or one from "Coming up". */
data class CircleReminderEditorState(val reminder: Reminder? = null)

/** A circle's page (issue #88). */
data class CirclePageUiState(
    val page: CirclePage? = null,
    val isLoading: Boolean = true,
    /** The circle is gone (deleted elsewhere): the page closes. */
    val isGone: Boolean = false,
    /** Everyone, for the plan and reminder editors. */
    val friends: List<Friend> = emptyList(),
    /** Every circle, for the plan editor. */
    val circles: List<FriendGroup> = emptyList(),
    val planEditor: CirclePlanEditor? = null,
    val reminderEditor: CircleReminderEditorState? = null,
    val message: CirclePageMessage? = null,
    val undo: CirclePageUndo? = null,
    val now: Instant = Instant.DISTANT_PAST,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
)

class CirclePageViewModel(
    private val circleId: String,
    observePage: ObserveCirclePageUseCase,
    observeFriends: ObserveFriendsUseCase,
    observeGroups: ObserveGroupsUseCase,
    private val savePlan: SavePlanUseCase,
    private val addReminder: AddReminderUseCase,
    private val editReminder: EditReminderUseCase,
    private val snooze: SnoozeCatchUpUseCase,
    private val deleteEntry: DeleteTimelineEntryUseCase,
    private val restoreEntry: RestoreTimelineEntryUseCase,
    private val covers: CircleCoverUseCases,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(CirclePageUiState())

    val state: StateFlow<CirclePageUiState> = combine(observePage(circleId), observeFriends(), observeGroups(), local) { page, friends, groups, ui ->
        ui.copy(
            page = page,
            isLoading = false,
            isGone = page == null,
            friends = friends.map { it.friend },
            circles = groups.map { it.group },
            now = clock.now(),
            today = clock.today(),
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    /** Snooze in the ⋮ menu (only with a rhythm): the weekend or a week. */
    fun onSnooze(option: SnoozeOption) {
        val name = state.value.page?.group?.name ?: return
        viewModelScope.launch {
            snooze.group(circleId, option)
            local.update { it.copy(message = CirclePageMessage.Snoozed(name, option.until(clock.today())), undo = null) }
        }
    }

    // --- Plans ---

    /** "Plan": the plan editor with the circle picked. */
    fun onNewPlan() = local.update { it.copy(planEditor = CirclePlanEditor()) }

    /** A plan on "Coming up": the editor on it. */
    fun onOpenPlan(plan: Plan) = local.update { it.copy(planEditor = CirclePlanEditor(plan)) }

    fun onDismissPlan() = local.update { it.copy(planEditor = null) }

    fun onSavePlan(input: PlanInput) {
        val editor = local.value.planEditor ?: return
        if (input.title.isBlank() || (input.friendIds.isEmpty() && input.circleId == null)) return
        viewModelScope.launch {
            savePlan.save(editor.plan?.id, input, editor.plan?.ideaId)
            local.update { it.copy(planEditor = null, message = CirclePageMessage.PlanSaved, undo = null) }
        }
    }

    // --- Reminders ---

    /** "Reminder": a new reminder that belongs to the circle. */
    fun onNewReminder() = local.update { it.copy(reminderEditor = CircleReminderEditorState()) }

    /** A reminder on "Coming up": the editor on it. */
    fun onOpenReminder(reminder: Reminder) = local.update { it.copy(reminderEditor = CircleReminderEditorState(reminder)) }

    fun onDismissReminder() = local.update { it.copy(reminderEditor = null) }

    fun onSaveReminder(draft: ReminderDraft) {
        val editor = local.value.reminderEditor ?: return
        if (draft.title.isBlank()) return
        viewModelScope.launch {
            val due = draft.date?.atTime(LocalTime(draft.hour.coerceIn(0, 23), 0))?.toInstant(clock.timeZone)
            val existing = editor.reminder
            if (existing == null) {
                addReminder(draft.title, draft.friendIds, due, draft.circleId)
            } else {
                editReminder(existing, draft.title, draft.friendIds, due, draft.circleId)
            }
            local.update { it.copy(reminderEditor = null, message = CirclePageMessage.ReminderSaved, undo = null) }
        }
    }

    // --- Timeline ---

    /** Long press on a meetup or a share: it goes (a meetup for everyone who came), with Undo. */
    fun onDeleteEntry(entry: TimelineEntry) {
        if (!entry.deletable) return
        viewModelScope.launch {
            deleteEntry(entry)
            local.update { it.copy(message = CirclePageMessage.EntryDeleted, undo = CirclePageUndo.Entry(entry)) }
        }
    }

    // --- Cover photo ---

    /** A picture picked and cropped wide for the cover; unreadable ones leave the cover as it was. */
    fun onCoverPicked(image: ByteArray?) {
        viewModelScope.launch {
            val result = covers.set(circleId, image)
            if (result.isFailure) local.update { it.copy(message = CirclePageMessage.CoverUnreadable, undo = null) }
        }
    }

    /** Remove cover: back to the circle's colour, with Undo. */
    fun onRemoveCover() {
        viewModelScope.launch {
            val before = covers.remove(circleId) ?: return@launch
            local.update { it.copy(message = CirclePageMessage.CoverRemoved, undo = CirclePageUndo.Cover(before)) }
        }
    }

    /** Undo for the last delete: a timeline entry or the cover. */
    fun onUndo() {
        val undo = local.value.undo ?: return
        viewModelScope.launch {
            when (undo) {
                is CirclePageUndo.Entry -> restoreEntry(undo.entry)
                is CirclePageUndo.Cover -> covers.restore(circleId, undo.cover)
            }
            local.update { it.copy(undo = null) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null, undo = null) }
}
