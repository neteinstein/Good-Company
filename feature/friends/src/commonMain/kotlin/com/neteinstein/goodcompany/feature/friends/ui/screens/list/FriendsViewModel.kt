package com.neteinstein.goodcompany.feature.friends.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.usecase.AddFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ContactsFileImportResult
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.domain.usecase.ImportContactsFileUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportPickedContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveKinshipProposalsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObservePeopleDashboardUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ParseKinshipQueryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PickedContactsResult
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.SetHomeCircleFilterUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetRealFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/** How the People list is ordered: grouped by closeness (the default, F5) or A–Z from the ⋮ menu. */
enum class PeopleSort { CLOSENESS, NAME }

/** The closeness chips above the list: everyone, or one level ([closeness] null = "not set"). */
sealed interface PeopleFilter {
    data object All : PeopleFilter

    data class Level(val closeness: Closeness?) : PeopleFilter
}

/** A heading in the People list: a closeness level (null = not set) or, sorted A–Z, a first letter. */
sealed interface PeopleSection {
    data class Level(val closeness: Closeness?) : PeopleSection

    data class Letter(val letter: Char) : PeopleSection
}

/** One closeness chip: the level (null = not set) and how many people it holds. */
data class LevelCount(val closeness: Closeness?, val count: Int)

/** The "Your people" card on top of People (issue #66): a summary of the dashboard. */
data class YourPeopleSummary(val people: Int, val meetupsLast90Days: Int, val cooling: List<Friend>)

/**
 * The ways "Add friend" offers to add someone, in the order the sheet shows them. Both contact options are titled
 * "Pick from contacts": [PICK_FROM_CONTACTS] opens the system contact picker, [CHOOSE_FROM_LIST] the contact list with
 * checkboxes (asking for the contacts permission first).
 */
enum class AddFriendOption { PICK_FROM_CONTACTS, CHOOSE_FROM_LIST, TYPE_IT_IN }

/**
 * What "Add friend" offers on this build, with one way in from the phone's contacts (owner decision 2026-11-14):
 * the contact list with checkboxes where the app may read contacts (iOS, Android sideload; decision D2), otherwise the
 * system contact picker where there is one (Android Play build), and typing a name everywhere. Where the list is
 * offered, a refused permission falls back to the picker (see [FriendsUiState.pickerFallback]).
 */
fun addFriendOptions(hasContactPicker: Boolean, hasContactList: Boolean): List<AddFriendOption> = buildList {
    when {
        hasContactList -> add(AddFriendOption.CHOOSE_FROM_LIST)
        hasContactPicker -> add(AddFriendOption.PICK_FROM_CONTACTS)
    }
    add(AddFriendOption.TYPE_IT_IN)
}

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface FriendsMessage {
    data object ContactsDenied : FriendsMessage

    data class Synced(val contacts: Int, val calls: Int) : FriendsMessage

    data object SyncFailed : FriendsMessage

    data class Added(val name: String) : FriendsMessage

    data object SampleAdded : FriendsMessage

    data class FileImported(val result: ContactsFileImportResult) : FriendsMessage

    data class ContactsPicked(val result: PickedContactsResult) : FriendsMessage
}

data class FriendsUiState(
    /** Who the list shows, in order (every section's people, one after another). */
    val friends: List<FriendSummary> = emptyList(),
    /** [friends] under their headings: by closeness (Inner first, "not set" last), or by first letter when sorted A–Z. */
    val sections: List<Pair<PeopleSection, List<FriendSummary>>> = emptyList(),
    /** The closeness chips, in order, with how many people each holds (only levels somebody is in). */
    val levels: List<LevelCount> = emptyList(),
    /** How many people the "All" chip counts (the list before a level is picked). */
    val listedCount: Int = 0,
    /** "Your people" on top; null until the first numbers arrive. */
    val yourPeople: YourPeopleSummary? = null,
    val totalCount: Int = 0,
    val query: String = "",
    val filter: PeopleFilter = PeopleFilter.All,
    val sort: PeopleSort = PeopleSort.CLOSENESS,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val message: FriendsMessage? = null,
    /** Warmth dots and "who's cooling" (off in Settings = softer view). */
    val showWarmth: Boolean = true,
    val now: Instant = Instant.DISTANT_PAST,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
    /** Every circle, A–Z, for the chip row under the header. */
    val circles: List<Relationships.GroupStatus> = emptyList(),
    /** The circle People is filtered by (remembered between launches); null = All. The list shows only its members. */
    val circle: Relationships.GroupStatus? = null,
    /** "Pick from contacts" was refused the contacts permission: the add sheet explains it. */
    val contactListDenied: Boolean = false,
    /** The contact list was refused just now, so the screen opens the system contact picker instead, once. */
    val pickerFallback: Boolean = false,
    /** The search is a kinship one ("my nieces and nephews", F7): the relations asked for; null = a name search. */
    val kinship: Set<LinkType>? = null,
    /** How many people "Suggested family" proposes kinship for (F7). */
    val suggestedFamily: Int = 0,
) {
    val isSearching: Boolean get() = query.isNotBlank()
}

/**
 * The People tab (issue #66, F5): one list of everyone grouped by closeness (or A–Z), closeness chips with counts, the
 * circle filter, and "Your people" on top.
 */
class FriendsViewModel(
    observeFriends: ObserveFriendsUseCase,
    observeSettings: ObserveSettingsUseCase,
    observeCircles: ObserveGroupsUseCase,
    observeDashboard: ObservePeopleDashboardUseCase,
    private val importPickedContacts: ImportPickedContactsUseCase,
    private val syncContacts: SyncContactsUseCase,
    private val importContactsFile: ImportContactsFileUseCase,
    private val addFriend: AddFriendUseCase,
    private val setRealFriend: SetRealFriendUseCase,
    private val loadSampleData: LoadSampleDataUseCase,
    private val setCircleFilter: SetHomeCircleFilterUseCase,
    observeKinship: ObserveKinshipProposalsUseCase,
    private val parseKinship: ParseKinshipQueryUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(FriendsUiState())

    /** The current query with the relations it asks for (F7), worked out off the UI language's words. */
    private val kinshipQuery = local.map { it.query }.distinctUntilChanged().map { it to parseKinship(it) }

    private data class Extras(
        val settings: AppSettings,
        val circles: List<Relationships.GroupStatus>,
        val proposals: Int,
        val kinship: Pair<String, Set<LinkType>?>,
    )

    val state: StateFlow<FriendsUiState> = combine(
        observeFriends(),
        // The card only needs counts and who's cooling, not the 13-week trend.
        observeDashboard(withTrend = false).map { d -> YourPeopleSummary(d.people, d.meetupsLast90Days, d.cooling.map { it.friend }) },
        combine(observeSettings(), observeCircles(), observeKinship(), kinshipQuery) { settings, circles, proposals, kin ->
            Extras(settings, circles, proposals.size, kin)
        },
        local,
    ) { everyone, yourPeople, (settings, circles, proposals, kin), ui ->
        val q = ui.query.trim().lowercase()
        // A deleted circle (or one from another device's backup) reads as All.
        val circle = circles.firstOrNull { it.group.id == settings.homeCircleFilter }
        val members = circle?.group?.memberIds?.toSet()
        val all = everyone.filter { members == null || it.friend.id in members }
        // "My nieces": confirmed kinship only, whoever is hidden from the list (F7).
        val kinship = kin.second.takeIf { kin.first == ui.query }
        val searching = kinship != null || q.isNotEmpty()
        val listed = when {
            kinship != null -> all.filter { it.friend.relationToMe in kinship }
            // A search looks across everyone, whatever the closeness chip.
            q.isNotEmpty() -> all.filter { it.friend.displayName.lowercase().contains(q) || it.friend.likes.any { l -> l.lowercase().contains(q) } }
            // F9: people with "Show in my list" off (friends' kids) stay out unless searched for or in a circle.
            else -> all.filter { circle != null || it.friend.showInMyList }
        }
        val levels = levelOrder.mapNotNull { c -> listed.count { it.friend.closeness == c }.takeIf { it > 0 }?.let { LevelCount(c, it) } }
        // A level nobody is in any more (e.g. after a change of closeness) reads as All.
        val filter = ui.filter.takeIf { f -> f is PeopleFilter.Level && levels.any { it.closeness == f.closeness } } ?: PeopleFilter.All
        val shown = if (!searching && filter is PeopleFilter.Level) listed.filter { it.friend.closeness == filter.closeness } else listed
        val byName = shown.sortedBy { it.friend.displayName.lowercase() }
        val sections: List<Pair<PeopleSection, List<FriendSummary>>> = when (ui.sort) {
            PeopleSort.CLOSENESS -> levelOrder.mapNotNull { c ->
                byName.filter { it.friend.closeness == c }.takeIf { it.isNotEmpty() }?.let { PeopleSection.Level(c) to it }
            }
            PeopleSort.NAME -> byName.groupBy { it.friend.displayName.firstOrNull()?.uppercaseChar()?.takeIf(Char::isLetter) ?: '#' }
                .map { (letter, people) -> PeopleSection.Letter(letter) to people }
        }
        ui.copy(
            friends = sections.flatMap { it.second },
            sections = sections,
            levels = levels,
            listedCount = listed.size,
            yourPeople = yourPeople,
            totalCount = everyone.size,
            circles = circles.sortedBy { it.group.name.lowercase() },
            circle = circle,
            showWarmth = settings.showWarmth,
            kinship = kinship,
            suggestedFamily = proposals,
            isLoading = false,
            filter = filter,
            now = clock.now(),
            today = clock.today(),
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsUiState())

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    /** A closeness chip (or All). */
    fun onFilterChange(filter: PeopleFilter) = local.update { it.copy(filter = filter) }

    /** "A–Z" in the ⋮ menu switches between A–Z and grouped by closeness. */
    fun onSortChange(sort: PeopleSort) = local.update { it.copy(sort = sort) }

    /** Filters the list by a circle (null = All); remembered between launches. */
    fun onCircleFilter(groupId: String?) {
        viewModelScope.launch { setCircleFilter(groupId) }
    }

    fun onToggleClose(id: FriendId) {
        viewModelScope.launch { setRealFriend.toggle(id) }
    }

    /** Called after the contacts permission prompt resolves. */
    fun onContactsPermissionResult(granted: Boolean) {
        if (!granted) {
            local.update { it.copy(message = FriendsMessage.ContactsDenied) }
            return
        }
        viewModelScope.launch {
            local.update { it.copy(isSyncing = true) }
            val result = runCatching { syncContacts() }
            local.update {
                it.copy(
                    isSyncing = false,
                    message = result.fold({ r -> FriendsMessage.Synced(r.contacts, r.logEntries) }, { FriendsMessage.SyncFailed }),
                )
            }
        }
    }

    /**
     * "Pick from contacts" asked for the contacts permission. The screen opens the list when [granted]; when refused,
     * it opens the system contact picker instead ([FriendsUiState.pickerFallback], cleared by [onPickerFallbackShown]).
     */
    fun onContactListPermissionResult(granted: Boolean) = local.update { it.copy(contactListDenied = !granted, pickerFallback = !granted) }

    /** The screen opened the system contact picker after a refused contact list. */
    fun onPickerFallbackShown() = local.update { it.copy(pickerFallback = false) }

    /** People were saved from the contact list. */
    fun onContactsChosen(added: Int) {
        if (added > 0) local.update { it.copy(message = FriendsMessage.ContactsPicked(PickedContactsResult(added, 0))) }
    }

    /** A .vcf file picked by the user (the address-book path on the web). */
    fun onContactsFilePicked(text: String?) {
        if (text.isNullOrBlank()) return
        viewModelScope.launch {
            local.update { it.copy(isSyncing = true) }
            val result = importContactsFile(text)
            local.update { it.copy(isSyncing = false, message = FriendsMessage.FileImported(result)) }
        }
    }

    /** People chosen in the system contact picker (no contacts permission involved). */
    fun onContactsPicked(records: List<ContactRecord>) {
        if (records.isEmpty()) return
        viewModelScope.launch {
            val result = importPickedContacts(records)
            local.update { it.copy(message = FriendsMessage.ContactsPicked(result)) }
        }
    }

    fun onAddFriend(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            addFriend(name)
            local.update { it.copy(message = FriendsMessage.Added(name.trim())) }
        }
    }

    fun onExploreSample() {
        viewModelScope.launch {
            loadSampleData()
            local.update { it.copy(message = FriendsMessage.SampleAdded) }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    private companion object {
        /** Closeness levels from closest to furthest, then "not set" (null). */
        val levelOrder: List<Closeness?> = Closeness.entries + null
    }
}
