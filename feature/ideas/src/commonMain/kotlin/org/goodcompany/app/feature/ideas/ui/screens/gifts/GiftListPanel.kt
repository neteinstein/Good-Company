package org.goodcompany.app.feature.ideas.ui.screens.gifts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.LinkType
import org.goodcompany.app.core.domain.usecase.GiftListPerson
import org.goodcompany.app.core.domain.usecase.GiftTotals
import org.goodcompany.app.core.domain.usecase.ListedGift
import org.goodcompany.app.core.ui.components.FriendAvatar
import org.goodcompany.app.core.ui.components.PeoplePickerSheet
import org.goodcompany.app.core.ui.components.showMessage
import org.goodcompany.app.core.ui.l10n.aiErrorText
import org.goodcompany.app.core.ui.l10n.relationToMeText
import org.goodcompany.app.core.ui.resources.add
import org.goodcompany.app.core.ui.resources.close
import org.goodcompany.app.core.ui.resources.remove
import org.goodcompany.app.core.ui.resources.undo
import org.goodcompany.app.feature.ideas.resources.Res
import org.goodcompany.app.feature.ideas.resources.gift_list_add_gift
import org.goodcompany.app.feature.ideas.resources.gift_list_add_people
import org.goodcompany.app.feature.ideas.resources.gift_list_ai_ideas
import org.goodcompany.app.feature.ideas.resources.gift_list_ai_working
import org.goodcompany.app.feature.ideas.resources.gift_list_budget
import org.goodcompany.app.feature.ideas.resources.gift_list_budget_each
import org.goodcompany.app.feature.ideas.resources.gift_list_from_link
import org.goodcompany.app.feature.ideas.resources.gift_list_gift_price
import org.goodcompany.app.feature.ideas.resources.gift_list_gift_title
import org.goodcompany.app.feature.ideas.resources.gift_list_last_year
import org.goodcompany.app.feature.ideas.resources.gift_list_missing
import org.goodcompany.app.feature.ideas.resources.gift_list_name_age
import org.goodcompany.app.feature.ideas.resources.gift_list_no_budget
import org.goodcompany.app.feature.ideas.resources.gift_list_no_new_ideas
import org.goodcompany.app.feature.ideas.resources.gift_list_nobody
import org.goodcompany.app.feature.ideas.resources.gift_list_partial
import org.goodcompany.app.feature.ideas.resources.gift_list_pick_ideas
import org.goodcompany.app.feature.ideas.resources.gift_list_planned
import org.goodcompany.app.feature.ideas.resources.gift_list_remove_person
import org.goodcompany.app.feature.ideas.resources.gift_list_removed
import org.goodcompany.app.feature.ideas.resources.gift_list_set_budget
import org.goodcompany.app.feature.ideas.resources.gift_list_spent
import org.goodcompany.app.feature.ideas.resources.gift_list_year
import org.goodcompany.app.feature.ideas.resources.gift_lists_more
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.goodcompany.app.core.ui.resources.Res as CoreRes

/**
 * One gift list opened in place on the Plans tab (issue #104, was its own screen): the totals, each person with their
 * gifts (to buy → bought → given), budget, past gifts, saved and AI ideas, and "Add people". Messages go to the tab's
 * [snackbar].
 */
@Composable
fun GiftListPanel(
    listId: String,
    snackbar: SnackbarHostState,
    onOpenFriend: (FriendId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GiftListViewModel = koinViewModel(key = "gift-list-$listId") { parametersOf(listId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GiftListPanelContent(
        state,
        GiftListActions(
            onOpenFriend = onOpenFriend,
            onStartAdding = viewModel::onStartAdding,
            onDismissAdding = viewModel::onDismissAdding,
            kinshipOf = viewModel::kinshipOf,
            onAddBudget = viewModel::onAddBudget,
            onConfirmAdding = viewModel::onConfirmAdding,
            onSetBudget = viewModel::onSetBudget,
            onRemovePerson = viewModel::onRemovePerson,
            onUndoRemove = viewModel::onUndoRemove,
            onAddGift = viewModel::onAddGift,
            onAddIdea = viewModel::onAddIdea,
            onRemoveGift = viewModel::onRemoveGift,
            onAdvance = viewModel::onAdvance,
            onSuggest = viewModel::onSuggest,
            onKeepSuggestion = viewModel::onKeepSuggestion,
            onDismissSuggestions = viewModel::onDismissSuggestions,
            onMessageShown = viewModel::onMessageShown,
        ),
        snackbar = snackbar,
        modifier = modifier,
    )
}

data class GiftListActions(
    val onOpenFriend: (FriendId) -> Unit = {},
    val onStartAdding: () -> Unit = {},
    val onDismissAdding: () -> Unit = {},
    /** The picker's kinship search ("my cousins"); null for a name search. */
    val kinshipOf: suspend (String) -> Set<LinkType>? = { null },
    val onAddBudget: (String) -> Unit = {},
    val onConfirmAdding: (List<FriendId>) -> Unit = {},
    val onSetBudget: (FriendId, String) -> Unit = { _, _ -> },
    val onRemovePerson: (FriendId) -> Unit = {},
    val onUndoRemove: () -> Unit = {},
    val onAddGift: (FriendId, String, String) -> Unit = { _, _, _ -> },
    val onAddIdea: (FriendId, String) -> Unit = { _, _ -> },
    val onRemoveGift: (FriendId, String) -> Unit = { _, _ -> },
    val onAdvance: (FriendId, String) -> Unit = { _, _ -> },
    val onSuggest: (FriendId) -> Unit = {},
    val onKeepSuggestion: (FriendId, String) -> Unit = { _, _ -> },
    val onDismissSuggestions: (FriendId) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

suspend fun GiftListMessage.text(): String = when (this) {
    is GiftListMessage.Removed -> getString(Res.string.gift_list_removed, name)
    GiftListMessage.NoNewIdeas -> getString(Res.string.gift_list_no_new_ideas)
    is GiftListMessage.AiError -> aiErrorText(error)
}

@Composable
fun GiftListPanelContent(state: GiftListUiState, actions: GiftListActions, snackbar: SnackbarHostState, modifier: Modifier = Modifier) {
    val undo = stringResource(CoreRes.string.undo)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val canUndo = message is GiftListMessage.Removed && state.recentlyRemoved != null
        val result = snackbar.showMessage(message.text(), undo.takeIf { canUndo })
        if (result == SnackbarResult.ActionPerformed) actions.onUndoRemove()
        actions.onMessageShown()
    }
    val detail = state.detail
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (detail == null) {
            if (!state.isLoading) {
                Text(
                    stringResource(Res.string.gift_list_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        Totals(detail.totals)
        if (detail.people.isEmpty()) {
            Text(
                stringResource(Res.string.gift_list_nobody),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        detail.people.forEach { person ->
            PersonCard(
                person = person,
                currency = detail.totals.currency,
                year = (detail.list.occasionDate ?: detail.today).year,
                state = state,
                actions = actions,
            )
        }
        OutlinedButton(onClick = actions.onStartAdding) {
            Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.gift_list_add_people))
        }
    }
    state.adding?.let { AddPeopleSheet(it, state, actions) }
}

@Composable
private fun Totals(totals: GiftTotals, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TotalTile(money(totals.spent, totals.currency), stringResource(Res.string.gift_list_spent), Modifier.weight(1f))
            TotalTile(money(totals.planned, totals.currency), stringResource(Res.string.gift_list_planned), Modifier.weight(1f))
        }
        if (totals.spentPartial || totals.plannedPartial) {
            Text(
                stringResource(Res.string.gift_list_partial),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TotalTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonCard(
    person: GiftListPerson,
    currency: String?,
    year: Int,
    state: GiftListUiState,
    actions: GiftListActions,
    modifier: Modifier = Modifier,
) {
    val friend = person.friend
    val id = friend.id
    var budgetDialog by remember { mutableStateOf(false) }
    var giftDialog by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FriendAvatar(friend, size = 48.dp, modifier = Modifier.clickable { actions.onOpenFriend(id) })
                Column(Modifier.weight(1f).padding(horizontal = 12.dp).clickable { actions.onOpenFriend(id) }) {
                    Text(
                        person.age?.let { stringResource(Res.string.gift_list_name_age, friend.displayName, it) } ?: friend.displayName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        listOfNotNull(
                            friend.relationToMe?.let { relationToMeText(it, friend.pronouns) },
                            person.entry.budget?.let { stringResource(Res.string.gift_list_budget, money(it, currency)) }
                                ?: stringResource(Res.string.gift_list_no_budget),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (person.spent > 0) Text(money(person.spent, currency), style = MaterialTheme.typography.titleSmall)
                PersonMenu(onBudget = { budgetDialog = true }, onRemove = { actions.onRemovePerson(id) })
            }
            person.gifts.forEach { gift ->
                GiftRow(gift, currency, onAdvance = { actions.onAdvance(id, gift.gift.id) }, onRemove = { actions.onRemoveGift(id, gift.gift.id) })
            }
            PastGifts(person, year)
            if (state.hasAiKey) {
                val working = state.suggestingFor == id
                TextButton(onClick = { actions.onSuggest(id) }, enabled = state.suggestingFor == null) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (working) Res.string.gift_list_ai_working else Res.string.gift_list_ai_ideas))
                }
                state.suggestions[id]?.let { ideas ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ideas.forEach { idea -> SuggestionChip(onClick = { actions.onKeepSuggestion(id, idea) }, label = { Text(idea) }) }
                        }
                        IconButton(onClick = { actions.onDismissSuggestions(id) }) { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.close)) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { giftDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.gift_list_add_gift))
                }
                if (person.savedIdeas.isNotEmpty()) SavedIdeas(person, onPick = { actions.onAddIdea(id, it) })
            }
        }
    }
    if (budgetDialog) {
        TextFieldsDialog(
            title = stringResource(Res.string.gift_list_set_budget),
            fields = listOf(stringResource(Res.string.gift_list_set_budget) to person.entry.budget?.let { money(it, null) }.orEmpty()),
            numeric = setOf(0),
            onDismiss = { budgetDialog = false },
            onSave = { values ->
                budgetDialog = false
                actions.onSetBudget(id, values.first())
            },
        )
    }
    if (giftDialog) {
        TextFieldsDialog(
            title = stringResource(Res.string.gift_list_add_gift),
            fields = listOf(stringResource(Res.string.gift_list_gift_title) to "", stringResource(Res.string.gift_list_gift_price) to ""),
            onDismiss = { giftDialog = false },
            onSave = { values ->
                giftDialog = false
                actions.onAddGift(id, values[0], values[1])
            },
            confirm = stringResource(CoreRes.string.add),
            requireFirst = true,
        )
    }
}

@Composable
private fun PersonMenu(onBudget: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.gift_lists_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.gift_list_set_budget)) }, onClick = {
                open = false
                onBudget()
            })
            DropdownMenuItem(text = { Text(stringResource(Res.string.gift_list_remove_person)) }, onClick = {
                open = false
                onRemove()
            })
        }
    }
}

@Composable
private fun GiftRow(gift: ListedGift, currency: String?, onAdvance: () -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Rounded.CardGiftcard, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(gift.gift.title, style = MaterialTheme.typography.bodyLarge)
            val price = gift.price?.let { money(it.amount, it.currency ?: currency) } ?: gift.gift.price
            val sub = listOfNotNull(stringResource(Res.string.gift_list_from_link).takeIf { gift.gift.url != null }, price).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusChip(gift.gift.title, gift.status, onAdvance)
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.remove)) }
    }
}

/** "Last year: paint box · 2024: scooter". */
@Composable
private fun PastGifts(person: GiftListPerson, year: Int) {
    val byYear = person.pastGifts.mapNotNull { g -> g.givenOn?.let { it.year to g.title } }.filter { it.first < year }.take(3)
    if (byYear.isEmpty()) return
    val text = byYear.map { (y, title) ->
        if (y == year - 1) stringResource(Res.string.gift_list_last_year, title) else stringResource(Res.string.gift_list_year, y, title)
    }.joinToString(" · ")
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun SavedIdeas(person: GiftListPerson, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Text(pluralStringResource(Res.plurals.gift_list_pick_ideas, person.savedIdeas.size, person.savedIdeas.size))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            person.savedIdeas.forEach { idea ->
                DropdownMenuItem(
                    text = { Text(listOfNotNull(idea.title, idea.price).joinToString(" · ")) },
                    onClick = {
                        open = false
                        onPick(idea.id)
                    },
                )
            }
        }
    }
}

/** "Add people": the one people picker (issue #89), with one optional budget for everyone added. */
@Composable
private fun AddPeopleSheet(draft: AddPeopleDraft, state: GiftListUiState, actions: GiftListActions) {
    PeoplePickerSheet(
        everyone = state.addable,
        circles = state.circles,
        chosen = emptyList(),
        onDismiss = actions.onDismissAdding,
        onDone = actions.onConfirmAdding,
        title = stringResource(Res.string.gift_list_add_people),
        requireSomeone = true,
        kinshipOf = actions.kinshipOf,
        extra = {
            OutlinedTextField(
                value = draft.budget,
                onValueChange = actions.onAddBudget,
                label = { Text(stringResource(Res.string.gift_list_budget_each)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
    )
}
