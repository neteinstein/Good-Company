package com.neteinstein.goodcompany.feature.friends.ui.screens.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.DeleteTimelineEntryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportChatUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveTimelineUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreTimelineEntryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.Timeline
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.domain.usecase.TimelineFilter
import com.neteinstein.goodcompany.core.domain.usecase.TimelineMonth
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** One-off feedback for the timeline's snackbar; the screen turns it into localised text. */
sealed interface FriendTimelineMessage {
    /** Offered with "Undo". */
    data object Removed : FriendTimelineMessage

    data class ChatImported(val messages: Int, val sender: String?) : FriendTimelineMessage

    data object ChatInvalid : FriendTimelineMessage
}

data class FriendTimelineUiState(
    val friend: Friend? = null,
    val isLoading: Boolean = true,
    val filter: TimelineFilter = TimelineFilter.ALL,
    /** The filtered entries under month headings, newest first. */
    val months: List<TimelineMonth> = emptyList(),
    /** Whether the friend has any entry at all (an empty filter says something different). */
    val hasEntries: Boolean = false,
    val message: FriendTimelineMessage? = null,
    /** Entry just deleted, offered for undo. */
    val undo: TimelineEntry? = null,
    val zone: TimeZone = TimeZone.UTC,
    val today: LocalDate = LocalDate(2000, 1, 1),
)

/** "Ana's timeline": every entry, filter chips, delete with Undo, and the WhatsApp import in the overflow menu. */
class FriendTimelineViewModel(
    private val friendId: FriendId,
    observeTimeline: ObserveTimelineUseCase,
    observeFriends: ObserveFriendsUseCase,
    private val deleteEntry: DeleteTimelineEntryUseCase,
    private val restoreEntry: RestoreTimelineEntryUseCase,
    private val importChat: ImportChatUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(FriendTimelineUiState())

    val state: StateFlow<FriendTimelineUiState> = combine(
        observeTimeline(friendId),
        observeFriends().map { list -> list.firstOrNull { it.friend.id == friendId }?.friend },
        local,
    ) { entries, friend, ui ->
        ui.copy(
            friend = friend,
            isLoading = false,
            months = Timeline.byMonth(Timeline.filter(entries, ui.filter), clock.timeZone),
            hasEntries = entries.isNotEmpty(),
            zone = clock.timeZone,
            today = clock.today(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendTimelineUiState())

    fun onFilter(filter: TimelineFilter) = local.update { it.copy(filter = filter) }

    fun onDelete(entry: TimelineEntry) {
        if (!entry.deletable) return
        viewModelScope.launch {
            deleteEntry(entry)
            local.update { it.copy(undo = entry, message = FriendTimelineMessage.Removed) }
        }
    }

    fun onUndoDelete() {
        val entry = local.value.undo ?: return
        viewModelScope.launch {
            restoreEntry(entry)
            local.update { it.copy(undo = null) }
        }
    }

    fun onChatPicked(text: String?) {
        if (text.isNullOrBlank()) return
        viewModelScope.launch {
            val result = importChat(friendId, text)
            val message = if (result.messages == 0) {
                FriendTimelineMessage.ChatInvalid
            } else {
                FriendTimelineMessage.ChatImported(result.messages, result.friendSender)
            }
            local.update { it.copy(message = message) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null, undo = null) }
}
