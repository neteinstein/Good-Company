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
    /** Default "meet every N days" per circle. */
    val innerCircleDays: Int = 7,
    val closeCircleDays: Int = 30,
    val goodFriendsDays: Int = 90,
    /** Gather catch-up nudges into one weekly note instead of one per friend. */
    val weeklyDigest: Boolean = true,
    /** Day of the weekly digest (ISO: 1 = Monday … 7 = Sunday). */
    val digestDay: Int = 6,
    /** Hide names and details from notifications on the lock screen. */
    val privateNotifications: Boolean = false,
    /** Ask for fingerprint / face / device PIN when the app opens. */
    val appLock: Boolean = false,
    /** Show the warmth number and "Drifting apart" (off = softer view without scores). */
    val showWarmth: Boolean = true,
    /** Last time the app was opened, so nudges can back off when it's ignored. */
    val lastOpenedAt: Instant? = null,
    /** When the last copy was saved to the user's Google Drive (device-specific; kept on restore). */
    val lastDriveBackupAt: Instant? = null,
) {
    val hasAiKey: Boolean get() = !aiApiKey.isNullOrBlank()
}
