package com.neteinstein.goodcompany.feature.friends.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.AddFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

enum class FriendFilter { CLOSE, ALL }

data class FriendsUiState(
    val friends: List<FriendSummary> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val filter: FriendFilter = FriendFilter.CLOSE,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val message: String? = null,
    val now: Instant = Instant.DISTANT_PAST,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
)

class FriendsViewModel(
    observeFriends: ObserveFriendsUseCase,
    private val syncContacts: SyncContactsUseCase,
    private val addFriend: AddFriendUseCase,
    private val updateFriend: UpdateFriendUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(FriendsUiState())

    val state: StateFlow<FriendsUiState> = combine(observeFriends(), local) { all, ui ->
        val q = ui.query.trim().lowercase()
        val visible = all
            .filter { ui.filter == FriendFilter.ALL || it.friend.isCloseFriend }
            .filter { q.isEmpty() || it.friend.name.lowercase().contains(q) || it.friend.likes.any { l -> l.lowercase().contains(q) } }
        ui.copy(
            friends = visible,
            totalCount = all.size,
            isLoading = false,
            // First run: nobody marked as close yet → show everyone instead of an empty list.
            filter = if (ui.filter == FriendFilter.CLOSE && all.none { it.friend.isCloseFriend }) FriendFilter.ALL else ui.filter,
            now = clock.now(),
            today = clock.today(),
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsUiState())

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun onFilterChange(filter: FriendFilter) = local.update { it.copy(filter = filter) }

    fun onToggleClose(id: FriendId) {
        viewModelScope.launch { updateFriend(id) { it.copy(isCloseFriend = !it.isCloseFriend) } }
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

    fun onMessageShown() = local.update { it.copy(message = null) }
}
