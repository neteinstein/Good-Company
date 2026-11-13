package com.neteinstein.goodcompany.feature.friends.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.core.ui.components.WarmthDot
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.l10n.circleLabel
import com.neteinstein.goodcompany.core.ui.l10n.warmthLabel
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.row_added_in_app
import com.neteinstein.goodcompany.feature.friends.resources.row_catch_up
import com.neteinstein.goodcompany.feature.friends.resources.row_from_contacts
import com.neteinstein.goodcompany.feature.friends.resources.row_last_in_touch
import com.neteinstein.goodcompany.feature.friends.resources.row_likes
import com.neteinstein.goodcompany.feature.friends.resources.row_mark_real
import com.neteinstein.goodcompany.feature.friends.resources.row_unmark_real
import com.neteinstein.goodcompany.feature.friends.resources.row_warmth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

@Composable
fun FriendRow(
    summary: FriendSummary,
    now: Instant,
    today: LocalDate,
    zone: TimeZone,
    onClick: () -> Unit,
    onToggleClose: () -> Unit,
    showWarmth: Boolean = true,
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
        Box {
            FriendAvatar(f, highlighted = f.isCloseFriend, sharedKey = "avatar-${f.id.value}")
            if (f.isCloseFriend && showWarmth) {
                val warmth = stringResource(Res.string.row_warmth, warmthLabel(summary.warmth.level))
                WarmthDot(summary.warmth.level, Modifier.align(Alignment.BottomEnd).semantics { contentDescription = warmth }, size = 12.dp)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = summary.lastContact?.let { stringResource(Res.string.row_last_in_touch, relativePast(it, now, zone)) }
                ?: f.likes.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.row_likes, it.joinToString(limit = 3)) }
                ?: stringResource(if (f.contactId == null) Res.string.row_added_in_app else Res.string.row_from_contacts)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val birthdaySoon = summary.nextBirthday?.takeIf { today.daysUntil(it) in 0..14 }
            val circle = f.circle?.takeIf { f.isCloseFriend }
            if (birthdaySoon != null || summary.meetupOverdue || circle != null) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    circle?.let { InfoPill(circleLabel(it, short = true)) }
                    birthdaySoon?.let { InfoPill(relativeDay(it, today), icon = Icons.Rounded.Cake, emphasized = true) }
                    if (summary.meetupOverdue) InfoPill(stringResource(Res.string.row_catch_up), icon = Icons.Rounded.Schedule)
                }
            }
        }
        IconToggleButton(checked = f.isCloseFriend, onCheckedChange = { onToggleClose() }) {
            Icon(
                if (f.isCloseFriend) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(if (f.isCloseFriend) Res.string.row_unmark_real else Res.string.row_mark_real),
                tint = if (f.isCloseFriend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}
