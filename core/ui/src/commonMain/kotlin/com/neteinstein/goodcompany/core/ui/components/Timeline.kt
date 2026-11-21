package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsTennis
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.ActivityChoice
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.NoteMedia
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntry
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.l10n.occasionLabel
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.activity_at_home
import com.neteinstein.goodcompany.core.ui.resources.activity_coffee
import com.neteinstein.goodcompany.core.ui.resources.activity_culture
import com.neteinstein.goodcompany.core.ui.resources.activity_dinner
import com.neteinstein.goodcompany.core.ui.resources.activity_drinks
import com.neteinstein.goodcompany.core.ui.resources.activity_lunch
import com.neteinstein.goodcompany.core.ui.resources.activity_music
import com.neteinstein.goodcompany.core.ui.resources.activity_running
import com.neteinstein.goodcompany.core.ui.resources.activity_sports
import com.neteinstein.goodcompany.core.ui.resources.activity_trip
import com.neteinstein.goodcompany.core.ui.resources.activity_walk
import com.neteinstein.goodcompany.core.ui.resources.delete
import com.neteinstein.goodcompany.core.ui.resources.edit
import com.neteinstein.goodcompany.core.ui.resources.tl_activity_at
import com.neteinstein.goodcompany.core.ui.resources.tl_and
import com.neteinstein.goodcompany.core.ui.resources.tl_call
import com.neteinstein.goodcompany.core.ui.resources.tl_call_minutes
import com.neteinstein.goodcompany.core.ui.resources.tl_email
import com.neteinstein.goodcompany.core.ui.resources.tl_entry_options
import com.neteinstein.goodcompany.core.ui.resources.tl_game
import com.neteinstein.goodcompany.core.ui.resources.tl_gift
import com.neteinstein.goodcompany.core.ui.resources.tl_journal
import com.neteinstein.goodcompany.core.ui.resources.tl_long_chat
import com.neteinstein.goodcompany.core.ui.resources.tl_message
import com.neteinstein.goodcompany.core.ui.resources.tl_messages
import com.neteinstein.goodcompany.core.ui.resources.tl_other
import com.neteinstein.goodcompany.core.ui.resources.tl_shared
import com.neteinstein.goodcompany.core.ui.resources.tl_source_call_log
import com.neteinstein.goodcompany.core.ui.resources.tl_source_imported
import com.neteinstein.goodcompany.core.ui.resources.tl_source_shared
import com.neteinstein.goodcompany.core.ui.resources.tl_together
import com.neteinstein.goodcompany.core.ui.resources.tl_video_call
import com.neteinstein.goodcompany.core.ui.resources.tl_whats_new
import com.neteinstein.goodcompany.core.ui.resources.tl_whatsapp
import com.neteinstein.goodcompany.core.ui.resources.tl_with
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun activityLabel(activity: HangoutActivity): String = stringResource(
    when (activity) {
        HangoutActivity.LUNCH -> Res.string.activity_lunch
        HangoutActivity.DINNER -> Res.string.activity_dinner
        HangoutActivity.DRINKS -> Res.string.activity_drinks
        HangoutActivity.COFFEE -> Res.string.activity_coffee
        HangoutActivity.SPORTS -> Res.string.activity_sports
        HangoutActivity.RUNNING -> Res.string.activity_running
        HangoutActivity.WALK -> Res.string.activity_walk
        HangoutActivity.AT_HOME -> Res.string.activity_at_home
        HangoutActivity.CULTURE -> Res.string.activity_culture
        HangoutActivity.MUSIC -> Res.string.activity_music
        HangoutActivity.TRIP -> Res.string.activity_trip
    },
)

fun activityIcon(activity: HangoutActivity): ImageVector = when (activity) {
    HangoutActivity.LUNCH -> Icons.Rounded.LunchDining
    HangoutActivity.DINNER -> Icons.Rounded.DinnerDining
    HangoutActivity.DRINKS -> Icons.Rounded.LocalBar
    HangoutActivity.COFFEE -> Icons.Rounded.LocalCafe
    HangoutActivity.SPORTS -> Icons.Rounded.SportsTennis
    HangoutActivity.RUNNING -> Icons.AutoMirrored.Rounded.DirectionsRun
    HangoutActivity.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
    HangoutActivity.AT_HOME -> Icons.Rounded.Home
    HangoutActivity.CULTURE -> Icons.Rounded.TheaterComedy
    HangoutActivity.MUSIC -> Icons.Rounded.MusicNote
    HangoutActivity.TRIP -> Icons.Rounded.Luggage
}

/** A built-in activity's label, or your own activity's name (issue #121). */
@Composable
fun activityLabel(choice: ActivityChoice): String = when (choice) {
    is ActivityChoice.BuiltIn -> activityLabel(choice.activity)
    is ActivityChoice.Custom -> choice.name
}

/** A built-in activity's icon; your own activities share one (issue #121). */
fun activityIcon(choice: ActivityChoice): ImageVector = when (choice) {
    is ActivityChoice.BuiltIn -> activityIcon(choice.activity)
    is ActivityChoice.Custom -> Icons.Rounded.Interests
}

/** The icon for a kind of contact (meetups use [Icons.Rounded.Groups]). */
fun interactionIcon(type: InteractionType): ImageVector = when (type) {
    InteractionType.MEETUP -> Icons.Rounded.Groups
    InteractionType.CALL -> Icons.Rounded.Call
    InteractionType.VIDEO_CALL -> Icons.Rounded.Videocam
    InteractionType.EMAIL -> Icons.Rounded.Email
    InteractionType.MESSAGE -> Icons.AutoMirrored.Rounded.Chat
    InteractionType.WHATSAPP -> Icons.Rounded.Forum
    InteractionType.LONG_CHAT -> Icons.Rounded.QuestionAnswer
    InteractionType.GAME -> Icons.Rounded.SportsEsports
    InteractionType.OTHER -> Icons.Rounded.Interests
}

/** "Pedro", "Pedro and Rui", "Pedro, Rui and Ana" (first names). */
@Composable
fun namesList(friends: List<Friend>): String {
    val names = friends.map { it.displayName.substringBefore(' ') }
    return when (names.size) {
        0 -> ""
        1 -> names.single()
        else -> stringResource(Res.string.tl_and, names.dropLast(1).joinToString(", "), names.last())
    }
}

/** Where the rail runs: the centre of [TimelineEntryRow]'s 40 dp icon, after the row's 16 dp start padding. */
private val RailX = 36.dp

/**
 * The timeline's connector line (issue #130) behind a row that isn't a [TimelineEntryRow] (plans, reminders, birthdays,
 * moments), at the same x as that row's rail, so the whole list reads as one line. The row's leading badge is opaque
 * and hides it; [fromCentre] starts it halfway down (the first row), [toCentre] stops it there (the last).
 */
fun Modifier.timelineRail(color: Color, fromCentre: Boolean = false, toCentre: Boolean = false): Modifier = drawBehind {
    val x = RailX.toPx()
    val top = if (fromCentre) size.height / 2 else 0f
    val bottom = if (toCentre) size.height / 2 else size.height
    if (bottom > top) drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
}

/**
 * One timeline line: an icon on the rail, a title, the date and details, and (for notes) the text in a bubble.
 * [last] hides the rail below the icon.
 */
@Composable
fun TimelineEntryRow(
    entry: TimelineEntry,
    zone: TimeZone,
    modifier: Modifier = Modifier,
    last: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val date = entry.at.toLocalDate(zone).shortLabel()
    val v = describe(entry)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!last) {
                Box(Modifier.padding(top = 40.dp).width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            }
            Surface(shape = CircleShape, color = if (v.strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer) {
                Icon(
                    v.icon,
                    null,
                    Modifier.padding(9.dp).size(20.dp),
                    tint = if (v.strong) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                v.tag?.let {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                listOfNotNull(date, v.detail).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            v.quote?.let {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
            v.attachment?.let { AttachmentPreview(it, Modifier.padding(top = 6.dp)) }
            if (v.media.isNotEmpty()) NoteMediaRow(v.media, Modifier.padding(top = 6.dp))
        }
        trailing?.invoke()
    }
}

/**
 * A timeline line you can change (issue #102): a tap runs [onEdit] (the Log popup in edit mode, the news dialog, or the
 * gift), a long press or ⋮ offers Edit and, when [onDelete] is set, Delete (the caller offers Undo). Used on a person's
 * page and on the full timeline; it knows nothing about either.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditableTimelineEntry(
    entry: TimelineEntry,
    zone: TimeZone,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    last: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    val editLabel = stringResource(Res.string.edit)
    TimelineEntryRow(
        entry,
        zone,
        modifier.combinedClickable(onClickLabel = editLabel, onClick = onEdit, onLongClick = { menu = true }),
        last = last,
        trailing = { EditDeleteMenu(menu, onOpen = { menu = true }, onClose = { menu = false }, onEdit = onEdit, onDelete = onDelete) },
    )
}

private data class EntryLook(
    val icon: ImageVector,
    val title: String,
    val detail: String? = null,
    val quote: String? = null,
    val tag: String? = null,
    val strong: Boolean = false,
    /** The share it was logged from (D6): shown with its thumbnail. */
    val attachment: Attachment? = null,
    /** A note's photos and videos (issue #119). */
    val media: List<NoteMedia> = emptyList(),
)

@Composable
private fun describe(entry: TimelineEntry): EntryLook = when (entry) {
    is TimelineEntry.Meetup -> {
        val h = entry.hangout
        val place = h.place?.label
        val activity = h.activityChoice?.let { activityLabel(it) }
        val title = when {
            activity != null && place != null -> stringResource(Res.string.tl_activity_at, activity, place)
            activity != null -> activity
            place != null -> place
            else -> stringResource(Res.string.tl_together)
        }
        EntryLook(
            icon = h.activityChoice?.let { activityIcon(it) } ?: Icons.Rounded.Groups,
            title = title,
            detail = entry.others.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.tl_with, namesList(it)) },
            quote = h.note.trim().ifEmpty { null },
            strong = true,
            attachment = entry.attachment,
        )
    }
    is TimelineEntry.Contact -> {
        val i = entry.interaction
        val title = when (i.type) {
            InteractionType.CALL -> i.durationSeconds?.takeIf { it >= 60 }?.let { stringResource(Res.string.tl_call_minutes, it / 60) }
                ?: stringResource(Res.string.tl_call)
            InteractionType.VIDEO_CALL -> stringResource(Res.string.tl_video_call)
            InteractionType.EMAIL -> stringResource(Res.string.tl_email)
            InteractionType.MESSAGE -> stringResource(Res.string.tl_message)
            InteractionType.WHATSAPP -> stringResource(Res.string.tl_whatsapp)
            InteractionType.MEETUP -> stringResource(Res.string.tl_together)
            InteractionType.LONG_CHAT -> stringResource(Res.string.tl_long_chat)
            InteractionType.GAME -> stringResource(Res.string.tl_game)
            InteractionType.OTHER -> i.label.trim().ifEmpty { stringResource(Res.string.tl_other) }
        }
        val details = listOfNotNull(
            i.messageCount?.let { pluralStringResource(Res.plurals.tl_messages, it, it) },
            entry.others.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.tl_with, namesList(it)) },
            i.note.trim().ifEmpty { null },
        )
        EntryLook(
            icon = interactionIcon(i.type),
            title = title,
            detail = details.joinToString(" · ").ifEmpty { null },
            tag = when (i.source) {
                InteractionSource.CALL_LOG -> stringResource(Res.string.tl_source_call_log)
                InteractionSource.CHAT_IMPORT -> stringResource(Res.string.tl_source_imported)
                InteractionSource.SHARE -> stringResource(Res.string.tl_source_shared)
                InteractionSource.MANUAL, InteractionSource.NOTIFICATION -> null
            },
            attachment = entry.attachment,
        )
    }
    is TimelineEntry.Journal -> EntryLook(
        Icons.AutoMirrored.Rounded.MenuBook,
        stringResource(Res.string.tl_journal),
        quote = entry.entry.text.ifBlank { null },
        media = entry.entry.media,
    )
    is TimelineEntry.WhatsNew -> EntryLook(Icons.Rounded.AutoAwesome, stringResource(Res.string.tl_whats_new), quote = entry.update.text)
    is TimelineEntry.GiftGiven -> EntryLook(
        Icons.Rounded.CardGiftcard,
        stringResource(Res.string.tl_gift, entry.gift.title),
        detail = entry.gift.occasion?.let { occasionLabel(it) },
    )
    // Shared to a circle (issue #88): the caption as the title, the share itself below.
    is TimelineEntry.Share -> EntryLook(
        Icons.Rounded.Share,
        entry.attachment.caption.trim().ifEmpty { stringResource(Res.string.tl_shared) },
        attachment = entry.attachment,
    )
}

/**
 * ⋮ with Edit and, when [onDelete] is set, Delete (issue #102): for any listed entry that can be changed. [expanded] is
 * the caller's, so a long press on the row can open the same menu.
 */
@Composable
fun EditDeleteMenu(
    expanded: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    contentDescription: String = stringResource(Res.string.tl_entry_options),
) {
    Box {
        IconButton(onClick = onOpen, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.MoreVert, contentDescription, Modifier.size(20.dp))
        }
        DropdownMenu(expanded, onDismissRequest = onClose) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.edit)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = {
                    onClose()
                    onEdit()
                },
            )
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.delete)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = {
                        onClose()
                        onDelete()
                    },
                )
            }
        }
    }
}
