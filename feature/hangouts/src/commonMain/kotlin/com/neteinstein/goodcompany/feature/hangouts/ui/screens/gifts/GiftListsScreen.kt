package com.neteinstein.goodcompany.feature.hangouts.ui.screens.gifts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.usecase.GiftListSummary
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.resources.back
import com.neteinstein.goodcompany.core.ui.resources.delete
import com.neteinstein.goodcompany.core.ui.resources.undo
import com.neteinstein.goodcompany.feature.hangouts.resources.Res
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_partial
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_all_given
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_bought
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_deleted
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_duplicate
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_empty
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_more
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_new
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_past
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_people
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_spent_of
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_title
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

@Composable
fun GiftListsScreen(
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    viewModel: GiftListsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.open) {
        state.open?.let {
            viewModel.onOpened()
            onOpenList(it)
        }
    }
    GiftListsContent(
        state,
        GiftListsActions(
            onBack = onBack,
            onOpen = onOpenList,
            onNew = { viewModel.onNewList(NewGiftList(occasion = GiftOccasion.CHRISTMAS)) },
            onDismissNew = viewModel::onDismissNew,
            onCreate = viewModel::onCreate,
            onDuplicate = viewModel::onDuplicate,
            onDelete = viewModel::onDelete,
            onUndoDelete = viewModel::onUndoDelete,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

data class GiftListsActions(
    val onBack: () -> Unit = {},
    val onOpen: (String) -> Unit = {},
    val onNew: () -> Unit = {},
    val onDismissNew: () -> Unit = {},
    val onCreate: (String, GiftOccasion?, LocalDate?) -> Unit = { _, _, _ -> },
    val onDuplicate: (String) -> Unit = {},
    val onDelete: (String) -> Unit = {},
    val onUndoDelete: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

suspend fun GiftListsMessage.text(): String = when (this) {
    is GiftListsMessage.Deleted -> getString(Res.string.gift_lists_deleted, title)
}

@Composable
fun GiftListsContent(state: GiftListsUiState, actions: GiftListsActions) {
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(CoreRes.string.undo)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(message.text(), actionLabel = undo.takeIf { state.recentlyDeleted != null })
        if (result == SnackbarResult.ActionPerformed) actions.onUndoDelete()
        actions.onMessageShown()
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.back)) }
                    Text(
                        stringResource(Res.string.gift_lists_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = actions.onNew) { Icon(Icons.Rounded.Add, stringResource(Res.string.gift_lists_new)) }
                }
            }
            if (!state.isLoading && state.current.isEmpty() && state.past.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.CardGiftcard,
                        stringResource(Res.string.gift_lists_title),
                        stringResource(Res.string.gift_lists_empty),
                        actionLabel = stringResource(Res.string.gift_lists_new),
                        onAction = actions.onNew,
                    )
                }
            }
            items(state.current, key = { "c-${it.list.id}" }) { summary ->
                CurrentListCard(summary, actions, Modifier.padding(horizontal = 16.dp).animateItem())
            }
            if (state.past.isNotEmpty()) {
                item(key = "past-h") { SectionHeader(stringResource(Res.string.gift_lists_past)) }
                items(state.past, key = { "p-${it.list.id}" }) { summary -> PastListRow(summary, actions, Modifier.animateItem()) }
            }
        }
    }
    state.creating?.let { draft ->
        GiftListEditorDialog(draft, state.today, isNew = true, onDismiss = actions.onDismissNew, onSave = actions.onCreate)
    }
}

@Composable
private fun CurrentListCard(summary: GiftListSummary, actions: GiftListsActions, modifier: Modifier = Modifier) {
    val list = summary.list
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().clickable { actions.onOpen(list.id) },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OccasionBadge(list.occasion)
                Text(
                    list.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                list.occasionDate?.let {
                    Text(it.shortLabel(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                ListMenu(list.id, actions)
            }
            if (summary.people.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StackedAvatars(summary.people)
                    Text(
                        listOfNotNull(
                            pluralStringResource(Res.plurals.gift_lists_people, summary.people.size, summary.people.size),
                            summary.gifts.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.gift_lists_bought, it, summary.bought, it) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (summary.gifts > 0) {
                LinearProgressIndicator(progress = { summary.bought.toFloat() / summary.gifts }, modifier = Modifier.fillMaxWidth())
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
}

@Composable
private fun PastListRow(summary: GiftListSummary, actions: GiftListsActions, modifier: Modifier = Modifier) {
    val list = summary.list
    Row(
        modifier.fillMaxWidth().clickable { actions.onOpen(list.id) }.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OccasionBadge(list.occasion)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(list.title, style = MaterialTheme.typography.titleSmall)
            Text(
                listOfNotNull(
                    pluralStringResource(Res.plurals.gift_lists_people, summary.people.size, summary.people.size),
                    summary.totals.spent.takeIf { it > 0 }?.let { money(it, summary.totals.currency) },
                    stringResource(Res.string.gift_lists_all_given).takeIf { summary.allGiven },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { actions.onDuplicate(list.id) }) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 4.dp))
            Text(stringResource(Res.string.gift_lists_duplicate))
        }
        ListMenu(list.id, actions, duplicate = false)
    }
}

@Composable
private fun ListMenu(id: String, actions: GiftListsActions, duplicate: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.gift_lists_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (duplicate) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.gift_lists_duplicate)) },
                    onClick = {
                        open = false
                        actions.onDuplicate(id)
                    },
                )
            }
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
