package org.goodcompany.app.core.domain.model

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
    /** What you did together (F3), picked by icon in the Log popup. */
    val activity: HangoutActivity? = null,
    /** One of your own activities (issue #121), by name, when [activity] is null. Kept when it's renamed or removed. */
    val activityName: String? = null,
) {
    val activityChoice: ActivityChoice? get() = ActivityChoice.of(activity, activityName)
}

/**
 * An activity as the pickers offer it (issue #121): a built-in one or one of your own, by name. Meetups and plans store
 * a built-in one as [HangoutActivity] and your own as its name, so a past entry keeps its label.
 */
sealed interface ActivityChoice {
    data class BuiltIn(val activity: HangoutActivity) : ActivityChoice

    data class Custom(val name: String) : ActivityChoice

    val builtIn: HangoutActivity? get() = (this as? BuiltIn)?.activity
    val customName: String? get() = (this as? Custom)?.name

    companion object {
        fun of(activity: HangoutActivity?, name: String?): ActivityChoice? =
            activity?.let(::BuiltIn) ?: name?.trim()?.takeIf { it.isNotEmpty() }?.let(::Custom)
    }
}

/**
 * What a meetup was (F3). The Log popup shows the four most used first, the rest behind "See all" (issue #151). [CULTURE] is cinema,
 * concerts and theatre; [MUSIC] is playing or singing together; [TRIP] is a trip or an outing.
 */
enum class HangoutActivity { LUNCH, DINNER, DRINKS, COFFEE, SPORTS, RUNNING, WALK, AT_HOME, CULTURE, MUSIC, TRIP }

@Serializable
data class Place(
    val name: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** The [NamedPlace] this meetup was at (F11), if any. Renaming or merging that place updates [name] here too. */
    val placeId: String? = null,
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
