package com.neteinstein.goodcompany.feature.hangouts.ui.screens.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.DeleteGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.NextCircleColorUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveGroupUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What's typed or picked so far. */
private data class CircleDraft(
    val name: String = "",
    val emoji: String = SaveGroupUseCase.DEFAULT_EMOJI,
    val color: CircleColor = CircleColor.PURPLE,
    val remind: Boolean = false,
    val days: Int = CircleEditorUiState.DEFAULT_DAYS,
    val memberIds: List<FriendId> = emptyList(),
)

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface CircleEditorMessage {
    /** Deleted, with Undo; the editor closes when the snackbar goes away without it. */
    data class Deleted(val name: String) : CircleEditorMessage
}

data class CircleEditorUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = true,
    val name: String = "",
    val emoji: String = SaveGroupUseCase.DEFAULT_EMOJI,
    val color: CircleColor = CircleColor.PURPLE,
    /** "Remind me when we haven't met" (off by default for a new circle). */
    val remind: Boolean = false,
    val days: Int = DEFAULT_DAYS,
    /** Members in the order they were added; each one's closeness is shown, never edited, here. */
    val members: List<Friend> = emptyList(),
    /** Everyone who can be added, A–Z. */
    val everyone: List<Friend> = emptyList(),
    val saving: Boolean = false,
    /** Deleted a moment ago (Undo is on offer). */
    val deleted: FriendGroup? = null,
    /** Saved or deleted for good: the screen closes. */
    val closed: Boolean = false,
    val message: CircleEditorMessage? = null,
) {
    val canSave: Boolean get() = name.isNotBlank() && members.isNotEmpty() && !saving && deleted == null && !isLoading

    companion object {
        const val DEFAULT_DAYS = 30
        val RHYTHMS = listOf(7, 14, 30, 60, 90)
    }
}

/**
 * The circle editor behind `CircleEditorRoute(id)` (decision D1): emoji, name, colour, the optional reminder and the
 * members. [groupId] null creates a circle, starting on the next colour in turn and with the reminder off.
 */
class CircleEditorViewModel(
    private val groupId: String?,
    observeFriends: ObserveFriendsUseCase,
    private val observeGroups: ObserveGroupsUseCase,
    private val saveGroup: SaveGroupUseCase,
    private val deleteGroup: DeleteGroupUseCase,
    private val restoreGroup: RestoreGroupUseCase,
    private val nextColor: NextCircleColorUseCase,
) : ViewModel() {
    private val draft = MutableStateFlow(CircleDraft())
    private val local = MutableStateFlow(CircleEditorUiState(isNew = groupId == null))

    init {
        viewModelScope.launch {
            val existing = groupId?.let { id -> observeGroups().first().firstOrNull { it.group.id == id }?.group }
            draft.value = existing?.let {
                CircleDraft(
                    name = it.name,
                    emoji = it.emoji,
                    color = it.color,
                    remind = it.meetEveryDays != null,
                    days = it.meetEveryDays ?: CircleEditorUiState.DEFAULT_DAYS,
                    memberIds = it.memberIds,
                )
            } ?: CircleDraft(color = nextColor())
            // A circle that's gone (deleted elsewhere) opens as a new one.
            local.update { it.copy(isNew = existing == null, isLoading = false) }
        }
    }

    val state: StateFlow<CircleEditorUiState> = combine(observeFriends(), draft, local) { friends, d, ui ->
        val byId = friends.associateBy { it.friend.id }
        ui.copy(
            name = d.name,
            emoji = d.emoji,
            color = d.color,
            remind = d.remind,
            days = d.days,
            members = d.memberIds.mapNotNull { byId[it]?.friend },
            everyone = friends.map { it.friend }.sortedBy { it.displayName.lowercase() },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun onName(name: String) = draft.update { it.copy(name = name) }

    /** Keeps the first emoji (a flag or a family is several code points, so a few chars are allowed). */
    fun onEmoji(emoji: String) = draft.update { it.copy(emoji = emoji.trim().take(MAX_EMOJI_CHARS)) }

    fun onColor(color: CircleColor) = draft.update { it.copy(color = color) }

    fun onRemind(on: Boolean) = draft.update { it.copy(remind = on) }

    fun onDays(days: Int) = draft.update { it.copy(days = days.coerceAtLeast(1), remind = true) }

    /** "+ Add": the picker's result is the whole member list (unticked people leave). */
    fun onMembers(ids: List<FriendId>) = draft.update { it.copy(memberIds = ids.distinct()) }

    fun onRemoveMember(id: FriendId) = draft.update { it.copy(memberIds = it.memberIds - id) }

    fun onSave() {
        val s = state.value
        if (!s.canSave) return
        local.update { it.copy(saving = true) }
        val d = draft.value
        viewModelScope.launch {
            saveGroup(groupId.takeUnless { s.isNew }, d.name, d.emoji, d.color, d.memberIds, d.days.takeIf { d.remind })
            local.update { it.copy(saving = false, closed = true) }
        }
    }

    fun onDelete() {
        val id = groupId ?: return
        if (state.value.isNew) return
        viewModelScope.launch {
            val removed = deleteGroup(id) ?: return@launch
            local.update { it.copy(deleted = removed, message = CircleEditorMessage.Deleted(removed.name)) }
        }
    }

    fun onUndoDelete() {
        val group = local.value.deleted ?: return
        viewModelScope.launch {
            restoreGroup(group)
            local.update { it.copy(deleted = null, message = null) }
        }
    }

    /** The snackbar went away: after a delete without Undo, the editor closes. */
    fun onMessageShown() = local.update { it.copy(message = null, closed = it.closed || it.deleted != null) }

    private companion object {
        const val MAX_EMOJI_CHARS = 8
    }
}
