package com.neteinstein.goodcompany.feature.agenda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.FriendId

@Composable
fun AgendaRow(item: AgendaItem, onOpenFriend: (FriendId) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    data class Row(val icon: ImageVector, val tint: Color, val title: String, val subtitle: String?, val friend: FriendId?)
    val row = when (item) {
        is AgendaItem.FriendBirthday -> Row(
            Icons.Rounded.Cake,
            colors.tertiary,
            "${item.friend.name}'s birthday",
            item.turning?.let {
                "Turns $it" +
                    item.friend.likes.takeIf { l -> l.isNotEmpty() }?.joinToString(prefix = " · loves ", limit = 3).orEmpty()
            },
            item.friend.id,
        )
        is AgendaItem.KidBirthday -> Row(
            Icons.Rounded.ChildCare,
            colors.tertiary,
            "${item.kid.name} (${item.parent.name.substringBefore(' ')}'s kid)",
            listOfNotNull(
                item.turning?.let {
                    "Turns $it"
                },
                item.kid.likes.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Likes "),
            ).joinToString(" · "),
            item.parent.id,
        )
        is AgendaItem.FriendEvent -> Row(Icons.Rounded.Favorite, colors.primary, "Ping ${item.friend.name}", item.event.title, item.friend.id)
        is AgendaItem.ReminderDue -> Row(
            Icons.Rounded.Alarm,
            colors.secondary,
            item.reminder.title,
            item.friends.takeIf { it.isNotEmpty() }?.joinToString(prefix = "With ") { it.name.substringBefore(' ') },
            null,
        )
        is AgendaItem.MeetupOverdue -> Row(
            Icons.Rounded.Schedule,
            colors.error,
            "See ${item.friend.name}",
            item.daysSince?.let { "$it days since you met" } ?: "No meetup logged yet",
            item.friend.id,
        )
    }
    ListItem(
        modifier = modifier.then(if (row.friend != null) Modifier.clickable { onOpenFriend(row.friend) } else Modifier),
        leadingContent = {
            Box(Modifier.size(40.dp).background(row.tint.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(row.icon, null, tint = row.tint)
            }
        },
        headlineContent = { Text(row.title) },
        supportingContent = row.subtitle?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
    )
}
