package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Place
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.usecase.CadenceRules
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.Warmth
import com.neteinstein.goodcompany.core.ui.components.DateBadge
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.HeartBurst
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.core.ui.components.LocalDatePickerDialog
import com.neteinstein.goodcompany.core.ui.components.PlanEditor
import com.neteinstein.goodcompany.core.ui.components.PlanRow
import com.neteinstein.goodcompany.core.ui.components.ProgressRing
import com.neteinstein.goodcompany.core.ui.components.QuickAction
import com.neteinstein.goodcompany.core.ui.components.ReminderEditor
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.components.SendMessageSheet
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.components.TagEditor
import com.neteinstein.goodcompany.core.ui.components.WarmthDot
import com.neteinstein.goodcompany.core.ui.components.avatarColors
import com.neteinstein.goodcompany.core.ui.components.calendarEntry
import com.neteinstein.goodcompany.core.ui.format.isFarDate
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.relativeDayPhrase
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.format.shortLabelText
import com.neteinstein.goodcompany.core.ui.l10n.aiErrorText
import com.neteinstein.goodcompany.core.ui.l10n.circleLabel
import com.neteinstein.goodcompany.core.ui.l10n.everyNDays
import com.neteinstein.goodcompany.core.ui.l10n.occasionLabel
import com.neteinstein.goodcompany.core.ui.l10n.topicText
import com.neteinstein.goodcompany.core.ui.l10n.warmthLabel
import com.neteinstein.goodcompany.core.ui.platform.rememberCalendarAdder
import com.neteinstein.goodcompany.core.ui.platform.rememberTextFilePicker
import com.neteinstein.goodcompany.core.ui.resources.add
import com.neteinstein.goodcompany.core.ui.resources.cadence_off
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.close
import com.neteinstein.goodcompany.core.ui.resources.plan_cancelled
import com.neteinstein.goodcompany.core.ui.resources.plan_closed
import com.neteinstein.goodcompany.core.ui.resources.plan_logged
import com.neteinstein.goodcompany.core.ui.resources.plan_saved
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
import com.neteinstein.goodcompany.feature.friends.resources.contact_call
import com.neteinstein.goodcompany.feature.friends.resources.contact_met
import com.neteinstein.goodcompany.feature.friends.resources.contact_whatsapp
import com.neteinstein.goodcompany.feature.friends.resources.delete_body
import com.neteinstein.goodcompany.feature.friends.resources.delete_body_contact
import com.neteinstein.goodcompany.feature.friends.resources.delete_title
import com.neteinstein.goodcompany.feature.friends.resources.detail_add_birthday
import com.neteinstein.goodcompany.feature.friends.resources.detail_back
import com.neteinstein.goodcompany.feature.friends.resources.detail_birthday_turns
import com.neteinstein.goodcompany.feature.friends.resources.detail_birthday_when
import com.neteinstein.goodcompany.feature.friends.resources.detail_missing
import com.neteinstein.goodcompany.feature.friends.resources.detail_orphaned
import com.neteinstein.goodcompany.feature.friends.resources.detail_remove_friend
import com.neteinstein.goodcompany.feature.friends.resources.detail_seen
import com.neteinstein.goodcompany.feature.friends.resources.gifts_for_kid
import com.neteinstein.goodcompany.feature.friends.resources.gifts_given
import com.neteinstein.goodcompany.feature.friends.resources.gifts_given_on
import com.neteinstein.goodcompany.feature.friends.resources.gifts_hint
import com.neteinstein.goodcompany.feature.friends.resources.gifts_ideas
import com.neteinstein.goodcompany.feature.friends.resources.gifts_mark_given
import com.neteinstein.goodcompany.feature.friends.resources.gifts_none_found
import com.neteinstein.goodcompany.feature.friends.resources.gifts_suggest
import com.neteinstein.goodcompany.feature.friends.resources.gifts_suggest_for
import com.neteinstein.goodcompany.feature.friends.resources.gifts_suggestions
import com.neteinstein.goodcompany.feature.friends.resources.gifts_title
import com.neteinstein.goodcompany.feature.friends.resources.groups_member_of
import com.neteinstein.goodcompany.feature.friends.resources.ideas_dismiss
import com.neteinstein.goodcompany.feature.friends.resources.ideas_find
import com.neteinstein.goodcompany.feature.friends.resources.ideas_hint_ai
import com.neteinstein.goodcompany.feature.friends.resources.ideas_hint_ai_chats
import com.neteinstein.goodcompany.feature.friends.resources.ideas_hint_no_ai
import com.neteinstein.goodcompany.feature.friends.resources.ideas_open
import com.neteinstein.goodcompany.feature.friends.resources.ideas_setup
import com.neteinstein.goodcompany.feature.friends.resources.ideas_title
import com.neteinstein.goodcompany.feature.friends.resources.kids_birthday
import com.neteinstein.goodcompany.feature.friends.resources.kids_birthday_turns
import com.neteinstein.goodcompany.feature.friends.resources.kids_hint
import com.neteinstein.goodcompany.feature.friends.resources.kids_loves
import com.neteinstein.goodcompany.feature.friends.resources.kids_remove
import com.neteinstein.goodcompany.feature.friends.resources.kids_title
import com.neteinstein.goodcompany.feature.friends.resources.moments_hint
import com.neteinstein.goodcompany.feature.friends.resources.moments_reminder
import com.neteinstein.goodcompany.feature.friends.resources.moments_title
import com.neteinstein.goodcompany.feature.friends.resources.msg_call_logged_short
import com.neteinstein.goodcompany.feature.friends.resources.msg_chat_imported
import com.neteinstein.goodcompany.feature.friends.resources.msg_chat_invalid
import com.neteinstein.goodcompany.feature.friends.resources.msg_chat_matched
import com.neteinstein.goodcompany.feature.friends.resources.msg_ideas_found
import com.neteinstein.goodcompany.feature.friends.resources.msg_ideas_none
import com.neteinstein.goodcompany.feature.friends.resources.msg_logged_with
import com.neteinstein.goodcompany.feature.friends.resources.msg_reminder_saved
import com.neteinstein.goodcompany.feature.friends.resources.notes_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.notes_title
import com.neteinstein.goodcompany.feature.friends.resources.occasions_hint
import com.neteinstein.goodcompany.feature.friends.resources.occasions_title
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
import com.neteinstein.goodcompany.feature.friends.resources.prefs_real
import com.neteinstein.goodcompany.feature.friends.resources.prefs_real_hint
import com.neteinstein.goodcompany.feature.friends.resources.prefs_title
import com.neteinstein.goodcompany.feature.friends.resources.qa_call
import com.neteinstein.goodcompany.feature.friends.resources.qa_remind
import com.neteinstein.goodcompany.feature.friends.resources.qa_text
import com.neteinstein.goodcompany.feature.friends.resources.qa_together
import com.neteinstein.goodcompany.feature.friends.resources.qa_whatsapp
import com.neteinstein.goodcompany.feature.friends.resources.row_mark_real
import com.neteinstein.goodcompany.feature.friends.resources.row_unmark_real
import com.neteinstein.goodcompany.feature.friends.resources.snooze_week
import com.neteinstein.goodcompany.feature.friends.resources.snooze_weekend
import com.neteinstein.goodcompany.feature.friends.resources.tastes_dislikes
import com.neteinstein.goodcompany.feature.friends.resources.tastes_dislikes_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.tastes_loves
import com.neteinstein.goodcompany.feature.friends.resources.tastes_loves_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.tastes_title
import com.neteinstein.goodcompany.feature.friends.resources.together_hint
import com.neteinstein.goodcompany.feature.friends.resources.together_hung_out
import com.neteinstein.goodcompany.feature.friends.resources.together_title
import com.neteinstein.goodcompany.feature.friends.resources.topics_ai
import com.neteinstein.goodcompany.feature.friends.resources.topics_from_notes
import com.neteinstein.goodcompany.feature.friends.resources.touch_called
import com.neteinstein.goodcompany.feature.friends.resources.touch_circle
import com.neteinstein.goodcompany.feature.friends.resources.touch_circle_none
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
import com.neteinstein.goodcompany.feature.friends.ui.components.GiftDialog
import com.neteinstein.goodcompany.feature.friends.ui.components.GiftGivenDialog
import com.neteinstein.goodcompany.feature.friends.ui.components.ImportantEventDialog
import com.neteinstein.goodcompany.feature.friends.ui.components.KidDialog
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
    viewModel: FriendDetailViewModel = koinViewModel(key = friendId.value) { parametersOf(friendId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickChat = rememberTextFilePicker(onPicked = viewModel::onChatPicked)
    val addToCalendar = rememberCalendarAdder()
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    FriendDetailContent(
        state = state,
        actions = FriendDetailActions(
            onBack = onBack,
            onOpenSettings = onOpenSettings,
            onToggleClose = viewModel::onToggleClose,
            onCircleChange = viewModel::onCircleChange,
            onToggleBirthdayNotifications = viewModel::onToggleBirthdayNotifications,
            onToggleSuggestEvents = viewModel::onToggleSuggestEvents,
            onMeetEveryChange = viewModel::onMeetEveryChange,
            onSnooze = viewModel::onSnooze,
            onClearSnooze = viewModel::onClearSnooze,
            onCalled = viewModel::onCalled,
            onBirthdayChange = viewModel::onBirthdayChange,
            onAddLikes = viewModel::onAddLikes,
            onRemoveLike = viewModel::onRemoveLike,
            onAddDislikes = viewModel::onAddDislikes,
            onRemoveDislike = viewModel::onRemoveDislike,
            onNotesChange = viewModel::onNotesChange,
            onSaveKid = viewModel::onSaveKid,
            onRemoveKid = viewModel::onRemoveKid,
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
            onImportChat = pickChat,
            onGenerateIdeas = viewModel::onGenerateIdeas,
            onDismissIdea = viewModel::onDismissIdea,
            onDelete = viewModel::onDelete,
            onTogetherNow = viewModel::onTogetherNow,
            onUndoTogether = viewModel::onUndoTogether,
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
            onCancelPlan = viewModel::onCancelPlan,
            onUndoCancelPlan = viewModel::onUndoCancelPlan,
            onAddPlanToCalendar = { plan, friends -> addToCalendar(plan.calendarEntry(friends)) },
        ),
    )
}

/** All UI events of the detail screen, so the content composable stays previewable. */
data class FriendDetailActions(
    val onBack: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onToggleClose: () -> Unit = {},
    val onCircleChange: (Circle?) -> Unit = {},
    val onToggleBirthdayNotifications: () -> Unit = {},
    val onToggleSuggestEvents: () -> Unit = {},
    val onMeetEveryChange: (Int?) -> Unit = {},
    val onSnooze: (SnoozeOption) -> Unit = {},
    val onClearSnooze: () -> Unit = {},
    val onCalled: () -> Unit = {},
    val onBirthdayChange: (LocalDate?) -> Unit = {},
    val onAddLikes: (List<String>) -> Unit = {},
    val onRemoveLike: (String) -> Unit = {},
    val onAddDislikes: (List<String>) -> Unit = {},
    val onRemoveDislike: (String) -> Unit = {},
    val onNotesChange: (String) -> Unit = {},
    val onSaveKid: (id: String?, name: String, birthday: LocalDate?, likes: List<String>) -> Unit = { _, _, _, _ -> },
    val onRemoveKid: (String) -> Unit = {},
    val onAddEvent: (String, LocalDate, Int) -> Unit = { _, _, _ -> },
    val onRemoveEvent: (String) -> Unit = {},
    val onSaveGift: (title: String, note: String, forKidId: String?) -> Unit = { _, _, _ -> },
    val onRemoveGift: (String) -> Unit = {},
    val onMarkGiftGiven: (String, GiftOccasion?) -> Unit = { _, _ -> },
    val onSuggestGifts: (kidId: String?) -> Unit = {},
    val onKeepGiftSuggestion: (String) -> Unit = {},
    val onDismissGiftSuggestions: () -> Unit = {},
    val onOpenWish: () -> Unit = {},
    val onDismissWish: () -> Unit = {},
    val onPersonaliseWish: () -> Unit = {},
    val onImportChat: () -> Unit = {},
    val onGenerateIdeas: () -> Unit = {},
    val onDismissIdea: (String) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onTogetherNow: () -> Unit = {},
    val onUndoTogether: () -> Unit = {},
    val onOpenReminderEditor: () -> Unit = {},
    val onDismissReminderEditor: () -> Unit = {},
    val onSaveReminder: (String, List<FriendId>, LocalDate?, Int) -> Unit = { _, _, _, _ -> },
    val onMessageShown: () -> Unit = {},
    val onTogglePause: () -> Unit = {},
    val onToggleContactCounts: () -> Unit = {},
    val onToggleOccasion: (Occasion) -> Unit = {},
    val onOpenPlanEditor: (Plan?) -> Unit = {},
    val onDismissPlanEditor: () -> Unit = {},
    val onSavePlan: (String, List<FriendId>, LocalDate, String?) -> Unit = { _, _, _, _ -> },
    val onConfirmPlan: (id: String, happened: Boolean) -> Unit = { _, _ -> },
    val onCancelPlan: (Plan) -> Unit = {},
    val onUndoCancelPlan: () -> Unit = {},
    val onAddPlanToCalendar: (Plan, List<Friend>) -> Unit = { _, _ -> },
)

private val RHYTHMS = listOf(7, 14, 30, 90)

/** Localised snackbar text for a [FriendDetailMessage]. */
suspend fun FriendDetailMessage.text(): String = when (this) {
    is FriendDetailMessage.LoggedWith -> getString(Res.string.msg_logged_with, name)
    FriendDetailMessage.CallLogged -> getString(Res.string.msg_call_logged_short)
    is FriendDetailMessage.Snoozed -> getString(Res.string.touch_snoozed, until.shortLabelText())
    is FriendDetailMessage.ChatImported -> getPluralString(Res.plurals.msg_chat_imported, messages, messages).let { base ->
        sender?.let { getString(Res.string.msg_chat_matched, base, it) } ?: base
    }
    FriendDetailMessage.ChatInvalid -> getString(Res.string.msg_chat_invalid)
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FriendDetailContent(state: FriendDetailUiState, actions: FriendDetailActions) {
    val snackbar = remember { SnackbarHostState() }
    var kidEditing by remember { mutableStateOf<Kid?>(null) }
    var addingKid by rememberSaveable { mutableStateOf(false) }
    var addingEvent by rememberSaveable { mutableStateOf(false) }
    var addingGift by rememberSaveable { mutableStateOf(false) }
    var givingGift by remember { mutableStateOf<Gift?>(null) }
    var pickBirthday by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val undoLabel = stringResource(CoreRes.string.undo)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val undoable = state.undoHangoutId != null || state.undoPlan != null
        val result = snackbar.showSnackbar(message.text(), actionLabel = if (undoable) undoLabel else null)
        if (result == SnackbarResult.ActionPerformed) {
            if (state.undoPlan != null) actions.onUndoCancelPlan() else actions.onUndoTogether()
        }
        actions.onMessageShown()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val detail = state.detail
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator() else Text(stringResource(Res.string.detail_missing))
            }
            return@Scaffold
        }
        val f = detail.friend
        val firstName = f.name.substringBefore(' ')
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 32.dp)) {
            item(key = "hero") { Hero(f, state, actions, onEditBirthday = { pickBirthday = true }, onDelete = { confirmDelete = true }) }

            val nextBirthday = f.birthday?.nextOccurrence(state.today)
            if (nextBirthday != null && state.today.daysUntil(nextBirthday) in 0..7) {
                item(key = "bday") { BirthdayBanner(firstName, nextBirthday, state.today, actions.onOpenWish) }
            }

            item(key = "touch") { StayInTouch(f, state, actions) }

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
                        PlanRow(
                            plan = plan,
                            friends = with.filterNot { it.id == f.id },
                            today = state.today,
                            onConfirm = { happened -> actions.onConfirmPlan(plan.id, happened) },
                            onAddToCalendar = { actions.onAddPlanToCalendar(plan, with) },
                            onChange = { actions.onOpenPlanEditor(plan) },
                            onCancel = { actions.onCancelPlan(plan) },
                        )
                    }
                }
            }

            item(key = "tastes") {
                SectionCard(stringResource(Res.string.tastes_title), icon = Icons.Rounded.ThumbUp) {
                    Label(stringResource(Res.string.tastes_loves), Icons.Rounded.ThumbUp)
                    TagEditor(f.likes, stringResource(Res.string.tastes_loves_placeholder), actions.onAddLikes, actions.onRemoveLike)
                    Label(stringResource(Res.string.tastes_dislikes), Icons.Rounded.ThumbDown)
                    TagEditor(
                        f.dislikes,
                        stringResource(Res.string.tastes_dislikes_placeholder),
                        actions.onAddDislikes,
                        actions.onRemoveDislike,
                        chipColor = MaterialTheme.colorScheme.errorContainer,
                    )
                }
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

            item(key = "kids") {
                SectionCard(
                    stringResource(Res.string.kids_title),
                    icon = Icons.Rounded.ChildCare,
                    action = { TextButton(onClick = { addingKid = true }) { Text(stringResource(CoreRes.string.add)) } },
                ) {
                    if (f.kids.isEmpty()) Hint(stringResource(Res.string.kids_hint))
                    f.kids.forEach { kid ->
                        ListItem(
                            modifier = Modifier.clickable { kidEditing = kid },
                            leadingContent = { FriendAvatar(kid.name, kid.name.take(1).uppercase(), size = 44.dp) },
                            headlineContent = { Text(kid.name, style = MaterialTheme.typography.titleSmall) },
                            supportingContent = {
                                val parts = listOfNotNull(
                                    kid.birthday?.let { b ->
                                        val next = b.nextOccurrence(state.today)
                                        val day = relativeDay(next, state.today)
                                        b.ageOn(next)?.let { stringResource(Res.string.kids_birthday_turns, day, it) }
                                            ?: stringResource(Res.string.kids_birthday, day)
                                    },
                                    kid.likes.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.kids_loves, it.joinToString()) },
                                )
                                if (parts.isNotEmpty()) Text(parts.joinToString("\n"))
                            },
                            trailingContent = {
                                IconButton(onClick = { actions.onRemoveKid(kid.id) }) {
                                    Icon(Icons.Rounded.Close, stringResource(Res.string.kids_remove, kid.name))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
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

            item(key = "together") {
                SectionCard(stringResource(Res.string.together_title), icon = Icons.Rounded.Diversity3) {
                    if (detail.hangouts.isEmpty()) Hint(stringResource(Res.string.together_hint))
                    detail.hangouts.take(8).forEach { h ->
                        val others = h.friends.filterNot { it.id == f.id }
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            DateBadge(h.hangout.at.toLocalDateTime(state.zone).date, state.today)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    h.hangout.place?.label ?: stringResource(Res.string.together_hung_out),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    listOfNotNull(
                                        relativePast(h.hangout.at, state.now, state.zone),
                                        h.hangout.note.ifBlank {
                                            null
                                        },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (others.isNotEmpty()) StackedAvatars(others.map { it.name to it.initials }, size = 28.dp, max = 3)
                        }
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
                        OutlinedButton(onClick = actions.onImportChat) {
                            Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.chat_import))
                        }
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
                    ToggleRow(
                        stringResource(Res.string.prefs_real),
                        stringResource(Res.string.prefs_real_hint),
                        f.isCloseFriend,
                        actions.onToggleClose,
                    )
                    ToggleRow(
                        stringResource(Res.string.prefs_birthday),
                        stringResource(Res.string.prefs_birthday_hint),
                        f.notifyBirthday,
                        actions.onToggleBirthdayNotifications,
                    )
                    ToggleRow(
                        stringResource(Res.string.prefs_events),
                        stringResource(Res.string.prefs_events_hint),
                        f.suggestEvents,
                        actions.onToggleSuggestEvents,
                    )
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

        if (addingKid || kidEditing != null) {
            val editing = kidEditing
            KidDialog(
                kid = editing,
                onDismiss = {
                    addingKid = false
                    kidEditing = null
                },
                onSave = { name, birthday, likes ->
                    actions.onSaveKid(editing?.id, name, birthday, likes)
                    addingKid = false
                    kidEditing = null
                },
            )
        }
        if (addingEvent) {
            ImportantEventDialog(state.today, { addingEvent = false }) { t, d, b ->
                actions.onAddEvent(t, d, b)
                addingEvent = false
            }
        }
        if (addingGift) {
            GiftDialog(f, onDismiss = { addingGift = false }) { title, note, kidId ->
                actions.onSaveGift(title, note, kidId)
                addingGift = false
            }
        }
        givingGift?.let { gift ->
            GiftGivenDialog(gift, onDismiss = { givingGift = null }) { occasion ->
                actions.onMarkGiftGiven(gift.id, occasion)
                givingGift = null
            }
        }
        if (pickBirthday) {
            val initial = f.birthday?.let { LocalDate(it.year ?: 1990, Month(it.month), it.day) }
            LocalDatePickerDialog(initial, onDismiss = { pickBirthday = false }, onPicked = actions.onBirthdayChange)
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
            )
        }
        if (state.showWish) {
            val ideas = f.gifts.filter { it.isIdea && it.forKidId == null }
            SendMessageSheet(
                title = stringResource(Res.string.wish_title, firstName),
                suggestion = state.wishDraft ?: stringResource(CoreRes.string.wish_default, firstName),
                phone = f.phoneNumbers.firstOrNull(),
                onDismiss = actions.onDismissWish,
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
                title = { Text(stringResource(Res.string.delete_title, f.name)) },
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
            FilterChip(f.circle == null, { actions.onCircleChange(null) }, label = { Text(stringResource(Res.string.touch_circle_none)) })
            Circle.entries.forEach { c ->
                FilterChip(f.circle == c, { actions.onCircleChange(c) }, label = { Text(circleLabel(c)) })
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
        if (detail.cadenceSource == CadenceRules.Source.CIRCLE && goal != null) {
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
        if (detail.groups.isNotEmpty()) {
            Label(stringResource(Res.string.groups_member_of), Icons.Rounded.Diversity3)
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.groups.forEach { g -> InfoPill("${g.emoji} ${g.name}") }
            }
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
        val kidName = { id: String? -> f.kids.firstOrNull { it.id == id }?.name }
        if (f.gifts.isEmpty()) Hint(stringResource(Res.string.gifts_hint))
        if (ideas.isNotEmpty()) Label(stringResource(Res.string.gifts_ideas), Icons.Rounded.AutoAwesome)
        ideas.forEach { gift ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(gift.title, style = MaterialTheme.typography.titleSmall)
                    val sub = listOfNotNull(kidName(gift.forKidId)?.let { stringResource(Res.string.gifts_for_kid, it) }, gift.note.ifBlank { null })
                    if (sub.isNotEmpty()) {
                        Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    val parts = listOfNotNull(
                        gift.occasion?.let { stringResource(Res.string.gifts_given_on, occasionLabel(it), date) } ?: date,
                        kidName(gift.forKidId)?.let { stringResource(Res.string.gifts_for_kid, it) },
                    )
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    onClick = { actions.onSuggestGifts(null) },
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
                f.kids.forEach { kid ->
                    AssistChip(
                        onClick = { actions.onSuggestGifts(kid.id) },
                        enabled = busy == null,
                        label = { Text(stringResource(Res.string.gifts_suggest_for, kid.name)) },
                        leadingIcon = {
                            if (busy ==
                                kid.id
                            ) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.ChildCare, null, Modifier.size(18.dp))
                            }
                        },
                    )
                }
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(f: Friend, state: FriendDetailUiState, actions: FriendDetailActions, onEditBirthday: () -> Unit, onDelete: () -> Unit) {
    val (start, end) = avatarColors(f.name)
    val uri = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current
    val phone = f.phoneNumbers.firstOrNull()
    val digits = phone?.filter { it.isDigit() || it == '+' }
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(start.copy(alpha = 0.55f), end.copy(alpha = 0.22f), MaterialTheme.colorScheme.background)),
            ),
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                val glass = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                FilledTonalIconButton(onClick = actions.onBack, colors = glass) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.detail_back))
                }
                Spacer(Modifier.weight(1f))
                FilledTonalIconButton(
                    onClick = actions.onToggleClose,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        contentColor = if (f.isCloseFriend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(
                        if (f.isCloseFriend) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        stringResource(if (f.isCloseFriend) Res.string.row_unmark_real else Res.string.row_mark_real),
                    )
                }
                FilledTonalIconButton(onClick = onDelete, colors = glass) {
                    Icon(Icons.Rounded.Delete, stringResource(Res.string.detail_remove_friend))
                }
            }
            FriendAvatar(f.name, f.initials, size = 112.dp, highlighted = f.isCloseFriend, sharedKey = "avatar-${f.id.value}")
            Spacer(Modifier.height(12.dp))
            Text(
                f.name,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
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
                f.circle?.takeIf { f.isCloseFriend }?.let { InfoPill(circleLabel(it), icon = Icons.Rounded.Groups) }
                state.detail?.lastSeen?.let {
                    InfoPill(stringResource(Res.string.detail_seen, relativePast(it, state.now, state.zone)), icon = Icons.Rounded.Place)
                }
                if (f.isOrphaned) InfoPill(stringResource(Res.string.detail_orphaned))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    QuickAction(Icons.Rounded.Diversity3, stringResource(Res.string.qa_together), onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        actions.onTogetherNow()
                    }, emphasized = true)
                    HeartBurst(state.celebrate, Modifier.size(72.dp))
                }
                QuickAction(Icons.Rounded.Call, stringResource(Res.string.qa_call), onClick = {
                    digits?.let { uri.openUri("tel:$it") }
                }, enabled = digits != null, modifier = Modifier.weight(1f))
                QuickAction(Icons.Rounded.Sms, stringResource(Res.string.qa_text), onClick = {
                    digits?.let { uri.openUri("sms:$it") }
                }, enabled = digits != null, modifier = Modifier.weight(1f))
                QuickAction(
                    Icons.AutoMirrored.Rounded.Chat,
                    stringResource(Res.string.qa_whatsapp),
                    onClick = { digits?.let { uri.openUri("https://wa.me/" + it.filter(Char::isDigit)) } },
                    enabled = digits != null,
                    modifier = Modifier.weight(1f),
                )
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
