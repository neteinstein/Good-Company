package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable

/**
 * A name given to a GPS spot (F11), e.g. "Marta's house". A check-in within [radiusM] metres of it gets its name by
 * itself. Only coordinates are kept: no place-search or map service is ever called.
 */
@Serializable
data class NamedPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusM: Int = DEFAULT_RADIUS_M,
) {
    val point: GeoPoint get() = GeoPoint(latitude, longitude)

    companion object {
        const val DEFAULT_RADIUS_M = 100
    }
}
