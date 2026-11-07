package com.neteinstein.goodcompany

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray
import com.neteinstein.goodcompany.composeApp.resources.Res as AppRes
import com.neteinstein.goodcompany.composeApp.resources.allPluralStringResources as appPlurals
import com.neteinstein.goodcompany.composeApp.resources.allStringArrayResources as appArrays
import com.neteinstein.goodcompany.composeApp.resources.allStringResources as appStrings
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes
import com.neteinstein.goodcompany.core.ui.resources.allPluralStringResources as corePlurals
import com.neteinstein.goodcompany.core.ui.resources.allStringArrayResources as coreArrays
import com.neteinstein.goodcompany.core.ui.resources.allStringResources as coreStrings
import com.neteinstein.goodcompany.feature.agenda.resources.Res as AgendaRes
import com.neteinstein.goodcompany.feature.agenda.resources.allPluralStringResources as agendaPlurals
import com.neteinstein.goodcompany.feature.agenda.resources.allStringArrayResources as agendaArrays
import com.neteinstein.goodcompany.feature.agenda.resources.allStringResources as agendaStrings
import com.neteinstein.goodcompany.feature.friends.resources.Res as FriendsRes
import com.neteinstein.goodcompany.feature.friends.resources.allPluralStringResources as friendsPlurals
import com.neteinstein.goodcompany.feature.friends.resources.allStringArrayResources as friendsArrays
import com.neteinstein.goodcompany.feature.friends.resources.allStringResources as friendsStrings
import com.neteinstein.goodcompany.feature.hangouts.resources.Res as HangoutsRes
import com.neteinstein.goodcompany.feature.hangouts.resources.allPluralStringResources as hangoutsPlurals
import com.neteinstein.goodcompany.feature.hangouts.resources.allStringArrayResources as hangoutsArrays
import com.neteinstein.goodcompany.feature.hangouts.resources.allStringResources as hangoutsStrings
import com.neteinstein.goodcompany.feature.ideas.resources.Res as IdeasRes
import com.neteinstein.goodcompany.feature.ideas.resources.allPluralStringResources as ideasPlurals
import com.neteinstein.goodcompany.feature.ideas.resources.allStringArrayResources as ideasArrays
import com.neteinstein.goodcompany.feature.ideas.resources.allStringResources as ideasStrings
import com.neteinstein.goodcompany.feature.onboarding.resources.Res as OnboardingRes
import com.neteinstein.goodcompany.feature.onboarding.resources.allPluralStringResources as onboardingPlurals
import com.neteinstein.goodcompany.feature.onboarding.resources.allStringArrayResources as onboardingArrays
import com.neteinstein.goodcompany.feature.onboarding.resources.allStringResources as onboardingStrings
import com.neteinstein.goodcompany.feature.review.resources.Res as ReviewRes
import com.neteinstein.goodcompany.feature.review.resources.allPluralStringResources as reviewPlurals
import com.neteinstein.goodcompany.feature.review.resources.allStringArrayResources as reviewArrays
import com.neteinstein.goodcompany.feature.review.resources.allStringResources as reviewStrings
import com.neteinstein.goodcompany.feature.settings.resources.Res as SettingsRes
import com.neteinstein.goodcompany.feature.settings.resources.allPluralStringResources as settingsPlurals
import com.neteinstein.goodcompany.feature.settings.resources.allStringArrayResources as settingsArrays
import com.neteinstein.goodcompany.feature.settings.resources.allStringResources as settingsStrings

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
