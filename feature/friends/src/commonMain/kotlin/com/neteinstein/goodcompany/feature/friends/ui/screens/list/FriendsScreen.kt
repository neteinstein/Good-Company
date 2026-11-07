package com.neteinstein.goodcompany.feature.friends.ui.screens.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.platformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.feature.friends.ui.components.CatchUpCard
import com.neteinstein.goodcompany.feature.friends.ui.components.FriendRow
import com.neteinstein.goodcompany.feature.friends.ui.components.PersonChip
import com.neteinstein.goodcompany.feature.friends.ui.components.UpcomingCard
import org.koin.compose.viewmodel.koinViewModel

/** Route: pulls state from [FriendsViewModel] and handles the platform permission prompt. */
@Composable
fun FriendsScreen(onOpenFriend: (FriendId) -> Unit, viewModel: FriendsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestContacts = rememberPermissionRequester(AppPermission.CONTACTS, viewModel::onContactsPermissionResult)
    FriendsContent(
        state = state,
        canSyncContacts = platformCapabilities.hasAddressBook,
        actions = FriendsActions(
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onSync = requestContacts,
            onOpenFriend = onOpenFriend,
            onToggleClose = viewModel::onToggleClose,
            onMetToday = viewModel::onMetToday,
            onAddFriend = viewModel::onAddFriend,
            onExploreSample = viewModel::onExploreSample,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

data class FriendsActions(
    val onQueryChange: (String) -> Unit = {},
    val onFilterChange: (FriendFilter) -> Unit = {},
    val onSync: () -> Unit = {},
    val onOpenFriend: (FriendId) -> Unit = {},
    val onToggleClose: (FriendId) -> Unit = {},
    val onMetToday: (FriendId) -> Unit = {},
    val onAddFriend: (String) -> Unit = {},
    val onExploreSample: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FriendsContent(state: FriendsUiState, canSyncContacts: Boolean, actions: FriendsActions) {
    val snackbar = remember { SnackbarHostState() }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    val isEmpty = !state.isLoading && state.totalCount == 0

    Scaffold(
        floatingActionButton = {
            if (!isEmpty) {
                ExtendedFloatingActionButton(onClick = {
                    showAdd = true
                }, icon = { Icon(Icons.Rounded.PersonAdd, null) }, text = { Text("Add friend") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom =
                padding.calculateBottomPadding() + 88.dp,
            ),
        ) {
            item(key = "header") {
                ScreenHeader(
                    eyebrow = state.greeting.text,
                    title = "Your people",
                    subtitle = state.weekSummary ?: if (isEmpty) null else "All quiet this week — a good time to call someone ☕",
                    trailing = {
                        Row {
                            if (!isEmpty) {
                                FilledTonalIconButton(onClick = {
                                    searchOpen = !searchOpen
                                    if (!searchOpen) actions.onQueryChange("")
                                }) {
                                    Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, if (searchOpen) "Close search" else "Search")
                                }
                            }
                            if (canSyncContacts) {
                                IconButton(onClick = actions.onSync, enabled = !state.isSyncing) { Icon(Icons.Rounded.Sync, "Sync contacts") }
                            }
                        }
                    },
                )
            }
            item(key = "progress") {
                AnimatedVisibility(state.isSyncing) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
            }
            item(key = "search") {
                AnimatedVisibility(searchOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    TextField(
                        value = state.query,
                        onValueChange = actions.onQueryChange,
                        placeholder = { Text("Search names or tastes — “jazz”, “F1”…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            if (isEmpty) {
                item(key = "welcome") { Welcome(canSyncContacts, actions.onSync, { showAdd = true }, actions.onExploreSample) }
                return@LazyColumn
            }

            if (!state.isSearching && state.upcoming.isNotEmpty()) {
                item(key = "upcoming-h") { SectionHeader("Coming up") }
                item(key = "upcoming") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.upcoming, key = { it.key }) { UpcomingCard(it, state.today, actions.onOpenFriend, Modifier.animateItem()) }
                    }
                }
            }
            if (!state.isSearching && state.catchUp.isNotEmpty()) {
                item(key = "catchup-h") { SectionHeader("Time to catch up") }
                items(state.catchUp.take(3), key = { "c-${it.friend.id.value}" }) { s ->
                    CatchUpCard(s, onOpen = {
                        actions.onOpenFriend(s.friend.id)
                    }, onMet = { actions.onMetToday(s.friend.id) }, modifier = Modifier.animateItem())
                }
            }
            if (!state.isSearching && state.closeFriends.isNotEmpty()) {
                item(key = "people-h") { SectionHeader("Real friends · ${state.closeFriends.size}") }
                item(key = "people") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.closeFriends, key = { it.id.value }) { PersonChip(it, { actions.onOpenFriend(it.id) }, Modifier.animateItem()) }
                    }
                }
            }

            item(key = "filters") {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.filter == FriendFilter.CLOSE, { actions.onFilterChange(FriendFilter.CLOSE) }, label = { Text("Real friends") })
                    FilterChip(
                        state.filter == FriendFilter.ALL,
                        { actions.onFilterChange(FriendFilter.ALL) },
                        label = { Text("Everyone · ${state.totalCount}") },
                    )
                }
            }
            if (state.friends.isEmpty()) {
                item(key = "nomatch") {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = "No matches",
                        message = if (state.isSearching) {
                            "Nobody called or into “${state.query}”."
                        } else {
                            "Tap ♥ on someone in Everyone to make them a real friend."
                        },
                    )
                }
            }
            state.sections.forEach { (letter, people) ->
                stickyHeader(key = "letter-$letter") {
                    Text(
                        letter.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                    )
                }
                items(people, key = { it.friend.id.value }) { summary ->
                    FriendRow(
                        summary = summary,
                        now = state.now,
                        today = state.today,
                        zone = state.zone,
                        onClick = { actions.onOpenFriend(summary.friend.id) },
                        onToggleClose = { actions.onToggleClose(summary.friend.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddFriendDialog(onDismiss = { showAdd = false }, onAdd = {
            actions.onAddFriend(it)
            showAdd = false
        })
    }
}

@Composable
private fun Welcome(canSyncContacts: Boolean, onImport: () -> Unit, onAdd: () -> Unit, onSample: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            icon = Icons.Rounded.Contacts,
            title = "Who are your people?",
            message = if (canSyncContacts) {
                "Import your contacts and tap ♥ on the ones who are real friends — or add someone by hand."
            } else {
                "Add the people who matter. You'll track what they love, their kids, and the last time you met."
            },
        )
        if (canSyncContacts) {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.Contacts, null)
                Spacer(Modifier.width(8.dp))
                Text("Import contacts")
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.PersonAdd, null)
            Spacer(Modifier.width(8.dp))
            Text("Add someone")
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = onSample, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Explore with sample friends")
        }
        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun AddFriendDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.PersonAdd, null) },
        title = { Text("Add a friend") },
        text = {
            Column {
                Text("You can add birthdays, tastes and kids on their page.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
