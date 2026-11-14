package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** One person in the app-icon long-press menu. */
data class ShortcutPerson(val id: FriendId, val name: String, val initials: String, val photo: ContactPhoto?) {
    val firstName: String get() = name.substringBefore(' ')
}

/**
 * What the app-icon long-press menu offers besides "Log a meetup": the people you were in touch with most recently.
 * With [hideNames] (app lock or private notifications) [people] is empty, so no name or photo shows on the home screen.
 */
data class ShortcutSnapshot(val people: List<ShortcutPerson>, val hideNames: Boolean)

object Shortcuts {
    const val MAX_PEOPLE = 3

    /** The [MAX_PEOPLE] most recent people by last contact (meetups and interactions), ties by name; orphans left out. */
    fun snapshot(
        friends: List<Friend>,
        hangouts: List<Hangout>,
        interactions: List<Interaction>,
        hideNames: Boolean,
    ): ShortcutSnapshot {
        if (hideNames) return ShortcutSnapshot(emptyList(), hideNames = true)
        val last = Relationships.lastContact(hangouts, interactions)
        val people = friends
            .filter { !it.isOrphaned && it.id in last }
            .sortedWith(compareByDescending<Friend> { last.getValue(it.id) }.thenBy { it.displayName.lowercase() })
            .take(MAX_PEOPLE)
            .map { ShortcutPerson(it.id, it.displayName, it.initials, it.avatar) }
        return ShortcutSnapshot(people, hideNames = false)
    }
}

/** Feeds the Android dynamic shortcuts and the iOS home-screen quick actions. */
class ObserveShortcutsUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
) {
    operator fun invoke(): Flow<ShortcutSnapshot> =
        combine(friends.friends, hangouts.hangouts, interactions.interactions, settings.settings) { fs, hs, ints, s ->
            Shortcuts.snapshot(fs, hs, ints, hideNames = s.appLock || s.privateNotifications)
        }.distinctUntilChanged()
}

/** A request to open the "Log" sheet from outside the app (an app-icon shortcut): [friendIds] may be empty. */
data class QuickEntry(val friendIds: List<FriendId> = emptyList(), val kind: LogKind = LogKind.MEETUP)

/**
 * Quick entries waiting to open the "Log" sheet (like [ShareInbox]). Platforms [offer] what a shortcut asked for; the
 * app opens `LogInteractionRoute` (behind the app lock, when it's on) and [clear]s it. In memory only.
 */
class QuickEntryInbox {
    private val state = MutableStateFlow<QuickEntry?>(null)
    val pending: StateFlow<QuickEntry?> = state.asStateFlow()

    fun offer(entry: QuickEntry) {
        state.value = entry
    }

    fun clear() {
        state.value = null
    }
}

/**
 * The app's own `goodcompany://` links: `met/<id>` (the iOS widget's "We met": logs straight away) and `log` /
 * `log/<id>` (app-icon shortcuts: open the "Log" sheet, with that person picked). Shared by every platform.
 */
sealed interface AppLink {
    data class Met(val friendId: FriendId) : AppLink

    /** [friendId] null opens the sheet with nobody picked; an unknown or deleted id ends up the same way. */
    data class Log(val friendId: FriendId?) : AppLink

    companion object {
        const val SCHEME = "goodcompany"
        private const val PREFIX = "$SCHEME://"

        fun log(friendId: FriendId? = null): String = PREFIX + "log" + (friendId?.let { "/${it.value}" } ?: "")

        fun parse(url: String): AppLink? {
            if (!url.startsWith(PREFIX)) return null
            val parts = url.removePrefix(PREFIX).substringBefore('?').substringBefore('#').split('/').filter { it.isNotEmpty() }
            return when (parts.firstOrNull()) {
                "met" -> parts.getOrNull(1)?.let { Met(FriendId(it)) }
                "log" -> Log(parts.getOrNull(1)?.let(::FriendId))
                else -> null
            }
        }
    }
}

/** What an [AppLink.Log] asks the app to open. */
fun AppLink.Log.toQuickEntry(): QuickEntry = QuickEntry(listOfNotNull(friendId))
