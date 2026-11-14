package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactChannel
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.CustomField
import com.neteinstein.goodcompany.core.domain.model.FeedItem
import com.neteinstein.goodcompany.core.domain.model.FriendEmail
import com.neteinstein.goodcompany.core.domain.model.FriendFeed
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Introducer
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.MetInfo
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.util.PhoneMatcher
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import com.neteinstein.goodcompany.core.domain.model.Friend as DomainFriend
import com.neteinstein.goodcompany.core.domain.model.Gift as DomainGift
import com.neteinstein.goodcompany.core.domain.model.Kid as DomainKid
import com.neteinstein.goodcompany.core.domain.model.Relative as DomainRelative

/** Tables that make up a friend. Observers of friends listen to all of them. */
internal val FRIEND_TABLES = arrayOf(
    "friend", "friend_phone", "friend_taste", "friend_occasion", "kid", "kid_like", "important_event", "gift",
    "life_update", "feed", "feed_item", "friend_photo", "friend_custom_photo",
    "friend_email", "relative", "relative_like", "custom_field",
)

private const val LIKE = "LIKE"
private const val DISLIKE = "DISLIKE"
private const val FOOD = "FOOD"
private const val DRINK = "DRINK"
private const val INTEREST = "INTEREST"

/** `yyyy-mm-dd`, or `yyyy-mm` when only the month is known. */
internal fun MetInfo.onColumn(): String? = on?.let { if (dayKnown) it.toString() else it.toString().take(7) }

internal fun metOf(place: String?, on: String?): MetInfo? {
    if (place == null && on == null) return null
    val date = on?.let { raw -> runCatching { if (raw.length == 7) LocalDate.parse("$raw-01") else LocalDate.parse(raw) }.getOrNull() }
    return MetInfo(place.orEmpty(), date, dayKnown = on?.length != 7)
}

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
    val photos = q.allPhotos().awaitAsList().associate { it.friend_id to ContactPhoto(it.image_base64) }
    val customPhotos = q.allCustomPhotos().awaitAsList().associate { it.friend_id to ContactPhoto(it.image_base64) }
    val emails = q.allEmails().awaitAsList().groupBy { it.friend_id }
    val relativeLikes = q.allRelativeLikes().awaitAsList().groupBy { it.friend_id to it.relative_id }
    val relatives = q.allRelatives().awaitAsList().groupBy { it.friend_id }
    val customFields = q.allCustomFields().awaitAsList().groupBy { it.friend_id }

    return q.allFriends().awaitAsList().map { row ->
        val id = row.id
        val friendTastes = tastes[id].orEmpty()
        val likes = friendTastes.filter { it.kind == LIKE }
        val food = friendTastes.filter { it.kind == FOOD }
        val drinks = friendTastes.filter { it.kind == DRINK }
        val interests = friendTastes.filter { it.kind == INTEREST }
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
            closeness = row.circle?.let(Closeness::valueOf),
            snoozedUntil = row.snoozed_until?.toLocalDate(),
            gifts = gifts[id].orEmpty().map {
                DomainGift(
                    it.id,
                    it.title,
                    it.note,
                    it.for_kid_id,
                    it.given_on?.toLocalDate(),
                    it.occasion?.let(GiftOccasion::valueOf),
                    it.url,
                    it.price,
                )
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
            photo = photos[id],
            job = row.job,
            livesIn = row.lives_in,
            met = metOf(row.met_place, row.met_on),
            introducedBy = row.introduced_by_name?.let { Introducer(row.introduced_by_friend_id?.let(::FriendId), it) },
            preferredChannel = row.preferred_channel?.let { name -> ContactChannel.entries.firstOrNull { it.name == name } },
            emails = emails[id].orEmpty().map { FriendEmail(it.address, it.from_contact.toFlag()) },
            relatives = relatives[id].orEmpty().map { r ->
                DomainRelative(
                    id = r.id,
                    relation = Relation.valueOf(r.relation),
                    name = r.name,
                    birthday = birthdayOf(r.birthday_month, r.birthday_day, r.birthday_year),
                    likes = relativeLikes[id to r.id].orEmpty().map { it.liked },
                    note = r.note,
                    notifyBirthday = r.notify_birthday.toFlag(),
                    addedAt = r.added_at?.toInstant(),
                )
            },
            food = food.map { it.taste },
            drinks = drinks.map { it.taste },
            interests = interests.map { it.taste },
            foodAddedAt = food.addedAt(),
            drinksAddedAt = drinks.addedAt(),
            interestsAddedAt = interests.addedAt(),
            customFields = customFields[id].orEmpty().map { CustomField(it.id, it.label, it.value_) },
            relationToMe = LinkType.parse(row.relation_to_me),
            pronouns = Pronouns.parse(row.pronouns),
            customName = row.custom_name,
            customPhoto = customPhotos[id],
        )
    }
}

private fun List<Friend_taste>.addedAt(): Map<String, Instant> =
    mapNotNull { t -> t.added_at?.let { t.taste to it.toInstant() } }.toMap()

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
            circle = friend.closeness?.name,
            snoozed_until = friend.snoozedUntil?.toColumn(),
            catch_up_paused = friend.catchUpPaused.toColumn(),
            contact_counts_as_catch_up = friend.contactCountsAsCatchUp.toColumn(),
            job = friend.job,
            lives_in = friend.livesIn,
            met_place = friend.met?.place,
            met_on = friend.met?.onColumn(),
            introduced_by_friend_id = friend.introducedBy?.friendId?.value,
            introduced_by_name = friend.introducedBy?.name,
            preferred_channel = friend.preferredChannel?.name,
            relation_to_me = friend.relationToMe?.name,
            pronouns = friend.pronouns?.name,
            custom_name = friend.customName?.takeIf { it.isNotBlank() },
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
    suspend fun tastes(kind: String, values: List<String>, addedAt: Map<String, Instant>) =
        values.forEachIndexed { i, taste -> q.insertTaste(Friend_taste(id, kind, i.toLong(), taste, addedAt[taste]?.toColumn())) }
    tastes(FOOD, friend.food, friend.foodAddedAt)
    tastes(DRINK, friend.drinks, friend.drinksAddedAt)
    tastes(INTEREST, friend.interests, friend.interestsAddedAt)
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
            Gift(id, g.id, i.toLong(), g.title, g.note, g.forKidId?.takeIf { it in kidIds }, g.givenOn?.toColumn(), g.occasion?.name, g.url, g.price),
        )
    }
    friend.lifeUpdates.forEachIndexed { i, u -> q.insertLifeUpdate(Life_update(id, u.id, i.toLong(), u.text, u.addedAt.toColumn())) }
    friend.feeds.forEachIndexed { i, feed ->
        q.insertFeed(Feed(id, feed.url, i.toLong(), feed.title, feed.siteUrl, feed.checkedAt?.toColumn()))
        feed.items.forEachIndexed { j, item ->
            q.insertFeedItem(Feed_item(id, feed.url, item.id, j.toLong(), item.title, item.link, item.publishedAt?.toColumn()))
        }
    }
    friend.photo?.let { q.insertPhoto(Friend_photo(id, it.base64)) }
    friend.customPhoto?.let { q.insertCustomPhoto(Friend_custom_photo(id, it.base64)) }
    friend.emails.forEachIndexed { i, e -> q.insertEmail(Friend_email(id, i.toLong(), e.address, e.fromContact.toColumn())) }
    friend.relatives.distinctBy { it.id }.forEachIndexed { i, r ->
        q.insertRelative(
            Relative(
                friend_id = id,
                id = r.id,
                position = i.toLong(),
                relation = r.relation.name,
                name = r.name,
                birthday_month = r.birthday?.month?.toColumn(),
                birthday_day = r.birthday?.day?.toColumn(),
                birthday_year = r.birthday?.year?.toColumn(),
                note = r.note,
                notify_birthday = r.notifyBirthday.toColumn(),
                added_at = r.addedAt?.toColumn(),
            ),
        )
        r.likes.forEachIndexed { j, like -> q.insertRelativeLike(Relative_like(id, r.id, j.toLong(), like)) }
    }
    friend.customFields.forEachIndexed { i, f -> q.insertCustomField(Custom_field(id, i.toLong(), f.id, f.label, f.value)) }
}
