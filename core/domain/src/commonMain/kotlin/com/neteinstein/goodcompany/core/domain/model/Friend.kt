package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate
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
    /** Which circle a real friend belongs to — gives a default "meet every" cadence without per-friend setup. */
    val circle: Circle? = null,
    /** Catch-up nudges are paused until this date (inclusive). */
    val snoozedUntil: LocalDate? = null,
    /** Gift ideas (not yet given) and gift history (given), for them or their kids. */
    val gifts: List<Gift> = emptyList(),
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
    val date: LocalDate,
    /** Also remind this many days before (0 = only on the day). */
    val remindDaysBefore: Int = 0,
)

/**
 * Dunbar-style tiers for real friends. Each has a default cadence (configurable in Settings) so reminders work
 * the moment someone is marked as a real friend. A per-friend "meet every" value still overrides it.
 */
enum class Circle { INNER, CLOSE, GOOD }

/** A gift idea ([givenOn] null) or a gift already given. [forKidId] targets one of the friend's kids. */
@Serializable
data class Gift(
    val id: String,
    val title: String,
    val note: String = "",
    val forKidId: String? = null,
    val givenOn: LocalDate? = null,
    val occasion: GiftOccasion? = null,
) {
    val isIdea: Boolean get() = givenOn == null
}

enum class GiftOccasion { BIRTHDAY, CHRISTMAS, THANK_YOU, JUST_BECAUSE }
