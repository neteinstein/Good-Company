package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.repository.CircleCoverRepository
import com.neteinstein.goodcompany.core.domain.repository.CoverShrinker
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.repository.coverOf
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

// Circles (decision D1). The code keeps the older "group" names; the UI says "circle".

/** Circles with "last time together" and their reminder status, most overdue first. */
class ObserveGroupsUseCase(
    private val groups: GroupRepository,
    private val friends: FriendRepository,
    private val hangouts: HangoutRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<List<Relationships.GroupStatus>> = combine(groups.groups, friends.friends, hangouts.hangouts) { gs, fs, hs ->
        val today = clock.today()
        Relationships.groupStatuses(gs, fs, hs, today, clock.timeZone)
            .sortedWith(compareByDescending<Relationships.GroupStatus> { it.isOverdue(today) }.thenBy { it.group.name.lowercase() })
    }
}

class SaveGroupUseCase(private val groups: GroupRepository, private val ids: IdGenerator) {
    /**
     * Creates (id null) or updates a circle. Needs a name and at least one member. [meetEveryDays] is the optional
     * "Remind me when we haven't met" goal (null or 0 = off). An update keeps the circle's snooze.
     */
    suspend operator fun invoke(
        id: String?,
        name: String,
        emoji: String,
        color: CircleColor,
        members: Collection<FriendId>,
        meetEveryDays: Int?,
    ): FriendGroup {
        require(name.isNotBlank()) { "Name required" }
        require(members.isNotEmpty()) { "A circle needs at least one friend" }
        val existing = id?.let { groups.groups.first().firstOrNull { g -> g.id == it } }
        val group = FriendGroup(
            id = id ?: ids.next(),
            name = name.trim(),
            emoji = emoji.ifBlank { DEFAULT_EMOJI },
            memberIds = members.distinct(),
            meetEveryDays = meetEveryDays?.takeIf { it > 0 },
            snoozedUntil = existing?.snoozedUntil,
            color = color,
        )
        groups.upsert(group)
        return group
    }

    companion object {
        const val DEFAULT_EMOJI = "👥"
    }
}

/** The colour a new circle starts with: the next one in turn after the circles that exist. */
class NextCircleColorUseCase(private val groups: GroupRepository) {
    suspend operator fun invoke(): CircleColor {
        val used = groups.groups.first().map { it.color }
        return CircleColor.entries.firstOrNull { it !in used } ?: CircleColor.forIndex(used.size)
    }
}

/**
 * Deletes a circle and its cover (issue #88); Home goes back to "All" if it was filtered by it. Returns the circle with
 * its cover, for Undo. Its plans and reminders keep their `circleId`, which reads as no circle until Undo.
 */
class DeleteGroupUseCase(
    private val groups: GroupRepository,
    private val settings: SettingsRepository,
    private val covers: CircleCoverRepository = CircleCoverRepository.None,
) {
    suspend operator fun invoke(id: String): FriendGroup? {
        val group = groups.groups.first().firstOrNull { it.id == id }
        val cover = covers.covers.first()[id]
        groups.delete(id)
        if (cover != null) covers.set(id, null)
        settings.update { if (it.homeCircleFilter == id) it.copy(homeCircleFilter = null) else it }
        return group?.copy(cover = cover ?: group.cover)
    }
}

/**
 * Undo for [DeleteGroupUseCase] (and for [SetCircleMemberUseCase]): puts the circle back as it was, with its cover when
 * it had one (members who were deleted meanwhile are dropped).
 */
class RestoreGroupUseCase(
    private val groups: GroupRepository,
    private val covers: CircleCoverRepository = CircleCoverRepository.None,
) {
    suspend operator fun invoke(group: FriendGroup) {
        groups.upsert(group)
        group.cover?.let { covers.set(group.id, it) }
    }
}

/**
 * A circle's cover photo (issue #88): set from a picked, wide-cropped picture (shrunk to the cover limit), removed, or
 * put back by Undo. Works for the built-in Family circle too (covers are keyed by circle id).
 */
class CircleCoverUseCases(private val covers: CircleCoverRepository, private val shrinker: CoverShrinker) {
    /** Sets or replaces the cover. Returns the cover it had (null for none) wrapped in [Result]; a failure when [image] can't be read. */
    suspend fun set(circleId: String, image: ByteArray?): Result<ContactPhoto?> {
        val cover = shrinker.coverOf(image) ?: return Result.failure(IllegalArgumentException("Unreadable picture"))
        val before = covers.covers.first()[circleId]
        covers.set(circleId, cover)
        return Result.success(before)
    }

    /** Removes the cover (the circle shows its colour again). Returns the cover it had, for Undo. */
    suspend fun remove(circleId: String): ContactPhoto? {
        val before = covers.covers.first()[circleId] ?: return null
        covers.set(circleId, null)
        return before
    }

    /** Undo: puts [cover] back (null removes the one set since). */
    suspend fun restore(circleId: String, cover: ContactPhoto?) = covers.set(circleId, cover)
}

/** The circle Home is filtered by (null = All). Remembered between launches. */
class SetHomeCircleFilterUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(groupId: String?) = settings.update { it.copy(homeCircleFilter = groupId) }
}

/**
 * Puts one person in a circle or takes them out (issue #70: the chips and the "+ Circle" sheet on their page). Returns
 * the circle as it was, for Undo ([RestoreGroupUseCase]); null when nothing changed (an unknown or built-in circle, or
 * they were already in / out). Taking out the last member keeps the circle, with nobody in it: its name, colour,
 * reminder and plans stay, and the editor can add people again.
 */
class SetCircleMemberUseCase(private val groups: GroupRepository) {
    suspend operator fun invoke(groupId: String, friendId: FriendId, member: Boolean): FriendGroup? {
        val group = groups.groups.first().firstOrNull { it.id == groupId } ?: return null
        if (group.isBuiltIn || (friendId in group.memberIds) == member) return null
        groups.upsert(group.copy(memberIds = if (member) group.memberIds + friendId else group.memberIds - friendId))
        return group
    }
}
