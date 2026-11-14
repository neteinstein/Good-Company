package com.neteinstein.goodcompany.feature.settings.ui.screens.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.usecase.DeleteTagUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KeepTagSpellingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveTagDuplicatesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveTagsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemovedTag
import com.neteinstein.goodcompany.core.domain.usecase.RenameTagUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreTagUseCase
import com.neteinstein.goodcompany.core.domain.usecase.Tag
import com.neteinstein.goodcompany.core.domain.usecase.Tags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface TagsMessage {
    data class Renamed(val to: String) : TagsMessage

    data class Merged(val into: String) : TagsMessage

    /** Offered with "Undo". */
    data class Deleted(val name: String) : TagsMessage
}

data class TagsUiState(
    /** Every tag, A–Z. */
    val tags: List<Tag> = emptyList(),
    /** Spellings that look like the same tag ("Sushi" · "sushi"), offered for merging. */
    val duplicates: List<List<Tag>> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val message: TagsMessage? = null,
    /** The tag just deleted, offered for undo. */
    val undo: RemovedTag? = null,
) {
    /** [tags] matching [query], ignoring case and accents. */
    val shown: List<Tag>
        get() = Tags.normalize(query).let { q -> if (q.isEmpty()) tags else tags.filter { q in it.key } }
}

/** Settings → Tags (F16): rename, merge and delete tags for everyone at once. */
class TagsViewModel(
    observeTags: ObserveTagsUseCase,
    observeDuplicates: ObserveTagDuplicatesUseCase,
    private val renameTag: RenameTagUseCase,
    private val deleteTag: DeleteTagUseCase,
    private val restoreTag: RestoreTagUseCase,
    private val keepSpellings: KeepTagSpellingsUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(TagsUiState())

    val state: StateFlow<TagsUiState> = combine(observeTags(), observeDuplicates(), local) { tags, duplicates, ui ->
        ui.copy(
            tags = tags.sortedWith(compareBy<Tag> { it.key }.thenBy { it.name }),
            duplicates = duplicates,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState())

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    /**
     * Renames [tag] to [newName]. When [newName] is another tag's spelling (ignoring case and accents) the two merge
     * into that one.
     */
    fun onRename(tag: Tag, newName: String) {
        val typed = newName.trim()
        if (typed.isEmpty() || typed == tag.name) return
        val others = state.value.tags.filter { it.name != tag.name }
        val existing = others.firstOrNull { it.name == typed } ?: others.firstOrNull { it.key == Tags.normalize(typed) }
        val target = existing?.name ?: typed
        viewModelScope.launch {
            renameTag(setOf(tag.name), target)
            local.update { it.copy(message = if (existing != null) TagsMessage.Merged(target) else TagsMessage.Renamed(target)) }
        }
    }

    /** Merges a group of spellings into the most used one (the first). */
    fun onMerge(group: List<Tag>) {
        val into = group.firstOrNull()?.name ?: return
        viewModelScope.launch {
            renameTag(group.mapTo(HashSet()) { it.name }, into)
            local.update { it.copy(message = TagsMessage.Merged(into)) }
        }
    }

    fun onKeepBoth(group: List<Tag>) {
        viewModelScope.launch { keepSpellings(group.map { it.name }) }
    }

    fun onDelete(tag: Tag) {
        viewModelScope.launch {
            val removed = deleteTag(tag.name)
            local.update { it.copy(message = TagsMessage.Deleted(tag.name), undo = removed) }
        }
    }

    fun onUndoDelete() {
        val removed = local.value.undo ?: return
        local.update { it.copy(undo = null) }
        viewModelScope.launch { restoreTag(removed) }
    }

    fun onMessageShown() = local.update { it.copy(message = null, undo = null) }
}
