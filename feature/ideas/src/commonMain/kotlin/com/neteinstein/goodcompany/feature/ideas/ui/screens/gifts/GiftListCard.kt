package com.neteinstein.goodcompany.feature.ideas.ui.screens.gifts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.GiftListSummary
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.resources.delete
import com.neteinstein.goodcompany.feature.ideas.resources.Res
import com.neteinstein.goodcompany.feature.ideas.resources.gift_list_edit
import com.neteinstein.goodcompany.feature.ideas.resources.gift_list_partial
import com.neteinstein.goodcompany.feature.ideas.resources.gift_lists_all_given
import com.neteinstein.goodcompany.feature.ideas.resources.gift_lists_bought
import com.neteinstein.goodcompany.feature.ideas.resources.gift_lists_duplicate
import com.neteinstein.goodcompany.feature.ideas.resources.gift_lists_hide
import com.neteinstein.goodcompany.feature.ideas.resources.gift_lists_more
import com.neteinstein.goodcompany.feature.ideas.resources.gift_lists_people
import com.neteinstein.goodcompany.feature.ideas.resources.gift_lists_show
import com.neteinstein.goodcompany.feature.ideas.resources.gift_lists_spent_of
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** What a gift list's card can do on the Plans tab (issue #104). */
data class GiftListCardActions(
    val onToggle: (String) -> Unit = {},
    val onEdit: (String) -> Unit = {},
    val onDuplicate: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onOpenFriend: (FriendId) -> Unit = {},
)

/**
 * One gift list on the Plans tab (issue #104): its name, date, people, how much is bought and the totals; tapping it
 * opens the list in place ([GiftListPanel]). A past list offers "Duplicate" for next year.
 */
@Composable
fun GiftListCard(
    summary: GiftListSummary,
    expanded: Boolean,
    past: Boolean,
    snackbar: SnackbarHostState,
    actions: GiftListCardActions,
    modifier: Modifier = Modifier,
) {
    val list = summary.list
    Surface(
        onClick = { actions.onToggle(list.id) },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OccasionBadge(list.occasion)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(list.title, style = MaterialTheme.typography.titleMedium)
                    list.occasionDate?.let {
                        Text(
                            if (past) "${it.shortLabel()} ${it.year}" else it.shortLabel(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(if (expanded) Res.string.gift_lists_hide else Res.string.gift_lists_show),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ListMenu(list.id, actions)
            }
            Summary(summary, past, Modifier.padding(end = 12.dp))
            if (past && !expanded) {
                TextButton(onClick = { actions.onDuplicate(list.id) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                    Text(stringResource(Res.string.gift_lists_duplicate))
                }
            }
            AnimatedVisibility(expanded) {
                GiftListPanel(list.id, snackbar, actions.onOpenFriend, Modifier.padding(end = 12.dp, top = 4.dp))
            }
        }
    }
}

@Composable
private fun Summary(summary: GiftListSummary, past: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (summary.people.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StackedAvatars(summary.people)
                Text(
                    listOfNotNull(
                        pluralStringResource(Res.plurals.gift_lists_people, summary.people.size, summary.people.size),
                        summary.gifts.takeIf { it > 0 && !summary.allGiven }
                            ?.let { pluralStringResource(Res.plurals.gift_lists_bought, it, summary.bought + summary.given, it) },
                        stringResource(Res.string.gift_lists_all_given).takeIf { summary.allGiven },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (summary.gifts > 0 && !past) {
            LinearProgressIndicator(progress = { (summary.bought + summary.given).toFloat() / summary.gifts }, modifier = Modifier.fillMaxWidth())
        }
        val totals = summary.totals
        if (totals.spent > 0 || totals.planned > 0) {
            Text(
                stringResource(Res.string.gift_lists_spent_of, money(totals.spent, totals.currency), money(totals.planned, totals.currency)),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (totals.spentPartial || totals.plannedPartial) {
                Text(
                    stringResource(Res.string.gift_list_partial),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ListMenu(id: String, actions: GiftListCardActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.gift_lists_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gift_list_edit)) },
                onClick = {
                    open = false
                    actions.onEdit(id)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.gift_lists_duplicate)) },
                onClick = {
                    open = false
                    actions.onDuplicate(id)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(CoreRes.string.delete)) },
                onClick = {
                    open = false
                    actions.onDelete(id)
                },
            )
        }
    }
}
