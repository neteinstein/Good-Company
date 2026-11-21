package com.neteinstein.goodcompany.feature.settings.ui.screens.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.usecase.ActivityNameProblem
import com.neteinstein.goodcompany.core.domain.usecase.CustomActivitiesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetActivitiesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface ActivitiesMessage {
    /** Offered with "Undo". */
    data class Removed(val name: String) : ActivitiesMessage

    data class NotSaved(val problem: ActivityNameProblem) : ActivitiesMessage
}

/**
 * Settings → Activities (F3). [enabled] is what the Log popup offers, in the user's order; [disabled] are the rest,
 * in the default order, shown below them.
 */
data class ActivitiesUiState(
    val enabled: List<HangoutActivity> = HangoutActivity.entries,
    val disabled: List<HangoutActivity> = emptyList(),
    /** Your own activities (issue #121), in your order. */
    val custom: List<String> = emptyList(),
    val message: ActivitiesMessage? = null,
) {
    /** The last one on can't be turned off. */
    val canTurnOff: Boolean get() = enabled.size > 1
}

/**
 * Settings → Your data → Activities (F3, issue #121): the built-in ones on or off and in order, and your own, which
 * can be added, renamed, moved and removed (with Undo). Past meetups and plans keep the name they were given.
 */
class ActivitiesViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val setActivities: SetActivitiesUseCase,
    private val customActivities: CustomActivitiesUseCase,
) : ViewModel() {
    private data class Local(val message: ActivitiesMessage? = null, val removedAt: Int = -1)

    private val local = MutableStateFlow(Local())

    val state: StateFlow<ActivitiesUiState> = combine(observeSettings(), local) { s, ui ->
        ActivitiesUiState(s.activities, HangoutActivity.entries - s.activities.toSet(), s.customActivities, ui.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivitiesUiState())

    fun onAddCustom(name: String) {
        viewModelScope.launch { customActivities.add(name)?.let(::notSaved) }
    }

    fun onRenameCustom(from: String, to: String) {
        viewModelScope.launch { customActivities.rename(from, to)?.let(::notSaved) }
    }

    fun onRemoveCustom(name: String) {
        viewModelScope.launch {
            val at = customActivities.remove(name)
            if (at >= 0) local.update { it.copy(message = ActivitiesMessage.Removed(name), removedAt = at) }
        }
    }

    fun onUndoRemove() {
        val removed = local.value.message as? ActivitiesMessage.Removed ?: return
        val at = local.value.removedAt
        viewModelScope.launch { customActivities.restore(removed.name, at) }
    }

    fun onMoveCustom(from: Int, to: Int) {
        viewModelScope.launch { customActivities.move(from, to) }
    }

    fun onMessageShown() = local.update { Local() }

    private fun notSaved(problem: ActivityNameProblem) = local.update { it.copy(message = ActivitiesMessage.NotSaved(problem)) }

    /** On: it joins the end of the list. Off: it leaves it (never the last one). */
    fun onToggle(activity: HangoutActivity) {
        val enabled = state.value.enabled
        val next = if (activity in enabled) enabled - activity else enabled + activity
        save(next)
    }

    /** Moves an activity that's on from [from] to [to] (positions in [ActivitiesUiState.enabled]). */
    fun onMove(from: Int, to: Int) {
        val enabled = state.value.enabled
        if (from !in enabled.indices || to !in enabled.indices || from == to) return
        save(enabled.toMutableList().apply { add(to, removeAt(from)) })
    }

    private fun save(activities: List<HangoutActivity>) {
        if (activities.isEmpty()) return
        viewModelScope.launch { setActivities(activities) }
    }
}
