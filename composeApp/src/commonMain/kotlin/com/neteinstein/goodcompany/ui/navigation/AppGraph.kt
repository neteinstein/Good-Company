package com.neteinstein.goodcompany.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import kotlinx.serialization.serializer

/**
 * What [appDestinations] registers into: the real [NavGraphBuilder] in the app, or nothing in a test (navigation can't
 * build destinations on the JVM, it parses routes with `android.net.Uri`). Either way [registered] records each route's
 * serial name, so `AppRoutesTest` can check that every [AppRoute] has a destination (issue #114: an unregistered
 * `TagsRoute` crashed with "Destination with route … cannot be found in navigation graph").
 *
 * [appDestinations] has this as its receiver, not [NavGraphBuilder], so a destination can only be added through
 * [composable] / [dialog] below, which always record it.
 */
class AppGraph(@PublishedApi internal val builder: NavGraphBuilder?) {
    private val names = mutableListOf<String>()

    /** Serial names of the registered routes, in order. */
    val registered: List<String> get() = names

    @PublishedApi
    internal fun record(serialName: String) {
        names += serialName
    }
}

typealias EnterFor = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?
typealias ExitFor = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?

inline fun <reified T : AppRoute> AppGraph.composable(
    noinline enterTransition: EnterFor? = null,
    noinline popExitTransition: ExitFor? = null,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    record(serializer<T>().descriptor.serialName)
    builder?.composable<T>(enterTransition = enterTransition, popExitTransition = popExitTransition, content = content)
}

inline fun <reified T : AppRoute> AppGraph.dialog(
    dialogProperties: DialogProperties = DialogProperties(),
    noinline content: @Composable (NavBackStackEntry) -> Unit,
) {
    record(serializer<T>().descriptor.serialName)
    builder?.dialog<T>(dialogProperties = dialogProperties, content = content)
}
