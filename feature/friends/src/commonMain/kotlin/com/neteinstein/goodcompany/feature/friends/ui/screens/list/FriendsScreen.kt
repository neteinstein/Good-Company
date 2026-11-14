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
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.CircleChip
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.circlePalette
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.shortLabelText
import com.neteinstein.goodcompany.core.ui.l10n.everyNDays
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
import com.neteinstein.goodcompany.feature.friends.resources.circle_all
import com.neteinstein.goodcompany.feature.friends.resources.circle_az
import com.neteinstein.goodcompany.feature.friends.resources.circle_goal
import com.neteinstein.goodcompany.feature.friends.resources.circle_last_together
import com.neteinstein.goodcompany.feature.friends.resources.circle_log_meetup
import com.neteinstein.goodcompany.feature.friends.resources.circle_never_together
import com.neteinstein.goodcompany.feature.friends.resources.filter_everyone
import com.neteinstein.goodcompany.feature.friends.resources.filter_real_friends
import com.neteinstein.goodcompany.feature.friends.resources.greeting_afternoon
import com.neteinstein.goodcompany.feature.friends.resources.greeting_evening
import com.neteinstein.goodcompany.feature.friends.resources.greeting_morning
import com.neteinstein.goodcompany.feature.friends.resources.home_quiet_week
import com.neteinstein.goodcompany.feature.friends.resources.home_title
import com.neteinstein.goodcompany.feature.friends.resources.kin_search_empty
import com.neteinstein.goodcompany.feature.friends.resources.kin_search_result
import com.neteinstein.goodcompany.feature.friends.resources.msg_added
import com.neteinstein.goodcompany.feature.friends.resources.msg_call_logged
import com.neteinstein.goodcompany.feature.friends.resources.msg_contacts_denied
import com.neteinstein.goodcompany.feature.friends.resources.msg_picked
import com.neteinstein.goodcompany.feature.friends.resources.msg_sample_added
import com.neteinstein.goodcompany.feature.friends.resources.msg_snoozed
import com.neteinstein.goodcompany.feature.friends.resources.msg_sync_failed
import com.neteinstein.goodcompany.feature.friends.resources.msg_synced
import com.neteinstein.goodcompany.feature.friends.resources.msg_synced_calls
import com.neteinstein.goodcompany.feature.friends.resources.msg_vcf_empty
import com.neteinstein.goodcompany.feature.friends.resources.msg_vcf_result
import com.neteinstein.goodcompany.feature.friends.resources.no_matches_close
import com.neteinstein.goodcompany.feature.friends.resources.no_matches_search
import com.neteinstein.goodcompany.feature.friends.resources.no_matches_title
import com.neteinstein.goodcompany.feature.friends.resources.search_placeholder
import com.neteinstein.goodcompany.feature.friends.resources.section_before_you_meet
import com.neteinstein.goodcompany.feature.friends.resources.section_catch_up
import com.neteinstein.goodcompany.feature.friends.resources.section_coming_up
import com.neteinstein.goodcompany.feature.friends.resources.section_drifting
import com.neteinstein.goodcompany.feature.friends.resources.section_drifting_hint
import com.neteinstein.goodcompany.feature.friends.resources.section_real_friends
import com.neteinstein.goodcompany.feature.friends.resources.sort_by
import com.neteinstein.goodcompany.feature.friends.resources.sort_name
import com.neteinstein.goodcompany.feature.friends.resources.sort_warmth
import com.neteinstein.goodcompany.feature.friends.resources.suggested_family_count
import com.neteinstein.goodcompany.feature.friends.resources.suggested_family_title
import com.neteinstein.goodcompany.feature.friends.resources.week_big_days
import com.neteinstein.goodcompany.feature.friends.resources.week_birthdays
import com.neteinstein.goodcompany.feature.friends.resources.week_catch_up
import com.neteinstein.goodcompany.feature.friends.resources.week_suffix
import com.neteinstein.goodcompany.feature.friends.resources.welcome_add_someone
import com.neteinstein.goodcompany.feature.friends.resources.welcome_import_contacts
import com.neteinstein.goodcompany.feature.friends.resources.welcome_import_vcf
import com.neteinstein.goodcompany.feature.friends.resources.welcome_message_contacts
import com.neteinstein.goodcompany.feature.friends.resources.welcome_message_manual
import com.neteinstein.goodcompany.feature.friends.resources.welcome_pick_contacts
import com.neteinstein.goodcompany.feature.friends.resources.welcome_sample
import com.neteinstein.goodcompany.feature.friends.resources.welcome_title
import com.neteinstein.goodcompany.feature.friends.ui.components.CatchUpCard
import com.neteinstein.goodcompany.feature.friends.ui.components.DriftingCard
import com.neteinstein.goodcompany.feature.friends.ui.components.FriendRow
import com.neteinstein.goodcompany.feature.friends.ui.components.PersonChip
import com.neteinstein.goodcompany.feature.friends.ui.components.StoryBubbleChip
import com.neteinstein.goodcompany.feature.friends.ui.components.UpcomingCard
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
    onOpenStory: (FriendId) -> Unit = {},
    onEditCircle: (String) -> Unit = {},
    onLogCircle: (String) -> Unit = {},
    /** "We met" on a catch-up or drifting card: opens the small "We met" panel (issue #47). */
    onWeMet: (FriendId) -> Unit = {},
    /** Opens the "Choose friends" list (contacts with checkboxes). */
    onChooseContacts: () -> Unit = {},
    /** How many people the "Choose friends" list just added (0 = nothing to report); [onChosenShown] clears it. */
    chosenContacts: Int = 0,
    onChosenShown: () -> Unit = {},
    /** "Your network" and "Your people" (issue #46), from the menu. */
    onOpenNetwork: () -> Unit = {},
    onOpenPeople: () -> Unit = {},
    /** "Suggested family" (F7). */
    onOpenSuggestedFamily: () -> Unit = {},
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
            onFilterChange = viewModel::onFilterChange,
            onSortChange = viewModel::onSortChange,
            onSync = requestContacts,
            onImportFile = pickVcf,
            onPickContacts = if (capabilities.hasContactWriteBack) requestThenPick else pickContacts,
            onOpenFriend = onOpenFriend,
            onOpenStory = onOpenStory,
            onToggleClose = viewModel::onToggleClose,
            onMetToday = onWeMet,
            onCalled = viewModel::onCalled,
            onSnooze = viewModel::onSnooze,
            onAddFriend = viewModel::onAddFriend,
            onChooseContacts = requestContactList,
            onExploreSample = viewModel::onExploreSample,
            onMessageShown = viewModel::onMessageShown,
            onCircleFilter = viewModel::onCircleFilter,
            onEditCircle = onEditCircle,
            onLogCircle = onLogCircle,
            onOpenNetwork = onOpenNetwork,
            onOpenPeople = onOpenPeople,
            onOpenSuggestedFamily = onOpenSuggestedFamily,
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
    val onOpenStory: (FriendId) -> Unit = {},
    val onToggleClose: (FriendId) -> Unit = {},
    val onMetToday: (FriendId) -> Unit = {},
    val onCalled: (FriendId) -> Unit = {},
    val onSnooze: (FriendId, SnoozeOption) -> Unit = { _, _ -> },
    val onAddFriend: (String) -> Unit = {},
    /** Asks for the contacts permission, then opens the contact list with checkboxes. */
    val onChooseContacts: () -> Unit = {},
    val onExploreSample: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    /** Filters Home by a circle (null = All). */
    val onCircleFilter: (String?) -> Unit = {},
    /** Long-press on a circle chip. */
    val onEditCircle: (String) -> Unit = {},
    /** "Log a <circle> meetup" on the circle card. */
    val onLogCircle: (String) -> Unit = {},
    val onOpenNetwork: () -> Unit = {},
    val onOpenPeople: () -> Unit = {},
    val onOpenSuggestedFamily: () -> Unit = {},
)

/** Localised snackbar text for a [FriendsMessage]. */
suspend fun FriendsMessage.text(): String = when (this) {
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
                                        text = { Text(stringResource(Res.string.action_network)) },
                                        leadingIcon = { Icon(Icons.Rounded.Hub, null) },
                                        onClick = {
                                            menuOpen = false
                                            actions.onOpenNetwork()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.action_people)) },
                                        leadingIcon = { Icon(Icons.Rounded.Insights, null) },
                                        onClick = {
                                            menuOpen = false
                                            actions.onOpenPeople()
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
                                }
                            }
                        }
                    },
                )
            }
            if (!isEmpty && state.circles.isNotEmpty()) {
                item(key = "circles") { CircleFilterRow(state, actions) }
                state.circle?.let { c -> item(key = "circle-card") { CircleCard(c, state, actions) } }
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

            if (!state.isSearching && state.stories.isNotEmpty()) {
                item(key = "stories-h") { SectionHeader(stringResource(Res.string.section_before_you_meet)) }
                item(key = "stories") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.stories, key = { "s-${it.friend.id.value}" }) { b ->
                            StoryBubbleChip(b, state.today, { actions.onOpenStory(b.friend.id) }, Modifier.animateItem())
                        }
                    }
                }
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
            item(key = "filters") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val circle = state.circle
                    val kinship = state.kinship
                    if (kinship != null) {
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
                        )
                    } else if (circle != null) {
                        // A circle shows all its members, whatever their closeness.
                        Text(
                            stringResource(Res.string.circle_az, state.friends.size, circle.group.name).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
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
                    }
                    Spacer(Modifier.weight(1f))
                    SortMenu(state.sort, actions.onSortChange)
                }
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

/** "All" + one chip per circle; the selected one shows its member count. A long-press edits the circle. */
@Composable
private fun CircleFilterRow(state: FriendsUiState, actions: FriendsActions) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") {
            FilterChip(state.circle == null, { actions.onCircleFilter(null) }, label = { Text(stringResource(Res.string.circle_all)) })
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

/** The selected circle: last time together, the reminder goal and "Log a <circle> meetup". */
@Composable
private fun CircleCard(status: Relationships.GroupStatus, state: FriendsUiState, actions: FriendsActions) {
    val g = status.group
    val palette = circlePalette(g.color)
    Surface(
        color = palette.container,
        contentColor = palette.onContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(g.emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(g.name, style = MaterialTheme.typography.titleMedium)
                    val last = status.lastTogether?.let { stringResource(Res.string.circle_last_together, relativePast(it, state.now, state.zone)) }
                        ?: stringResource(Res.string.circle_never_together)
                    val goal = g.meetEveryDays?.let { stringResource(Res.string.circle_goal, everyNDays(it)) }
                    Text(listOfNotNull(last, goal).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { actions.onLogCircle(g.id) },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(Res.string.circle_log_meetup, g.name)) }
        }
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
