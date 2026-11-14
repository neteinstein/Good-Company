package com.neteinstein.goodcompany.feature.friends.ui.screens.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.ui.components.AllChip
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.CircleChip
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.HeaderSettingsButton
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.l10n.closenessLabel
import com.neteinstein.goodcompany.core.ui.l10n.linkWord
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.FileKind
import com.neteinstein.goodcompany.core.ui.platform.currentPlatformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.rememberContactPicker
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.core.ui.platform.rememberTextFilePicker
import com.neteinstein.goodcompany.core.ui.resources.add
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.action_add_friend
import com.neteinstein.goodcompany.feature.friends.resources.action_close_search
import com.neteinstein.goodcompany.feature.friends.resources.action_import_vcf
import com.neteinstein.goodcompany.feature.friends.resources.action_more
import com.neteinstein.goodcompany.feature.friends.resources.action_network
import com.neteinstein.goodcompany.feature.friends.resources.action_people
import com.neteinstein.goodcompany.feature.friends.resources.action_pick_contacts
import com.neteinstein.goodcompany.feature.friends.resources.action_search
import com.neteinstein.goodcompany.feature.friends.resources.action_sync_contacts
import com.neteinstein.goodcompany.feature.friends.resources.add_hint
import com.neteinstein.goodcompany.feature.friends.resources.add_name
import com.neteinstein.goodcompany.feature.friends.resources.add_option_list_again
import com.neteinstein.goodcompany.feature.friends.resources.add_option_list_body
import com.neteinstein.goodcompany.feature.friends.resources.add_option_pick_body
import com.neteinstein.goodcompany.feature.friends.resources.add_option_type
import com.neteinstein.goodcompany.feature.friends.resources.add_option_type_body
import com.neteinstein.goodcompany.feature.friends.resources.add_title
import com.neteinstein.goodcompany.feature.friends.resources.choose_add_none
import com.neteinstein.goodcompany.feature.friends.resources.choose_empty_body
import com.neteinstein.goodcompany.feature.friends.resources.choose_only_ticked
import com.neteinstein.goodcompany.feature.friends.resources.choose_pick_instead
import com.neteinstein.goodcompany.feature.friends.resources.kin_search_empty
import com.neteinstein.goodcompany.feature.friends.resources.kin_search_result
import com.neteinstein.goodcompany.feature.friends.resources.msg_added
import com.neteinstein.goodcompany.feature.friends.resources.msg_contacts_denied
import com.neteinstein.goodcompany.feature.friends.resources.msg_picked
import com.neteinstein.goodcompany.feature.friends.resources.msg_sample_added
import com.neteinstein.goodcompany.feature.friends.resources.msg_sync_failed
import com.neteinstein.goodcompany.feature.friends.resources.msg_synced
import com.neteinstein.goodcompany.feature.friends.resources.msg_synced_calls
import com.neteinstein.goodcompany.feature.friends.resources.msg_vcf_empty
import com.neteinstein.goodcompany.feature.friends.resources.msg_vcf_result
import com.neteinstein.goodcompany.feature.friends.resources.no_matches_search
import com.neteinstein.goodcompany.feature.friends.resources.no_matches_title
import com.neteinstein.goodcompany.feature.friends.resources.people_all
import com.neteinstein.goodcompany.feature.friends.resources.people_card_cooling
import com.neteinstein.goodcompany.feature.friends.resources.people_card_count
import com.neteinstein.goodcompany.feature.friends.resources.people_card_meetups
import com.neteinstein.goodcompany.feature.friends.resources.people_empty
import com.neteinstein.goodcompany.feature.friends.resources.people_level_count
import com.neteinstein.goodcompany.feature.friends.resources.people_open
import com.neteinstein.goodcompany.feature.friends.resources.people_title
import com.neteinstein.goodcompany.feature.friends.resources.search_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.sort_name
import com.neteinstein.goodcompany.feature.friends.resources.suggested_family_count
import com.neteinstein.goodcompany.feature.friends.resources.suggested_family_title
import com.neteinstein.goodcompany.feature.friends.resources.welcome_add_someone
import com.neteinstein.goodcompany.feature.friends.resources.welcome_import_contacts
import com.neteinstein.goodcompany.feature.friends.resources.welcome_import_vcf
import com.neteinstein.goodcompany.feature.friends.resources.welcome_message_contacts
import com.neteinstein.goodcompany.feature.friends.resources.welcome_message_manual
import com.neteinstein.goodcompany.feature.friends.resources.welcome_pick_contacts
import com.neteinstein.goodcompany.feature.friends.resources.welcome_sample
import com.neteinstein.goodcompany.feature.friends.resources.welcome_title
import com.neteinstein.goodcompany.feature.friends.ui.components.FriendRow
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** Route: pulls state from [FriendsViewModel] and handles the platform permission prompt and file picker. */
@Composable
fun FriendsScreen(
    onOpenFriend: (FriendId) -> Unit,
    onEditCircle: (String) -> Unit = {},
    /** Opens the "Choose friends" list (contacts with checkboxes). */
    onChooseContacts: () -> Unit = {},
    /** How many people the "Choose friends" list just added (0 = nothing to report); [onChosenShown] clears it. */
    chosenContacts: Int = 0,
    onChosenShown: () -> Unit = {},
    /** "Your network" and "Your people" (issue #46), from the "Your people" card on top. */
    onOpenNetwork: () -> Unit = {},
    onOpenPeople: () -> Unit = {},
    /** "Suggested family" (F7). */
    onOpenSuggestedFamily: () -> Unit = {},
    /** The header's Settings button (issue #65). */
    onOpenSettings: () -> Unit = {},
    viewModel: FriendsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(chosenContacts) {
        if (chosenContacts > 0) {
            viewModel.onContactsChosen(chosenContacts)
            onChosenShown()
        }
    }
    val requestContacts = rememberPermissionRequester(AppPermission.CONTACTS, viewModel::onContactsPermissionResult)
    val pickVcf = rememberTextFilePicker(FileKind.VCARD, onPicked = viewModel::onContactsFilePicked)
    val pickContacts = rememberContactPicker(onPicked = viewModel::onContactsPicked)
    val capabilities = currentPlatformCapabilities()
    // Sideload build: ask for the contacts permission the write-back needs, then open the picker whatever the answer.
    val requestThenPick = rememberPermissionRequester(AppPermission.CONTACTS) { pickContacts() }
    // iOS and the sideload build: "Pick from contacts" asks for the permission each time (it may have been refused
    // before), then opens the list with checkboxes; a refusal falls back to the system picker (owner decision 2026-11-14).
    val requestContactList = rememberPermissionRequester(AppPermission.CONTACTS) { granted ->
        viewModel.onContactListPermissionResult(granted)
        if (granted) onChooseContacts()
    }
    LaunchedEffect(state.pickerFallback) {
        if (state.pickerFallback) {
            viewModel.onPickerFallbackShown()
            if (capabilities.hasContactPicker) pickContacts()
        }
    }
    FriendsContent(
        state = state,
        canSyncContacts = capabilities.hasAddressBook,
        canPickContacts = capabilities.hasContactPicker,
        addOptions = addFriendOptions(capabilities.hasContactPicker, capabilities.hasContactList),
        actions = FriendsActions(
            onQueryChange = viewModel::onQueryChange,
            onSortChange = viewModel::onSortChange,
            onSync = requestContacts,
            onImportFile = pickVcf,
            onPickContacts = if (capabilities.hasContactWriteBack) requestThenPick else pickContacts,
            onOpenFriend = onOpenFriend,
            onAddFriend = viewModel::onAddFriend,
            onChooseContacts = requestContactList,
            onExploreSample = viewModel::onExploreSample,
            onMessageShown = viewModel::onMessageShown,
            onCircleFilter = viewModel::onCircleFilter,
            onEditCircle = onEditCircle,
            onOpenNetwork = onOpenNetwork,
            onOpenPeople = onOpenPeople,
            onOpenSuggestedFamily = onOpenSuggestedFamily,
            onOpenSettings = onOpenSettings,
        ),
    )
}

data class FriendsActions(
    val onQueryChange: (String) -> Unit = {},
    val onSortChange: (PeopleSort) -> Unit = {},
    val onSync: () -> Unit = {},
    val onImportFile: () -> Unit = {},
    val onPickContacts: () -> Unit = {},
    val onOpenFriend: (FriendId) -> Unit = {},
    val onAddFriend: (String) -> Unit = {},
    /** Asks for the contacts permission, then opens the contact list with checkboxes. */
    val onChooseContacts: () -> Unit = {},
    val onExploreSample: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    /** Filters the list by a circle (null = All). */
    val onCircleFilter: (String?) -> Unit = {},
    /** Long-press on a circle chip. */
    val onEditCircle: (String) -> Unit = {},
    val onOpenNetwork: () -> Unit = {},
    val onOpenPeople: () -> Unit = {},
    val onOpenSuggestedFamily: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

/** Localised snackbar text for a [FriendsMessage]. */
suspend fun FriendsMessage.text(): String = when (this) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FriendsContent(
    state: FriendsUiState,
    canSyncContacts: Boolean,
    actions: FriendsActions,
    canPickContacts: Boolean = false,
    addOptions: List<AddFriendOption> = listOf(AddFriendOption.TYPE_IT_IN),
) {
    val snackbar = remember { SnackbarHostState() }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    // Only "Type it in" (the web): straight to the name dialog, as before.
    val openAdd = { if (addOptions == listOf(AddFriendOption.TYPE_IT_IN)) showAdd = true else showAddSheet = true }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showMessage(it.text())
            actions.onMessageShown()
        }
    }
    val isEmpty = !state.isLoading && state.totalCount == 0

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = stringResource(Res.string.people_title),
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
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.action_add_friend)) },
                                        leadingIcon = { Icon(Icons.Rounded.PersonAdd, null) },
                                        onClick = {
                                            menuOpen = false
                                            openAdd()
                                        },
                                    )
                                    if (canPickContacts || AddFriendOption.CHOOSE_FROM_LIST in addOptions) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(Res.string.action_pick_contacts)) },
                                            leadingIcon = { Icon(Icons.Rounded.Contacts, null) },
                                            onClick = {
                                                menuOpen = false
                                                // One way in from the contacts: the list where this build has it.
                                                if (AddFriendOption.CHOOSE_FROM_LIST in
                                                    addOptions
                                                ) {
                                                    actions.onChooseContacts()
                                                } else {
                                                    actions.onPickContacts()
                                                }
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
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.suggested_family_title)) },
                                        leadingIcon = { Icon(Icons.Rounded.FamilyRestroom, null) },
                                        onClick = {
                                            menuOpen = false
                                            actions.onOpenSuggestedFamily()
                                        },
                                    )
                                    HorizontalDivider()
                                    // A–Z instead of grouped by closeness (issue #66).
                                    val az = state.sort == PeopleSort.NAME
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.sort_name)) },
                                        leadingIcon = { Icon(Icons.Rounded.SortByAlpha, null) },
                                        trailingIcon = if (az) {
                                            { Icon(Icons.Rounded.Check, null) }
                                        } else {
                                            null
                                        },
                                        modifier = Modifier.semantics { selected = az },
                                        onClick = {
                                            menuOpen = false
                                            actions.onSortChange(if (az) PeopleSort.CLOSENESS else PeopleSort.NAME)
                                        },
                                    )
                                }
                            }
                            HeaderSettingsButton(actions.onOpenSettings)
                        }
                    },
                )
            }
            // Issue #86: search right under the header, then "Your people", the circle chips and the list.
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
            item(key = "progress") {
                AnimatedVisibility(state.isSyncing) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
            }

            if (isEmpty) {
                item(key = "welcome") {
                    Welcome(
                        canSyncContacts = canSyncContacts,
                        onImport = actions.onSync,
                        onImportFile = actions.onImportFile,
                        onAdd = { showAdd = true },
                        onSample = actions.onExploreSample,
                        onPickContacts = actions.onPickContacts.takeIf { AddFriendOption.PICK_FROM_CONTACTS in addOptions },
                        onChooseContacts = actions.onChooseContacts.takeIf { AddFriendOption.CHOOSE_FROM_LIST in addOptions },
                    )
                }
                return@LazyColumn
            }

            if (!state.isSearching) {
                state.yourPeople?.let { yp ->
                    item(key = "your-people") {
                        YourPeopleCard(yp, state.showWarmth, onOpen = actions.onOpenPeople, onOpenNetwork = actions.onOpenNetwork)
                    }
                }
            }
            if (!state.isSearching && state.circle == null && state.suggestedFamily > 0) {
                item(key = "suggested-family") {
                    ListItem(
                        modifier = Modifier.clickable(onClick = actions.onOpenSuggestedFamily).padding(horizontal = 4.dp),
                        leadingContent = { Icon(Icons.Rounded.FamilyRestroom, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(stringResource(Res.string.suggested_family_title), style = MaterialTheme.typography.titleSmall) },
                        supportingContent = {
                            Text(pluralStringResource(Res.plurals.suggested_family_count, state.suggestedFamily, state.suggestedFamily))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            val kinship = state.kinship
            if (kinship != null) {
                item(key = "kinship") {
                    // "Nieces & nephews · 3" (F7): what the search understood.
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                stringResource(
                                    Res.string.kin_search_result,
                                    kinship.map { linkWord(it, null) }.joinToString(", "),
                                    state.friends.size,
                                ),
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            } else if (!state.isSearching && state.circles.isNotEmpty()) {
                // One row of filters: the circles. Closeness is how the list is grouped, not a second row of chips.
                item(key = "circles") { CircleFilterRow(state, actions) }
            }
            // Not while the first load is still running: the database answers after the first frame.
            if (!state.isLoading && state.friends.isEmpty()) {
                item(key = "nomatch") {
                    EmptyState(
                        icon = Icons.Rounded.Search,
                        title = stringResource(Res.string.no_matches_title),
                        message = if (state.kinship != null) {
                            stringResource(Res.string.kin_search_empty)
                        } else if (state.isSearching) {
                            stringResource(Res.string.no_matches_search, state.query)
                        } else {
                            stringResource(Res.string.people_empty)
                        },
                    )
                }
            }
            state.sections.forEach { (section, people) ->
                val key = when (section) {
                    is PeopleSection.Level -> "level-${section.closeness.name}"
                    is PeopleSection.Letter -> "letter-${section.letter}"
                }
                stickyHeader(key = key) {
                    Text(
                        when (section) {
                            // "Close · 9": each heading shows its count (no folding, no filter).
                            is PeopleSection.Level -> stringResource(
                                Res.string.people_level_count,
                                closenessLabel(section.closeness, short = true),
                                people.size,
                            )
                            is PeopleSection.Letter -> section.letter.toString()
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 24.dp, vertical = 6.dp)
                            .semantics { heading() },
                    )
                }
                items(people, key = { it.friend.id.value }) { summary ->
                    FriendRow(
                        summary = summary,
                        now = state.now,
                        today = state.today,
                        zone = state.zone,
                        onClick = { actions.onOpenFriend(summary.friend.id) },
                        showWarmth = state.showWarmth,
                        showCloseness = state.sort == PeopleSort.NAME,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddFriendSheet(
            options = addOptions,
            listDenied = state.contactListDenied,
            onDismiss = { showAddSheet = false },
            onOption = { option ->
                showAddSheet = false
                when (option) {
                    AddFriendOption.PICK_FROM_CONTACTS -> actions.onPickContacts()
                    AddFriendOption.CHOOSE_FROM_LIST -> actions.onChooseContacts()
                    AddFriendOption.TYPE_IT_IN -> showAdd = true
                }
            },
        )
    }

    if (showAdd) {
        AddFriendDialog(onDismiss = { showAdd = false }, onAdd = {
            actions.onAddFriend(it)
            showAdd = false
        })
    }
}

/** "All · N" + one chip per circle; the selected one shows its member count. A long-press edits the circle. */
@Composable
private fun CircleFilterRow(state: FriendsUiState, actions: FriendsActions) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") {
            AllChip(stringResource(Res.string.people_all, state.listedCount), selected = state.circle == null, onClick = {
                actions.onCircleFilter(null)
            })
        }
        items(state.circles, key = { it.group.id }) { c ->
            val selected = state.circle?.group?.id == c.group.id
            CircleChip(
                c.group,
                selected = selected,
                onClick = { actions.onCircleFilter(if (selected) null else c.group.id) },
                onLongClick = { actions.onEditCircle(c.group.id) },
                trailing = if (selected) c.members.size.toString() else null,
            )
        }
    }
}

/** "Your people" on top of the list: people, meetups in 90 days and who's cooling; opens the dashboard or the map. */
@Composable
private fun YourPeopleCard(summary: YourPeopleSummary, showWarmth: Boolean, onOpen: () -> Unit, onOpenNetwork: () -> Unit) {
    SectionCard(
        title = stringResource(Res.string.action_people),
        icon = Icons.Rounded.Insights,
        action = { TextButton(onClick = onOpen) { Text(stringResource(Res.string.people_open)) } },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(summary.people, pluralStringResource(Res.plurals.people_card_count, summary.people), onOpen, Modifier.weight(1f))
            StatTile(
                summary.meetupsLast90Days,
                pluralStringResource(Res.plurals.people_card_meetups, summary.meetupsLast90Days),
                onOpen,
                Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val cooling = summary.cooling.takeIf { showWarmth }.orEmpty()
            Text(
                if (cooling.isEmpty()) {
                    ""
                } else {
                    pluralStringResource(
                        Res.plurals.people_card_cooling,
                        cooling.size,
                        cooling.size,
                        cooling.joinToString(", ") { it.displayName.substringBefore(' ') },
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenNetwork) {
                Icon(Icons.Rounded.Hub, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.action_network))
            }
        }
    }
}

@Composable
private fun StatTile(value: Int, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    onChooseContacts: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyState(
            icon = Icons.Rounded.Contacts,
            title = stringResource(Res.string.welcome_title),
            message = stringResource(
                when {
                    onChooseContacts != null -> Res.string.choose_only_ticked
                    canSyncContacts -> Res.string.welcome_message_contacts
                    else -> Res.string.welcome_message_manual
                },
            ),
        )
        if (onChooseContacts != null) {
            // iOS and the Android sideload build (decision D2, owner decision 2026-11-14): one way in, the contacts with
            // checkboxes; a refused permission falls back to the system picker.
            Button(onClick = onChooseContacts, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.Contacts, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.choose_pick_instead))
            }
        } else if (canSyncContacts) {
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

/** "Add friend": one way in from the contacts (the list with checkboxes, or the system picker) and typing a name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFriendSheet(options: List<AddFriendOption>, listDenied: Boolean, onDismiss: () -> Unit, onOption: (AddFriendOption) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                stringResource(Res.string.choose_add_none),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (listDenied && AddFriendOption.CHOOSE_FROM_LIST in options) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(Res.string.choose_empty_body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            options.forEach { option ->
                val (icon, title, body) = when (option) {
                    AddFriendOption.PICK_FROM_CONTACTS -> Triple(
                        Icons.Rounded.Contacts,
                        Res.string.choose_pick_instead,
                        Res.string.add_option_pick_body,
                    )
                    AddFriendOption.CHOOSE_FROM_LIST -> Triple(
                        Icons.Rounded.Contacts,
                        Res.string.choose_pick_instead,
                        if (listDenied) Res.string.add_option_list_again else Res.string.add_option_list_body,
                    )
                    AddFriendOption.TYPE_IT_IN -> Triple(Icons.Rounded.Edit, Res.string.add_option_type, Res.string.add_option_type_body)
                }
                ListItem(
                    leadingContent = {
                        Box(
                            Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                    },
                    headlineContent = { Text(stringResource(title)) },
                    supportingContent = { Text(stringResource(body)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onOption(option) }.padding(horizontal = 8.dp),
                )
            }
        }
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
