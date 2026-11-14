package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Named places (F11): distance matching and "usually here with". Pure; everything stays on the device. */
object NamedPlaces {
    /** Mean Earth radius (IUGG), in metres. */
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** "From your last N meetups here." */
    const val RECENT_MEETUPS = 8

    /** At most this many people are suggested. */
    const val MAX_SUGGESTIONS = 5

    /** In Settings, another place this close (in radii) is shown as a likely duplicate to merge. */
    private const val NEARBY_RADII = 2

    /** Great-circle (haversine) distance in metres. */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = a.latitude.toRadians()
        val lat2 = b.latitude.toRadians()
        val dLat = lat2 - lat1
        val dLon = (b.longitude - a.longitude).toRadians()
        val h = sin(dLat / 2).let { it * it } + cos(lat1) * cos(lat2) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    data class Match(val place: NamedPlace, val distanceM: Double)

    /** The nearest place whose radius covers [at] (the edge counts as inside), or null. */
    fun nearest(places: List<NamedPlace>, at: GeoPoint): Match? = places
        .map { Match(it, distanceM(it.point, at)) }
        .filter { it.distanceM <= it.place.radiusM }
        .minByOrNull { it.distanceM }

    /** Meetups linked to [placeId], newest first. */
    fun meetupsAt(placeId: String, hangouts: List<Hangout>): List<Hangout> =
        hangouts.filter { it.place?.placeId == placeId }.sortedByDescending { it.at }

    /**
     * The people usually met at [placeId]: the most frequent in its last [RECENT_MEETUPS] meetups (ties: seen there most
     * recently first), without [exclude]. A suggestion only: nobody is ever ticked from this.
     */
    fun usualPeople(
        placeId: String,
        hangouts: List<Hangout>,
        exclude: Collection<FriendId> = emptySet(),
        limit: Int = MAX_SUGGESTIONS,
    ): List<FriendId> {
        val recent = meetupsAt(placeId, hangouts).take(RECENT_MEETUPS)
        val counts = LinkedHashMap<FriendId, Int>() // insertion order = most recent first
        recent.forEach { h -> h.friendIds.forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        return counts.entries.filter { it.key !in exclude }
            .sortedByDescending { it.value } // stable: ties keep the most recent first
            .take(limit)
            .map { it.key }
    }

    /** The other place nearest to [place] within a couple of radii: likely the same spot named twice. */
    fun nearbyDuplicate(place: NamedPlace, places: List<NamedPlace>): Match? = places
        .filter { it.id != place.id }
        .map { Match(it, distanceM(it.point, place.point)) }
        .filter { it.distanceM <= NEARBY_RADII * maxOf(it.place.radiusM, place.radiusM) }
        .minByOrNull { it.distanceM }

    private fun Double.toRadians() = this * kotlin.math.PI / 180.0
}

/** Everything the log sheets need to name a spot and suggest people. */
data class PlaceBook(val places: List<NamedPlace> = emptyList(), val hangouts: List<Hangout> = emptyList()) {
    fun match(at: GeoPoint?): NamedPlaces.Match? = at?.let { NamedPlaces.nearest(places, it) }

    fun usualPeople(placeId: String, exclude: Collection<FriendId>): List<FriendId> =
        NamedPlaces.usualPeople(placeId, hangouts, exclude)

    /** How many of the last meetups there the suggestion comes from. */
    fun recentMeetups(placeId: String): Int = minOf(NamedPlaces.meetupsAt(placeId, hangouts).size, NamedPlaces.RECENT_MEETUPS)
}

class ObservePlaceBookUseCase(private val hangouts: HangoutRepository) {
    operator fun invoke(): Flow<PlaceBook> = combine(hangouts.places, hangouts.hangouts, ::PlaceBook)
}

/** One row of Settings → Places. */
data class PlaceSummary(
    val place: NamedPlace,
    val meetups: Int,
    /** Usually met there, most frequent first. */
    val usually: List<Friend>,
    /** Another place close by, offered for a merge. */
    val nearby: NamedPlaces.Match?,
)

/** Settings → Places: most visited first, then A–Z. */
class ObservePlacesUseCase(private val friends: FriendRepository, private val hangouts: HangoutRepository) {
    operator fun invoke(): Flow<List<PlaceSummary>> = combine(friends.friends, hangouts.places, hangouts.hangouts) { fs, ps, hs ->
        val byId = fs.associateBy { it.id }
        ps.map { p ->
            PlaceSummary(
                place = p,
                meetups = NamedPlaces.meetupsAt(p.id, hs).size,
                usually = NamedPlaces.usualPeople(p.id, hs, limit = 2).mapNotNull(byId::get),
                nearby = NamedPlaces.nearbyDuplicate(p, ps),
            )
        }.sortedWith(compareByDescending<PlaceSummary> { it.meetups }.thenBy { it.place.name.lowercase() })
    }
}

class RenamePlaceUseCase(private val hangouts: HangoutRepository) {
    /** Blank names are ignored. Every meetup there takes the new name. */
    suspend operator fun invoke(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        hangouts.renamePlace(id, clean)
    }
}

class DeletePlaceUseCase(private val hangouts: HangoutRepository) {
    /** Returns the place removed, for the snackbar's Undo. */
    suspend operator fun invoke(id: String): NamedPlace? {
        val place = hangouts.places.first().firstOrNull { it.id == id } ?: return null
        hangouts.deletePlace(id)
        return place
    }
}

class RestorePlaceUseCase(private val hangouts: HangoutRepository) {
    suspend operator fun invoke(place: NamedPlace) = hangouts.upsertPlace(place)
}

/** Two names for the same spot: [fromId]'s meetups move to [intoId], which keeps its name. */
class MergePlacesUseCase(private val hangouts: HangoutRepository) {
    suspend operator fun invoke(fromId: String, intoId: String) {
        if (fromId == intoId) return
        hangouts.mergePlaces(fromId, intoId)
    }
}
