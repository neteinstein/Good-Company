package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

// Shared tags (F16). The list is derived from what people have: every spelling in likes, dislikes, food, drinks,
// interests and kids' and relatives' likes. Friend lists keep storing the text, so backups stay readable and a restore
// re-creates the list by itself.

/**
 * One spelling in the shared tag list and who has it. A person who has it in several places counts once; a tag can be
 * one person's like and another's dislike. Names are the friend's, or the kid's / relative's for their likes.
 */
data class Tag(
    val name: String,
    val likedBy: List<String> = emptyList(),
    val dislikedBy: List<String> = emptyList(),
) {
    val uses: Int get() = likedBy.size + dislikedBy.size

    /** Case- and accent-insensitive form: "Café" and "cafe" share it. */
    val key: String get() = Tags.normalize(name)
}

object Tags {
    /** Lower case, trimmed, accents folded ("Pastéis" → "pasteis", "Ș" → "s"), inner spaces collapsed. */
    fun normalize(text: String): String = buildString {
        var lastSpace = false
        for (c in text.trim().lowercase()) {
            if (c.isWhitespace()) {
                if (!lastSpace) append(' ')
                lastSpace = true
                continue
            }
            lastSpace = false
            append(FOLD[c] ?: c.toString())
        }
    }

    /** Every tag in use, most used first (ties by name). */
    fun catalog(friends: List<Friend>): List<Tag> {
        val liked = LinkedHashMap<String, LinkedHashSet<String>>()
        val disliked = LinkedHashMap<String, LinkedHashSet<String>>()
        fun add(map: MutableMap<String, LinkedHashSet<String>>, tags: List<String>, who: String) =
            tags.map(String::trim).filter(String::isNotEmpty).forEach { map.getOrPut(it) { LinkedHashSet() } += who }
        friends.forEach { f ->
            // Keyed by id so two friends with the same name still count twice.
            val who = "${f.id.value}\u0000${f.name}"
            add(liked, f.likes + f.food + f.drinks + f.interests, who)
            add(disliked, f.dislikes, who)
            f.kids.forEach { add(liked, it.likes, "${f.id.value}/${it.id}\u0000${it.name}") }
            f.relatives.forEach { add(liked, it.likes, "${f.id.value}/${it.id}\u0000${it.name}") }
        }
        fun names(set: Set<String>?) = set.orEmpty().map { it.substringAfter('\u0000') }
        return (liked.keys + disliked.keys)
            .map { Tag(it, names(liked[it]), names(disliked[it])) }
            .sortedWith(compareByDescending<Tag> { it.uses }.thenBy { it.key }.thenBy { it.name })
    }

    /**
     * What the tag editors offer while typing. Blank [query]: the most
     * used tags. Otherwise matches ignoring case and accents: those starting with the query (or one of its words)
     * first, then most used. Tags already in [exclude] (by [normalize]) are left out.
     */
    fun suggest(catalog: List<Tag>, query: String, exclude: Collection<String> = emptyList(), limit: Int = 8): List<Tag> {
        val skip = exclude.mapTo(HashSet(), ::normalize)
        val available = catalog.filter { it.key !in skip }
        val q = normalize(query)
        if (q.isEmpty()) return available.take(limit)
        fun rank(tag: Tag) = when {
            tag.key == q -> 0
            tag.key.startsWith(q) -> 1
            tag.key.split(' ').any { it.startsWith(q) } -> 2
            else -> 3
        }
        return available.filter { q in it.key }
            .sortedWith(compareBy<Tag>(::rank).thenByDescending { it.uses }.thenBy { it.key })
            .take(limit)
    }

    /** The existing spelling of [typed] (most used, ignoring case and accents), or [typed] trimmed when it's new. */
    fun canonical(catalog: List<Tag>, typed: String): String {
        val key = normalize(typed)
        return catalog.firstOrNull { it.key == key }?.name ?: typed.trim()
    }

    /** Spellings of the same tag ("Sushi" and "sushi", "Café" and "cafe"), most used first in each group. */
    fun duplicates(catalog: List<Tag>): List<List<Tag>> = catalog.groupBy { it.key }.values
        .filter { it.size > 1 }
        .map { group -> group.sortedWith(compareByDescending<Tag> { it.uses }.thenBy { it.name }) }

    /** Identifies a group of spellings for "Keep both": a new spelling makes it a different group. */
    fun signature(names: Collection<String>): String = names.sorted().joinToString("\n")

    /**
     * Renames every spelling in [from] to [to] in all of a friend's tag lists (merging when [to] is already there).
     * When-added dates move along, keeping the earliest.
     */
    fun rename(friend: Friend, from: Set<String>, to: String): Friend {
        val target = to.trim()
        if (target.isEmpty() || from.isEmpty()) return friend
        fun list(tags: List<String>) = if (tags.none { it in from }) tags else tags.map { if (it in from) target else it }.distinct()
        fun dates(map: Map<String, Instant>): Map<String, Instant> {
            if (map.keys.none { it in from }) return map
            val moved = map.filterKeys { it in from || it == target }.values.minOrNull()
            return map.filterKeys { it !in from } + listOfNotNull(moved?.let { target to it })
        }
        return friend.copy(
            likes = list(friend.likes),
            dislikes = list(friend.dislikes),
            food = list(friend.food),
            drinks = list(friend.drinks),
            interests = list(friend.interests),
            likesAddedAt = dates(friend.likesAddedAt),
            foodAddedAt = dates(friend.foodAddedAt),
            drinksAddedAt = dates(friend.drinksAddedAt),
            interestsAddedAt = dates(friend.interestsAddedAt),
            kids = friend.kids.map { it.copy(likes = list(it.likes)) },
            relatives = friend.relatives.map { it.copy(likes = list(it.likes)) },
        )
    }

    /** Takes [name] (that exact spelling) off all of a friend's tag lists. */
    fun remove(friend: Friend, name: String): Friend {
        fun list(tags: List<String>) = tags - name
        return friend.copy(
            likes = list(friend.likes),
            dislikes = list(friend.dislikes),
            food = list(friend.food),
            drinks = list(friend.drinks),
            interests = list(friend.interests),
            likesAddedAt = friend.likesAddedAt - name,
            foodAddedAt = friend.foodAddedAt - name,
            drinksAddedAt = friend.drinksAddedAt - name,
            interestsAddedAt = friend.interestsAddedAt - name,
            kids = friend.kids.map { it.copy(likes = list(it.likes)) },
            relatives = friend.relatives.map { it.copy(likes = list(it.likes)) },
        )
    }

    /** Puts [before]'s tag lists back on [current] (Undo), leaving everything else as it is now. */
    fun restore(current: Friend, before: Friend): Friend = current.copy(
        likes = before.likes,
        dislikes = before.dislikes,
        food = before.food,
        drinks = before.drinks,
        interests = before.interests,
        likesAddedAt = before.likesAddedAt,
        foodAddedAt = before.foodAddedAt,
        drinksAddedAt = before.drinksAddedAt,
        interestsAddedAt = before.interestsAddedAt,
        kids = current.kids.map { kid -> before.kids.firstOrNull { it.id == kid.id }?.let { kid.copy(likes = it.likes) } ?: kid },
        relatives = current.relatives.map { r -> before.relatives.firstOrNull { it.id == r.id }?.let { r.copy(likes = it.likes) } ?: r },
    )

    private val FOLD: Map<Char, String> = buildMap {
        fun map(chars: String, to: String) = chars.forEach { put(it, to) }
        map("àáâãäåāăą", "a")
        map("çćč", "c")
        map("ďđ", "d")
        map("èéêëēėęě", "e")
        map("ìíîïīį", "i")
        map("ñńň", "n")
        map("òóôõöøō", "o")
        map("ùúûüūůű", "u")
        map("ýÿ", "y")
        map("śšșş", "s")
        map("ťțţ", "t")
        map("źżž", "z")
        map("łľ", "l")
        map("ř", "r")
        map("ğ", "g")
        put('æ', "ae")
        put('œ', "oe")
        put('ß', "ss")
    }
}

/** The shared tag list, most used first, kept up to date. */
class ObserveTagsUseCase(private val friends: FriendRepository) {
    operator fun invoke(): Flow<List<Tag>> = friends.friends.map(Tags::catalog)
}

/** Groups of spellings that look like the same tag, minus the ones the user chose to keep apart. */
class ObserveTagDuplicatesUseCase(private val friends: FriendRepository, private val settings: SettingsRepository) {
    operator fun invoke(): Flow<List<List<Tag>>> = combine(friends.friends, settings.settings) { fs, s ->
        Tags.duplicates(Tags.catalog(fs)).filterNot { group -> Tags.signature(group.map { it.name }) in s.keptTagSpellings }
    }
}

/** "Keep both": stop offering to merge these spellings (until another spelling of the same tag shows up). */
class KeepTagSpellingsUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(names: Collection<String>) {
        val signature = Tags.signature(names)
        settings.update { it.copy(keptTagSpellings = it.keptTagSpellings + signature) }
    }
}

/**
 * Renames the spellings [from] to [to] for everyone at once (one transaction). Renaming to a tag that exists merges
 * them. Returns how many people changed.
 */
class RenameTagUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(from: Set<String>, to: String): Int {
        val target = to.trim()
        val sources = from - target
        if (target.isEmpty() || sources.isEmpty()) return 0
        return friends.updateAll { Tags.rename(it, sources, target) }.size
    }
}

/** A tag taken off everyone, kept for "Undo": [before] holds the people who had it, as they were. */
data class RemovedTag(val name: String, val before: List<Friend>)

/** Deletes the spelling [name] from every person (one transaction). */
class DeleteTagUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(name: String): RemovedTag = RemovedTag(name, friends.updateAll { Tags.remove(it, name) })
}

/** Undo for [DeleteTagUseCase]: puts the tag lists of those people back, in one transaction. */
class RestoreTagUseCase(private val friends: FriendRepository) {
    suspend operator fun invoke(removed: RemovedTag) {
        val before: Map<FriendId, Friend> = removed.before.associateBy { it.id }
        friends.updateAll { f -> before[f.id]?.let { Tags.restore(f, it) } ?: f }
    }
}
