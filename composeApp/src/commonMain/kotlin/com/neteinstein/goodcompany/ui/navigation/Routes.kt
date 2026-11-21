package com.neteinstein.goodcompany.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.neteinstein.goodcompany.composeApp.resources.Res
import com.neteinstein.goodcompany.composeApp.resources.nav_circles
import com.neteinstein.goodcompany.composeApp.resources.nav_people
import com.neteinstein.goodcompany.composeApp.resources.nav_plans
import com.neteinstein.goodcompany.composeApp.resources.nav_today
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/**
 * Every navigation route. Sealed and @Serializable so `AppRoutesTest` can list them all (through the sealed
 * serializer, no reflection) and check that each one is registered in [appDestinations]: navigating to an unregistered
 * route crashes ("Destination with route … cannot be found", issue #114).
 */
@Serializable sealed interface AppRoute

@Serializable data object FriendsRoute : AppRoute

@Serializable data class FriendDetailRoute(val id: String) : AppRoute

/** A friend's whole timeline (filters, delete with Undo, WhatsApp import). */
@Serializable data class FriendTimelineRoute(val id: String) : AppRoute

/**
 * The "Log" sheet, as a dialog destination: [friendIds] preselected, [type] a `LogKind` or `InteractionType` name
 * (e.g. "MEETUP", "CALL", "JOURNAL"); null opens on "Met". [circleId] opens on that circle's members, nobody ticked (F20).
 * [plan] answers a plan's "Did you meet?" (`<planId>@<date>`, `PlanOccurrence`, issue #43).
 * [note], [place] and [label] (with [type], [date] and [activity]) carry what the "Caught up" panel had chosen ("More details",
 * issues #47, #101).
 * [editFriend] + [editKey] open it in edit mode on that entry of that person's timeline (`TimelineEntry.key`, issue #102):
 * everything else is then ignored, and a gone entry closes the sheet.
 */
@Serializable data class LogInteractionRoute(
    val friendIds: List<String>,
    val type: String? = null,
    val circleId: String? = null,
    val plan: String? = null,
    /** ISO date to open on (a calendar suggestion's day, or the panel's); null = today. */
    val date: String? = null,
    /** A HangoutActivity name (guessed from a calendar event, or picked in the panel). */
    val activity: String? = null,
    val note: String? = null,
    val place: String? = null,
    /** The "Other" label typed in the "Caught up" panel (issue #101). */
    val label: String? = null,
    val editFriend: String? = null,
    val editKey: String? = null,
) : AppRoute {
    companion object {
        /** The Log sheet editing [key] on [friendId]'s timeline (issue #102). */
        fun edit(friendId: String, key: String) = LogInteractionRoute(listOf(friendId), editFriend = friendId, editKey = key)
    }
}

/**
 * The small "Caught up" panel (issues #47, #101, F2; it was "We met"): every in-app "Caught up" opens it, with "How?" first. [friendIds] or [circleId] (members listed,
 * nobody ticked, F20); [plan] answers that plan occurrence's "Did you meet?" (`<planId>@<date>`, `PlanOccurrence`).
 */
@Serializable data class WeMetRoute(val friendIds: List<String> = emptyList(), val circleId: String? = null, val plan: String? = null) : AppRoute

/**
 * The circle editor (decision D1); [id] null creates a circle. [withFriend] starts a new circle with that person in
 * ("New circle with <name>" on their page, issue #70).
 */
@Serializable data class CircleEditorRoute(val id: String? = null, val withFriend: String? = null) : AppRoute

/**
 * A circle's page (issue #88): header, actions, people, "Coming up" and its timeline. [id] may be the built-in Family
 * circle's. Opened from a card on the Circles tab and from a circle chip on a person's page.
 */
@Serializable data class CirclePageRoute(val id: String) : AppRoute

/** "Choose friends": the phone's contacts with checkboxes (decision D2; iOS and the Android sideload build). */
@Serializable data object ChooseContactsRoute : AppRoute

/** "Save to a friend" for something shared into the app (see ShareInbox). */
@Serializable data object ShareRoute : AppRoute

/** A friend's catch-up story; [fromFriend] = opened from their page (so no "Open profile" button). */
@Serializable data class CatchUpStoryRoute(val id: String, val fromFriend: Boolean = false) : AppRoute

/** The Circles tab (issue #67): the circles, "Last time with…" and the memories (was Together). */
@Serializable data object HangoutsRoute : AppRoute

/** The Today tab (issue #65): what needs you this week. */
@Serializable data object TodayRoute : AppRoute

/** "Everything coming up": the full agenda, pushed from Today (issue #65). */
@Serializable data object AgendaRoute : AppRoute

/** The Plans tab (issue #67): plans, gift lists, "Who's free for…?" and, with AI on, the weekly ideas (was Ideas). */
@Serializable data object IdeasRoute : AppRoute

/** The weekly AI ideas in full, pushed from the Plans tab (issue #67); only reachable while AI is on (F1). */
@Serializable data object WeeklyIdeasRoute : AppRoute

/** "Who's free for…?", pushed from the Plans tab (issue #67). */
@Serializable data object WhosFreeRoute : AppRoute

/** Settings, opened from the top right of every tab header (issue #65); no longer a tab. */
@Serializable data object SettingsRoute : AppRoute

/** "Your network": the map of your people (issue #46). */
@Serializable data object NetworkMapRoute : AppRoute

/** "Your people": the dashboard (issue #46). */
@Serializable data object PeopleDashboardRoute : AppRoute

/** "Suggested family": kinship worked out from the links, confirmed with one tap (issue #41). */
@Serializable data object SuggestedFamilyRoute : AppRoute

/** Settings → Places (F11). */
@Serializable data object PlacesRoute : AppRoute

/** Settings → Activities: what the log sheet offers, in which order (F3). */
@Serializable data object ActivitiesRoute : AppRoute

/** Settings → Calendar suggestions (D3). */
@Serializable data object CalendarSettingsRoute : AppRoute

/** Settings → Notifications: switches, persistent ones, hour, digest and privacy (issue #98). */
@Serializable data object NotificationSettingsRoute : AppRoute

/** "From your calendar": past meetups to log (D3). */
@Serializable data object CalendarSuggestionsRoute : AppRoute

/** Settings → Tags: the shared tag list (F16). */
@Serializable data object TagsRoute : AppRoute

/** Year in review; [year] null = the most relevant year. */
@Serializable data class YearReviewRoute(val year: Int? = null) : AppRoute

/**
 * The bottom bar (issue #65): Today · People · Circles · Plans, the same on every platform whatever the AI switch.
 * Settings left the bar: every tab header opens it ([SettingsRoute]). The route objects kept their old names
 * ([HangoutsRoute] is Circles, [IdeasRoute] is Plans).
 */
enum class TopLevelDestination(val route: AppRoute, val label: StringResource, val icon: ImageVector, val selectedIcon: ImageVector) {
    TODAY(TodayRoute, Res.string.nav_today, Icons.Outlined.WbSunny, Icons.Rounded.WbSunny),
    PEOPLE(FriendsRoute, Res.string.nav_people, Icons.Outlined.People, Icons.Rounded.People),
    CIRCLES(HangoutsRoute, Res.string.nav_circles, Icons.Outlined.Diversity3, Icons.Rounded.Diversity3),
    PLANS(IdeasRoute, Res.string.nav_plans, Icons.Outlined.Event, Icons.Rounded.Event),
}
