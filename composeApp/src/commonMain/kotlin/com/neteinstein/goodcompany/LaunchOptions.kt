package com.neteinstein.goodcompany

import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ShareInbox
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
    /** Opens this friend's catch-up story on top of the start tab. */
    val openStoryFriendId: String? = null,
    /** Something shared into the installed web app (Web Share Target: `share_title`, `share_text`, `share_url`). */
    val shared: SharedContent? = null,
) {
    companion object {
        private fun sharedFrom(params: Map<String, String>): SharedContent? {
            fun param(name: String) = params[name]?.let(::percentDecode)?.trim()?.takeIf { it.isNotEmpty() }
            val title = param("share_title")
            val text = param("share_text")
            val url = param("share_url")
            // The title stays apart (it heads the link card, D6); alone, it is the text.
            val body = text ?: title.takeIf { url == null }
            return SharedContent(text = body, url = url, title = title.takeIf { it != body }).takeUnless { it.isEmpty }
        }

        /** `application/x-www-form-urlencoded` → text (UTF-8, `+` = space). Malformed escapes are kept as typed. */
        fun percentDecode(raw: String): String {
            val bytes = ArrayList<Byte>(raw.length)
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                when {
                    c == '+' -> bytes += ' '.code.toByte()
                    c == '%' && i + 2 <= raw.lastIndex && raw.substring(i + 1, i + 3).toIntOrNull(16) != null -> {
                        bytes += raw.substring(i + 1, i + 3).toInt(16).toByte()
                        i += 2
                    }
                    else -> c.toString().encodeToByteArray().forEach { bytes += it }
                }
                i++
            }
            return bytes.toByteArray().decodeToString()
        }

        /**
         * Parses `demo`, `tab=<friends|together|agenda|ideas|settings>`, `friend=<id>`, `theme=<light|dark>`, `onboarding`,
         * `review`, `story=<id>`, and the Web Share Target's `share_title` / `share_text` / `share_url` (percent-encoded).
         */
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
                openStoryFriendId = params["story"]?.takeIf { it.isNotBlank() },
                shared = sharedFrom(params),
            )
        }
    }
}

/** Applies the data-side launch options (sample data, theme, onboarding state, an incoming web share). */
class ApplyLaunchOptions(
    private val loadSampleData: LoadSampleDataUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val shareInbox: ShareInbox,
) {
    suspend operator fun invoke(options: LaunchOptions) {
        options.shared?.let(shareInbox::offer)
        if (options.loadSampleData) {
            loadSampleData()
            updateSettings { it.copy(onboardingDone = true) }
        }
        if (options.showOnboarding) updateSettings { it.copy(onboardingDone = false) }
        options.theme?.let { theme -> updateSettings { it.copy(themeMode = theme, dynamicColor = false) } }
    }
}
