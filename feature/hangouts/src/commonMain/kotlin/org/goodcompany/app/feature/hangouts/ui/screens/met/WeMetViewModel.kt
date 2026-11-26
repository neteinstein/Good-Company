package org.goodcompany.app.feature.hangouts.ui.screens.met

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.goodcompany.app.core.domain.model.ActivityChoice
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.HangoutActivity
import org.goodcompany.app.core.domain.model.PlanOccurrence
import org.goodcompany.app.core.domain.usecase.ConfirmPlanUseCase
import org.goodcompany.app.core.domain.usecase.EditTimelineEntryUseCase
import org.goodcompany.app.core.domain.usecase.GetCurrentLocationUseCase
import org.goodcompany.app.core.domain.usecase.LogInteractionUseCase
import org.goodcompany.app.core.domain.usecase.LogKind
import org.goodcompany.app.core.domain.usecase.LogKindOption
import org.goodcompany.app.core.domain.usecase.LoggedMeetupInbox
import org.goodcompany.app.core.domain.usecase.ObserveActivitiesUseCase
import org.goodcompany.app.core.domain.usecase.ObserveCatchUpPolicyUseCase
import org.goodcompany.app.core.domain.usecase.ObserveFriendsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveGroupsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveLogKindsUseCase
import org.goodcompany.app.core.domain.usecase.ObservePlaceBookUseCase
import org.goodcompany.app.core.domain.usecase.ObservePlansUseCase
import org.goodcompany.app.core.domain.usecase.ObserveTimelineUseCase
import org.goodcompany.app.core.domain.usecase.Tags
import org.goodcompany.app.core.domain.usecase.TimelineEntryRef
import org.goodcompany.app.core.domain.usecase.asLogRequest
import org.goodcompany.app.core.domain.usecase.editsInLogSheet
import org.goodcompany.app.core.domain.util.AppClock
import org.goodcompany.app.core.ui.components.LogDraft
import org.goodcompany.app.core.ui.components.LogSheetModel
import org.goodcompany.app.core.ui.components.LogSheetState

data class WeMetUiState(
    val sheet: LogSheetState,
    /**
     * Issue #151: "Where · Note" was tapped (or a place or note is already set, or it's a journal entry, which needs its
     * text): the place (in person only) and the note show.
     */
    val detailsOpen: Boolean = false,
    /** "How?", most used first (issue #151): the first [ObserveLogKindsUseCase.SHOWN] on show, the rest behind "See all". */
    val kinds: List<LogKindOption> = ObserveLogKindsUseCase.DEFAULT_ORDER.map { LogKindOption(it) },
    /**
     * The chosen "How?" option: the sheet's kind, or one of your own (issue #152), which is an "Other" carrying its name
     * as the label. "Other" asks for a label only when it is the built-in one.
     */
    val selected: LogKindOption = LogKindOption(sheet.kind),
    /** Saved: the panel closes (the app-wide snackbar offers Undo). */
    val done: Boolean = false,
    /**
     * Issue #116: opened with nobody, no circle and no plan (Today's Log button, a shortcut, the daily reminder): the
     * people picker opens first.
     */
    val startsWithNobody: Boolean = false,
)

/**
 * What the popup opens with besides people, circle and plan (issue #151, it was the full Log popup's): a kind, a calendar
 * suggestion's day and activity guess (D3), or a timeline entry to edit (issue #102). A future [date] falls back to today.
 */
data class LogPrefill(
    /** A [LogKind] or InteractionType name (see [LogKind.parse]); unknown or null preselects in person. */
    val type: String? = null,
    val date: LocalDate? = null,
    val activity: HangoutActivity? = null,
    /** One of your own activities (issue #121), when [activity] is null. */
    val activityName: String? = null,
    /** Edit this timeline entry instead of logging a new one (issue #102); the rest is then ignored. */
    val edit: TimelineEntryRef? = null,
)

/**
 * The Log popup (issues #47, #101 and #151, F2/F3) behind `WeMetRoute`: the only place to log. Who, then "How?" (every
 * [LogKind], most used first, in person by default; a remote kind is written as an interaction and has no place or
 * activity, "Other" takes a short label, a journal entry needs its text), then When (Today preselected, Yesterday or a
 * picked day, never the future), then What (the activity, in person only), then a collapsed "Where · Note", then Save.
 * A circle lists its members with nobody ticked (F20). [plan] answers that plan occurrence's "Did you meet?" (issue #43):
 * its day, people (a circle plan's members unticked), activity, place and title fill the panel. [prefill] opens it on a
 * kind, day and activity, or on a timeline entry to edit. The logic is [LogSheetModel] (issue #21); saving goes through
 * `LogInteractionUseCase`.
 */
class WeMetViewModel(
    friendIds: List<FriendId>,
    circleId: String?,
    plan: PlanOccurrence?,
    prefill: LogPrefill,
    observeFriends: ObserveFriendsUseCase,
    observeActivities: ObserveActivitiesUseCase,
    observeCircles: ObserveGroupsUseCase,
    observePlaceBook: ObservePlaceBookUseCase,
    observePlans: ObservePlansUseCase,
    observeLogKinds: ObserveLogKindsUseCase,
    observeCatchUp: ObserveCatchUpPolicyUseCase,
    logInteraction: LogInteractionUseCase,
    confirmPlan: ConfirmPlanUseCase,
    currentLocation: GetCurrentLocationUseCase,
    loggedMeetups: LoggedMeetupInbox,
    observeTimeline: ObserveTimelineUseCase,
    editEntry: EditTimelineEntryUseCase,
    clock: AppClock,
) : ViewModel() {
    private val model = LogSheetModel(
        viewModelScope,
        LogDraft(
            kind = LogKind.parse(prefill.type) ?: LogKind.MEETUP,
            people = friendIds,
            date = prefill.date ?: clock.today(),
            circleId = circleId,
            activity = ActivityChoice.of(prefill.activity, prefill.activityName),
        ),
        observeFriends,
        observeActivities,
        observeCircles,
        observePlaceBook,
        observePlans,
        logInteraction,
        confirmPlan,
        currentLocation,
        loggedMeetups,
        clock,
        plan,
        editEntry,
        observeCatchUp(),
    )
    private val startsWithNobody = friendIds.isEmpty() && circleId == null && plan == null && prefill.edit == null
    private val detailsOpen = MutableStateFlow(false)

    /** The option tapped on "How?"; null until then (an entry being edited shows as the option its label names). */
    private val chosen = MutableStateFlow<LogKindOption?>(null)
    private val done = MutableStateFlow(false)

    init {
        // Edit mode (issue #102): open on the entry as it is; an entry that's gone (or can't be edited here) closes the popup.
        prefill.edit?.let { ref ->
            viewModelScope.launch {
                val entry = observeTimeline(ref.friendId).first().firstOrNull { it.key == ref.key }
                val request = entry?.takeIf { it.editsInLogSheet }?.asLogRequest(ref.friendId, clock.timeZone)
                if (entry == null || request == null) done.value = true else model.startEditing(entry, request)
            }
        }
    }

    val state: StateFlow<WeMetUiState> = combine(model.sheet, observeLogKinds(), detailsOpen, done, chosen) { sheet, kinds, open, isDone, picked ->
        val placeSet = sheet.kind == LogKind.MEETUP &&
            (sheet.place.name.isNotEmpty() || sheet.place.location != null || sheet.place.isLocating)
        WeMetUiState(
            sheet,
            detailsOpen = open || sheet.note.isNotEmpty() || placeSet || sheet.kind == LogKind.JOURNAL,
            kinds = kinds,
            selected = selectedOption(sheet, kinds, picked),
            done = isDone,
            startsWithNobody = startsWithNobody,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeMetUiState(model.sheet.value, startsWithNobody = startsWithNobody))

    /** "How?": any [LogKind] (issue #151: email and the journal too). */
    fun onKind(kind: LogKind) = onKind(LogKindOption(kind))

    /**
     * "How?" (issue #152): one of your own is an "Other" with its name as the label; leaving one of your own for another
     * option clears that label.
     */
    fun onKind(option: LogKindOption) {
        val before = state.value.selected
        model.onKind(option.kind)
        val name = option.name
        when {
            name != null -> model.onLabel(name)
            before.name != null -> model.onLabel("")
        }
        chosen.value = option
    }

    fun onLabel(label: String) = model.onLabel(label)

    fun onDate(date: LocalDate) = model.onDate(date)

    fun onTime(time: LocalTime?) = model.onTime(time)

    fun onActivity(activity: ActivityChoice?) = model.onActivity(activity)

    fun onTogglePerson(id: FriendId) = model.onTogglePerson(id)

    fun onPeople(ids: List<FriendId>) = model.onPeople(ids)

    /** "Where · Note" (issue #151): opens the place and note fields. */
    fun onOpenDetails() = detailsOpen.update { true }

    fun onNote(note: String) = model.onNote(note)

    fun onPlaceName(name: String) = model.onPlaceName(name)

    fun onUseLocation() = model.onUseLocation()

    fun onLocationPermissionResult(granted: Boolean) = model.onLocationPermissionResult(granted)

    fun onClearLocation() = model.onClearLocation()

    fun onChangePlace() = model.onChangePlace()

    fun onSave() = model.onSave { done.value = true }

    private companion object {
        /** What "How?" shows as chosen: the tapped option while it still matches the sheet, else derived from it. */
        fun selectedOption(sheet: LogSheetState, kinds: List<LogKindOption>, picked: LogKindOption?): LogKindOption = when {
            picked != null && picked.kind == sheet.kind -> picked
            sheet.kind == LogKind.OTHER && sheet.label.isNotBlank() ->
                kinds.firstOrNull { o -> o.name?.let { Tags.normalize(it) == Tags.normalize(sheet.label) } == true } ?: LogKindOption(LogKind.OTHER)
            else -> LogKindOption(sheet.kind)
        }
    }
}
