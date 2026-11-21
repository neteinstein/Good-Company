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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.toRoute
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.PlanOccurrence
import com.neteinstein.goodcompany.core.domain.usecase.FamilyCircle
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.TimelineEntryRef
import com.neteinstein.goodcompany.core.ui.components.LocalSharedElement
import com.neteinstein.goodcompany.core.ui.platform.sheetDialogProperties
import com.neteinstein.goodcompany.feature.agenda.ui.screens.AgendaScreen
import com.neteinstein.goodcompany.feature.agenda.ui.screens.calendar.CalendarSuggestionsScreen
import com.neteinstein.goodcompany.feature.agenda.ui.screens.today.TodayScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.choose.ChooseContactsScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.detail.FriendDetailScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.family.SuggestedFamilyScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.list.FriendsScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.share.ShareToFriendScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.story.CatchUpStoryScreen
import com.neteinstein.goodcompany.feature.friends.ui.screens.timeline.FriendTimelineScreen
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.HangoutsScreen
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.circle.CircleEditorScreen
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.free.WhosFreeScreen
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.met.LogPrefill
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.met.WeMetPanelScreen
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.page.CirclePageScreen
import com.neteinstein.goodcompany.feature.ideas.ui.screens.IdeasScreen
import com.neteinstein.goodcompany.feature.ideas.ui.screens.plans.PlansScreen
import com.neteinstein.goodcompany.feature.review.ui.screens.YearReviewScreen
import com.neteinstein.goodcompany.feature.review.ui.screens.network.NetworkMapScreen
import com.neteinstein.goodcompany.feature.review.ui.screens.network.PeopleDashboardScreen
import com.neteinstein.goodcompany.feature.settings.ui.screens.SettingsScreen
import com.neteinstein.goodcompany.feature.settings.ui.screens.activities.ActivitiesScreen
import com.neteinstein.goodcompany.feature.settings.ui.screens.calendar.CalendarSettingsScreen
import com.neteinstein.goodcompany.feature.settings.ui.screens.notifications.NotificationSettingsScreen
import com.neteinstein.goodcompany.feature.settings.ui.screens.places.PlacesScreen
import com.neteinstein.goodcompany.feature.settings.ui.screens.tags.TagsScreen
import kotlinx.datetime.LocalDate

const val APP_VERSION = "1.0.0"

/** Saved-state key: how many people "Choose friends" added, read by Home. */
private const val CONTACTS_ADDED = "contacts_added"
private const val DELETE_CIRCLE = "delete_circle"

/** Saved-state key on the Plans tab: the person whose gift list a "Gift lists" link should open (issue #104). */
private const val GIFT_LISTS_FOR = "gift_lists_for"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Any = TodayRoute,
    /** The AI switch (F1): Plans shows the weekly ideas only while it is on. */
    aiEnabled: Boolean = false,
) {
    SharedTransitionLayout(modifier) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn(tween(220)) + scaleIn(initialScale = 0.98f) },
            exitTransition = { fadeOut(tween(160)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(160)) + scaleOut(targetScale = 0.98f) },
        ) {
            AppGraph(this).appDestinations(nav = { navController }, shared = { this@SharedTransitionLayout }, aiEnabled = aiEnabled)
        }
    }
}

/**
 * Every destination of the app. [nav] and [shared] are read only when a destination shows, so a test can list the
 * registered routes without a NavController ([AppRoute] lists every route; `AppRoutesTest` checks each one is here).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun AppGraph.appDestinations(nav: () -> NavHostController, shared: () -> SharedTransitionScope, aiEnabled: Boolean) {
    val navController: NavHostController by lazy(nav)
    val openFriend: (FriendId) -> Unit = { navController.navigate(FriendDetailRoute(it.value)) }
    val openStory: (FriendId) -> Unit = { navController.navigate(CatchUpStoryRoute(it.value)) }
    // Settings is a screen of its own, opened from every tab header (issue #65).
    val openSettings: () -> Unit = { navController.navigate(SettingsRoute) { launchSingleTop = true } }
    // The built-in Family circle (F6) has no editor: it fills itself from "relationship to me".
    val editCircle: (String?) -> Unit = { if (it != FamilyCircle.ID) navController.navigate(CircleEditorRoute(it)) }
    // Every in-app "We met" opens the small panel (issue #47); the widget and notification still log today directly.
    val weMet: (FriendId) -> Unit = { navController.navigate(WeMetRoute(friendIds = listOf(it.value))) }
    val circleMet: (String) -> Unit = { navController.navigate(WeMetRoute(circleId = it)) }
    // Every circle has a page (issue #88), the built-in Family circle too.
    val openCircle: (String) -> Unit = { navController.navigate(CirclePageRoute(it)) { launchSingleTop = true } }
    // "Did you meet?" → yes: the panel on that occurrence's day with the plan's people and activity; a circle plan lists
    // its members unticked (F20, issue #43).
    val logPlan: (String, LocalDate) -> Unit = { planId, day -> navController.navigate(WeMetRoute(plan = PlanOccurrence(planId, day).encode())) }
    sharedComposable<FriendsRoute>(shared) { entry ->
        // How many people "Choose friends" just added, handed back for Home's snackbar.
        val chosen by entry.savedStateHandle.getStateFlow(CONTACTS_ADDED, 0).collectAsStateWithLifecycle()
        FriendsScreen(
            onOpenFriend = openFriend,
            onEditCircle = editCircle,
            onChooseContacts = { navController.navigate(ChooseContactsRoute) },
            chosenContacts = chosen,
            onChosenShown = { entry.savedStateHandle[CONTACTS_ADDED] = 0 },
            onOpenNetwork = { navController.navigate(NetworkMapRoute) },
            onOpenPeople = { navController.navigate(PeopleDashboardRoute) },
            onOpenSuggestedFamily = { navController.navigate(SuggestedFamilyRoute) { launchSingleTop = true } },
            onOpenSettings = openSettings,
        )
    }
    composable<ChooseContactsRoute>(
        enterTransition = { slideInVertically { it / 4 } + fadeIn() },
        popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
    ) {
        ChooseContactsScreen(
            onBack = { navController.popBackStack(ChooseContactsRoute, inclusive = true) },
            onDone = { added, single ->
                if (single != null) {
                    // One person added: their page, in place of the list (issue #114).
                    navController.navigate(FriendDetailRoute(single.value)) { popUpTo<ChooseContactsRoute> { inclusive = true } }
                } else {
                    navController.previousBackStackEntry?.savedStateHandle?.set(CONTACTS_ADDED, added)
                    navController.popBackStack(ChooseContactsRoute, inclusive = true)
                }
            },
        )
    }
    composable<FriendDetailRoute>(
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) { entry ->
        ProvideSharedElements(shared(), this) {
            val id = entry.toRoute<FriendDetailRoute>().id
            FriendDetailScreen(
                friendId = FriendId(id),
                onBack = { navController.popBackStack() },
                onOpenSettings = openSettings,
                onOpenStory = { navController.navigate(CatchUpStoryRoute(id, fromFriend = true)) },
                onOpenFriend = openFriend,
                onOpenTimeline = { navController.navigate(FriendTimelineRoute(id)) },
                onLog = { navController.navigate(WeMetRoute(friendIds = listOf(id))) },
                // "+ Note" on Notes (issue #103): a dated note is a journal entry on the Log popup.
                onEditEntry = { key -> navController.navigate(WeMetRoute.edit(id, key)) },
                onLogPlan = logPlan,
                onWeMet = { navController.navigate(WeMetRoute(friendIds = listOf(id))) },
                // A circle chip opens that circle's page (issue #88).
                onOpenCircle = openCircle,
                onNewCircle = { navController.navigate(CircleEditorRoute(withFriend = id)) },
                // Issue #104: gift lists live on the Plans tab; the link opens the list they're on there.
                onOpenGiftLists = {
                    navController.navigateTopLevel(TopLevelDestination.PLANS)
                    runCatching { navController.getBackStackEntry<IdeasRoute>().savedStateHandle[GIFT_LISTS_FOR] = id }
                },
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
            onLog = { navController.navigate(WeMetRoute(friendIds = listOf(id))) },
            onEditEntry = { key -> navController.navigate(WeMetRoute.edit(id, key)) },
        )
    }
    dialog<WeMetRoute>(dialogProperties = sheetDialogProperties) { entry ->
        val route = entry.toRoute<WeMetRoute>()
        WeMetPanelScreen(
            friendIds = route.friendIds.map(::FriendId),
            circleId = route.circleId,
            plan = route.plan,
            prefill = LogPrefill(
                type = route.type,
                date = route.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                activity = HangoutActivity.entries.firstOrNull { it.name == route.activity },
                activityName = route.activityName,
                edit = route.editKey?.let { key -> route.editFriend?.let { TimelineEntryRef(FriendId(it), key) } },
            ),
            onClose = { navController.popBackStack() },
        )
    }
    composable<CircleEditorRoute>(
        enterTransition = { slideInVertically { it / 4 } + fadeIn() },
        popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
    ) { entry ->
        val route = entry.toRoute<CircleEditorRoute>()
        CircleEditorScreen(
            groupId = route.id,
            onClose = { navController.popBackStack() },
            withFriend = route.withFriend?.let(::FriendId),
        )
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
    sharedComposable<HangoutsRoute>(shared) { entry ->
        // A circle deleted from its page (issue #88) is deleted here, so the tab offers the Undo.
        val pendingDelete by entry.savedStateHandle.getStateFlow<String?>(DELETE_CIRCLE, null).collectAsStateWithLifecycle()
        HangoutsScreen(
            onOpenCircle = openCircle,
            onEditCircle = editCircle,
            onCircleMet = circleMet,
            onOpenSettings = openSettings,
            pendingDelete = pendingDelete,
            onPendingDeleteHandled = { entry.savedStateHandle[DELETE_CIRCLE] = null },
        )
    }
    composable<CirclePageRoute>(
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) { entry ->
        val id = entry.toRoute<CirclePageRoute>().id
        CirclePageScreen(
            circleId = id,
            onBack = { navController.popBackStack() },
            onOpenFriend = openFriend,
            onWeMet = circleMet,
            onEdit = { editCircle(it) },
            onDelete = { circle ->
                // "Delete" goes back to the Circles tab, which deletes the circle and offers Undo.
                val tabBelow = runCatching { navController.getBackStackEntry<HangoutsRoute>() }.isSuccess
                if (tabBelow) {
                    navController.popBackStack<HangoutsRoute>(inclusive = false)
                } else {
                    navController.popBackStack()
                    navController.navigateTopLevel(TopLevelDestination.CIRCLES)
                }
                runCatching { navController.getBackStackEntry<HangoutsRoute>() }.getOrNull()
                    ?.savedStateHandle?.set(DELETE_CIRCLE, circle)
            },
        )
    }
    sharedComposable<TodayRoute>(shared) {
        TodayScreen(
            onOpenFriend = openFriend,
            onOpenStory = openStory,
            onWeMet = weMet,
            onCircleMet = circleMet,
            onLogPlan = logPlan,
            onOpenAgenda = { navController.navigate(AgendaRoute()) { launchSingleTop = true } },
            onOpenSettings = openSettings,
            onOpenPeople = { navController.navigateTopLevel(TopLevelDestination.PEOPLE) },
            // Issue #116: "I'm with…" became Log: the Log popup with nobody picked, the people picker first.
            onLog = { navController.navigate(WeMetRoute()) },
            onOpenYearReview = { navController.navigate(YearReviewRoute(it)) },
            onOpenCircle = openCircle,
        )
    }
    // "Everything coming up" (the old Coming up tab), pushed from Today.
    composable<AgendaRoute>(
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) { entry ->
        val editReminderId = entry.toRoute<AgendaRoute>().editReminderId
        ProvideSharedElements(shared(), this) {
            AgendaScreen(
                editReminderId = editReminderId,
                onOpenFriend = openFriend,
                onCircleMet = circleMet,
                onLogPlan = logPlan,
                onOpenCalendar = { navController.navigate(CalendarSuggestionsRoute) },
                onBack = { navController.popBackStack() },
                onOpenCircle = openCircle,
            )
        }
    }
    composable<IdeasRoute> { entry ->
        // Plans (issue #67): works with AI off; the weekly ideas card shows only while AI is on (F1).
        // Gift lists open in place here (issue #104); a person's "Gift lists" link sets GIFT_LISTS_FOR.
        val giftListsFor by entry.savedStateHandle.getStateFlow(GIFT_LISTS_FOR, "").collectAsStateWithLifecycle()
        PlansScreen(
            onOpenSettings = openSettings,
            onOpenFriend = openFriend,
            showGiftListsFor = giftListsFor.takeIf { it.isNotEmpty() }?.let(::FriendId),
            onShowGiftListsHandled = { entry.savedStateHandle[GIFT_LISTS_FOR] = "" },
            onOpenWhosFree = { navController.navigate(WhosFreeRoute) { launchSingleTop = true } },
            onOpenIdeas = { navController.navigate(WeeklyIdeasRoute) { launchSingleTop = true } },
            onOpenYearReview = { navController.navigate(YearReviewRoute(it)) },
            onLogPlan = logPlan,
        )
    }
    composable<WeeklyIdeasRoute>(
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) {
        // Turning AI off while here leaves nothing to show (F1): go back to Plans.
        if (aiEnabled) {
            IdeasScreen(onOpenSettings = openSettings, onBack = { navController.popBackStack() })
        } else {
            LaunchedEffect(Unit) { navController.popBackStack() }
        }
    }
    composable<WhosFreeRoute>(
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) {
        WhosFreeScreen(onBack = { navController.popBackStack() }, onOpenFriend = openFriend)
    }
    composable<SettingsRoute>(
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) {
        SettingsScreen(
            onBack = { navController.popBackStack() },
            appVersion = APP_VERSION,
            onOpenPlaces = { navController.navigate(PlacesRoute) },
            onOpenCalendar = { navController.navigate(CalendarSettingsRoute) },
            onOpenNotifications = { navController.navigate(NotificationSettingsRoute) { launchSingleTop = true } },
            onOpenTags = { navController.navigate(TagsRoute) },
            onOpenActivities = { navController.navigate(ActivitiesRoute) },
        )
    }
    composable<PlacesRoute>(
        enterTransition = { slideInVertically { it / 4 } + fadeIn() },
        popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
    ) {
        PlacesScreen(onBack = { navController.popBackStack() })
    }
    composable<NotificationSettingsRoute>(
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) {
        NotificationSettingsScreen(onBack = { navController.popBackStack() })
    }
    composable<CalendarSettingsRoute>(
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) {
        CalendarSettingsScreen(
            onBack = { navController.popBackStack() },
            onOpenSuggestions = { navController.navigate(CalendarSuggestionsRoute) },
        )
    }
    composable<CalendarSuggestionsRoute>(
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) {
        CalendarSuggestionsScreen(
            onBack = { navController.popBackStack() },
            onLog = { s ->
                navController.navigate(
                    WeMetRoute(
                        friendIds = s.friends.map { it.id.value },
                        type = LogKind.MEETUP.name,
                        date = s.date.toString(),
                        activity = s.activity?.name,
                    ),
                )
            },
        )
    }
    composable<ActivitiesRoute> { ActivitiesScreen(onBack = { navController.popBackStack() }) }
    // Settings → Your data → Tags (F16). It was navigated to but never registered (issue #114: the tap crashed).
    composable<TagsRoute>(
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) {
        TagsScreen(onBack = { navController.popBackStack() })
    }
    composable<YearReviewRoute>(
        enterTransition = { slideInVertically { it / 4 } + fadeIn() },
        popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
    ) { entry ->
        YearReviewScreen(
            year = entry.toRoute<YearReviewRoute>().year,
            onBack = { navController.popBackStack() },
            onOpenPeople = { navController.navigate(PeopleDashboardRoute) },
        )
    }
    composable<NetworkMapRoute>(
        enterTransition = { slideInVertically { it / 4 } + fadeIn() },
        popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
    ) {
        NetworkMapScreen(
            onBack = { navController.popBackStack() },
            onOpenFriend = openFriend,
            onOpenDashboard = { navController.navigate(PeopleDashboardRoute) { launchSingleTop = true } },
        )
    }
    composable<PeopleDashboardRoute>(
        enterTransition = { slideInVertically { it / 4 } + fadeIn() },
        popExitTransition = { slideOutVertically { it / 4 } + fadeOut() },
    ) {
        PeopleDashboardScreen(
            onBack = { navController.popBackStack() },
            onOpenFriend = openFriend,
            onOpenMap = { navController.navigate(NetworkMapRoute) { launchSingleTop = true } },
        )
    }
    composable<SuggestedFamilyRoute>(
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
    ) {
        SuggestedFamilyScreen(onBack = { navController.popBackStack() }, onOpenFriend = openFriend)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
private inline fun <reified T : AppRoute> AppGraph.sharedComposable(
    noinline shared: () -> SharedTransitionScope,
    crossinline content: @Composable (NavBackStackEntry) -> Unit,
) = composable<T> { entry -> ProvideSharedElements(shared(), this) { content(entry) } }

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
