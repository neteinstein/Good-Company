package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.CatchUpCounts
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactChannel
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.CustomField
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendEmail
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Introducer
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson
import com.neteinstein.goodcompany.core.domain.model.MetInfo
import com.neteinstein.goodcompany.core.domain.model.NoteMedia
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanAnswer
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.Relative
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.model.ReminderSnooze
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.model.SharedImage
import com.neteinstein.goodcompany.core.domain.model.SocialLink
import com.neteinstein.goodcompany.core.domain.model.SocialNetwork
import com.neteinstein.goodcompany.core.domain.model.Socials
import com.neteinstein.goodcompany.core.domain.model.TasteKind
import com.neteinstein.goodcompany.core.domain.usecase.AddFeedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.AddReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CadenceRules
import com.neteinstein.goodcompany.core.domain.usecase.CatchUpStoryBuilder
import com.neteinstein.goodcompany.core.domain.usecase.ClosePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CompleteReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ConfirmPlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteAttachmentUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DeleteTimelineEntryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DismissIdeaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.DraftBirthdayMessageUseCase
import com.neteinstein.goodcompany.core.domain.usecase.EditReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.FeedNotFoundException
import com.neteinstein.goodcompany.core.domain.usecase.FriendDetail
import com.neteinstein.goodcompany.core.domain.usecase.GenerateTalkingPointsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportChatUseCase
import com.neteinstein.goodcompany.core.domain.usecase.KidDraft
import com.neteinstein.goodcompany.core.domain.usecase.LinkDraft
import com.neteinstein.goodcompany.core.domain.usecase.MarkMediaExplainedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAttachmentsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendDetailUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveRemindersUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveTagsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PersonBackgroundUseCases
import com.neteinstein.goodcompany.core.domain.usecase.PersonLinkUseCases
import com.neteinstein.goodcompany.core.domain.usecase.PersonTimeline
import com.neteinstein.goodcompany.core.domain.usecase.PickedNoteMedia
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.domain.usecase.PrepareNoteMediaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ReminderTarget
import com.neteinstein.goodcompany.core.domain.usecase.RemoveFriendPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RenameFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreAttachmentUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreGroupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestorePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreTimelineEntryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SaveNoteUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SavePlanUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCatchUpPausedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetCircleMemberUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetClosenessUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetFriendPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ShareInbox
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeCatchUpUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeReminderUseCase
import com.neteinstein.goodcompany.core.domain.usecase.StoryRing
import com.neteinstein.goodcompany.core.domain.usecase.StoryViews
import com.neteinstein.goodcompany.core.domain.usecase.SuggestGiftsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.Tag
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.domain.usecase.TogetherTimeline
import com.neteinstein.goodcompany.core.domain.usecase.UpdateFriendUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import com.neteinstein.goodcompany.core.ui.components.ReminderDraft
import com.neteinstein.goodcompany.core.ui.components.ReminderNotice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface FriendDetailMessage {

    data class Snoozed(val until: LocalDate) : FriendDetailMessage

    data class IdeasFound(val count: Int) : FriendDetailMessage

    data object NoNewGifts : FriendDetailMessage

    data class AiError(val error: Throwable) : FriendDetailMessage

    data object ReminderSaved : FriendDetailMessage

    /** Done, snoozed or deleted (issue #99); Done and Delete offer Undo. */
    data class ReminderChanged(val notice: ReminderNotice) : FriendDetailMessage

    data object PlanSaved : FriendDetailMessage

    /** Offered with "Undo". */
    data object PlanCancelled : FriendDetailMessage

    data object PlanDeleted : FriendDetailMessage

    data object PlanNotAttended : FriendDetailMessage

    data object PlanLogged : FriendDetailMessage

    data object PlanClosed : FriendDetailMessage

    /** One occurrence of a recurring plan skipped; the series goes on. */
    data object PlanSkipped : FriendDetailMessage

    data class FeedAdded(val title: String) : FriendDetailMessage

    data object FeedNotFound : FriendDetailMessage

    data object FeedError : FriendDetailMessage

    data object AddedToNews : FriendDetailMessage

    /** The Conversations box's Add (issue #117): a chat export read. */
    data class ChatImported(val messages: Int, val sender: String?) : FriendDetailMessage

    data object ChatInvalid : FriendDetailMessage

    /** The note popup saved (issue #119). */
    data object NoteSaved : FriendDetailMessage

    /** Something deleted from the page (issue #102); offered with "Undo". */
    data object Removed : FriendDetailMessage

    /** The picked picture couldn't be read (or no picture came back). */
    data object PhotoUnreadable : FriendDetailMessage

    /** The page's background image was removed (issue #144); offered with "Undo". */
    data object BackgroundRemoved : FriendDetailMessage

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

    /** A choice or a sheet saved on its own (no Save tap, #64); offered with "Undo". */
    data object Saved : FriendDetailMessage
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

/**
 * What a save-on-choice (or save-on-close) change replaced, kept for "Undo" (decision "Pickers · save without a Save
 * button", issue #64). Each one restores only what that save touched.
 */
sealed interface SavedEdit {
    data class BirthdayBefore(val birthday: Birthday?, val notify: Boolean) : SavedEdit

    /** [previous] null = the relative was new. */
    data class RelativeBefore(val id: String, val previous: Relative?) : SavedEdit

    data class GiftBefore(val gift: Gift) : SavedEdit

    data class KidAdded(val id: FriendId) : SavedEdit

    /** The page's background image before it was set or removed (issue #144); null = there was none. */
    data class BackgroundBefore(val background: ContactPhoto?) : SavedEdit

    /** [tracksKeepInTouch] too: becoming your kid or partner turns "Stay in touch" off (issue #132). */
    data class RelationToMeBefore(val relation: LinkType?, val pronouns: Pronouns?, val tracksKeepInTouch: Boolean = true) : SavedEdit

    /** [previous] null = the link was new; [other]'s pronouns are set from the same sheet. */
    data class LinkBefore(val savedId: String, val previous: PersonLink?, val other: FriendId, val otherPronouns: Pronouns?) : SavedEdit

    /** A circle as it was before they were put in or taken out (issue #70). */
    data class CircleBefore(val circle: FriendGroup) : SavedEdit

    /** "What's new" before a line was deleted (issue #102). */
    data class NewsBefore(val updates: List<LifeUpdate>) : SavedEdit

    /** Important moments before one was deleted (issue #102). */
    data class MomentsBefore(val events: List<ImportantEvent>) : SavedEdit

    /** Every taste list before one was deleted (issue #102). */
    data class TastesBefore(val friend: Friend) : SavedEdit

    /** Their socials and websites before one was removed (issue #118). */
    data class LinksBefore(val socials: List<SocialLink>, val websites: List<String>) : SavedEdit

    /** A timeline entry deleted from the person's page (issue #102). */
    data class EntryRemoved(val entry: TimelineEntry) : SavedEdit
}

/** Where a taste chip lives on Tastes: the three kinds, likes and dislikes. */
enum class TasteSlot { FOOD, DRINK, INTEREST, LIKE, DISLIKE }

/** A deleted "More about" line and where it was, kept for "Undo". */
data class RemovedCustomField(val field: CustomField, val index: Int)

data class PlanEditing(val plan: Plan?)

/** AI gift ideas waiting to be kept or dismissed. A kid's are asked on the kid's own page (F9). */
data class GiftSuggestions(val ideas: List<String>)

/** The note popup (issue #119): the note changed ([entry] null = a new one) and its photos and videos so far. */
data class NoteEditing(val entry: JournalEntry? = null, val media: List<NoteMedia> = emptyList(), val isAddingMedia: Boolean = false)

data class FriendDetailUiState(
    val detail: FriendDetail? = null,
    /** The background image behind the top of their page (issue #144); null = the usual gradient only. */
    val background: ContactPhoto? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    /** The AI switch (F1): off hides "Things to talk about" (the topics from notes stay) and every AI button. */
    val aiEnabled: Boolean = false,
    /** AI is on and a key is saved. */
    val hasAiKey: Boolean = false,
    val isGenerating: Boolean = false,
    val message: FriendDetailMessage? = null,
    val deleted: Boolean = false,
    val showReminderEditor: Boolean = false,
    /** The reminder the editor changes; null = a new one. */
    val editingReminder: Reminder? = null,
    /** Their open reminders and those of their circles (issue #99), soonest first. */
    val reminders: List<Reminder> = emptyList(),
    /** The note popup (issue #119); null while closed. */
    val noteEditing: NoteEditing? = null,
    /** The one-line "a small preview, the original stays in your gallery" was shown once on this device (issue #119). */
    val mediaExplained: Boolean = true,
    /** The Together timeline (issue #117): Make plans, upcoming, Today, the last [PersonTimeline.SHOWN] past entries. */
    val together: TogetherTimeline = TogetherTimeline(),
    /** The reminder as it was before Done or Delete, for the snackbar's Undo. */
    val reminderUndo: Reminder? = null,
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
    /** Each level's default rhythm, for the closeness picker (issue #86). */
    val cadenceRules: CadenceRules = CadenceRules(),
    /** Things added since you last met — the catch-up story leads with them. */
    val freshCount: Int = 0,
    /** The ring on their photo (issue #103): bright while something fresh is unseen, grey once their story was opened. */
    val storyRing: StoryRing = StoryRing.NONE,
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
    /** A change that saved without a Save tap (#64), offered for undo. */
    val undoSave: SavedEdit? = null,
    /** The shared tag list (F16) the tag editors suggest from. */
    val tags: List<Tag> = emptyList(),
)

class FriendDetailViewModel(
    private val friendId: FriendId,
    observeDetail: ObserveFriendDetailUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val updateFriend: UpdateFriendUseCase,
    private val deleteFriend: DeleteFriendUseCase,
    private val setCloseness: SetClosenessUseCase,
    private val snooze: SnoozeCatchUpUseCase,
    private val generateTalkingPoints: GenerateTalkingPointsUseCase,
    private val dismissIdea: DismissIdeaUseCase,
    private val draftBirthdayMessage: DraftBirthdayMessageUseCase,
    private val suggestGifts: SuggestGiftsUseCase,
    private val addReminder: AddReminderUseCase,
    private val editReminder: EditReminderUseCase,
    private val completeReminder: CompleteReminderUseCase,
    private val snoozeReminder: SnoozeReminderUseCase,
    private val deleteReminder: DeleteReminderUseCase,
    private val restoreReminder: RestoreReminderUseCase,
    observeReminders: ObserveRemindersUseCase,
    private val setCatchUpPaused: SetCatchUpPausedUseCase,
    private val savePlan: SavePlanUseCase,
    private val confirmPlan: ConfirmPlanUseCase,
    private val closePlan: ClosePlanUseCase,
    private val restorePlan: RestorePlanUseCase,
    private val addFeed: AddFeedUseCase,
    private val setPhoto: SetFriendPhotoUseCase,
    private val removePhoto: RemoveFriendPhotoUseCase,
    private val backgrounds: PersonBackgroundUseCases,
    private val renameFriend: RenameFriendUseCase,
    private val setCircleMember: SetCircleMemberUseCase,
    private val restoreGroup: RestoreGroupUseCase,
    observeAttachments: ObserveAttachmentsUseCase,
    observeTags: ObserveTagsUseCase,
    private val deleteAttachment: DeleteAttachmentUseCase,
    private val restoreAttachment: RestoreAttachmentUseCase,
    private val shareInbox: ShareInbox,
    private val links: PersonLinkUseCases,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val deleteEntry: DeleteTimelineEntryUseCase,
    private val restoreEntry: RestoreTimelineEntryUseCase,
    storyViews: StoryViews,
    private val importChat: ImportChatUseCase,
    private val prepareNoteMedia: PrepareNoteMediaUseCase,
    private val saveNote: SaveNoteUseCase,
    private val markMediaExplained: MarkMediaExplainedUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(FriendDetailUiState())

    val state: StateFlow<FriendDetailUiState> = combine(
        observeDetail(friendId),
        observeSettings(),
        observeAttachments(friendId).combine(observeReminders()) { attachments, reminders -> attachments to reminders },
        links.observe(friendId).combine(observeTags()) { linked, tags -> linked to tags },
        combine(local, storyViews.views, backgrounds.observe(friendId)) { ui, views, background -> Triple(ui, views[friendId], background) },
    ) { detail, settings, (attachments, reminders), (linked, tags), (ui, storySeenAt, background) ->
        val fresh = detail?.let { d ->
            val since = d.lastSeen ?: (clock.now() - CatchUpStoryBuilder.FALLBACK_FRESH_DAYS.days)
            CatchUpStoryBuilder.freshItems(d.friend, d.kids).map { it.addedAt }.filter { it > since }
        }.orEmpty()
        val theirs = reminders.filter { it.isFor(friendId, detail?.groups.orEmpty()) }
        ui.copy(
            detail = detail,
            background = background,
            reminders = theirs.filter { !it.done },
            together = detail?.let { d ->
                PersonTimeline.build(d.friend, d.timeline, d.plans + d.closedPlans, theirs, attachments, clock.today(), clock.timeZone)
            } ?: TogetherTimeline(),
            attachments = attachments,
            links = linked,
            tags = tags,
            isLoading = false,
            notFound = detail == null,
            aiEnabled = settings.aiEnabled,
            mediaExplained = settings.mediaExplained,
            hasAiKey = settings.usableAiKey != null,
            showWarmth = settings.showWarmth,
            cadenceRules = CadenceRules.from(settings),
            freshCount = fresh.size,
            storyRing = StoryRing.of(fresh, storySeenAt),
            now = clock.now(),
            today = clock.today(),
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendDetailUiState())

    private fun edit(transform: (Friend) -> Friend) {
        viewModelScope.launch { updateFriend(friendId, transform) }
    }

    private fun say(message: FriendDetailMessage) = local.update { it.copy(message = message) }

    /** Like [edit], then "Saved" with Undo; [before] records what the change replaces. Nothing changed, nothing said. */
    private fun editWithUndo(
        before: (Friend) -> SavedEdit,
        message: FriendDetailMessage = FriendDetailMessage.Saved,
        transform: (Friend) -> Friend,
    ) {
        viewModelScope.launch {
            var undo: SavedEdit? = null
            updateFriend(friendId) { f ->
                val next = transform(f)
                if (next != f) undo = before(f)
                next
            }
            undo?.let { saved -> local.update { it.copy(undoSave = saved, message = message) } }
        }
    }

    private fun offerUndo(saved: SavedEdit, message: FriendDetailMessage = FriendDetailMessage.Saved) =
        local.update { it.copy(undoSave = saved, message = message) }

    /** Undo for anything that saved on its own (#64). */
    fun onUndoSave() {
        val undo = local.value.undoSave ?: return
        local.update { it.copy(undoSave = null) }
        viewModelScope.launch {
            when (undo) {
                is SavedEdit.BirthdayBefore -> updateFriend(friendId) { it.copy(birthday = undo.birthday, notifyBirthday = undo.notify) }
                is SavedEdit.RelativeBefore -> updateFriend(friendId) { f ->
                    val previous = undo.previous
                    f.copy(
                        relatives = if (previous == null) {
                            f.relatives.filterNot { it.id == undo.id }
                        } else {
                            f.relatives.map { if (it.id == undo.id) previous else it }
                        },
                    )
                }
                is SavedEdit.GiftBefore -> updateFriend(friendId) { f ->
                    f.copy(gifts = f.gifts.map { if (it.id == undo.gift.id) undo.gift else it })
                }
                is SavedEdit.KidAdded -> deleteFriend(undo.id)
                is SavedEdit.BackgroundBefore -> backgrounds.restore(friendId, undo.background)
                is SavedEdit.NewsBefore -> updateFriend(friendId) { it.copy(lifeUpdates = undo.updates) }
                is SavedEdit.MomentsBefore -> updateFriend(friendId) { it.copy(importantEvents = undo.events) }
                is SavedEdit.LinksBefore -> updateFriend(friendId) { it.copy(socials = undo.socials, websites = undo.websites) }
                is SavedEdit.TastesBefore -> updateFriend(friendId) { f ->
                    val t = undo.friend
                    f.copy(
                        food = t.food,
                        foodAddedAt = t.foodAddedAt,
                        drinks = t.drinks,
                        drinksAddedAt = t.drinksAddedAt,
                        interests = t.interests,
                        interestsAddedAt = t.interestsAddedAt,
                        likes = t.likes,
                        likesAddedAt = t.likesAddedAt,
                        dislikes = t.dislikes,
                    )
                }
                is SavedEdit.EntryRemoved -> restoreEntry(undo.entry)
                is SavedEdit.CircleBefore -> restoreGroup(undo.circle)
                is SavedEdit.RelationToMeBefore -> links.setRelationToMe(friendId, undo.relation, undo.pronouns, undo.tracksKeepInTouch)
                is SavedEdit.LinkBefore -> {
                    val previous = undo.previous
                    if (previous == null) links.delete(undo.savedId) else links.restore(previous)
                    links.setPronouns(undo.other, undo.otherPronouns)
                }
            }
        }
    }

    /**
     * A tick in the "+ Circle" sheet (issue #70): saves at once (#64), with Undo. The built-in Family circle isn't
     * offered (it follows relations), and writes to it are ignored anyway.
     */
    fun onSetInCircle(groupId: String, member: Boolean) {
        viewModelScope.launch {
            setCircleMember(groupId, friendId, member)?.let { before -> offerUndo(SavedEdit.CircleBefore(before)) }
        }
    }

    /** A level from the picker (four levels, no "not set", issue #86); saved on choice (#64). */
    fun onClosenessChange(closeness: Closeness) {
        viewModelScope.launch { setCloseness(friendId, closeness) }
    }

    fun onToggleBirthdayNotifications() = edit { it.copy(notifyBirthday = !it.notifyBirthday) }

    fun onToggleSuggestEvents() = edit { it.copy(suggestEvents = !it.suggestEvents) }

    /**
     * The custom rhythm (issue #139): N days overrides their closeness, null ("From closeness") follows it and
     * [Friend.RHYTHM_OFF] means no rhythm, so no catch-ups.
     */
    fun onMeetEveryChange(days: Int?) = edit { it.copy(meetEveryDays = days?.coerceAtLeast(Friend.RHYTHM_OFF)) }

    fun onSnooze(option: SnoozeOption) {
        viewModelScope.launch {
            snooze.friend(friendId, option)
            say(FriendDetailMessage.Snoozed(option.until(clock.today())))
        }
    }

    fun onClearSnooze() {
        viewModelScope.launch { snooze.clearFriend(friendId) }
    }

    /** The birthday sheet: day and month, an optional year (F4), and the reminder switch (on by default, F10). */
    fun onSaveBirthday(birthday: Birthday?, notify: Boolean) = editWithUndo(
        before = { f -> SavedEdit.BirthdayBefore(f.birthday, f.notifyBirthday) },
    ) { f -> f.copy(birthday = birthday, notifyBirthday = notify) }

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

    /** "Message them first on" in edit mode (issue #90): a choice, saved at once (#64); null = no preference. */
    fun onSetPreferredChannel(channel: ContactChannel?) = edit { it.copy(preferredChannel = channel) }

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
        editWithUndo(before = { f -> SavedEdit.RelativeBefore(relative.id, f.relatives.firstOrNull { it.id == relative.id }) }) { f ->
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
        val current = state.value
        val previous = edit.id?.let { id -> current.links.firstOrNull { it.link.id == id }?.link }
        val otherPronouns = (current.links.map { it.person } + current.detail?.others.orEmpty()).firstOrNull { it.id == edit.other }?.pronouns
        viewModelScope.launch {
            links.setPronouns(edit.other, edit.otherPronouns)
            val saved = links.save(
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
            if (saved != previous || otherPronouns != edit.otherPronouns) {
                offerUndo(SavedEdit.LinkBefore(saved.id, previous, edit.other, otherPronouns))
            }
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
        val friend = state.value.detail?.friend
        viewModelScope.launch {
            links.setRelationToMe(friendId, relation, pronouns)
            if (friend != null && (friend.relationToMe != relation || friend.pronouns != pronouns)) {
                offerUndo(SavedEdit.RelationToMeBefore(friend.relationToMe, friend.pronouns, friend.tracksKeepInTouch))
            }
        }
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

    /** A taste chip renamed in its dialog (issue #102): same place in its list; a name it already has just merges. */
    fun onRenameTaste(slot: TasteSlot, old: String, new: String) {
        val name = new.trim()
        if (name.isEmpty() || name == old) return
        fun List<String>.renamed() = map { if (it == old) name else it }.distinct()
        fun Map<String, Instant>.renamed() = this[old]?.let { at -> (this - old) + (name to (this[name] ?: at)) } ?: this
        edit { f ->
            when (slot) {
                TasteSlot.FOOD -> f.copy(food = f.food.renamed(), foodAddedAt = f.foodAddedAt.renamed())
                TasteSlot.DRINK -> f.copy(drinks = f.drinks.renamed(), drinksAddedAt = f.drinksAddedAt.renamed())
                TasteSlot.INTEREST -> f.copy(interests = f.interests.renamed(), interestsAddedAt = f.interestsAddedAt.renamed())
                TasteSlot.LIKE -> f.copy(likes = f.likes.renamed(), likesAddedAt = f.likesAddedAt.renamed())
                TasteSlot.DISLIKE -> f.copy(dislikes = f.dislikes.renamed())
            }
        }
    }

    /** A taste chip deleted (issue #102), with Undo. */
    fun onDeleteTaste(slot: TasteSlot, value: String) = editWithUndo(before = { SavedEdit.TastesBefore(it) }, FriendDetailMessage.Removed) { f ->
        when (slot) {
            TasteSlot.FOOD -> f.copy(food = f.food - value, foodAddedAt = f.foodAddedAt - value)
            TasteSlot.DRINK -> f.copy(drinks = f.drinks - value, drinksAddedAt = f.drinksAddedAt - value)
            TasteSlot.INTEREST -> f.copy(interests = f.interests - value, interestsAddedAt = f.interestsAddedAt - value)
            TasteSlot.LIKE -> f.copy(likes = f.likes - value, likesAddedAt = f.likesAddedAt - value)
            TasteSlot.DISLIKE -> f.copy(dislikes = f.dislikes - value)
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
            links.addKid(friendId, draft)?.let { kid ->
                offerUndo(SavedEdit.KidAdded(kid), FriendDetailMessage.KidAdded(draft.name.trim().substringBefore(' ')))
            }
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

    /** Deletes an important moment, with Undo (issue #102). */
    fun onRemoveEvent(id: String) = editWithUndo(before = { SavedEdit.MomentsBefore(it.importantEvents) }, FriendDetailMessage.Removed) { f ->
        f.copy(importantEvents = f.importantEvents.filterNot { it.id == id })
    }

    /** An important moment edited in its dialog (issue #102). */
    fun onEditEvent(id: String, title: String, date: LocalDate, remindDaysBefore: Int) {
        if (title.isBlank()) return
        edit { f ->
            f.copy(
                importantEvents = f.importantEvents.map {
                    if (it.id == id) it.copy(title = title.trim(), date = date, remindDaysBefore = remindDaysBefore.coerceAtLeast(0)) else it
                }.sortedBy { it.date },
            )
        }
    }

    // --- What's new (life updates) ----------------------------------------------------------------------------------

    /** "New job", "moved to Porto"… Leads their catch-up story until you meet. */
    fun onAddLifeUpdate(text: String) {
        if (text.isBlank()) return
        val update = LifeUpdate(ids.next(), text.trim(), clock.now())
        edit { f -> f.copy(lifeUpdates = f.lifeUpdates + update) }
    }

    /** Deletes a "What's new" line, with Undo (issue #102). */
    fun onRemoveLifeUpdate(id: String) = editWithUndo(before = { SavedEdit.NewsBefore(it.lifeUpdates) }, FriendDetailMessage.Removed) { f ->
        f.copy(lifeUpdates = f.lifeUpdates.filterNot { it.id == id })
    }

    /** A "What's new" line edited in its dialog (issue #102): the text changes, when you heard it doesn't. */
    fun onEditLifeUpdate(id: String, text: String) {
        if (text.isBlank()) return
        edit { f -> f.copy(lifeUpdates = f.lifeUpdates.map { if (it.id == id) it.copy(text = text.trim()) else it }) }
    }

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

    /** Adds a profile (issue #118): what was typed is cleaned by [Socials.parse]; the same one twice is kept once. */
    fun onAddSocial(network: SocialNetwork, input: String) {
        val link = Socials.parse(network, input) ?: return
        edit { f -> if (link in f.socials) f else f.copy(socials = f.socials + link) }
    }

    fun onRemoveSocial(link: SocialLink) = editWithUndo(before = {
        SavedEdit.LinksBefore(it.socials, it.websites)
    }, FriendDetailMessage.Removed) { f ->
        f.copy(socials = f.socials - link)
    }

    /** Adds a website (issue #118), with https:// when none was typed. Never fetched. */
    fun onAddWebsite(input: String) {
        val url = Socials.website(input) ?: return
        edit { f -> if (url in f.websites) f else f.copy(websites = f.websites + url) }
    }

    fun onRemoveWebsite(url: String) = editWithUndo(before = { SavedEdit.LinksBefore(it.socials, it.websites) }, FriendDetailMessage.Removed) { f ->
        f.copy(websites = f.websites - url)
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

    /** A gift's title and note edited (a gift given opens here from the timeline, issue #102). */
    fun onEditGift(id: String, title: String, note: String) {
        if (title.isBlank()) return
        edit { f -> f.copy(gifts = f.gifts.map { if (it.id == id) it.copy(title = title.trim(), note = note.trim()) else it }) }
    }

    /** Deletes a timeline entry from the page's History (issue #102), with Undo. Gifts given aren't deleted here. */
    fun onDeleteEntry(entry: TimelineEntry) {
        if (!entry.deletable) return
        viewModelScope.launch {
            deleteEntry(entry)
            offerUndo(SavedEdit.EntryRemoved(entry), FriendDetailMessage.Removed)
        }
    }

    fun onRemoveGift(id: String) = edit { f -> f.copy(gifts = f.gifts.filterNot { it.id == id }) }

    fun onMarkGiftGiven(id: String, occasion: GiftOccasion?) {
        val today = clock.today()
        editWithUndo(before = { f -> SavedEdit.GiftBefore(f.gifts.first { it.id == id }) }) { f ->
            f.copy(gifts = f.gifts.map { if (it.id == id) it.copy(givenOn = today, occasion = occasion) else it })
        }
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

    /**
     * A picture picked and cropped wide for the background of their page (issue #144); null when the picker was
     * cancelled, empty when the picture couldn't be read. Like a circle's cover, setting or changing it says nothing
     * (the page shows it); only a remove offers Undo.
     */
    fun onBackgroundPicked(image: ByteArray?) {
        if (image == null) return
        viewModelScope.launch {
            if (backgrounds.set(friendId, image).isFailure) say(FriendDetailMessage.PhotoUnreadable)
        }
    }

    /** Removes the background image: the page's usual gradient shows again, with Undo. */
    fun onRemoveBackground() {
        viewModelScope.launch {
            val before = backgrounds.remove(friendId) ?: return@launch
            offerUndo(SavedEdit.BackgroundBefore(before), FriendDetailMessage.BackgroundRemoved)
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

    fun onOpenReminderEditor() = local.update { it.copy(showReminderEditor = true, editingReminder = null) }

    /** "Edit" on one of their reminders (issue #99). */
    fun onEditReminder(reminder: Reminder) = local.update { it.copy(showReminderEditor = true, editingReminder = reminder) }

    /**
     * A tap on a reminder on their timeline (issue #135, [ReminderTarget]): a circle's opens the circle (returned for the
     * screen to open); one for several people, nobody or just this person (whose page this already is) opens the editor.
     */
    fun onOpenReminder(reminder: Reminder): ReminderTarget {
        val target = ReminderTarget.of(reminder).let {
            if (it is ReminderTarget.Person &&
                it.friendId == friendId
            ) {
                ReminderTarget.Edit(reminder.id)
            } else {
                it
            }
        }
        if (target is ReminderTarget.Edit) onEditReminder(reminder)
        return target
    }

    fun onDismissReminderEditor() = local.update { it.copy(showReminderEditor = false, editingReminder = null) }

    /** A new reminder (about this person when nobody else is picked), or the one being edited. */
    fun onSaveReminder(draft: ReminderDraft) {
        if (draft.title.isBlank()) return
        val editing = local.value.editingReminder
        viewModelScope.launch {
            val due = draft.dueAt(clock.timeZone)
            if (editing == null) {
                addReminder(draft.title, draft.friendIds.ifEmpty { listOf(friendId) }, due, draft.circleId, draft.repeat, draft.important)
            } else {
                editReminder(editing, draft.title, draft.friendIds, due, draft.circleId, draft.repeat, draft.important)
            }
            local.update { it.copy(showReminderEditor = false, editingReminder = null, message = FriendDetailMessage.ReminderSaved) }
        }
    }

    /** "Done": closes it (a repeating one rolls on). Never asks "did you meet?" (issue #99). */
    fun onReminderDone(reminder: Reminder) {
        viewModelScope.launch {
            val saved = completeReminder(reminder)
            val notice = ReminderNotice.Done(saved.dueAt.takeIf { !saved.done })
            local.update { it.copy(reminderUndo = reminder, message = FriendDetailMessage.ReminderChanged(notice)) }
        }
    }

    fun onSnoozeReminder(reminder: Reminder, snooze: ReminderSnooze) {
        viewModelScope.launch {
            val until = snoozeReminder(reminder, snooze).snoozedUntil ?: return@launch
            local.update { it.copy(reminderUndo = null, message = FriendDetailMessage.ReminderChanged(ReminderNotice.Snoozed(until))) }
        }
    }

    fun onDeleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            deleteReminder(reminder.id)
            local.update { it.copy(reminderUndo = reminder, message = FriendDetailMessage.ReminderChanged(ReminderNotice.Deleted)) }
        }
    }

    /** Undo on the Done or Delete snackbar: puts the reminder back as it was. */
    fun onUndoReminder() {
        val reminder = local.value.reminderUndo ?: return
        local.update { it.copy(reminderUndo = null) }
        viewModelScope.launch { restoreReminder(reminder) }
    }

    // --- Rhythm extras ---------------------------------------------------------------------------------------------

    fun onTogglePause() {
        val paused = state.value.detail?.friend?.catchUpPaused ?: return
        viewModelScope.launch { setCatchUpPaused(friendId, !paused) }
    }

    /** Issue #115: keep-in-touch tracking on or off for this person. */
    fun onToggleTracking() = edit { it.copy(tracksKeepInTouch = !it.tracksKeepInTouch) }

    /** Decision 2026-11-21 ("Catch-up · what counts"): the Settings default, in person only, or any contact. */
    fun onSetCatchUpCounts(counts: CatchUpCounts) = edit { it.copy(catchUpCounts = counts) }

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

    /**
     * An answer from "How did it go?" or the plan's menu on the friend page (issue #100), for the open occurrence.
     * "It happened" opens the "We met" panel through navigation instead. Cancel and Delete offer Undo.
     */
    fun onAnswerPlan(plan: Plan, answer: PlanAnswer, wholeSeries: Boolean = false) {
        if (answer == PlanAnswer.HAPPENED) return
        viewModelScope.launch {
            val before = closePlan(plan.id, answer, day = plan.date, wholeSeries = wholeSeries)
            val message = when (answer) {
                PlanAnswer.DIDNT_HAPPEN, PlanAnswer.HAPPENED -> FriendDetailMessage.PlanClosed
                PlanAnswer.NOT_ATTENDED -> FriendDetailMessage.PlanNotAttended
                PlanAnswer.CANCEL -> FriendDetailMessage.PlanCancelled
                PlanAnswer.DELETE -> FriendDetailMessage.PlanDeleted
            }
            val undo = before.takeIf { answer == PlanAnswer.CANCEL || answer == PlanAnswer.DELETE }
            local.update { it.copy(undoPlan = undo, message = message) }
        }
    }

    fun onUndoPlan() {
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

    /** "+ Note", or a note tapped (issue #119): its own popup with the text, the date and photos and videos. */
    fun onOpenNote(entry: JournalEntry?) = local.update { it.copy(noteEditing = NoteEditing(entry, entry?.media.orEmpty())) }

    fun onCloseNote() = local.update { it.copy(noteEditing = null) }

    /** Photos and videos picked in the note popup: thumbnails are made now; the originals stay in the gallery. */
    fun onNoteMediaPicked(picked: List<PickedNoteMedia>) {
        if (picked.isEmpty() || local.value.noteEditing == null) return
        local.update { it.copy(noteEditing = it.noteEditing?.copy(isAddingMedia = true)) }
        viewModelScope.launch {
            val media = picked.mapNotNull { prepareNoteMedia(it) }
            local.update { ui ->
                val editing = ui.noteEditing ?: return@update ui
                ui.copy(
                    noteEditing = editing.copy(media = editing.media + media, isAddingMedia = false),
                    message = if (media.size < picked.size) FriendDetailMessage.PhotoUnreadable else ui.message,
                )
            }
        }
    }

    fun onRemoveNoteMedia(id: String) = local.update { ui ->
        ui.copy(
            noteEditing = ui.noteEditing?.let { e ->
                e.copy(
                    media = e.media.filterNot {
                        it.id ==
                            id
                    },
                )
            },
        )
    }

    /** Save in the note popup: needs words or media; the first note with media marks the explanation as shown. */
    fun onSaveNote(text: String, date: LocalDate) {
        val editing = local.value.noteEditing ?: return
        viewModelScope.launch {
            val saved = saveNote(friendId, text, date, editing.media, editing.entry) ?: return@launch
            if (saved.media.isNotEmpty() && !state.value.mediaExplained) markMediaExplained()
            local.update { it.copy(noteEditing = null, message = FriendDetailMessage.NoteSaved) }
        }
    }

    /** The Conversations box's Add (issue #117): the picked chat export, imported as on the full timeline. */
    fun onChatPicked(text: String?) {
        if (text.isNullOrBlank()) return
        viewModelScope.launch {
            val result = importChat(friendId, text)
            say(if (result.messages == 0) FriendDetailMessage.ChatInvalid else FriendDetailMessage.ChatImported(result.messages, result.friendSender))
        }
    }

    fun onMessageShown() = local.update {
        it.copy(message = null, undoPlan = null, undoCustomField = null, undoAttachment = null, undoLink = null, undoSave = null, reminderUndo = null)
    }
}
