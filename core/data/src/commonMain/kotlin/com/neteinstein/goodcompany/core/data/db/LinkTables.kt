package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.awaitAsList
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.PersonLink

// Links between people (issue #39). `friend` is observed too: deleting a friend removes their links by cascade.
internal val LINK_TABLES = arrayOf("person_link", "friend")

internal suspend fun GoodCompanyDatabase.readLinks(): List<PersonLink> =
    personLinkQueries.allLinks().awaitAsList().mapNotNull { row ->
        PersonLink(
            id = row.id,
            from = FriendId(row.from_friend_id),
            to = FriendId(row.to_friend_id),
            type = LinkType.parse(row.type) ?: return@mapNotNull null,
            startedOn = row.started_on?.toLocalDate(),
            endedOn = row.ended_on?.toLocalDate(),
            note = row.note,
            remindAnniversary = row.remind_anniversary.toFlag(),
            addedAt = row.added_at?.toInstant(),
        )
    }

/** Adds or updates [link]; skipped when either person isn't stored, or when it links someone to themself. */
internal suspend fun GoodCompanyDatabase.writeLink(link: PersonLink) {
    if (link.from == link.to) return
    personLinkQueries.upsert(
        id = link.id,
        fromId = link.from.value,
        toId = link.to.value,
        type = link.type.name,
        startedOn = link.startedOn?.toColumn(),
        endedOn = link.endedOn?.toColumn(),
        note = link.note,
        remindAnniversary = link.remindAnniversary.toColumn(),
        addedAt = link.addedAt?.toColumn(),
    )
}
