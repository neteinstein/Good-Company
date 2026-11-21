package com.neteinstein.goodcompany.feature.settings.ui.screens.how

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.usecase.ActivityNameProblem
import com.neteinstein.goodcompany.core.domain.usecase.CustomLogKindsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetLogKindsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface HowOptionsMessage {
    /** Offered with "Undo". */
    data class Removed(val name: String) : HowOptionsMessage

    data class NotSaved(val problem: ActivityNameProblem) : HowOptionsMessage
}

/**
 * Settings → How (issue #152). [enabled] is what the Log popup's "How?" offers, in the user's order; [disabled] are the
 * rest, in the default order, shown below them.
 */
data class HowOptionsUiState(
    val enabled: List<LogKind> = LogKind.DEFAULT_ORDER,
    val disabled: List<LogKind> = emptyList(),
    /** Your own How options, in your order. */
    val custom: List<String> = emptyList(),
    val message: HowOptionsMessage? = null,
) {
    /** The last built-in one on can't be turned off. */
    val canTurnOff: Boolean get() = enabled.size > 1
}

/**
 * Settings → Your data → How (issue #152), next to Activities and built the same way: the built-in options on or off
 * and in order, and your own, which can be added, renamed, moved and removed (with Undo). Past entries keep their name.
 */
class HowOptionsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val setLogKinds: SetLogKindsUseCase,
    private val customLogKinds: CustomLogKindsUseCase,
) : ViewModel() {
    private data class Local(val message: HowOptionsMessage? = null, val removedAt: Int = -1)

    private val local = MutableStateFlow(Local())

    val state: StateFlow<HowOptionsUiState> = combine(observeSettings(), local) { s, ui ->
        HowOptionsUiState(s.logKinds, LogKind.DEFAULT_ORDER - s.logKinds.toSet(), s.customLogKinds, ui.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HowOptionsUiState())

    fun onAddCustom(name: String) {
        viewModelScope.launch { customLogKinds.add(name)?.let(::notSaved) }
    }

    fun onRenameCustom(from: String, to: String) {
        viewModelScope.launch { customLogKinds.rename(from, to)?.let(::notSaved) }
    }

    fun onRemoveCustom(name: String) {
        viewModelScope.launch {
            val at = customLogKinds.remove(name)
            if (at >= 0) local.update { it.copy(message = HowOptionsMessage.Removed(name), removedAt = at) }
        }
    }

    fun onUndoRemove() {
        val removed = local.value.message as? HowOptionsMessage.Removed ?: return
        val at = local.value.removedAt
        viewModelScope.launch { customLogKinds.restore(removed.name, at) }
    }

    fun onMoveCustom(from: Int, to: Int) {
        viewModelScope.launch { customLogKinds.move(from, to) }
    }

    fun onMessageShown() = local.update { Local() }

    private fun notSaved(problem: ActivityNameProblem) = local.update { it.copy(message = HowOptionsMessage.NotSaved(problem)) }

    /** On: it joins the end of the list. Off: it leaves it (never the last one). */
    fun onToggle(kind: LogKind) {
        val enabled = state.value.enabled
        save(if (kind in enabled) enabled - kind else enabled + kind)
    }

    /** Moves a built-in option that's on from [from] to [to] (positions in [HowOptionsUiState.enabled]). */
    fun onMove(from: Int, to: Int) {
        val enabled = state.value.enabled
        if (from !in enabled.indices || to !in enabled.indices || from == to) return
        save(enabled.toMutableList().apply { add(to, removeAt(from)) })
    }

    private fun save(kinds: List<LogKind>) {
        if (kinds.isEmpty()) return
        viewModelScope.launch { setLogKinds(kinds) }
    }
}
