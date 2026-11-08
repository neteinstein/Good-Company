package com.neteinstein.goodcompany.feature.friends.ui.screens.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.CatchUpStory
import com.neteinstein.goodcompany.core.domain.usecase.ObserveCatchUpStoryUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

data class CatchUpStoryUiState(
    val story: CatchUpStory? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    /** The slide on screen. */
    val index: Int = 0,
    /** Held down (long-press) — the timer stops. */
    val paused: Boolean = false,
    /** Tapped past the last slide: the screen closes. */
    val finished: Boolean = false,
    val now: Instant = Instant.DISTANT_PAST,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
) {
    val slideCount: Int get() = story?.slides?.size ?: 0
    val isLast: Boolean get() = index >= slideCount - 1
}

/** A friend's catch-up story: tap through what's new and what to ask about. */
class CatchUpStoryViewModel(friendId: FriendId, observeStory: ObserveCatchUpStoryUseCase, private val clock: AppClock) : ViewModel() {
    private val local = MutableStateFlow(CatchUpStoryUiState())

    val state: StateFlow<CatchUpStoryUiState> = combine(observeStory(friendId), local) { story, ui ->
        val count = story?.slides?.size ?: 0
        ui.copy(
            story = story,
            isLoading = false,
            notFound = story == null,
            index = ui.index.coerceIn(0, (count - 1).coerceAtLeast(0)),
            now = clock.now(),
            today = clock.today(),
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatchUpStoryUiState())

    fun onNext() {
        val count = state.value.slideCount
        local.update { if (it.index >= count - 1) it.copy(finished = true) else it.copy(index = it.index + 1) }
    }

    fun onPrevious() = local.update { it.copy(index = (it.index - 1).coerceAtLeast(0)) }

    fun onPause(paused: Boolean) = local.update { it.copy(paused = paused) }
}
