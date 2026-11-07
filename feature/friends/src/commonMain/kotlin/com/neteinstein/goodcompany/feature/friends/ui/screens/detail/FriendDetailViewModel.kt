package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DismissIdeaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DraftBirthdayMessageUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FriendDetail
import com.neteinstein.goodcompany.core.domain.usecase.GenerateTalkingPointsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportChatUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogCallUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendDetailUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCircleUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetRealFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeCatchUpUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.SuggestGiftsUseCase
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
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface FriendDetailMessage {
    data class LoggedWith(val name: String) : FriendDetailMessage

    data object CallLogged : FriendDetailMessage

    data class Snoozed(val until: LocalDate) : FriendDetailMessage

    data class ChatImported(val messages: Int, val sender: String?) : FriendDetailMessage

    data object ChatInvalid : FriendDetailMessage

    data class IdeasFound(val count: Int) : FriendDetailMessage

    data object NoNewGifts : FriendDetailMessage

    data class AiError(val error: Throwable) : FriendDetailMessage

    data object ReminderSaved : FriendDetailMessage
}

/** Who the AI gift suggestions are for: the friend (kidId = null) or one of their kids. */
data class GiftSuggestions(val kidId: String?, val ideas: List<String>)

data class FriendDetailUiState(
    val detail: FriendDetail? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val hasAiKey: Boolean = false,
    val isGenerating: Boolean = false,
    val message: FriendDetailMessage? = null,
    val deleted: Boolean = false,
    /** Hangout just logged with "Together", offered for undo. */
    val undoHangoutId: String? = null,
    /** Increments on every "Together" so the UI can play the heart burst. */
    val celebrate: Int = 0,
    val showReminderEditor: Boolean = false,
    /** Kid id (or "" for the friend) whose gift ideas the AI is working on. */
    val suggestingGiftsFor: String? = null,
    val giftSuggestions: GiftSuggestions? = null,
    val showWish: Boolean = false,
    /** AI-personalised birthday message; null → the UI shows the default template. */
    val wishDraft: String? = null,
    val isDraftingWish: Boolean = false,
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
    private val setRealFriend: SetRealFriendUseCase,
    private val setCircle: SetCircleUseCase,
    private val snooze: SnoozeCatchUpUseCase,
    private val logCall: LogCallUseCase,
    private val importChat: ImportChatUseCase,
    private val generateTalkingPoints: GenerateTalkingPointsUseCase,
    private val dismissIdea: DismissIdeaUseCase,
    private val draftBirthdayMessage: DraftBirthdayMessageUseCase,
    private val suggestGifts: SuggestGiftsUseCase,
    private val checkIn: CheckInUseCase,
    private val deleteHangout: DeleteHangoutUseCase,
    private val addReminder: AddReminderUseCase,
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

    private fun say(message: FriendDetailMessage) = local.update { it.copy(message = message) }

    fun onToggleClose() {
        viewModelScope.launch { setRealFriend.toggle(friendId) }
    }

    fun onCircleChange(circle: Circle?) {
        viewModelScope.launch { setCircle(friendId, circle) }
    }

    fun onToggleBirthdayNotifications() = edit { it.copy(notifyBirthday = !it.notifyBirthday) }

    fun onToggleSuggestEvents() = edit { it.copy(suggestEvents = !it.suggestEvents) }

    /** A custom rhythm that overrides the circle's default (null → back to the circle default). */
    fun onMeetEveryChange(days: Int?) = edit { it.copy(meetEveryDays = days) }

    fun onSnooze(option: SnoozeOption) {
        viewModelScope.launch {
            snooze.friend(friendId, option)
            say(FriendDetailMessage.Snoozed(option.until(clock.today())))
        }
    }

    fun onClearSnooze() {
        viewModelScope.launch { snooze.clearFriend(friendId) }
    }

    fun onCalled() {
        viewModelScope.launch {
            logCall(friendId)
            say(FriendDetailMessage.CallLogged)
        }
    }

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

    // --- Gifts ---------------------------------------------------------------------------------------------------

    fun onSaveGift(title: String, note: String, forKidId: String?) {
        if (title.isBlank()) return
        val gift = Gift(ids.next(), title.trim(), note.trim(), forKidId)
        edit { f -> f.copy(gifts = f.gifts + gift) }
    }

    fun onRemoveGift(id: String) = edit { f -> f.copy(gifts = f.gifts.filterNot { it.id == id }) }

    fun onMarkGiftGiven(id: String, occasion: GiftOccasion?) {
        val today = clock.today()
        edit { f -> f.copy(gifts = f.gifts.map { if (it.id == id) it.copy(givenOn = today, occasion = occasion) else it }) }
    }

    fun onSuggestGifts(kidId: String?) {
        if (local.value.suggestingGiftsFor != null) return
        viewModelScope.launch {
            local.update { it.copy(suggestingGiftsFor = kidId.orEmpty(), giftSuggestions = null) }
            val result = suggestGifts(friendId, kidId)
            local.update {
                result.fold(
                    { ideas ->
                        it.copy(
                            suggestingGiftsFor = null,
                            giftSuggestions = GiftSuggestions(kidId, ideas).takeIf { ideas.isNotEmpty() },
                            message = if (ideas.isEmpty()) FriendDetailMessage.NoNewGifts else it.message,
                        )
                    },
                    { e -> it.copy(suggestingGiftsFor = null, message = FriendDetailMessage.AiError(e)) },
                )
            }
        }
    }

    /** Keeps one AI suggestion as a gift idea. */
    fun onKeepGiftSuggestion(title: String) {
        val suggestions = local.value.giftSuggestions ?: return
        onSaveGift(title, "", suggestions.kidId)
        local.update {
            val rest = suggestions.ideas - title
            it.copy(giftSuggestions = if (rest.isEmpty()) null else suggestions.copy(ideas = rest))
        }
    }

    fun onDismissGiftSuggestions() = local.update { it.copy(giftSuggestions = null) }

    // --- Birthday wishes -----------------------------------------------------------------------------------------

    fun onOpenWish() = local.update { it.copy(showWish = true) }

    fun onDismissWish() = local.update { it.copy(showWish = false) }

    fun onPersonaliseWish() {
        if (local.value.isDraftingWish) return
        viewModelScope.launch {
            local.update { it.copy(isDraftingWish = true) }
            val result = draftBirthdayMessage(friendId)
            local.update {
                result.fold(
                    { text -> it.copy(isDraftingWish = false, wishDraft = text) },
                    { e -> it.copy(isDraftingWish = false, message = FriendDetailMessage.AiError(e)) },
                )
            }
        }
    }

    // --- Chats and AI talking points -----------------------------------------------------------------------------

    fun onChatPicked(text: String?) {
        if (text.isNullOrBlank()) return
        viewModelScope.launch {
            val result = importChat(friendId, text)
            say(if (result.messages == 0) FriendDetailMessage.ChatInvalid else FriendDetailMessage.ChatImported(result.messages, result.friendSender))
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
                    message = result.fold({ n -> FriendDetailMessage.IdeasFound(n) }, { e -> FriendDetailMessage.AiError(e) }),
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

    /** One tap: "we're together right now". */
    fun onTogetherNow() {
        viewModelScope.launch {
            val hangout = checkIn(listOf(friendId))
            val name = state.value.detail?.friend?.name?.substringBefore(' ').orEmpty()
            local.update { it.copy(message = FriendDetailMessage.LoggedWith(name), undoHangoutId = hangout.id, celebrate = it.celebrate + 1) }
        }
    }

    fun onUndoTogether() {
        val id = local.value.undoHangoutId ?: return
        viewModelScope.launch {
            deleteHangout(id)
            local.update { it.copy(undoHangoutId = null) }
        }
    }

    fun onOpenReminderEditor() = local.update { it.copy(showReminderEditor = true) }

    fun onDismissReminderEditor() = local.update { it.copy(showReminderEditor = false) }

    fun onSaveReminder(title: String, friendIds: List<FriendId>, date: LocalDate?, hour: Int) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val due = date?.atTime(LocalTime(hour.coerceIn(0, 23), 0))?.toInstant(clock.timeZone)
            addReminder(title, friendIds.ifEmpty { listOf(friendId) }, due)
            local.update { it.copy(showReminderEditor = false, message = FriendDetailMessage.ReminderSaved) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null, undoHangoutId = null) }
}
