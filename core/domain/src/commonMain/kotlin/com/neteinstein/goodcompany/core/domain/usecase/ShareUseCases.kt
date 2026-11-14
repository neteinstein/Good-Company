package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.repository.AiAssistant
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.InteractionRepository
import com.neteinstein.goodcompany.core.domain.repository.LanguageProvider
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Shares waiting for the user to pick a friend. Platforms [offer] what the share sheet handed them (Android intent,
 * iOS share extension inbox, web share target); the app shows the "Save to a friend" screen while [pending] is set.
 * In memory only: an unsaved share is simply dropped.
 */
class ShareInbox {
    private val state = MutableStateFlow<SharedContent?>(null)
    val pending: StateFlow<SharedContent?> = state.asStateFlow()

    fun offer(content: SharedContent) {
        if (!content.isEmpty) state.value = content
    }

    fun clear() {
        state.value = null
    }
}

/** Ranks friends for a share: people named in the shared text first (full name, "firstlast" handle or first name), then real friends, then A–Z. */
object ShareMatcher {
    fun rank(friends: List<Friend>, content: SharedContent): List<Friend> {
        val haystack = haystack(content)
        return friends.sortedWith(
            compareByDescending<Friend> { isMentioned(it, haystack) }.thenByDescending { it.isCloseFriend }.thenBy { it.name.lowercase() },
        )
    }

    fun mentions(friends: List<Friend>, content: SharedContent): Set<FriendId> {
        val haystack = haystack(content)
        return friends.filter { isMentioned(it, haystack) }.map { it.id }.toSet()
    }

    private fun haystack(content: SharedContent) = listOfNotNull(content.text, content.url).joinToString(" ").lowercase()

    private fun isMentioned(f: Friend, haystack: String): Boolean {
        if (haystack.isBlank()) return false
        val full = f.name.lowercase().trim()
        val first = full.substringBefore(' ')
        return full in haystack ||
            (full.contains(' ') && full.replace(" ", "") in haystack) ||
            (first.length >= MIN_FIRST_NAME && Regex("\\b${Regex.escape(first)}\\b").containsMatchIn(haystack))
    }

    private const val MIN_FIRST_NAME = 3
}

/** Adds a "What's new" line to a friend (from the share screen, or anywhere else). Blank text is ignored. */
class AddLifeUpdateUseCase(private val friends: FriendRepository, private val clock: AppClock, private val ids: IdGenerator) {
    suspend operator fun invoke(id: FriendId, text: String): Boolean {
        val clean = text.trim().takeIf { it.isNotEmpty() } ?: return false
        val friend = friends.get(id) ?: return false
        friends.upsert(friend.copy(lifeUpdates = friend.lifeUpdates + LifeUpdate(ids.next(), clean, clock.now())))
        return true
    }
}

/** AI: turns a shared post / screenshot into one "what's new" line about the friend. Needs the user's key. */
class SuggestUpdateFromSharedUseCase(
    private val friends: FriendRepository,
    private val interactions: InteractionRepository,
    private val hangouts: HangoutRepository,
    private val settings: SettingsRepository,
    private val assistant: AiAssistant,
    private val language: LanguageProvider,
) {
    /** The suggested line, or null when the AI found no news about them in what was shared. */
    suspend operator fun invoke(id: FriendId, content: SharedContent): Result<String?> {
        val key = settings.settings.first().aiApiKey?.takeIf { it.isNotBlank() } ?: return Result.failure(MissingApiKeyException())
        val friend = friends.get(id) ?: return Result.failure(NoSuchElementException("friend"))
        val text = listOfNotNull(content.text?.trim()?.takeIf { it.isNotEmpty() }, content.url?.trim()?.takeIf { it.isNotEmpty() })
            .distinct()
            .joinToString("\n")
            .take(MAX_TEXT)
            .ifEmpty { null }
        return assistant.lifeUpdateFromShared(
            key,
            IdeaMapper.profile(friend, interactions, hangouts),
            text,
            content.image,
            language.languageTag(),
        )
            .map { it?.trim()?.takeIf(String::isNotEmpty) }
    }

    private companion object {
        const val MAX_TEXT = 4_000
    }
}
