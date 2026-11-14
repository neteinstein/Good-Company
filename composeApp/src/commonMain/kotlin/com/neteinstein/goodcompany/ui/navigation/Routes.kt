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

@Serializable data object FriendsRoute

@Serializable data class FriendDetailRoute(val id: String)

/** A friend's whole timeline (filters, delete with Undo, WhatsApp import). */
@Serializable data class FriendTimelineRoute(val id: String)

/**
 * The "Log" sheet, as a dialog destination: [friendIds] preselected, [type] a `LogKind` or `InteractionType` name
 * (e.g. "MEETUP", "CALL", "JOURNAL"); null opens on "Met". [circleId] opens on that circle's members, nobody ticked (F20).
 * [plan] answers a plan's "Did you meet?" (`<planId>@<date>`, `PlanOccurrence`, issue #43).
 * [note] and [place] (with [date] and [activity]) carry what the "We met" panel had chosen ("More details", issue #47).
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
)

/**
 * The small "We met" panel (issue #47, F2): every in-app "We met" opens it. [friendIds] or [circleId] (members listed,
 * nobody ticked, F20); [plan] answers that plan occurrence's "Did you meet?" (`<planId>@<date>`, `PlanOccurrence`).
 */
@Serializable data class WeMetRoute(val friendIds: List<String> = emptyList(), val circleId: String? = null, val plan: String? = null)

/**
 * The circle editor (decision D1); [id] null creates a circle. [withFriend] starts a new circle with that person in
 * ("New circle with <name>" on their page, issue #70).
 */
@Serializable data class CircleEditorRoute(val id: String? = null, val withFriend: String? = null)

/** "Choose friends": the phone's contacts with checkboxes (decision D2; iOS and the Android sideload build). */
@Serializable data object ChooseContactsRoute

/** "Save to a friend" for something shared into the app (see ShareInbox). */
@Serializable data object ShareRoute

/** A friend's catch-up story; [fromFriend] = opened from their page (so no "Open profile" button). */
@Serializable data class CatchUpStoryRoute(val id: String, val fromFriend: Boolean = false)

/** The Circles tab (issue #67): the circles, "Last time with…" and the memories (was Together). */
@Serializable data object HangoutsRoute

/** The Today tab (issue #65): what needs you this week. */
@Serializable data object TodayRoute

/** "Everything coming up": the full agenda, pushed from Today (issue #65). */
@Serializable data object AgendaRoute

/** The Plans tab (issue #67): plans, gift lists, "Who's free for…?" and, with AI on, the weekly ideas (was Ideas). */
@Serializable data object IdeasRoute

/** The weekly AI ideas in full, pushed from the Plans tab (issue #67); only reachable while AI is on (F1). */
@Serializable data object WeeklyIdeasRoute

/** "Who's free for…?", pushed from the Plans tab (issue #67). */
@Serializable data object WhosFreeRoute

/** Settings, opened from the top right of every tab header (issue #65); no longer a tab. */
@Serializable data object SettingsRoute

/** "Your network": the map of your people (issue #46). */
@Serializable data object NetworkMapRoute

/** "Your people": the dashboard (issue #46). */
@Serializable data object PeopleDashboardRoute

/** "Suggested family": kinship worked out from the links, confirmed with one tap (issue #41). */
@Serializable data object SuggestedFamilyRoute

/** Gift lists per occasion (F8): from the Plans tab (issue #67) and from a friend's gift card. */
@Serializable data object GiftListsRoute

@Serializable data class GiftListRoute(val id: String)

/** Settings → Places (F11). */
@Serializable data object PlacesRoute

/** Settings → Activities: what the log sheet offers, in which order (F3). */
@Serializable data object ActivitiesRoute

/** Settings → Calendar suggestions (D3). */
@Serializable data object CalendarSettingsRoute

/** "From your calendar": past meetups to log (D3). */
@Serializable data object CalendarSuggestionsRoute

/** Settings → Tags: the shared tag list (F16). */
@Serializable data object TagsRoute

/** Year in review; [year] null = the most relevant year. */
@Serializable data class YearReviewRoute(val year: Int? = null)

/**
 * The bottom bar (issue #65): Today · People · Circles · Plans, the same on every platform whatever the AI switch.
 * Settings left the bar: every tab header opens it ([SettingsRoute]). The route objects kept their old names
 * ([HangoutsRoute] is Circles, [IdeasRoute] is Plans).
 */
enum class TopLevelDestination(val route: Any, val label: StringResource, val icon: ImageVector, val selectedIcon: ImageVector) {
    TODAY(TodayRoute, Res.string.nav_today, Icons.Outlined.WbSunny, Icons.Rounded.WbSunny),
    PEOPLE(FriendsRoute, Res.string.nav_people, Icons.Outlined.People, Icons.Rounded.People),
    CIRCLES(HangoutsRoute, Res.string.nav_circles, Icons.Outlined.Diversity3, Icons.Rounded.Diversity3),
    PLANS(IdeasRoute, Res.string.nav_plans, Icons.Outlined.Event, Icons.Rounded.Event),
}
