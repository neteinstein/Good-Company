package com.neteinstein.goodcompany.feature.settings.ui.screens.activities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetActivitiesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Settings → Activities (F3). [enabled] is what the log sheet offers, in the user's order; [disabled] are the rest,
 * in the default order, shown below them.
 */
data class ActivitiesUiState(
    val enabled: List<HangoutActivity> = HangoutActivity.entries,
    val disabled: List<HangoutActivity> = emptyList(),
) {
    /** The last one on can't be turned off. */
    val canTurnOff: Boolean get() = enabled.size > 1
}

class ActivitiesViewModel(observeSettings: ObserveSettingsUseCase, private val setActivities: SetActivitiesUseCase) : ViewModel() {
    val state: StateFlow<ActivitiesUiState> = observeSettings()
        .map { s -> ActivitiesUiState(s.activities, HangoutActivity.entries - s.activities.toSet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivitiesUiState())

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
