package com.neteinstein.goodcompany

import com.neteinstein.goodcompany.ui.navigation.AppGraph
import com.neteinstein.goodcompany.ui.navigation.AppRoute
import com.neteinstein.goodcompany.ui.navigation.TagsRoute
import com.neteinstein.goodcompany.ui.navigation.TopLevelDestination
import com.neteinstein.goodcompany.ui.navigation.appDestinations
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Navigating to a route that isn't in the graph crashes ("Destination with route … cannot be found in navigation
 * graph", issue #114: Settings → Your data → Tags was never registered, and crashed the release build). Every
 * [AppRoute] must have exactly one destination.
 */
class AppRoutesTest {
    private fun registered(aiEnabled: Boolean = true): List<String> =
        AppGraph(builder = null).apply {
            appDestinations(nav = { error("not shown") }, shared = { error("not shown") }, aiEnabled = aiEnabled)
        }.registered

    /** A sealed serializer's second element lists every subclass, so no reflection is needed. */
    private fun allRoutes(): List<String> = AppRoute.serializer().descriptor.getElementDescriptor(1).let { subclasses ->
        (0 until subclasses.elementsCount).map { subclasses.getElementDescriptor(it).serialName }
    }

    @Test
    fun everyRouteHasADestination() {
        val routes = allRoutes()
        assertTrue(routes.size > 20, "expected every route, got $routes")
        assertEquals(emptyList(), routes - registered().toSet(), "routes with no destination in appDestinations")
    }

    @Test
    fun tagsHasADestination() {
        assertTrue(serializer<TagsRoute>().descriptor.serialName in registered())
    }

    @Test
    fun everyDestinationIsRegisteredOnceWhateverTheAiSwitch() {
        listOf(true, false).forEach { ai ->
            val registered = registered(ai)
            assertEquals(registered.distinct(), registered)
            assertEquals(allRoutes().sorted(), registered.sorted())
        }
    }

    @Test
    fun everyTabHasADestination() {
        val registered = registered()
        TopLevelDestination.entries.forEach { tab ->
            assertTrue(registered.any { it.endsWith("." + tab.route::class.simpleName) }, "${tab.name} has no destination")
        }
    }
}
