package com.neteinstein.goodcompany.feature.friends.ui.screens.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.usecase.GetCurrentLocationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogInteractionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.LogRequest
import com.neteinstein.goodcompany.core.domain.usecase.ObserveActivitiesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlaceBookUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.ui.components.LogSheetState
import com.neteinstein.goodcompany.core.ui.components.meetupPlaceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** What's typed or picked in the sheet so far; the friends themselves come from the repository. */
private data class Draft(
    val kind: LogKind,
    val people: List<FriendId>,
    val date: LocalDate,
    val note: String = "",
    val placeName: String = "",
    val location: GeoPoint? = null,
    val isLocating: Boolean = false,
    /** "Change" was tapped on a place that named itself: a new name is typed instead. */
    val placeChanged: Boolean = false,
    val activity: HangoutActivity? = null,
    val saving: Boolean = false,
    val circleId: String? = null,
)

data class LogInteractionUiState(
    val sheet: LogSheetState,
    /** Saved: the sheet closes. */
    val done: Boolean = false,
)

/**
 * The "Log" sheet behind `LogInteractionRoute(friendIds, type, circleId)`: [type] is a [LogKind] or an InteractionType
 * name (see [LogKind.parse]); unknown or null preselects "Met". [circleId] opens on that circle's members, nobody ticked
 * (F20: you tick who came). Saving goes through [LogInteractionUseCase].
 */
class LogInteractionViewModel(
    friendIds: List<FriendId>,
    type: String?,
    circleId: String?,
    observeFriends: ObserveFriendsUseCase,
    observeActivities: ObserveActivitiesUseCase,
    observeCircles: ObserveGroupsUseCase,
    observePlaceBook: ObservePlaceBookUseCase,
    private val logInteraction: LogInteractionUseCase,
    private val currentLocation: GetCurrentLocationUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val draft = MutableStateFlow(Draft(LogKind.parse(type) ?: LogKind.MEETUP, friendIds.distinct(), clock.today(), circleId = circleId))
    private val done = MutableStateFlow(false)

    private fun initial() = LogInteractionUiState(LogSheetState(kind = draft.value.kind, date = draft.value.date, today = clock.today()))

    val state: StateFlow<LogInteractionUiState> = combine(
        combine(observeFriends(), observePlaceBook(), ::Pair),
        observeActivities(),
        observeCircles(),
        draft,
        done,
    ) {
            (friends, places),
            activities,
            circles,
            d,
            isDone,
        ->
        val byId = friends.associateBy { it.friend.id }
        val circle = circles.firstOrNull { it.group.id == d.circleId }
        val everyone = friends.map { it.friend }
        LogInteractionUiState(
            sheet = LogSheetState(
                kind = d.kind,
                people = d.people.mapNotNull { byId[it]?.friend },
                everyone = everyone.sortedBy { it.displayName.lowercase() },
                date = d.date,
                today = clock.today(),
                note = d.note,
                place = meetupPlaceState(d.placeName, d.location, d.isLocating, d.placeChanged, places, d.people, everyone),
                activity = d.activity,
                activities = activities,
                saving = d.saving,
                circles = circles.map { it.group }.sortedBy { it.name.lowercase() },
                circle = circle?.group,
                circleMembers = circle?.members.orEmpty(),
            ),
            done = isDone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial())

    fun onKind(kind: LogKind) = draft.update { it.copy(kind = kind) }

    fun onPeople(ids: List<FriendId>) = draft.update { it.copy(people = ids.distinct()) }

    /** Picks a circle (null = none): its members are listed unticked; whoever is already ticked stays ticked. */
    fun onCircle(id: String?) = draft.update { it.copy(circleId = id) }

    fun onTogglePerson(id: FriendId) = draft.update { if (id in it.people) it.copy(people = it.people - id) else it.copy(people = it.people + id) }

    /** Future days can't be picked; they fall back to today. */
    fun onDate(date: LocalDate) = draft.update { it.copy(date = minOf(date, clock.today())) }

    fun onNote(note: String) = draft.update { it.copy(note = note) }

    fun onPlaceName(name: String) = draft.update { it.copy(placeName = name) }

    fun onActivity(activity: HangoutActivity?) = draft.update { it.copy(activity = activity) }

    fun onUseLocation() {
        draft.update { it.copy(isLocating = true) }
        viewModelScope.launch {
            val point = currentLocation()
            draft.update { it.copy(isLocating = false, location = point ?: it.location) }
        }
    }

    fun onClearLocation() = draft.update { it.copy(location = null, placeChanged = false) }

    /** F11: the place named itself, but it's somewhere else: type its name instead. */
    fun onChangePlace() = draft.update { it.copy(placeChanged = true, placeName = "") }

    fun onSave() {
        val d = draft.value
        val sheet = state.value.sheet
        if (!sheet.canSave) return
        // At a named place the meetup takes its name (CheckInUseCase matches it again from the fix).
        val placeName = sheet.place.match?.place?.name ?: d.placeName
        // Only people who exist: an app-icon shortcut can name someone deleted since (goodcompany://log/<id>).
        val people = sheet.people.map { it.id }
        draft.update { it.copy(saving = true) }
        viewModelScope.launch {
            val meetup = d.kind == LogKind.MEETUP
            val ok = runCatching {
                logInteraction(
                    LogRequest(
                        friendIds = people,
                        kind = d.kind,
                        date = d.date,
                        note = d.note,
                        placeName = placeName.takeIf { meetup },
                        location = d.location.takeIf { meetup },
                        activity = d.activity.takeIf { meetup },
                    ),
                )
            }.isSuccess
            draft.update { it.copy(saving = false) }
            if (ok) done.value = true
        }
    }
}
