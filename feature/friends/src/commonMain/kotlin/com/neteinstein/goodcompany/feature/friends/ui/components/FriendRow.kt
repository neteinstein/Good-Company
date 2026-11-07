package com.neteinstein.goodcompany.feature.friends.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.relativePast
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlin.time.Instant

@Composable
fun FriendRow(
    summary: FriendSummary,
    now: Instant,
    today: LocalDate,
    zone: TimeZone,
    onClick: () -> Unit,
    onToggleClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = summary.friend
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(f.name, f.initials, highlighted = f.isCloseFriend, sharedKey = "avatar-${f.id.value}")
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = summary.lastContact?.let { "Last in touch ${relativePast(it, now, zone)}" }
                ?: f.likes.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Likes ", limit = 3)
                ?: if (f.contactId == null) "Added in app" else "From contacts"
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val birthdaySoon = summary.nextBirthday?.takeIf { today.daysUntil(it) in 0..14 }
            if (birthdaySoon != null || summary.meetupOverdue) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    birthdaySoon?.let { InfoPill(relativeDay(it, today), icon = Icons.Rounded.Cake, emphasized = true) }
                    if (summary.meetupOverdue) InfoPill("Catch up", icon = Icons.Rounded.Schedule)
                }
            }
        }
        IconToggleButton(checked = f.isCloseFriend, onCheckedChange = { onToggleClose() }) {
            Icon(
                if (f.isCloseFriend) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (f.isCloseFriend) "Unmark close friend" else "Mark as close friend",
                tint = if (f.isCloseFriend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}
