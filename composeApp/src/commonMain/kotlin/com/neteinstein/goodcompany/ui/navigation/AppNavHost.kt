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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.toRoute
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.ui.components.LocalSharedElement
import com.neteinstein.goodcompany.feature.agenda.ui.screens.AgendaScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.choose.ChooseContactsScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.FriendDetailScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendsScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.log.LogInteractionScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.share.ShareToFriendScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.story.CatchUpStoryScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.timeline.FriendTimelineScreen
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.HangoutsScreen
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.circle.CircleEditorScreen
import com.neteinstein.goodcompany.feature.ideas.ui.screens.IdeasScreen
import com.neteinstein.goodcompany.feature.review.ui.screens.YearReviewScreen
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsScreen

const val APP_VERSION = "1.0.0"

/** Saved-state key: how many people "Choose friends" added, read by Home. */
private const val CONTACTS_ADDED = "contacts_added"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier, startDestination: Any = FriendsRoute) {
    val openFriend: (FriendId) -> Unit = { navController.navigate(FriendDetailRoute(it.value)) }
    val openStory: (FriendId) -> Unit = { navController.navigate(CatchUpStoryRoute(it.value)) }
    val openSettings: () -> Unit = { navController.navigateTopLevel(TopLevelDestination.SETTINGS) }
    val editCircle: (String?) -> Unit = { navController.navigate(CircleEditorRoute(it)) }
    SharedTransitionLayout(modifier) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 0.98f) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) + scaleOut(targetScale = 0.98f) },
        ) {
            sharedComposable<FriendsRoute>(this@SharedTransitionLayout) { entry ->
                // How many people "Choose friends" just added, handed back for Home's snackbar.
                val chosen by entry.savedStateHandle.getStateFlow(CONTACTS_ADDED, 0).collectAsStateWithLifecycle()
                FriendsScreen(
                    onOpenFriend = openFriend,
                    onOpenStory = openStory,
                    onEditCircle = editCircle,
                    onLogCircle = { navController.navigate(LogInteractionRoute(emptyList(), LogKind.MEETUP.name, circleId = it)) },
                    onChooseContacts = { navController.navigate(ChooseContactsRoute) },
                    chosenContacts = chosen,
                    onChosenShown = { entry.savedStateHandle[CONTACTS_ADDED] = 0 },
                )
            }
            composable<ChooseContactsRoute>(
                enterTransition = { slideInVertically { it / 4 } + fadeIn() },
                popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
            ) {
                ChooseContactsScreen(
                    onBack = { navController.popBackStack(ChooseContactsRoute, inclusive = true) },
                    onDone = { added ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(CONTACTS_ADDED, added)
                        navController.popBackStack(ChooseContactsRoute, inclusive = true)
                    },
                )
            }
            composable<FriendDetailRoute>(
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { entry ->
                ProvideSharedElements(this@SharedTransitionLayout, this) {
                    val id = entry.toRoute<FriendDetailRoute>().id
                    FriendDetailScreen(
                        friendId = FriendId(id),
                        onBack = { navController.popBackStack() },
                        onOpenSettings = openSettings,
                        onOpenStory = { navController.navigate(CatchUpStoryRoute(id, fromFriend = true)) },
                        onOpenFriend = openFriend,
                        onOpenTimeline = { navController.navigate(FriendTimelineRoute(id)) },
                        onLog = { navController.navigate(LogInteractionRoute(listOf(id))) },
                        // The friend page already set Home's circle filter; Home shows it.
                        onOpenCircle = { navController.navigateTopLevel(TopLevelDestination.FRIENDS) },
                        onEditCircle = editCircle,
                    )
                }
            }
            composable<FriendTimelineRoute>(
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { entry ->
                val id = entry.toRoute<FriendTimelineRoute>().id
                FriendTimelineScreen(
                    friendId = FriendId(id),
                    onBack = { navController.popBackStack() },
                    onLog = { navController.navigate(LogInteractionRoute(listOf(id))) },
                )
            }
            dialog<LogInteractionRoute>(dialogProperties = DialogProperties(usePlatformDefaultWidth = false)) { entry ->
                val route = entry.toRoute<LogInteractionRoute>()
                LogInteractionScreen(
                    friendIds = route.friendIds.map(::FriendId),
                    type = route.type,
                    circleId = route.circleId,
                    onClose = { navController.popBackStack() },
                )
            }
            composable<CircleEditorRoute>(
                enterTransition = { slideInVertically { it / 4 } + fadeIn() },
                popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
            ) { entry ->
                CircleEditorScreen(groupId = entry.toRoute<CircleEditorRoute>().id, onClose = { navController.popBackStack() })
            }
            composable<ShareRoute>(
                enterTransition = { slideInVertically { it / 4 } + fadeIn() },
                popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
            ) {
                ShareToFriendScreen(
                    onClose = { navController.popBackStack(ShareRoute, inclusive = true) },
                    onSaved = { id ->
                        // "Add a photo" on a friend's page lands back on that page instead of opening it twice.
                        val previous = navController.previousBackStackEntry
                        val fromThatFriend = previous?.destination?.hasRoute<FriendDetailRoute>() == true &&
                            previous.toRoute<FriendDetailRoute>().id == id.value
                        if (fromThatFriend) {
                            navController.popBackStack(ShareRoute, inclusive = true)
                        } else {
                            navController.navigate(FriendDetailRoute(id.value)) { popUpTo<ShareRoute> { inclusive = true } }
                        }
                    },
                )
            }
            composable<CatchUpStoryRoute>(
                enterTransition = { slideInVertically { it / 4 } + fadeIn() },
                popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
            ) { entry ->
                val route = entry.toRoute<CatchUpStoryRoute>()
                CatchUpStoryScreen(
                    friendId = FriendId(route.id),
                    onClose = { navController.popBackStack() },
                    onOpenFriend = if (route.fromFriend) {
                        null
                    } else {
                        {
                            navController.popBackStack()
                            navController.navigate(FriendDetailRoute(route.id))
                        }
                    },
                )
            }
            sharedComposable<HangoutsRoute>(this@SharedTransitionLayout) {
                HangoutsScreen(
                    onOpenFriend = openFriend,
                    onOpenYearReview = { navController.navigate(YearReviewRoute(it)) },
                    onEditCircle = editCircle,
                )
            }
            sharedComposable<AgendaRoute>(this@SharedTransitionLayout) { AgendaScreen(onOpenFriend = openFriend) }
            composable<IdeasRoute> { IdeasScreen(onOpenSettings = openSettings) }
            composable<SettingsRoute> { SettingsScreen(appVersion = APP_VERSION) }
            composable<YearReviewRoute>(
                enterTransition = { slideInVertically { it / 4 } + fadeIn() },
                popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
            ) { entry ->
                YearReviewScreen(year = entry.toRoute<YearReviewRoute>().year, onBack = { navController.popBackStack() })
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private inline fun <reified T : Any> NavGraphBuilder.sharedComposable(
    shared: SharedTransitionScope,
    crossinline content: @Composable (NavBackStackEntry) -> Unit,
) = composable<T> { entry -> ProvideSharedElements(shared, this) { content(entry) } }

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
