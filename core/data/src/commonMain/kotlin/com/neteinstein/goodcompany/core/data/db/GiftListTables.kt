package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GiftList
import com.neteinstein.goodcompany.core.domain.model.GiftListEntry
import com.neteinstein.goodcompany.core.domain.model.GiftListItem
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.GiftStatus

// Gift lists (F8, issue #42). Deleting a friend drops their entries and items by cascade, hence "friend" below.
internal val GIFT_LIST_TABLES = arrayOf("gift_list", "gift_list_entry", "gift_list_item", "friend")

internal suspend fun GoodCompanyDatabase.readGiftLists(): List<GiftList> {
    val q = giftListQueries
    val items = q.allItems().awaitAsList().groupBy { it.list_id to it.friend_id }
    val entries = q.allEntries().awaitAsList().groupBy { it.list_id }
    return q.allLists().awaitAsList().map { row ->
        GiftList(
            id = row.id,
            title = row.title,
            occasionDate = row.occasion_date?.toLocalDate(),
            createdAt = row.created_at.toInstant(),
            // Unknown occasion → none.
            occasion = GiftOccasion.entries.firstOrNull { it.name == row.occasion },
            entries = entries[row.id].orEmpty().map { e ->
                GiftListEntry(
                    friendId = FriendId(e.friend_id),
                    budget = e.budget,
                    // An unknown status (from a newer app) reads as "to buy" rather than dropping the gift.
                    items = items[row.id to e.friend_id].orEmpty().map { GiftListItem(it.gift_id, GiftStatus.parse(it.status) ?: GiftStatus.TO_BUY) },
                )
            },
        )
    }
}

/** Saves [list] whole: the old people and items go with the delete. Call inside a transaction. */
internal suspend fun GoodCompanyDatabase.writeGiftList(list: GiftList) {
    val q = giftListQueries
    q.deleteList(list.id)
    q.insertList(list.id, list.title, list.occasion?.name, list.occasionDate?.toColumn(), list.createdAt.toColumn())
    list.entries.forEachIndexed { i, entry ->
        q.insertEntry(list.id, entry.friendId.value, entry.budget, i.toLong())
        entry.items.forEachIndexed { j, item -> q.insertItem(list.id, entry.friendId.value, item.giftId, item.status.name, j.toLong()) }
    }
}
