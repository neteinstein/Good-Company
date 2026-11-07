package com.neteinstein.goodcompany.feature.agenda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

/** A colour-coded agenda card: birthday (sunflower), kid (teal), big day (coral), reminder (violet), catch-up (red). */
@Composable
fun AgendaRow(item: AgendaItem, onOpenFriend: (FriendId) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    data class Spec(val icon: ImageVector, val tint: Color, val label: String, val title: String, val subtitle: String?, val friend: Friend?)
    val spec = when (item) {
        is AgendaItem.FriendBirthday -> Spec(
            Icons.Rounded.Cake,
            Color(0xFFF2A100),
            "BIRTHDAY",
            "${item.friend.name}'s birthday",
            listOfNotNull(
                item.turning?.let {
                    "Turns $it"
                },
                item.friend.likes.takeIf { it.isNotEmpty() }?.joinToString(prefix = "loves ", limit = 3),
            ).joinToString(" · "),
            item.friend,
        )
        is AgendaItem.KidBirthday -> Spec(
            Icons.Rounded.ChildCare,
            Color(0xFF00A88F),
            "KID'S BIRTHDAY",
            "${item.kid.name} (${item.parent.name.substringBefore(' ')}'s)",
            listOfNotNull(
                item.turning?.let {
                    "Turns $it"
                },
                item.kid.likes.takeIf { it.isNotEmpty() }?.joinToString(prefix = "gift idea: ", limit = 2),
            ).joinToString(" · "),
            item.parent,
        )
        is AgendaItem.FriendEvent -> Spec(
            Icons.Rounded.Favorite,
            colors.primary,
            "BIG DAY",
            "Ping ${item.friend.name}",
            item.event.title,
            item.friend,
        )
        is AgendaItem.ReminderDue -> Spec(
            Icons.Rounded.Alarm,
            Color(0xFF7E57C2),
            "REMINDER",
            item.reminder.title,
            item.friends.takeIf { it.isNotEmpty() }?.joinToString(prefix = "With ") { it.name.substringBefore(' ') },
            item.friends.firstOrNull(),
        )
        is AgendaItem.MeetupOverdue -> Spec(
            Icons.Rounded.Schedule,
            colors.error,
            "CATCH UP",
            "See ${item.friend.name}",
            item.daysSince?.let { "$it days since you met" } ?: "No meetup logged yet",
            item.friend,
        )
    }
    Surface(
        onClick = { spec.friend?.let { onOpenFriend(it.id) } },
        enabled = spec.friend != null,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
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
            }
            spec.friend?.let { FriendAvatar(it.name, it.initials, size = 36.dp) }
            Spacer(Modifier.width(12.dp))
        }
    }
}
