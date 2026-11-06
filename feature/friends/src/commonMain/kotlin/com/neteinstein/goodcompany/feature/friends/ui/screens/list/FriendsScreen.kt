package com.neteinstein.goodcompany.feature.friends.ui.screens.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.platformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.feature.friends.ui.components.FriendRow
import org.koin.compose.viewmodel.koinViewModel

/** Route: pulls state from [FriendsViewModel] and handles the platform permission prompt. */
@Composable
fun FriendsScreen(onOpenFriend: (FriendId) -> Unit, viewModel: FriendsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestContacts = rememberPermissionRequester(AppPermission.CONTACTS, viewModel::onContactsPermissionResult)
    FriendsContent(
        state = state,
        canSyncContacts = platformCapabilities.hasAddressBook,
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onSync = requestContacts,
        onOpenFriend = onOpenFriend,
        onToggleClose = viewModel::onToggleClose,
        onAddFriend = viewModel::onAddFriend,
        onMessageShown = viewModel::onMessageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsContent(
    state: FriendsUiState,
    canSyncContacts: Boolean,
    onQueryChange: (String) -> Unit,
    onFilterChange: (FriendFilter) -> Unit,
    onSync: () -> Unit,
    onOpenFriend: (FriendId) -> Unit,
    onToggleClose: (FriendId) -> Unit,
    onAddFriend: (String) -> Unit,
    onMessageShown: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Good Company") },
                actions = {
                    if (canSyncContacts) {
                        IconButton(onClick = onSync, enabled = !state.isSyncing) { Icon(Icons.Rounded.Sync, "Sync contacts") }
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Rounded.PersonAdd, null) },
                text = { Text("Add friend") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "progress") {
                AnimatedVisibility(state.isSyncing) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            item(key = "search") {
                TextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search friends or tastes") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item(key = "filters") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.filter == FriendFilter.CLOSE, { onFilterChange(FriendFilter.CLOSE) }, label = { Text("Real friends") })
                    FilterChip(state.filter == FriendFilter.ALL, { onFilterChange(FriendFilter.ALL) }, label = { Text("Everyone · ${state.totalCount}") })
                }
            }
            if (!state.isLoading && state.friends.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Groups,
                        title = if (state.totalCount == 0) "Your people live here" else "No matches",
                        message = when {
                            state.totalCount > 0 -> "Try another search, or switch to Everyone."
                            canSyncContacts -> "Import your contacts, then tap ♥ on the ones who are real friends."
                            else -> "Add the people who matter, then track tastes, kids and the last time you met."
                        },
                        actionLabel = if (state.totalCount == 0 && canSyncContacts) "Import contacts" else null,
                        onAction = onSync,
                    )
                }
            }
            items(state.friends, key = { it.friend.id.value }) { summary ->
                FriendRow(
                    summary = summary,
                    now = state.now,
                    today = state.today,
                    zone = state.zone,
                    onClick = { onOpenFriend(summary.friend.id) },
                    onToggleClose = { onToggleClose(summary.friend.id) },
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "fab-spacer") { Box(Modifier.padding(48.dp)) }
        }
    }

    if (showAdd) AddFriendDialog(onDismiss = { showAdd = false }, onAdd = { onAddFriend(it); showAdd = false })
}

@Composable
private fun AddFriendDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.PersonAdd, null) },
        title = { Text("Add a friend") },
        text = {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
