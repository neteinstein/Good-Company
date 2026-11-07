package com.neteinstein.goodcompany.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.ui.components.LocalSharedElement
import com.neteinstein.goodcompany.feature.agenda.ui.screens.AgendaScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.FriendDetailScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendsScreen
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.HangoutsScreen
import com.neteinstein.goodcompany.feature.ideas.ui.screens.IdeasScreen
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsScreen

const val APP_VERSION = "1.0.0"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    val openFriend: (FriendId) -> Unit = { navController.navigate(FriendDetailRoute(it.value)) }
    val openSettings: () -> Unit = { navController.navigateTopLevel(TopLevelDestination.SETTINGS) }
    SharedTransitionLayout(modifier) {
        NavHost(
            navController = navController,
            startDestination = FriendsRoute,
            enterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 0.98f) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) + scaleOut(targetScale = 0.98f) },
        ) {
            sharedComposable<FriendsRoute>(this@SharedTransitionLayout) { FriendsScreen(onOpenFriend = openFriend) }
            composable<FriendDetailRoute>(
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { entry ->
                ProvideSharedElements(this@SharedTransitionLayout, this) {
                    FriendDetailScreen(
                        friendId = FriendId(entry.toRoute<FriendDetailRoute>().id),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = openSettings,
                    )
                }
            }
            sharedComposable<HangoutsRoute>(this@SharedTransitionLayout) { HangoutsScreen(onOpenFriend = openFriend) }
            sharedComposable<AgendaRoute>(this@SharedTransitionLayout) { AgendaScreen(onOpenFriend = openFriend) }
            composable<IdeasRoute> { IdeasScreen(onOpenSettings = openSettings) }
            composable<SettingsRoute> { SettingsScreen(appVersion = APP_VERSION) }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private inline fun <reified T : Any> NavGraphBuilder.sharedComposable(
    shared: SharedTransitionScope,
    crossinline content: @Composable () -> Unit,
) = composable<T> { ProvideSharedElements(shared, this) { content() } }

/** Bridges navigation's AnimatedContentScope to the feature-agnostic [LocalSharedElement] hook. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ProvideSharedElements(shared: SharedTransitionScope, animated: AnimatedContentScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSharedElement provides { key ->
            with(shared) { Modifier.sharedElement(rememberSharedContentState(key), animatedVisibilityScope = animated) }
        },
        content = content,
    )
}

fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
