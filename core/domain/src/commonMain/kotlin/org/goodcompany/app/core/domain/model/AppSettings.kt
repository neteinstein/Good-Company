package org.goodcompany.app.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.goodcompany.app.core.domain.usecase.CatchUpPolicy
import org.goodcompany.app.core.domain.usecase.LogKind
import kotlin.time.Instant

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Settings → Notifications (issue #98): a master switch, one switch per [NotificationType], and whether the important
 * ones stay on screen. Everything on by default, so nothing changes for people who never open the screen.
 */
@Serializable
data class NotificationSettings(
    val enabled: Boolean = true,
    val birthdays: Boolean = true,
    val events: Boolean = true,
    val reminders: Boolean = true,
    val plans: Boolean = true,
    val catchUps: Boolean = true,
    val calendar: Boolean = true,
    /** "Keep important ones on screen": persistent notifications for what matters (see [NotificationPlanner]). */
    val persistent: Boolean = true,
    /** Issue #116: the daily "log your day" reminder, on by default. */
    val dailyLog: Boolean = true,
    /** Issue #116: when it comes, in minutes after midnight (21:00 by default). */
    val dailyLogMinute: Int = DEFAULT_DAILY_LOG_MINUTE,
    /**
     * Issue #133: "Heads-up before birthdays", how many days before a birthday an early notice comes (0 = off). One of
     * [BIRTHDAY_HEADS_UP_CHOICES]; a week by default. Only while [birthdays] is on.
     */
    val birthdayHeadsUpDays: Int = DEFAULT_BIRTHDAY_HEADS_UP_DAYS,
) {
    /** [dailyLogMinute] as a time of day (kept within the day). */
    val dailyLogTime: LocalTime
        get() = dailyLogMinute.coerceIn(0, MINUTES_PER_DAY - 1).let { LocalTime(it / 60, it % 60) }

    fun allows(type: NotificationType): Boolean = enabled &&
        when (type) {
            NotificationType.BIRTHDAYS -> birthdays
            NotificationType.EVENTS -> events
            NotificationType.REMINDERS -> reminders
            NotificationType.PLANS -> plans
            NotificationType.CATCH_UPS -> catchUps
            NotificationType.CALENDAR -> calendar
            NotificationType.DAILY_LOG -> dailyLog
        }

    fun with(type: NotificationType, on: Boolean): NotificationSettings = when (type) {
        NotificationType.BIRTHDAYS -> copy(birthdays = on)
        NotificationType.EVENTS -> copy(events = on)
        NotificationType.REMINDERS -> copy(reminders = on)
        NotificationType.PLANS -> copy(plans = on)
        NotificationType.CATCH_UPS -> copy(catchUps = on)
        NotificationType.CALENDAR -> copy(calendar = on)
        NotificationType.DAILY_LOG -> copy(dailyLog = on)
    }

    companion object {
        const val DEFAULT_DAILY_LOG_MINUTE = 21 * 60
        const val DEFAULT_BIRTHDAY_HEADS_UP_DAYS = 7

        /** Off · 1 day · 2 days · 3 days · 1 week · 2 weeks (issue #133). */
        val BIRTHDAY_HEADS_UP_CHOICES = listOf(0, 1, 2, 3, 7, 14)
        private const val MINUTES_PER_DAY = 24 * 60
    }
}

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
    /** Default "meet every N days" per closeness (Closest / Close / Friends; stored under their old "circle" names). */
    val innerCircleDays: Int = 7,
    val closeCircleDays: Int = 30,
    val goodFriendsDays: Int = 90,
    /**
     * Issue #69: Acquaintances' default "meet every N days"; null (the default) = no catch-up rhythm. Kept with the
     * device settings (no schema change), and carried by backups like the rest of the settings.
     */
    val acquaintanceDays: Int? = null,
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
    /**
     * Issue #166: how often the automatic Drive backup runs (Android only; iOS and the web keep "Back up now").
     * Device-only like [lastDriveBackupAt]: never exported, kept on restore.
     */
    val autoDriveBackup: AutoBackupFrequency = AutoBackupFrequency.DAILY,
    /** Issue #166: the last automatic Drive backup attempt and whether it worked. Device-only. */
    val lastAutoBackup: AutoBackupRun? = null,
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
    /** The activities the Log popup offers (F3), in the user's order; never empty. Defaults to all of them. */
    val activities: List<HangoutActivity> = HangoutActivity.entries,
    /** Your own activities (issue #121), in your order, offered after the built-in ones. */
    val customActivities: List<String> = emptyList(),
    /**
     * The built-in "How?" options the Log popup offers (issue #152), in the user's order (the tiebreak below "most used
     * first"); never empty. Defaults to all of them in [LogKind.DEFAULT_ORDER].
     */
    val logKinds: List<LogKind> = LogKind.DEFAULT_ORDER,
    /**
     * Your own "How?" options (issue #152), in your order, offered after the built-in ones. An entry logged with one is
     * an [InteractionType.OTHER] interaction with the name as its [Interaction.label].
     */
    val customLogKinds: List<String> = emptyList(),
    /**
     * Decision 2026-11-21 ("Catch-up · what counts"): the built-in ways of keeping in touch that restart the rhythm of
     * someone on the default ([org.goodcompany.app.core.domain.model.CatchUpCounts.DEFAULT]). In person always
     * counts and journal entries never do, whatever this holds.
     */
    val catchUpKinds: Set<LogKind> = CatchUpPolicy.DEFAULT_KINDS,
    /** The How options of your own ([customLogKinds]) that count the same way; none by default. */
    val catchUpCustomKinds: List<String> = emptyList(),
    /**
     * Issue #68: the day "Not now" hid Today's "When did you last see…?" card; it shows again the next day.
     * Device-only like [lastOpenedAt]: never exported, kept on restore.
     */
    val lastSeenCardHiddenOn: LocalDate? = null,
    /** Issue #98: Settings → Notifications. Kept with the device settings (no schema change); backups carry it. */
    val notifications: NotificationSettings = NotificationSettings(),
    /**
     * Issue #98: "Not now" on Today's "Turn on notifications" card. Device-only like [lastSeenCardHiddenOn]: never
     * exported, kept on restore.
     */
    val notificationPrimerDismissed: Boolean = false,
    /**
     * Issue #119: the one-line "the app keeps a small preview, the original stays in your gallery" was shown, the first
     * time media went on a note. Device-only: never exported, kept on restore.
     */
    val mediaExplained: Boolean = false,
    /** Issue #120: the one-time legend tip on People (ring and circle-dot colours) was dismissed. Device-only. */
    val peopleLegendSeen: Boolean = false,
    /** Issue #138: how "Everything coming up" shows, the month calendar by default. Device-only: never exported. */
    val agendaView: AgendaView = AgendaView.CALENDAR,
    /**
     * Issue #174: the app's language, [AppLanguage.SYSTEM] (the device's) by default. Device-only: never exported, kept
     * on restore, so a new phone starts on the system language.
     */
    val language: AppLanguage = AppLanguage.SYSTEM,
) {
    val hasAiKey: Boolean get() = !aiApiKey.isNullOrBlank()

    /** The key to call the AI with: null while AI is off or no key is saved. Every AI use case goes through this. */
    val usableAiKey: String? get() = aiApiKey?.takeIf { aiEnabled && it.isNotBlank() }
}

/**
 * The languages the app ships (issue #174), or [SYSTEM] to follow the device. [tag] is the BCP-47 tag the app runs in
 * and asks the AI to answer in (Portuguese is European); null for [SYSTEM].
 */
@Serializable
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    PORTUGUESE("pt-PT"),
    SPANISH("es"),
    FRENCH("fr"),
    ROMANIAN("ro"),
    ;

    /** The language subtag ("pt" for "pt-PT"); null for [SYSTEM]. */
    val language: String? get() = tag?.substringBefore('-')

    /** The region subtag ("PT" for "pt-PT"), or "" when there is none. */
    val region: String get() = tag?.substringAfter('-', "").orEmpty()

    companion object {
        /** The shipped language a platform tag ("pt-BR", "en_US", "fr") stands for; [SYSTEM] for none or another one. */
        fun fromTag(tag: String?): AppLanguage {
            val language = tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase()
            if (language.isNullOrEmpty()) return SYSTEM
            return entries.firstOrNull { it.language == language } ?: SYSTEM
        }
    }
}

/** The two views of "Everything coming up" (issue #138). */
@Serializable
enum class AgendaView { CALENDAR, LIST }
