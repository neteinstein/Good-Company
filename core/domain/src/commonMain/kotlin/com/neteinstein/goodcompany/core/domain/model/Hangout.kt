package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** "I am / was with these people" — the one-tap check-in. */
@Serializable
data class Hangout(
    val id: String,
    val friendIds: List<FriendId>,
    val at: Instant,
    val place: Place? = null,
    val note: String = "",
)

@Serializable
data class Place(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val label: String?
        get() = name?.takeIf { it.isNotBlank() }
            ?: if (latitude != null && longitude != null) {
                "${latitude.format4()}, ${longitude.format4()}"
            } else {
                null
            }
}

private fun Double.format4(): String {
    val scaled = kotlin.math.round(this * 10_000) / 10_000
    return scaled.toString()
}

/** A geographic fix from the device. */
data class GeoPoint(val latitude: Double, val longitude: Double)
