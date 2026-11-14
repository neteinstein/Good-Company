package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftList
import com.neteinstein.goodcompany.core.domain.model.GiftListEntry
import com.neteinstein.goodcompany.core.domain.model.GiftListItem
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.GiftStatus
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GiftListRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** An amount read from a free-text price ("€35", "1.299,00 €", "35 lei"); [currency] is what's around the number. */
data class Money(val amount: Double, val currency: String? = null)

/** Prices are free text ([Gift.price]): read a number where there is one, never guess when there isn't. */
object GiftPrices {
    private val number = Regex("""\d[\d .,  ']*""")

    fun parse(text: String?): Money? {
        if (text.isNullOrBlank()) return null
        val match = number.find(text) ?: return null
        val raw = match.value.trimEnd(' ', '.', ',', ' ', ' ', '\'')
        val amount = amountOf(raw) ?: return null
        val rest = (text.substring(0, match.range.first) + text.substring(match.range.first + raw.length)).trim()
        val currency = rest.takeIf { it.isNotEmpty() && it.length <= 4 && it.none(Char::isDigit) }
        return Money(amount, currency)
    }

    /** "1.299,00" and "1,299.00" are 1299; a last separator followed by one or two digits is the decimal point. */
    internal fun amountOf(raw: String): Double? {
        val compact = raw.filterNot { it == ' ' || it == ' ' || it == ' ' || it == '\'' }
        val last = compact.indexOfLast { it == '.' || it == ',' }
        val normalized = if (last >= 0 && compact.length - last - 1 in 1..2) {
            compact.substring(0, last).filter(Char::isDigit) + "." + compact.substring(last + 1)
        } else {
            compact.filter(Char::isDigit)
        }
        return normalized.toDoubleOrNull()
    }
}

/** A gift on a list, with its status and its price when it can be read. */
data class ListedGift(val gift: Gift, val status: GiftStatus, val price: Money? = GiftPrices.parse(gift.price))

/**
 * Money on a list. [spent] adds the prices of what's bought or given; [planned] adds each person's budget, or the
 * prices of their gifts when they have none. A `*Partial` flag says a price couldn't be read, so the sum is short.
 */
data class GiftTotals(
    val spent: Double = 0.0,
    val planned: Double = 0.0,
    val spentPartial: Boolean = false,
    val plannedPartial: Boolean = false,
    /** The currency most prices are written in, null when none says. */
    val currency: String? = null,
)

/** A person's row on a list. */
data class GiftListPerson(
    val friend: Friend,
    val entry: GiftListEntry,
    /** Age on the occasion (or today), when the birth year is known. */
    val age: Int?,
    val gifts: List<ListedGift>,
    val spent: Double,
    val spentPartial: Boolean,
    /** Their gift ideas not on this list yet (shared ones too, with link and price). */
    val savedIdeas: List<Gift>,
    /** Gifts already given (not from this list), newest first. */
    val pastGifts: List<Gift>,
)

data class GiftListDetail(
    val list: GiftList,
    val people: List<GiftListPerson>,
    val totals: GiftTotals,
    val today: LocalDate,
    /** Everyone who could still be added (not on the list, not an orphaned contact), by name. */
    val others: List<Friend> = emptyList(),
)

data class GiftListSummary(val list: GiftList, val people: List<Friend>, val totals: GiftTotals, val gifts: Int, val bought: Int, val given: Int) {
    val allGiven: Boolean get() = gifts > 0 && given == gifts
}

data class GiftListsOverview(val current: List<GiftListSummary> = emptyList(), val past: List<GiftListSummary> = emptyList())

/** The sums and rows behind the gift-list screens. Pure. */
object GiftListMath {
    fun giftsOf(entry: GiftListEntry, friend: Friend?): List<ListedGift> = entry.items.mapNotNull { item ->
        friend?.gifts?.firstOrNull { it.id == item.giftId }?.let { ListedGift(it, item.status) }
    }

    private fun isPaid(status: GiftStatus) = status != GiftStatus.TO_BUY

    /** What's been spent on one person, and whether a price couldn't be read. */
    fun spent(gifts: List<ListedGift>): Pair<Double, Boolean> {
        val paid = gifts.filter { isPaid(it.status) }
        return paid.sumOf { it.price?.amount ?: 0.0 } to paid.any { it.price == null }
    }

    fun totals(list: GiftList, friends: Map<FriendId, Friend>): GiftTotals {
        var spent = 0.0
        var planned = 0.0
        var spentPartial = false
        var plannedPartial = false
        val currencies = mutableListOf<String>()
        list.entries.forEach { entry ->
            val gifts = giftsOf(entry, friends[entry.friendId])
            gifts.mapNotNullTo(currencies) { it.price?.currency }
            val (s, partial) = spent(gifts)
            spent += s
            spentPartial = spentPartial || partial
            if (entry.budget != null) {
                planned += entry.budget
            } else {
                planned += gifts.sumOf { it.price?.amount ?: 0.0 }
                plannedPartial = plannedPartial || gifts.any { it.price == null }
            }
        }
        val currency = currencies.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return GiftTotals(spent, planned, spentPartial, plannedPartial, currency)
    }

    fun summary(list: GiftList, friends: Map<FriendId, Friend>): GiftListSummary {
        val gifts = list.entries.flatMap { giftsOf(it, friends[it.friendId]) }
        return GiftListSummary(
            list = list,
            people = list.entries.mapNotNull { friends[it.friendId] },
            totals = totals(list, friends),
            gifts = gifts.size,
            bought = gifts.count { isPaid(it.status) },
            given = gifts.count { it.status == GiftStatus.GIVEN },
        )
    }

    /** Lists whose day has passed are "Past", newest first; the others come by date (undated last). */
    fun overview(lists: List<GiftList>, friends: List<Friend>, today: LocalDate): GiftListsOverview {
        val byId = friends.associateBy { it.id }
        val (past, current) = lists.partition { it.occasionDate != null && it.occasionDate < today }
        return GiftListsOverview(
            current = current.sortedWith(compareBy<GiftList> { it.occasionDate == null }.thenBy { it.occasionDate }.thenBy { it.createdAt })
                .map { summary(it, byId) },
            past = past.sortedByDescending { it.occasionDate }.map { summary(it, byId) },
        )
    }

    fun detail(list: GiftList, friends: List<Friend>, today: LocalDate): GiftListDetail {
        val byId = friends.associateBy { it.id }
        val on = list.occasionDate ?: today
        val people = list.entries.mapNotNull { entry ->
            val friend = byId[entry.friendId] ?: return@mapNotNull null
            val gifts = giftsOf(entry, friend)
            val onList = entry.items.map { it.giftId }.toSet()
            val (spent, partial) = spent(gifts)
            GiftListPerson(
                friend = friend,
                entry = entry,
                age = friend.birthday?.ageOn(on),
                gifts = gifts,
                spent = spent,
                spentPartial = partial,
                savedIdeas = friend.gifts.filter { it.isIdea && it.id !in onList },
                pastGifts = friend.gifts.filter { !it.isIdea && it.id !in onList }.sortedByDescending { it.givenOn },
            )
        }
        val onList = list.entries.map { it.friendId }.toSet()
        val others = friends.filter { it.id !in onList && !it.isOrphaned }.sortedBy { it.displayName.lowercase() }
        return GiftListDetail(list, people, totals(list, byId), today, others)
    }

    /** The same people and budgets a year on, nothing on it yet: "Christmas 2025" becomes "Christmas 2026". */
    fun duplicate(list: GiftList, id: String, now: Instant, zone: TimeZone): GiftList {
        val year = list.occasionDate?.year ?: list.createdAt.toLocalDateTime(zone).year
        val title = if (list.title.contains(year.toString())) list.title.replace(year.toString(), (year + 1).toString()) else list.title
        return GiftList(
            id = id,
            title = title,
            occasionDate = list.occasionDate?.plus(1, DateTimeUnit.YEAR),
            createdAt = now,
            occasion = list.occasion,
            entries = list.entries.map { GiftListEntry(it.friendId, it.budget) },
        )
    }
}

class ObserveGiftListsUseCase(private val lists: GiftListRepository, private val friends: FriendRepository, private val clock: AppClock) {
    operator fun invoke(): Flow<GiftListsOverview> = combine(lists.lists, friends.friends) { ls, fs -> GiftListMath.overview(ls, fs, clock.today()) }
}

class ObserveGiftListUseCase(private val lists: GiftListRepository, private val friends: FriendRepository, private val clock: AppClock) {
    operator fun invoke(id: String): Flow<GiftListDetail?> = combine(lists.lists, friends.friends) { ls, fs ->
        ls.firstOrNull { it.id == id }?.let { GiftListMath.detail(it, fs, clock.today()) }
    }
}

/** New list ("Christmas 2026"); returns its id. */
class CreateGiftListUseCase(private val lists: GiftListRepository, private val ids: IdGenerator, private val clock: AppClock) {
    suspend operator fun invoke(title: String, occasion: GiftOccasion?, date: LocalDate?): String? {
        val name = title.trim().takeIf { it.isNotEmpty() } ?: return null
        val id = ids.next()
        lists.upsert(GiftList(id, name, date, clock.now(), occasion))
        return id
    }
}

class UpdateGiftListUseCase(private val lists: GiftListRepository) {
    suspend operator fun invoke(id: String, title: String, occasion: GiftOccasion?, date: LocalDate?) {
        val name = title.trim().takeIf { it.isNotEmpty() } ?: return
        val list = lists.get(id) ?: return
        lists.upsert(list.copy(title = name, occasion = occasion, occasionDate = date))
    }
}

/** Deletes a list (the gifts stay on each person's page); returns it for Undo. */
class DeleteGiftListUseCase(private val lists: GiftListRepository) {
    suspend operator fun invoke(id: String): GiftList? = lists.get(id)?.also { lists.delete(id) }
}

/** Puts a list back as it was (Undo after a delete or after removing someone). */
class RestoreGiftListUseCase(private val lists: GiftListRepository) {
    suspend operator fun invoke(list: GiftList) = lists.upsert(list)
}

/** "Duplicate": same people and budgets, a year on, nothing ticked. Returns the new list's id. */
class DuplicateGiftListUseCase(private val lists: GiftListRepository, private val ids: IdGenerator, private val clock: AppClock) {
    suspend operator fun invoke(id: String): String? {
        val list = lists.get(id) ?: return null
        val copy = GiftListMath.duplicate(list, ids.next(), clock.now(), clock.timeZone)
        lists.upsert(copy)
        return copy.id
    }
}

/** Adds people (a whole circle or picked by search); [budget] is set for the ones added, others keep theirs. */
class AddPeopleToGiftListUseCase(private val lists: GiftListRepository) {
    suspend operator fun invoke(id: String, people: Collection<FriendId>, budget: Double? = null) {
        val list = lists.get(id) ?: return
        val added = people.distinct().filter { list.entryFor(it) == null }.map { GiftListEntry(it, budget?.takeIf { b -> b >= 0 }) }
        if (added.isNotEmpty()) lists.upsert(list.copy(entries = list.entries + added))
    }
}

/** Takes someone off a list; returns the list as it was, for Undo. Their gifts stay on their page. */
class RemoveFromGiftListUseCase(private val lists: GiftListRepository) {
    suspend operator fun invoke(id: String, friendId: FriendId): GiftList? {
        val list = lists.get(id) ?: return null
        if (list.entryFor(friendId) == null) return null
        lists.upsert(list.copy(entries = list.entries.filterNot { it.friendId == friendId }))
        return list
    }
}

class SetGiftBudgetUseCase(private val lists: GiftListRepository) {
    suspend operator fun invoke(id: String, friendId: FriendId, budget: Double?) {
        val list = lists.get(id) ?: return
        if (list.entryFor(friendId) == null) return
        lists.upsert(list.withEntry(friendId) { it.copy(budget = budget?.takeIf { b -> b >= 0 }) })
    }
}

/** Puts a gift on someone's list: a new idea (saved on their page too) or one of their saved ideas. */
class AddGiftToListUseCase(private val lists: GiftListRepository, private val friends: FriendRepository, private val ids: IdGenerator) {
    /** A new idea, [title] and an optional free-text [price]. Returns the gift's id. */
    suspend operator fun invoke(id: String, friendId: FriendId, title: String, price: String? = null): String? {
        val name = title.trim().takeIf { it.isNotEmpty() } ?: return null
        val list = lists.get(id) ?: return null
        val friend = friends.get(friendId) ?: return null
        val gift = Gift(ids.next(), name, price = price?.trim()?.takeIf { it.isNotEmpty() })
        friends.upsert(friend.copy(gifts = friend.gifts + gift))
        lists.upsert(list.withEntry(friendId) { it.copy(items = it.items + GiftListItem(gift.id)) })
        return gift.id
    }

    /** One of their saved ideas. */
    suspend fun existing(id: String, friendId: FriendId, giftId: String) {
        val list = lists.get(id) ?: return
        val friend = friends.get(friendId) ?: return
        if (friend.gifts.none { it.id == giftId }) return
        if (list.entryFor(friendId)?.items?.any { it.giftId == giftId } == true) return
        lists.upsert(list.withEntry(friendId) { it.copy(items = it.items + GiftListItem(giftId)) })
    }
}

/** Takes a gift off the list. The idea (or the gift given) stays on the person's page. */
class RemoveGiftFromListUseCase(private val lists: GiftListRepository) {
    suspend operator fun invoke(id: String, friendId: FriendId, giftId: String) {
        val list = lists.get(id) ?: return
        lists.upsert(list.withEntry(friendId) { e -> e.copy(items = e.items.filterNot { it.giftId == giftId }) })
    }
}

/**
 * Moves a gift along: to buy → bought → given → to buy. "Given" records it on the person's page as a gift given
 * today ([Gift.givenOn], with the list's [GiftList.occasion]); moving it on from given makes it an idea again.
 * Returns the new status.
 */
class AdvanceGiftStatusUseCase(private val lists: GiftListRepository, private val friends: FriendRepository, private val clock: AppClock) {
    suspend operator fun invoke(id: String, friendId: FriendId, giftId: String): GiftStatus? {
        val list = lists.get(id) ?: return null
        val item = list.entryFor(friendId)?.items?.firstOrNull { it.giftId == giftId } ?: return null
        val friend = friends.get(friendId) ?: return null
        val next = item.status.next
        val gifts = friend.gifts.map { gift ->
            when {
                gift.id != giftId -> gift
                next == GiftStatus.GIVEN -> gift.copy(givenOn = clock.today(), occasion = list.occasion ?: gift.occasion)
                item.status == GiftStatus.GIVEN -> gift.copy(givenOn = null, occasion = null)
                else -> gift
            }
        }
        if (gifts != friend.gifts) friends.upsert(friend.copy(gifts = gifts))
        lists.upsert(
            list.withEntry(friendId) { e -> e.copy(items = e.items.map { if (it.giftId == giftId) it.copy(status = next) else it }) },
        )
        return next
    }
}

/** Everything a gift-list screen does, in one bundle (keeps ViewModel constructors short). */
data class GiftListUseCases(
    val create: CreateGiftListUseCase,
    val update: UpdateGiftListUseCase,
    val delete: DeleteGiftListUseCase,
    val restore: RestoreGiftListUseCase,
    val duplicate: DuplicateGiftListUseCase,
    val addPeople: AddPeopleToGiftListUseCase,
    val removePerson: RemoveFromGiftListUseCase,
    val setBudget: SetGiftBudgetUseCase,
    val addGift: AddGiftToListUseCase,
    val removeGift: RemoveGiftFromListUseCase,
    val advance: AdvanceGiftStatusUseCase,
)

/** Shared by the "Add people" sheet: who matches a search, by name or by kinship ("my cousins", issue #41). */
object GiftListPicker {
    fun matches(friends: List<Friend>, query: String, kinship: Set<LinkType>?): List<Friend> {
        val q = query.trim()
        val pool = friends.sortedBy { it.displayName.lowercase() }
        return when {
            kinship != null -> pool.filter { it.relationToMe in kinship }
            q.isEmpty() -> pool
            else -> pool.filter { it.displayName.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
        }
    }
}
