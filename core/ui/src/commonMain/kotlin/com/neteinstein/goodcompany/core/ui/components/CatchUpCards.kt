package com.neteinstein.goodcompany.core.ui.components

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
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.StoryBubble
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.l10n.everyNDays
import com.neteinstein.goodcompany.core.ui.l10n.warmthLabel
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.catchup_called
import com.neteinstein.goodcompany.core.ui.resources.catchup_days
import com.neteinstein.goodcompany.core.ui.resources.catchup_message
import com.neteinstein.goodcompany.core.ui.resources.catchup_met
import com.neteinstein.goodcompany.core.ui.resources.catchup_more
import com.neteinstein.goodcompany.core.ui.resources.catchup_never
import com.neteinstein.goodcompany.core.ui.resources.catchup_snooze_week
import com.neteinstein.goodcompany.core.ui.resources.catchup_snooze_weekend
import com.neteinstein.goodcompany.core.ui.resources.catchup_warmth
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// Catch-up cards shared by Home (People) and Today (issue #65): moved here from :feature:friends.

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
                FriendAvatar(f, size = 44.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(f.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    status.daysSinceLastSeen?.let { pluralStringResource(Res.plurals.catchup_days, it, it, everyNDays(status.cadenceDays)) }
                        ?: stringResource(Res.string.catchup_never),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                // Under the text, not beside it: labels like "Estivemos juntos" are long in some languages.
                FilledTonalButton(
                    onClick = onMet,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.padding(top = 6.dp).height(34.dp),
                ) { Text(stringResource(Res.string.catchup_met), style = MaterialTheme.typography.labelLarge, maxLines = 1) }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.catchup_more)) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.catchup_called)) },
                        leadingIcon = { Icon(Icons.Rounded.Call, null) },
                        onClick = {
                            menu = false
                            onCalled()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.catchup_snooze_weekend)) },
                        leadingIcon = { Icon(Icons.Rounded.Weekend, null) },
                        onClick = {
                            menu = false
                            onSnooze(SnoozeOption.WEEKEND)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.catchup_snooze_week)) },
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
                FriendAvatar(f, size = 44.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(f.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(Res.string.catchup_warmth, warmthLabel(summary.warmth.level)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSnooze) { Icon(Icons.Rounded.Snooze, stringResource(Res.string.catchup_snooze_week)) }
            TextButton(onClick = onOpen) { Text(stringResource(Res.string.catchup_message)) }
        }
    }
}

/** "Before you meet": an Instagram-style story bubble with a gradient ring; tap for their catch-up story. */
@Composable
fun StoryBubbleChip(bubble: StoryBubble, today: LocalDate, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val friend = bubble.friend
    val ring = if (bubble.freshCount > 0) {
        listOf(Color(0xFFF2A100), Color(0xFFE5574F), Color(0xFFD81B60))
    } else {
        avatarColors(friend.displayName).let { (a, b) -> listOf(a, b) }
    }
    Column(
        modifier.width(80.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(Brush.sweepGradient(ring + ring.first())).padding(3.dp)
                .clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            FriendAvatar(friend, size = 58.dp)
        }
        Text(friend.displayName.substringBefore(' '), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            relativeDay(bubble.meetingOn, today),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}
