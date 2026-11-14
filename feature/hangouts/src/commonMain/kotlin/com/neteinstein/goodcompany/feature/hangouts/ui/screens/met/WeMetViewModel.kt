package com.neteinstein.goodcompany.feature.hangouts.ui.screens.met

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.usecase.GetCurrentLocationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogInteractionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.LoggedMeetupInbox
import com.neteinstein.goodcompany.core.domain.usecase.ObserveActivitiesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlansUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.ui.components.LogDraft
import com.neteinstein.goodcompany.core.ui.components.LogSheetModel
import com.neteinstein.goodcompany.core.ui.components.LogSheetState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class WeMetUiState(
    val sheet: LogSheetState,
    /** "+ Add a note" was tapped (or there already is one): the note field shows. */
    val noteOpen: Boolean = false,
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
    val planId: String?,
)

/**
 * The small "We met" panel (issue #47, F2/F3) behind `WeMetRoute(friendIds, circleId, planId)`: Today preselected,
 * Yesterday or a picked day (never the future), the activity and an optional note, then Save. A circle lists its
 * members with nobody ticked (F20). [planId] answers that plan's "Did you meet?": its day, place and title fill the
 * panel and saving closes it as happened. The logic is the Log sheet's own [LogSheetModel] (issue #21).
 */
class WeMetViewModel(
    friendIds: List<FriendId>,
    circleId: String?,
    planId: String?,
    observeFriends: ObserveFriendsUseCase,
    observeActivities: ObserveActivitiesUseCase,
    observeCircles: ObserveGroupsUseCase,
    observePlans: ObservePlansUseCase,
    logInteraction: LogInteractionUseCase,
    currentLocation: GetCurrentLocationUseCase,
    loggedMeetups: LoggedMeetupInbox,
    clock: AppClock,
) : ViewModel() {
    private val model = LogSheetModel(
        viewModelScope,
        LogDraft(kind = LogKind.MEETUP, people = friendIds, date = clock.today(), circleId = circleId, planId = planId),
        observeFriends,
        observeActivities,
        observeCircles,
        logInteraction,
        currentLocation,
        loggedMeetups,
        clock,
    )
    private val noteOpen = MutableStateFlow(false)
    private val done = MutableStateFlow(false)

    val state: StateFlow<WeMetUiState> = combine(model.sheet, noteOpen, done) { sheet, open, isDone ->
        WeMetUiState(sheet, noteOpen = open || sheet.note.isNotEmpty(), done = isDone)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeMetUiState(model.sheet.value))

    init {
        if (planId != null) {
            viewModelScope.launch {
                val plan = observePlans().first().firstOrNull { it.id == planId } ?: return@launch
                model.prefill {
                    it.copy(
                        people = it.people.ifEmpty { plan.friendIds },
                        date = plan.date,
                        placeName = plan.place.orEmpty(),
                        note = plan.title,
                    )
                }
            }
        }
    }

    fun onDate(date: LocalDate) = model.onDate(date)

    fun onActivity(activity: HangoutActivity?) = model.onActivity(activity)

    fun onTogglePerson(id: FriendId) = model.onTogglePerson(id)

    fun onPeople(ids: List<FriendId>) = model.onPeople(ids)

    fun onOpenNote() = noteOpen.update { true }

    fun onNote(note: String) = model.onNote(note)

    fun onSave() = model.onSave { done.value = true }

    /** Everything chosen so far, for "More details" (the full Log sheet). */
    fun details(): WeMetDetails = model.draft.value.let {
        WeMetDetails(it.people, it.circleId, it.date, it.activity, it.note, it.planId)
    }
}
