package com.neteinstein.goodcompany

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neteinstein.goodcompany.composeApp.resources.Res
import com.neteinstein.goodcompany.composeApp.resources.app_name
import com.neteinstein.goodcompany.composeApp.resources.storage_temporary
import com.neteinstein.goodcompany.core.domain.usecase.QuickEntryInbox
import com.neteinstein.goodcompany.core.domain.usecase.ShareInbox
import com.neteinstein.goodcompany.core.ui.components.AppLockScreen
import com.neteinstein.goodcompany.core.ui.components.ContentWidth
import com.neteinstein.goodcompany.core.ui.platform.rememberDeviceAuthenticator
import com.neteinstein.goodcompany.core.ui.resources.lock_reason
import com.neteinstein.goodcompany.core.ui.theme.GoodCompanyTheme
import com.neteinstein.goodcompany.core.ui.theme.OnColorGradients
import com.neteinstein.goodcompany.feature.friends.ui.screens.choose.ChooseContactsScreen
import com.neteinstein.goodcompany.feature.onboarding.ui.screens.OnboardingScreen
import com.neteinstein.goodcompany.ui.navigation.AppNavHost
import com.neteinstein.goodcompany.ui.navigation.CatchUpStoryRoute
import com.neteinstein.goodcompany.ui.navigation.FriendDetailRoute
import com.neteinstein.goodcompany.ui.navigation.FriendsRoute
import com.neteinstein.goodcompany.ui.navigation.LogInteractionRoute
import com.neteinstein.goodcompany.ui.navigation.ShareRoute
import com.neteinstein.goodcompany.ui.navigation.TopLevelDestination
import com.neteinstein.goodcompany.ui.navigation.YearReviewRoute
import com.neteinstein.goodcompany.ui.navigation.navigateTopLevel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** Root composable shared by Android, iOS and Web. */
@Composable
fun App(launch: LaunchOptions = LaunchOptions(), viewModel: AppViewModel = koinViewModel()) {
    val applyLaunchOptions: ApplyLaunchOptions = koinInject()
    LaunchedEffect(launch) { applyLaunchOptions(launch) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    GoodCompanyTheme(themeMode = state.settings.themeMode, dynamicColor = state.settings.dynamicColor) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (state.isReady) {
                if (state.storageIsTemporary) TemporaryStorageBanner()
                AnimatedContent(
                    targetState = state.settings.onboardingDone,
                    transitionSpec = { (fadeIn(tween(400)) + scaleIn(initialScale = 0.96f)) togetherWith fadeOut(tween(250)) },
                ) { done ->
                    if (done) {
                        AppScaffold(launch)
                    } else {
                        OnboardingScreen(
                            onDone = {},
                            contactList = { onClose -> ChooseContactsScreen(onBack = { onClose(0) }, onDone = onClose) },
                        )
                    }
                }
                if (state.settings.appLock) AppLock()
            }
        }
    }
}

/** Shown over everything when this web tab only got temporary storage (see AGENTS.md gotcha 24). */
@Composable
private fun TemporaryStorageBanner() {
    Box(Modifier.fillMaxSize().zIndex(1f), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = MaterialTheme.shapes.large,
            shadowElevation = 6.dp,
        ) {
            Text(stringResource(Res.string.storage_temporary), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AppScaffold(launch: LaunchOptions) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination
    val selected = TopLevelDestination.entries.firstOrNull { d -> current?.hierarchy?.any { it.hasRoute(d.route::class) } == true }
    val showChrome = selected != null
    val start = launch.startTab?.route ?: FriendsRoute
    LaunchedEffect(launch.openFriendId) { launch.openFriendId?.let { navController.navigate(FriendDetailRoute(it)) } }
    LaunchedEffect(launch.openStoryFriendId) { launch.openStoryFriendId?.let { navController.navigate(CatchUpStoryRoute(it)) } }
    LaunchedEffect(launch.showReview) { if (launch.showReview) navController.navigate(YearReviewRoute()) }
    // Something shared from another app (Android intent, iOS share extension, web share target).
    val shareInbox: ShareInbox = koinInject()
    val pendingShare by shareInbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShare) { if (pendingShare != null) navController.navigate(ShareRoute) { launchSingleTop = true } }
    // An app-icon shortcut ("Log a meetup", or one of the recent people): opens the Log sheet, under the app lock.
    val quickEntryInbox: QuickEntryInbox = koinInject()
    val pendingEntry by quickEntryInbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingEntry) {
        val entry = pendingEntry ?: return@LaunchedEffect
        navController.navigate(LogInteractionRoute(entry.friendIds.map { it.value }, entry.kind.name))
        quickEntryInbox.clear()
    }

    BoxWithConstraints {
        val wide = maxWidth >= 720.dp
        if (wide) {
            // Tablets / desktop browsers: navigation rail + centred content column.
            Row {
                NavigationRail(Modifier.fillMaxHeight(), header = { AppMark() }) {
                    Spacer(Modifier.weight(1f))
                    TopLevelDestination.entries.forEach { d ->
                        NavigationRailItem(
                            selected = d == selected,
                            onClick = { navController.navigateTopLevel(d) },
                            icon = { Icon(if (d == selected) d.selectedIcon else d.icon, null) },
                            label = { Text(stringResource(d.label)) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Box(Modifier.weight(1f)) { ContentWidth { AppNavHost(navController, startDestination = start) } }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    AnimatedVisibility(showChrome, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            TopLevelDestination.entries.forEach { d ->
                                NavigationBarItem(
                                    selected = d == selected,
                                    onClick = { navController.navigateTopLevel(d) },
                                    icon = { Icon(if (d == selected) d.selectedIcon else d.icon, null) },
                                    label = { Text(stringResource(d.label), maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                },
            ) { padding -> AppNavHost(navController, Modifier.padding(padding), startDestination = start) }
        }
    }
}

/**
 * Covers the app until the device lock (biometrics / PIN) confirms the user. Locks again when the app has been in the
 * background for more than [RELOCK_AFTER] — short trips to a file picker or share sheet don't count.
 */
@Composable
private fun AppLock() {
    val authenticator = rememberDeviceAuthenticator()
    if (!authenticator.isAvailable) return // Lock removed from the device: don't trap the user out of their data.
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var stoppedAt by remember { mutableStateOf<TimeMark?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { stoppedAt = TimeSource.Monotonic.markNow() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (stoppedAt?.let { it.elapsedNow() > RELOCK_AFTER } == true) unlocked = false
        stoppedAt = null
    }
    if (unlocked) return
    val reason = stringResource(CoreRes.string.lock_reason)
    AppLockScreen(onUnlock = { authenticator.authenticate(reason) { ok -> if (ok) unlocked = true } })
}

private val RELOCK_AFTER = 60.seconds

/** The two-hearts brand mark, drawn in Compose (no bitmap) for the navigation rail header. */
@Composable
private fun AppMark() {
    Box(
        Modifier.padding(top = 12.dp).size(48.dp).background(
            Brush.linearGradient(OnColorGradients.Hero),
            CircleShape,
        ),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Favorite, contentDescription = stringResource(Res.string.app_name), tint = Color.White) }
}
