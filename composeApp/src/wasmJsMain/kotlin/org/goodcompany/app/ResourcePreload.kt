package org.goodcompany.app

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray
import org.goodcompany.app.composeApp.resources.Res as AppRes
import org.goodcompany.app.composeApp.resources.allPluralStringResources as appPlurals
import org.goodcompany.app.composeApp.resources.allStringArrayResources as appArrays
import org.goodcompany.app.composeApp.resources.allStringResources as appStrings
import org.goodcompany.app.core.ui.resources.Res as CoreRes
import org.goodcompany.app.core.ui.resources.allPluralStringResources as corePlurals
import org.goodcompany.app.core.ui.resources.allStringArrayResources as coreArrays
import org.goodcompany.app.core.ui.resources.allStringResources as coreStrings
import org.goodcompany.app.feature.agenda.resources.Res as AgendaRes
import org.goodcompany.app.feature.agenda.resources.allPluralStringResources as agendaPlurals
import org.goodcompany.app.feature.agenda.resources.allStringArrayResources as agendaArrays
import org.goodcompany.app.feature.agenda.resources.allStringResources as agendaStrings
import org.goodcompany.app.feature.friends.resources.Res as FriendsRes
import org.goodcompany.app.feature.friends.resources.allPluralStringResources as friendsPlurals
import org.goodcompany.app.feature.friends.resources.allStringArrayResources as friendsArrays
import org.goodcompany.app.feature.friends.resources.allStringResources as friendsStrings
import org.goodcompany.app.feature.hangouts.resources.Res as HangoutsRes
import org.goodcompany.app.feature.hangouts.resources.allPluralStringResources as hangoutsPlurals
import org.goodcompany.app.feature.hangouts.resources.allStringArrayResources as hangoutsArrays
import org.goodcompany.app.feature.hangouts.resources.allStringResources as hangoutsStrings
import org.goodcompany.app.feature.ideas.resources.Res as IdeasRes
import org.goodcompany.app.feature.ideas.resources.allPluralStringResources as ideasPlurals
import org.goodcompany.app.feature.ideas.resources.allStringArrayResources as ideasArrays
import org.goodcompany.app.feature.ideas.resources.allStringResources as ideasStrings
import org.goodcompany.app.feature.onboarding.resources.Res as OnboardingRes
import org.goodcompany.app.feature.onboarding.resources.allPluralStringResources as onboardingPlurals
import org.goodcompany.app.feature.onboarding.resources.allStringArrayResources as onboardingArrays
import org.goodcompany.app.feature.onboarding.resources.allStringResources as onboardingStrings
import org.goodcompany.app.feature.review.resources.Res as ReviewRes
import org.goodcompany.app.feature.review.resources.allPluralStringResources as reviewPlurals
import org.goodcompany.app.feature.review.resources.allStringArrayResources as reviewArrays
import org.goodcompany.app.feature.review.resources.allStringResources as reviewStrings
import org.goodcompany.app.feature.settings.resources.Res as SettingsRes
import org.goodcompany.app.feature.settings.resources.allPluralStringResources as settingsPlurals
import org.goodcompany.app.feature.settings.resources.allStringArrayResources as settingsArrays
import org.goodcompany.app.feature.settings.resources.allStringResources as settingsStrings

private class Bundle(
    val strings: Collection<StringResource>,
    val plurals: Collection<PluralStringResource>,
    val arrays: Collection<StringArrayResource>,
)

/**
 * Web only: loads every string of every module once, before the UI starts.
 *
 * In the browser, compose resources load asynchronously and share one in-flight load per string. If the composable
 * that started a load leaves the composition (a list item replaced while sample data arrives, say), that shared load
 * is cancelled and every other label waiting on it stays blank. Loading everything up front in a scope that is never
 * cancelled fills the cache, so composables read finished values. Android and iOS read resources synchronously and
 * don't need this.
 */
suspend fun preloadStringResources() = coroutineScope {
    bundles().flatMap { b ->
        b.strings.map { async { getString(it) } } +
            b.plurals.map { async { getPluralString(it, 1) } } +
            b.arrays.map { async { getStringArray(it) } }
    }.awaitAll()
}

private fun bundles() = listOf(
    Bundle(CoreRes.coreStrings.values, CoreRes.corePlurals.values, CoreRes.coreArrays.values),
    Bundle(AppRes.appStrings.values, AppRes.appPlurals.values, AppRes.appArrays.values),
    Bundle(FriendsRes.friendsStrings.values, FriendsRes.friendsPlurals.values, FriendsRes.friendsArrays.values),
    Bundle(HangoutsRes.hangoutsStrings.values, HangoutsRes.hangoutsPlurals.values, HangoutsRes.hangoutsArrays.values),
    Bundle(AgendaRes.agendaStrings.values, AgendaRes.agendaPlurals.values, AgendaRes.agendaArrays.values),
    Bundle(IdeasRes.ideasStrings.values, IdeasRes.ideasPlurals.values, IdeasRes.ideasArrays.values),
    Bundle(SettingsRes.settingsStrings.values, SettingsRes.settingsPlurals.values, SettingsRes.settingsArrays.values),
    Bundle(OnboardingRes.onboardingStrings.values, OnboardingRes.onboardingPlurals.values, OnboardingRes.onboardingArrays.values),
    Bundle(ReviewRes.reviewStrings.values, ReviewRes.reviewPlurals.values, ReviewRes.reviewArrays.values),
)
