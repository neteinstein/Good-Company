package com.neteinstein.goodcompany.core.domain.repository

import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification

/** Current position, if the platform and the user allow it. */
interface LocationProvider {
    suspend fun currentLocation(): GeoPoint?
}

/** Replaces the full set of scheduled local notifications. */
interface NotificationScheduler {
    suspend fun replaceAll(notifications: List<PlannedNotification>)
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
)

data class GeneratedIdea(
    val friendIds: List<String>,
    val kind: String,
    val title: String,
    val description: String,
    val url: String?,
    val whenText: String?,
)
