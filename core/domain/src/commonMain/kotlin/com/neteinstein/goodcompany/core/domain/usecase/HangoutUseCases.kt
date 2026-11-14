package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlin.time.Instant

/** The one-tap "I'm with…" check-in. */
class CheckInUseCase(
    private val hangouts: HangoutRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        friendIds: Collection<FriendId>,
        placeName: String? = null,
        location: GeoPoint? = null,
        note: String = "",
        at: Instant? = null,
        activity: HangoutActivity? = null,
    ): Hangout {
        require(friendIds.isNotEmpty()) { "Pick at least one friend" }
        val name = placeName?.trim()?.ifBlank { null }
        val named = location?.let { namedPlace(name, it) }
        val place = if (name == null && location == null) {
            null
        } else {
            Place(named?.name ?: name, location?.latitude, location?.longitude, named?.id)
        }
        val hangout = Hangout(ids.next(), friendIds.distinct(), at ?: clock.now(), place, note.trim(), activity)
        hangouts.add(hangout)
        return hangout
    }

    /**
     * F11: within the radius of a named place, a meetup with no name (or that place's own name) takes that place.
     * A new name at a GPS fix becomes a new named place, so the next check-in within ~100 m names itself. No name and
     * no place nearby keeps just the coordinates.
     */
    private suspend fun namedPlace(name: String?, at: GeoPoint): NamedPlace? {
        val match = NamedPlaces.nearest(hangouts.places.first(), at)?.place
        return when {
            name == null -> match
            match != null && match.name.equals(name, ignoreCase = true) -> match
            else -> NamedPlace(ids.next(), name, at.latitude, at.longitude).also { hangouts.upsertPlace(it) }
        }
    }
}

class DeleteHangoutUseCase(private val hangouts: HangoutRepository) {
    suspend operator fun invoke(id: String) = hangouts.delete(id)
}

/** Puts back a hangout removed a moment ago (snackbar "Undo"). */
class RestoreHangoutUseCase(private val hangouts: HangoutRepository) {
    suspend operator fun invoke(hangout: Hangout) = hangouts.add(hangout)
}

class GetCurrentLocationUseCase(private val location: LocationProvider) {
    suspend operator fun invoke(): GeoPoint? = runCatching { location.currentLocation() }.getOrNull()
}

/** "Meet every N days" rules and how each one is doing, soonest due first. */
class ObserveMeetupStatusUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<List<Relationships.MeetupStatus>> =
        combine(friends.friends, hangouts.hangouts, interactions.interactions, settings.settings) { fs, hs, ints, s ->
            Relationships.meetupStatuses(fs, hs, clock.today(), clock.timeZone, CadenceRules.from(s), ints)
        }
}
