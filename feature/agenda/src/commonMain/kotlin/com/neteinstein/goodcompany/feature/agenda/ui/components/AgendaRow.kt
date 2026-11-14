package com.neteinstein.goodcompany.feature.agenda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.l10n.anniversaryTitle
import com.neteinstein.goodcompany.core.ui.resources.anniversary_years
import com.neteinstein.goodcompany.core.ui.resources.plan_add_calendar
import com.neteinstein.goodcompany.core.ui.resources.plan_did_you_meet
import com.neteinstein.goodcompany.core.ui.resources.plan_no
import com.neteinstein.goodcompany.core.ui.resources.plan_yes
import com.neteinstein.goodcompany.feature.agenda.resources.Res
import com.neteinstein.goodcompany.feature.agenda.resources.label_big_day
import com.neteinstein.goodcompany.feature.agenda.resources.label_birthday
import com.neteinstein.goodcompany.feature.agenda.resources.label_catch_up
import com.neteinstein.goodcompany.feature.agenda.resources.label_group
import com.neteinstein.goodcompany.feature.agenda.resources.label_kid_birthday
import com.neteinstein.goodcompany.feature.agenda.resources.label_plan
import com.neteinstein.goodcompany.feature.agenda.resources.label_reminder
import com.neteinstein.goodcompany.feature.agenda.resources.row_birthday
import com.neteinstein.goodcompany.feature.agenda.resources.row_days_since
import com.neteinstein.goodcompany.feature.agenda.resources.row_gift_idea
import com.neteinstein.goodcompany.feature.agenda.resources.row_group_days
import com.neteinstein.goodcompany.feature.agenda.resources.row_kid
import com.neteinstein.goodcompany.feature.agenda.resources.row_loves
import com.neteinstein.goodcompany.feature.agenda.resources.row_never
import com.neteinstein.goodcompany.feature.agenda.resources.row_ping
import com.neteinstein.goodcompany.feature.agenda.resources.row_relative
import com.neteinstein.goodcompany.feature.agenda.resources.row_see
import com.neteinstein.goodcompany.feature.agenda.resources.row_send_wishes
import com.neteinstein.goodcompany.feature.agenda.resources.row_turns
import com.neteinstein.goodcompany.feature.agenda.resources.row_we_met
import com.neteinstein.goodcompany.feature.agenda.resources.row_with
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/**
 * A colour-coded agenda card: birthday (sunflower), kid (teal), big day (coral), reminder (violet), catch-up (red),
 * group (secondary). Birthdays today or tomorrow get a "Send wishes" button; overdue groups get "We met".
 */
@Composable
fun AgendaRow(
    item: AgendaItem,
    today: LocalDate,
    onOpenFriend: (FriendId) -> Unit,
    modifier: Modifier = Modifier,
    onOpenWish: (FriendId) -> Unit = {},
    onGroupMet: (String) -> Unit = {},
    onConfirmPlan: (id: String, happened: Boolean) -> Unit = { _, _ -> },
    onAddPlanToCalendar: (AgendaItem.PlanUpcoming) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    data class Spec(val icon: ImageVector, val tint: Color, val label: String, val title: String, val subtitle: String?, val friend: Friend?)
    val spec = when (item) {
        is AgendaItem.FriendBirthday -> Spec(
            Icons.Rounded.Cake,
            Color(0xFFF2A100),
            stringResource(Res.string.label_birthday),
            stringResource(Res.string.row_birthday, item.friend.name),
            listOfNotNull(
                item.turning?.let { stringResource(Res.string.row_turns, it) },
                item.friend.likes.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.row_loves, it.joinToString(limit = 3)) },
            ).joinToString(" · "),
            item.friend,
        )
        is AgendaItem.KidBirthday -> Spec(
            Icons.Rounded.ChildCare,
            Color(0xFF00A88F),
            stringResource(Res.string.label_kid_birthday),
            stringResource(Res.string.row_kid, item.kid.name, item.parent.name.substringBefore(' ')),
            listOfNotNull(
                item.turning?.let { stringResource(Res.string.row_turns, it) },
                (item.parent.gifts.filter { it.isIdea && it.forKidId == item.kid.id }.map { it.title } + item.kid.likes)
                    .takeIf { it.isNotEmpty() }
                    ?.let { stringResource(Res.string.row_gift_idea, it.joinToString(limit = 2)) },
            ).joinToString(" · "),
            item.parent,
        )
        is AgendaItem.RelativeBirthday -> Spec(
            Icons.Rounded.Cake,
            Color(0xFF00A88F),
            stringResource(Res.string.label_birthday),
            stringResource(Res.string.row_relative, item.relative.name, item.friend.name.substringBefore(' ')),
            listOfNotNull(
                item.turning?.let { stringResource(Res.string.row_turns, it) },
                item.relative.likes.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.row_loves, it.joinToString(limit = 3)) },
            ).joinToString(" · "),
            item.friend,
        )
        is AgendaItem.Anniversary -> Spec(
            Icons.Rounded.Favorite,
            colors.primary,
            stringResource(Res.string.label_big_day),
            anniversaryTitle(item.first.name.substringBefore(' '), item.second.name.substringBefore(' ')),
            item.years?.takeIf { it > 0 }?.let { pluralStringResource(CoreRes.plurals.anniversary_years, it, it) }.orEmpty(),
            item.first,
        )
        is AgendaItem.FriendEvent -> Spec(
            Icons.Rounded.Favorite,
            colors.primary,
            stringResource(Res.string.label_big_day),
            stringResource(Res.string.row_ping, item.friend.name),
            item.event.title,
            item.friend,
        )
        is AgendaItem.ReminderDue -> Spec(
            Icons.Rounded.Alarm,
            Color(0xFF7E57C2),
            stringResource(Res.string.label_reminder),
            item.reminder.title,
            item.friends.takeIf {
                it.isNotEmpty()
            }?.let { fs -> stringResource(Res.string.row_with, fs.joinToString { it.name.substringBefore(' ') }) },
            item.friends.firstOrNull(),
        )
        is AgendaItem.MeetupOverdue -> Spec(
            Icons.Rounded.Schedule,
            colors.error,
            stringResource(Res.string.label_catch_up),
            stringResource(Res.string.row_see, item.friend.name),
            item.daysSince?.let { pluralStringResource(Res.plurals.row_days_since, it, it) } ?: stringResource(Res.string.row_never),
            item.friend,
        )
        is AgendaItem.GroupOverdue -> Spec(
            Icons.Rounded.Groups,
            colors.secondary,
            stringResource(Res.string.label_group),
            "${item.group.emoji} ${item.group.name}",
            item.members.joinToString { it.name.substringBefore(' ') }.let { names ->
                item.daysSince?.let { pluralStringResource(Res.plurals.row_group_days, it, names, it) } ?: names
            },
            null,
        )
        is AgendaItem.PlanUpcoming -> Spec(
            Icons.Rounded.EventAvailable,
            PLAN_GREEN,
            stringResource(Res.string.label_plan),
            item.plan.title,
            listOfNotNull(
                item.friends.takeIf {
                    it.isNotEmpty()
                }?.let { fs -> stringResource(Res.string.row_with, fs.joinToString { it.name.substringBefore(' ') }) },
                item.plan.place,
            ).joinToString(" · "),
            item.friends.singleOrNull(),
        )
        is AgendaItem.PlanToConfirm -> Spec(
            Icons.Rounded.EventAvailable,
            PLAN_GREEN,
            stringResource(Res.string.label_plan),
            item.plan.title,
            listOfNotNull(
                item.plan.date.shortLabel(),
                item.friends.takeIf {
                    it.isNotEmpty()
                }?.let { fs -> stringResource(Res.string.row_with, fs.joinToString { it.name.substringBefore(' ') }) },
            ).joinToString(" · "),
            item.friends.singleOrNull(),
        )
    }
    Surface(
        onClick = { spec.friend?.let { onOpenFriend(it.id) } },
        enabled = spec.friend != null,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(spec.tint))
            Box(
                Modifier.padding(start = 12.dp).size(40.dp).background(spec.tint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(spec.icon, null, tint = spec.tint, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(spec.label, style = MaterialTheme.typography.labelSmall, color = spec.tint)
                Text(spec.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                spec.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item is AgendaItem.FriendBirthday && today.daysUntil(item.date) in 0..1) {
                    FilledTonalButton(
                        onClick = { onOpenWish(item.friend.id) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.padding(top = 6.dp).height(34.dp),
                    ) {
                        Icon(Icons.Rounded.Cake, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.row_send_wishes), style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (item is AgendaItem.PlanToConfirm) {
                    Text(
                        stringResource(CoreRes.string.plan_did_you_meet),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { onConfirmPlan(item.plan.id, true) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                        ) { Text(stringResource(CoreRes.string.plan_yes), style = MaterialTheme.typography.labelLarge) }
                        TextButton(onClick = { onConfirmPlan(item.plan.id, false) }, modifier = Modifier.height(34.dp)) {
                            Text(stringResource(CoreRes.string.plan_no), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (item is AgendaItem.PlanUpcoming) {
                    TextButton(
                        onClick = { onAddPlanToCalendar(item) },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(CoreRes.string.plan_add_calendar), style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (item is AgendaItem.GroupOverdue && item.group.memberIds.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { onGroupMet(item.group.id) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.padding(top = 6.dp).height(34.dp),
                    ) { Text(stringResource(Res.string.row_we_met), style = MaterialTheme.typography.labelLarge) }
                }
            }
            when {
                spec.friend != null -> FriendAvatar(spec.friend, size = 36.dp)
                item is AgendaItem.GroupOverdue -> StackedAvatars(item.members, size = 26.dp, max = 3)
                item is AgendaItem.PlanUpcoming && item.friends.size > 1 ->
                    StackedAvatars(item.friends, size = 26.dp, max = 3)
                item is AgendaItem.PlanToConfirm && item.friends.size > 1 ->
                    StackedAvatars(item.friends, size = 26.dp, max = 3)
            }
            Spacer(Modifier.width(12.dp))
        }
    }
}

private val PLAN_GREEN = Color(0xFF2E7D32)
