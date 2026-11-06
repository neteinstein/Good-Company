package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
@JvmInline
value class FriendId(val value: String)

/**
 * A person in your circle. Seeded from the device address book ([contactId] set) or created
 * manually in the app ([contactId] null — the only option on the web).
 *
 * Contact-owned fields (name, phones, birthday) are refreshed on every contacts sync; everything
 * else is app-owned and never overwritten by a sync.
 */
@Serializable
data class Friend(
    val id: FriendId,
    val name: String,
    val contactId: String? = null,
    val phoneNumbers: List<String> = emptyList(),
    val birthday: Birthday? = null,
    /** "Real friend" — promoted out of the address-book noise. */
    val isCloseFriend: Boolean = false,
    val notifyBirthday: Boolean = false,
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val notes: String = "",
    val kids: List<Kid> = emptyList(),
    val importantEvents: List<ImportantEvent> = emptyList(),
    /** "If I don't meet them in this many days, remind me." Null = no rule. */
    val meetEveryDays: Int? = null,
    /** Opt-in for the weekly AI event suggestions. */
    val suggestEvents: Boolean = false,
    /** Contact disappeared from the address book; kept because it has app data. */
    val isOrphaned: Boolean = false,
) {
    val initials: String
        get() = name.split(' ', '-')
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}

@Serializable
data class Kid(
    val id: String,
    val name: String,
    val birthday: Birthday? = null,
    val likes: List<String> = emptyList(),
)

/** Something big in a friend's life (surgery, exam, interview…) worth a ping. */
@Serializable
data class ImportantEvent(
    val id: String,
    val title: String,
    val date: kotlinx.datetime.LocalDate,
    /** Also remind this many days before (0 = only on the day). */
    val remindDaysBefore: Int = 0,
)
