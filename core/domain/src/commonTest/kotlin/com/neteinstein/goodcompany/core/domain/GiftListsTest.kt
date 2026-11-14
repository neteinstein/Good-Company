package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftList
import com.neteinstein.goodcompany.core.domain.model.GiftListEntry
import com.neteinstein.goodcompany.core.domain.model.GiftListItem
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.GiftStatus
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.usecase.GiftListMath
import com.neteinstein.goodcompany.core.domain.usecase.GiftListPicker
import com.neteinstein.goodcompany.core.domain.usecase.GiftPrices
import com.neteinstein.goodcompany.core.domain.usecase.Money
import com.neteinstein.goodcompany.core.domain.usecase.SampleData
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GiftListsTest {
    private val christmas = LocalDate(2026, 12, 25)

    private fun graph() = TestGraph(
        listOf(
            friend("tomas") { copy(relationToMe = LinkType.COUSIN, birthday = Birthday(5, 1, 1990)) },
            friend("bruno") { copy(relationToMe = LinkType.COUSIN, gifts = listOf(Gift("scooter", "Scooter", givenOn = LocalDate(2024, 12, 25)))) },
            friend("ana"),
        ),
    )

    private suspend fun TestGraph.newList(): String = giftLists.create("Christmas 2026", GiftOccasion.CHRISTMAS, christmas)!!

    // --- Prices ---

    @Test
    fun prices_areReadWhereTheyCanBe() {
        assertEquals(Money(35.0, "€"), GiftPrices.parse("€35"))
        assertEquals(Money(1299.0, "€"), GiftPrices.parse("1.299,00 €"))
        assertEquals(Money(1299.99, "$"), GiftPrices.parse("$1,299.99"))
        assertEquals(Money(1299.0, "€"), GiftPrices.parse("€1.299"))
        assertEquals(Money(35.0, "lei"), GiftPrices.parse("35 lei"))
        assertEquals(Money(20.0, null), GiftPrices.parse("20-30 €"))
        assertNull(GiftPrices.parse("about twenty"))
        assertNull(GiftPrices.parse(null))
        assertNull(GiftPrices.parse(" "))
    }

    @Test
    fun totals_addPricesAndBudgets_andSayWhenTheyArePartial() {
        val friends = listOf(
            friend("a") { copy(gifts = listOf(Gift("g1", "Lego", price = "€35"), Gift("g2", "Pyjamas", price = "€20"))) },
            friend("b") { copy(gifts = listOf(Gift("g3", "Orchid", price = "nice one"))) },
            friend("c") { copy(gifts = listOf(Gift("g4", "Book", price = "€12"), Gift("g5", "Socks"))) },
        ).associateBy { it.id }
        val list = GiftList(
            "l",
            "Christmas",
            christmas,
            at(2026, 11, 1),
            entries = listOf(
                GiftListEntry(FriendId("a"), 40.0, listOf(GiftListItem("g1", GiftStatus.BOUGHT), GiftListItem("g2"))),
                GiftListEntry(FriendId("b"), 60.0, listOf(GiftListItem("g3", GiftStatus.GIVEN))),
                GiftListEntry(FriendId("c"), null, listOf(GiftListItem("g4"), GiftListItem("g5"))),
                GiftListEntry(FriendId("gone"), 10.0, listOf(GiftListItem("x", GiftStatus.BOUGHT))),
            ),
        )
        val totals = GiftListMath.totals(list, friends)
        assertEquals(35.0, totals.spent) // the orchid's price can't be read
        assertTrue(totals.spentPartial)
        assertEquals(40.0 + 60.0 + 12.0 + 10.0, totals.planned) // c has no budget: their prices, socks unknown
        assertTrue(totals.plannedPartial)
        assertEquals("€", totals.currency)

        val readable = GiftListMath.totals(list.copy(entries = list.entries.take(1)), friends)
        assertFalse(readable.spentPartial)
        assertFalse(readable.plannedPartial)
    }

    // --- Status ---

    @Test
    fun aGift_movesToBuyBoughtGiven_andGivenFillsTheirGiftHistory() = runTest {
        val g = graph()
        val id = g.newList()
        g.giftLists.addPeople(id, listOf(FriendId("tomas")))
        val giftId = g.giftLists.addGift(id, FriendId("tomas"), " Climbing shoes ", "€80")!!
        val saved = g.friendRepo.get(FriendId("tomas"))!!.gifts.single()
        assertEquals("Climbing shoes", saved.title)
        assertTrue(saved.isIdea)

        assertEquals(GiftStatus.BOUGHT, g.giftLists.advance(id, FriendId("tomas"), giftId))
        assertTrue(g.friendRepo.get(FriendId("tomas"))!!.gifts.single().isIdea)
        assertEquals(GiftStatus.GIVEN, g.giftLists.advance(id, FriendId("tomas"), giftId))
        val given = g.friendRepo.get(FriendId("tomas"))!!.gifts.single()
        assertEquals(g.clock.today(), given.givenOn)
        assertEquals(GiftOccasion.CHRISTMAS, given.occasion)

        // Tapping on starts again: it's an idea once more.
        assertEquals(GiftStatus.TO_BUY, g.giftLists.advance(id, FriendId("tomas"), giftId))
        val back = g.friendRepo.get(FriendId("tomas"))!!.gifts.single()
        assertNull(back.givenOn)
        assertNull(back.occasion)
        assertNull(g.giftLists.advance(id, FriendId("tomas"), "nope"))
    }

    @Test
    fun savedIdeas_canBePutOnTheList_once_andTakingThemOffKeepsThem() = runTest {
        val g = TestGraph(listOf(friend("ana") { copy(gifts = listOf(Gift("lego", "LEGO Dino set", url = "https://shop", price = "€35"))) }))
        val id = g.newList()
        g.giftLists.addPeople(id, listOf(FriendId("ana")))
        var detail = g.observeGiftList(id).first()!!
        assertEquals(listOf("lego"), detail.people.single().savedIdeas.map { it.id })
        g.giftLists.addGift.existing(id, FriendId("ana"), "lego")
        g.giftLists.addGift.existing(id, FriendId("ana"), "lego")
        g.giftLists.addGift.existing(id, FriendId("ana"), "unknown")
        detail = g.observeGiftList(id).first()!!
        assertEquals(listOf("lego"), detail.people.single().gifts.map { it.gift.id })
        assertEquals(Money(35.0, "€"), detail.people.single().gifts.single().price)
        assertTrue(detail.people.single().savedIdeas.isEmpty())

        g.giftLists.removeGift(id, FriendId("ana"), "lego")
        assertTrue(g.observeGiftList(id).first()!!.people.single().gifts.isEmpty())
        assertEquals(1, g.friendRepo.get(FriendId("ana"))!!.gifts.size)
    }

    // --- People ---

    @Test
    fun addingACircle_addsEveryoneOnce_withTheBudget_andKeepsOthersBudgets() = runTest {
        val g = graph()
        g.groupRepo.upsert(FriendGroup("cousins", "Cousins", memberIds = listOf(FriendId("tomas"), FriendId("bruno"))))
        val id = g.newList()
        g.giftLists.addPeople(id, listOf(FriendId("tomas")), budget = 25.0)
        val circle = g.circles.groups.first().first { it.id == "cousins" }
        g.giftLists.addPeople(id, circle.memberIds + FriendId("bruno"), budget = 40.0)
        val entries = g.giftListRepo.get(id)!!.entries
        assertEquals(listOf(FriendId("tomas") to 25.0, FriendId("bruno") to 40.0), entries.map { it.friendId to it.budget })

        g.giftLists.setBudget(id, FriendId("bruno"), null)
        assertNull(g.giftListRepo.get(id)!!.entryFor(FriendId("bruno"))!!.budget)
        g.giftLists.setBudget(id, FriendId("ana"), 10.0) // not on the list: nothing happens
        assertNull(g.giftListRepo.get(id)!!.entryFor(FriendId("ana")))

        val before = g.giftLists.removePerson(id, FriendId("tomas"))!!
        assertEquals(listOf(FriendId("bruno")), g.giftListRepo.get(id)!!.entries.map { it.friendId })
        g.giftLists.restore(before)
        assertEquals(2, g.giftListRepo.get(id)!!.entries.size)
    }

    @Test
    fun thePicker_findsByNameOrByKinship() {
        val friends = listOf(friend("tomas", "Tomás Costa") { copy(relationToMe = LinkType.COUSIN) }, friend("ana", "Ana"), friend("rui", "Rui"))
        assertEquals(listOf("Ana", "Rui", "Tomás Costa"), GiftListPicker.matches(friends, " ", null).map { it.name })
        assertEquals(listOf("Tomás Costa"), GiftListPicker.matches(friends, "my cousins", setOf(LinkType.COUSIN)).map { it.name })
        assertEquals(listOf("Rui"), GiftListPicker.matches(friends, "ru", null).map { it.name })
    }

    @Test
    fun aPersonsRow_showsAgeOnTheDay_ideas_andPastGifts() = runTest {
        val g = graph()
        val id = g.newList()
        g.giftLists.addPeople(id, listOf(FriendId("tomas"), FriendId("bruno"), FriendId("ghost")))
        val people = g.observeGiftList(id).first()!!.people
        assertEquals(listOf("tomas", "bruno"), people.map { it.friend.id.value }) // nobody unknown
        assertEquals(36, people[0].age) // on Christmas 2026
        assertNull(people[1].age)
        assertEquals(listOf("Scooter"), people[1].pastGifts.map { it.title })
    }

    // --- Lists ---

    @Test
    fun duplicate_keepsPeopleAndBudgets_aYearOn_withNothingTicked() = runTest {
        val g = graph()
        val id = g.giftLists.create("Christmas 2025", GiftOccasion.CHRISTMAS, LocalDate(2025, 12, 25))!!
        g.giftLists.addPeople(id, listOf(FriendId("tomas")), 30.0)
        val gift = g.giftLists.addGift(id, FriendId("tomas"), "Book")!!
        g.giftLists.advance(id, FriendId("tomas"), gift)

        val copyId = g.giftLists.duplicate(id)!!
        val copy = g.giftListRepo.get(copyId)!!
        assertEquals("Christmas 2026", copy.title)
        assertEquals(christmas, copy.occasionDate)
        assertEquals(GiftOccasion.CHRISTMAS, copy.occasion)
        assertEquals(listOf(GiftListEntry(FriendId("tomas"), 30.0)), copy.entries)
        assertEquals(g.clock.now(), copy.createdAt)
        assertNull(g.giftLists.duplicate("nope"))

        // An undated list without a year in its title keeps its name.
        val santa = GiftListMath.duplicate(GiftList("s", "Secret Santa", null, at(2026, 1, 5)), "t", at(2026, 3, 1), g.clock.timeZone)
        assertEquals("Secret Santa", santa.title)
        assertNull(santa.occasionDate)
    }

    @Test
    fun overview_putsPassedListsInPast_andCountsWhatsBought() = runTest {
        val g = graph()
        val old = g.giftLists.create("Christmas 2025", GiftOccasion.CHRISTMAS, LocalDate(2025, 12, 25))!!
        val now = g.newList()
        val santa = g.giftLists.create("Secret Santa", null, null)!!
        assertNull(g.giftLists.create("  ", null, null))
        g.giftLists.addPeople(now, listOf(FriendId("tomas"), FriendId("bruno")))
        val a = g.giftLists.addGift(now, FriendId("tomas"), "A", "€10")!!
        g.giftLists.addGift(now, FriendId("bruno"), "B")
        g.giftLists.advance(now, FriendId("tomas"), a)

        val overview = g.observeGiftLists().first()
        assertEquals(listOf(now, santa), overview.current.map { it.list.id })
        assertEquals(listOf(old), overview.past.map { it.list.id })
        val christmas = overview.current.first()
        assertEquals(2, christmas.people.size)
        assertEquals(2, christmas.gifts)
        assertEquals(1, christmas.bought)
        assertFalse(christmas.allGiven)
        assertEquals(10.0, christmas.totals.spent)

        g.giftLists.update(santa, "Secret Santa · Choir", GiftOccasion.JUST_BECAUSE, LocalDate(2026, 12, 19))
        assertEquals("Secret Santa · Choir", g.giftListRepo.get(santa)!!.title)
        val deleted = assertNotNull(g.giftLists.delete(santa))
        assertNull(g.giftListRepo.get(santa))
        g.giftLists.restore(deleted)
        assertNotNull(g.giftListRepo.get(santa))
    }

    @Test
    fun backup_carriesGiftLists() = runTest {
        val g = graph()
        val id = g.newList()
        g.giftLists.addPeople(id, listOf(FriendId("tomas")), 20.0)
        val backup = g.backupRepo.snapshot()
        assertEquals(1, backup.giftLists.size)
        g.giftListRepo.delete(id)
        g.backupRepo.restore(backup)
        assertEquals(backup.giftLists, g.giftListRepo.lists.first())
    }

    @Test
    fun theSample_hasAChristmasList_thatPointsAtRealGifts_andGoesAwayWithIt() = runTest {
        val g = TestGraph()
        g.loadSampleData()
        val list = g.observeGiftLists().first().current.single()
        assertTrue(SampleData.isSample(list.list.id))
        assertEquals(3, list.people.size)
        assertEquals(3, list.gifts)
        assertEquals(35.0, list.totals.spent)
        g.removeSampleData()
        assertTrue(g.giftListRepo.lists.first().isEmpty())
    }
}
