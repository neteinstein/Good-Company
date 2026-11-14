package com.neteinstein.goodcompany.feature.friends.ui.screens.log

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

data class LogInteractionUiState(
    val sheet: LogSheetState,
    /** Saved: the sheet closes. */
    val done: Boolean = false,
)

/**
 * What the "We met" panel had chosen when "More details" opened the full sheet (issue #47). All optional: an empty
 * value opens the sheet as before. [date] is ISO (`2026-11-06`), [activity] a [HangoutActivity] name.
 */
data class LogPrefill(
    val date: String? = null,
    val activity: String? = null,
    val note: String? = null,
    val planId: String? = null,
)

/**
 * The "Log" sheet behind `LogInteractionRoute(friendIds, type, circleId)`: [type] is a [LogKind] or an InteractionType
 * name (see [LogKind.parse]); unknown or null preselects "Met". [circleId] opens on that circle's members, nobody ticked
 * (F20: you tick who came). [prefill] carries what the "We met" panel had chosen. The logic is [LogSheetModel], shared
 * with that panel; saving goes through [LogInteractionUseCase].
 */
class LogInteractionViewModel(
    friendIds: List<FriendId>,
    type: String?,
    circleId: String?,
    prefill: LogPrefill,
    observeFriends: ObserveFriendsUseCase,
    observeActivities: ObserveActivitiesUseCase,
    observeCircles: ObserveGroupsUseCase,
    logInteraction: LogInteractionUseCase,
    currentLocation: GetCurrentLocationUseCase,
    loggedMeetups: LoggedMeetupInbox,
    private val clock: AppClock,
) : ViewModel() {
    private val model = LogSheetModel(
        viewModelScope,
        LogDraft(
            kind = LogKind.parse(type) ?: LogKind.MEETUP,
            people = friendIds,
            date = prefill.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: clock.today(),
            note = prefill.note.orEmpty(),
            activity = prefill.activity?.let { name -> HangoutActivity.entries.firstOrNull { it.name == name } },
            circleId = circleId,
            planId = prefill.planId,
        ),
        observeFriends,
        observeActivities,
        observeCircles,
        logInteraction,
        currentLocation,
        loggedMeetups,
        clock,
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

    fun onSave() = model.onSave { done.value = true }
}
