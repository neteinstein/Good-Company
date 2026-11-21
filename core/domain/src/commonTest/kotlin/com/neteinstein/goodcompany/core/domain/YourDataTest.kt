package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.ActivityChoice
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.usecase.ActivityNameProblem
import com.neteinstein.goodcompany.core.domain.usecase.ObserveActivitiesUseCase
import com.neteinstein.goodcompany.core.domain.usecase.Tags
import com.neteinstein.goodcompany.core.domain.usecase.TasteGroup
import com.neteinstein.goodcompany.core.domain.usecase.TypedPlaces
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Issue #121: your own activities, every place (with or without GPS) and tastes by list. */
class YourDataTest {
    private val ana = FriendId("ana")

    @Test
    fun customActivities_addRenameMoveRemoveAndUndo() = runTest {
        val g = TestGraph()
        val a = g.customActivities
        assertNull(a.add(" Board games "))
        assertNull(a.add("Surf"))
        assertEquals(ActivityNameProblem.BLANK, a.add("  "))
        assertEquals(ActivityNameProblem.TAKEN, a.add("board GAMES"))
        assertEquals(listOf("Board games", "Surf"), g.settingsRepo.settings.first().customActivities)

        assertEquals(ActivityNameProblem.TAKEN, a.rename("Surf", "Board games"))
        assertEquals(ActivityNameProblem.BLANK, a.rename("Surf", ""))
        assertNull(a.rename("Surf", "Surfing"))
        a.move(1, 0)
        assertEquals(listOf("Surfing", "Board games"), g.settingsRepo.settings.first().customActivities)

        assertEquals(0, a.remove("Surfing"))
        assertEquals(-1, a.remove("Nope"))
        assertEquals(listOf("Board games"), g.settingsRepo.settings.first().customActivities)
        a.restore("Surfing", 0)
        a.restore("Surfing", 0) // twice is a no-op
        assertEquals(listOf("Surfing", "Board games"), g.settingsRepo.settings.first().customActivities)
    }

    @Test
    fun customActivities_areOffered_rankedByUse_andMeetupsKeepTheirLabel() = runTest {
        val g = TestGraph()
        g.customActivities.add("Surf")
        g.setActivities(listOf(HangoutActivity.WALK))
        val h = g.checkIn(listOf(ana), activityName = "Surf")
        assertEquals(ActivityChoice.Custom("Surf"), h.activityChoice)
        assertEquals(listOf(ActivityChoice.Custom("Surf"), ActivityChoice.BuiltIn(HangoutActivity.WALK)), g.observeActivities().first())
        // Removing it only takes it out of the pickers.
        g.customActivities.remove("Surf")
        assertEquals(listOf(ActivityChoice.BuiltIn(HangoutActivity.WALK)), g.observeActivities().first())
        assertEquals("Surf", g.hangoutRepo.hangouts.value.single().activityName)

        val ranked = ObserveActivitiesUseCase.ranked(emptyList(), listOf(HangoutActivity.LUNCH), listOf("Surf", "Surf"))
        assertEquals(listOf(ActivityChoice.BuiltIn(HangoutActivity.LUNCH), ActivityChoice.Custom("Surf")), ranked)
        assertNull(ActivityChoice.of(null, "  "))
        assertEquals(ActivityChoice.BuiltIn(HangoutActivity.LUNCH), ActivityChoice.of(HangoutActivity.LUNCH, "Surf"))
    }

    @Test
    fun aPlan_keepsItsOwnActivity() = runTest {
        val g = TestGraph()
        val plan = g.savePlan(null, "Waves", listOf(ana), LocalDate(2026, 3, 14), activityName = "Surf")
        assertEquals(ActivityChoice.Custom("Surf"), plan.activityChoice)
        assertEquals("Surf", g.planRepo.plans.first().single().activityName)
    }

    @Test
    fun typedPlaces_areMeetupsAndPlansWithNoNamedPlace() {
        val named = listOf(NamedPlace("p1", "Home", 1.0, 2.0))
        val hangouts = listOf(
            Hangout("a", listOf(ana), at(2026, 3, 1), Place("Café Nata")),
            Hangout("b", listOf(ana), at(2026, 3, 2), Place("Café Nata ")),
            Hangout("c", listOf(ana), at(2026, 3, 3), Place("Home", placeId = "p1")),
            Hangout("d", listOf(ana), at(2026, 3, 4), Place("Old spot", placeId = "gone")),
            Hangout("e", listOf(ana), at(2026, 3, 5), Place(latitude = 1.0, longitude = 2.0)),
        )
        val plans = listOf(Plan("x", listOf(ana), "Lunch", LocalDate(2026, 3, 9), place = "cafe nata", createdAt = at(2026, 3, 1)))
        val typed = TypedPlaces.of(hangouts, plans, named, listOf(friend("ana")))
        assertEquals(listOf("Café Nata", "cafe nata", "Old spot"), typed.map { it.name })
        assertEquals(2, typed[0].meetups)
        assertEquals(listOf("Ana"), typed[0].usually.map { it.name })
        assertEquals(1, typed[1].plans)
    }

    @Test
    fun typedPlaces_mergeSpellings_andPinCoordinates() = runTest {
        val g = TestGraph()
        g.hangoutRepo.add(Hangout("a", listOf(ana), at(2026, 3, 1), Place("Café Nata")))
        g.hangoutRepo.add(Hangout("b", listOf(ana), at(2026, 3, 2), Place("cafe nata")))
        g.planRepo.upsert(Plan("x", listOf(ana), "Lunch", LocalDate(2026, 3, 9), place = "cafe nata", createdAt = at(2026, 3, 1)))

        assertEquals(2, g.renameTypedPlace(setOf("cafe nata"), "Café Nata"))
        assertEquals(0, g.renameTypedPlace(setOf("Café Nata"), "Café Nata"))
        assertEquals(listOf("Café Nata"), g.observeTypedPlaces().first().map { it.name })

        val pinned = g.pinTypedPlace("Café Nata", GeoPoint(38.7, -9.1))!!
        assertEquals(listOf(pinned.id, pinned.id), g.hangoutRepo.hangouts.value.map { it.place?.placeId })
        // The plan keeps its text, so it still shows there.
        assertEquals(listOf("Café Nata"), g.observeTypedPlaces().first().map { it.name })
        assertEquals(0, g.observeTypedPlaces().first().single().meetups)
        assertNull(g.pinTypedPlace("  ", GeoPoint(0.0, 0.0)))
    }

    @Test
    fun tastes_byList_withCounts() {
        val catalog = Tags.catalog(
            listOf(
                friend("ana") { copy(food = listOf("Sushi"), drinks = listOf("Port"), likes = listOf("Sushi")) },
                friend("rui") { copy(food = listOf("Sushi"), interests = listOf("Chess"), dislikes = listOf("Port")) },
            ),
        )
        assertEquals(listOf("Sushi"), Tags.inGroup(catalog, TasteGroup.FOOD).map { it.name })
        assertEquals(2, catalog.single { it.name == "Sushi" }.kinds[TasteGroup.FOOD])
        assertEquals(listOf("Port"), Tags.inGroup(catalog, TasteGroup.DISLIKES).map { it.name })
        assertEquals(
            mapOf(TasteGroup.FOOD to 1, TasteGroup.DRINKS to 1, TasteGroup.INTERESTS to 1, TasteGroup.LIKES to 1, TasteGroup.DISLIKES to 1),
            Tags.groupCounts(catalog),
        )
    }
}
