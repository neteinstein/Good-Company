package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
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
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.LinkCategory
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.model.TasteKind
import com.neteinstein.goodcompany.core.domain.usecase.CadenceRules
import com.neteinstein.goodcompany.core.domain.usecase.KidDraft
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.Warmth
import com.neteinstein.goodcompany.core.domain.usecase.isBuiltIn
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.CircleChip
import com.neteinstein.goodcompany.core.ui.components.DateBadge
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.core.ui.components.LocalDatePickerDialog
import com.neteinstein.goodcompany.core.ui.components.PhotoCropDialog
import com.neteinstein.goodcompany.core.ui.components.PlanEditor
import com.neteinstein.goodcompany.core.ui.components.PlanRow
import com.neteinstein.goodcompany.core.ui.components.ProgressRing
import com.neteinstein.goodcompany.core.ui.components.QuickAction
import com.neteinstein.goodcompany.core.ui.components.ReminderEditor
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.components.SendMessageSheet
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.components.TagEditor
import com.neteinstein.goodcompany.core.ui.components.TimelineEntryRow
import com.neteinstein.goodcompany.core.ui.components.WarmthDot
import com.neteinstein.goodcompany.core.ui.components.avatarColors
import com.neteinstein.goodcompany.core.ui.components.calendarEntry
import com.neteinstein.goodcompany.core.ui.components.interactionIcon
import com.neteinstein.goodcompany.core.ui.components.showMessage
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
import com.neteinstein.goodcompany.core.ui.l10n.relationToMeLabel
import com.neteinstein.goodcompany.core.ui.l10n.topicText
import com.neteinstein.goodcompany.core.ui.l10n.warmthLabel
import com.neteinstein.goodcompany.core.ui.platform.OpenedPhoto
import com.neteinstein.goodcompany.core.ui.platform.rememberCalendarAdder
import com.neteinstein.goodcompany.core.ui.platform.rememberGalleryPhotoOpener
import com.neteinstein.goodcompany.core.ui.platform.rememberGalleryPhotoPicker
import com.neteinstein.goodcompany.core.ui.platform.rememberImagePicker
import com.neteinstein.goodcompany.core.ui.resources.add
import com.neteinstein.goodcompany.core.ui.resources.cadence_off
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.close
import com.neteinstein.goodcompany.core.ui.resources.plan_cancelled
import com.neteinstein.goodcompany.core.ui.resources.plan_closed
import com.neteinstein.goodcompany.core.ui.resources.plan_logged
import com.neteinstein.goodcompany.core.ui.resources.plan_saved
import com.neteinstein.goodcompany.core.ui.resources.plan_skipped
import com.neteinstein.goodcompany.core.ui.resources.plans_title
import com.neteinstein.goodcompany.core.ui.resources.remove
import com.neteinstein.goodcompany.core.ui.resources.remove_item
import com.neteinstein.goodcompany.core.ui.resources.undo
import com.neteinstein.goodcompany.core.ui.resources.wish_default
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.birthday_banner_soon
import com.neteinstein.goodcompany.feature.friends.resources.birthday_banner_today
import com.neteinstein.goodcompany.feature.friends.resources.birthday_banner_tomorrow
import com.neteinstein.goodcompany.feature.friends.resources.birthday_send_wishes
import com.neteinstein.goodcompany.feature.friends.resources.chat_count
import com.neteinstein.goodcompany.feature.friends.resources.chat_how
import com.neteinstein.goodcompany.feature.friends.resources.chat_import
import com.neteinstein.goodcompany.feature.friends.resources.chat_title
import com.neteinstein.goodcompany.feature.friends.resources.circles_add
import com.neteinstein.goodcompany.feature.friends.resources.circles_remove
import com.neteinstein.goodcompany.feature.friends.resources.closeness_change
import com.neteinstein.goodcompany.feature.friends.resources.closeness_no_rhythm
import com.neteinstein.goodcompany.feature.friends.resources.closeness_sheet_title
import com.neteinstein.goodcompany.feature.friends.resources.contact_call
import com.neteinstein.goodcompany.feature.friends.resources.contact_email
import com.neteinstein.goodcompany.feature.friends.resources.contact_message
import com.neteinstein.goodcompany.feature.friends.resources.contact_met
import com.neteinstein.goodcompany.feature.friends.resources.contact_video
import com.neteinstein.goodcompany.feature.friends.resources.contact_whatsapp
import com.neteinstein.goodcompany.feature.friends.resources.delete_body
import com.neteinstein.goodcompany.feature.friends.resources.delete_body_contact
import com.neteinstein.goodcompany.feature.friends.resources.delete_title
import com.neteinstein.goodcompany.feature.friends.resources.detail_add_birthday
import com.neteinstein.goodcompany.feature.friends.resources.detail_add_photo
import com.neteinstein.goodcompany.feature.friends.resources.detail_back
import com.neteinstein.goodcompany.feature.friends.resources.detail_birthday_turns
import com.neteinstein.goodcompany.feature.friends.resources.detail_birthday_when
import com.neteinstein.goodcompany.feature.friends.resources.detail_edit_name
import com.neteinstein.goodcompany.feature.friends.resources.detail_missing
import com.neteinstein.goodcompany.feature.friends.resources.detail_orphaned
import com.neteinstein.goodcompany.feature.friends.resources.detail_remove_friend
import com.neteinstein.goodcompany.feature.friends.resources.detail_seen
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
import com.neteinstein.goodcompany.feature.friends.resources.ideas_title
import com.neteinstein.goodcompany.feature.friends.resources.moments_hint
import com.neteinstein.goodcompany.feature.friends.resources.moments_reminder
import com.neteinstein.goodcompany.feature.friends.resources.moments_title
import com.neteinstein.goodcompany.feature.friends.resources.msg_added_as_person
import com.neteinstein.goodcompany.feature.friends.resources.msg_added_to_news
import com.neteinstein.goodcompany.feature.friends.resources.msg_call_logged_short
import com.neteinstein.goodcompany.feature.friends.resources.msg_chat_imported
import com.neteinstein.goodcompany.feature.friends.resources.msg_chat_invalid
import com.neteinstein.goodcompany.feature.friends.resources.msg_chat_matched
import com.neteinstein.goodcompany.feature.friends.resources.msg_feed_added
import com.neteinstein.goodcompany.feature.friends.resources.msg_feed_error
import com.neteinstein.goodcompany.feature.friends.resources.msg_feed_not_found
import com.neteinstein.goodcompany.feature.friends.resources.msg_field_removed
import com.neteinstein.goodcompany.feature.friends.resources.msg_ideas_found
import com.neteinstein.goodcompany.feature.friends.resources.msg_ideas_none
import com.neteinstein.goodcompany.feature.friends.resources.msg_link_removed
import com.neteinstein.goodcompany.feature.friends.resources.msg_photo_unreadable
import com.neteinstein.goodcompany.feature.friends.resources.msg_reminder_saved
import com.neteinstein.goodcompany.feature.friends.resources.msg_removed_from_circle
import com.neteinstein.goodcompany.feature.friends.resources.msg_saved
import com.neteinstein.goodcompany.feature.friends.resources.news_added
import com.neteinstein.goodcompany.feature.friends.resources.news_hint
import com.neteinstein.goodcompany.feature.friends.resources.news_title
import com.neteinstein.goodcompany.feature.friends.resources.notes_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.notes_title
import com.neteinstein.goodcompany.feature.friends.resources.occasions_hint
import com.neteinstein.goodcompany.feature.friends.resources.occasions_title
import com.neteinstein.goodcompany.feature.friends.resources.photo_sheet_title
import com.neteinstein.goodcompany.feature.friends.resources.plans_add
import com.neteinstein.goodcompany.feature.friends.resources.plans_empty
import com.neteinstein.goodcompany.feature.friends.resources.prefs_birthday
import com.neteinstein.goodcompany.feature.friends.resources.prefs_birthday_hint
import com.neteinstein.goodcompany.feature.friends.resources.prefs_contact_counts
import com.neteinstein.goodcompany.feature.friends.resources.prefs_contact_counts_hint
import com.neteinstein.goodcompany.feature.friends.resources.prefs_events
import com.neteinstein.goodcompany.feature.friends.resources.prefs_events_hint
import com.neteinstein.goodcompany.feature.friends.resources.prefs_pause
import com.neteinstein.goodcompany.feature.friends.resources.prefs_pause_hint
import com.neteinstein.goodcompany.feature.friends.resources.prefs_title
import com.neteinstein.goodcompany.feature.friends.resources.qa_call
import com.neteinstein.goodcompany.feature.friends.resources.qa_email
import com.neteinstein.goodcompany.feature.friends.resources.qa_remind
import com.neteinstein.goodcompany.feature.friends.resources.qa_text
import com.neteinstein.goodcompany.feature.friends.resources.qa_together
import com.neteinstein.goodcompany.feature.friends.resources.qa_whatsapp
import com.neteinstein.goodcompany.feature.friends.resources.relation_to_me_add
import com.neteinstein.goodcompany.feature.friends.resources.saved_removed
import com.neteinstein.goodcompany.feature.friends.resources.show_in_my_list
import com.neteinstein.goodcompany.feature.friends.resources.show_in_my_list_hint
import com.neteinstein.goodcompany.feature.friends.resources.show_in_my_list_hint_any
import com.neteinstein.goodcompany.feature.friends.resources.snooze_week
import com.neteinstein.goodcompany.feature.friends.resources.snooze_weekend
import com.neteinstein.goodcompany.feature.friends.resources.story_cta
import com.neteinstein.goodcompany.feature.friends.resources.story_cta_fresh
import com.neteinstein.goodcompany.feature.friends.resources.story_cta_hint
import com.neteinstein.goodcompany.feature.friends.resources.story_meeting_hint
import com.neteinstein.goodcompany.feature.friends.resources.story_meeting_today
import com.neteinstein.goodcompany.feature.friends.resources.story_meeting_tomorrow
import com.neteinstein.goodcompany.feature.friends.resources.tastes_dislikes
import com.neteinstein.goodcompany.feature.friends.resources.tastes_dislikes_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.tastes_doesnt_like
import com.neteinstein.goodcompany.feature.friends.resources.tastes_drink
import com.neteinstein.goodcompany.feature.friends.resources.tastes_drink_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.tastes_food
import com.neteinstein.goodcompany.feature.friends.resources.tastes_food_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.tastes_interests
import com.neteinstein.goodcompany.feature.friends.resources.tastes_interests_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.tastes_loves
import com.neteinstein.goodcompany.feature.friends.resources.tastes_loves_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.tastes_other_likes
import com.neteinstein.goodcompany.feature.friends.resources.tastes_title
import com.neteinstein.goodcompany.feature.friends.resources.timeline_empty
import com.neteinstein.goodcompany.feature.friends.resources.timeline_log
import com.neteinstein.goodcompany.feature.friends.resources.timeline_see_all
import com.neteinstein.goodcompany.feature.friends.resources.timeline_title
import com.neteinstein.goodcompany.feature.friends.resources.together_hint
import com.neteinstein.goodcompany.feature.friends.resources.together_hung_out
import com.neteinstein.goodcompany.feature.friends.resources.together_title
import com.neteinstein.goodcompany.feature.friends.resources.topics_ai
import com.neteinstein.goodcompany.feature.friends.resources.topics_from_notes
import com.neteinstein.goodcompany.feature.friends.resources.touch_called
import com.neteinstein.goodcompany.feature.friends.resources.touch_circle
import com.neteinstein.goodcompany.feature.friends.resources.touch_custom
import com.neteinstein.goodcompany.feature.friends.resources.touch_from_circle
import com.neteinstein.goodcompany.feature.friends.resources.touch_goal
import com.neteinstein.goodcompany.feature.friends.resources.touch_last_together
import com.neteinstein.goodcompany.feature.friends.resources.touch_never
import com.neteinstein.goodcompany.feature.friends.resources.touch_overdue
import com.neteinstein.goodcompany.feature.friends.resources.touch_paused
import com.neteinstein.goodcompany.feature.friends.resources.touch_pick
import com.neteinstein.goodcompany.feature.friends.resources.touch_resume
import com.neteinstein.goodcompany.feature.friends.resources.touch_snoozed
import com.neteinstein.goodcompany.feature.friends.resources.touch_title
import com.neteinstein.goodcompany.feature.friends.resources.touch_unsnooze
import com.neteinstein.goodcompany.feature.friends.resources.warmth_hint_cooling
import com.neteinstein.goodcompany.feature.friends.resources.warmth_hint_fading
import com.neteinstein.goodcompany.feature.friends.resources.warmth_hint_warm
import com.neteinstein.goodcompany.feature.friends.resources.warmth_title
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
import kotlinx.datetime.Month
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
    /** A plan's "Did you meet?" yes opens the small "We met" panel on that occurrence (issues #43, #47). */
    onLogPlan: (planId: String, day: LocalDate) -> Unit = { _, _ -> },
    /** "Together" opens the small "We met" panel (issue #47). */
    onWeMet: () -> Unit = {},
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
            onSaveRelative = viewModel::onSaveRelative,
            onRemoveRelative = viewModel::onRemoveRelative,
            onAddTastes = viewModel::onAddTastes,
            onRemoveTaste = viewModel::onRemoveTaste,
            onSaveCustomField = viewModel::onSaveCustomField,
            onRemoveCustomField = viewModel::onRemoveCustomField,
            onUndoRemoveCustomField = viewModel::onUndoRemoveCustomField,
            onAddLifeUpdate = viewModel::onAddLifeUpdate,
            onRemoveLifeUpdate = viewModel::onRemoveLifeUpdate,
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
            onNotesChange = viewModel::onNotesChange,
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
            onOpenCircle = { id ->
                viewModel.onOpenCircle(id)
                onOpenCircle(id)
            },
            onSetInCircle = viewModel::onSetInCircle,
            onRemoveFromCircle = viewModel::onRemoveFromCircle,
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
            onMessageShown = viewModel::onMessageShown,
            onTogglePause = viewModel::onTogglePause,
            onToggleContactCounts = viewModel::onToggleContactCounts,
            onToggleOccasion = viewModel::onToggleOccasion,
            onOpenPlanEditor = viewModel::onOpenPlanEditor,
            onDismissPlanEditor = viewModel::onDismissPlanEditor,
            onSavePlan = viewModel::onSavePlan,
            onConfirmPlan = viewModel::onConfirmPlan,
            onSkipPlan = viewModel::onSkipPlan,
            onLogPlan = onLogPlan,
            onCancelPlan = viewModel::onCancelPlan,
            onUndoCancelPlan = viewModel::onUndoCancelPlan,
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
    val onNotesChange: (String) -> Unit = {},
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
    /** A tick in the "+ Circle" sheet (issue #70); saves at once, with Undo. */
    val onSetInCircle: (groupId: String, member: Boolean) -> Unit = { _, _ -> },
    /** The × on a circle chip, with Undo. */
    val onRemoveFromCircle: (String) -> Unit = {},
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
    val onSaveReminder: (String, List<FriendId>, LocalDate?, Int) -> Unit = { _, _, _, _ -> },
    val onMessageShown: () -> Unit = {},
    val onTogglePause: () -> Unit = {},
    val onToggleContactCounts: () -> Unit = {},
    val onToggleOccasion: (Occasion) -> Unit = {},
    val onOpenPlanEditor: (Plan?) -> Unit = {},
    val onDismissPlanEditor: () -> Unit = {},
    val onSavePlan: (PlanInput) -> Unit = {},
    val onConfirmPlan: (id: String, happened: Boolean) -> Unit = { _, _ -> },
    val onSkipPlan: (Plan) -> Unit = {},
    /** "Yes" on a circle plan: open the Log sheet to tick who came (F20). */
    val onLogPlan: (planId: String, day: LocalDate) -> Unit = { _, _ -> },
    val onCancelPlan: (Plan) -> Unit = {},
    val onUndoCancelPlan: () -> Unit = {},
    val onAddPlanToCalendar: (Plan, List<Friend>, FriendGroup?) -> Unit = { _, _, _ -> },
)

private val RHYTHMS = listOf(7, 14, 30, 90)

/** Localised snackbar text for a [FriendDetailMessage]. */
suspend fun FriendDetailMessage.text(): String = when (this) {
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
    is FriendDetailMessage.RemovedFromCircle -> getString(Res.string.msg_removed_from_circle, "${circle.emoji} ${circle.name}")
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
    LaunchedEffect(state.feedAdded) { addingFeed = false }
    var givingGift by remember { mutableStateOf<Gift?>(null) }
    var pickBirthday by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var photoOptions by rememberSaveable { mutableStateOf(false) }
    var choosingCircles by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var seeAllSaved by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<AttachmentView?>(null) }
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
            state.undoSave != null
        val result = snackbar.showMessage(message.text(), if (undoable) undoLabel else null)
        if (result == SnackbarResult.ActionPerformed) {
            when {
                state.undoSave != null -> actions.onUndoSave()
                state.undoAttachment != null -> actions.onUndoRemoveAttachment()
                state.undoLink != null -> actions.onUndoRemoveLink()
                state.undoCustomField != null -> actions.onUndoRemoveCustomField()
                state.undoPlan != null -> actions.onUndoCancelPlan()
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
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp)) {
            item(key = "hero") {
                Hero(
                    f,
                    state,
                    actions,
                    onEditBirthday = { pickBirthday = true },
                    onEditName = { editingName = true },
                    onPhoto = { if (f.avatar == null) actions.onAddPhoto() else photoOptions = true },
                    onDelete = { confirmDelete = true },
                    onEditRelationToMe = { editingRelationToMe = true },
                    onAddCircle = { choosingCircles = true },
                )
            }

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

            item(key = "about") { AboutCard(f, detail.others, onEdit = { editingAbout = true }, onOpenFriend = actions.onOpenFriend) }

            item(key = "story") {
                val meeting = detail.plans.firstOrNull { it.date >= state.today && state.today.daysUntil(it.date) <= 1 }?.date
                StoryCta(f.displayName, meeting, state.today, state.freshCount, actions.onOpenStory)
            }

            val nextBirthday = f.birthday?.nextOccurrence(state.today)
            if (nextBirthday != null && state.today.daysUntil(nextBirthday) in 0..7) {
                item(key = "bday") { BirthdayBanner(firstName, nextBirthday, state.today, actions.onOpenWish) }
            }

            item(key = "touch") { StayInTouch(f, state, actions) }

            item(key = "news") {
                SectionCard(
                    stringResource(Res.string.news_title),
                    icon = Icons.Rounded.NewReleases,
                    action = { TextButton(onClick = { addingNews = true }) { Text(stringResource(CoreRes.string.add)) } },
                ) {
                    if (f.lifeUpdates.isEmpty()) Hint(stringResource(Res.string.news_hint))
                    f.lifeUpdates.sortedByDescending { it.addedAt }.forEach { update ->
                        ListItem(
                            headlineContent = { Text(update.text, style = MaterialTheme.typography.titleSmall) },
                            supportingContent = { Text(stringResource(Res.string.news_added, relativePast(update.addedAt, state.now, state.zone))) },
                            trailingContent = {
                                IconButton(onClick = { actions.onRemoveLifeUpdate(update.id) }) {
                                    Icon(Icons.Rounded.Close, stringResource(CoreRes.string.remove_item, update.text))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            item(key = "saved") {
                SavedCard(
                    firstName,
                    state.attachments,
                    state.today,
                    state.zone,
                    onAddPhoto = actions.onAddSavedPhoto,
                    onOpen = openAttachment,
                    onSeeAll = { seeAllSaved = true },
                )
            }

            item(key = "feeds") { Feeds(f, state, actions, onAdd = { addingFeed = true }) }

            item(key = "plans") {
                SectionCard(
                    stringResource(CoreRes.string.plans_title),
                    icon = Icons.Rounded.EventAvailable,
                    action = { TextButton(onClick = { actions.onOpenPlanEditor(null) }) { Text(stringResource(Res.string.plans_add)) } },
                ) {
                    if (detail.plans.isEmpty()) Hint(stringResource(Res.string.plans_empty))
                    val people = (listOf(f) + detail.hangouts.flatMap { it.friends }).associateBy { it.id }
                    detail.plans.forEach { plan ->
                        val with = plan.friendIds.mapNotNull(people::get)
                        val circle = detail.groups.firstOrNull { it.id == plan.circleId }
                        PlanRow(
                            plan = plan,
                            friends = with.filterNot { it.id == f.id },
                            today = state.today,
                            onConfirm = { happened ->
                                // A yes opens the "We met" panel (issue #47): a person plan as well as a circle plan.
                                if (happened) actions.onLogPlan(plan.id, plan.date) else actions.onConfirmPlan(plan.id, false)
                            },
                            onAddToCalendar = { actions.onAddPlanToCalendar(plan, with, circle) },
                            onChange = { actions.onOpenPlanEditor(plan) },
                            onCancel = { actions.onCancelPlan(plan) },
                            circle = circle,
                            onSkip = { actions.onSkipPlan(plan) },
                        )
                    }
                }
            }

            item(key = "tastes") {
                SectionCard(stringResource(Res.string.tastes_title), icon = Icons.Rounded.ThumbUp) {
                    Label(stringResource(Res.string.tastes_food), Icons.Rounded.Restaurant)
                    TagEditor(
                        f.food,
                        stringResource(Res.string.tastes_food_placeholder),
                        { actions.onAddTastes(TasteKind.FOOD, it) },
                        { actions.onRemoveTaste(TasteKind.FOOD, it) },
                        catalog = state.tags,
                    )
                    Label(stringResource(Res.string.tastes_drink), Icons.Rounded.LocalCafe)
                    TagEditor(
                        f.drinks,
                        stringResource(Res.string.tastes_drink_placeholder),
                        { actions.onAddTastes(TasteKind.DRINK, it) },
                        { actions.onRemoveTaste(TasteKind.DRINK, it) },
                        catalog = state.tags,
                    )
                    Label(stringResource(Res.string.tastes_doesnt_like), Icons.Rounded.ThumbDown)
                    TagEditor(
                        f.dislikes,
                        stringResource(Res.string.tastes_dislikes_placeholder),
                        actions.onAddDislikes,
                        actions.onRemoveDislike,
                        chipColor = MaterialTheme.colorScheme.errorContainer,
                        catalog = state.tags,
                    )
                    Label(stringResource(Res.string.tastes_interests), Icons.Rounded.Interests)
                    TagEditor(
                        f.interests,
                        stringResource(Res.string.tastes_interests_placeholder),
                        { actions.onAddTastes(TasteKind.INTEREST, it) },
                        { actions.onRemoveTaste(TasteKind.INTEREST, it) },
                        catalog = state.tags,
                    )
                    // Everything stored as a like before the groups existed lives here, so nothing is lost or guessed.
                    Label(stringResource(Res.string.tastes_other_likes), Icons.Rounded.ThumbUp)
                    TagEditor(
                        f.likes,
                        stringResource(Res.string.tastes_loves_placeholder),
                        actions.onAddLikes,
                        actions.onRemoveLike,
                        catalog = state.tags,
                    )
                }
            }

            item(key = "more") {
                MoreAboutCard(f, onAdd = { addingField = true }, onEdit = { fieldEditing = it }, onRemove = actions.onRemoveCustomField)
            }

            item(key = "occasions") {
                SectionCard(stringResource(Res.string.occasions_title), icon = Icons.Rounded.Celebration) {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Occasion.entries.forEach { o ->
                            FilterChip(o in f.occasions, { actions.onToggleOccasion(o) }, label = { Text(occasionLabel(o)) })
                        }
                    }
                    Hint(stringResource(Res.string.occasions_hint))
                }
            }

            item(key = "gifts") { Gifts(f, state, actions, onAdd = { addingGift = true }, onGive = { givingGift = it }) }

            item(key = "family") {
                FamilyCard(
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
            }

            item(key = "links-other") {
                OtherLinksCard(state.links.filter { it.role.category == LinkCategory.OTHER }, actions.onOpenFriend, onEdit = { linkEditing = it })
            }

            item(key = "moments") {
                SectionCard(stringResource(Res.string.moments_title), icon = Icons.Rounded.Favorite, action = {
                    TextButton(onClick = { addingEvent = true }) { Text(stringResource(CoreRes.string.add)) }
                }) {
                    if (f.importantEvents.isEmpty()) Hint(stringResource(Res.string.moments_hint))
                    f.importantEvents.forEach { event ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            DateBadge(event.date, state.today)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(event.title, style = MaterialTheme.typography.titleSmall)
                                val day = relativeDay(event.date, state.today)
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
                            IconButton(onClick = { actions.onRemoveEvent(event.id) }) {
                                Icon(Icons.Rounded.Close, stringResource(CoreRes.string.remove_item, event.title))
                            }
                        }
                    }
                }
            }

            if (state.aiEnabled) {
                item(key = "ideas") {
                    SectionCard(
                        stringResource(Res.string.ideas_title),
                        icon = Icons.Rounded.AutoAwesome,
                        action = {
                            AnimatedContent(state.isGenerating) { busy ->
                                if (busy) {
                                    CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = if (state.hasAiKey) actions.onGenerateIdeas else actions.onOpenSettings) {
                                        Text(stringResource(if (state.hasAiKey) Res.string.ideas_find else Res.string.ideas_setup))
                                    }
                                }
                            }
                        },
                    ) {
                        if (detail.topics.isNotEmpty()) {
                            Label(stringResource(Res.string.topics_from_notes), Icons.Rounded.Lightbulb)
                            detail.topics.forEach { TopicRow(topicText(it)) }
                            if (detail.ideas.isNotEmpty()) Label(stringResource(Res.string.topics_ai), Icons.Rounded.AutoAwesome)
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
            } else if (detail.topics.isNotEmpty()) {
                // AI off (F1): the topics from notes need no AI, so they stay; nothing else of the AI card does.
                item(key = "topics") {
                    SectionCard(stringResource(Res.string.topics_from_notes), icon = Icons.Rounded.Lightbulb) {
                        detail.topics.forEach { TopicRow(topicText(it)) }
                    }
                }
            }

            item(key = "timeline") {
                SectionCard(
                    stringResource(Res.string.timeline_title),
                    icon = Icons.Rounded.Diversity3,
                    action = {
                        Row {
                            if (detail.timeline.isNotEmpty()) {
                                TextButton(onClick = actions.onOpenTimeline) { Text(stringResource(Res.string.timeline_see_all)) }
                            }
                            IconButton(onClick = actions.onLog) { Icon(Icons.Rounded.Add, stringResource(Res.string.timeline_log)) }
                        }
                    },
                ) {
                    if (detail.timeline.isEmpty()) Hint(stringResource(Res.string.timeline_empty))
                    val latest = detail.timeline.take(3)
                    latest.forEachIndexed { i, entry ->
                        TimelineEntryRow(entry, state.zone, last = i == latest.lastIndex)
                    }
                }
            }

            item(key = "chat") {
                SectionCard(stringResource(Res.string.chat_title), icon = Icons.Rounded.Forum) {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (detail.messageCount > 0) {
                                pluralStringResource(Res.plurals.chat_count, detail.messageCount, detail.messageCount)
                            } else {
                                stringResource(Res.string.chat_how)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "notes") {
                SectionCard(stringResource(Res.string.notes_title), icon = Icons.AutoMirrored.Rounded.Notes) {
                    var notes by rememberSaveable(f.id.value) { mutableStateOf(f.notes) }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = {
                            notes = it
                            actions.onNotesChange(it)
                        },
                        placeholder = { Text(stringResource(Res.string.notes_placeholder)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(120.dp),
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            }

            item(key = "prefs") {
                SectionCard(stringResource(Res.string.prefs_title), icon = Icons.Rounded.Tune) {
                    // F9: a kid can be kept out of the main list (they stay on their parent's page and in birthdays).
                    val parent = detail.parents.firstOrNull()
                    if (parent != null || !f.showInMyList) {
                        ToggleRow(
                            stringResource(Res.string.show_in_my_list),
                            parent?.let { stringResource(Res.string.show_in_my_list_hint, it.displayName.substringBefore(' ')) }
                                ?: stringResource(Res.string.show_in_my_list_hint_any),
                            f.showInMyList,
                        ) { actions.onSetShowInMyList(!f.showInMyList) }
                    }
                    ToggleRow(
                        stringResource(Res.string.prefs_birthday),
                        stringResource(Res.string.prefs_birthday_hint),
                        f.notifyBirthday,
                        actions.onToggleBirthdayNotifications,
                    )
                    if (state.aiEnabled) {
                        ToggleRow(
                            stringResource(Res.string.prefs_events),
                            stringResource(Res.string.prefs_events_hint),
                            f.suggestEvents,
                            actions.onToggleSuggestEvents,
                        )
                    }
                    ToggleRow(
                        stringResource(Res.string.prefs_contact_counts),
                        stringResource(Res.string.prefs_contact_counts_hint),
                        f.contactCountsAsCatchUp,
                        actions.onToggleContactCounts,
                    )
                    ToggleRow(
                        stringResource(Res.string.prefs_pause),
                        stringResource(Res.string.prefs_pause_hint),
                        f.catchUpPaused,
                        actions.onTogglePause,
                    )
                }
            }
        }

        if (addingLink || linkEditing != null) {
            val linked = state.links.mapTo(HashSet()) { it.person.id }
            LinkSheet(
                friend = f,
                others = detail.others.filter { it.id !in linked },
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
        familyEditing?.let { editing ->
            FamilySheet(
                firstName = firstName,
                editing = editing,
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
            val editing = fieldEditing
            CustomFieldDialog(
                field = editing,
                suggestions = detail.customFieldLabels,
                onDismiss = {
                    addingField = false
                    fieldEditing = null
                },
                onSave = { label, value ->
                    actions.onSaveCustomField(editing?.id, label, value)
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
        if (state.showReminderEditor) {
            val people = (listOf(f) + detail.hangouts.flatMap { it.friends }).distinctBy { it.id }
            ReminderEditor(
                friends = people,
                today = state.today,
                onDismiss = actions.onDismissReminderEditor,
                onSave = actions.onSaveReminder,
                preselected = setOf(f.id),
            )
        }
        state.planEditing?.let { editing ->
            val people = (listOf(f) + detail.hangouts.flatMap { it.friends }).distinctBy { it.id }
            PlanEditor(
                friends = people,
                today = state.today,
                onDismiss = actions.onDismissPlanEditor,
                onSave = actions.onSavePlan,
                preselected = setOf(f.id),
                initial = editing.plan,
                circles = detail.groups,
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

/** Opens the catch-up story; stands out when you're seeing them today or tomorrow. */
@Composable
private fun StoryCta(name: String, meetingOn: LocalDate?, today: LocalDate, freshCount: Int, onOpen: () -> Unit) {
    val meeting = meetingOn != null
    val firstName = name.substringBefore(' ')
    val (start, end) = avatarColors(name)
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = if (meeting) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(start, end))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.AutoStories, null, tint = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        meetingOn == today -> stringResource(Res.string.story_meeting_today, firstName)
                        meeting -> stringResource(Res.string.story_meeting_tomorrow, firstName)
                        else -> stringResource(Res.string.story_cta, firstName)
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (freshCount > 0) {
                        pluralStringResource(Res.plurals.story_cta_fresh, freshCount, freshCount)
                    } else {
                        stringResource(if (meeting) Res.string.story_meeting_hint else Res.string.story_cta_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (freshCount > 0) {
                Text(
                    freshCount.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 10.dp, vertical = 4.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StayInTouch(f: Friend, state: FriendDetailUiState, actions: FriendDetailActions) {
    val detail = state.detail ?: return
    SectionCard(stringResource(Res.string.touch_title), icon = Icons.Rounded.Schedule) {
        val goal = detail.cadenceDays
        val days = detail.lastSeen?.toLocalDateTime(state.zone)?.date?.daysUntil(state.today)
        val snoozedUntil = f.snoozedUntil?.takeIf { it >= state.today }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val progress = when {
                goal != null && days != null -> days.toFloat() / goal
                goal != null -> 1f
                else -> 0f
            }
            val overdue = goal != null && progress >= 1f && snoozedUntil == null && !f.catchUpPaused
            ProgressRing(progress, size = 64.dp, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) {
                Text(days?.toString() ?: "–", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    detail.lastSeen?.let { stringResource(Res.string.touch_last_together, relativePast(it, state.now, state.zone)) }
                        ?: stringResource(Res.string.touch_never),
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
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = actions.onCalled) {
                Icon(Icons.Rounded.Call, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.touch_called))
            }
            if (f.catchUpPaused) {
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

        Label(stringResource(Res.string.touch_circle), Icons.Rounded.Groups)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Closeness.entries.forEach { c ->
                FilterChip(f.closeness == c, { actions.onClosenessChange(c) }, label = { Text(closenessLabel(c)) })
            }
        }
        Label(stringResource(Res.string.touch_custom), Icons.Rounded.Schedule)
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(f.meetEveryDays == null, { actions.onMeetEveryChange(null) }, label = { Text(stringResource(CoreRes.string.cadence_off)) })
            RHYTHMS.forEach { d ->
                FilterChip(f.meetEveryDays == d, { actions.onMeetEveryChange(d) }, label = { Text(everyNDays(d)) })
            }
        }
        if (detail.cadenceSource == CadenceRules.Source.CLOSENESS && goal != null) {
            Hint(stringResource(Res.string.touch_from_circle, everyNDays(goal)))
        }
        if (f.isCloseFriend && state.showWarmth) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                WarmthDot(detail.warmth.level, size = 12.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.warmth_title, detail.warmth.value) + " · " + warmthLabel(detail.warmth.level),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Hint(
                stringResource(
                    when (detail.warmth.level) {
                        Warmth.Level.WARM -> Res.string.warmth_hint_warm
                        Warmth.Level.COOLING -> Res.string.warmth_hint_cooling
                        Warmth.Level.FADING -> Res.string.warmth_hint_fading
                    },
                ),
            )
        }
        LastContactRow(detail.lastContactByType, state)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Gifts(f: Friend, state: FriendDetailUiState, actions: FriendDetailActions, onAdd: () -> Unit, onGive: (Gift) -> Unit) {
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
                IconButton(onClick = {
                    actions.onRemoveGift(gift.id)
                }) { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.remove_item, gift.title)) }
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
                IconButton(onClick = {
                    actions.onRemoveGift(gift.id)
                }) { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.remove_item, gift.title)) }
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
        // F8: gift lists per occasion live on the Together tab; this is the way there from a person.
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(
    f: Friend,
    state: FriendDetailUiState,
    actions: FriendDetailActions,
    onEditBirthday: () -> Unit,
    onEditName: () -> Unit,
    onPhoto: () -> Unit,
    onDelete: () -> Unit,
    onEditRelationToMe: () -> Unit,
    onAddCircle: () -> Unit,
) {
    val (start, end) = avatarColors(f.displayName)
    val uri = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current
    val phone = f.phoneNumbers.firstOrNull()
    val digits = phone?.filter { it.isDigit() || it == '+' }
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(start.copy(alpha = 0.4f), end.copy(alpha = 0.16f), MaterialTheme.colorScheme.background)),
            ),
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val glass = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                FilledTonalIconButton(onClick = actions.onBack, colors = glass) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.detail_back))
                }
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(onClick = onDelete, colors = glass) {
                    Icon(Icons.Rounded.Delete, stringResource(Res.string.detail_remove_friend))
                }
            }
            // F14: the avatar (and its camera badge) is always tappable: pick a photo, or replace, crop or remove it.
            val photoLabel = if (f.avatar == null) {
                stringResource(Res.string.detail_add_photo)
            } else {
                stringResource(Res.string.photo_sheet_title, f.displayName.substringBefore(' '))
            }
            Box {
                FriendAvatar(
                    f,
                    size = 112.dp,
                    highlighted = f.isCloseFriend,
                    sharedKey = "avatar-${f.id.value}",
                    modifier = Modifier.clip(CircleShape).clickable(onClickLabel = photoLabel, onClick = onPhoto),
                )
                Surface(
                    onClick = onPhoto,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                    modifier = Modifier.align(Alignment.BottomEnd).size(34.dp),
                ) {
                    Icon(Icons.Rounded.AddAPhoto, contentDescription = photoLabel, modifier = Modifier.padding(7.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            // F19: the pencil edits the name shown everywhere in the app.
            Row(
                Modifier.padding(horizontal = 24.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onEditName),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(f.displayName, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = stringResource(Res.string.detail_edit_name),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp).size(18.dp),
                )
            }
            // Issue #86: their level sits under the name (where the heart was) and opens the closeness picker.
            var pickLevel by rememberSaveable { mutableStateOf(false) }
            val changeLevel = stringResource(Res.string.closeness_change)
            Row(
                Modifier
                    .padding(top = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable(onClickLabel = changeLevel) { pickLevel = true }
                    .padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    closenessLabel(f.closeness),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Icon(Icons.Rounded.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            }
            if (pickLevel) {
                ClosenessSheet(f, state.cadenceRules, onPick = {
                    pickLevel = false
                    actions.onClosenessChange(it)
                }, onDismiss = { pickLevel = false })
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                val birthdayText = f.birthday?.let { b ->
                    val next = b.nextOccurrence(state.today)
                    val base = if (isFarDate(next, state.today)) {
                        next.shortLabel()
                    } else {
                        stringResource(Res.string.detail_birthday_when, next.shortLabel(), relativeDayPhrase(next, state.today))
                    }
                    b.ageOn(next)?.let { stringResource(Res.string.detail_birthday_turns, base, it) } ?: base
                } ?: stringResource(Res.string.detail_add_birthday)
                Box(Modifier.clip(CircleShape).clickable(onClick = onEditBirthday)) {
                    InfoPill(birthdayText, icon = if (f.birthday != null) Icons.Rounded.Cake else Icons.Rounded.Edit, emphasized = f.birthday != null)
                }
                // "Your brother" (F6); tapping it (or "Related to you?") opens the "is your…" sheet.
                Box(Modifier.clip(CircleShape).clickable(onClick = onEditRelationToMe)) {
                    val relation = relationToMeLabel(f)
                    InfoPill(
                        relation ?: stringResource(Res.string.relation_to_me_add),
                        icon = if (relation != null) Icons.Rounded.FavoriteBorder else Icons.Rounded.Edit,
                        emphasized = relation != null,
                    )
                }
                state.detail?.lastSeen?.let {
                    InfoPill(stringResource(Res.string.detail_seen, relativePast(it, state.now, state.zone)), icon = Icons.Rounded.Place)
                }
                if (f.isOrphaned) InfoPill(stringResource(Res.string.detail_orphaned))
            }
            Spacer(Modifier.height(10.dp))
            // Their circles (issue #70): tap opens the circle, × takes them out (Undo), "+ Circle" ticks more. The
            // built-in Family circle follows relations, so it has no ×.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                state.detail?.groups.orEmpty().forEach { g ->
                    CircleChip(
                        g,
                        selected = true,
                        onClick = { actions.onOpenCircle(g.id) },
                        showDot = false,
                        onRemove = if (g.isBuiltIn) null else ({ actions.onRemoveFromCircle(g.id) }),
                        removeLabel = stringResource(Res.string.circles_remove, g.name),
                    )
                }
                AssistChip(
                    onClick = onAddCircle,
                    label = { Text(stringResource(Res.string.circles_add)) },
                    leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                val email = f.emails.firstOrNull()?.address
                // The preferred channel's action comes first and is the highlighted one; Together keeps its heart burst.
                val preferred = f.preferredChannel?.takeIf { it != ContactChannel.EMAIL || email != null }
                val channels = buildList {
                    add(ContactChannel.CALL)
                    add(ContactChannel.SMS)
                    add(ContactChannel.WHATSAPP)
                    if (preferred == ContactChannel.EMAIL) add(ContactChannel.EMAIL)
                }.sortedBy { if (it == preferred) 0 else 1 }

                @Composable
                fun ChannelAction(channel: ContactChannel) {
                    val (icon, label) = when (channel) {
                        ContactChannel.CALL -> Icons.Rounded.Call to stringResource(Res.string.qa_call)
                        ContactChannel.SMS -> Icons.Rounded.Sms to stringResource(Res.string.qa_text)
                        ContactChannel.WHATSAPP -> Icons.AutoMirrored.Rounded.Chat to stringResource(Res.string.qa_whatsapp)
                        ContactChannel.EMAIL -> Icons.Rounded.Email to stringResource(Res.string.qa_email)
                    }
                    val link = when (channel) {
                        ContactChannel.CALL -> digits?.let { "tel:$it" }
                        ContactChannel.SMS -> digits?.let { "sms:$it" }
                        ContactChannel.WHATSAPP -> digits?.let { "https://wa.me/" + it.filter(Char::isDigit) }
                        ContactChannel.EMAIL -> email?.let { "mailto:${it.trim()}" }
                    }
                    QuickAction(
                        icon,
                        label,
                        onClick = { link?.let(uri::openUri) },
                        enabled = link != null,
                        emphasized = channel == preferred,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (preferred != null) ChannelAction(channels.first())
                QuickAction(Icons.Rounded.Diversity3, stringResource(Res.string.qa_together), onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    actions.onWeMet()
                }, emphasized = preferred == null, modifier = Modifier.weight(1f))
                channels.drop(if (preferred != null) 1 else 0).forEach { ChannelAction(it) }
                QuickAction(
                    Icons.Rounded.AlarmAdd,
                    stringResource(Res.string.qa_remind),
                    onClick = actions.onOpenReminderEditor,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Label(text: String, icon: ImageVector) {
    Row(Modifier.padding(start = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: () -> Unit) {
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
private fun Hint(text: String) {
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
