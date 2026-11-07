package com.neteinstein.goodcompany.feature.friends.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.ProgressRing
import com.neteinstein.goodcompany.core.ui.components.avatarColors
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import kotlinx.datetime.LocalDate

/** Gradient card for the "Coming up" carousel. */
@Composable
fun UpcomingCard(item: AgendaItem, today: LocalDate, onOpenFriend: (FriendId) -> Unit, modifier: Modifier = Modifier) {
    data class Spec(val friend: Friend?, val icon: ImageVector, val title: String, val subtitle: String)
    val spec = when (item) {
        is AgendaItem.FriendBirthday -> Spec(
            item.friend,
            Icons.Rounded.Cake,
            "${item.friend.name.substringBefore(' ')}'s birthday",
            item.turning?.let { "Turns $it" } ?: "Say hi!",
        )
        is AgendaItem.KidBirthday -> Spec(
            item.parent,
            Icons.Rounded.ChildCare,
            "${item.kid.name}'s birthday",
            listOfNotNull(
                item.turning?.let {
                    "Turns $it"
                },
                item.kid.likes.firstOrNull()?.let { "loves $it" },
            ).joinToString(" · ").ifBlank { item.parent.name },
        )
        is AgendaItem.FriendEvent -> Spec(item.friend, Icons.Rounded.Favorite, item.event.title, "Ping ${item.friend.name.substringBefore(' ')}")
        is AgendaItem.ReminderDue -> Spec(
            item.friends.firstOrNull(),
            Icons.Rounded.Alarm,
            item.reminder.title,
            item.friends.joinToString {
                it.name.substringBefore(' ')
            }.ifBlank { "Reminder" },
        )
        is AgendaItem.MeetupOverdue -> Spec(item.friend, Icons.Rounded.Schedule, "See ${item.friend.name}", "Overdue")
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

/** "Time to catch up" row: a full red ring means "you're past your goal". */
@Composable
fun CatchUpCard(status: Relationships.MeetupStatus, onOpen: () -> Unit, onMet: () -> Unit, modifier: Modifier = Modifier) {
    val f = status.friend
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(progress = 1f, size = 56.dp, stroke = 4.dp, color = MaterialTheme.colorScheme.error) {
                FriendAvatar(f.name, f.initials, size = 44.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    status.daysSinceLastSeen?.let { "$it days since you met · goal every ${f.meetEveryDays}" } ?: "No meetup logged yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            FilledTonalButton(onClick = onMet) { Text("We met") }
        }
    }
}

/** Avatar + first name for the "Your people" strip. */
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
