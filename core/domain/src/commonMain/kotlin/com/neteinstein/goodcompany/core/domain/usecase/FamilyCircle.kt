package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.repository.BuiltInCircleNames
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The built-in Family circle (F6, question 2): everyone whose [Friend.relationToMe] is a family one, in A–Z order.
 * It is never stored (so it isn't in backups and can't be edited or deleted); it exists while it has members.
 */
object FamilyCircle {
    const val ID = "builtin-family"
    const val EMOJI = "🏡"
    val COLOR = CircleColor.RUST

    fun members(friends: List<Friend>): List<Friend> = friends.filter { it.isFamily }.sortedBy { it.displayName.lowercase() }

    /** The circle, named [name]; null when nobody is marked as family. */
    fun of(friends: List<Friend>, name: String): FriendGroup? = members(friends).takeIf { it.isNotEmpty() }?.let { family ->
        FriendGroup(id = ID, name = name, emoji = EMOJI, memberIds = family.map { it.id }, color = COLOR)
    }
}

/** True for a circle the app makes itself (Family): it can't be edited or deleted. */
val FriendGroup.isBuiltIn: Boolean get() = id == FamilyCircle.ID

/**
 * The stored circles plus the built-in [FamilyCircle] first, so Family shows up wherever circles do (Home filter,
 * check-in, Log, share). Writes to the built-in circle are ignored; everything else goes to [stored].
 */
class GroupsWithFamilyCircle(
    private val stored: GroupRepository,
    friends: FriendRepository,
    private val names: BuiltInCircleNames,
) : GroupRepository {
    override val groups: Flow<List<FriendGroup>> = combine(stored.groups, friends.friends) { groups, fs ->
        listOfNotNull(FamilyCircle.of(fs, names.family())) + groups.filterNot { it.isBuiltIn }
    }

    override suspend fun upsert(group: FriendGroup) {
        if (!group.isBuiltIn) stored.upsert(group)
    }

    override suspend fun delete(id: String) {
        if (id != FamilyCircle.ID) stored.delete(id)
    }
}
