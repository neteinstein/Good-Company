package com.neteinstein.goodcompany.feature.ideas.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.usecase.DismissIdeaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RunWeeklyIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.WeeklyIdeasOutcome
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

data class IdeaCard(val idea: Idea, val friends: List<Friend>)

data class IdeasUiState(
    val ideas: List<IdeaCard> = emptyList(),
    val hasAiKey: Boolean = false,
    val weeklyEnabled: Boolean = true,
    val optedInFriends: Int = 0,
    val lastRunAt: Instant? = null,
    val isRunning: Boolean = false,
    val isLoading: Boolean = true,
    val message: String? = null,
    val now: Instant = Instant.DISTANT_PAST,
    val zone: TimeZone = TimeZone.UTC,
)

class IdeasViewModel(
    observeIdeas: ObserveIdeasUseCase,
    observeFriends: ObserveFriendsUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val runWeeklyIdeas: RunWeeklyIdeasUseCase,
    private val dismissIdea: DismissIdeaUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(IdeasUiState())

    val state: StateFlow<IdeasUiState> = combine(observeIdeas(), observeFriends(), observeSettings(), local) { ideas, friends, settings, ui ->
        val byId = friends.associate { it.friend.id to it.friend }
        ui.copy(
            ideas = ideas.map { IdeaCard(it, it.friendIds.mapNotNull(byId::get)) },
            hasAiKey = settings.hasAiKey,
            weeklyEnabled = settings.weeklyIdeasEnabled,
            optedInFriends = friends.count { it.friend.suggestEvents },
            lastRunAt = settings.lastIdeasRunAt,
            isLoading = false,
            now = clock.now(),
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IdeasUiState())

    fun onRunNow() {
        if (local.value.isRunning) return
        viewModelScope.launch {
            local.update { it.copy(isRunning = true) }
            val outcome = runWeeklyIdeas(force = true)
            local.update { it.copy(isRunning = false, message = outcome.toMessage()) }
        }
    }

    fun onWeeklyToggle(enabled: Boolean) {
        viewModelScope.launch { updateSettings { it.copy(weeklyIdeasEnabled = enabled) } }
    }

    fun onDismiss(id: String) {
        viewModelScope.launch { dismissIdea(id) }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private fun WeeklyIdeasOutcome.toMessage(): String = when (this) {
        is WeeklyIdeasOutcome.Done -> if (ideas == 0) "No great matches this time." else "$ideas new ideas ✨"
        is WeeklyIdeasOutcome.Failed -> "Couldn't fetch ideas: ${error.message}"
        is WeeklyIdeasOutcome.Skipped -> when (reason) {
            WeeklyIdeasOutcome.Reason.NO_API_KEY -> "Add your API key in Settings first."
            WeeklyIdeasOutcome.Reason.NO_FRIENDS_OPTED_IN -> "Turn on “Weekly event ideas” for at least one friend."
            WeeklyIdeasOutcome.Reason.DISABLED, WeeklyIdeasOutcome.Reason.NOT_DUE -> "Nothing to do right now."
        }
    }
}
