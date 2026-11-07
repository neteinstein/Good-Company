package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    ): Hangout {
        require(friendIds.isNotEmpty()) { "Pick at least one friend" }
        val place = if (placeName.isNullOrBlank() && location == null) {
            null
        } else {
            Place(placeName?.trim()?.ifBlank { null }, location?.latitude, location?.longitude)
        }
        val hangout = Hangout(ids.next(), friendIds.distinct(), at ?: clock.now(), place, note.trim())
        hangouts.add(hangout)
        return hangout
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

/** Hangout history, optionally narrowed to those that included **all** of the given friends. */
class ObserveHangoutHistoryUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
) {
    operator fun invoke(withAll: Flow<Set<FriendId>>): Flow<List<HangoutWithFriends>> =
        combine(friends.friends, hangouts.hangouts, withAll) { fs, hs, filter ->
            val byId = fs.associateBy { it.id }
            Relationships.hangoutsWithAll(hs, filter).map { h -> HangoutWithFriends(h, h.friendIds.mapNotNull(byId::get)) }
        }
}

/** "Meet every N days" rules and how each one is doing, soonest due first. */
class ObserveMeetupStatusUseCase(
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<List<Relationships.MeetupStatus>> = combine(friends.friends, hangouts.hangouts) { fs, hs ->
        Relationships.meetupStatuses(fs, hs, clock.today(), clock.timeZone)
    }
}
