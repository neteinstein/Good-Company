package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
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

/** Deletes a circle; Home goes back to "All" if it was filtered by it. Returns the circle, for Undo. */
class DeleteGroupUseCase(private val groups: GroupRepository, private val settings: SettingsRepository) {
    suspend operator fun invoke(id: String): FriendGroup? {
        val group = groups.groups.first().firstOrNull { it.id == id }
        groups.delete(id)
        settings.update { if (it.homeCircleFilter == id) it.copy(homeCircleFilter = null) else it }
        return group
    }
}

/** Undo for [DeleteGroupUseCase]: puts the circle back as it was (members who were deleted meanwhile are dropped). */
class RestoreGroupUseCase(private val groups: GroupRepository) {
    suspend operator fun invoke(group: FriendGroup) = groups.upsert(group)
}

/** The circle Home is filtered by (null = All). Remembered between launches. */
class SetHomeCircleFilterUseCase(private val settings: SettingsRepository) {
    suspend operator fun invoke(groupId: String?) = settings.update { it.copy(homeCircleFilter = groupId) }
}
