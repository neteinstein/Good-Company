package com.neteinstein.goodcompany.feature.hangouts.ui.screens.met

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.PlanOccurrence
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GetCurrentLocationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogInteractionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.LoggedMeetupInbox
import com.neteinstein.goodcompany.core.domain.usecase.ObserveActivitiesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlaceBookUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlansUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.ui.components.LogDraft
import com.neteinstein.goodcompany.core.ui.components.LogSheetModel
import com.neteinstein.goodcompany.core.ui.components.LogSheetState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class WeMetUiState(
    val sheet: LogSheetState,
    /** "+ Add a note" was tapped (or there already is one): the note field shows. */
    val noteOpen: Boolean = false,
    /** "+ Add a place" was tapped (or one is set): the place field (named places, F11) shows. */
    val placeOpen: Boolean = false,
    /** Saved: the panel closes (the app-wide snackbar offers Undo). */
    val done: Boolean = false,
    /**
     * Issue #116: opened with nobody, no circle and no plan (Today's Log button, a shortcut, the daily reminder): the
     * people picker opens first and "who" leads the panel.
     */
    val startsWithNobody: Boolean = false,
)

/** What "More details" hands to the full Log sheet: everything chosen so far. */
data class WeMetDetails(
    val friendIds: List<FriendId>,
    val circleId: String?,
    val date: LocalDate,
    val activity: HangoutActivity?,
    val note: String,
    val plan: PlanOccurrence?,
    val placeName: String = "",
    /** The "How?" chosen (issue #101), so the full Log sheet opens on the same kind. */
    val kind: LogKind = LogKind.MEETUP,
    val label: String = "",
    /** Issue #116: the time of day chosen (null = now / noon). */
    val time: LocalTime? = null,
)

/**
 * The small "Caught up" panel (issues #47 and #101, F2/F3) behind `WeMetRoute(friendIds, circleId, plan)`. First
 * "How?" ([LogKind.CAUGHT_UP], in person by default): a remote kind is written as an interaction and has no place or
 * activity, and "Other" takes a short label. Then Today preselected,
 * Yesterday or a picked day (never the future), the activity and an optional note, then Save. A circle lists its
 * members with nobody ticked (F20). [plan] answers that plan occurrence's "Did you meet?" (issue #43): its day,
 * people (a circle plan's members unticked), activity, place and title fill the panel, and saving closes it. The logic is the Log sheet's own [LogSheetModel] (issue #21).
 */
class WeMetViewModel(
    friendIds: List<FriendId>,
    circleId: String?,
    plan: PlanOccurrence?,
    observeFriends: ObserveFriendsUseCase,
    observeActivities: ObserveActivitiesUseCase,
    observeCircles: ObserveGroupsUseCase,
    observePlaceBook: ObservePlaceBookUseCase,
    observePlans: ObservePlansUseCase,
    logInteraction: LogInteractionUseCase,
    confirmPlan: ConfirmPlanUseCase,
    currentLocation: GetCurrentLocationUseCase,
    loggedMeetups: LoggedMeetupInbox,
    clock: AppClock,
) : ViewModel() {
    private val model = LogSheetModel(
        viewModelScope,
        LogDraft(kind = LogKind.MEETUP, people = friendIds, date = clock.today(), circleId = circleId),
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
    )
    private val startsWithNobody = friendIds.isEmpty() && circleId == null && plan == null
    private val noteOpen = MutableStateFlow(false)
    private val placeOpen = MutableStateFlow(false)
    private val done = MutableStateFlow(false)

    val state: StateFlow<WeMetUiState> = combine(model.sheet, noteOpen, placeOpen, done) { sheet, note, place, isDone ->
        WeMetUiState(
            sheet,
            noteOpen = note || sheet.note.isNotEmpty(),
            placeOpen = !sheet.kind.isRemote &&
                (place || sheet.place.name.isNotEmpty() || sheet.place.location != null || sheet.place.isLocating),
            done = isDone,
            startsWithNobody = startsWithNobody,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeMetUiState(model.sheet.value, startsWithNobody = startsWithNobody))

    /** "How?": in person, a call, a video call, "said hi", a long chat, a game online or something else. */
    fun onKind(kind: LogKind) {
        if (kind in LogKind.CAUGHT_UP) model.onKind(kind)
    }

    fun onLabel(label: String) = model.onLabel(label)

    fun onDate(date: LocalDate) = model.onDate(date)

    fun onTime(time: LocalTime?) = model.onTime(time)

    fun onActivity(activity: HangoutActivity?) = model.onActivity(activity)

    fun onTogglePerson(id: FriendId) = model.onTogglePerson(id)

    fun onPeople(ids: List<FriendId>) = model.onPeople(ids)

    fun onOpenNote() = noteOpen.update { true }

    fun onNote(note: String) = model.onNote(note)

    fun onOpenPlace() = placeOpen.update { true }

    fun onPlaceName(name: String) = model.onPlaceName(name)

    fun onUseLocation() = model.onUseLocation()

    fun onLocationPermissionResult(granted: Boolean) = model.onLocationPermissionResult(granted)

    fun onClearLocation() = model.onClearLocation()

    fun onChangePlace() = model.onChangePlace()

    fun onSave() = model.onSave { done.value = true }

    /** Everything chosen so far, for "More details" (the full Log sheet). */
    fun details(): WeMetDetails = model.draft.value.let {
        WeMetDetails(it.people, it.circleId, it.date, it.activity, it.note, model.plan, it.placeName, it.kind, it.label, it.time)
    }
}
