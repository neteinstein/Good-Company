package com.neteinstein.goodcompany.feature.friends.ui.screens.log

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
import kotlinx.datetime.LocalDate

/**
 * What the sheet opens with besides people and type: a calendar suggestion's day and activity guess (D3), or what the
 * "We met" panel had chosen when "More details" opened the sheet (issue #47). A future [date] falls back to today.
 */
data class LogPrefill(
    val date: LocalDate? = null,
    val activity: HangoutActivity? = null,
    val note: String? = null,
    val place: String? = null,
)

data class LogInteractionUiState(
    val sheet: LogSheetState,
    /** Saved: the sheet closes. */
    val done: Boolean = false,
)

/**
 * The "Log" sheet behind `LogInteractionRoute(friendIds, type, circleId)`: [type] is a [LogKind] or an InteractionType
 * name (see [LogKind.parse]); unknown or null preselects "Met". [circleId] opens on that circle's members, nobody ticked
 * (F20: you tick who came). [plan] opens it as the answer to that plan occurrence's "Did you meet?" (issue #43); [prefill]
 * on a given day, activity, note and place. The logic is [LogSheetModel], shared with the "We met" panel (issue #47);
 * saving goes through [LogInteractionUseCase].
 */
class LogInteractionViewModel(
    friendIds: List<FriendId>,
    type: String?,
    circleId: String?,
    plan: PlanOccurrence?,
    prefill: LogPrefill,
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
        LogDraft(
            kind = LogKind.parse(type) ?: LogKind.MEETUP,
            people = friendIds,
            date = prefill.date ?: clock.today(),
            note = prefill.note.orEmpty(),
            placeName = prefill.place.orEmpty(),
            activity = prefill.activity,
            circleId = circleId,
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
    )
    private val done = MutableStateFlow(false)

    val state: StateFlow<LogInteractionUiState> = combine(model.sheet, done) { sheet, isDone -> LogInteractionUiState(sheet, isDone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogInteractionUiState(model.sheet.value))

    fun onKind(kind: LogKind) = model.onKind(kind)

    fun onPeople(ids: List<FriendId>) = model.onPeople(ids)

    fun onCircle(id: String?) = model.onCircle(id)

    fun onTogglePerson(id: FriendId) = model.onTogglePerson(id)

    fun onDate(date: LocalDate) = model.onDate(date)

    fun onNote(note: String) = model.onNote(note)

    fun onPlaceName(name: String) = model.onPlaceName(name)

    fun onActivity(activity: HangoutActivity?) = model.onActivity(activity)

    fun onUseLocation() = model.onUseLocation()

    fun onClearLocation() = model.onClearLocation()

    fun onChangePlace() = model.onChangePlace()

    fun onSave() = model.onSave { done.value = true }
}
