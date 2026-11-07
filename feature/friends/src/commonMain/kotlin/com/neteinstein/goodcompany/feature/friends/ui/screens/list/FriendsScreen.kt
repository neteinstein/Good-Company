package com.neteinstein.goodcompany.feature.friends.ui.screens.list

import com.neteinstein.goodcompany.feature.friends.resources.msg_picked
import com.neteinstein.goodcompany.feature.friends.resources.welcome_pick_contacts
import com.neteinstein.goodcompany.feature.friends.resources.action_pick_contacts
import com.neteinstein.goodcompany.core.ui.platform.rememberContactPicker
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
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.format.shortLabelText
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.FileKind
import com.neteinstein.goodcompany.core.ui.platform.platformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.core.ui.platform.rememberTextFilePicker
import com.neteinstein.goodcompany.core.ui.resources.add
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.action_close_search
import com.neteinstein.goodcompany.feature.friends.resources.action_import_vcf
import com.neteinstein.goodcompany.feature.friends.resources.action_more
import com.neteinstein.goodcompany.feature.friends.resources.action_search
import com.neteinstein.goodcompany.feature.friends.resources.action_sync_contacts
import com.neteinstein.goodcompany.feature.friends.resources.add_hint
import com.neteinstein.goodcompany.feature.friends.resources.add_name
import com.neteinstein.goodcompany.feature.friends.resources.add_title
import com.neteinstein.goodcompany.feature.friends.resources.fab_add_friend
import com.neteinstein.goodcompany.feature.friends.resources.filter_everyone
import com.neteinstein.goodcompany.feature.friends.resources.filter_real_friends
import com.neteinstein.goodcompany.feature.friends.resources.greeting_afternoon
import com.neteinstein.goodcompany.feature.friends.resources.greeting_evening
import com.neteinstein.goodcompany.feature.friends.resources.greeting_morning
import com.neteinstein.goodcompany.feature.friends.resources.home_quiet_week
import com.neteinstein.goodcompany.feature.friends.resources.home_title
import com.neteinstein.goodcompany.feature.friends.resources.msg_added
import com.neteinstein.goodcompany.feature.friends.resources.msg_call_logged
import com.neteinstein.goodcompany.feature.friends.resources.msg_contacts_denied
import com.neteinstein.goodcompany.feature.friends.resources.msg_logged_with
import com.neteinstein.goodcompany.feature.friends.resources.msg_sample_added
import com.neteinstein.goodcompany.feature.friends.resources.msg_snoozed
import com.neteinstein.goodcompany.feature.friends.resources.msg_sync_failed
import com.neteinstein.goodcompany.feature.friends.resources.msg_synced
import com.neteinstein.goodcompany.feature.friends.resources.msg_synced_calls
import com.neteinstein.goodcompany.feature.friends.resources.msg_vcf_empty
import com.neteinstein.goodcompany.feature.friends.resources.msg_vcf_result
import com.neteinstein.goodcompany.feature.friends.resources.msg_your_friend
import com.neteinstein.goodcompany.feature.friends.resources.no_matches_close
import com.neteinstein.goodcompany.feature.friends.resources.no_matches_search
import com.neteinstein.goodcompany.feature.friends.resources.no_matches_title
import com.neteinstein.goodcompany.feature.friends.resources.search_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.section_catch_up
import com.neteinstein.goodcompany.feature.friends.resources.section_coming_up
import com.neteinstein.goodcompany.feature.friends.resources.section_drifting
import com.neteinstein.goodcompany.feature.friends.resources.section_drifting_hint
import com.neteinstein.goodcompany.feature.friends.resources.section_real_friends
import com.neteinstein.goodcompany.feature.friends.resources.sort_by
import com.neteinstein.goodcompany.feature.friends.resources.sort_name
import com.neteinstein.goodcompany.feature.friends.resources.sort_warmth
import com.neteinstein.goodcompany.feature.friends.resources.week_big_days
import com.neteinstein.goodcompany.feature.friends.resources.week_birthdays
import com.neteinstein.goodcompany.feature.friends.resources.week_catch_up
import com.neteinstein.goodcompany.feature.friends.resources.week_suffix
import com.neteinstein.goodcompany.feature.friends.resources.welcome_add_someone
import com.neteinstein.goodcompany.feature.friends.resources.welcome_import_contacts
import com.neteinstein.goodcompany.feature.friends.resources.welcome_import_vcf
import com.neteinstein.goodcompany.feature.friends.resources.welcome_message_contacts
import com.neteinstein.goodcompany.feature.friends.resources.welcome_message_manual
import com.neteinstein.goodcompany.feature.friends.resources.welcome_sample
import com.neteinstein.goodcompany.feature.friends.resources.welcome_title
import com.neteinstein.goodcompany.feature.friends.ui.components.CatchUpCard
import com.neteinstein.goodcompany.feature.friends.ui.components.DriftingCard
import com.neteinstein.goodcompany.feature.friends.ui.components.FriendRow
import com.neteinstein.goodcompany.feature.friends.ui.components.PersonChip
import com.neteinstein.goodcompany.feature.friends.ui.components.UpcomingCard
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** Route: pulls state from [FriendsViewModel] and handles the platform permission prompt and file picker. */
@Composable
fun FriendsScreen(onOpenFriend: (FriendId) -> Unit, viewModel: FriendsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestContacts = rememberPermissionRequester(AppPermission.CONTACTS, viewModel::onContactsPermissionResult)
    val pickVcf = rememberTextFilePicker(FileKind.VCARD, onPicked = viewModel::onContactsFilePicked)
    val pickContacts = rememberContactPicker(onPicked = viewModel::onContactsPicked)
    FriendsContent(
        state = state,
        canSyncContacts = platformCapabilities.hasAddressBook,
        canPickContacts = platformCapabilities.hasContactPicker,
        actions = FriendsActions(
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onSortChange = viewModel::onSortChange,
            onSync = requestContacts,
            onImportFile = pickVcf,
            onPickContacts = pickContacts,
            onOpenFriend = onOpenFriend,
            onToggleClose = viewModel::onToggleClose,
            onMetToday = viewModel::onMetToday,
            onCalled = viewModel::onCalled,
            onSnooze = viewModel::onSnooze,
            onAddFriend = viewModel::onAddFriend,
            onExploreSample = viewModel::onExploreSample,
            onMessageShown = viewModel::onMessageShown,
        ),
    )
}

data class FriendsActions(
    val onQueryChange: (String) -> Unit = {},
    val onFilterChange: (FriendFilter) -> Unit = {},
    val onSortChange: (FriendSort) -> Unit = {},
    val onSync: () -> Unit = {},
    val onImportFile: () -> Unit = {},
    val onPickContacts: () -> Unit = {},
    val onOpenFriend: (FriendId) -> Unit = {},
    val onToggleClose: (FriendId) -> Unit = {},
    val onMetToday: (FriendId) -> Unit = {},
    val onCalled: (FriendId) -> Unit = {},
    val onSnooze: (FriendId, SnoozeOption) -> Unit = { _, _ -> },
    val onAddFriend: (String) -> Unit = {},
    val onExploreSample: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** Localised snackbar text for a [FriendsMessage]. */
suspend fun FriendsMessage.text(): String = when (this) {
    is FriendsMessage.LoggedWith -> getString(Res.string.msg_logged_with, name ?: getString(Res.string.msg_your_friend))
    is FriendsMessage.CallLogged -> getString(Res.string.msg_call_logged, name)
    is FriendsMessage.Snoozed -> getString(Res.string.msg_snoozed, name, until.shortLabelText())
    FriendsMessage.ContactsDenied -> getString(Res.string.msg_contacts_denied)
    is FriendsMessage.Synced -> if (calls > 0) getString(Res.string.msg_synced_calls, contacts, calls) else getString(Res.string.msg_synced, contacts)
    FriendsMessage.SyncFailed -> getString(Res.string.msg_sync_failed)
    is FriendsMessage.Added -> getString(Res.string.msg_added, name)
    FriendsMessage.SampleAdded -> getString(Res.string.msg_sample_added)
    is FriendsMessage.FileImported -> if (result.added + result.updated + result.skipped == 0) {
        getString(Res.string.msg_vcf_empty)
    } else {
        getString(Res.string.msg_vcf_result, result.added, result.updated, result.skipped)
    }
    is FriendsMessage.ContactsPicked -> (result.added + result.updated).let { n -> getPluralString(Res.plurals.msg_picked, n, n) }
}

@Composable
private fun weekSummaryText(week: WeekSummary): String? {
    if (week.isEmpty) return null
    val parts = listOfNotNull(
        week.birthdays.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.week_birthdays, it, it) },
        week.bigDays.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.week_big_days, it, it) },
        week.catchUps.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.week_catch_up, it, it) },
    )
    return stringResource(Res.string.week_suffix, parts.joinToString(" · "))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FriendsContent(state: FriendsUiState, canSyncContacts: Boolean, actions: FriendsActions, canPickContacts: Boolean = false) {
    val snackbar = remember { SnackbarHostState() }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.text())
            actions.onMessageShown()
        }
    }
    val isEmpty = !state.isLoading && state.totalCount == 0

    Scaffold(
        floatingActionButton = {
            if (!isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = { showAdd = true },
                    icon = { Icon(Icons.Rounded.PersonAdd, null) },
                    text = { Text(stringResource(Res.string.fab_add_friend)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    eyebrow = stringResource(
                        when (state.greeting) {
                            Greeting.MORNING -> Res.string.greeting_morning
                            Greeting.AFTERNOON -> Res.string.greeting_afternoon
                            Greeting.EVENING -> Res.string.greeting_evening
                        },
                    ),
                    title = stringResource(Res.string.home_title),
                    subtitle = weekSummaryText(state.week) ?: if (isEmpty) null else stringResource(Res.string.home_quiet_week),
                    trailing = {
                        Row {
                            if (!isEmpty) {
                                FilledTonalIconButton(onClick = {
                                    searchOpen = !searchOpen
                                    if (!searchOpen) actions.onQueryChange("")
                                }) {
                                    Icon(
                                        if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                                        stringResource(if (searchOpen) Res.string.action_close_search else Res.string.action_search),
                                    )
                                }
                            }
                            if (canSyncContacts) {
                                IconButton(onClick = actions.onSync, enabled = !state.isSyncing) {
                                    Icon(Icons.Rounded.Sync, stringResource(Res.string.action_sync_contacts))
                                }
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, stringResource(Res.string.action_more)) }
                                DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                                    if (canPickContacts) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.action_pick_contacts)) },
                                            leadingIcon = { Icon(Icons.Rounded.PersonAdd, null) },
                                            onClick = {
                                                menuOpen = false
                                                actions.onPickContacts()
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.action_import_vcf)) },
                                        leadingIcon = { Icon(Icons.Rounded.UploadFile, null) },
                                        onClick = {
                                            menuOpen = false
                                            actions.onImportFile()
                                        },
                                    )
                                }
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
                        placeholder = { Text(stringResource(Res.string.search_placeholder)) },
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
                item(key = "welcome") { Welcome(canSyncContacts, actions.onSync, actions.onImportFile, { showAdd = true }, actions.onExploreSample, actions.onPickContacts.takeIf { canPickContacts }) }
                return@LazyColumn
            }

            if (!state.isSearching && state.upcoming.isNotEmpty()) {
                item(key = "upcoming-h") { SectionHeader(stringResource(Res.string.section_coming_up)) }
                item(key = "upcoming") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.upcoming, key = { it.key }) { UpcomingCard(it, state.today, actions.onOpenFriend, Modifier.animateItem()) }
                    }
                }
            }
            if (!state.isSearching && state.catchUp.isNotEmpty()) {
                item(key = "catchup-h") { SectionHeader(stringResource(Res.string.section_catch_up)) }
                items(state.catchUp.take(3), key = { "c-${it.friend.id.value}" }) { s ->
                    val id = s.friend.id
                    CatchUpCard(
                        s,
                        onOpen = { actions.onOpenFriend(id) },
                        onMet = { actions.onMetToday(id) },
                        onCalled = { actions.onCalled(id) },
                        onSnooze = { actions.onSnooze(id, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (!state.isSearching && state.drifting.isNotEmpty()) {
                item(key = "drift-h") { SectionHeader(stringResource(Res.string.section_drifting)) }
                item(key = "drift-hint") {
                    Text(
                        stringResource(Res.string.section_drifting_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
                items(state.drifting, key = { "d-${it.friend.id.value}" }) { s ->
                    DriftingCard(
                        s,
                        onOpen = { actions.onOpenFriend(s.friend.id) },
                        onSnooze = { actions.onSnooze(s.friend.id, SnoozeOption.ONE_WEEK) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (!state.isSearching && state.closeFriends.isNotEmpty()) {
                item(key = "people-h") { SectionHeader(stringResource(Res.string.section_real_friends, state.closeFriends.size)) }
                item(key = "people") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.closeFriends, key = { it.id.value }) { PersonChip(it, { actions.onOpenFriend(it.id) }, Modifier.animateItem()) }
                    }
                }
            }

            item(key = "filters") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        state.filter == FriendFilter.CLOSE,
                        { actions.onFilterChange(FriendFilter.CLOSE) },
                        label = { Text(stringResource(Res.string.filter_real_friends)) },
                    )
                    FilterChip(
                        state.filter == FriendFilter.ALL,
                        { actions.onFilterChange(FriendFilter.ALL) },
                        label = { Text(stringResource(Res.string.filter_everyone, state.totalCount)) },
                    )
                    Spacer(Modifier.weight(1f))
                    SortMenu(state.sort, actions.onSortChange)
                }
            }
            if (state.friends.isEmpty()) {
                item(key = "nomatch") {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = stringResource(Res.string.no_matches_title),
                        message = if (state.isSearching) {
                            stringResource(Res.string.no_matches_search, state.query)
                        } else {
                            stringResource(Res.string.no_matches_close)
                        },
                    )
                }
            }
            state.sections.forEach { (letter, people) ->
                if (letter != null) {
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
                }
                items(people, key = { it.friend.id.value }) { summary ->
                    FriendRow(
                        summary = summary,
                        now = state.now,
                        today = state.today,
                        zone = state.zone,
                        onClick = { actions.onOpenFriend(summary.friend.id) },
                        onToggleClose = { actions.onToggleClose(summary.friend.id) },
                        showWarmth = state.showWarmth,
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
private fun SortMenu(sort: FriendSort, onChange: (FriendSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (sort == FriendSort.NAME) Res.string.sort_name else Res.string.sort_warmth), maxLines = 1)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Text(
                stringResource(Res.string.sort_by),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            listOf(FriendSort.NAME to Res.string.sort_name, FriendSort.WARMTH to Res.string.sort_warmth).forEach { (value, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    open = false
                    onChange(value)
                })
            }
        }
    }
}

@Composable
private fun Welcome(
    canSyncContacts: Boolean,
    onImport: () -> Unit,
    onImportFile: () -> Unit,
    onAdd: () -> Unit,
    onSample: () -> Unit,
    onPickContacts: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            icon = Icons.Rounded.Contacts,
            title = stringResource(Res.string.welcome_title),
            message = stringResource(if (canSyncContacts) Res.string.welcome_message_contacts else Res.string.welcome_message_manual),
        )
        if (canSyncContacts) {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.Contacts, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.welcome_import_contacts))
            }
            onPickContacts?.let { TextButton(onClick = it) { Text(stringResource(Res.string.welcome_pick_contacts)) } }
        } else {
            Button(onClick = onImportFile, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.UploadFile, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.welcome_import_vcf))
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.PersonAdd, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.welcome_add_someone))
        }
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = onSample, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.welcome_sample))
        }
        if (canSyncContacts) {
            TextButton(onClick = onImportFile) { Text(stringResource(Res.string.welcome_import_vcf)) }
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
        title = { Text(stringResource(Res.string.add_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.add_hint), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text(stringResource(Res.string.add_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank()) { Text(stringResource(CoreRes.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}
