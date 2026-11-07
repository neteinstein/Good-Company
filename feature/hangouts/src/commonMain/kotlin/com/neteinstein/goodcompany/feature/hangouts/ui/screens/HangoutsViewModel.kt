package com.neteinstein.goodcompany.feature.hangouts.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Occasion
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
import com.neteinstein.goodcompany.core.domain.usecase.OccasionMatch
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.RestoreHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SavePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeCatchUpUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.YearInReviewBuilder
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
) {
    val canSave: Boolean get() = selected.isNotEmpty()
}

/** The group editor dialog. id == null → new group. */
data class GroupDraft(
    val id: String? = null,
    val name: String = "",
    val emoji: String = "👥",
    val members: Set<FriendId> = emptySet(),
    val meetEveryDays: Int? = 30,
) {
    val canSave: Boolean get() = name.isNotBlank() && members.size >= 2
}

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface HangoutsMessage {
    data object LocationDenied : HangoutsMessage

    data object LocationUnavailable : HangoutsMessage

    data class LoggedWith(val names: List<String>) : HangoutsMessage

    data object MemoryDeleted : HangoutsMessage

    data class GroupSaved(val name: String) : HangoutsMessage

    data class GroupDeleted(val name: String) : HangoutsMessage

    data class GroupSnoozed(val name: String, val until: LocalDate) : HangoutsMessage

    data object PlanSaved : HangoutsMessage
}

/** The plan editor opened from "Who's free for…?". */
data class PlanDraft(val friendId: FriendId, val occasion: Occasion)

data class HangoutsUiState(
    /** Close friends first — the check-in picker and the "with…" filter. */
    val friends: List<Friend> = emptyList(),
    val history: List<HangoutWithFriends> = emptyList(),
    val filter: Set<FriendId> = emptySet(),
    val overdue: List<Relationships.MeetupStatus> = emptyList(),
    val upcoming: List<Relationships.MeetupStatus> = emptyList(),
    val draft: CheckInDraft? = null,
    val groups: List<Relationships.GroupStatus> = emptyList(),
    val groupDraft: GroupDraft? = null,
    /** The year the "Your year in friendship" card opens, or null when there's nothing to show yet. */
    val reviewYear: Int? = null,
    val stats: Relationships.MonthStats = Relationships.MonthStats(0, 0, null),
    /** Hangout deleted a moment ago, offered for undo. */
    val recentlyDeleted: Hangout? = null,
    /** Increments on every check-in so the UI can celebrate. */
    val celebrate: Int = 0,
    val message: HangoutsMessage? = null,
    /** "Who's free for…?": friends per occasion (only occasions someone is tagged with). */
    val occasions: Map<Occasion, List<OccasionMatch>> = emptyMap(),
    val selectedOccasion: Occasion? = null,
    val planDraft: PlanDraft? = null,
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
    private val savePlan: SavePlanUseCase,
    private val saveGroup: SaveGroupUseCase,
    private val deleteGroup: DeleteGroupUseCase,
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
        combine(observeAllHangouts(flowOf(emptySet())), observeGroups(), observeOccasions()) { all, groups, occ -> Triple(all, groups, occ) },
        local,
    ) { friends, history, meetups, (all, groups, occasions), ui ->
        val today = clock.today()
        val hangouts = all.map { it.hangout }
        val reviewYear = YearInReviewBuilder.defaultYear(today, hangouts, clock.timeZone)
        ui.copy(
            groups = groups,
            reviewYear = reviewYear.takeIf { y -> hangouts.any { it.at.toLocalDateTime(clock.timeZone).year == y } },
            stats = Relationships.monthStats(all.map { it.hangout }, today, clock.timeZone),
            friends = friends.map { it.friend },
            history = history,
            overdue = meetups.filter { it.isOverdue(today) },
            upcoming = meetups.filterNot { it.isOverdue(today) }.take(UPCOMING_LIMIT),
            occasions = occasions,
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

    /** Tapping a saved group in the check-in sheet adds all its members. */
    fun onPickDraftGroup(groupId: String) {
        val members = state.value.groups.firstOrNull { it.group.id == groupId }?.group?.memberIds ?: return
        editDraft { it.copy(selected = if (it.selected.containsAll(members)) it.selected - members.toSet() else it.selected + members) }
    }

    /** "We met" on a group card: opens the check-in sheet with every member selected. */
    fun onCheckInGroup(groupId: String) {
        val members = state.value.groups.firstOrNull { it.group.id == groupId }?.group?.memberIds ?: return
        local.update { it.copy(draft = CheckInDraft(selected = members.toSet())) }
    }

    fun onNewGroup() = local.update { it.copy(groupDraft = GroupDraft()) }

    fun onEditGroup(groupId: String) {
        val g = state.value.groups.firstOrNull { it.group.id == groupId }?.group ?: return
        local.update { it.copy(groupDraft = GroupDraft(g.id, g.name, g.emoji, g.memberIds.toSet(), g.meetEveryDays)) }
    }

    fun onDismissGroup() = local.update { it.copy(groupDraft = null) }

    fun onGroupDraftChange(draft: GroupDraft) = local.update { it.copy(groupDraft = draft) }

    fun onSaveGroup() {
        val draft = local.value.groupDraft ?: return
        if (!draft.canSave) return
        viewModelScope.launch {
            val group = saveGroup(draft.id, draft.name, draft.emoji, draft.members, draft.meetEveryDays)
            local.update { it.copy(groupDraft = null, message = HangoutsMessage.GroupSaved(group.name)) }
        }
    }

    fun onDeleteGroup(groupId: String) {
        val name = state.value.groups.firstOrNull { it.group.id == groupId }?.group?.name ?: return
        viewModelScope.launch {
            deleteGroup(groupId)
            local.update { it.copy(groupDraft = null, message = HangoutsMessage.GroupDeleted(name)) }
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

    fun onClearDraftLocation() = editDraft { it.copy(location = null) }

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
            checkIn(draft.selected, draft.place, draft.location, draft.note)
            val names = state.value.friends.filter { it.id in draft.selected }.map { it.name.substringBefore(' ') }
            local.update { it.copy(draft = null, celebrate = it.celebrate + 1, message = HangoutsMessage.LoggedWith(names)) }
        }
    }

    fun onDeleteHangout(id: String) {
        val hangout = state.value.history.firstOrNull { it.hangout.id == id }?.hangout
        viewModelScope.launch {
            deleteHangout(id)
            local.update { it.copy(recentlyDeleted = hangout, message = HangoutsMessage.MemoryDeleted) }
        }
    }

    fun onUndoDelete() {
        val hangout = local.value.recentlyDeleted ?: return
        viewModelScope.launch {
            restoreHangout(hangout)
            local.update { it.copy(recentlyDeleted = null) }
        }
    }

    fun onSelectOccasion(occasion: Occasion) = local.update { it.copy(selectedOccasion = occasion) }

    fun onPlanWith(friendId: FriendId, occasion: Occasion) = local.update { it.copy(planDraft = PlanDraft(friendId, occasion)) }

    fun onDismissPlan() = local.update { it.copy(planDraft = null) }

    fun onSavePlan(title: String, friendIds: List<FriendId>, date: LocalDate, place: String?) {
        val draft = local.value.planDraft ?: return
        if (title.isBlank()) return
        viewModelScope.launch {
            savePlan(null, title, friendIds.ifEmpty { listOf(draft.friendId) }, date, place)
            local.update { it.copy(planDraft = null, message = HangoutsMessage.PlanSaved) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null, recentlyDeleted = null) }

    private fun Set<FriendId>.toggle(id: FriendId) = if (id in this) this - id else this + id

    private companion object {
        const val UPCOMING_LIMIT = 5
    }
}
