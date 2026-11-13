package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.neteinstein.goodcompany.core.data.source.PhoneMatcher
import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.FeedItem
import com.neteinstein.goodcompany.core.domain.model.FriendFeed
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.Friend as DomainFriend
import com.neteinstein.goodcompany.core.domain.model.Gift as DomainGift
import com.neteinstein.goodcompany.core.domain.model.Kid as DomainKid

/** Tables that make up a friend. Observers of friends listen to all of them. */
internal val FRIEND_TABLES = arrayOf(
    "friend", "friend_phone", "friend_taste", "friend_occasion", "kid", "kid_like", "important_event", "gift",
    "life_update", "feed", "feed_item",
)

private const val LIKE = "LIKE"
private const val DISLIKE = "DISLIKE"

/** Every friend, in list order, assembled from the friend table and its child tables (one query per table). */
internal suspend fun GoodCompanyDatabase.readFriends(): List<DomainFriend> {
    val q = friendQueries
    val phones = q.allPhones().awaitAsList().groupBy { it.friend_id }
    val tastes = q.allTastes().awaitAsList().groupBy { it.friend_id }
    val occasions = q.allOccasions().awaitAsList().groupBy { it.friend_id }
    val kidLikes = q.allKidLikes().awaitAsList().groupBy { it.friend_id to it.kid_id }
    val kids = q.allKids().awaitAsList().groupBy { it.friend_id }
    val events = q.allEvents().awaitAsList().groupBy { it.friend_id }
    val gifts = q.allGifts().awaitAsList().groupBy { it.friend_id }
    val updates = q.allLifeUpdates().awaitAsList().groupBy { it.friend_id }
    val feedItems = q.allFeedItems().awaitAsList().groupBy { it.friend_id to it.feed_url }
    val feeds = q.allFeeds().awaitAsList().groupBy { it.friend_id }

    return q.allFriends().awaitAsList().map { row ->
        val id = row.id
        val friendTastes = tastes[id].orEmpty()
        val likes = friendTastes.filter { it.kind == LIKE }
        DomainFriend(
            id = FriendId(id),
            name = row.name,
            contactId = row.contact_id,
            phoneNumbers = phones[id].orEmpty().map { it.number },
            birthday = birthdayOf(row.birthday_month, row.birthday_day, row.birthday_year),
            isCloseFriend = row.is_close_friend.toFlag(),
            notifyBirthday = row.notify_birthday.toFlag(),
            likes = likes.map { it.taste },
            dislikes = friendTastes.filter { it.kind == DISLIKE }.map { it.taste },
            notes = row.notes,
            kids = kids[id].orEmpty().map { kid ->
                DomainKid(
                    id = kid.id,
                    name = kid.name,
                    birthday = birthdayOf(kid.birthday_month, kid.birthday_day, kid.birthday_year),
                    likes = kidLikes[id to kid.id].orEmpty().map { it.liked },
                    addedAt = kid.added_at?.toInstant(),
                )
            },
            importantEvents = events[id].orEmpty().map {
                ImportantEvent(it.id, it.title, it.date.toLocalDate(), it.remind_days_before.toInt(), it.added_at?.toInstant())
            },
            meetEveryDays = row.meet_every_days?.toInt(),
            suggestEvents = row.suggest_events.toFlag(),
            isOrphaned = row.is_orphaned.toFlag(),
            circle = row.circle?.let(Circle::valueOf),
            snoozedUntil = row.snoozed_until?.toLocalDate(),
            gifts = gifts[id].orEmpty().map {
                DomainGift(it.id, it.title, it.note, it.for_kid_id, it.given_on?.toLocalDate(), it.occasion?.let(GiftOccasion::valueOf))
            },
            catchUpPaused = row.catch_up_paused.toFlag(),
            contactCountsAsCatchUp = row.contact_counts_as_catch_up.toFlag(),
            occasions = occasions[id].orEmpty().map { Occasion.valueOf(it.occasion) },
            lifeUpdates = updates[id].orEmpty().map { LifeUpdate(it.id, it.text, it.added_at.toInstant()) },
            likesAddedAt = likes.mapNotNull { like -> like.added_at?.let { like.taste to it.toInstant() } }.toMap(),
            feeds = feeds[id].orEmpty().map { feed ->
                FriendFeed(
                    url = feed.url,
                    title = feed.title,
                    siteUrl = feed.site_url,
                    checkedAt = feed.checked_at?.toInstant(),
                    items = feedItems[id to feed.url].orEmpty().map {
                        FeedItem(it.id, it.title, it.link, it.published_at?.toInstant())
                    },
                )
            },
        )
    }
}

internal suspend fun GoodCompanyDatabase.nextFriendPosition(): Long = friendQueries.nextPosition().awaitAsOne()

/**
 * Writes [friend] at [position]: upserts its row (never deleting it, so nothing that references it is touched) and
 * replaces its child rows. Call inside a transaction.
 *
 * Stored as the domain has it, with two exceptions the schema can't hold: a gift's `forKidId` that isn't one of this
 * friend's kids becomes null, and `likesAddedAt` keeps only entries for current likes.
 */
internal suspend fun GoodCompanyDatabase.writeFriend(friend: DomainFriend, position: Long) {
    val q = friendQueries
    val id = friend.id.value
    q.upsert(
        Friend(
            id = id,
            position = position,
            name = friend.name,
            contact_id = friend.contactId,
            birthday_month = friend.birthday?.month?.toColumn(),
            birthday_day = friend.birthday?.day?.toColumn(),
            birthday_year = friend.birthday?.year?.toColumn(),
            is_close_friend = friend.isCloseFriend.toColumn(),
            notify_birthday = friend.notifyBirthday.toColumn(),
            notes = friend.notes,
            meet_every_days = friend.meetEveryDays?.toColumn(),
            suggest_events = friend.suggestEvents.toColumn(),
            is_orphaned = friend.isOrphaned.toColumn(),
            circle = friend.circle?.name,
            snoozed_until = friend.snoozedUntil?.toColumn(),
            catch_up_paused = friend.catchUpPaused.toColumn(),
            contact_counts_as_catch_up = friend.contactCountsAsCatchUp.toColumn(),
        ),
    )
    q.deleteChildren(id).await()

    friend.phoneNumbers.forEachIndexed { i, number ->
        q.insertPhone(Friend_phone(id, i.toLong(), number, PhoneMatcher.key(number)))
    }
    friend.likes.forEachIndexed { i, like ->
        q.insertTaste(Friend_taste(id, LIKE, i.toLong(), like, friend.likesAddedAt[like]?.toColumn()))
    }
    friend.dislikes.forEachIndexed { i, dislike ->
        q.insertTaste(Friend_taste(id, DISLIKE, i.toLong(), dislike, null))
    }
    friend.occasions.forEachIndexed { i, occasion -> q.insertOccasion(Friend_occasion(id, occasion.name, i.toLong())) }
    friend.kids.forEachIndexed { i, kid ->
        q.insertKid(
            Kid(
                friend_id = id,
                id = kid.id,
                position = i.toLong(),
                name = kid.name,
                birthday_month = kid.birthday?.month?.toColumn(),
                birthday_day = kid.birthday?.day?.toColumn(),
                birthday_year = kid.birthday?.year?.toColumn(),
                added_at = kid.addedAt?.toColumn(),
            ),
        )
        kid.likes.forEachIndexed { j, like -> q.insertKidLike(Kid_like(id, kid.id, j.toLong(), like)) }
    }
    friend.importantEvents.forEachIndexed { i, e ->
        q.insertEvent(Important_event(id, e.id, i.toLong(), e.title, e.date.toColumn(), e.remindDaysBefore.toColumn(), e.addedAt?.toColumn()))
    }
    val kidIds = friend.kids.mapTo(HashSet()) { it.id }
    friend.gifts.forEachIndexed { i, g ->
        q.insertGift(
            Gift(id, g.id, i.toLong(), g.title, g.note, g.forKidId?.takeIf { it in kidIds }, g.givenOn?.toColumn(), g.occasion?.name),
        )
    }
    friend.lifeUpdates.forEachIndexed { i, u -> q.insertLifeUpdate(Life_update(id, u.id, i.toLong(), u.text, u.addedAt.toColumn())) }
    friend.feeds.forEachIndexed { i, feed ->
        q.insertFeed(Feed(id, feed.url, i.toLong(), feed.title, feed.siteUrl, feed.checkedAt?.toColumn()))
        feed.items.forEachIndexed { j, item ->
            q.insertFeedItem(Feed_item(id, feed.url, item.id, j.toLong(), item.title, item.link, item.publishedAt?.toColumn()))
        }
    }
}
