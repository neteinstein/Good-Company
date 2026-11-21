package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.repository.CoverShrinker
import com.neteinstein.goodcompany.core.domain.repository.PersonBackgroundRepository
import com.neteinstein.goodcompany.core.domain.repository.coverOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The background image of a person's page (issue #144), set from the photo options on their page. It works like a
 * circle's cover ([CircleCoverUseCases]): a picked picture cropped wide, shrunk with the same [CoverShrinker] to the
 * cover limits, removed, or put back by Undo. App-owned: contacts never see it.
 */
class PersonBackgroundUseCases(private val backgrounds: PersonBackgroundRepository, private val shrinker: CoverShrinker) {
    /** Their background, or null for none (the page shows its usual gradient). */
    fun observe(friendId: FriendId): Flow<ContactPhoto?> = backgrounds.backgrounds.map { it[friendId] }.distinctUntilChanged()

    /** Sets or replaces the background. Returns the one it had (null for none) wrapped in [Result]; a failure when [image] can't be read. */
    suspend fun set(friendId: FriendId, image: ByteArray?): Result<ContactPhoto?> {
        val background = shrinker.coverOf(image) ?: return Result.failure(IllegalArgumentException("Unreadable picture"))
        val before = backgrounds.backgrounds.first()[friendId]
        backgrounds.set(friendId, background)
        return Result.success(before)
    }

    /** Removes the background. Returns the one it had, for Undo; null when there was none. */
    suspend fun remove(friendId: FriendId): ContactPhoto? {
        val before = backgrounds.backgrounds.first()[friendId] ?: return null
        backgrounds.set(friendId, null)
        return before
    }

    /** Undo: puts [background] back (null removes the one set since). */
    suspend fun restore(friendId: FriendId, background: ContactPhoto?) = backgrounds.set(friendId, background)
}
