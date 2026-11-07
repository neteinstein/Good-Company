package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Saved groups with "last time all together" and their cadence status, most overdue first. */
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
    /** Creates (id null) or updates a group. Needs a name and at least two members. */
    suspend operator fun invoke(id: String?, name: String, emoji: String, members: Collection<FriendId>, meetEveryDays: Int?): FriendGroup {
        require(name.isNotBlank()) { "Name required" }
        require(members.distinct().size >= 2) { "A group needs at least two friends" }
        val group = FriendGroup(id ?: ids.next(), name.trim(), emoji.ifBlank { "👥" }, members.distinct(), meetEveryDays?.takeIf { it > 0 })
        groups.upsert(group)
        return group
    }
}

class DeleteGroupUseCase(private val groups: GroupRepository) {
    suspend operator fun invoke(id: String) = groups.delete(id)
}
