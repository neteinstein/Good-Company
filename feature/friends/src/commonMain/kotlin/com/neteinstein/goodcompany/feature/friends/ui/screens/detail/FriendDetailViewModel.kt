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
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson
import com.neteinstein.goodcompany.core.domain.model.MetInfo
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Pronouns
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
import com.neteinstein.goodcompany.core.domain.usecase.KidDraft
import com.neteinstein.goodcompany.core.domain.usecase.LinkDraft
import com.neteinstein.goodcompany.core.domain.usecase.LogCallUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAttachmentsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendDetailUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveTagsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PersonLinkUseCases
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.domain.usecase.RemoveFriendPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RenameFriendUseCase
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
import com.neteinstein.goodcompany.core.domain.usecase.Tag
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

    /** One occurrence of a recurring plan skipped; the series goes on. */
    data object PlanSkipped : FriendDetailMessage

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

    /** A link to another person was removed; offered with "Undo". */
    data object LinkRemoved : FriendDetailMessage

    /** A relative became a person of their own ("Add as a person"). */
    data class AddedAsPerson(val name: String) : FriendDetailMessage

    /** A kid was added as a person of their own (F9). */
    data class KidAdded(val name: String) : FriendDetailMessage
}

/** What the link sheet saves, seen from the page's friend: [other] is their [role] ("Ana is Rui's girlfriend"). */
data class LinkEdit(
    val other: FriendId,
    val role: LinkType,
    val startedOn: LocalDate? = null,
    val endedOn: LocalDate? = null,
    val remindAnniversary: Boolean = false,
    /** The words for [other], set in the same sheet. */
    val otherPronouns: Pronouns? = null,
    /** The link being edited; null = a new one. */
    val id: String? = null,
)

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
    val birthday: Birthday? = null,
    val likes: List<String> = emptyList(),
    val note: String = "",
    val notifyBirthday: Boolean = true,
)

/** A deleted "More about" line and where it was, kept for "Undo". */
data class RemovedCustomField(val field: CustomField, val index: Int)

data class PlanEditing(val plan: Plan?)

/** AI gift ideas waiting to be kept or dismissed. A kid's are asked on the kid's own page (F9). */
data class GiftSuggestions(val ideas: List<String>)

data class FriendDetailUiState(
    val detail: FriendDetail? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    /** The AI switch (F1): off hides "Things to talk about" (the topics from notes stay) and every AI button. */
    val aiEnabled: Boolean = false,
    /** AI is on and a key is saved. */
    val hasAiKey: Boolean = false,
    val isGenerating: Boolean = false,
    val message: FriendDetailMessage? = null,
    val deleted: Boolean = false,
    /** Hangout just logged with "Together", offered for undo. */
    val undoHangoutId: String? = null,
    /** Increments on every "Together" so the UI can play the heart burst. */
    val celebrate: Int = 0,
    val showReminderEditor: Boolean = false,
    /** "" while the AI is working on gift ideas, null otherwise. */
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
    /** The people linked to this friend (issue #39): partners first, then exes, family and the rest. */
    val links: List<LinkedPerson> = emptyList(),
    /** Link just removed, offered for undo. */
    val undoLink: PersonLink? = null,
    /** The shared tag list (F16) the tag editors suggest from. */
    val tags: List<Tag> = emptyList(),
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
    private val removePhoto: RemoveFriendPhotoUseCase,
    private val renameFriend: RenameFriendUseCase,
    private val setHomeCircleFilter: SetHomeCircleFilterUseCase,
    observeAttachments: ObserveAttachmentsUseCase,
    observeTags: ObserveTagsUseCase,
    private val deleteAttachment: DeleteAttachmentUseCase,
    private val restoreAttachment: RestoreAttachmentUseCase,
    private val shareInbox: ShareInbox,
    private val links: PersonLinkUseCases,
    private val clock: AppClock,
    private val ids: IdGenerator,
) : ViewModel() {
    private val local = MutableStateFlow(FriendDetailUiState())

    val state: StateFlow<FriendDetailUiState> = combine(
        observeDetail(friendId),
        observeSettings(),
        observeAttachments(friendId),
        links.observe(friendId).combine(observeTags()) { linked, tags -> linked to tags },
        local,
    ) { detail, settings, attachments, (linked, tags), ui ->
        ui.copy(
            detail = detail,
            attachments = attachments,
            links = linked,
            tags = tags,
            isLoading = false,
            notFound = detail == null,
            aiEnabled = settings.aiEnabled,
            hasAiKey = settings.usableAiKey != null,
            showWarmth = settings.showWarmth,
            freshCount = detail?.let { d ->
                val since = d.lastSeen ?: (clock.now() - CatchUpStoryBuilder.FALLBACK_FRESH_DAYS.days)
                CatchUpStoryBuilder.freshItems(d.friend, d.kids).count { it.addedAt > since }
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

    /** The birthday sheet: day and month, an optional year (F4), and the reminder switch (on by default, F10). */
    fun onSaveBirthday(birthday: Birthday?, notify: Boolean) = edit { f -> f.copy(birthday = birthday, notifyBirthday = notify) }

    // --- Name (F19) -------------------------------------------------------------------------------------------------

    /** Saves the name given in the app; null (or the contact's own name) goes back to the contact's name. */
    fun onRename(name: String?) {
        viewModelScope.launch { renameFriend(friendId, name) }
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
            birthday = edit.birthday,
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

    /** "Add as a person" (question 1): the relative gets their own page, linked to this friend. */
    fun onAddRelativeAsPerson(relativeId: String) {
        val name = state.value.detail?.friend?.relatives?.firstOrNull { it.id == relativeId }?.name ?: return
        viewModelScope.launch {
            if (links.addRelativeAsPerson(friendId, relativeId) != null) say(FriendDetailMessage.AddedAsPerson(name))
        }
    }

    // --- Links between people and relationship to me (issue #39) ------------------------------------------------------

    /** Saves a link from this page: [LinkEdit.other] is this friend's [LinkEdit.role]. */
    fun onSaveLink(edit: LinkEdit) {
        if (edit.other == friendId) return
        viewModelScope.launch {
            links.setPronouns(edit.other, edit.otherPronouns)
            links.save(
                LinkDraft(
                    from = friendId,
                    to = edit.other,
                    type = edit.role,
                    startedOn = edit.startedOn,
                    endedOn = edit.endedOn,
                    remindAnniversary = edit.remindAnniversary,
                    id = edit.id,
                ),
            )
        }
    }

    fun onRemoveLink(id: String) {
        viewModelScope.launch {
            val removed = links.delete(id) ?: return@launch
            local.update { it.copy(undoLink = removed, message = FriendDetailMessage.LinkRemoved) }
        }
    }

    fun onUndoRemoveLink() {
        val link = local.value.undoLink ?: return
        viewModelScope.launch {
            links.restore(link)
            local.update { it.copy(undoLink = null) }
        }
    }

    /** "Rui is your…" (F6): null [relation] clears it. */
    fun onSaveRelationToMe(relation: LinkType?, pronouns: Pronouns?) {
        viewModelScope.launch { links.setRelationToMe(friendId, relation, pronouns) }
    }

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

    /**
     * "Add <name>'s kid" (F9): the kid becomes a person with their own page and birthday reminder, linked to this
     * friend. Removing them later removes the link (with Undo) from the Family card; the person stays.
     */
    fun onAddKid(draft: KidDraft) {
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            links.addKid(friendId, draft)?.let { say(FriendDetailMessage.KidAdded(draft.name.trim().substringBefore(' '))) }
        }
    }

    /** "Yes, niece" or a chip on an open kinship proposal (F7): sets what they are to you. */
    fun onConfirmKinship(relation: LinkType) {
        viewModelScope.launch { links.confirmKinship(friendId, relation) }
    }

    /** "Not right" / "Not family" (F7): this proposal isn't offered again. */
    fun onDismissKinship() {
        val proposal = state.value.detail?.kinship ?: return
        viewModelScope.launch { links.dismissKinship(friendId, proposal.options) }
    }

    /** "Show in my list" (F9) for this person. */
    fun onSetShowInMyList(show: Boolean) {
        viewModelScope.launch { links.setShowInMyList(friendId, show) }
    }

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

    fun onSaveGift(title: String, note: String) {
        if (title.isBlank()) return
        val gift = Gift(ids.next(), title.trim(), note.trim())
        edit { f -> f.copy(gifts = f.gifts + gift) }
    }

    fun onRemoveGift(id: String) = edit { f -> f.copy(gifts = f.gifts.filterNot { it.id == id }) }

    fun onMarkGiftGiven(id: String, occasion: GiftOccasion?) {
        val today = clock.today()
        edit { f -> f.copy(gifts = f.gifts.map { if (it.id == id) it.copy(givenOn = today, occasion = occasion) else it }) }
    }

    fun onSuggestGifts() {
        if (local.value.suggestingGiftsFor != null) return
        viewModelScope.launch {
            local.update { it.copy(suggestingGiftsFor = "", giftSuggestions = null) }
            val result = suggestGifts(friendId)
            local.update {
                result.fold(
                    { ideas ->
                        it.copy(
                            suggestingGiftsFor = null,
                            giftSuggestions = GiftSuggestions(ideas).takeIf { ideas.isNotEmpty() },
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
        onSaveGift(title, "")
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

    /**
     * A picture picked from the gallery and cropped (F14), kept as the photo set in the app; null when the picker was
     * cancelled, empty when the picture couldn't be read.
     */
    fun onPhotoPicked(image: ByteArray?) {
        if (image == null) return
        viewModelScope.launch {
            if (!setPhoto(friendId, image)) say(FriendDetailMessage.PhotoUnreadable)
        }
    }

    /** Removes the photo set in the app: the contact's photo, or the initials, show again. */
    fun onRemovePhoto() {
        viewModelScope.launch { removePhoto(friendId) }
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
            val name = state.value.detail?.friend?.displayName?.substringBefore(' ').orEmpty()
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

    /** Saves the editor's plan; with neither people nor a circle picked, it's with this friend. */
    fun onSavePlan(input: PlanInput) {
        if (input.title.isBlank()) return
        val editing = local.value.planEditing?.plan
        val withSomeone = if (input.friendIds.isEmpty() && input.circleId == null) input.copy(friendIds = listOf(friendId)) else input
        viewModelScope.launch {
            savePlan.save(editing?.id, withSomeone)
            local.update { it.copy(planEditing = null, message = FriendDetailMessage.PlanSaved) }
        }
    }

    /** "Skip this one": the open occurrence of a recurring plan; the series goes on. */
    fun onSkipPlan(plan: Plan) {
        viewModelScope.launch {
            confirmPlan(plan.id, happened = false, day = plan.date)
            say(FriendDetailMessage.PlanSkipped)
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
        it.copy(message = null, undoHangoutId = null, undoPlan = null, undoCustomField = null, undoAttachment = null, undoLink = null)
    }
}
