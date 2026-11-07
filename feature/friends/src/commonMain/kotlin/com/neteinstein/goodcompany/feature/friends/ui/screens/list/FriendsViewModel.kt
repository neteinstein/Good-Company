package com.neteinstein.goodcompany.feature.friends.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.AddFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAgendaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMeetupStatusUseCase
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateFriendUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class FriendFilter { CLOSE, ALL }

enum class Greeting(val text: String) { MORNING("Good morning"), AFTERNOON("Good afternoon"), EVENING("Good evening") }

data class FriendsUiState(
    val friends: List<FriendSummary> = emptyList(),
    /** [friends] grouped by first letter for the A–Z list. */
    val sections: List<Pair<Char, List<FriendSummary>>> = emptyList(),
    val closeFriends: List<Friend> = emptyList(),
    /** Agenda items in the next two weeks — the home "Coming up" carousel. */
    val upcoming: List<AgendaItem> = emptyList(),
    val catchUp: List<Relationships.MeetupStatus> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val filter: FriendFilter = FriendFilter.CLOSE,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val greeting: Greeting = Greeting.MORNING,
    val message: String? = null,
    val now: Instant = Instant.DISTANT_PAST,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
) {
    val isSearching: Boolean get() = query.isNotBlank()
    val weekSummary: String?
        get() {
            val week = upcoming.filter { it.date <= today.plus(6, DateTimeUnit.DAY) }
            val birthdays = week.count { it is AgendaItem.FriendBirthday || it is AgendaItem.KidBirthday }
            val moments = week.count { it is AgendaItem.FriendEvent }
            val parts = listOfNotNull(
                birthdays.takeIf { it > 0 }?.let { if (it == 1) "1 birthday" else "$it birthdays" },
                moments.takeIf { it > 0 }?.let { if (it == 1) "1 big day" else "$it big days" },
                catchUp.size.takeIf { it > 0 }?.let { "$it to catch up with" },
            )
            return if (parts.isEmpty()) null else parts.joinToString(" · ") + " this week"
        }
}

class FriendsViewModel(
    observeFriends: ObserveFriendsUseCase,
    observeAgenda: ObserveAgendaUseCase,
    observeMeetups: ObserveMeetupStatusUseCase,
    private val syncContacts: SyncContactsUseCase,
    private val addFriend: AddFriendUseCase,
    private val updateFriend: UpdateFriendUseCase,
    private val checkIn: CheckInUseCase,
    private val loadSampleData: LoadSampleDataUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(FriendsUiState())

    val state: StateFlow<FriendsUiState> = combine(observeFriends(), observeAgenda(), observeMeetups(), local) { all, agenda, meetups, ui ->
        val today = clock.today()
        val q = ui.query.trim().lowercase()
        // First run: nobody marked as close yet → show everyone instead of an empty list.
        val filter = if (ui.filter == FriendFilter.CLOSE && all.none { it.friend.isCloseFriend }) FriendFilter.ALL else ui.filter
        val visible = all
            .filter { q.isNotEmpty() || filter == FriendFilter.ALL || it.friend.isCloseFriend }
            .filter { q.isEmpty() || it.friend.name.lowercase().contains(q) || it.friend.likes.any { l -> l.lowercase().contains(q) } }
            .sortedBy { it.friend.name.lowercase() }
        val hour = clock.now().toLocalDateTime(clock.timeZone).hour
        ui.copy(
            friends = visible,
            sections = visible.groupBy { it.friend.name.first().uppercaseChar().takeIf(Char::isLetter) ?: '#' }.toList(),
            closeFriends = all.filter { it.friend.isCloseFriend }.map { it.friend },
            upcoming = agenda.filter { it !is AgendaItem.MeetupOverdue && it.date <= today.plus(UPCOMING_DAYS, DateTimeUnit.DAY) }.take(8),
            catchUp = meetups.filter { it.isOverdue(today) },
            totalCount = all.size,
            isLoading = false,
            filter = filter,
            greeting = when (hour) {
                in 5..11 -> Greeting.MORNING
                in 12..17 -> Greeting.AFTERNOON
                else -> Greeting.EVENING
            },
            now = clock.now(),
            today = today,
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsUiState())

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun onFilterChange(filter: FriendFilter) = local.update { it.copy(filter = filter) }

    fun onToggleClose(id: FriendId) {
        viewModelScope.launch { updateFriend(id) { it.copy(isCloseFriend = !it.isCloseFriend) } }
    }

    /** "Met them" straight from the catch-up carousel. */
    fun onMetToday(id: FriendId) {
        viewModelScope.launch {
            checkIn(listOf(id))
            val name = state.value.catchUp.firstOrNull { it.friend.id == id }?.friend?.name?.substringBefore(' ')
            local.update { it.copy(message = "Logged time with ${name ?: "your friend"} ✨") }
        }
    }

    /** Called after the contacts permission prompt resolves. */
    fun onContactsPermissionResult(granted: Boolean) {
        if (!granted) {
            local.update { it.copy(message = "Without contacts access you can still add friends by hand.") }
            return
        }
        viewModelScope.launch {
            local.update { it.copy(isSyncing = true) }
            val result = runCatching { syncContacts() }
            local.update {
                it.copy(
                    isSyncing = false,
                    message = result.fold(
                        { r -> "Synced ${r.contacts} contacts" + if (r.logEntries > 0) " · ${r.logEntries} calls matched" else "" },
                        { e -> "Sync failed: ${e.message}" },
                    ),
                )
            }
        }
    }

    fun onAddFriend(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            addFriend(name)
            local.update { it.copy(message = "Added ${name.trim()}") }
        }
    }

    fun onExploreSample() {
        viewModelScope.launch {
            loadSampleData()
            local.update { it.copy(message = "Sample friends added — remove them anytime in Settings") }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private companion object {
        const val UPCOMING_DAYS = 14
    }
}
