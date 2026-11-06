package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.usecase.DeleteFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DismissIdeaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FriendDetail
import com.neteinstein.goodcompany.core.domain.usecase.GenerateTalkingPointsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportChatUseCase
import com.neteinstein.goodcompany.core.domain.usecase.MissingApiKeyException
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendDetailUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateFriendUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

data class FriendDetailUiState(
    val detail: FriendDetail? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val hasAiKey: Boolean = false,
    val isGenerating: Boolean = false,
    val message: String? = null,
    val deleted: Boolean = false,
    val now: Instant = Instant.DISTANT_PAST,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
)

class FriendDetailViewModel(
    private val friendId: FriendId,
    observeDetail: ObserveFriendDetailUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val updateFriend: UpdateFriendUseCase,
    private val deleteFriend: DeleteFriendUseCase,
    private val importChat: ImportChatUseCase,
    private val generateTalkingPoints: GenerateTalkingPointsUseCase,
    private val dismissIdea: DismissIdeaUseCase,
    private val clock: AppClock,
    private val ids: IdGenerator,
) : ViewModel() {
    private val local = MutableStateFlow(FriendDetailUiState())

    val state: StateFlow<FriendDetailUiState> = combine(observeDetail(friendId), observeSettings(), local) { detail, settings, ui ->
        ui.copy(
            detail = detail,
            isLoading = false,
            notFound = detail == null,
            hasAiKey = settings.hasAiKey,
            now = clock.now(),
            today = clock.today(),
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendDetailUiState())

    private fun edit(transform: (Friend) -> Friend) {
        viewModelScope.launch { updateFriend(friendId, transform) }
    }

    fun onToggleClose() = edit { it.copy(isCloseFriend = !it.isCloseFriend) }

    fun onToggleBirthdayNotifications() = edit { it.copy(notifyBirthday = !it.notifyBirthday) }

    fun onToggleSuggestEvents() = edit { it.copy(suggestEvents = !it.suggestEvents) }

    fun onMeetEveryChange(days: Int?) = edit { it.copy(meetEveryDays = days) }

    fun onBirthdayChange(date: LocalDate?) = edit { f ->
        f.copy(birthday = date?.let { Birthday(it.month.ordinal + 1, it.day, it.year) })
    }

    fun onAddLikes(values: List<String>) = edit { it.copy(likes = (it.likes + values).distinct()) }

    fun onRemoveLike(value: String) = edit { it.copy(likes = it.likes - value) }

    fun onAddDislikes(values: List<String>) = edit { it.copy(dislikes = (it.dislikes + values).distinct()) }

    fun onRemoveDislike(value: String) = edit { it.copy(dislikes = it.dislikes - value) }

    fun onNotesChange(notes: String) = edit { it.copy(notes = notes) }

    fun onSaveKid(id: String?, name: String, birthday: LocalDate?, likes: List<String>) {
        if (name.isBlank()) return
        val kid = Kid(
            id = id ?: ids.next(),
            name = name.trim(),
            birthday = birthday?.let { Birthday(it.month.ordinal + 1, it.day, it.year) },
            likes = likes,
        )
        edit { f -> f.copy(kids = if (f.kids.any { it.id == kid.id }) f.kids.map { if (it.id == kid.id) kid else it } else f.kids + kid) }
    }

    fun onRemoveKid(id: String) = edit { f -> f.copy(kids = f.kids.filterNot { it.id == id }) }

    fun onAddEvent(title: String, date: LocalDate, remindDaysBefore: Int) {
        if (title.isBlank()) return
        val event = ImportantEvent(ids.next(), title.trim(), date, remindDaysBefore.coerceAtLeast(0))
        edit { f -> f.copy(importantEvents = (f.importantEvents + event).sortedBy { it.date }) }
    }

    fun onRemoveEvent(id: String) = edit { f -> f.copy(importantEvents = f.importantEvents.filterNot { it.id == id }) }

    fun onChatPicked(text: String?) {
        if (text.isNullOrBlank()) return
        viewModelScope.launch {
            val result = importChat(friendId, text)
            local.update {
                it.copy(
                    message = if (result.messages == 0) {
                        "That doesn't look like a WhatsApp chat export."
                    } else {
                        "Imported ${result.messages} messages" + (result.friendSender?.let { s -> " · matched \"$s\"" } ?: "")
                    },
                )
            }
        }
    }

    fun onGenerateIdeas() {
        if (local.value.isGenerating) return
        viewModelScope.launch {
            local.update { it.copy(isGenerating = true) }
            val result = generateTalkingPoints(friendId)
            local.update {
                it.copy(
                    isGenerating = false,
                    message = result.fold(
                        { n -> if (n == 0) "Nothing fresh found this time." else "Found $n ideas" },
                        { e -> if (e is MissingApiKeyException) e.message else "Couldn't fetch ideas: ${e.message}" },
                    ),
                )
            }
        }
    }

    fun onDismissIdea(id: String) {
        viewModelScope.launch { dismissIdea(id) }
    }

    fun onDelete() {
        viewModelScope.launch {
            deleteFriend(friendId)
            local.update { it.copy(deleted = true) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }
}
