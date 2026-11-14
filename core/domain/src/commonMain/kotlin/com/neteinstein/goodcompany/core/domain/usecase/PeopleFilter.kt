package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkType

/**
 * The People tab's filter row, shared with the people picker (issue #89, decisions "Choosing people · one picker" and
 * "People tab · closeness in the All menu"): everyone, one closeness level or one circle, one at a time. Picking a
 * circle resets the level to everyone; picking a level clears the circle.
 */
data class PeopleFilter(val circleId: String? = null, val closeness: Closeness? = null) {
    val isEveryone: Boolean get() = circleId == null && closeness == null

    /** A circle (null = everyone); the level goes back to everyone. */
    fun withCircle(id: String?): PeopleFilter = PeopleFilter(circleId = id)

    /** A level (null = everyone); the circle is cleared. */
    fun withCloseness(level: Closeness?): PeopleFilter = PeopleFilter(closeness = level)

    companion object {
        val Everyone = PeopleFilter()
    }
}

/**
 * What a [PeopleFilter] plus a search leaves: [people] A–Z, and the counts the row's "All" menu shows ([everyoneCount],
 * [levelCounts]), which ignore the filter and the search.
 */
data class FilteredPeople<T>(val people: List<T>, val everyoneCount: Int, val levelCounts: Map<Closeness, Int>) {
    /** [people] under their closeness headings, Inner first; empty levels are left out. */
    fun byCloseness(closenessOf: (T) -> Closeness): List<Pair<Closeness, List<T>>> = Closeness.entries.mapNotNull { c ->
        people.filter { closenessOf(it) == c }.takeIf { it.isNotEmpty() }?.let { c to it }
    }
}

/** Applies a [PeopleFilter] and a search to people (pure; the People tab and the people picker both call it). */
object PeopleFiltering {
    /** The People tab's name search: any part of the name, case aside; [likes] also matches what they like. */
    fun matches(friend: Friend, query: String, likes: Boolean = false): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return friend.displayName.contains(q, ignoreCase = true) ||
            friend.name.contains(q, ignoreCase = true) ||
            (likes && friend.likes.any { it.contains(q, ignoreCase = true) })
    }

    /**
     * Filters [items] (each read as a [Friend] through [friendOf]) by [filter] and [query].
     * - A circle keeps its members (the built-in Family circle included: it comes with its members filled in).
     * - [kinship] (what the query means as kinship, "my cousins", F7) wins over the name search: confirmed relations only.
     * - [exclude] never shows (e.g. the person whose page it is).
     * - [listedOnly] (the People tab) leaves out people with "Show in my list" off unless searched for or in a circle,
     *   and the counts follow it.
     */
    fun <T> apply(
        items: List<T>,
        friendOf: (T) -> Friend,
        filter: PeopleFilter,
        circles: List<FriendGroup>,
        query: String = "",
        kinship: Set<LinkType>? = null,
        exclude: Set<FriendId> = emptySet(),
        listedOnly: Boolean = false,
        matchLikes: Boolean = false,
    ): FilteredPeople<T> {
        val pool = items.filter { friendOf(it).id !in exclude }
        val counted = if (listedOnly) pool.filter { friendOf(it).showInMyList } else pool
        // A deleted circle (or one from another device's backup) reads as everyone.
        val members = circles.firstOrNull { it.id == filter.circleId }?.memberIds?.toSet()
        val searching = kinship != null || query.isNotBlank()
        val people = pool.asSequence()
            .filter { members == null || friendOf(it).id in members }
            .filter { filter.closeness == null || friendOf(it).closeness == filter.closeness }
            .filter {
                val f = friendOf(it)
                when {
                    kinship != null -> f.relationToMe in kinship
                    searching -> matches(f, query, matchLikes)
                    else -> !listedOnly || members != null || f.showInMyList
                }
            }
            .sortedBy { friendOf(it).displayName.lowercase() }
            .toList()
        return FilteredPeople(
            people = people,
            everyoneCount = counted.size,
            levelCounts = Closeness.entries.associateWith { c -> counted.count { friendOf(it).closeness == c } },
        )
    }

    /** [apply] for plain friends. */
    fun apply(
        friends: List<Friend>,
        filter: PeopleFilter,
        circles: List<FriendGroup>,
        query: String = "",
        kinship: Set<LinkType>? = null,
        exclude: Set<FriendId> = emptySet(),
    ): FilteredPeople<Friend> = apply(friends, { it }, filter, circles, query, kinship, exclude)
}
