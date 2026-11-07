package com.neteinstein.goodcompany.feature.friends.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.ProgressRing
import com.neteinstein.goodcompany.core.ui.components.avatarColors
import com.neteinstein.goodcompany.core.ui.components.warmthColor
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.l10n.everyNDays
import com.neteinstein.goodcompany.core.ui.l10n.warmthLabel
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.action_more
import com.neteinstein.goodcompany.feature.friends.resources.card_birthday
import com.neteinstein.goodcompany.feature.friends.resources.card_loves
import com.neteinstein.goodcompany.feature.friends.resources.card_overdue
import com.neteinstein.goodcompany.feature.friends.resources.card_ping
import com.neteinstein.goodcompany.feature.friends.resources.card_reminder
import com.neteinstein.goodcompany.feature.friends.resources.card_say_hi
import com.neteinstein.goodcompany.feature.friends.resources.card_see
import com.neteinstein.goodcompany.feature.friends.resources.card_turns
import com.neteinstein.goodcompany.feature.friends.resources.catch_up_called
import com.neteinstein.goodcompany.feature.friends.resources.catch_up_days
import com.neteinstein.goodcompany.feature.friends.resources.catch_up_met
import com.neteinstein.goodcompany.feature.friends.resources.catch_up_never
import com.neteinstein.goodcompany.feature.friends.resources.drifting_message
import com.neteinstein.goodcompany.feature.friends.resources.row_warmth
import com.neteinstein.goodcompany.feature.friends.resources.snooze_week
import com.neteinstein.goodcompany.feature.friends.resources.snooze_weekend
import com.neteinstein.goodcompany.core.ui.resources.plan_did_you_meet
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** Gradient card for the "Coming up" carousel. */
@Composable
fun UpcomingCard(item: AgendaItem, today: LocalDate, onOpenFriend: (FriendId) -> Unit, modifier: Modifier = Modifier) {
    data class Spec(val friend: Friend?, val icon: ImageVector, val title: String, val subtitle: String)
    val spec = when (item) {
        is AgendaItem.FriendBirthday -> Spec(
            item.friend,
            Icons.Rounded.Cake,
            stringResource(Res.string.card_birthday, item.friend.name.substringBefore(' ')),
            item.turning?.let { stringResource(Res.string.card_turns, it) } ?: stringResource(Res.string.card_say_hi),
        )
        is AgendaItem.KidBirthday -> Spec(
            item.parent,
            Icons.Rounded.ChildCare,
            stringResource(Res.string.card_birthday, item.kid.name),
            listOfNotNull(
                item.turning?.let { stringResource(Res.string.card_turns, it) },
                item.kid.likes.firstOrNull()?.let { stringResource(Res.string.card_loves, it) },
            ).joinToString(" · ").ifBlank { item.parent.name },
        )
        is AgendaItem.FriendEvent -> Spec(
            item.friend,
            Icons.Rounded.Favorite,
            item.event.title,
            stringResource(Res.string.card_ping, item.friend.name.substringBefore(' ')),
        )
        is AgendaItem.ReminderDue -> Spec(
            item.friends.firstOrNull(),
            Icons.Rounded.Alarm,
            item.reminder.title,
            item.friends.joinToString { it.name.substringBefore(' ') }.ifBlank { stringResource(Res.string.card_reminder) },
        )
        is AgendaItem.MeetupOverdue -> Spec(
            item.friend,
            Icons.Rounded.Schedule,
            stringResource(Res.string.card_see, item.friend.name),
            stringResource(Res.string.card_overdue),
        )
        is AgendaItem.GroupOverdue -> Spec(
            item.members.firstOrNull(),
            Icons.Rounded.Groups,
            "${item.group.emoji} ${item.group.name}",
            item.members.joinToString { it.name.substringBefore(' ') },
        )
        is AgendaItem.PlanUpcoming -> Spec(
            item.friends.firstOrNull(),
            Icons.Rounded.EventAvailable,
            item.plan.title,
            item.friends.joinToString { it.name.substringBefore(' ') },
        )
        is AgendaItem.PlanToConfirm -> Spec(
            item.friends.firstOrNull(),
            Icons.Rounded.EventAvailable,
            item.plan.title,
            stringResource(CoreRes.string.plan_did_you_meet),
        )
    }
    val (start, end) = spec.friend?.let { avatarColors(it.name) } ?: (MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.tertiary)
    Box(
        modifier
            .width(196.dp)
            .height(148.dp)
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(start, end)))
            .clickable(enabled = spec.friend != null) { spec.friend?.let { onOpenFriend(it.id) } }
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).background(Color.White.copy(alpha = 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(spec.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.92f)) {
                    Text(
                        relativeDay(item.date, today),
                        style = MaterialTheme.typography.labelMedium,
                        color = end,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(spec.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                spec.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Time to catch up" row: a full red ring means "you're past your goal". The menu offers "called" and snoozes. */
@Composable
fun CatchUpCard(
    status: Relationships.MeetupStatus,
    onOpen: () -> Unit,
    onMet: () -> Unit,
    onCalled: () -> Unit,
    onSnooze: (SnoozeOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = status.friend
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress = 1f, size = 56.dp, stroke = 4.dp, color = MaterialTheme.colorScheme.error) {
                FriendAvatar(f.name, f.initials, size = 44.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    status.daysSinceLastSeen?.let { pluralStringResource(Res.plurals.catch_up_days, it, it, everyNDays(status.cadenceDays)) }
                        ?: stringResource(Res.string.catch_up_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                // Under the text, not beside it: labels like "Estivemos juntos" are long in some languages.
                FilledTonalButton(
                    onClick = onMet,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.padding(top = 6.dp).height(34.dp),
                ) { Text(stringResource(Res.string.catch_up_met), style = MaterialTheme.typography.labelLarge, maxLines = 1) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.action_more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.catch_up_called)) },
                        leadingIcon = { Icon(Icons.Rounded.Call, null) },
                        onClick = {
                            menu = false
                            onCalled()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.snooze_weekend)) },
                        leadingIcon = { Icon(Icons.Rounded.Weekend, null) },
                        onClick = {
                            menu = false
                            onSnooze(SnoozeOption.WEEKEND)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.snooze_week)) },
                        leadingIcon = { Icon(Icons.Rounded.Snooze, null) },
                        onClick = {
                            menu = false
                            onSnooze(SnoozeOption.ONE_WEEK)
                        },
                    )
                }
            }
        }
    }
}

/** A real friend whose warmth is fading: nudge to reach out, or snooze. */
@Composable
fun DriftingCard(summary: FriendSummary, onOpen: () -> Unit, onSnooze: () -> Unit, modifier: Modifier = Modifier) {
    val f = summary.friend
    val color = warmthColor(summary.warmth.level)
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress = summary.warmth.value / 100f, size = 56.dp, stroke = 4.dp, color = color) {
                FriendAvatar(f.name, f.initials, size = 44.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(Res.string.row_warmth, warmthLabel(summary.warmth.level)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSnooze) { Icon(Icons.Rounded.Snooze, stringResource(Res.string.snooze_week)) }
            TextButton(onClick = onOpen) { Text(stringResource(Res.string.drifting_message)) }
        }
    }
}

/** Avatar + first name for the "Real friends" strip. */
@Composable
fun PersonChip(friend: Friend, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.width(76.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FriendAvatar(friend.name, friend.initials, size = 60.dp, highlighted = true)
        Text(friend.name.substringBefore(' '), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
