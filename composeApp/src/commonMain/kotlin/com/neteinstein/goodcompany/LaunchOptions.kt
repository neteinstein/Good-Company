package com.neteinstein.goodcompany

import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import com.neteinstein.goodcompany.ui.navigation.TopLevelDestination

/**
 * Optional start-up options — used by the web build's URL query (`?demo&tab=together&theme=dark`) for demos
 * and for the README screenshot pipeline (scripts/screenshots). Mobile builds start with the defaults.
 */
data class LaunchOptions(
    val loadSampleData: Boolean = false,
    val startTab: TopLevelDestination? = null,
    val openFriendId: String? = null,
    val theme: ThemeMode? = null,
    val showOnboarding: Boolean = false,
    /** Opens "Your year in friendship" on top of the start tab. */
    val showReview: Boolean = false,
) {
    companion object {
        /** Parses `demo`, `tab=<friends|together|agenda|ideas|settings>`, `friend=<id>`, `theme=<light|dark>`, `onboarding`, `review`. */
        fun parse(query: String): LaunchOptions {
            val params = query.removePrefix("?").split('&').filter { it.isNotBlank() }.associate {
                val (k, v) = (it.split('=', limit = 2) + "").take(2)
                k.lowercase() to v
            }
            return LaunchOptions(
                loadSampleData = "demo" in params,
                startTab = params["tab"]?.let { tab -> TopLevelDestination.entries.firstOrNull { it.name.equals(tab, ignoreCase = true) } },
                openFriendId = params["friend"]?.takeIf { it.isNotBlank() },
                theme = params["theme"]?.let { t -> ThemeMode.entries.firstOrNull { it.name.equals(t, ignoreCase = true) } },
                showOnboarding = "onboarding" in params,
                showReview = "review" in params,
            )
        }
    }
}

/** Applies the data-side launch options (sample data, theme, onboarding state). */
class ApplyLaunchOptions(private val loadSampleData: LoadSampleDataUseCase, private val updateSettings: UpdateSettingsUseCase) {
    suspend operator fun invoke(options: LaunchOptions) {
        if (options.loadSampleData) {
            loadSampleData()
            updateSettings { it.copy(onboardingDone = true) }
        }
        if (options.showOnboarding) updateSettings { it.copy(onboardingDone = false) }
        options.theme?.let { theme -> updateSettings { it.copy(themeMode = theme, dynamicColor = false) } }
    }
}
