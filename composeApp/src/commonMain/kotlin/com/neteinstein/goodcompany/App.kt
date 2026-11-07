package com.neteinstein.goodcompany

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.neteinstein.goodcompany.core.ui.theme.GoodCompanyTheme
import com.neteinstein.goodcompany.ui.navigation.AppNavHost
import com.neteinstein.goodcompany.ui.navigation.TopLevelDestination
import com.neteinstein.goodcompany.ui.navigation.navigateTopLevel
import org.koin.compose.viewmodel.koinViewModel

/** Root composable shared by Android, iOS and Web. */
@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GoodCompanyTheme(themeMode = state.settings.themeMode, dynamicColor = state.settings.dynamicColor) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppScaffold()
        }
    }
}

@Composable
private fun AppScaffold() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination
    val selected = TopLevelDestination.entries.firstOrNull { d -> current?.hierarchy?.any { it.hasRoute(d.route::class) } == true }
    val showChrome = selected != null

    BoxWithConstraints {
        val wide = maxWidth >= 720.dp
        if (wide) {
            // Tablets / desktop browsers: navigation rail on the side.
            Row {
                NavigationRail(Modifier.fillMaxHeight()) {
                    TopLevelDestination.entries.forEach { d ->
                        NavigationRailItem(
                            selected = d == selected,
                            onClick = { navController.navigateTopLevel(d) },
                            icon = { Icon(if (d == selected) d.selectedIcon else d.icon, null) },
                            label = { Text(d.label) },
                        )
                    }
                }
                AppNavHost(navController, Modifier.weight(1f))
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                bottomBar = {
                    AnimatedVisibility(showChrome, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                        NavigationBar {
                            TopLevelDestination.entries.forEach { d ->
                                NavigationBarItem(
                                    selected = d == selected,
                                    onClick = { navController.navigateTopLevel(d) },
                                    icon = { Icon(if (d == selected) d.selectedIcon else d.icon, null) },
                                    label = { Text(d.label, maxLines = 1) },
                                )
                            }
                        }
                    }
                },
            ) { padding -> AppNavHost(navController, Modifier.padding(padding)) }
        }
    }
}
