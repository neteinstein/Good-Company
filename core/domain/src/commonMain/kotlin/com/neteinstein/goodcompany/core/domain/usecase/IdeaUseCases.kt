package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.repository.FriendProfile
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GeneratedIdea
import com.neteinstein.goodcompany.core.domain.repository.IdeaGenerator
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRequest
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.LocationProvider
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.days

/** Thrown (wrapped in a failed [Result]) when an AI feature is used without an API key. */
class MissingApiKeyException : IllegalStateException("Add an Anthropic API key in Settings to use AI features.")

class ObserveIdeasUseCase(private val ideas: IdeaRepository) {
    operator fun invoke(): Flow<List<Idea>> =
        ideas.ideas.map { list -> list.filterNot { it.dismissed }.sortedByDescending { it.createdAt } }
}

class DismissIdeaUseCase(private val ideas: IdeaRepository) {
    suspend operator fun invoke(id: String) = ideas.dismiss(id)
}

/** Maps raw model output onto domain ideas, dropping anything that references unknown friends. */
internal object IdeaMapper {
    fun map(
        generated: List<GeneratedIdea>,
        known: Map<String, Friend>,
        fallbackFriends: List<FriendId>,
        defaultKind: IdeaKind,
        clock: AppClock,
        ids: IdGenerator,
    ): List<Idea> = generated.mapNotNull { g ->
        if (g.title.isBlank()) return@mapNotNull null
        val friendIds = g.friendIds.mapNotNull { known[it]?.id }.ifEmpty { fallbackFriends }
        if (friendIds.isEmpty()) return@mapNotNull null
        Idea(
            id = ids.next(),
            friendIds = friendIds.distinct(),
            kind = IdeaKind.entries.firstOrNull { it.name.equals(g.kind, ignoreCase = true) } ?: defaultKind,
            title = g.title.trim(),
            description = g.description.trim(),
            url = g.url?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
            whenText = g.whenText?.takeIf { it.isNotBlank() },
            createdAt = clock.now(),
        )
    }

    fun profile(friend: Friend, messages: List<ConversationMessage>): FriendProfile = FriendProfile(
        id = friend.id.value,
        name = friend.name,
        likes = friend.likes,
        dislikes = friend.dislikes,
        kids = friend.kids.map { k -> if (k.likes.isEmpty()) k.name else "${k.name} (likes ${k.likes.joinToString()})" },
        recentMessages = messages.filter { it.friendId == friend.id }.sortedBy { it.at }.takeLast(RECENT_MESSAGES)
            .map { (if (it.fromMe) "Me: " else "${friend.name}: ") + it.text },
    )

    private const val RECENT_MESSAGES = 25
}

/** News + conversation starters matched to one friend's tastes and recent chats. Requires an API key. */
class GenerateTalkingPointsUseCase(
    private val friends: FriendRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
    private val ideas: IdeaRepository,
    private val generator: IdeaGenerator,
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(id: FriendId): Result<Int> {
        val s = settings.settings.first()
        val key = s.aiApiKey?.takeIf { it.isNotBlank() } ?: return Result.failure(MissingApiKeyException())
        val friend = friends.get(id) ?: return Result.success(0)
        val request = IdeaRequest(
            apiKey = key,
            mode = IdeaRequest.Mode.TALKING_POINTS,
            friends = listOf(IdeaMapper.profile(friend, interactions.messages.first())),
            area = s.homeArea.ifBlank { null },
            todayIso = clock.today().toString(),
        )
        return generator.generate(request).map { generated ->
            val mapped = IdeaMapper.map(generated, mapOf(friend.id.value to friend), listOf(friend.id), IdeaKind.TALKING_POINT, clock, ids)
            ideas.addAll(mapped)
            mapped.size
        }
    }
}

sealed interface WeeklyIdeasOutcome {
    data class Skipped(val reason: Reason) : WeeklyIdeasOutcome

    data class Done(val ideas: Int) : WeeklyIdeasOutcome

    data class Failed(val error: Throwable) : WeeklyIdeasOutcome

    enum class Reason { DISABLED, NO_API_KEY, NOT_DUE, NO_FRIENDS_OPTED_IN }
}

/**
 * The weekly job: for friends opted in to suggestions, find events near you they'd enjoy.
 * Runs at most once every 7 days unless [invoke] is called with `force = true`.
 * Scheduled by WorkManager on Android; on iOS/web it catches up on app launch.
 */
class RunWeeklyIdeasUseCase(
    private val friends: FriendRepository,
    private val interactions: InteractionRepository,
    private val settings: SettingsRepository,
    private val ideas: IdeaRepository,
    private val generator: IdeaGenerator,
    private val location: LocationProvider,
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(force: Boolean = false): WeeklyIdeasOutcome {
        val s = settings.settings.first()
        if (!force && !s.weeklyIdeasEnabled) return WeeklyIdeasOutcome.Skipped(WeeklyIdeasOutcome.Reason.DISABLED)
        val key = s.aiApiKey?.takeIf { it.isNotBlank() }
            ?: return WeeklyIdeasOutcome.Skipped(WeeklyIdeasOutcome.Reason.NO_API_KEY)
        if (!force && !isDue(s)) return WeeklyIdeasOutcome.Skipped(WeeklyIdeasOutcome.Reason.NOT_DUE)
        val optedIn = friends.friends.first().filter { it.suggestEvents }.take(MAX_FRIENDS_PER_RUN)
        if (optedIn.isEmpty()) return WeeklyIdeasOutcome.Skipped(WeeklyIdeasOutcome.Reason.NO_FRIENDS_OPTED_IN)

        val area = s.homeArea.ifBlank { null }
            ?: runCatching { location.currentLocation() }.getOrNull()?.let { "near ${it.latitude}, ${it.longitude}" }
        val messages = interactions.messages.first()
        val request = IdeaRequest(
            apiKey = key,
            mode = IdeaRequest.Mode.WEEKLY_EVENTS,
            friends = optedIn.map { IdeaMapper.profile(it, messages) },
            area = area,
            todayIso = clock.today().toString(),
        )
        return generator.generate(request).fold(
            onSuccess = { generated ->
                val known = optedIn.associateBy { it.id.value }
                val mapped = IdeaMapper.map(generated, known, emptyList(), IdeaKind.EVENT, clock, ids)
                ideas.addAll(mapped)
                settings.update { it.copy(lastIdeasRunAt = clock.now()) }
                WeeklyIdeasOutcome.Done(mapped.size)
            },
            onFailure = { WeeklyIdeasOutcome.Failed(it) },
        )
    }

    fun isDue(s: AppSettings): Boolean = s.lastIdeasRunAt?.let { clock.now() - it >= 7.days } ?: true

    companion object {
        const val MAX_FRIENDS_PER_RUN = 12
    }
}
