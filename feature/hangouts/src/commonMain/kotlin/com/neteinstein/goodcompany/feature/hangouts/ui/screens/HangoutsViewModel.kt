package com.neteinstein.goodcompany.feature.hangouts.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.GetCurrentLocationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HangoutWithFriends
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveHangoutHistoryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMeetupStatusUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveOccasionMatchesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlaceBookUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePlansUseCase
import com.neteinstein.goodcompany.core.domain.usecase.OccasionMatch
import com.neteinstein.goodcompany.core.domain.usecase.PlaceBook
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.RestoreGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SavePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeCatchUpUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.YearInReviewBuilder
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.ui.components.MeetupPlaceState
import com.neteinstein.goodcompany.core.ui.components.meetupPlaceState
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
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** The "I'm with…" sheet. */
data class CheckInDraft(
    val selected: Set<FriendId> = emptySet(),
    val query: String = "",
    val place: String = "",
    val note: String = "",
    val location: GeoPoint? = null,
    val isLocating: Boolean = false,
    /** "Change" was tapped on a place that named itself (F11): a new name is typed instead. */
    val placeChanged: Boolean = false,
    /** A circle picked in the sheet: its members are listed first, nobody ticked, to tick who came (F20). */
    val circleId: String? = null,
) {
    val canSave: Boolean get() = selected.isNotEmpty()
}

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface HangoutsMessage {
    data object LocationDenied : HangoutsMessage

    data object LocationUnavailable : HangoutsMessage

    data class LoggedWith(val names: List<String>) : HangoutsMessage

    data object MemoryDeleted : HangoutsMessage

    data class GroupDeleted(val name: String) : HangoutsMessage

    data class GroupSnoozed(val name: String, val until: LocalDate) : HangoutsMessage

    data object PlanSaved : HangoutsMessage
}

/** The plan editor, opened from "Who's free for…?" (a friend and an occasion) or from a circle's card. */
data class PlanDraft(val friendId: FriendId? = null, val occasion: Occasion? = null, val circleId: String? = null)

data class HangoutsUiState(
    /** Close friends first — the check-in picker and the "with…" filter. */
    val friends: List<Friend> = emptyList(),
    val history: List<HangoutWithFriends> = emptyList(),
    val filter: Set<FriendId> = emptySet(),
    val overdue: List<Relationships.MeetupStatus> = emptyList(),
    val upcoming: List<Relationships.MeetupStatus> = emptyList(),
    val draft: CheckInDraft? = null,
    /** The check-in's place (F11): named by itself at a known spot, with "Usually here with". */
    val draftPlace: MeetupPlaceState = MeetupPlaceState(),
    /** Circles ("Your circles"), most overdue first. */
    val groups: List<Relationships.GroupStatus> = emptyList(),
    /** The year the "Your year in friendship" card opens, or null when there's nothing to show yet. */
    val reviewYear: Int? = null,
    val stats: Relationships.MonthStats = Relationships.MonthStats(0, 0, null),
    /** Hangout deleted a moment ago, offered for undo. */
    val recentlyDeleted: Hangout? = null,
    /** Circle deleted a moment ago, offered for undo. */
    val recentlyDeletedGroup: FriendGroup? = null,
    /** Increments on every check-in so the UI can celebrate. */
    val celebrate: Int = 0,
    /** True until the stored data has been read once (so "no memories yet" isn't shown while loading). */
    val isLoading: Boolean = true,
    val message: HangoutsMessage? = null,
    /** "Who's free for…?": friends per occasion (only occasions someone is tagged with). */
    val occasions: Map<Occasion, List<OccasionMatch>> = emptyMap(),
    val selectedOccasion: Occasion? = null,
    val planDraft: PlanDraft? = null,
    /** Each circle's next open plan (its card shows it), by circle id. */
    val circlePlans: Map<String, Plan> = emptyMap(),
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
    observeGroups: ObserveGroupsUseCase,
    observeOccasions: ObserveOccasionMatchesUseCase,
    observePlaceBook: ObservePlaceBookUseCase,
    observePlans: ObservePlansUseCase,
    private val savePlan: SavePlanUseCase,
    private val deleteGroup: DeleteGroupUseCase,
    private val restoreGroup: RestoreGroupUseCase,
    private val snooze: SnoozeCatchUpUseCase,
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
        combine(observeAllHangouts(flowOf(emptySet())), observeGroups(), observeOccasions(), observePlaceBook(), observePlans(), ::Extras),
        local,
    ) { friends, history, meetups, (all, groups, occasions, places, plans), ui ->
        val today = clock.today()
        val hangouts = all.map { it.hangout }
        val reviewYear = YearInReviewBuilder.defaultYear(today, hangouts, clock.timeZone)
        val draft = ui.draft
        ui.copy(
            isLoading = false,
            draftPlace = if (draft == null) {
                MeetupPlaceState()
            } else {
                meetupPlaceState(draft.place, draft.location, draft.isLocating, draft.placeChanged, places, draft.selected, friends.map { it.friend })
            },
            groups = groups,
            reviewYear = reviewYear.takeIf { y -> hangouts.any { it.at.toLocalDateTime(clock.timeZone).year == y } },
            stats = Relationships.monthStats(all.map { it.hangout }, today, clock.timeZone),
            friends = friends.map { it.friend },
            history = history,
            overdue = meetups.filter { it.isOverdue(today) },
            upcoming = meetups.filterNot { it.isOverdue(today) }.take(UPCOMING_LIMIT),
            occasions = occasions,
            circlePlans = plans.filter { it.circleId != null }.groupBy { it.circleId!! }.mapValues { (_, ps) -> ps.minBy { it.date } },
            selectedOccasion = ui.selectedOccasion?.takeIf { it in occasions } ?: occasions.keys.firstOrNull(),
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

    /**
     * Tapping a circle in the check-in sheet lists its members first with nobody ticked, so you tick who came (F20).
     * Tapping it again goes back to everyone; people already ticked stay ticked.
     */
    fun onPickDraftGroup(groupId: String) = editDraft { it.copy(circleId = groupId.takeUnless { id -> id == it.circleId }) }

    fun onDeleteGroup(groupId: String) {
        viewModelScope.launch {
            val removed = deleteGroup(groupId) ?: return@launch
            local.update { it.copy(recentlyDeletedGroup = removed, recentlyDeleted = null, message = HangoutsMessage.GroupDeleted(removed.name)) }
        }
    }

    fun onSnoozeGroup(groupId: String, option: SnoozeOption) {
        val name = state.value.groups.firstOrNull { it.group.id == groupId }?.group?.name ?: return
        viewModelScope.launch {
            snooze.group(groupId, option)
            local.update { it.copy(message = HangoutsMessage.GroupSnoozed(name, option.until(clock.today()))) }
        }
    }

    fun onDraftQueryChange(query: String) = editDraft { it.copy(query = query) }

    fun onDraftPlaceChange(place: String) = editDraft { it.copy(place = place) }

    fun onDraftNoteChange(note: String) = editDraft { it.copy(note = note) }

    fun onClearDraftLocation() = editDraft { it.copy(location = null, placeChanged = false) }

    /** F11: the place named itself, but it's somewhere else: type its name instead. */
    fun onChangeDraftPlace() = editDraft { it.copy(placeChanged = true, place = "") }

    /** Called once the location permission prompt resolves. */
    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            local.update { it.copy(message = HangoutsMessage.LocationDenied) }
            return
        }
        editDraft { it.copy(isLocating = true) }
        viewModelScope.launch {
            val point = currentLocation()
            editDraft { it.copy(isLocating = false, location = point) }
            if (point == null) local.update { it.copy(message = HangoutsMessage.LocationUnavailable) }
        }
    }

    fun onSaveCheckIn() {
        val draft = local.value.draft ?: return
        if (!draft.canSave) return
        viewModelScope.launch {
            // At a named place the meetup takes its name (CheckInUseCase matches it again from the fix).
            val place = state.value.draftPlace.match?.place?.name ?: draft.place
            checkIn(draft.selected, place, draft.location, draft.note)
            val names = state.value.friends.filter { it.id in draft.selected }.map { it.displayName.substringBefore(' ') }
            local.update { it.copy(draft = null, celebrate = it.celebrate + 1, message = HangoutsMessage.LoggedWith(names)) }
        }
    }

    fun onDeleteHangout(id: String) {
        val hangout = state.value.history.firstOrNull { it.hangout.id == id }?.hangout
        viewModelScope.launch {
            deleteHangout(id)
            local.update { it.copy(recentlyDeleted = hangout, recentlyDeletedGroup = null, message = HangoutsMessage.MemoryDeleted) }
        }
    }

    /** Undo for the last delete: a memory or a circle. */
    fun onUndoDelete() {
        val hangout = local.value.recentlyDeleted
        val group = local.value.recentlyDeletedGroup
        if (hangout == null && group == null) return
        viewModelScope.launch {
            hangout?.let { restoreHangout(it) }
            group?.let { restoreGroup(it) }
            local.update { it.copy(recentlyDeleted = null, recentlyDeletedGroup = null) }
        }
    }

    fun onSelectOccasion(occasion: Occasion) = local.update { it.copy(selectedOccasion = occasion) }

    fun onPlanWith(friendId: FriendId, occasion: Occasion) = local.update { it.copy(planDraft = PlanDraft(friendId, occasion)) }

    /** "Plan" on a circle's card: the editor with that circle picked. */
    fun onPlanWithCircle(circleId: String) = local.update { it.copy(planDraft = PlanDraft(circleId = circleId)) }

    fun onDismissPlan() = local.update { it.copy(planDraft = null) }

    fun onSavePlan(input: PlanInput) {
        val draft = local.value.planDraft ?: return
        if (input.title.isBlank()) return
        val fallback = draft.friendId
        val withSomeone = if (input.friendIds.isEmpty() && input.circleId == null &&
            fallback != null
        ) {
            input.copy(friendIds = listOf(fallback))
        } else {
            input
        }
        if (withSomeone.friendIds.isEmpty() && withSomeone.circleId == null) return
        viewModelScope.launch {
            savePlan.save(null, withSomeone)
            local.update { it.copy(planDraft = null, message = HangoutsMessage.PlanSaved) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null, recentlyDeleted = null, recentlyDeletedGroup = null) }

    private fun Set<FriendId>.toggle(id: FriendId) = if (id in this) this - id else this + id

    private companion object {
        const val UPCOMING_LIMIT = 5
    }
}

private data class Extras(
    val all: List<HangoutWithFriends>,
    val groups: List<Relationships.GroupStatus>,
    val occasions: Map<Occasion, List<OccasionMatch>>,
    val places: PlaceBook,
    val plans: List<Plan>,
)
