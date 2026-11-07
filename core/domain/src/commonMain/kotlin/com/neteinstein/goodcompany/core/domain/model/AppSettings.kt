package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppSettings(
    /** User-supplied Anthropic API key. Required for every AI feature; never leaves the device except to the API. */
    val aiApiKey: String? = null,
    val weeklyIdeasEnabled: Boolean = true,
    val lastIdeasRunAt: Instant? = null,
    /** Free-text city/area used to localise event suggestions when no GPS fix is available. */
    val homeArea: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Hour of day (local) at which date-based reminders fire. */
    val reminderHour: Int = 9,
    /** First-run onboarding completed (or skipped). */
    val onboardingDone: Boolean = false,
) {
    val hasAiKey: Boolean get() = !aiApiKey.isNullOrBlank()
}
