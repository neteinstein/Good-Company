package com.neteinstein.goodcompany.feature.hangouts.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GetCurrentLocationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HangoutWithFriends
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveHangoutHistoryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMeetupStatusUseCase
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.RestoreHangoutUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** The "I'm with…" sheet. */
data class CheckInDraft(
    val selected: Set<FriendId> = emptySet(),
    val query: String = "",
    val place: String = "",
    val note: String = "",
    val location: GeoPoint? = null,
    val isLocating: Boolean = false,
) {
    val canSave: Boolean get() = selected.isNotEmpty()
}

data class HangoutsUiState(
    /** Close friends first — the check-in picker and the "with…" filter. */
    val friends: List<Friend> = emptyList(),
    val history: List<HangoutWithFriends> = emptyList(),
    val filter: Set<FriendId> = emptySet(),
    val overdue: List<Relationships.MeetupStatus> = emptyList(),
    val upcoming: List<Relationships.MeetupStatus> = emptyList(),
    val draft: CheckInDraft? = null,
    val stats: Relationships.MonthStats = Relationships.MonthStats(0, 0, null),
    /** Hangout deleted a moment ago, offered for undo. */
    val recentlyDeleted: Hangout? = null,
    /** Increments on every check-in so the UI can celebrate. */
    val celebrate: Int = 0,
    val message: String? = null,
    val now: Instant = Instant.DISTANT_PAST,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
) {
    /** "Last time with Ana & Rui" — the newest hangout matching the filter. */
    val lastTogether: HangoutWithFriends? get() = if (filter.isEmpty()) null else history.firstOrNull()
}

class HangoutsViewModel(
    observeFriends: ObserveFriendsUseCase,
    observeHistory: ObserveHangoutHistoryUseCase,
    observeMeetups: ObserveMeetupStatusUseCase,
    observeAllHangouts: ObserveHangoutHistoryUseCase,
    private val checkIn: CheckInUseCase,
    private val deleteHangout: DeleteHangoutUseCase,
    private val restoreHangout: RestoreHangoutUseCase,
    private val currentLocation: GetCurrentLocationUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(HangoutsUiState())
    private val filter = local.map { it.filter }.distinctUntilChanged()

    val state: StateFlow<HangoutsUiState> = combine(
        observeFriends(),
        observeHistory(filter),
        observeMeetups(),
        observeAllHangouts(flowOf(emptySet())),
        local,
    ) { friends, history, meetups, all, ui ->
        val today = clock.today()
        ui.copy(
            stats = Relationships.monthStats(all.map { it.hangout }, today, clock.timeZone),
            friends = friends.map { it.friend },
            history = history,
            overdue = meetups.filter { it.isOverdue(today) },
            upcoming = meetups.filterNot { it.isOverdue(today) }.take(UPCOMING_LIMIT),
            now = clock.now(),
            today = today,
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HangoutsUiState())

    fun onToggleFilter(id: FriendId) = local.update { it.copy(filter = it.filter.toggle(id)) }

    fun onClearFilter() = local.update { it.copy(filter = emptySet()) }

    fun onStartCheckIn(preselected: FriendId? = null) =
        local.update { it.copy(draft = CheckInDraft(selected = setOfNotNull(preselected))) }

    fun onDismissCheckIn() = local.update { it.copy(draft = null) }

    private fun editDraft(transform: (CheckInDraft) -> CheckInDraft) =
        local.update { s -> s.draft?.let { s.copy(draft = transform(it)) } ?: s }

    fun onToggleDraftFriend(id: FriendId) = editDraft { it.copy(selected = it.selected.toggle(id)) }

    fun onDraftQueryChange(query: String) = editDraft { it.copy(query = query) }

    fun onDraftPlaceChange(place: String) = editDraft { it.copy(place = place) }

    fun onDraftNoteChange(note: String) = editDraft { it.copy(note = note) }

    fun onClearDraftLocation() = editDraft { it.copy(location = null) }

    /** Called once the location permission prompt resolves. */
    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            local.update { it.copy(message = "Location permission denied — type the place instead.") }
            return
        }
        editDraft { it.copy(isLocating = true) }
        viewModelScope.launch {
            val point = currentLocation()
            editDraft { it.copy(isLocating = false, location = point) }
            if (point == null) local.update { it.copy(message = "Couldn't get your location right now.") }
        }
    }

    fun onSaveCheckIn() {
        val draft = local.value.draft ?: return
        if (!draft.canSave) return
        viewModelScope.launch {
            checkIn(draft.selected, draft.place, draft.location, draft.note)
            val names = state.value.friends.filter { it.id in draft.selected }.map { it.name.substringBefore(' ') }
            local.update { it.copy(draft = null, celebrate = it.celebrate + 1, message = "Logged time with ${names.joinToString()} ✨") }
        }
    }

    fun onDeleteHangout(id: String) {
        val hangout = state.value.history.firstOrNull { it.hangout.id == id }?.hangout
        viewModelScope.launch {
            deleteHangout(id)
            local.update { it.copy(recentlyDeleted = hangout, message = "Memory deleted") }
        }
    }

    fun onUndoDelete() {
        val hangout = local.value.recentlyDeleted ?: return
        viewModelScope.launch {
            restoreHangout(hangout)
            local.update { it.copy(recentlyDeleted = null) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null, recentlyDeleted = null) }

    private fun Set<FriendId>.toggle(id: FriendId) = if (id in this) this - id else this + id

    private companion object {
        const val UPCOMING_LIMIT = 5
    }
}
