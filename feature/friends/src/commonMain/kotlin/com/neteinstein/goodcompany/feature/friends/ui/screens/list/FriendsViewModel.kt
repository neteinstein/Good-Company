package com.neteinstein.goodcompany.feature.friends.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.friendIds
import com.neteinstein.goodcompany.core.domain.usecase.AddFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ContactsFileImportResult
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.domain.usecase.ImportContactsFileUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportPickedContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogCallUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveAgendaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveGroupsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMeetupStatusUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveStoryBubblesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.PickedContactsResult
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.SetHomeCircleFilterUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SetRealFriendUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeCatchUpUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.domain.usecase.StoryBubble
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.Warmth
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

enum class FriendFilter { CLOSE, ALL }

enum class FriendSort { NAME, WARMTH }

enum class Greeting { MORNING, AFTERNOON, EVENING }

/** What's happening this week, for the header subtitle. */
data class WeekSummary(val birthdays: Int, val bigDays: Int, val catchUps: Int) {
    val isEmpty get() = birthdays == 0 && bigDays == 0 && catchUps == 0
}

/** The ways "Add friend" offers to add someone, in the order the sheet shows them. */
enum class AddFriendOption { PICK_FROM_CONTACTS, CHOOSE_FROM_LIST, TYPE_IT_IN }

/**
 * What "Add friend" offers on this build: the system contact picker where there is one, the contact list with
 * checkboxes only where the app may read contacts (iOS, Android sideload; decision D2), and typing a name everywhere.
 */
fun addFriendOptions(hasContactPicker: Boolean, hasContactList: Boolean): List<AddFriendOption> = buildList {
    if (hasContactPicker) add(AddFriendOption.PICK_FROM_CONTACTS)
    if (hasContactList) add(AddFriendOption.CHOOSE_FROM_LIST)
    add(AddFriendOption.TYPE_IT_IN)
}

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface FriendsMessage {
    data class LoggedWith(val name: String?) : FriendsMessage

    data class CallLogged(val name: String) : FriendsMessage

    data class Snoozed(val name: String, val until: LocalDate) : FriendsMessage

    data object ContactsDenied : FriendsMessage

    data class Synced(val contacts: Int, val calls: Int) : FriendsMessage

    data object SyncFailed : FriendsMessage

    data class Added(val name: String) : FriendsMessage

    data object SampleAdded : FriendsMessage

    data class FileImported(val result: ContactsFileImportResult) : FriendsMessage

    data class ContactsPicked(val result: PickedContactsResult) : FriendsMessage
}

data class FriendsUiState(
    val friends: List<FriendSummary> = emptyList(),
    /** [friends] grouped by first letter for the A–Z list (one untitled section when sorted by warmth). */
    val sections: List<Pair<Char?, List<FriendSummary>>> = emptyList(),
    val closeFriends: List<Friend> = emptyList(),
    /** Agenda items in the next two weeks — the home "Coming up" carousel. */
    val upcoming: List<AgendaItem> = emptyList(),
    /** "Before you meet": friends you're seeing today or tomorrow, each with a catch-up story. */
    val stories: List<StoryBubble> = emptyList(),
    val catchUp: List<Relationships.MeetupStatus> = emptyList(),
    /** Real friends whose warmth is fading and who aren't already in [catchUp]. */
    val drifting: List<FriendSummary> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val filter: FriendFilter = FriendFilter.CLOSE,
    val sort: FriendSort = FriendSort.NAME,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val greeting: Greeting = Greeting.MORNING,
    val week: WeekSummary = WeekSummary(0, 0, 0),
    val message: FriendsMessage? = null,
    /** Warmth dots and "Drifting apart" (off in Settings = softer view). */
    val showWarmth: Boolean = true,
    val now: Instant = Instant.DISTANT_PAST,
    val today: LocalDate = LocalDate(2000, 1, 1),
    val zone: TimeZone = TimeZone.UTC,
    /** Every circle, A–Z, for the chip row under the header. */
    val circles: List<Relationships.GroupStatus> = emptyList(),
    /** The circle Home is filtered by (remembered between launches); null = All. Every section shows only its members. */
    val circle: Relationships.GroupStatus? = null,
    /** "Choose from the full list" was refused the contacts permission: the add sheet explains and keeps the picker. */
    val contactListDenied: Boolean = false,
) {
    val isSearching: Boolean get() = query.isNotBlank()
}

class FriendsViewModel(
    observeFriends: ObserveFriendsUseCase,
    observeAgenda: ObserveAgendaUseCase,
    observeMeetups: ObserveMeetupStatusUseCase,
    observeSettings: ObserveSettingsUseCase,
    observeStories: ObserveStoryBubblesUseCase,
    observeCircles: ObserveGroupsUseCase,
    private val importPickedContacts: ImportPickedContactsUseCase,
    private val syncContacts: SyncContactsUseCase,
    private val importContactsFile: ImportContactsFileUseCase,
    private val addFriend: AddFriendUseCase,
    private val setRealFriend: SetRealFriendUseCase,
    private val checkIn: CheckInUseCase,
    private val logCall: LogCallUseCase,
    private val snooze: SnoozeCatchUpUseCase,
    private val loadSampleData: LoadSampleDataUseCase,
    private val setCircleFilter: SetHomeCircleFilterUseCase,
    private val clock: AppClock,
) : ViewModel() {
    private val local = MutableStateFlow(FriendsUiState())

    val state: StateFlow<FriendsUiState> = combine(
        observeFriends(),
        observeAgenda(),
        observeMeetups(),
        combine(observeSettings(), observeStories(), observeCircles()) { settings, stories, circles -> Triple(settings, stories, circles) },
        local,
    ) { everyone, fullAgenda, allMeetups, (settings, allStories, circles), ui ->
        val today = clock.today()
        val q = ui.query.trim().lowercase()
        // A deleted circle (or one from another device's backup) reads as All.
        val circle = circles.firstOrNull { it.group.id == settings.homeCircleFilter }
        val members = circle?.group?.memberIds?.toSet()
        fun FriendId.inCircle() = members == null || this in members
        val all = everyone.filter { it.friend.id.inCircle() }
        val agenda = fullAgenda.filter { item ->
            members == null || if (item is AgendaItem.GroupOverdue) item.group.id == circle?.group?.id else item.friendIds.any { it in members }
        }
        val meetups = allMeetups.filter { it.friend.id.inCircle() }
        val stories = allStories.filter { it.friend.id.inCircle() }
        // First run: nobody marked as close yet → show everyone instead of an empty list. A circle shows all its members.
        val filter = if (ui.filter == FriendFilter.CLOSE && everyone.none { it.friend.isCloseFriend }) FriendFilter.ALL else ui.filter
        val matching = all
            .filter { q.isNotEmpty() || circle != null || filter == FriendFilter.ALL || it.friend.isCloseFriend }
            .filter { q.isEmpty() || it.friend.name.lowercase().contains(q) || it.friend.likes.any { l -> l.lowercase().contains(q) } }
        val visible = when (ui.sort) {
            FriendSort.NAME -> matching.sortedBy { it.friend.name.lowercase() }
            FriendSort.WARMTH -> matching.sortedWith(compareBy({ it.warmth.value }, { it.friend.name.lowercase() }))
        }
        val catchUp = meetups.filter { it.isOverdue(today) }
        val catchUpIds = catchUp.map { it.friend.id }.toSet()
        val upcoming = agenda.filter { it !is AgendaItem.MeetupOverdue && it.date <= today.plus(UPCOMING_DAYS, DateTimeUnit.DAY) }
        val week = upcoming.filter { it.date <= today.plus(6, DateTimeUnit.DAY) }
        val hour = clock.now().toLocalDateTime(clock.timeZone).hour
        ui.copy(
            friends = visible,
            sections = when (ui.sort) {
                FriendSort.NAME -> visible.groupBy<FriendSummary, Char?> {
                    it.friend.name.first().uppercaseChar().takeIf(Char::isLetter) ?: '#'
                }.toList()
                FriendSort.WARMTH -> if (visible.isEmpty()) emptyList() else listOf(null to visible)
            },
            closeFriends = all.filter { it.friend.isCloseFriend }.map { it.friend },
            upcoming = upcoming.take(8),
            stories = stories,
            catchUp = catchUp,
            drifting = all
                .filter { settings.showWarmth }
                .filter { it.friend.isCloseFriend && !it.friend.catchUpPaused }
                .filter { it.warmth.level == Warmth.Level.FADING && it.friend.id !in catchUpIds }
                .filter { s -> s.friend.snoozedUntil?.let { it < today } ?: true }
                .sortedBy { it.warmth.value }
                .take(3),
            totalCount = everyone.size,
            circles = circles.sortedBy { it.group.name.lowercase() },
            circle = circle,
            showWarmth = settings.showWarmth,
            isLoading = false,
            filter = filter,
            greeting = when (hour) {
                in 5..11 -> Greeting.MORNING
                in 12..17 -> Greeting.AFTERNOON
                else -> Greeting.EVENING
            },
            week = WeekSummary(
                birthdays = week.count { it is AgendaItem.FriendBirthday || it is AgendaItem.KidBirthday || it is AgendaItem.RelativeBirthday },
                bigDays = week.count { it is AgendaItem.FriendEvent },
                catchUps = catchUp.size,
            ),
            now = clock.now(),
            today = today,
            zone = clock.timeZone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FriendsUiState())

    fun onQueryChange(query: String) = local.update { it.copy(query = query) }

    fun onFilterChange(filter: FriendFilter) = local.update { it.copy(filter = filter) }

    fun onSortChange(sort: FriendSort) = local.update { it.copy(sort = sort) }

    /** Filters every Home section by a circle (null = All); remembered between launches. */
    fun onCircleFilter(groupId: String?) {
        viewModelScope.launch { setCircleFilter(groupId) }
    }

    fun onToggleClose(id: FriendId) {
        viewModelScope.launch { setRealFriend.toggle(id) }
    }

    private fun firstName(id: FriendId): String? = state.value.friends.firstOrNull { it.friend.id == id }?.friend?.name?.substringBefore(' ')
        ?: state.value.catchUp.firstOrNull { it.friend.id == id }?.friend?.name?.substringBefore(' ')
        ?: state.value.drifting.firstOrNull { it.friend.id == id }?.friend?.name?.substringBefore(' ')

    /** "We met" straight from the catch-up card. */
    fun onMetToday(id: FriendId) {
        viewModelScope.launch {
            checkIn(listOf(id))
            local.update { it.copy(message = FriendsMessage.LoggedWith(firstName(id))) }
        }
    }

    fun onCalled(id: FriendId) {
        viewModelScope.launch {
            logCall(id)
            local.update { it.copy(message = FriendsMessage.CallLogged(firstName(id).orEmpty())) }
        }
    }

    fun onSnooze(id: FriendId, option: SnoozeOption) {
        viewModelScope.launch {
            snooze.friend(id, option)
            local.update { it.copy(message = FriendsMessage.Snoozed(firstName(id).orEmpty(), option.until(clock.today()))) }
        }
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

    /** "Choose from the full list" asked for the contacts permission. The screen opens the list when [granted]. */
    fun onContactListPermissionResult(granted: Boolean) = local.update { it.copy(contactListDenied = !granted) }

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
        const val UPCOMING_DAYS = 14
    }
}
