package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactChannel
import com.neteinstein.goodcompany.core.domain.model.CustomField
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendEmail
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Introducer
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.model.MetInfo
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.Relative
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.model.SharedImage
import com.neteinstein.goodcompany.core.domain.model.TasteKind
import com.neteinstein.goodcompany.core.domain.usecase.AddFeedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CancelPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CatchUpStoryBuilder
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteAttachmentUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteHangoutUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DismissIdeaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DraftBirthdayMessageUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FeedNotFoundException
import com.neteinstein.goodcompany.core.domain.usecase.FriendDetail
import com.neteinstein.goodcompany.core.domain.usecase.GenerateTalkingPointsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogCallUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAttachmentsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendDetailUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreAttachmentUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestorePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SavePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCatchUpPausedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetClosenessUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetFriendPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetHomeCircleFilterUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetRealFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ShareInbox
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
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface FriendDetailMessage {
    data class LoggedWith(val name: String) : FriendDetailMessage

    data object CallLogged : FriendDetailMessage

    data class Snoozed(val until: LocalDate) : FriendDetailMessage

    data class IdeasFound(val count: Int) : FriendDetailMessage

    data object NoNewGifts : FriendDetailMessage

    data class AiError(val error: Throwable) : FriendDetailMessage

    data object ReminderSaved : FriendDetailMessage

    data object PlanSaved : FriendDetailMessage

    /** Offered with "Undo". */
    data object PlanCancelled : FriendDetailMessage

    data object PlanLogged : FriendDetailMessage

    data object PlanClosed : FriendDetailMessage

    data class FeedAdded(val title: String) : FriendDetailMessage

    data object FeedNotFound : FriendDetailMessage

    data object FeedError : FriendDetailMessage

    data object AddedToNews : FriendDetailMessage

    /** The picked picture couldn't be read (or no picture came back). */
    data object PhotoUnreadable : FriendDetailMessage

    /** A "More about" line was deleted; offered with "Undo". */
    data class CustomFieldRemoved(val label: String) : FriendDetailMessage

    /** A saved attachment was removed; offered with "Undo". */
    data object AttachmentRemoved : FriendDetailMessage
}

/** Everything the "About" dialog edits, saved in one go. [emails] are the typed ones (contact ones are kept as they are). */
data class AboutEdit(
    val job: String = "",
    val livesIn: String = "",
    val met: MetInfo? = null,
    val introducedBy: Introducer? = null,
    val preferredChannel: ContactChannel? = null,
    val emails: List<String> = emptyList(),
)

/** A relative from the "Add to <name>'s family" sheet ([id] null = new). */
data class RelativeEdit(
    val id: String? = null,
    val relation: Relation,
    val name: String,
    val birthday: LocalDate? = null,
    val likes: List<String> = emptyList(),
    val note: String = "",
    val notifyBirthday: Boolean = true,
)

/** A deleted "More about" line and where it was, kept for "Undo". */
data class RemovedCustomField(val field: CustomField, val index: Int)

data class PlanEditing(val plan: Plan?)

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
    /** Plan editor open: [PlanEditing.plan] null = new plan. */
    val planEditing: PlanEditing? = null,
    /** Plan just cancelled, offered for undo. */
    val undoPlan: Plan? = null,
    /** "More about" line just deleted, offered for undo. */
    val undoCustomField: RemovedCustomField? = null,
    /** Show the warmth score (off in Settings = softer view). */
    val showWarmth: Boolean = true,
    /** Things added since you last met — the catch-up story leads with them. */
    val freshCount: Int = 0,
    /** Looking for a blog's feed (the add dialog stays open meanwhile). */
    val isAddingFeed: Boolean = false,
    /** Closes the "Follow their blog" dialog after a successful add. */
    val feedAdded: Int = 0,
    val now: Instant = Instant.DISTANT_PAST,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
    /** "Saved for <name>" (D6): their attachments and their circles', newest first. */
    val attachments: List<Attachment> = emptyList(),
    /** Attachment just removed, offered for undo. */
    val undoAttachment: Attachment? = null,
)

class FriendDetailViewModel(
    private val friendId: FriendId,
    observeDetail: ObserveFriendDetailUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val updateFriend: UpdateFriendUseCase,
    private val deleteFriend: DeleteFriendUseCase,
    private val setRealFriend: SetRealFriendUseCase,
    private val setCloseness: SetClosenessUseCase,
    private val snooze: SnoozeCatchUpUseCase,
    private val logCall: LogCallUseCase,
    private val generateTalkingPoints: GenerateTalkingPointsUseCase,
    private val dismissIdea: DismissIdeaUseCase,
    private val draftBirthdayMessage: DraftBirthdayMessageUseCase,
    private val suggestGifts: SuggestGiftsUseCase,
    private val checkIn: CheckInUseCase,
    private val deleteHangout: DeleteHangoutUseCase,
    private val addReminder: AddReminderUseCase,
    private val setCatchUpPaused: SetCatchUpPausedUseCase,
    private val savePlan: SavePlanUseCase,
    private val confirmPlan: ConfirmPlanUseCase,
    private val cancelPlan: CancelPlanUseCase,
    private val restorePlan: RestorePlanUseCase,
    private val addFeed: AddFeedUseCase,
    private val setPhoto: SetFriendPhotoUseCase,
    private val setHomeCircleFilter: SetHomeCircleFilterUseCase,
    observeAttachments: ObserveAttachmentsUseCase,
    private val deleteAttachment: DeleteAttachmentUseCase,
    private val restoreAttachment: RestoreAttachmentUseCase,
    private val shareInbox: ShareInbox,
    private val clock: AppClock,
    private val ids: IdGenerator,
) : ViewModel() {
    private val local = MutableStateFlow(FriendDetailUiState())

    val state: StateFlow<FriendDetailUiState> = combine(
        observeDetail(friendId),
        observeSettings(),
        observeAttachments(friendId),
        local,
    ) { detail, settings, attachments, ui ->
        ui.copy(
            detail = detail,
            attachments = attachments,
            isLoading = false,
            notFound = detail == null,
            hasAiKey = settings.hasAiKey,
            showWarmth = settings.showWarmth,
            freshCount = detail?.let { d ->
                val since = d.lastSeen ?: (clock.now() - CatchUpStoryBuilder.FALLBACK_FRESH_DAYS.days)
                CatchUpStoryBuilder.freshItems(d.friend).count { it.addedAt > since }
            } ?: 0,
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

    /** A circle pill: Home is filtered by that circle (the screen then opens Home). */
    fun onOpenCircle(groupId: String) {
        viewModelScope.launch { setHomeCircleFilter(groupId) }
    }

    fun onClosenessChange(closeness: Closeness?) {
        viewModelScope.launch { setCloseness(friendId, closeness) }
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

    fun onAddLikes(values: List<String>) {
        val now = clock.now()
        edit { f ->
            val added = values.filterNot { it in f.likes }
            f.copy(likes = (f.likes + values).distinct(), likesAddedAt = f.likesAddedAt + added.associateWith { now })
        }
    }

    fun onRemoveLike(value: String) = edit { it.copy(likes = it.likes - value, likesAddedAt = it.likesAddedAt - value) }

    fun onAddDislikes(values: List<String>) = edit { it.copy(dislikes = (it.dislikes + values).distinct()) }

    fun onRemoveDislike(value: String) = edit { it.copy(dislikes = it.dislikes - value) }

    fun onNotesChange(notes: String) = edit { it.copy(notes = notes) }

    // --- About ------------------------------------------------------------------------------------------------------

    fun onSaveAbout(about: AboutEdit) = edit { f ->
        val typed = about.emails.map(String::trim).filter { it.isNotEmpty() }.distinctBy(String::lowercase)
        val fromContact = f.emails.filter { it.fromContact }
        val keys = fromContact.mapTo(HashSet()) { it.address.lowercase() }
        f.copy(
            job = about.job.trim(),
            livesIn = about.livesIn.trim(),
            met = about.met?.let { it.copy(place = it.place.trim()) }?.takeUnless { it.isEmpty },
            introducedBy = about.introducedBy?.let { it.copy(name = it.name.trim()) }?.takeIf { it.name.isNotEmpty() },
            preferredChannel = about.preferredChannel,
            emails = fromContact + typed.filter { it.lowercase() !in keys }.map { FriendEmail(it) },
        )
    }

    // --- Family -----------------------------------------------------------------------------------------------------

    fun onSaveRelative(edit: RelativeEdit) {
        if (edit.name.isBlank()) return
        val now = clock.now()
        val relative = Relative(
            id = edit.id ?: ids.next(),
            relation = edit.relation,
            name = edit.name.trim(),
            birthday = edit.birthday?.let { Birthday(it.month.ordinal + 1, it.day, it.year) },
            likes = edit.likes,
            note = edit.note.trim(),
            notifyBirthday = edit.notifyBirthday,
        )
        edit { f ->
            val existing = f.relatives.firstOrNull { it.id == relative.id }
            f.copy(
                relatives = if (existing != null) {
                    f.relatives.map { if (it.id == relative.id) relative.copy(addedAt = existing.addedAt) else it }
                } else {
                    f.relatives + relative.copy(addedAt = now)
                },
            )
        }
    }

    fun onRemoveRelative(id: String) = edit { f -> f.copy(relatives = f.relatives.filterNot { it.id == id }) }

    // --- Tastes by kind (food, drink, interests) ---------------------------------------------------------------------

    fun onAddTastes(kind: TasteKind, values: List<String>) {
        val now = clock.now()
        edit { f ->
            when (kind) {
                TasteKind.FOOD -> {
                    val added = values.filterNot { it in f.food }
                    f.copy(food = (f.food + values).distinct(), foodAddedAt = f.foodAddedAt + added.associateWith { now })
                }
                TasteKind.DRINK -> {
                    val added = values.filterNot { it in f.drinks }
                    f.copy(drinks = (f.drinks + values).distinct(), drinksAddedAt = f.drinksAddedAt + added.associateWith { now })
                }
                TasteKind.INTEREST -> {
                    val added = values.filterNot { it in f.interests }
                    f.copy(interests = (f.interests + values).distinct(), interestsAddedAt = f.interestsAddedAt + added.associateWith { now })
                }
            }
        }
    }

    fun onRemoveTaste(kind: TasteKind, value: String) = edit { f ->
        when (kind) {
            TasteKind.FOOD -> f.copy(food = f.food - value, foodAddedAt = f.foodAddedAt - value)
            TasteKind.DRINK -> f.copy(drinks = f.drinks - value, drinksAddedAt = f.drinksAddedAt - value)
            TasteKind.INTEREST -> f.copy(interests = f.interests - value, interestsAddedAt = f.interestsAddedAt - value)
        }
    }

    // --- "More about" (custom fields) ---------------------------------------------------------------------------------

    fun onSaveCustomField(id: String?, label: String, value: String) {
        if (label.isBlank() || value.isBlank()) return
        val field = CustomField(id ?: ids.next(), label.trim(), value.trim())
        edit { f ->
            f.copy(
                customFields = if (f.customFields.any { it.id == field.id }) {
                    f.customFields.map { if (it.id == field.id) field else it }
                } else {
                    f.customFields + field
                },
            )
        }
    }

    fun onRemoveCustomField(id: String) {
        val fields = state.value.detail?.friend?.customFields ?: return
        val index = fields.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        val removed = fields[index]
        edit { f -> f.copy(customFields = f.customFields.filterNot { it.id == id }) }
        local.update {
            it.copy(undoCustomField = RemovedCustomField(removed, index), message = FriendDetailMessage.CustomFieldRemoved(removed.label))
        }
    }

    fun onUndoRemoveCustomField() {
        val removed = local.value.undoCustomField ?: return
        edit { f ->
            if (f.customFields.any { it.id == removed.field.id }) {
                f
            } else {
                val list = f.customFields.toMutableList()
                list.add(removed.index.coerceIn(0, list.size), removed.field)
                f.copy(customFields = list)
            }
        }
        local.update { it.copy(undoCustomField = null) }
    }

    fun onSaveKid(id: String?, name: String, birthday: LocalDate?, likes: List<String>) {
        if (name.isBlank()) return
        val now = clock.now()
        val kid = Kid(
            id = id ?: ids.next(),
            name = name.trim(),
            birthday = birthday?.let { Birthday(it.month.ordinal + 1, it.day, it.year) },
            likes = likes,
        )
        edit { f ->
            val existing = f.kids.firstOrNull { it.id == kid.id }
            f.copy(
                kids = if (existing != null) {
                    f.kids.map { if (it.id == kid.id) kid.copy(addedAt = existing.addedAt) else it }
                } else {
                    f.kids + kid.copy(addedAt = now)
                },
            )
        }
    }

    fun onRemoveKid(id: String) = edit { f -> f.copy(kids = f.kids.filterNot { it.id == id }) }

    fun onAddEvent(title: String, date: LocalDate, remindDaysBefore: Int) {
        if (title.isBlank()) return
        val event = ImportantEvent(ids.next(), title.trim(), date, remindDaysBefore.coerceAtLeast(0), addedAt = clock.now())
        edit { f -> f.copy(importantEvents = (f.importantEvents + event).sortedBy { it.date }) }
    }

    fun onRemoveEvent(id: String) = edit { f -> f.copy(importantEvents = f.importantEvents.filterNot { it.id == id }) }

    // --- What's new (life updates) ----------------------------------------------------------------------------------

    /** "New job", "moved to Porto"… Leads their catch-up story until you meet. */
    fun onAddLifeUpdate(text: String) {
        if (text.isBlank()) return
        val update = LifeUpdate(ids.next(), text.trim(), clock.now())
        edit { f -> f.copy(lifeUpdates = f.lifeUpdates + update) }
    }

    fun onRemoveLifeUpdate(id: String) = edit { f -> f.copy(lifeUpdates = f.lifeUpdates.filterNot { it.id == id }) }

    // --- Blogs & feeds -----------------------------------------------------------------------------------------------

    fun onAddFeed(url: String) {
        if (url.isBlank() || local.value.isAddingFeed) return
        viewModelScope.launch {
            local.update { it.copy(isAddingFeed = true) }
            val result = addFeed(friendId, url)
            local.update {
                result.fold(
                    { feed -> it.copy(isAddingFeed = false, feedAdded = it.feedAdded + 1, message = FriendDetailMessage.FeedAdded(feed.title)) },
                    { e ->
                        it.copy(
                            isAddingFeed = false,
                            message = if (e is FeedNotFoundException) FriendDetailMessage.FeedNotFound else FriendDetailMessage.FeedError,
                        )
                    },
                )
            }
        }
    }

    fun onRemoveFeed(url: String) = edit { f -> f.copy(feeds = f.feeds.filterNot { it.url == url }) }

    /** A post on their blog becomes a "What's new" line. */
    fun onFeedPostToNews(title: String) {
        onAddLifeUpdate(title)
        say(FriendDetailMessage.AddedToNews)
    }

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

    // --- Photo ------------------------------------------------------------------------------------------------------

    /** A picture picked from the gallery for someone without a contact photo; null when the picker was cancelled. */
    fun onPhotoPicked(image: ByteArray?) {
        if (image == null) return
        viewModelScope.launch {
            if (!setPhoto(friendId, image)) say(FriendDetailMessage.PhotoUnreadable)
        }
    }

    // --- Chats and AI talking points -----------------------------------------------------------------------------

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

    // --- Rhythm extras ---------------------------------------------------------------------------------------------

    fun onTogglePause() {
        val paused = state.value.detail?.friend?.catchUpPaused ?: return
        viewModelScope.launch { setCatchUpPaused(friendId, !paused) }
    }

    fun onToggleContactCounts() = edit { it.copy(contactCountsAsCatchUp = !it.contactCountsAsCatchUp) }

    fun onToggleOccasion(occasion: Occasion) = edit { f ->
        f.copy(occasions = if (occasion in f.occasions) f.occasions - occasion else (f.occasions + occasion).sortedBy { it.ordinal })
    }

    // --- Plans ---------------------------------------------------------------------------------------------------

    fun onOpenPlanEditor(plan: Plan?) = local.update { it.copy(planEditing = PlanEditing(plan)) }

    fun onDismissPlanEditor() = local.update { it.copy(planEditing = null) }

    fun onSavePlan(title: String, friendIds: List<FriendId>, date: LocalDate, place: String?) {
        if (title.isBlank()) return
        val editing = local.value.planEditing?.plan
        viewModelScope.launch {
            savePlan(editing?.id, title, friendIds.ifEmpty { listOf(friendId) }, date, place)
            local.update { it.copy(planEditing = null, message = FriendDetailMessage.PlanSaved) }
        }
    }

    fun onConfirmPlan(id: String, happened: Boolean) {
        viewModelScope.launch {
            confirmPlan(id, happened)
            say(if (happened) FriendDetailMessage.PlanLogged else FriendDetailMessage.PlanClosed)
        }
    }

    fun onCancelPlan(plan: Plan) {
        viewModelScope.launch {
            cancelPlan(plan.id)
            local.update { it.copy(undoPlan = plan, message = FriendDetailMessage.PlanCancelled) }
        }
    }

    fun onUndoCancelPlan() {
        val plan = local.value.undoPlan ?: return
        viewModelScope.launch {
            restorePlan(plan)
            local.update { it.copy(undoPlan = null) }
        }
    }

    // --- Saved (D6) ---

    /**
     * "Add a photo" on the Saved card: the picked photo goes through the same confirm step as a share, already for
     * this friend. Empty [image] means it couldn't be read.
     */
    fun onAddSavedPhoto(image: ByteArray?, ref: PhotoRef?, takenOn: LocalDate?) {
        if (image == null) return
        if (image.isEmpty()) {
            say(FriendDetailMessage.PhotoUnreadable)
            return
        }
        shareInbox.offer(SharedContent(image = SharedImage(image, "image/jpeg"), photoRef = ref, takenOn = takenOn), friendId)
    }

    fun onRemoveAttachment(attachment: Attachment) {
        viewModelScope.launch {
            deleteAttachment(attachment)
            local.update { it.copy(undoAttachment = attachment, message = FriendDetailMessage.AttachmentRemoved) }
        }
    }

    fun onUndoRemoveAttachment() {
        val attachment = local.value.undoAttachment ?: return
        viewModelScope.launch {
            restoreAttachment(attachment)
            local.update { it.copy(undoAttachment = null) }
        }
    }

    fun onMessageShown() = local.update {
        it.copy(message = null, undoHangoutId = null, undoPlan = null, undoCustomField = null, undoAttachment = null)
    }
}
