package com.neteinstein.goodcompany.core.domain.repository

import com.neteinstein.goodcompany.core.domain.model.AppLanguage
import com.neteinstein.goodcompany.core.domain.model.AppUpdate
import com.neteinstein.goodcompany.core.domain.model.BackupKey
import com.neteinstein.goodcompany.core.domain.model.CalendarEvent
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.DeviceCalendar
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.NewBackupKey
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.model.RenderedNotification
import kotlin.time.Instant

/** Current position, if the platform and the user allow it. */
interface LocationProvider {
    suspend fun currentLocation(): GeoPoint?
}

/** Replaces the full set of scheduled local notifications. */
interface NotificationScheduler {
    suspend fun replaceAll(notifications: List<RenderedNotification>)

    /** Shows one notification right away, outside the schedule (Settings → "Send a test notification", issue #98). */
    suspend fun showNow(notification: RenderedNotification)
}

/** Turns a [PlannedNotification] into localised text + actions (implemented with UI string resources). */
interface NotificationRenderer {
    suspend fun render(notification: PlannedNotification): RenderedNotification

    /** Neutral text shown on the lock screen when notifications are private ("You have a reminder"). */
    suspend fun privateText(): String
}

/** Names of the built-in circles, in the UI language (the domain never formats text). */
fun interface BuiltInCircleNames {
    /** The Family circle (F6). */
    suspend fun family(): String
}

/**
 * The words of a kinship search in the UI language (F7): [possessives] ("my"), [connectors] ("and") and, per
 * relation, the words that ask for it ("nieces", "nephews"…). They live in `:core:ui` resources, never in domain code.
 */
data class KinshipWords(
    val possessives: List<String> = emptyList(),
    val connectors: List<String> = emptyList(),
    val words: Map<LinkType, List<String>> = emptyMap(),
)

/** Supplies [KinshipWords] for the current UI language. */
fun interface KinshipVocabulary {
    suspend fun words(): KinshipWords
}

/**
 * Where the app's language (issue #174) meets the platform: [apply] switches screens, notifications and widgets to it
 * right away; [systemChoice] is the language the platform's own per-app setting holds (Android 13+'s app language,
 * the iOS Settings app), or null where there is none (the web, Android 12L).
 */
interface AppLanguagePlatform {
    fun systemChoice(): AppLanguage?

    fun apply(language: AppLanguage)

    /** No platform: tests and previews. */
    object None : AppLanguagePlatform {
        override fun systemChoice(): AppLanguage? = null

        override fun apply(language: AppLanguage) = Unit
    }
}

/** The user's UI language as a BCP-47 tag (e.g. "pt-PT"), used to ask the AI to answer in it. */
fun interface LanguageProvider {
    fun languageTag(): String
}

/** Short, single-shot AI helpers (no web search). Never called without an API key. */
interface AiAssistant {
    /** A warm, short birthday message in [languageTag], personalised with the friend's tastes. */
    suspend fun birthdayMessage(apiKey: String, friend: FriendProfile, languageTag: String): Result<String>

    /** 3–6 gift ideas for the friend (or for [kidName], one of their kids), in [languageTag]. */
    suspend fun giftIdeas(apiKey: String, friend: FriendProfile, kidName: String?, kidLikes: List<String>, languageTag: String): Result<List<String>>

    /**
     * One short "what's new" line about the friend, read from something shared into the app (a post's text, a link,
     * a screenshot), in [languageTag]. Null when there's nothing about their life in it.
     */
    suspend fun lifeUpdateFromShared(
        apiKey: String,
        friend: FriendProfile,
        text: String?,
        image: com.neteinstein.goodcompany.core.domain.model.SharedImage?,
        languageTag: String,
    ): Result<String?>
}

/** Fetches a URL's body as text (feeds and the pages they're discovered on). Throws on network / HTTP errors. */
fun interface FeedFetcher {
    suspend fun fetch(url: String): String
}

/** Generates ideas with an LLM. Implementations must never be called without an API key. */
interface IdeaGenerator {
    suspend fun generate(request: IdeaRequest): Result<List<GeneratedIdea>>
}

data class IdeaRequest(
    val apiKey: String,
    val mode: Mode,
    val friends: List<FriendProfile>,
    val area: String?,
    val todayIso: String,
    /** Language to write titles/descriptions in (BCP-47). */
    val languageTag: String = "en",
) {
    enum class Mode {
        /** Events near [area] the listed friends would enjoy together. */
        WEEKLY_EVENTS,

        /** News and conversation topics matched to one friend's tastes and recent chats. */
        TALKING_POINTS,
    }
}

data class FriendProfile(
    val id: String,
    val name: String,
    val likes: List<String>,
    val dislikes: List<String>,
    val kids: List<String>,
    val recentMessages: List<String>,
    /** What's new in their life, newest first (from "What's new"). */
    val news: List<String> = emptyList(),
    val job: String = "",
    val livesIn: String = "",
    /** Partner, parents, siblings, pets: "PARTNER: Tiago (likes padel)". Kids are in [kids]. */
    val family: List<String> = emptyList(),
    val food: List<String> = emptyList(),
    val drinks: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    /** The last few journal entries about them, newest first. */
    val journal: List<String> = emptyList(),
    /** The note on your last meetup, if any. */
    val lastMeetupNote: String = "",
)

data class GeneratedIdea(
    val friendIds: List<String>,
    val kind: String,
    val title: String,
    val description: String,
    val url: String?,
    val whenText: String?,
)

/** A backup file in the user's own cloud storage (Google Drive). */
data class CloudBackupInfo(val id: String, val modifiedAt: Instant?)

/** Why a cloud backup call failed, so the UI can say something useful. */
class CloudBackupFailure(val reason: Reason, detail: String? = null) : Exception(detail ?: reason.name) {
    enum class Reason {
        /** The access token was refused (expired or revoked): authorise again. */
        UNAUTHORIZED,

        /** Drive refused the call: the Drive API isn't enabled for this app's Google Cloud project, or no access. */
        FORBIDDEN,

        /** Drive storage is full. */
        STORAGE_FULL,
        NETWORK,
        OTHER,
    }
}

/** Where in the user's Google Drive a backup lives (D5). */
enum class CloudSpace {
    /** The app's private folder (`drive.appdata`): hidden from the Drive UI. Where encrypted backups go. */
    APP_DATA,

    /** A visible file in My Drive (`drive.file`): only the old plain "Good Company backup.json" lives here. */
    VISIBLE,
}

/**
 * The user's Google Drive, reached with an OAuth access token the UI obtained (scopes `drive.appdata` and
 * `drive.file`: the app sees only its private folder and the files it created). There is no server of ours in between.
 */
interface CloudBackupStore {
    /** Creates the backup file in [space], or replaces the newest one with the same [fileName] there. */
    suspend fun save(accessToken: String, fileName: String, content: String, space: CloudSpace = CloudSpace.APP_DATA): CloudBackupInfo

    /** The newest backup file called [fileName] in [space], or null when there isn't one. */
    suspend fun latest(accessToken: String, fileName: String, space: CloudSpace = CloudSpace.APP_DATA): CloudBackupInfo?

    suspend fun download(accessToken: String, id: String): String

    /** Moves a file to the Drive bin (the user can still get it back from there for 30 days). */
    suspend fun trash(accessToken: String, id: String)
}

/** What asking Google for a Drive token without any screen gave (issue #166). */
sealed interface SilentDriveToken {
    data class Token(val accessToken: String) : SilentDriveToken

    /** Google needs the user (sign-in or consent): only the app can ask, on "Back up now". */
    data object SignInNeeded : SilentDriveToken

    /** No OAuth client for this build's package and signing certificate. */
    data object NotConfigured : SilentDriveToken

    data class Failed(val network: Boolean, val detail: String? = null) : SilentDriveToken
}

/**
 * Drive tokens for work that runs with no screen: the automatic backup (issue #166, Android only). Uses the access the
 * user already granted in the app; never shows anything.
 */
interface DriveTokenSource {
    suspend fun tokenWithoutUi(): SilentDriveToken

    /** Drops a token Drive refused (revoked or expired), so the next request gets a fresh one. */
    suspend fun forget(accessToken: String)

    /** Platforms with no automatic backup (iOS, web): there's never a token without the user. */
    object None : DriveTokenSource {
        override suspend fun tokenWithoutUi(): SilentDriveToken = SilentDriveToken.SignInNeeded

        override suspend fun forget(accessToken: String) = Unit
    }
}

/**
 * D4 (issue #165): encrypts backups. The key is derived on the device from the password (PBKDF2); see
 * `CryptographyBackupCrypto` in `:core:data` for the format and parameters.
 */
interface BackupCrypto {
    /** A new random backup key, wrapped by [password] and by a new recovery key. */
    suspend fun newKey(password: String): NewBackupKey

    /** [key] with its password slot re-made for [newPassword]; the data key and the recovery slot stay. */
    suspend fun changePassword(key: BackupKey, newPassword: String): BackupKey

    /** Whether [secret] (the password or the recovery key) opens [key]. */
    suspend fun unlocks(key: BackupKey, secret: String): Boolean

    /** The backup document [json], encrypted with [key], as the text of a backup file. */
    suspend fun encrypt(key: BackupKey, json: String): String

    /** Whether [raw] is an encrypted backup file (whatever its key). */
    fun isEncrypted(raw: String): Boolean

    /** Opens an encrypted backup file with the password or the recovery key; null when [secret] doesn't open it. */
    suspend fun decrypt(raw: String, secret: String): DecryptedBackup?
}

/** An opened backup file: the backup document, and the key it was made with (for a device that has none yet). */
data class DecryptedBackup(val json: String, val key: BackupKey)

/** Where the sideload build learns about newer builds of itself (the `test-build` GitHub release). */
fun interface AppUpdateSource {
    /** The newest published build, or null when none is published. Throws when it can't be reached. */
    suspend fun latest(): AppUpdate?
}

/**
 * Makes a picture small enough for a [ContactPhoto]: at most [ContactPhoto.MAX_SIDE_PX] on its longest side, as JPEG.
 * Implemented in `:core:ui` (it needs an image codec); [None] keeps pictures that are already small enough.
 */
fun interface PhotoShrinker {
    /** The shrunk image, or null when [image] can't be decoded. */
    suspend fun shrink(image: ByteArray): ByteArray?

    companion object {
        val None = PhotoShrinker { it }
    }
}

/**
 * Makes a picture small enough for a circle's cover (issue #88): at most [ContactPhoto.COVER_MAX_SIDE_PX] on its long
 * side, as JPEG within [ContactPhoto.COVER_MAX_BYTES]. Implemented in `:core:ui` with the same codec as [PhotoShrinker].
 */
fun interface CoverShrinker {
    /** The shrunk image, or null when [image] can't be decoded. */
    suspend fun shrink(image: ByteArray): ByteArray?

    companion object {
        val None = CoverShrinker { it }
    }
}

/** The demo circle's cover photo (issue #88): an original image bundled with the app, so the sample shows a cover. */
fun interface SampleCoverSource {
    /** The cover, or null when it can't be read. */
    suspend fun cover(): ContactPhoto?

    companion object {
        val None = SampleCoverSource { null }
    }
}

/** A cover from [image], shrunk first when it's too large to keep as it is. */
suspend fun CoverShrinker.coverOf(image: ByteArray?): ContactPhoto? {
    if (image == null || image.isEmpty()) return null
    if (image.size <= ContactPhoto.COVER_MAX_BYTES) return ContactPhoto.cover(image)
    return ContactPhoto.cover(runCatching { shrink(image) }.getOrNull())
}

/** A [ContactPhoto] from [image], shrunk first when it's too large to keep as it is. */
suspend fun PhotoShrinker.photoOf(image: ByteArray?): ContactPhoto? {
    if (image == null || image.isEmpty()) return null
    if (image.size <= ContactPhoto.MAX_BYTES) return ContactPhoto.of(image)
    return ContactPhoto.of(runCatching { shrink(image) }.getOrNull())
}

/**
 * Reads the device calendars (decision D3): Android `CalendarContract` (READ_CALENDAR), iOS EventKit (full access).
 * Never used unless the user turned calendar suggestions on; what it returns stays on the device. The web has none.
 */
interface CalendarReader {
    /** False where the platform can't read calendars (the web). */
    val isSupported: Boolean

    /** Whether the app may read calendars right now (the permission was granted). */
    suspend fun hasAccess(): Boolean

    /** Every calendar on the device; empty without access. */
    suspend fun calendars(): List<DeviceCalendar>

    /** Occurrences from the given calendars overlapping [from, to); empty without access. */
    suspend fun events(
        calendarIds: Set<String>,
        from: Instant,
        to: Instant,
    ): List<CalendarEvent>

    object None : CalendarReader {
        override val isSupported = false

        override suspend fun hasAccess() = false

        override suspend fun calendars() = emptyList<DeviceCalendar>()

        override suspend fun events(calendarIds: Set<String>, from: Instant, to: Instant) =
            emptyList<CalendarEvent>()
    }
}
