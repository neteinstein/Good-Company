package com.neteinstein.goodcompany.feature.hangouts.ui.screens.gifts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.usecase.GiftListPerson
import com.neteinstein.goodcompany.core.domain.usecase.GiftTotals
import com.neteinstein.goodcompany.core.domain.usecase.ListedGift
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.l10n.aiErrorText
import com.neteinstein.goodcompany.core.ui.l10n.relationToMeText
import com.neteinstein.goodcompany.core.ui.resources.add
import com.neteinstein.goodcompany.core.ui.resources.back
import com.neteinstein.goodcompany.core.ui.resources.close
import com.neteinstein.goodcompany.core.ui.resources.remove
import com.neteinstein.goodcompany.core.ui.resources.undo
import com.neteinstein.goodcompany.feature.hangouts.resources.Res
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_add_gift
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_add_n
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_add_people
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_ai_ideas
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_ai_working
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_budget
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_budget_each
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_circle_chip
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_edit
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_from_link
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_gift_price
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_gift_title
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_last_year
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_missing
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_name_age
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_no_budget
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_no_new_ideas
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_nobody
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_nobody_found
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_or_search
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_partial
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_pick_ideas
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_planned
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_remove_person
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_removed
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_search_hint
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_set_budget
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_spent
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_whole_circle
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_year
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_duplicate
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_lists_more
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

@Composable
fun GiftListScreen(
    listId: String,
    onBack: () -> Unit,
    onOpenFriend: (FriendId) -> Unit,
    onOpenList: (String) -> Unit,
    viewModel: GiftListViewModel = koinViewModel(key = listId) { parametersOf(listId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.open) {
        state.open?.let {
            viewModel.onOpened()
            onOpenList(it)
        }
    }
    GiftListContent(
        state,
        GiftListActions(
            onBack = onBack,
            onOpenFriend = onOpenFriend,
            onEdit = viewModel::onEdit,
            onDismissEdit = viewModel::onDismissEdit,
            onSaveEdit = viewModel::onSaveEdit,
            onDuplicate = viewModel::onDuplicate,
            onStartAdding = viewModel::onStartAdding,
            onDismissAdding = viewModel::onDismissAdding,
            onAddQuery = viewModel::onAddQuery,
            onToggleCircle = viewModel::onToggleCircle,
            onTogglePerson = viewModel::onTogglePerson,
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
    )
}

data class GiftListActions(
    val onBack: () -> Unit = {},
    val onOpenFriend: (FriendId) -> Unit = {},
    val onEdit: () -> Unit = {},
    val onDismissEdit: () -> Unit = {},
    val onSaveEdit: (String, GiftOccasion?, LocalDate?) -> Unit = { _, _, _ -> },
    val onDuplicate: () -> Unit = {},
    val onStartAdding: () -> Unit = {},
    val onDismissAdding: () -> Unit = {},
    val onAddQuery: (String) -> Unit = {},
    val onToggleCircle: (String) -> Unit = {},
    val onTogglePerson: (FriendId) -> Unit = {},
    val onAddBudget: (String) -> Unit = {},
    val onConfirmAdding: () -> Unit = {},
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
fun GiftListContent(state: GiftListUiState, actions: GiftListActions) {
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(CoreRes.string.undo)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val canUndo = message is GiftListMessage.Removed && state.recentlyRemoved != null
        val result = snackbar.showSnackbar(message.text(), actionLabel = undo.takeIf { canUndo })
        if (result == SnackbarResult.ActionPerformed) actions.onUndoRemove()
        actions.onMessageShown()
    }
    val detail = state.detail
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (detail != null) {
                ExtendedFloatingActionButton(
                    onClick = actions.onStartAdding,
                    icon = { Icon(Icons.Rounded.GroupAdd, contentDescription = null) },
                    text = { Text(stringResource(Res.string.gift_list_add_people)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.back)) }
                    Column(Modifier.weight(1f)) {
                        Text(
                            detail?.list?.title.orEmpty(),
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                        detail?.list?.occasionDate?.let {
                            Text(
                                "${it.shortLabel()} ${it.year}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (detail != null) HeaderMenu(actions)
                }
            }
            if (!state.isLoading && detail == null) {
                item(key = "missing") { EmptyState(Icons.Rounded.CardGiftcard, stringResource(Res.string.gift_list_missing), "") }
            }
            if (detail == null) return@LazyColumn
            item(key = "totals") { Totals(detail.totals, Modifier.padding(horizontal = 16.dp)) }
            if (detail.people.isEmpty()) {
                item(key = "nobody") {
                    Text(
                        stringResource(Res.string.gift_list_nobody),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
            items(detail.people, key = { it.friend.id.value }) { person ->
                PersonCard(
                    person = person,
                    currency = detail.totals.currency,
                    year = (detail.list.occasionDate ?: detail.today).year,
                    state = state,
                    actions = actions,
                    modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                )
            }
        }
    }
    if (state.editing && detail != null) {
        val list = detail.list
        GiftListEditorDialog(
            NewGiftList(list.title, list.occasion, list.occasionDate),
            detail.today,
            isNew = false,
            onDismiss = actions.onDismissEdit,
            onSave = actions.onSaveEdit,
        )
    }
    state.adding?.let { AddPeopleSheet(it, state, actions) }
}

@Composable
private fun HeaderMenu(actions: GiftListActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.gift_lists_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.gift_list_edit)) }, onClick = {
                open = false
                actions.onEdit()
            })
            DropdownMenuItem(text = { Text(stringResource(Res.string.gift_lists_duplicate)) }, onClick = {
                open = false
                actions.onDuplicate()
            })
        }
    }
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
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddPeopleSheet(draft: AddPeopleDraft, state: GiftListUiState, actions: GiftListActions) {
    val available = state.detail?.others.orEmpty().map { it.id }.toSet()
    ModalBottomSheet(onDismissRequest = actions.onDismissAdding) {
        LazyColumn(
            Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "title") { Text(stringResource(Res.string.gift_list_add_people), style = MaterialTheme.typography.headlineSmall) }
            val circles = state.circles.filter { c -> c.memberIds.any { it in available } }
            if (circles.isNotEmpty()) {
                item(key = "circles-h") { Text(stringResource(Res.string.gift_list_whole_circle), style = MaterialTheme.typography.labelLarge) }
                item(key = "circles") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        circles.forEach { circle ->
                            val members = circle.memberIds.filter { it in available }
                            FilterChip(
                                selected = draft.picked.containsAll(members),
                                onClick = { actions.onToggleCircle(circle.id) },
                                label = { Text(stringResource(Res.string.gift_list_circle_chip, circle.emoji, circle.name, members.size)) },
                            )
                        }
                    }
                }
            }
            item(key = "search-h") { Text(stringResource(Res.string.gift_list_or_search), style = MaterialTheme.typography.labelLarge) }
            item(key = "search") {
                OutlinedTextField(
                    value = draft.query,
                    onValueChange = actions.onAddQuery,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(Res.string.gift_list_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val matches = state.matches
            if (matches.isEmpty()) {
                item(key = "none") {
                    Text(
                        stringResource(Res.string.gift_list_nobody_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(matches, key = { it.id.value }) { friend ->
                Row(
                    Modifier.fillMaxWidth().clickable { actions.onTogglePerson(friend.id) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = friend.id in draft.picked, onCheckedChange = { actions.onTogglePerson(friend.id) })
                    FriendAvatar(friend, size = 40.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(friend.displayName, style = MaterialTheme.typography.titleSmall)
                        friend.relationToMe?.let {
                            Text(
                                relationToMeText(it, friend.pronouns),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item(key = "budget") {
                OutlinedTextField(
                    value = draft.budget,
                    onValueChange = actions.onAddBudget,
                    label = { Text(stringResource(Res.string.gift_list_budget_each)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(key = "confirm") {
                Button(onClick = actions.onConfirmAdding, enabled = draft.picked.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(pluralStringResource(Res.plurals.gift_list_add_n, draft.picked.size, draft.picked.size))
                }
            }
        }
    }
}
