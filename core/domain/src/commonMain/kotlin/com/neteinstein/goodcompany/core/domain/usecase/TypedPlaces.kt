package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * A place known only by the name typed on meetups or plans (issue #121): no GPS, so no [NamedPlace]. A meetup counts
 * here when its place has a name and isn't linked to a stored named place (one whose coordinates were cleared counts).
 * Spellings are kept apart ("Café" and "cafe" are two rows) so they can be merged.
 */
data class TypedPlace(val name: String, val meetups: Int, val plans: Int, val usually: List<Friend> = emptyList())

object TypedPlaces {
    fun of(hangouts: List<Hangout>, plans: List<Plan>, named: List<NamedPlace>, friends: List<Friend> = emptyList()): List<TypedPlace> {
        val namedIds = named.mapTo(HashSet()) { it.id }
        val meetups = hangouts.filter { isTyped(it, namedIds) }.groupBy { it.place!!.name!!.trim() }
        val planned = plans.mapNotNull { p -> p.place?.trim()?.takeIf { it.isNotEmpty() } }.groupingBy { it }.eachCount()
        val byId = friends.associateBy { it.id }
        return (meetups.keys + planned.keys).map { name ->
            val there = meetups[name].orEmpty()
            val usually = there.flatMap { it.friendIds }.groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }.take(2).mapNotNull { byId[it.key] }
            TypedPlace(name, there.size, planned[name] ?: 0, usually)
        }.sortedWith(compareByDescending<TypedPlace> { it.meetups + it.plans }.thenBy { it.name.lowercase() })
    }

    internal fun isTyped(h: Hangout, namedIds: Set<String>): Boolean {
        val place = h.place ?: return false
        return !place.name.isNullOrBlank() && (place.placeId == null || place.placeId !in namedIds)
    }
}

/** Settings → Your data → Places: the places with no GPS, most used first (issue #121). */
class ObserveTypedPlacesUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val plans: PlanRepository,
) {
    operator fun invoke(): Flow<List<TypedPlace>> =
        combine(friends.friends, hangouts.hangouts, plans.plans, hangouts.places) { fs, hs, ps, named -> TypedPlaces.of(hs, ps, named, fs) }
}

/**
 * Renames a typed place, or merges spellings into one ([from] → [to]): every meetup and plan with one of those names
 * takes [to]. Named (GPS) places are left alone. Returns how many entries changed.
 */
class RenameTypedPlaceUseCase(private val hangouts: HangoutRepository, private val plans: PlanRepository) {
    suspend operator fun invoke(from: Set<String>, to: String): Int {
        val target = to.trim()
        val sources = from.map { it.trim() }.toSet() - target
        if (target.isEmpty() || sources.isEmpty()) return 0
        val namedIds = hangouts.places.first().mapTo(HashSet()) { it.id }
        var changed = 0
        hangouts.hangouts.first()
            .filter { TypedPlaces.isTyped(it, namedIds) && it.place!!.name!!.trim() in sources }
            .forEach {
                hangouts.add(it.copy(place = it.place!!.copy(name = target)))
                changed++
            }
        plans.plans.first().filter { it.place?.trim() in sources }.forEach {
            plans.upsert(it.copy(place = target))
            changed++
        }
        return changed
    }
}

/**
 * "Add coordinates" for a typed place (issue #121): the spot where the phone is now becomes a named place with that
 * name, and the meetups there link to it, so the next check-in nearby names itself (F11). The meetups keep their own
 * coordinates (or none); plans keep their text. No place-search or map service is ever called.
 */
class PinTypedPlaceUseCase(private val hangouts: HangoutRepository, private val ids: IdGenerator) {
    suspend operator fun invoke(name: String, at: GeoPoint): NamedPlace? {
        val clean = name.trim().ifEmpty { return null }
        val namedIds = hangouts.places.first().mapTo(HashSet()) { it.id }
        val place = NamedPlace(ids.next(), clean, at.latitude, at.longitude)
        hangouts.upsertPlace(place)
        hangouts.hangouts.first()
            .filter { TypedPlaces.isTyped(it, namedIds) && it.place!!.name!!.trim() == clean }
            .forEach { hangouts.add(it.copy(place = it.place!!.copy(placeId = place.id))) }
        return place
    }
}
