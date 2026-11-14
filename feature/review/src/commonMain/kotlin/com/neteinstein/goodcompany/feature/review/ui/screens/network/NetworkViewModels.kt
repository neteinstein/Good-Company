package com.neteinstein.goodcompany.feature.review.ui.screens.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.usecase.NetworkMap
import com.neteinstein.goodcompany.core.domain.usecase.ObserveNetworkMapUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePeopleDashboardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PeopleDashboard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class NetworkMapUiState(
    /** Null until the first layout is ready. */
    val map: NetworkMap? = null,
    /** The circle shown (null = everyone). */
    val circleId: String? = null,
) {
    /** People shown under the current filter (you are always shown). */
    val visibleIds: Set<String>
        get() = map?.graph?.nodes.orEmpty().filter { circleId == null || circleId in it.circleIds }.mapTo(HashSet()) { it.friend.id.value }
}

/** "Your network" (issue #46): the graph and its layout, computed off the main thread, plus the circle filter. */
class NetworkMapViewModel(observe: ObserveNetworkMapUseCase, layoutDispatcher: CoroutineDispatcher = Dispatchers.Default) : ViewModel() {
    private val filter = MutableStateFlow<String?>(null)

    val state: StateFlow<NetworkMapUiState> = combine(observe().flowOn(layoutDispatcher), filter) { map, circle ->
        NetworkMapUiState(map, circle?.takeIf { id -> map.graph.circles.any { it.id == id } })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetworkMapUiState())

    /** Shows only [id]'s members (null, or the same circle again, = everyone). */
    fun onCircle(id: String?) = filter.update { if (it == id) null else id }
}

data class PeopleDashboardUiState(val dashboard: PeopleDashboard? = null)

/** "Your people" (issue #46): warmth per circle, the trend, who is cooling, meetups per month, never logged. */
class PeopleDashboardViewModel(observe: ObservePeopleDashboardUseCase, dispatcher: CoroutineDispatcher = Dispatchers.Default) : ViewModel() {
    val state: StateFlow<PeopleDashboardUiState> = observe().flowOn(dispatcher)
        .map { PeopleDashboardUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleDashboardUiState())
}
