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

@Serializable data object HangoutsRoute

@Serializable data object AgendaRoute

@Serializable data object IdeasRoute

@Serializable data object SettingsRoute

/** Year in review; [year] null = the most relevant year. */
@Serializable data class YearReviewRoute(val year: Int? = null)

enum class TopLevelDestination(val route: Any, val label: StringResource, val icon: ImageVector, val selectedIcon: ImageVector) {
    FRIENDS(FriendsRoute, Res.string.nav_friends, Icons.Outlined.People, Icons.Rounded.People),
    TOGETHER(HangoutsRoute, Res.string.nav_together, Icons.Outlined.Diversity3, Icons.Rounded.Diversity3),
    AGENDA(AgendaRoute, Res.string.nav_agenda, Icons.Outlined.Event, Icons.Rounded.Event),
    IDEAS(IdeasRoute, Res.string.nav_ideas, Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome),
    SETTINGS(SettingsRoute, Res.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
}
