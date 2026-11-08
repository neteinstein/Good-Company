package com.neteinstein.goodcompany.core.domain.repository

import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.model.RenderedNotification

/** Current position, if the platform and the user allow it. */
interface LocationProvider {
    suspend fun currentLocation(): GeoPoint?
}

/** Replaces the full set of scheduled local notifications. */
interface NotificationScheduler {
    suspend fun replaceAll(notifications: List<RenderedNotification>)
}

/** Turns a [PlannedNotification] into localised text + actions (implemented with UI string resources). */
interface NotificationRenderer {
    suspend fun render(notification: PlannedNotification): RenderedNotification

    /** Neutral text shown on the lock screen when notifications are private ("You have a reminder"). */
    suspend fun privateText(): String
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
data class CloudBackupInfo(val id: String, val modifiedAt: kotlin.time.Instant?)

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

/**
 * The user's Google Drive, reached with an OAuth access token the UI obtained (scope `drive.file`: the app sees
 * only the files it created). There is no server of ours in between.
 */
interface CloudBackupStore {
    /** Creates the backup file, or replaces the newest one with the same [fileName]. */
    suspend fun save(accessToken: String, fileName: String, content: String): CloudBackupInfo

    /** The newest backup file called [fileName], or null when there isn't one. */
    suspend fun latest(accessToken: String, fileName: String): CloudBackupInfo?

    suspend fun download(accessToken: String, id: String): String
}
