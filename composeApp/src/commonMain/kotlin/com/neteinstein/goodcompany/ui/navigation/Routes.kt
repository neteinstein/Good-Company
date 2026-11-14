package com.neteinstein.goodcompany.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Diversity3
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.neteinstein.goodcompany.composeApp.resources.Res
import com.neteinstein.goodcompany.composeApp.resources.nav_agenda
import com.neteinstein.goodcompany.composeApp.resources.nav_friends
import com.neteinstein.goodcompany.composeApp.resources.nav_ideas
import com.neteinstein.goodcompany.composeApp.resources.nav_settings
import com.neteinstein.goodcompany.composeApp.resources.nav_together
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
 */
@Serializable data class LogInteractionRoute(
    val friendIds: List<String>,
    val type: String? = null,
    val circleId: String? = null,
    val plan: String? = null,
    /** ISO date to open on (a calendar suggestion's day); null = today. */
    val date: String? = null,
    /** A HangoutActivity name guessed from a calendar event. */
    val activity: String? = null,
)

/** The circle editor (decision D1); [id] null creates a circle. */
@Serializable data class CircleEditorRoute(val id: String? = null)

/** "Choose friends": the phone's contacts with checkboxes (decision D2; iOS and the Android sideload build). */
@Serializable data object ChooseContactsRoute

/** "Save to a friend" for something shared into the app (see ShareInbox). */
@Serializable data object ShareRoute

/** A friend's catch-up story; [fromFriend] = opened from their page (so no "Open profile" button). */
@Serializable data class CatchUpStoryRoute(val id: String, val fromFriend: Boolean = false)

@Serializable data object HangoutsRoute

@Serializable data object AgendaRoute

@Serializable data object IdeasRoute

@Serializable data object SettingsRoute

/** "Your network": the map of your people (issue #46). */
@Serializable data object NetworkMapRoute

/** "Your people": the dashboard (issue #46). */
@Serializable data object PeopleDashboardRoute

/** "Suggested family": kinship worked out from the links, confirmed with one tap (issue #41). */
@Serializable data object SuggestedFamilyRoute

/** Gift lists per occasion (F8): from the Together tab and from a friend's gift card. */
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

enum class TopLevelDestination(val route: Any, val label: StringResource, val icon: ImageVector, val selectedIcon: ImageVector) {
    FRIENDS(FriendsRoute, Res.string.nav_friends, Icons.Outlined.People, Icons.Rounded.People),
    TOGETHER(HangoutsRoute, Res.string.nav_together, Icons.Outlined.Diversity3, Icons.Rounded.Diversity3),
    AGENDA(AgendaRoute, Res.string.nav_agenda, Icons.Outlined.Event, Icons.Rounded.Event),
    IDEAS(IdeasRoute, Res.string.nav_ideas, Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome),
    SETTINGS(SettingsRoute, Res.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
    ;

    /** Ideas is AI only: it leaves the bar while AI is off (F1). */
    fun isShown(aiEnabled: Boolean): Boolean = this != IDEAS || aiEnabled

    companion object {
        /** The tabs in the bar, given the AI switch. */
        fun shown(aiEnabled: Boolean): List<TopLevelDestination> = entries.filter { it.isShown(aiEnabled) }
    }
}
