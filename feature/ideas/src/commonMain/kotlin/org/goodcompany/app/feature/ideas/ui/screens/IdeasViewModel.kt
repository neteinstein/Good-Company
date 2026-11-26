package org.goodcompany.app.feature.ideas.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Idea
import org.goodcompany.app.core.domain.model.IdeaKind
import org.goodcompany.app.core.domain.usecase.DismissIdeaUseCase
import org.goodcompany.app.core.domain.usecase.MarkIdeaProposedUseCase
import org.goodcompany.app.core.domain.usecase.ObserveFriendsUseCase
import org.goodcompany.app.core.domain.usecase.ObserveIdeasUseCase
import org.goodcompany.app.core.domain.usecase.ObserveSettingsUseCase
import org.goodcompany.app.core.domain.usecase.PlanInput
import org.goodcompany.app.core.domain.usecase.RunWeeklyIdeasUseCase
import org.goodcompany.app.core.domain.usecase.SavePlanUseCase
import org.goodcompany.app.core.domain.usecase.UpdateSettingsUseCase
import org.goodcompany.app.core.domain.usecase.WeeklyIdeasOutcome
import org.goodcompany.app.core.domain.util.AppClock
import kotlin.time.Instant

data class IdeaCard(val idea: Idea, val friends: List<Friend>)

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface IdeasMessage {
    data class Found(val count: Int) : IdeasMessage

    data class Failed(val error: Throwable) : IdeasMessage

    data class Skipped(val reason: WeeklyIdeasOutcome.Reason) : IdeasMessage

    data object PlanSaved : IdeasMessage
}

data class IdeasUiState(
    val ideas: List<IdeaCard> = emptyList(),
    val kindFilter: IdeaKind? = null,
    /** AI is on and a key is saved (F1): every AI button checks this. */
    val hasAiKey: Boolean = false,
    val weeklyEnabled: Boolean = true,
    val optedInFriends: Int = 0,
    val lastRunAt: Instant? = null,
    val isRunning: Boolean = false,
    val isLoading: Boolean = true,
    val message: IdeasMessage? = null,
    /** The idea whose "Propose to…" sheet is open. */
    val proposing: IdeaCard? = null,
    /** The idea being turned into a plan. */
    val planning: IdeaCard? = null,
    val today: LocalDate = LocalDate(2000, 1, 1),
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
    private val markProposed: MarkIdeaProposedUseCase,
    private val savePlan: SavePlanUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(IdeasUiState())

    val state: StateFlow<IdeasUiState> = combine(observeIdeas(), observeFriends(), observeSettings(), local) { ideas, friends, settings, ui ->
        val byId = friends.associate { it.friend.id to it.friend }
        ui.copy(
            ideas = ideas.filter { ui.kindFilter == null || it.kind == ui.kindFilter }.map { IdeaCard(it, it.friendIds.mapNotNull(byId::get)) },
            hasAiKey = settings.usableAiKey != null,
            weeklyEnabled = settings.weeklyIdeasEnabled,
            optedInFriends = friends.count { it.friend.suggestEvents },
            lastRunAt = settings.lastIdeasRunAt,
            isLoading = false,
            now = clock.now(),
            today = clock.today(),
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

    fun onKindFilter(kind: IdeaKind?) = local.update { it.copy(kindFilter = kind) }

    fun onDismiss(id: String) {
        viewModelScope.launch { dismissIdea(id) }
    }

    /** Opens the "Propose to…" sheet with a drafted message for the idea's friends. */
    fun onPropose(id: String) = local.update { s -> s.copy(proposing = state.value.ideas.firstOrNull { it.idea.id == id }) }

    fun onDismissPropose() = local.update { it.copy(proposing = null) }

    /** The user sent the plan through WhatsApp, a text or the share sheet. */
    fun onProposed() {
        val id = local.value.proposing?.idea?.id ?: return
        viewModelScope.launch { markProposed(id) }
    }

    /** "Make it a plan": pick a day; it then reminds you and asks afterwards if it happened. */
    fun onPlan(id: String) = local.update { s -> s.copy(planning = state.value.ideas.firstOrNull { it.idea.id == id }) }

    fun onDismissPlan() = local.update { it.copy(planning = null) }

    fun onSavePlan(input: PlanInput) {
        val card = local.value.planning ?: return
        if (input.title.isBlank()) return
        viewModelScope.launch {
            savePlan.save(null, input.copy(friendIds = input.friendIds.ifEmpty { card.idea.friendIds }), ideaId = card.idea.id)
            local.update { it.copy(planning = null, message = IdeasMessage.PlanSaved) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private fun WeeklyIdeasOutcome.toMessage(): IdeasMessage = when (this) {
        is WeeklyIdeasOutcome.Done -> IdeasMessage.Found(ideas)
        is WeeklyIdeasOutcome.Failed -> IdeasMessage.Failed(error)
        is WeeklyIdeasOutcome.Skipped -> IdeasMessage.Skipped(reason)
    }
}
