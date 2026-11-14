package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A **circle** (decision D1, 2026-11-13): a free-form group of friends ("Choir", "Football crew") with an emoji and a
 * colour. The UI calls it a circle; the code and the `friend_group` table keep the older "group" name. A friend can be
 * in several circles, and closeness ([Closeness]) still varies per person inside one.
 *
 * [meetEveryDays] is the optional "Remind me when we haven't met" goal (null = off, the default for new circles).
 * A circle never changes a member's own rhythm.
 *
 * [cover] is the circle's cover photo (issue #88), kept in its own table keyed by circle id (so the built-in Family
 * circle can have one too) and filled in when circles are read. It isn't part of the circle's JSON: backups carry covers
 * in [Backup.circleCovers].
 */
@Serializable
data class FriendGroup(
    val id: String,
    val name: String,
    val emoji: String = "👥",
    val memberIds: List<FriendId> = emptyList(),
    val meetEveryDays: Int? = null,
    val snoozedUntil: LocalDate? = null,
    /** Missing in backups made before circles had colours: those restore as [CircleColor.PURPLE]. */
    val color: CircleColor = CircleColor.PURPLE,
    @Transient val cover: ContactPhoto? = null,
)

/** A circle's cover photo (issue #88), as stored and backed up: no foreign key, Family's id has no stored circle. */
@Serializable
data class CircleCover(val circleId: String, val cover: ContactPhoto)

/**
 * The seven circle colours. Only the name is stored; the light and dark container/content values live in `:core:ui`
 * so they follow the theme.
 */
@Serializable
enum class CircleColor {
    PURPLE,
    BLUE,
    TEAL,
    AMBER,
    RUST,
    MAGENTA,
    SLATE,
    ;

    companion object {
        /** Colours in turn: the [index]th circle gets this one (the migration does the same for existing groups). */
        fun forIndex(index: Int): CircleColor = entries[index.mod(entries.size)]

        /** The stored name, or [PURPLE] for anything unknown (a newer app's colour, a hand-edited file). */
        fun parse(name: String?): CircleColor = entries.firstOrNull { it.name == name } ?: PURPLE
    }
}
