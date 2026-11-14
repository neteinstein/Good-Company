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
    /** Default "meet every N days" per closeness (Inner / Close / Good friends; stored under their old "circle" names). */
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
    /** The circle ([FriendGroup] id) Home is filtered by; null = All. Remembered between launches. */
    val homeCircleFilter: String? = null,
    /**
     * Calendar suggestions (D3): off by default. Device-only like the AI key: never exported, kept on restore, and
     * the events themselves are never stored anywhere.
     */
    val calendarSuggestions: Boolean = false,
    /** The device calendars the app may read; none until the user ticks them. */
    val calendarIds: List<String> = emptyList(),
    /** When suggestions were last turned on: earlier events are shown under "Looking back". */
    val calendarOnSince: Instant? = null,
    /**
     * Spellings of one tag the user chose to keep apart in Settings → Tags ("Keep both"), each as `Tags.signature`.
     * Stored with the device settings (no schema change); backups carry it like the rest of the settings.
     */
    val keptTagSpellings: Set<String> = emptySet(),
    /**
     * The AI switch (F1). Off (the default) hides every AI entry point: the Ideas tab, "Things to talk about", gift and
     * message suggestions, and the weekly job. Turned on for people who already had a key when it was introduced.
     */
    val aiEnabled: Boolean = false,
    /** The activities the log sheet offers (F3), in the user's order; never empty. Defaults to all of them. */
    val activities: List<HangoutActivity> = HangoutActivity.entries,
) {
    val hasAiKey: Boolean get() = !aiApiKey.isNullOrBlank()

    /** The key to call the AI with: null while AI is off or no key is saved. Every AI use case goes through this. */
    val usableAiKey: String? get() = aiApiKey?.takeIf { aiEnabled && it.isNotBlank() }
}
