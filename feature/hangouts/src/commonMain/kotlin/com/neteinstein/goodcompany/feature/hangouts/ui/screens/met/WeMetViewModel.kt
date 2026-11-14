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

data class WeMetUiState(
    val sheet: LogSheetState,
    /** "+ Add a note" was tapped (or there already is one): the note field shows. */
    val noteOpen: Boolean = false,
    /** "+ Add a place" was tapped (or one is set): the place field (named places, F11) shows. */
    val placeOpen: Boolean = false,
    /** Saved: the panel closes (the app-wide snackbar offers Undo). */
    val done: Boolean = false,
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
)

/**
 * The small "We met" panel (issue #47, F2/F3) behind `WeMetRoute(friendIds, circleId, plan)`: Today preselected,
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
    private val noteOpen = MutableStateFlow(false)
    private val placeOpen = MutableStateFlow(false)
    private val done = MutableStateFlow(false)

    val state: StateFlow<WeMetUiState> = combine(model.sheet, noteOpen, placeOpen, done) { sheet, note, place, isDone ->
        WeMetUiState(
            sheet,
            noteOpen = note || sheet.note.isNotEmpty(),
            placeOpen = place || sheet.place.name.isNotEmpty() || sheet.place.location != null || sheet.place.isLocating,
            done = isDone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeMetUiState(model.sheet.value))

    fun onDate(date: LocalDate) = model.onDate(date)

    fun onActivity(activity: HangoutActivity?) = model.onActivity(activity)

    fun onTogglePerson(id: FriendId) = model.onTogglePerson(id)

    fun onPeople(ids: List<FriendId>) = model.onPeople(ids)

    fun onOpenNote() = noteOpen.update { true }

    fun onNote(note: String) = model.onNote(note)

    fun onOpenPlace() = placeOpen.update { true }

    fun onPlaceName(name: String) = model.onPlaceName(name)

    fun onUseLocation() = model.onUseLocation()

    fun onClearLocation() = model.onClearLocation()

    fun onChangePlace() = model.onChangePlace()

    fun onSave() = model.onSave { done.value = true }

    /** Everything chosen so far, for "More details" (the full Log sheet). */
    fun details(): WeMetDetails = model.draft.value.let {
        WeMetDetails(it.people, it.circleId, it.date, it.activity, it.note, model.plan, it.placeName)
    }
}
