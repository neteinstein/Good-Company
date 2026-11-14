package com.neteinstein.goodcompany.feature.friends.ui.screens.choose

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.usecase.ContactChoice
import com.neteinstein.goodcompany.core.domain.usecase.ContactListFilter
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.format.monthShort
import com.neteinstein.goodcompany.core.ui.platform.rememberContactPicker
import com.neteinstein.goodcompany.core.ui.resources.date_short
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.choose_add
import com.neteinstein.goodcompany.feature.friends.resources.choose_add_none
import com.neteinstein.goodcompany.feature.friends.resources.choose_already_friend
import com.neteinstein.goodcompany.feature.friends.resources.choose_birthday
import com.neteinstein.goodcompany.feature.friends.resources.choose_count
import com.neteinstein.goodcompany.feature.friends.resources.choose_empty_body
import com.neteinstein.goodcompany.feature.friends.resources.choose_empty_title
import com.neteinstein.goodcompany.feature.friends.resources.choose_favourites
import com.neteinstein.goodcompany.feature.friends.resources.choose_filter_all
import com.neteinstein.goodcompany.feature.friends.resources.choose_filter_birthday
import com.neteinstein.goodcompany.feature.friends.resources.choose_filter_favourites
import com.neteinstein.goodcompany.feature.friends.resources.choose_no_match
import com.neteinstein.goodcompany.feature.friends.resources.choose_on_device
import com.neteinstein.goodcompany.feature.friends.resources.choose_only_ticked
import com.neteinstein.goodcompany.feature.friends.resources.choose_pick_instead
import com.neteinstein.goodcompany.feature.friends.resources.choose_search
import com.neteinstein.goodcompany.feature.friends.resources.choose_title
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes
import com.neteinstein.goodcompany.core.ui.resources.back as backLabel

/**
 * "Choose friends": the phone's contacts with checkboxes (decision D2). [onDone] gets how many people were added;
 * [onBack] leaves without saving anyone. When the list is empty (access off) the system contact picker is offered.
 */
@Composable
fun ChooseContactsScreen(onBack: () -> Unit, onDone: (added: Int) -> Unit, viewModel: ChooseContactsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!opened) {
            opened = true
            viewModel.onOpened()
        }
    }
    LaunchedEffect(state.saved) { state.saved?.let { onDone(it.added) } }
    val pick = rememberContactPicker(onPicked = viewModel::onPicked)
    ChooseContactsContent(
        state = state,
        actions = ChooseContactsActions(
            onBack = onBack,
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onToggle = viewModel::onToggle,
            onSave = viewModel::onSave,
            onPickInstead = pick,
        ),
    )
}

data class ChooseContactsActions(
    val onBack: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onFilterChange: (ContactListFilter) -> Unit = {},
    val onToggle: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onPickInstead: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChooseContactsContent(state: ChooseContactsUiState, actions: ChooseContactsActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.choose_title), modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.backLabel)) }
                },
                actions = {
                    if (state.total > 0) {
                        Text(
                            stringResource(Res.string.choose_count, state.selected.size, state.total),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.total > 0) {
                Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
                    Button(
                        onClick = actions.onSave,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
                    ) {
                        val n = state.selected.size
                        Text(if (n == 0) stringResource(Res.string.choose_add_none) else pluralStringResource(Res.plurals.choose_add, n, n))
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.isEmpty -> Column(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Rounded.Contacts,
                    title = stringResource(Res.string.choose_empty_title),
                    message = stringResource(Res.string.choose_empty_body),
                    actionLabel = stringResource(Res.string.choose_pick_instead),
                    onAction = actions.onPickInstead,
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
            ) {
                item(key = "search") { SearchField(state, actions) }
                item(key = "filters") { FilterRow(state.filter, actions.onFilterChange) }
                item(key = "privacy") { PrivacyNote() }
                if (state.sections.isEmpty()) {
                    item(key = "no-match") {
                        Text(
                            stringResource(Res.string.choose_no_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                state.sections.forEach { section ->
                    stickyHeader(key = "h-${section.letter ?: "fav"}") {
                        Text(
                            section.letter ?: stringResource(Res.string.choose_favourites).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 24.dp, vertical = 6.dp),
                        )
                    }
                    // A favourite shows only in the favourites section, so ids are unique across sections.
                    items(section.items, key = { "c-${it.record.contactId}" }) { choice ->
                        ContactRow(choice, ticked = choice.alreadyFriend || choice.record.contactId in state.selected, onToggle = actions.onToggle)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(state: ChooseContactsUiState, actions: ChooseContactsActions) {
    TextField(
        value = state.query,
        onValueChange = actions.onQueryChange,
        placeholder = { Text(pluralStringResource(Res.plurals.choose_search, state.total, state.total)) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
            unfocusedIndicatorColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun FilterRow(filter: ContactListFilter, onChange: (ContactListFilter) -> Unit) {
    val chips = listOf(
        ContactListFilter.FAVOURITES_FIRST to Res.string.choose_filter_favourites,
        ContactListFilter.WITH_BIRTHDAY to Res.string.choose_filter_birthday,
        ContactListFilter.ALL to Res.string.choose_filter_all,
    )
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips, key = { it.first.name }) { (value, label) ->
            FilterChip(selected = filter == value, onClick = { onChange(value) }, label = { Text(stringResource(label)) })
        }
    }
}

@Composable
private fun PrivacyNote() {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(Res.string.choose_only_ticked) + " " + stringResource(Res.string.choose_on_device),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContactRow(choice: ContactChoice, ticked: Boolean, onToggle: (String) -> Unit) {
    val r = choice.record
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = ticked, enabled = !choice.alreadyFriend, role = Role.Checkbox, onValueChange = { onToggle(r.contactId) })
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = ticked, onCheckedChange = null, enabled = !choice.alreadyFriend)
        Spacer(Modifier.width(12.dp))
        FriendAvatar(name = r.name, initials = initialsOf(r.name), size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(r.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle(choice)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun subtitle(choice: ContactChoice): String? = when {
    choice.alreadyFriend -> stringResource(Res.string.choose_already_friend)
    choice.record.birthday != null -> stringResource(Res.string.choose_birthday, choice.record.birthday!!.label())
    else -> choice.record.phoneNumbers.firstOrNull()
}

@Composable
private fun Birthday.label(): String = stringResource(CoreRes.string.date_short, monthShort(Month(month)), day)

private fun initialsOf(name: String): String = name.split(' ', '-')
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifEmpty { "?" }
