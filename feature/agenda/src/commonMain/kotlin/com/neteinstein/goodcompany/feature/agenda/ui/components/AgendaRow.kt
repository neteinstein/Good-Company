package com.neteinstein.goodcompany.feature.agenda.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanAnswer
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.LogIcon
import com.neteinstein.goodcompany.core.ui.components.PlanHappenedButton
import com.neteinstein.goodcompany.core.ui.components.PlanUpcomingMenu
import com.neteinstein.goodcompany.core.ui.components.ReminderActionsRow
import com.neteinstein.goodcompany.core.ui.components.ReminderCallbacks
import com.neteinstein.goodcompany.core.ui.components.ReminderWhen
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.components.repeatLabel
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.l10n.anniversaryTitle
import com.neteinstein.goodcompany.core.ui.platform.clockLabel
import com.neteinstein.goodcompany.core.ui.resources.anniversary_years
import com.neteinstein.goodcompany.core.ui.resources.catchup_snooze_week
import com.neteinstein.goodcompany.core.ui.resources.catchup_snooze_weekend
import com.neteinstein.goodcompany.core.ui.resources.plan_circle_people
import com.neteinstein.goodcompany.core.ui.resources.plan_how_did_it_go
import com.neteinstein.goodcompany.core.ui.resources.plan_next_after
import com.neteinstein.goodcompany.core.ui.resources.plan_people_count
import com.neteinstein.goodcompany.feature.agenda.resources.Res
import com.neteinstein.goodcompany.feature.agenda.resources.label_big_day
import com.neteinstein.goodcompany.feature.agenda.resources.label_birthday
import com.neteinstein.goodcompany.feature.agenda.resources.label_catch_up
import com.neteinstein.goodcompany.feature.agenda.resources.label_group
import com.neteinstein.goodcompany.feature.agenda.resources.label_kid_birthday
import com.neteinstein.goodcompany.feature.agenda.resources.label_plan
import com.neteinstein.goodcompany.feature.agenda.resources.label_reminder
import com.neteinstein.goodcompany.feature.agenda.resources.row_birthday
import com.neteinstein.goodcompany.feature.agenda.resources.row_circle_more
import com.neteinstein.goodcompany.feature.agenda.resources.row_circle_reminders_off
import com.neteinstein.goodcompany.feature.agenda.resources.row_days_since
import com.neteinstein.goodcompany.feature.agenda.resources.row_gift_idea
import com.neteinstein.goodcompany.feature.agenda.resources.row_group_last_met
import com.neteinstein.goodcompany.feature.agenda.resources.row_group_no_meetup
import com.neteinstein.goodcompany.feature.agenda.resources.row_group_rhythm
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
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
    /** Snooze in the ⋮ of a circle's row (issue #150); no ⋮ when null. */
    onSnoozeCircle: ((String, SnoozeOption) -> Unit)? = null,
    /** "Turn off reminders for this circle" in the same ⋮ (issue #150). */
    onCircleRemindersOff: ((String) -> Unit)? = null,
    /** "How did it go?" on a past plan (issue #100): the answer and whether it is for the whole series. */
    onAnswerPlan: (AgendaItem.PlanToConfirm, PlanAnswer, wholeSeries: Boolean) -> Unit = { _, _, _ -> },
    onAddPlanToCalendar: (AgendaItem.PlanUpcoming) -> Unit = {},
    onSkipPlan: (AgendaItem.PlanUpcoming) -> Unit = {},
    /** Edit in the ⋮ of an upcoming plan (issue #136): the plan editor. */
    onEditPlan: (Plan) -> Unit = {},
    /** Cancel plan or Delete in the ⋮ of an upcoming plan (issue #136), and whether it is for the whole series. */
    onAnswerUpcomingPlan: (AgendaItem.PlanUpcoming, PlanAnswer, wholeSeries: Boolean) -> Unit = { _, _, _ -> },
    /** Done · Snooze · Edit · Delete under a reminder (issue #99); none when null. */
    reminders: ReminderCallbacks? = null,
    now: Instant = Instant.DISTANT_PAST,
    zone: TimeZone = TimeZone.UTC,
) {
    data class Spec(val icon: ImageVector, val tint: Color, val label: String, val title: String, val subtitle: String?, val friend: Friend?)
    val tint = item.tint()
    val spec = when (item) {
        is AgendaItem.FriendBirthday -> Spec(
            Icons.Rounded.Cake,
            tint,
            stringResource(Res.string.label_birthday),
            stringResource(Res.string.row_birthday, item.friend.displayName),
            listOfNotNull(
                item.turning?.let { stringResource(Res.string.row_turns, it) },
                item.friend.likes.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.row_loves, it.joinToString(limit = 3)) },
            ).joinToString(" · "),
            item.friend,
        )
        is AgendaItem.KidBirthday -> Spec(
            Icons.Rounded.ChildCare,
            tint,
            stringResource(Res.string.label_kid_birthday),
            stringResource(Res.string.row_kid, item.kid.displayName, item.parent.displayName.substringBefore(' ')),
            listOfNotNull(
                item.turning?.let { stringResource(Res.string.row_turns, it) },
                (item.kid.gifts.filter { it.isIdea }.map { it.title } + item.kid.likes)
                    .takeIf { it.isNotEmpty() }
                    ?.let { stringResource(Res.string.row_gift_idea, it.joinToString(limit = 2)) },
            ).joinToString(" · "),
            item.kid,
        )
        is AgendaItem.RelativeBirthday -> Spec(
            Icons.Rounded.Cake,
            tint,
            stringResource(Res.string.label_birthday),
            stringResource(Res.string.row_relative, item.relative.name, item.friend.displayName.substringBefore(' ')),
            listOfNotNull(
                item.turning?.let { stringResource(Res.string.row_turns, it) },
                item.relative.likes.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.row_loves, it.joinToString(limit = 3)) },
            ).joinToString(" · "),
            item.friend,
        )
        is AgendaItem.Anniversary -> Spec(
            Icons.Rounded.Favorite,
            tint,
            stringResource(Res.string.label_big_day),
            anniversaryTitle(item.first.displayName.substringBefore(' '), item.second.displayName.substringBefore(' ')),
            item.years?.takeIf { it > 0 }?.let { pluralStringResource(CoreRes.plurals.anniversary_years, it, it) }.orEmpty(),
            item.first,
        )
        is AgendaItem.FriendEvent -> Spec(
            Icons.Rounded.Favorite,
            tint,
            stringResource(Res.string.label_big_day),
            stringResource(Res.string.row_ping, item.friend.displayName),
            item.event.title,
            item.friend,
        )
        is AgendaItem.ReminderDue -> Spec(
            Icons.Rounded.Alarm,
            tint,
            stringResource(Res.string.label_reminder),
            item.reminder.title,
            item.friends.takeIf {
                it.isNotEmpty()
            }?.let { fs -> stringResource(Res.string.row_with, fs.joinToString { it.displayName.substringBefore(' ') }) },
            item.friends.firstOrNull(),
        )
        is AgendaItem.MeetupOverdue -> Spec(
            Icons.Rounded.Schedule,
            tint,
            stringResource(Res.string.label_catch_up),
            stringResource(Res.string.row_see, item.friend.displayName),
            item.daysSince?.let { pluralStringResource(Res.plurals.row_days_since, it, it) } ?: stringResource(Res.string.row_never),
            item.friend,
        )
        is AgendaItem.GroupOverdue -> Spec(
            Icons.Rounded.Groups,
            tint,
            stringResource(Res.string.label_group),
            "${item.group.emoji} ${item.group.name}",
            // Issue #150: why it's here ("Every 15 days · last met 40 days ago"), then who is in it.
            listOfNotNull(
                listOfNotNull(
                    item.group.meetEveryDays?.let { pluralStringResource(Res.plurals.row_group_rhythm, it, it) },
                    item.daysSince?.let { pluralStringResource(Res.plurals.row_group_last_met, it, it) }
                        ?: stringResource(Res.string.row_group_no_meetup),
                ).joinToString(" · "),
                item.members.joinToString { it.displayName.substringBefore(' ') }.takeIf { it.isNotBlank() },
            ).joinToString("\n"),
            null,
        )
        is AgendaItem.PlanUpcoming -> Spec(
            Icons.Rounded.EventAvailable,
            tint,
            stringResource(Res.string.label_plan),
            item.plan.title,
            listOfNotNull(
                // Issue #167: the time first, when the plan has one.
                item.plan.time?.clockLabel(),
                item.circle?.let { c ->
                    stringResource(
                        CoreRes.string.plan_circle_people,
                        "${c.emoji} ${c.name}",
                        pluralStringResource(CoreRes.plurals.plan_people_count, item.friends.size, item.friends.size),
                    )
                } ?: item.friends.takeIf {
                    it.isNotEmpty()
                }?.let { fs -> stringResource(Res.string.row_with, fs.joinToString { it.displayName.substringBefore(' ') }) },
                item.plan.repeat?.let { r ->
                    listOfNotNull(
                        repeatLabel(r),
                        item.nextAfter?.let { stringResource(CoreRes.string.plan_next_after, it.shortLabel()) },
                    ).joinToString(" · ")
                },
                item.plan.place,
            ).joinToString(" · "),
            item.friends.singleOrNull(),
        )
        is AgendaItem.PlanToConfirm -> Spec(
            Icons.Rounded.EventAvailable,
            tint,
            stringResource(Res.string.label_plan),
            item.plan.title,
            listOfNotNull(
                item.occurrence.shortLabel(),
                item.plan.time?.clockLabel(),
                item.circle?.let { "${it.emoji} ${it.name}" },
                item.friends.takeIf { item.circle == null }?.takeIf {
                    it.isNotEmpty()
                }?.let { fs -> stringResource(Res.string.row_with, fs.joinToString { it.displayName.substringBefore(' ') }) },
            ).joinToString(" · "),
            item.friends.singleOrNull(),
        )
    }
    // A reminder goes where ReminderTarget says (issue #135), never just to its first person.
    val openReminder = (item as? AgendaItem.ReminderDue)?.let { r -> reminders?.onOpen?.let { open -> { open(r.reminder) } } }
    Surface(
        onClick = { openReminder?.invoke() ?: spec.friend?.let { onOpenFriend(it.id) } },
        enabled = openReminder != null || (item !is AgendaItem.ReminderDue && spec.friend != null),
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
                        maxLines = 3,
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
                        stringResource(CoreRes.string.plan_how_did_it_go),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    // Issue #136: "It happened" in one tap, the other answers behind ▾.
                    PlanHappenedButton(item.plan, onAnswer = { answer, whole -> onAnswerPlan(item, answer, whole) })
                }
                if (item is AgendaItem.ReminderDue && reminders != null) {
                    ReminderWhen(item.reminder, today, zone)
                    ReminderActionsRow(item.reminder, reminders, today, zone, now, Modifier.padding(top = 2.dp))
                }
                if (item is AgendaItem.GroupOverdue && item.group.memberIds.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { onGroupMet(item.group.id) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.padding(top = 6.dp).height(34.dp),
                    ) {
                        Icon(LogIcon, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.row_we_met), style = MaterialTheme.typography.labelLarge)
                    }
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
            if (item is AgendaItem.PlanUpcoming) {
                // Issue #136: Edit · Add to calendar · Skip this one · Cancel plan · Delete.
                PlanUpcomingMenu(
                    item.plan,
                    onEdit = { onEditPlan(item.plan) },
                    onAddToCalendar = { onAddPlanToCalendar(item) },
                    onSkip = { onSkipPlan(item) },
                    onAnswer = { answer, whole -> onAnswerUpcomingPlan(item, answer, whole) },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (item is AgendaItem.GroupOverdue && onSnoozeCircle != null && onCircleRemindersOff != null) {
                CircleRowMenu(
                    onSnooze = { onSnoozeCircle(item.group.id, it) },
                    onRemindersOff = { onCircleRemindersOff(item.group.id) },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** The ⋮ of a circle's row (issue #150): the same snoozes as people, and "Turn off reminders for this circle". */
@Composable
private fun CircleRowMenu(onSnooze: (SnoozeOption) -> Unit, onRemindersOff: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        // The timeline's ⋮ size (issue #130), like a plan's.
        IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.MoreVert, stringResource(Res.string.row_circle_more), Modifier.size(20.dp))
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(CoreRes.string.catchup_snooze_weekend)) },
                leadingIcon = { Icon(Icons.Rounded.Weekend, null) },
                onClick = {
                    menu = false
                    onSnooze(SnoozeOption.WEEKEND)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(CoreRes.string.catchup_snooze_week)) },
                leadingIcon = { Icon(Icons.Rounded.Snooze, null) },
                onClick = {
                    menu = false
                    onSnooze(SnoozeOption.ONE_WEEK)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.row_circle_reminders_off)) },
                leadingIcon = { Icon(Icons.Rounded.NotificationsOff, null) },
                onClick = {
                    menu = false
                    onRemindersOff()
                },
            )
        }
    }
}

private val PLAN_GREEN = Color(0xFF2E7D32)

/** The colour of each kind of entry (issue #138: the calendar's markers use the same ones as the rows). */
@Composable
internal fun AgendaItem.tint(): Color {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        is AgendaItem.FriendBirthday -> BIRTHDAY_SUNFLOWER
        is AgendaItem.KidBirthday, is AgendaItem.RelativeBirthday -> KID_TEAL
        is AgendaItem.Anniversary, is AgendaItem.FriendEvent -> colors.primary
        is AgendaItem.ReminderDue -> REMINDER_VIOLET
        is AgendaItem.MeetupOverdue -> colors.error
        is AgendaItem.GroupOverdue -> colors.secondary
        is AgendaItem.PlanUpcoming, is AgendaItem.PlanToConfirm -> PLAN_GREEN
    }
}

private val BIRTHDAY_SUNFLOWER = Color(0xFFF2A100)
private val KID_TEAL = Color(0xFF00A88F)
private val REMINDER_VIOLET = Color(0xFF7E57C2)
