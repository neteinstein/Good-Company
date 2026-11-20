package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactChannel
import com.neteinstein.goodcompany.core.domain.model.CustomField
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.model.LinkCategory
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanAnswer
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.model.TasteKind
import com.neteinstein.goodcompany.core.domain.usecase.CadenceRules
import com.neteinstein.goodcompany.core.domain.usecase.FamilyAround
import com.neteinstein.goodcompany.core.domain.usecase.KidDraft
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.Tag
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.DateBadge
import com.neteinstein.goodcompany.core.ui.components.EditDeleteMenu
import com.neteinstein.goodcompany.core.ui.components.EditableTimelineEntry
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.core.ui.components.PhotoCropDialog
import com.neteinstein.goodcompany.core.ui.components.PlanEditor
import com.neteinstein.goodcompany.core.ui.components.PlanRow
import com.neteinstein.goodcompany.core.ui.components.ProgressRing
import com.neteinstein.goodcompany.core.ui.components.ReminderCallbacks
import com.neteinstein.goodcompany.core.ui.components.ReminderDraft
import com.neteinstein.goodcompany.core.ui.components.ReminderEditor
import com.neteinstein.goodcompany.core.ui.components.ReminderRow
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.components.SendMessageSheet
import com.neteinstein.goodcompany.core.ui.components.TagEditor
import com.neteinstein.goodcompany.core.ui.components.calendarEntry
import com.neteinstein.goodcompany.core.ui.components.interactionIcon
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.components.text
import com.neteinstein.goodcompany.core.ui.format.isFarDate
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.relativeDayPhrase
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.format.shortLabelText
import com.neteinstein.goodcompany.core.ui.l10n.aiErrorText
import com.neteinstein.goodcompany.core.ui.l10n.closenessLabel
import com.neteinstein.goodcompany.core.ui.l10n.everyNDays
import com.neteinstein.goodcompany.core.ui.l10n.occasionLabel
import com.neteinstein.goodcompany.core.ui.l10n.topicText
import com.neteinstein.goodcompany.core.ui.platform.OpenedPhoto
import com.neteinstein.goodcompany.core.ui.platform.rememberCalendarAdder
import com.neteinstein.goodcompany.core.ui.platform.rememberGalleryPhotoOpener
import com.neteinstein.goodcompany.core.ui.platform.rememberGalleryPhotoPicker
import com.neteinstein.goodcompany.core.ui.platform.rememberImagePicker
import com.neteinstein.goodcompany.core.ui.resources.add
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.close
import com.neteinstein.goodcompany.core.ui.resources.delete
import com.neteinstein.goodcompany.core.ui.resources.edit
import com.neteinstein.goodcompany.core.ui.resources.plan_cancelled
import com.neteinstein.goodcompany.core.ui.resources.plan_closed
import com.neteinstein.goodcompany.core.ui.resources.plan_deleted
import com.neteinstein.goodcompany.core.ui.resources.plan_logged
import com.neteinstein.goodcompany.core.ui.resources.plan_not_attended
import com.neteinstein.goodcompany.core.ui.resources.plan_saved
import com.neteinstein.goodcompany.core.ui.resources.plan_skipped
import com.neteinstein.goodcompany.core.ui.resources.remove
import com.neteinstein.goodcompany.core.ui.resources.remove_item
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.core.ui.resources.undo
import com.neteinstein.goodcompany.core.ui.resources.wish_default
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.birthday_banner_soon
import com.neteinstein.goodcompany.feature.friends.resources.birthday_banner_today
import com.neteinstein.goodcompany.feature.friends.resources.birthday_banner_tomorrow
import com.neteinstein.goodcompany.feature.friends.resources.birthday_send_wishes
import com.neteinstein.goodcompany.feature.friends.resources.chat_count
import com.neteinstein.goodcompany.feature.friends.resources.chat_how
import com.neteinstein.goodcompany.feature.friends.resources.chat_title
import com.neteinstein.goodcompany.feature.friends.resources.closeness_no_rhythm
import com.neteinstein.goodcompany.feature.friends.resources.closeness_sheet_title
import com.neteinstein.goodcompany.feature.friends.resources.coming_up_title
import com.neteinstein.goodcompany.feature.friends.resources.complete_about
import com.neteinstein.goodcompany.feature.friends.resources.complete_family
import com.neteinstein.goodcompany.feature.friends.resources.complete_feeds
import com.neteinstein.goodcompany.feature.friends.resources.complete_gifts
import com.neteinstein.goodcompany.feature.friends.resources.complete_moments
import com.neteinstein.goodcompany.feature.friends.resources.complete_more
import com.neteinstein.goodcompany.feature.friends.resources.complete_occasions
import com.neteinstein.goodcompany.feature.friends.resources.complete_saved
import com.neteinstein.goodcompany.feature.friends.resources.complete_title
import com.neteinstein.goodcompany.feature.friends.resources.contact_call
import com.neteinstein.goodcompany.feature.friends.resources.contact_email
import com.neteinstein.goodcompany.feature.friends.resources.contact_game
import com.neteinstein.goodcompany.feature.friends.resources.contact_long_chat
import com.neteinstein.goodcompany.feature.friends.resources.contact_message
import com.neteinstein.goodcompany.feature.friends.resources.contact_met
import com.neteinstein.goodcompany.feature.friends.resources.contact_other
import com.neteinstein.goodcompany.feature.friends.resources.contact_video
import com.neteinstein.goodcompany.feature.friends.resources.contact_whatsapp
import com.neteinstein.goodcompany.feature.friends.resources.delete_body
import com.neteinstein.goodcompany.feature.friends.resources.delete_body_contact
import com.neteinstein.goodcompany.feature.friends.resources.delete_title
import com.neteinstein.goodcompany.feature.friends.resources.detail_missing
import com.neteinstein.goodcompany.feature.friends.resources.edit_done
import com.neteinstein.goodcompany.feature.friends.resources.feeds_hint
import com.neteinstein.goodcompany.feature.friends.resources.feeds_no_posts
import com.neteinstein.goodcompany.feature.friends.resources.feeds_title
import com.neteinstein.goodcompany.feature.friends.resources.feeds_to_news
import com.neteinstein.goodcompany.feature.friends.resources.gift_lists_link
import com.neteinstein.goodcompany.feature.friends.resources.gifts_given
import com.neteinstein.goodcompany.feature.friends.resources.gifts_given_on
import com.neteinstein.goodcompany.feature.friends.resources.gifts_hint
import com.neteinstein.goodcompany.feature.friends.resources.gifts_ideas
import com.neteinstein.goodcompany.feature.friends.resources.gifts_mark_given
import com.neteinstein.goodcompany.feature.friends.resources.gifts_none_found
import com.neteinstein.goodcompany.feature.friends.resources.gifts_suggest
import com.neteinstein.goodcompany.feature.friends.resources.gifts_suggestions
import com.neteinstein.goodcompany.feature.friends.resources.gifts_title
import com.neteinstein.goodcompany.feature.friends.resources.ideas_dismiss
import com.neteinstein.goodcompany.feature.friends.resources.ideas_find
import com.neteinstein.goodcompany.feature.friends.resources.ideas_hint_ai
import com.neteinstein.goodcompany.feature.friends.resources.ideas_hint_ai_chats
import com.neteinstein.goodcompany.feature.friends.resources.ideas_hint_no_ai
import com.neteinstein.goodcompany.feature.friends.resources.ideas_open
import com.neteinstein.goodcompany.feature.friends.resources.ideas_setup
import com.neteinstein.goodcompany.feature.friends.resources.moments_hint
import com.neteinstein.goodcompany.feature.friends.resources.moments_reminder
import com.neteinstein.goodcompany.feature.friends.resources.moments_title
import com.neteinstein.goodcompany.feature.friends.resources.msg_added_as_person
import com.neteinstein.goodcompany.feature.friends.resources.msg_added_to_news
import com.neteinstein.goodcompany.feature.friends.resources.msg_call_logged_short
import com.neteinstein.goodcompany.feature.friends.resources.msg_entry_removed
import com.neteinstein.goodcompany.feature.friends.resources.msg_feed_added
import com.neteinstein.goodcompany.feature.friends.resources.msg_feed_error
import com.neteinstein.goodcompany.feature.friends.resources.msg_feed_not_found
import com.neteinstein.goodcompany.feature.friends.resources.msg_field_removed
import com.neteinstein.goodcompany.feature.friends.resources.msg_ideas_found
import com.neteinstein.goodcompany.feature.friends.resources.msg_ideas_none
import com.neteinstein.goodcompany.feature.friends.resources.msg_link_removed
import com.neteinstein.goodcompany.feature.friends.resources.msg_photo_unreadable
import com.neteinstein.goodcompany.feature.friends.resources.msg_reminder_saved
import com.neteinstein.goodcompany.feature.friends.resources.msg_saved
import com.neteinstein.goodcompany.feature.friends.resources.news_add
import com.neteinstein.goodcompany.feature.friends.resources.news_added
import com.neteinstein.goodcompany.feature.friends.resources.news_hint
import com.neteinstein.goodcompany.feature.friends.resources.news_title
import com.neteinstein.goodcompany.feature.friends.resources.notes_add
import com.neteinstein.goodcompany.feature.friends.resources.notes_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.notes_title
import com.neteinstein.goodcompany.feature.friends.resources.occasions_hint
import com.neteinstein.goodcompany.feature.friends.resources.occasions_title
import com.neteinstein.goodcompany.feature.friends.resources.plans_add
import com.neteinstein.goodcompany.feature.friends.resources.saved_removed
import com.neteinstein.goodcompany.feature.friends.resources.snooze_week
import com.neteinstein.goodcompany.feature.friends.resources.snooze_weekend
import com.neteinstein.goodcompany.feature.friends.resources.talk_title
import com.neteinstein.goodcompany.feature.friends.resources.tastes_add_title
import com.neteinstein.goodcompany.feature.friends.resources.tastes_dislikes_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.tastes_doesnt_like
import com.neteinstein.goodcompany.feature.friends.resources.tastes_drink
import com.neteinstein.goodcompany.feature.friends.resources.tastes_drink_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.tastes_edit_title
import com.neteinstein.goodcompany.feature.friends.resources.tastes_food
import com.neteinstein.goodcompany.feature.friends.resources.tastes_food_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.tastes_interests
import com.neteinstein.goodcompany.feature.friends.resources.tastes_interests_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.tastes_title
import com.neteinstein.goodcompany.feature.friends.resources.timeline_empty
import com.neteinstein.goodcompany.feature.friends.resources.timeline_log
import com.neteinstein.goodcompany.feature.friends.resources.timeline_more_options
import com.neteinstein.goodcompany.feature.friends.resources.timeline_title
import com.neteinstein.goodcompany.feature.friends.resources.topics_ai
import com.neteinstein.goodcompany.feature.friends.resources.topics_from_notes
import com.neteinstein.goodcompany.feature.friends.resources.touch_called
import com.neteinstein.goodcompany.feature.friends.resources.touch_from_circle
import com.neteinstein.goodcompany.feature.friends.resources.touch_goal
import com.neteinstein.goodcompany.feature.friends.resources.touch_last_caught_up
import com.neteinstein.goodcompany.feature.friends.resources.touch_last_together
import com.neteinstein.goodcompany.feature.friends.resources.touch_never
import com.neteinstein.goodcompany.feature.friends.resources.touch_overdue
import com.neteinstein.goodcompany.feature.friends.resources.touch_paused
import com.neteinstein.goodcompany.feature.friends.resources.touch_pick
import com.neteinstein.goodcompany.feature.friends.resources.touch_resume
import com.neteinstein.goodcompany.feature.friends.resources.touch_snoozed
import com.neteinstein.goodcompany.feature.friends.resources.touch_unsnooze
import com.neteinstein.goodcompany.feature.friends.resources.wish_gift_hint
import com.neteinstein.goodcompany.feature.friends.resources.wish_title
import com.neteinstein.goodcompany.feature.friends.ui.components.AddFeedDialog
import com.neteinstein.goodcompany.feature.friends.ui.components.GiftDialog
import com.neteinstein.goodcompany.feature.friends.ui.components.GiftGivenDialog
import com.neteinstein.goodcompany.feature.friends.ui.components.ImportantEventDialog
import com.neteinstein.goodcompany.feature.friends.ui.components.KinshipCard
import com.neteinstein.goodcompany.feature.friends.ui.components.LifeUpdateDialog
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

@Composable
fun FriendDetailScreen(
    friendId: FriendId,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStory: () -> Unit = {},
    onOpenFriend: (FriendId) -> Unit = {},
    onOpenTimeline: () -> Unit = {},
    onLog: () -> Unit = {},
    /** "+ Note" on Notes (issue #103): the Log sheet on a dated note for this person. */
    onAddNote: () -> Unit = {},
    /** The Log sheet in edit mode on this timeline entry ([TimelineEntry.key], issue #102). */
    onEditEntry: (String) -> Unit = {},
    /** A plan's "Did you meet?" yes opens the small "We met" panel on that occurrence (issues #43, #47). */
    onLogPlan: (planId: String, day: LocalDate) -> Unit = { _, _ -> },
    /** "Together" opens the small "We met" panel (issue #47). */
    onWeMet: () -> Unit = {},
    /** A circle chip: that circle's page (issue #88). */
    onOpenCircle: (String) -> Unit = {},
    /** "New circle with <name>": the circle editor with this person already in (issue #70). */
    onNewCircle: () -> Unit = {},
    onOpenGiftLists: () -> Unit = {},
    viewModel: FriendDetailViewModel = koinViewModel(key = friendId.value) { parametersOf(friendId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // F14: a picked picture goes through the crop editor first; one the picker couldn't read goes straight on (it says so).
    var cropping by remember { mutableStateOf<ByteArray?>(null) }
    val pickPhoto = rememberImagePicker { image ->
        if (image != null && image.isNotEmpty()) cropping = image else viewModel.onPhotoPicked(image)
    }
    val pickSavedPhoto = rememberGalleryPhotoPicker { picked ->
        picked?.let { viewModel.onAddSavedPhoto(it.image, it.ref, it.takenOn) }
    }
    val photoOpener = rememberGalleryPhotoOpener()
    val addToCalendar = rememberCalendarAdder()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    FriendDetailContent(
        state = state,
        actions = FriendDetailActions(
            onBack = onBack,
            onOpenSettings = onOpenSettings,
            onOpenStory = onOpenStory,
            onOpenFriend = onOpenFriend,
            onSaveAbout = viewModel::onSaveAbout,
            onSetPreferredChannel = viewModel::onSetPreferredChannel,
            onSaveRelative = viewModel::onSaveRelative,
            onRemoveRelative = viewModel::onRemoveRelative,
            onAddTastes = viewModel::onAddTastes,
            onRemoveTaste = viewModel::onRemoveTaste,
            onSaveCustomField = viewModel::onSaveCustomField,
            onRemoveCustomField = viewModel::onRemoveCustomField,
            onUndoRemoveCustomField = viewModel::onUndoRemoveCustomField,
            onAddLifeUpdate = viewModel::onAddLifeUpdate,
            onRemoveLifeUpdate = viewModel::onRemoveLifeUpdate,
            onEditLifeUpdate = viewModel::onEditLifeUpdate,
            onEditEvent = viewModel::onEditEvent,
            onRenameTaste = viewModel::onRenameTaste,
            onDeleteTaste = viewModel::onDeleteTaste,
            onEditGift = viewModel::onEditGift,
            onEditEntry = onEditEntry,
            onDeleteEntry = viewModel::onDeleteEntry,
            onAddFeed = viewModel::onAddFeed,
            onRemoveFeed = viewModel::onRemoveFeed,
            onFeedPostToNews = viewModel::onFeedPostToNews,
            onClosenessChange = viewModel::onClosenessChange,
            onToggleBirthdayNotifications = viewModel::onToggleBirthdayNotifications,
            onToggleSuggestEvents = viewModel::onToggleSuggestEvents,
            onMeetEveryChange = viewModel::onMeetEveryChange,
            onSnooze = viewModel::onSnooze,
            onClearSnooze = viewModel::onClearSnooze,
            onCalled = viewModel::onCalled,
            onSaveBirthday = viewModel::onSaveBirthday,
            onRename = viewModel::onRename,
            onAddLikes = viewModel::onAddLikes,
            onRemoveLike = viewModel::onRemoveLike,
            onAddDislikes = viewModel::onAddDislikes,
            onRemoveDislike = viewModel::onRemoveDislike,
            onAddNote = onAddNote,
            onAddKid = viewModel::onAddKid,
            onSetShowInMyList = viewModel::onSetShowInMyList,
            onConfirmKinship = viewModel::onConfirmKinship,
            onDismissKinship = viewModel::onDismissKinship,
            onAddEvent = viewModel::onAddEvent,
            onRemoveEvent = viewModel::onRemoveEvent,
            onSaveGift = viewModel::onSaveGift,
            onRemoveGift = viewModel::onRemoveGift,
            onMarkGiftGiven = viewModel::onMarkGiftGiven,
            onSuggestGifts = viewModel::onSuggestGifts,
            onKeepGiftSuggestion = viewModel::onKeepGiftSuggestion,
            onDismissGiftSuggestions = viewModel::onDismissGiftSuggestions,
            onOpenWish = viewModel::onOpenWish,
            onDismissWish = viewModel::onDismissWish,
            onPersonaliseWish = viewModel::onPersonaliseWish,
            onOpenTimeline = onOpenTimeline,
            onLog = onLog,
            onOpenCircle = onOpenCircle,
            onSetInCircle = viewModel::onSetInCircle,
            onNewCircle = onNewCircle,
            onOpenGiftLists = onOpenGiftLists,
            onAddPhoto = pickPhoto,
            onAdjustCrop = { state.detail?.friend?.avatar?.bytes()?.takeIf { it.isNotEmpty() }?.let { cropping = it } },
            onRemovePhoto = viewModel::onRemovePhoto,
            onAddSavedPhoto = pickSavedPhoto,
            onOpenOriginal = photoOpener::open,
            onRemoveAttachment = viewModel::onRemoveAttachment,
            onUndoRemoveAttachment = viewModel::onUndoRemoveAttachment,
            onSaveLink = viewModel::onSaveLink,
            onRemoveLink = viewModel::onRemoveLink,
            onUndoRemoveLink = viewModel::onUndoRemoveLink,
            onUndoSave = viewModel::onUndoSave,
            onSaveRelationToMe = viewModel::onSaveRelationToMe,
            onAddRelativeAsPerson = viewModel::onAddRelativeAsPerson,
            onGenerateIdeas = viewModel::onGenerateIdeas,
            onDismissIdea = viewModel::onDismissIdea,
            onDelete = viewModel::onDelete,
            onWeMet = onWeMet,
            onOpenReminderEditor = viewModel::onOpenReminderEditor,
            onDismissReminderEditor = viewModel::onDismissReminderEditor,
            onSaveReminder = viewModel::onSaveReminder,
            reminders = ReminderCallbacks(
                onDone = viewModel::onReminderDone,
                onSnooze = viewModel::onSnoozeReminder,
                onEdit = viewModel::onEditReminder,
                onDelete = viewModel::onDeleteReminder,
                onOpenFriend = onOpenFriend,
                onOpenCircle = onOpenCircle,
            ),
            onUndoReminder = viewModel::onUndoReminder,
            onMessageShown = viewModel::onMessageShown,
            onTogglePause = viewModel::onTogglePause,
            onToggleTracking = viewModel::onToggleTracking,
            onToggleContactCounts = viewModel::onToggleContactCounts,
            onToggleOccasion = viewModel::onToggleOccasion,
            onOpenPlanEditor = viewModel::onOpenPlanEditor,
            onDismissPlanEditor = viewModel::onDismissPlanEditor,
            onSavePlan = viewModel::onSavePlan,
            onAnswerPlan = viewModel::onAnswerPlan,
            onSkipPlan = viewModel::onSkipPlan,
            onLogPlan = onLogPlan,
            onUndoPlan = viewModel::onUndoPlan,
            onAddPlanToCalendar = { plan, friends, circle -> addToCalendar(plan.calendarEntry(friends, circle = circle)) },
        ),
    )
    cropping?.let { image ->
        PhotoCropDialog(image, onDismiss = { cropping = null }, onCropped = { cropped ->
            cropping = null
            viewModel.onPhotoPicked(cropped)
        })
    }
}

/** All UI events of the detail screen, so the content composable stays previewable. */
data class FriendDetailActions(
    val onBack: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenStory: () -> Unit = {},
    val onOpenFriend: (FriendId) -> Unit = {},
    val onSaveAbout: (AboutEdit) -> Unit = {},
    /** "Message them first on" in edit mode, saved on choice (issue #90). */
    val onSetPreferredChannel: (ContactChannel?) -> Unit = {},
    val onSaveRelative: (RelativeEdit) -> Unit = {},
    val onRemoveRelative: (String) -> Unit = {},
    val onAddTastes: (TasteKind, List<String>) -> Unit = { _, _ -> },
    val onRemoveTaste: (TasteKind, String) -> Unit = { _, _ -> },
    val onSaveCustomField: (id: String?, label: String, value: String) -> Unit = { _, _, _ -> },
    val onRemoveCustomField: (String) -> Unit = {},
    val onUndoRemoveCustomField: () -> Unit = {},
    /** "Add a photo" on the Saved card (the gallery picker that keeps a reference). */
    val onAddSavedPhoto: () -> Unit = {},
    val onOpenOriginal: suspend (PhotoRef) -> OpenedPhoto = { OpenedPhoto.Unavailable },
    val onRemoveAttachment: (Attachment) -> Unit = {},
    val onUndoRemoveAttachment: () -> Unit = {},
    val onSaveLink: (LinkEdit) -> Unit = {},
    val onRemoveLink: (String) -> Unit = {},
    val onUndoRemoveLink: () -> Unit = {},
    /** Undo for a choice or sheet that saved on its own (#64). */
    val onUndoSave: () -> Unit = {},
    val onSaveRelationToMe: (LinkType?, Pronouns?) -> Unit = { _, _ -> },
    val onAddRelativeAsPerson: (String) -> Unit = {},
    val onAddLifeUpdate: (String) -> Unit = {},
    val onRemoveLifeUpdate: (String) -> Unit = {},
    // Issue #102: edit and delete every entry.
    val onEditLifeUpdate: (id: String, text: String) -> Unit = { _, _ -> },
    val onEditEvent: (id: String, title: String, date: LocalDate, remindDaysBefore: Int) -> Unit = { _, _, _, _ -> },
    val onRenameTaste: (TasteSlot, old: String, new: String) -> Unit = { _, _, _ -> },
    val onDeleteTaste: (TasteSlot, String) -> Unit = { _, _ -> },
    val onEditGift: (id: String, title: String, note: String) -> Unit = { _, _, _ -> },
    /** Meetups, contacts and journal entries open the Log sheet in edit mode ([TimelineEntry.key]). */
    val onEditEntry: (String) -> Unit = {},
    val onDeleteEntry: (TimelineEntry) -> Unit = {},
    val onAddFeed: (String) -> Unit = {},
    val onRemoveFeed: (String) -> Unit = {},
    val onFeedPostToNews: (String) -> Unit = {},
    val onClosenessChange: (Closeness) -> Unit = {},
    val onToggleBirthdayNotifications: () -> Unit = {},
    val onToggleSuggestEvents: () -> Unit = {},
    val onMeetEveryChange: (Int?) -> Unit = {},
    val onSnooze: (SnoozeOption) -> Unit = {},
    val onClearSnooze: () -> Unit = {},
    val onCalled: () -> Unit = {},
    /** The birthday sheet: the birthday (year optional, F4) and the reminder switch (F10). */
    val onSaveBirthday: (Birthday?, notify: Boolean) -> Unit = { _, _ -> },
    /** F19: the name given in the app; null goes back to the contact's name. */
    val onRename: (String?) -> Unit = {},
    val onAddLikes: (List<String>) -> Unit = {},
    val onRemoveLike: (String) -> Unit = {},
    val onAddDislikes: (List<String>) -> Unit = {},
    val onRemoveDislike: (String) -> Unit = {},
    /** "+ Note": a new dated note (issue #103). */
    val onAddNote: () -> Unit = {},
    val onAddKid: (KidDraft) -> Unit = {},
    val onSetShowInMyList: (Boolean) -> Unit = {},
    val onConfirmKinship: (LinkType) -> Unit = {},
    val onDismissKinship: () -> Unit = {},
    val onAddEvent: (String, LocalDate, Int) -> Unit = { _, _, _ -> },
    val onRemoveEvent: (String) -> Unit = {},
    val onSaveGift: (title: String, note: String) -> Unit = { _, _ -> },
    val onRemoveGift: (String) -> Unit = {},
    val onMarkGiftGiven: (String, GiftOccasion?) -> Unit = { _, _ -> },
    val onSuggestGifts: () -> Unit = {},
    val onKeepGiftSuggestion: (String) -> Unit = {},
    val onDismissGiftSuggestions: () -> Unit = {},
    val onOpenWish: () -> Unit = {},
    val onDismissWish: () -> Unit = {},
    val onPersonaliseWish: () -> Unit = {},
    val onOpenTimeline: () -> Unit = {},
    val onLog: () -> Unit = {},
    /** A circle chip: People filtered by that circle. */
    val onOpenCircle: (String) -> Unit = {},
    /** A tick in the "Circles" sheet (issues #70, #90): the only way in or out of a circle here; saves at once, with Undo. */
    val onSetInCircle: (groupId: String, member: Boolean) -> Unit = { _, _ -> },
    /** "New circle with <name>" in the sheet. */
    val onNewCircle: () -> Unit = {},
    /** "Gift lists" on the gift card (F8). */
    val onOpenGiftLists: () -> Unit = {},
    /** Picks a picture for the avatar (then the crop editor), offered for everyone (F14). */
    val onAddPhoto: () -> Unit = {},
    /** Crops the photo shown again. */
    val onAdjustCrop: () -> Unit = {},
    /** Removes the photo set in the app. */
    val onRemovePhoto: () -> Unit = {},
    val onGenerateIdeas: () -> Unit = {},
    val onDismissIdea: (String) -> Unit = {},
    val onDelete: () -> Unit = {},
    /** "Together": opens the "We met" panel. */
    val onWeMet: () -> Unit = {},
    val onOpenReminderEditor: () -> Unit = {},
    val onDismissReminderEditor: () -> Unit = {},
    val onSaveReminder: (ReminderDraft) -> Unit = {},
    /** Done · Snooze · Edit · Delete on their reminders (issue #99). */
    val reminders: ReminderCallbacks = ReminderCallbacks(onDone = {}, onSnooze = { _, _ -> }, onEdit = {}, onDelete = {}),
    val onUndoReminder: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onTogglePause: () -> Unit = {},
    val onToggleTracking: () -> Unit = {},
    val onToggleContactCounts: () -> Unit = {},
    val onToggleOccasion: (Occasion) -> Unit = {},
    val onOpenPlanEditor: (Plan?) -> Unit = {},
    val onDismissPlanEditor: () -> Unit = {},
    val onSavePlan: (PlanInput) -> Unit = {},
    /** "How did it go?" and the plan menu (issue #100), except "It happened" (that's [onLogPlan]). */
    val onAnswerPlan: (Plan, PlanAnswer, wholeSeries: Boolean) -> Unit = { _, _, _ -> },
    val onSkipPlan: (Plan) -> Unit = {},
    /** "It happened": open the "We met" panel on that occurrence (a circle plan ticks who came, F20). */
    val onLogPlan: (planId: String, day: LocalDate) -> Unit = { _, _ -> },
    val onUndoPlan: () -> Unit = {},
    val onAddPlanToCalendar: (Plan, List<Friend>, FriendGroup?) -> Unit = { _, _, _ -> },
)

internal val RHYTHMS = listOf(7, 14, 30, 90)

/** Localised snackbar text for a [FriendDetailMessage]. */
suspend fun FriendDetailMessage.text(zone: TimeZone = TimeZone.UTC, today: LocalDate = LocalDate(2000, 1, 1)): String = when (this) {
    is FriendDetailMessage.ReminderChanged -> notice.text(zone, today)
    FriendDetailMessage.CallLogged -> getString(Res.string.msg_call_logged_short)
    is FriendDetailMessage.Snoozed -> getString(Res.string.touch_snoozed, until.shortLabelText())
    is FriendDetailMessage.IdeasFound -> if (count ==
        0
    ) {
        getString(Res.string.msg_ideas_none)
    } else {
        getPluralString(Res.plurals.msg_ideas_found, count, count)
    }
    FriendDetailMessage.NoNewGifts -> getString(Res.string.gifts_none_found)
    is FriendDetailMessage.AiError -> aiErrorText(error)
    FriendDetailMessage.ReminderSaved -> getString(Res.string.msg_reminder_saved)
    FriendDetailMessage.PlanSaved -> getString(CoreRes.string.plan_saved)
    FriendDetailMessage.PlanCancelled -> getString(CoreRes.string.plan_cancelled)
    FriendDetailMessage.PlanDeleted -> getString(CoreRes.string.plan_deleted)
    FriendDetailMessage.PlanNotAttended -> getString(CoreRes.string.plan_not_attended)
    FriendDetailMessage.PlanLogged -> getString(CoreRes.string.plan_logged)
    FriendDetailMessage.PlanClosed -> getString(CoreRes.string.plan_closed)
    FriendDetailMessage.PlanSkipped -> getString(CoreRes.string.plan_skipped)
    is FriendDetailMessage.FeedAdded -> getString(Res.string.msg_feed_added, title)
    FriendDetailMessage.FeedNotFound -> getString(Res.string.msg_feed_not_found)
    FriendDetailMessage.FeedError -> getString(Res.string.msg_feed_error)
    FriendDetailMessage.AddedToNews -> getString(Res.string.msg_added_to_news)
    FriendDetailMessage.PhotoUnreadable -> getString(Res.string.msg_photo_unreadable)
    is FriendDetailMessage.CustomFieldRemoved -> getString(Res.string.msg_field_removed, label)
    FriendDetailMessage.AttachmentRemoved -> getString(Res.string.saved_removed)
    FriendDetailMessage.LinkRemoved -> getString(Res.string.msg_link_removed)
    is FriendDetailMessage.AddedAsPerson -> getString(Res.string.msg_added_as_person, name)
    is FriendDetailMessage.KidAdded -> getString(Res.string.msg_added_as_person, name)
    FriendDetailMessage.Saved -> getString(Res.string.msg_saved)
    FriendDetailMessage.Removed -> getString(Res.string.msg_entry_removed)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FriendDetailContent(state: FriendDetailUiState, actions: FriendDetailActions) {
    val snackbar = remember { SnackbarHostState() }
    var familyEditing by remember { mutableStateOf<FamilyEditing?>(null) }
    var addingLink by rememberSaveable { mutableStateOf(false) }
    var linkEditing by remember { mutableStateOf<LinkedPerson?>(null) }
    var editingRelationToMe by rememberSaveable { mutableStateOf(false) }
    var editingAbout by rememberSaveable { mutableStateOf(false) }
    var fieldEditing by remember { mutableStateOf<CustomField?>(null) }
    var addingField by rememberSaveable { mutableStateOf(false) }
    var addingEvent by rememberSaveable { mutableStateOf(false) }
    var addingGift by rememberSaveable { mutableStateOf(false) }
    var addingNews by rememberSaveable { mutableStateOf(false) }
    var addingFeed by rememberSaveable { mutableStateOf(false) }
    var addingTaste by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.feedAdded) { addingFeed = false }
    var givingGift by remember { mutableStateOf<Gift?>(null) }
    // Issue #102: the entry being edited in its own dialog (news, a moment, a gift, a taste).
    var pageEdit by remember { mutableStateOf<PageEdit?>(null) }
    var pickBirthday by rememberSaveable { mutableStateOf(false) }
    var pickLevel by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var photoOptions by rememberSaveable { mutableStateOf(false) }
    var choosingCircles by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var seeAllSaved by rememberSaveable { mutableStateOf(false) }
    var messaging by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<AttachmentView?>(null) }
    // Issue #103: no edit mode; the pencil opens the Edit person popup, Occasions has its own popup. The chosen tab is
    // screen state (it survives rotation, not a restart); the page always opens on Together.
    var editingPerson by rememberSaveable { mutableStateOf(false) }
    var editingOccasions by rememberSaveable { mutableStateOf(false) }
    var tabIndex by rememberSaveable { mutableIntStateOf(PersonTab.TOGETHER.ordinal) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val openLink: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }
    val openAttachment: (Attachment) -> Unit = { a ->
        when (a.kind) {
            AttachmentKind.LINK -> a.url?.let(openLink)
            AttachmentKind.TEXT -> viewing = AttachmentView.Text(a)
            AttachmentKind.PHOTO -> {
                val ref = a.photoRef
                if (ref == null) {
                    viewing = AttachmentView.Photo(a, null)
                } else {
                    scope.launch {
                        when (val opened = actions.onOpenOriginal(ref)) {
                            OpenedPhoto.Shown -> Unit
                            is OpenedPhoto.Loaded -> viewing = AttachmentView.Photo(a, opened.image)
                            OpenedPhoto.Unavailable -> viewing = AttachmentView.Photo(a, null)
                        }
                    }
                }
            }
        }
    }
    val undoLabel = stringResource(CoreRes.string.undo)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val undoable = state.undoPlan != null || state.undoCustomField != null || state.undoAttachment != null || state.undoLink != null ||
            state.undoSave != null || state.reminderUndo != null
        val result = snackbar.showMessage(message.text(state.zone, state.today), if (undoable) undoLabel else null)
        if (result == SnackbarResult.ActionPerformed) {
            when {
                state.reminderUndo != null -> actions.onUndoReminder()
                state.undoSave != null -> actions.onUndoSave()
                state.undoAttachment != null -> actions.onUndoRemoveAttachment()
                state.undoLink != null -> actions.onUndoRemoveLink()
                state.undoCustomField != null -> actions.onUndoRemoveCustomField()
                state.undoPlan != null -> actions.onUndoPlan()
            }
        }
        actions.onMessageShown()
    }

    Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        val detail = state.detail
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator() else Text(stringResource(Res.string.detail_missing))
            }
            return@Scaffold
        }
        val f = detail.friend
        val firstName = f.displayName.substringBefore(' ')
        val tab = PersonTab.entries[tabIndex]
        val facts = PageFacts(state.links, state.attachments.size, hasTopics = detail.topics.isNotEmpty(), aiEnabled = state.aiEnabled)
        val family = remember(f, state.links) { FamilyAround.of(f, state.links) }
        val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
        val pinnedOffset = with(LocalDensity.current) { 128.dp.roundToPx() }

        // The items before the tab's cards: the top, the kinship proposal, the pinned tabs.
        val leadingItems = 1 + (if (detail.kinship != null) 1 else 0) + 1

        /** Switches to [target]'s tab and scrolls to it (the tabs when it isn't shown). */
        fun scrollTo(target: PersonCard) {
            tabIndex = target.tab.ordinal
            val index = ProfileSections.shown(target.tab, f, facts).indexOf(target)
            val lead = leadingItems
            scope.launch {
                withFrameNanos { } // let the new tab lay out its items first
                if (index >= 0) listState.animateScrollToItem(lead + index, -pinnedOffset) else listState.animateScrollToItem(lead - 1)
            }
        }

        /** A chip of "Complete <name>'s profile": that section's add sheet. */
        fun complete(card: PersonCard) {
            when (card) {
                PersonCard.ABOUT -> editingAbout = true
                PersonCard.MORE_ABOUT -> addingField = true
                PersonCard.FAMILY -> familyEditing = FamilyEditing.New
                PersonCard.MOMENTS -> addingEvent = true
                PersonCard.GIFTS -> addingGift = true
                PersonCard.SAVED -> actions.onAddSavedPhoto()
                PersonCard.FEEDS -> addingFeed = true
                PersonCard.OCCASIONS -> editingOccasions = true
                else -> Unit
            }
        }

        val topActions = PersonTopActions(
            onBack = actions.onBack,
            onEditPerson = { editingPerson = true },
            onRemove = { confirmDelete = true },
            onPhoto = { if (f.avatar == null) actions.onAddPhoto() else photoOptions = true },
            onOpenStory = actions.onOpenStory,
            onEditBirthday = { pickBirthday = true },
            onPickCloseness = { pickLevel = true },
            onOpenCircle = actions.onOpenCircle,
            onEditCircles = { choosingCircles = true },
            onOpenFamily = { member -> member.person?.let { actions.onOpenFriend(it.id) } ?: scrollTo(PersonCard.FAMILY) },
            onMoreFamily = { scrollTo(PersonCard.FAMILY) },
            onMessage = { messaging = true },
            onWeMet = actions.onWeMet,
            onRemind = actions.onOpenReminderEditor,
            onAddNews = { addingNews = true },
            onLog = actions.onLog,
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item(key = "hero") { PersonTop(f, state, family, topActions) }

            detail.kinship?.let { proposal ->
                item(key = "kinship") {
                    KinshipCard(
                        proposal,
                        onConfirm = actions.onConfirmKinship,
                        onDismiss = actions.onDismissKinship,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            stickyHeader(key = "tabs") {
                PinnedTabs(f, tab, onTab = { tabIndex = it.ordinal }, collapsed = collapsed, actions = topActions)
            }

            // In-place editing on About stays (issue #103): the About cards always show their editors.
            ProfileSections.shown(tab, f, facts).forEach { card ->
                item(key = card.name) {
                    when (card) {
                        PersonCard.COMING_UP -> ComingUp(f, state, actions)
                        PersonCard.TO_TALK_ABOUT -> ToTalkAbout(f, state, actions, onAddNews = { addingNews = true }, onEdit = { pageEdit = it })
                        PersonCard.TASTES -> Tastes(f, actions, onAdd = { addingTaste = true }, onEdit = { pageEdit = it })
                        PersonCard.NOTES -> Notes(detail.timeline, state, actions)
                        PersonCard.COMPLETE -> CompleteProfile(firstName, ProfileSections.missing(f, facts), onPick = ::complete)
                        PersonCard.ABOUT -> AboutCard(
                            f,
                            detail.others,
                            onEdit = { editingAbout = true },
                            onOpenFriend = actions.onOpenFriend,
                        )
                        PersonCard.MORE_ABOUT -> MoreAboutCard(
                            f,
                            onAdd = { addingField = true },
                            onEdit = { fieldEditing = it },
                            onRemove = actions.onRemoveCustomField,
                        )
                        PersonCard.OCCASIONS -> Occasions(f, onEdit = { editingOccasions = true })
                        PersonCard.FAMILY -> FamilyCard(
                            f,
                            state.today,
                            onAdd = { familyEditing = FamilyEditing.New },
                            onEditRelative = { familyEditing = FamilyEditing.EditRelative(it) },
                            onRemoveRelative = actions.onRemoveRelative,
                            links = state.links,
                            onOpenPerson = actions.onOpenFriend,
                            onEditLink = { linkEditing = it },
                            onAddLink = { addingLink = true },
                            onAddAsPerson = actions.onAddRelativeAsPerson,
                        )
                        PersonCard.OTHER_LINKS -> OtherLinksCard(
                            state.links.filter { it.role.category == LinkCategory.OTHER },
                            actions.onOpenFriend,
                            onEdit = { linkEditing = it },
                        )
                        PersonCard.MOMENTS -> Moments(f, state, actions, onAdd = { addingEvent = true }, onEdit = { pageEdit = it })
                        PersonCard.GIFTS -> Gifts(f, state, actions, onAdd = { addingGift = true }, onGive = { givingGift = it })
                        PersonCard.SAVED -> SavedCard(
                            firstName,
                            state.attachments,
                            state.today,
                            state.zone,
                            onAddPhoto = actions.onAddSavedPhoto,
                            onOpen = openAttachment,
                            onSeeAll = { seeAllSaved = true },
                        )
                        PersonCard.FEEDS -> Feeds(f, state, actions, onAdd = { addingFeed = true })
                        PersonCard.CONVERSATIONS -> Conversations(detail.messageCount)
                        PersonCard.TIMELINE -> TimelineCard(detail.timeline, state, actions, onEdit = { pageEdit = it })
                    }
                }
            }
        }

        if (messaging) MessageSheet(f, onDismiss = { messaging = false })
        if (editingPerson) {
            EditPersonDialog(
                f,
                state,
                actions,
                onEditName = { editingName = true },
                onEditRelationToMe = { editingRelationToMe = true },
                onRemove = {
                    editingPerson = false
                    confirmDelete = true
                },
                onDismiss = { editingPerson = false },
            )
        }
        if (editingOccasions) OccasionsDialog(f, actions.onToggleOccasion, onDismiss = { editingOccasions = false })
        if (pickLevel) {
            ClosenessSheet(f, state.cadenceRules, onPick = {
                pickLevel = false
                actions.onClosenessChange(it)
            }, onDismiss = { pickLevel = false })
        }
        if (addingTaste) {
            AddTasteDialog(firstName, state.tags, actions, onDismiss = { addingTaste = false })
        }
        if (addingLink || linkEditing != null) {
            val linked = state.links.mapTo(HashSet()) { it.person.id }
            LinkSheet(
                friend = f,
                others = detail.others.filter { it.id !in linked },
                circles = detail.circles,
                editing = linkEditing,
                onDismiss = {
                    addingLink = false
                    linkEditing = null
                },
                onSave = {
                    actions.onSaveLink(it)
                    addingLink = false
                    linkEditing = null
                },
                onRemove = {
                    actions.onRemoveLink(it)
                    linkEditing = null
                },
            )
        }
        if (editingRelationToMe) {
            RelationToMeSheet(f, onDismiss = { editingRelationToMe = false }) { relation, pronouns ->
                actions.onSaveRelationToMe(relation, pronouns)
                editingRelationToMe = false
            }
        }
        familyEditing?.let { sheet ->
            FamilySheet(
                firstName = firstName,
                editing = sheet,
                latestYear = state.today.year,
                onDismiss = { familyEditing = null },
                onAddKid = {
                    actions.onAddKid(it)
                    familyEditing = null
                },
                parentIsFamily = f.isFamily,
                onSaveRelative = {
                    actions.onSaveRelative(it)
                    familyEditing = null
                },
                tags = state.tags,
            )
        }
        if (editingAbout) {
            AboutDialog(f, detail.others, onDismiss = { editingAbout = false }) {
                actions.onSaveAbout(it)
                editingAbout = false
            }
        }
        if (addingField || fieldEditing != null) {
            val field = fieldEditing
            CustomFieldDialog(
                field = field,
                suggestions = detail.customFieldLabels,
                onDismiss = {
                    addingField = false
                    fieldEditing = null
                },
                onSave = { label, value ->
                    actions.onSaveCustomField(field?.id, label, value)
                    addingField = false
                    fieldEditing = null
                },
            )
        }
        if (addingEvent) {
            ImportantEventDialog(state.today, { addingEvent = false }) { t, d, b ->
                actions.onAddEvent(t, d, b)
                addingEvent = false
            }
        }
        if (addingFeed) {
            AddFeedDialog(busy = state.isAddingFeed, onDismiss = { addingFeed = false }, onAdd = actions.onAddFeed)
        }
        if (addingNews) {
            LifeUpdateDialog(firstName, onDismiss = { addingNews = false }) { text ->
                actions.onAddLifeUpdate(text)
                addingNews = false
            }
        }
        if (addingGift) {
            GiftDialog(onDismiss = { addingGift = false }) { title, note ->
                actions.onSaveGift(title, note)
                addingGift = false
            }
        }
        pageEdit?.let { edit -> PageEditDialog(edit, firstName, state.today, actions, onDone = { pageEdit = null }) }
        givingGift?.let { gift ->
            GiftGivenDialog(gift, onDismiss = { givingGift = null }) { occasion ->
                actions.onMarkGiftGiven(gift.id, occasion)
                givingGift = null
            }
        }
        if (seeAllSaved) {
            SavedListSheet(
                firstName,
                state.attachments,
                state.today,
                state.zone,
                onOpen = openAttachment,
                onRemove = actions.onRemoveAttachment,
                onDismiss = { seeAllSaved = false },
            )
        }
        viewing?.let { view ->
            AttachmentViewer(
                view,
                onOpenLink = openLink,
                onRemove = {
                    actions.onRemoveAttachment(view.attachment)
                    viewing = null
                },
                onDismiss = { viewing = null },
            )
        }
        if (pickBirthday) {
            BirthdaySheet(f, latestYear = state.today.year, onSave = actions.onSaveBirthday, onDismiss = { pickBirthday = false })
        }
        if (editingName) NameDialog(f, onSave = actions.onRename, onDismiss = { editingName = false })
        if (choosingCircles) {
            CircleSheet(
                f,
                circles = detail.allCircles,
                onToggle = actions.onSetInCircle,
                onNewCircle = {
                    choosingCircles = false
                    actions.onNewCircle()
                },
                onDismiss = { choosingCircles = false },
            )
        }
        if (photoOptions) {
            PhotoOptionsSheet(
                f,
                onReplace = {
                    photoOptions = false
                    actions.onAddPhoto()
                },
                onAdjust = {
                    photoOptions = false
                    actions.onAdjustCrop()
                },
                onRemove = {
                    photoOptions = false
                    actions.onRemovePhoto()
                },
                onDismiss = { photoOptions = false },
            )
        }
        // The people picker lists everyone (issue #89); this person starts chosen.
        val everyone = listOf(f) + detail.others
        if (state.showReminderEditor) {
            ReminderEditor(
                friends = everyone,
                today = state.today,
                onDismiss = actions.onDismissReminderEditor,
                onSave = actions.onSaveReminder,
                preselected = setOf(f.id),
                circles = detail.circles,
                initial = state.editingReminder,
                zone = state.zone,
            )
        }
        state.planEditing?.let { editing ->
            PlanEditor(
                friends = everyone,
                today = state.today,
                onDismiss = actions.onDismissPlanEditor,
                onSave = actions.onSavePlan,
                preselected = setOf(f.id),
                initial = editing.plan,
                circles = detail.groups,
                pickerCircles = detail.circles,
            )
        }
        if (state.showWish) {
            val ideas = f.gifts.filter { it.isIdea }
            SendMessageSheet(
                title = stringResource(Res.string.wish_title, firstName),
                suggestion = state.wishDraft ?: stringResource(CoreRes.string.wish_default, firstName),
                phone = f.phoneNumbers.firstOrNull(),
                onDismiss = actions.onDismissWish,
                preferred = f.preferredChannel,
                email = f.emails.firstOrNull()?.address,
                isGenerating = state.isDraftingWish,
                onPersonalise = if (state.hasAiKey) actions.onPersonaliseWish else null,
                header = if (ideas.isEmpty()) {
                    null
                } else {
                    {
                        InfoPill(
                            stringResource(Res.string.wish_gift_hint, ideas.joinToString(limit = 3) { it.title }),
                            icon = Icons.Rounded.CardGiftcard,
                        )
                    }
                },
            )
        }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                icon = { Icon(Icons.Rounded.Delete, null) },
                title = { Text(stringResource(Res.string.delete_title, f.displayName)) },
                text = { Text(stringResource(if (f.contactId != null) Res.string.delete_body_contact else Res.string.delete_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        actions.onDelete()
                    }) { Text(stringResource(CoreRes.string.remove)) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(CoreRes.string.cancel)) } },
            )
        }
    }
}

/** Blogs / Substack / Letterboxd you follow for them, with their latest posts. */
@Composable
private fun Feeds(f: Friend, state: FriendDetailUiState, actions: FriendDetailActions, onAdd: () -> Unit) {
    val uri = LocalUriHandler.current
    SectionCard(
        stringResource(Res.string.feeds_title),
        icon = Icons.Rounded.RssFeed,
        action = { TextButton(onClick = onAdd) { Text(stringResource(CoreRes.string.add)) } },
    ) {
        if (f.feeds.isEmpty()) Hint(stringResource(Res.string.feeds_hint))
        f.feeds.forEach { feed ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    feed.title.ifBlank { feed.url },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).clickable { (feed.siteUrl ?: feed.url).let(uri::openUri) },
                )
                IconButton(onClick = { actions.onRemoveFeed(feed.url) }) {
                    Icon(Icons.Rounded.Close, stringResource(CoreRes.string.remove_item, feed.title.ifBlank { feed.url }))
                }
            }
            if (feed.items.isEmpty()) Hint(stringResource(Res.string.feeds_no_posts))
            feed.items.take(3).forEach { item ->
                ListItem(
                    modifier = Modifier.clickable(enabled = item.link != null) { item.link?.let(uri::openUri) },
                    headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = item.publishedAt?.let { at -> { Text(relativePast(at, state.now, state.zone)) } },
                    trailingContent = {
                        IconButton(onClick = { actions.onFeedPostToNews(item.title) }) {
                            Icon(Icons.Rounded.AddCircleOutline, stringResource(Res.string.feeds_to_news))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun BirthdayBanner(firstName: String, date: LocalDate, today: LocalDate, onWish: () -> Unit) {
    val days = today.daysUntil(date)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Cake, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                when (days) {
                    0 -> stringResource(Res.string.birthday_banner_today, firstName)
                    1 -> stringResource(Res.string.birthday_banner_tomorrow, firstName)
                    else -> stringResource(Res.string.birthday_banner_soon, firstName, relativeDayPhrase(date, today))
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onWish) { Text(stringResource(Res.string.birthday_send_wishes)) }
        }
    }
}

/**
 * Together's first entry (issues #90, #103): the birthday banner, then "Coming up": "Plan something" and the next
 * plans, their reminders, upcoming moments and the catch-up rhythm line with "I called" and snooze. The catch-up
 * story is the ring on the photo now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComingUp(f: Friend, state: FriendDetailUiState, actions: FriendDetailActions) {
    val detail = state.detail ?: return
    val firstName = f.displayName.substringBefore(' ')
    Column {
        val nextBirthday = f.birthday?.nextOccurrence(state.today)
        if (nextBirthday != null && state.today.daysUntil(nextBirthday) in 0..7) {
            BirthdayBanner(firstName, nextBirthday, state.today, actions.onOpenWish)
        }
        SectionCard(
            stringResource(Res.string.coming_up_title),
            icon = Icons.Rounded.EventAvailable,
            action = { TextButton(onClick = { actions.onOpenPlanEditor(null) }) { Text(stringResource(Res.string.plans_add)) } },
        ) {
            val people = (listOf(f) + detail.hangouts.flatMap { it.friends }).associateBy { it.id }
            detail.plans.forEach { plan ->
                val with = plan.friendIds.mapNotNull(people::get)
                val circle = detail.groups.firstOrNull { it.id == plan.circleId }
                PlanRow(
                    plan = plan,
                    friends = with.filterNot { it.id == f.id },
                    today = state.today,
                    onAnswer = { answer, whole ->
                        // "It happened" opens the "We met" panel (issue #47): a person plan as well as a circle plan.
                        if (answer == PlanAnswer.HAPPENED) actions.onLogPlan(plan.id, plan.date) else actions.onAnswerPlan(plan, answer, whole)
                    },
                    onAddToCalendar = { actions.onAddPlanToCalendar(plan, with, circle) },
                    onChange = { actions.onOpenPlanEditor(plan) },
                    circle = circle,
                    onSkip = { actions.onSkipPlan(plan) },
                )
            }
            // Issue #99: their reminders (and their circles'), with Done · Snooze · Edit · Delete; the chips open the others.
            val everyone = (listOf(f) + detail.others).associateBy { it.id }
            state.reminders.forEach { r ->
                ReminderRow(
                    r,
                    r.friendIds.filterNot { it == f.id }.mapNotNull(everyone::get),
                    state.today,
                    state.zone,
                    state.now,
                    actions.reminders,
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    circle = r.circleId?.let { id -> detail.circles.firstOrNull { it.id == id } },
                )
            }
            f.importantEvents.filter { it.date >= state.today }.sortedBy { it.date }.take(3).forEach { MomentRow(it, state.today) }
            RhythmLine(f, state, actions)
        }
    }
}

/** The catch-up ring and line, "I called", snooze and the last contact of each kind (warmth is in the info box, #103). */
@Composable
private fun RhythmLine(f: Friend, state: FriendDetailUiState, actions: FriendDetailActions) {
    val detail = state.detail ?: return
    val goal = detail.cadenceDays
    // The ring follows the rhythm's own rule (issue #101): the last meetup, or any contact when calls and chats count.
    val last = detail.lastCaughtUp
    val days = last?.toLocalDateTime(state.zone)?.date?.daysUntil(state.today)
    val snoozedUntil = f.snoozedUntil?.takeIf { it >= state.today }
    // Issue #115: no ring, rhythm line or snooze while keep-in-touch tracking is off; "I called" still logs.
    if (f.tracksKeepInTouch) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val progress = when {
                goal != null && days != null -> days.toFloat() / goal
                goal != null -> 1f
                else -> 0f
            }
            val overdue = goal != null && progress >= 1f && snoozedUntil == null && !f.catchUpPaused
            ProgressRing(progress, size = 56.dp, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) {
                Text(days?.toString() ?: "–", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    last?.let {
                        val ago = relativePast(it, state.now, state.zone)
                        stringResource(if (it == detail.lastSeen) Res.string.touch_last_together else Res.string.touch_last_caught_up, ago)
                    } ?: stringResource(Res.string.touch_never),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    when {
                        f.catchUpPaused -> stringResource(Res.string.touch_paused)
                        snoozedUntil != null -> stringResource(Res.string.touch_snoozed, snoozedUntil.shortLabel())
                        goal == null -> stringResource(Res.string.touch_pick)
                        overdue -> stringResource(Res.string.touch_overdue, everyNDays(goal))
                        else -> (goal - (days ?: 0)).let { left -> pluralStringResource(Res.plurals.touch_goal, left, everyNDays(goal), left) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = actions.onCalled) {
            Icon(Icons.Rounded.Call, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.touch_called))
        }
        if (!f.tracksKeepInTouch) {
            Unit
        } else if (f.catchUpPaused) {
            TextButton(onClick = actions.onTogglePause) { Text(stringResource(Res.string.touch_resume)) }
        } else if (snoozedUntil != null) {
            TextButton(onClick = actions.onClearSnooze) { Text(stringResource(Res.string.touch_unsnooze)) }
        } else if (goal != null) {
            var menu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.Snooze, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(Res.string.snooze_week))
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.snooze_weekend)) },
                        leadingIcon = { Icon(Icons.Rounded.Weekend, null) },
                        onClick = {
                            menu = false
                            actions.onSnooze(SnoozeOption.WEEKEND)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.snooze_week)) },
                        leadingIcon = { Icon(Icons.Rounded.Snooze, null) },
                        onClick = {
                            menu = false
                            actions.onSnooze(SnoozeOption.ONE_WEEK)
                        },
                    )
                }
            }
        }
    }
    if (detail.cadenceSource == CadenceRules.Source.CLOSENESS && goal != null) {
        Hint(stringResource(Res.string.touch_from_circle, everyNDays(goal)))
    }
    LastContactRow(detail.lastContactByType, state)
}

/** News (with "Add news"), topics from notes and, while AI is on, the AI ideas (gotcha 33). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToTalkAbout(
    f: Friend,
    state: FriendDetailUiState,
    actions: FriendDetailActions,
    onAddNews: () -> Unit,
    onEdit: (PageEdit) -> Unit,
) {
    val detail = state.detail ?: return
    val firstName = f.displayName.substringBefore(' ')
    SectionCard(
        stringResource(Res.string.talk_title),
        icon = Icons.Rounded.Lightbulb,
        action = { TextButton(onClick = onAddNews) { Text(stringResource(Res.string.news_add)) } },
    ) {
        if (f.lifeUpdates.isNotEmpty()) Label(stringResource(Res.string.news_title), Icons.Rounded.NewReleases)
        f.lifeUpdates.sortedByDescending { it.addedAt }.forEach { update ->
            var menu by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(update.text, style = MaterialTheme.typography.titleSmall) },
                supportingContent = { Text(stringResource(Res.string.news_added, relativePast(update.addedAt, state.now, state.zone))) },
                trailingContent = {
                    EditDeleteMenu(
                        menu,
                        onOpen = { menu = true },
                        onClose = { menu = false },
                        onEdit = { onEdit(PageEdit.News(update)) },
                        onDelete = { actions.onRemoveLifeUpdate(update.id) },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.combinedClickable(onClick = { onEdit(PageEdit.News(update)) }, onLongClick = { menu = true }),
            )
        }
        if (f.lifeUpdates.isEmpty() && detail.topics.isEmpty() && !state.aiEnabled) Hint(stringResource(Res.string.news_hint))
        if (detail.topics.isNotEmpty()) {
            Label(stringResource(Res.string.topics_from_notes), Icons.Rounded.Lightbulb)
            detail.topics.forEach { TopicRow(topicText(it)) }
        }
        if (state.aiEnabled) {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Label(stringResource(Res.string.topics_ai), Icons.Rounded.AutoAwesome) }
                AnimatedContent(state.isGenerating) { busy ->
                    if (busy) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = if (state.hasAiKey) actions.onGenerateIdeas else actions.onOpenSettings) {
                            Text(stringResource(if (state.hasAiKey) Res.string.ideas_find else Res.string.ideas_setup))
                        }
                    }
                }
            }
            if (detail.ideas.isEmpty()) {
                Hint(
                    when {
                        !state.hasAiKey -> stringResource(Res.string.ideas_hint_no_ai)
                        detail.messageCount > 0 -> stringResource(Res.string.ideas_hint_ai_chats, firstName)
                        else -> stringResource(Res.string.ideas_hint_ai, firstName)
                    },
                )
            }
            detail.ideas.take(5).forEach { IdeaRow(it, actions.onDismissIdea) }
        }
    }
}

/**
 * Tastes, editable on the page (issues #90, #103): one row of chips with × (dislikes in the error-container colour) and
 * "+ Add", which asks food / drink / interest / doesn't like; a tap on a chip renames or deletes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tastes(f: Friend, actions: FriendDetailActions, onAdd: () -> Unit, onEdit: (PageEdit) -> Unit) {
    SectionCard(stringResource(Res.string.tastes_title), icon = Icons.Rounded.ThumbUp) {
        FlowRow(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // A tap opens the chip to rename or delete it; × deletes it with Undo (issue #102).
            @Composable
            fun TasteChip(value: String, icon: ImageVector, slot: TasteSlot) {
                val dislike = slot == TasteSlot.DISLIKE
                InputChip(
                    selected = false,
                    onClick = { onEdit(PageEdit.Taste(slot, value)) },
                    label = { Text(value) },
                    leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) },
                    trailingIcon = {
                        Icon(
                            Icons.Rounded.Close,
                            stringResource(CoreRes.string.remove_item, value),
                            Modifier.size(16.dp).clickable { actions.onDeleteTaste(slot, value) },
                        )
                    },
                    colors = if (dislike) {
                        InputChipDefaults.inputChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                            leadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                            trailingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        InputChipDefaults.inputChipColors()
                    },
                )
            }
            f.food.forEach { TasteChip(it, Icons.Rounded.Restaurant, TasteSlot.FOOD) }
            f.drinks.forEach { TasteChip(it, Icons.Rounded.LocalCafe, TasteSlot.DRINK) }
            f.interests.forEach { TasteChip(it, Icons.Rounded.Interests, TasteSlot.INTEREST) }
            f.likes.forEach { TasteChip(it, Icons.Rounded.ThumbUp, TasteSlot.LIKE) }
            f.dislikes.forEach { TasteChip(it, Icons.Rounded.ThumbDown, TasteSlot.DISLIKE) }
            AssistChip(
                onClick = onAdd,
                label = { Text(stringResource(CoreRes.string.add)) },
                leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(AssistChipDefaults.IconSize)) },
            )
        }
    }
}

/** "+ Add" on Tastes: which kind, then the usual tag field with the tag catalogue. Each added tag saves at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddTasteDialog(firstName: String, tags: List<Tag>, actions: FriendDetailActions, onDismiss: () -> Unit) {
    // null = "Doesn't like"; the rest are the taste groups.
    var kind by rememberSaveable { mutableStateOf<TasteKind?>(TasteKind.FOOD) }
    var added by remember { mutableStateOf(emptyList<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ThumbUp, null) },
        title = { Text(stringResource(Res.string.tastes_add_title, firstName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(TasteKind.FOOD, TasteKind.DRINK, TasteKind.INTEREST, null).forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = {
                                Text(
                                    stringResource(
                                        when (k) {
                                            TasteKind.FOOD -> Res.string.tastes_food
                                            TasteKind.DRINK -> Res.string.tastes_drink
                                            TasteKind.INTEREST -> Res.string.tastes_interests
                                            null -> Res.string.tastes_doesnt_like
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                val current = kind
                TagEditor(
                    added,
                    stringResource(
                        when (current) {
                            TasteKind.FOOD -> Res.string.tastes_food_placeholder
                            TasteKind.DRINK -> Res.string.tastes_drink_placeholder
                            TasteKind.INTEREST -> Res.string.tastes_interests_placeholder
                            null -> Res.string.tastes_dislikes_placeholder
                        },
                    ),
                    onAdd = { values ->
                        added = (added + values).distinct()
                        if (current == null) actions.onAddDislikes(values) else actions.onAddTastes(current, values)
                    },
                    onRemove = { value ->
                        added = added - value
                        if (current == null) actions.onRemoveDislike(value) else actions.onRemoveTaste(current, value)
                    },
                    chipColor = if (current == null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    catalog = tags,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.edit_done)) } },
    )
}

/**
 * Dated notes (issue #103): every note is a journal entry with the day it was written, newest first, and "+ Note" adds
 * one. A tap edits it in the Log sheet; ⋮ or a long press offers Edit and Delete (with Undo), as on the timeline.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Notes(timeline: List<TimelineEntry>, state: FriendDetailUiState, actions: FriendDetailActions) {
    val notes = timeline.filterIsInstance<TimelineEntry.Journal>()
    SectionCard(
        stringResource(Res.string.notes_title),
        icon = Icons.AutoMirrored.Rounded.Notes,
        action = {
            TextButton(onClick = actions.onAddNote) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.notes_add))
            }
        },
    ) {
        if (notes.isEmpty()) Hint(stringResource(Res.string.notes_placeholder))
        notes.forEach { note ->
            var menu by remember { mutableStateOf(false) }
            val day = note.at.toLocalDateTime(state.zone).date
            ListItem(
                headlineContent = { Text(note.entry.text, style = MaterialTheme.typography.bodyMedium) },
                overlineContent = { Text(if (isFarDate(day, state.today)) day.shortLabel() else relativeDay(day, state.today)) },
                trailingContent = {
                    EditDeleteMenu(
                        menu,
                        onOpen = { menu = true },
                        onClose = { menu = false },
                        onEdit = { actions.onEditEntry(note.key) },
                        onDelete = { actions.onDeleteEntry(note) },
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.combinedClickable(onClick = { actions.onEditEntry(note.key) }, onLongClick = { menu = true }),
            )
        }
    }
}

/** "Complete <name>'s profile": a chip per empty About card; each opens that card's add sheet (issue #90). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompleteProfile(firstName: String, missing: List<PersonCard>, onPick: (PersonCard) -> Unit) {
    SectionCard(stringResource(Res.string.complete_title, firstName), icon = Icons.Rounded.Checklist) {
        FlowRow(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            missing.forEach { card ->
                val label = when (card) {
                    PersonCard.ABOUT -> Res.string.complete_about
                    PersonCard.MORE_ABOUT -> Res.string.complete_more
                    PersonCard.OCCASIONS -> Res.string.complete_occasions
                    PersonCard.FAMILY -> Res.string.complete_family
                    PersonCard.MOMENTS -> Res.string.complete_moments
                    PersonCard.GIFTS -> Res.string.complete_gifts
                    PersonCard.SAVED -> Res.string.complete_saved
                    PersonCard.FEEDS -> Res.string.complete_feeds
                    else -> null
                } ?: return@forEach
                AssistChip(
                    onClick = { onPick(card) },
                    label = { Text(stringResource(label)) },
                    leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
        }
    }
}

/** "Good company for": plain chips; "Edit" opens the ticks in their own popup (issue #103). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Occasions(f: Friend, onEdit: () -> Unit) {
    SectionCard(
        stringResource(Res.string.occasions_title),
        icon = Icons.Rounded.Celebration,
        action = { TextButton(onClick = onEdit) { Text(stringResource(CoreRes.string.edit)) } },
    ) {
        FlowRow(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            f.occasions.forEach { InfoPill(occasionLabel(it)) }
        }
    }
}

/** "Good company for…" ticks in a popup (issue #103): each tick saves at once; Done closes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OccasionsDialog(f: Friend, onToggle: (Occasion) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Celebration, null) },
        title = { Text(stringResource(Res.string.occasions_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.occasions_hint), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Occasion.entries.forEach { o -> FilterChip(o in f.occasions, { onToggle(o) }, label = { Text(occasionLabel(o)) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.edit_done)) } },
    )
}

/** Every moment, not only the upcoming ones; a tap edits, ⋮ or a long press offers Edit and Delete. */
@Composable
private fun Moments(
    f: Friend,
    state: FriendDetailUiState,
    actions: FriendDetailActions,
    onAdd: () -> Unit,
    onEdit: (PageEdit) -> Unit,
) {
    SectionCard(stringResource(Res.string.moments_title), icon = Icons.Rounded.Favorite, action = {
        TextButton(onClick = onAdd) { Text(stringResource(CoreRes.string.add)) }
    }) {
        if (f.importantEvents.isEmpty()) Hint(stringResource(Res.string.moments_hint))
        f.importantEvents.forEach { event ->
            MomentRow(
                event,
                state.today,
                onEdit = { onEdit(PageEdit.Moment(event)) },
                onDelete = { actions.onRemoveEvent(event.id) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MomentRow(
    event: ImportantEvent,
    today: LocalDate,
    onRemove: (() -> Unit)? = null,
    /** View mode (issue #102): a tap edits, a long press or ⋮ offers Edit and Delete. */
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val clickable = if (onEdit != null) Modifier.combinedClickable(onClick = onEdit, onLongClick = { menu = true }) else Modifier
    Row(clickable.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        DateBadge(event.date, today)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = MaterialTheme.typography.titleSmall)
            val day = relativeDay(event.date, today)
            Text(
                if (event.remindDaysBefore > 0) {
                    pluralStringResource(Res.plurals.moments_reminder, event.remindDaysBefore, day, event.remindDaysBefore)
                } else {
                    day
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.remove_item, event.title)) }
        } else if (onEdit != null) {
            EditDeleteMenu(menu, onOpen = { menu = true }, onClose = { menu = false }, onEdit = onEdit, onDelete = onDelete)
        }
    }
}

/** Together: the imported WhatsApp chat count, or how to import one. */
@Composable
private fun Conversations(messageCount: Int) {
    SectionCard(stringResource(Res.string.chat_title), icon = Icons.Rounded.Forum) {
        Text(
            if (messageCount > 0) pluralStringResource(Res.plurals.chat_count, messageCount, messageCount) else stringResource(Res.string.chat_how),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/**
 * Together: the whole timeline in place (no "See all"); "+" opens the Log sheet. ⋮ still opens the timeline screen for
 * what only it does (filters, swipe to delete, importing a WhatsApp export).
 */
@Composable
private fun TimelineCard(timeline: List<TimelineEntry>, state: FriendDetailUiState, actions: FriendDetailActions, onEdit: (PageEdit) -> Unit) {
    SectionCard(
        stringResource(Res.string.timeline_title),
        icon = Icons.Rounded.Diversity3,
        action = {
            Row {
                IconButton(onClick = actions.onLog) { Icon(Icons.Rounded.Add, stringResource(Res.string.timeline_log)) }
                IconButton(onClick = actions.onOpenTimeline) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.timeline_more_options)) }
            }
        },
    ) {
        if (timeline.isEmpty()) Hint(stringResource(Res.string.timeline_empty))
        // Issue #102: a tap edits (news and gifts in their dialogs, the rest in the Log sheet); ⋮ or a long press offers Delete too.
        timeline.forEachIndexed { i, entry ->
            EditableTimelineEntry(
                entry,
                state.zone,
                onEdit = {
                    when (entry) {
                        is TimelineEntry.WhatsNew -> onEdit(PageEdit.News(entry.update))
                        is TimelineEntry.GiftGiven -> onEdit(PageEdit.GiftEdit(entry.gift))
                        is TimelineEntry.Share -> Unit
                        else -> actions.onEditEntry(entry.key)
                    }
                },
                onDelete = if (entry.deletable) ({ actions.onDeleteEntry(entry) }) else null,
                last = i == timeline.lastIndex,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Gifts(
    f: Friend,
    state: FriendDetailUiState,
    actions: FriendDetailActions,
    onAdd: () -> Unit,
    onGive: (Gift) -> Unit,
) {
    SectionCard(
        stringResource(Res.string.gifts_title),
        icon = Icons.Rounded.CardGiftcard,
        action = { TextButton(onClick = onAdd) { Text(stringResource(CoreRes.string.add)) } },
    ) {
        val ideas = f.gifts.filter { it.isIdea }
        val given = f.gifts.filterNot { it.isIdea }.sortedByDescending { it.givenOn }
        if (f.gifts.isEmpty()) Hint(stringResource(Res.string.gifts_hint))
        if (ideas.isNotEmpty()) Label(stringResource(Res.string.gifts_ideas), Icons.Rounded.AutoAwesome)
        ideas.forEach { gift ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(gift.title, style = MaterialTheme.typography.titleSmall)
                    if (gift.note.isNotBlank()) {
                        Text(gift.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { onGive(gift) }) {
                    Icon(Icons.Rounded.CheckCircle, stringResource(Res.string.gifts_mark_given), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { actions.onRemoveGift(gift.id) }) {
                    Icon(Icons.Rounded.Close, stringResource(CoreRes.string.remove_item, gift.title))
                }
            }
        }
        if (given.isNotEmpty()) Label(stringResource(Res.string.gifts_given), Icons.Rounded.CardGiftcard)
        given.forEach { gift ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(gift.title, style = MaterialTheme.typography.titleSmall)
                    val date = gift.givenOn?.let { if (isFarDate(it, state.today)) it.shortLabel() else relativeDay(it, state.today) }.orEmpty()
                    val line = gift.occasion?.let { stringResource(Res.string.gifts_given_on, occasionLabel(it), date) } ?: date
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { actions.onRemoveGift(gift.id) }) {
                    Icon(Icons.Rounded.Close, stringResource(CoreRes.string.remove_item, gift.title))
                }
            }
        }
        if (state.hasAiKey) {
            FlowRow(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val busy = state.suggestingGiftsFor
                AssistChip(
                    onClick = actions.onSuggestGifts,
                    enabled = busy == null,
                    label = { Text(stringResource(Res.string.gifts_suggest)) },
                    leadingIcon = {
                        if (busy ==
                            ""
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                        }
                    },
                )
            }
        }
        AnimatedVisibility(state.giftSuggestions != null) {
            val suggestions = state.giftSuggestions
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.gifts_suggestions),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = actions.onDismissGiftSuggestions) { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.close)) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions?.ideas.orEmpty().forEach { idea ->
                        SuggestionChip(onClick = { actions.onKeepGiftSuggestion(idea) }, label = { Text(idea) })
                    }
                }
            }
        }
        // F8: gift lists per occasion live on the Plans tab (issue #104); this opens the list this person is on there.
        TextButton(onClick = actions.onOpenGiftLists, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Rounded.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.gift_lists_link))
        }
    }
}

/** "How close are you to Ana?": the four levels with their rhythm; picking one saves it (#64) and closes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClosenessSheet(f: Friend, rules: CadenceRules, onPick: (Closeness) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp).selectableGroup()) {
            Text(
                stringResource(Res.string.closeness_sheet_title, f.displayName.substringBefore(' ')),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Closeness.entries.forEach { level ->
                val selected = f.closeness == level
                ListItem(
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    headlineContent = { Text(closenessLabel(level)) },
                    supportingContent = {
                        Text(rules.daysFor(level)?.let { everyNDays(it) } ?: stringResource(Res.string.closeness_no_rhythm))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onPick(level) })
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
internal fun Label(text: String, icon: ImageVector) {
    Row(Modifier.padding(start = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = { onToggle() }) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LastContactRow(byType: Map<InteractionType, kotlin.time.Instant>, state: FriendDetailUiState) {
    if (byType.isEmpty()) return
    FlowRow(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        byType.entries.sortedByDescending { it.value }.forEach { (type, at) ->
            val ago = relativePast(at, state.now, state.zone)
            val (icon, label) = when (type) {
                InteractionType.MEETUP -> Icons.Rounded.Groups to stringResource(Res.string.contact_met, ago)
                InteractionType.CALL -> Icons.Rounded.Call to stringResource(Res.string.contact_call, ago)
                InteractionType.WHATSAPP -> Icons.Rounded.Forum to stringResource(Res.string.contact_whatsapp, ago)
                InteractionType.MESSAGE -> interactionIcon(type) to stringResource(Res.string.contact_message, ago)
                InteractionType.EMAIL -> interactionIcon(type) to stringResource(Res.string.contact_email, ago)
                InteractionType.VIDEO_CALL -> interactionIcon(type) to stringResource(Res.string.contact_video, ago)
                InteractionType.LONG_CHAT -> interactionIcon(type) to stringResource(Res.string.contact_long_chat, ago)
                InteractionType.GAME -> interactionIcon(type) to stringResource(Res.string.contact_game, ago)
                InteractionType.OTHER -> interactionIcon(type) to stringResource(Res.string.contact_other, ago)
            }
            InfoPill(label, icon = icon)
        }
    }
}

@Composable
private fun TopicRow(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun IdeaRow(idea: Idea, onDismiss: (String) -> Unit) {
    val uri = LocalUriHandler.current
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(idea.title, style = MaterialTheme.typography.titleSmall)
            if (idea.description.isNotBlank()) {
                Text(idea.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            idea.url?.let { url ->
                TextButton(onClick = { uri.openUri(url) }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(Res.string.ideas_open))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(14.dp))
                }
            }
        }
        IconButton(onClick = { onDismiss(idea.id) }) { Icon(Icons.Rounded.Close, stringResource(Res.string.ideas_dismiss)) }
    }
}

/** An entry on the page being edited in its own dialog (issue #102). */
internal sealed interface PageEdit {
    data class News(val update: LifeUpdate) : PageEdit

    data class Moment(val event: ImportantEvent) : PageEdit

    data class GiftEdit(val gift: Gift) : PageEdit

    data class Taste(val slot: TasteSlot, val value: String) : PageEdit
}

@Composable
private fun PageEditDialog(edit: PageEdit, firstName: String, today: LocalDate, actions: FriendDetailActions, onDone: () -> Unit) {
    when (edit) {
        is PageEdit.News -> LifeUpdateDialog(firstName, onDismiss = onDone, initial = edit.update.text) { text ->
            actions.onEditLifeUpdate(edit.update.id, text)
            onDone()
        }
        is PageEdit.Moment -> ImportantEventDialog(today, onDone, initial = edit.event) { t, d, b ->
            actions.onEditEvent(edit.event.id, t, d, b)
            onDone()
        }
        is PageEdit.GiftEdit -> GiftDialog(onDismiss = onDone, initial = edit.gift) { title, note ->
            actions.onEditGift(edit.gift.id, title, note)
            onDone()
        }
        is PageEdit.Taste -> TasteEditDialog(
            edit.value,
            onDismiss = onDone,
            onSave = {
                actions.onRenameTaste(edit.slot, edit.value, it)
                onDone()
            },
            onDelete = {
                actions.onDeleteTaste(edit.slot, edit.value)
                onDone()
            },
        )
    }
}

/** A taste chip opened (issue #102): rename it, or delete it (with Undo). */
@Composable
private fun TasteEditDialog(value: String, onDismiss: () -> Unit, onSave: (String) -> Unit, onDelete: () -> Unit) {
    var text by rememberSaveable(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ThumbUp, null) },
        title = { Text(stringResource(Res.string.tastes_edit_title)) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text(stringResource(CoreRes.string.save)) } },
        dismissButton = { TextButton(onClick = onDelete) { Text(stringResource(CoreRes.string.delete)) } },
    )
}
