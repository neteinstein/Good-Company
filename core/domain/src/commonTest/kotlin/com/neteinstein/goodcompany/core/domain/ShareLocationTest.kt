package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AddressKind
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.PostalAddress
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.model.SharedImage
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.domain.usecase.SaveShareRequest
import com.neteinstein.goodcompany.core.domain.usecase.ShareChoice
import com.neteinstein.goodcompany.core.domain.usecase.ShareGuesser
import com.neteinstein.goodcompany.core.domain.usecase.SharePlace
import com.neteinstein.goodcompany.core.domain.usecase.ShareTarget
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #170: a shared maps link becomes a plan, a meetup, an address, a named place or an attachment. */
class ShareLocationTest {
    private val ana = friend("ana", "Ana Silva")
    private val rui = friend("rui", "Rui Costa")
    private val link = "https://maps.apple.com/?address=Rua%20Augusta%201,%20Lisboa&ll=38.7101,-9.1366&q=Casa%20da%20In%C3%AAs"
    private val spot = GeoPoint(38.7101, -9.1366)
    private val place = SharePlace("Casa da Inês", "Rua Augusta 1, Lisboa", spot, link)

    @Test
    fun aMapsLinkIsGuessedAsALocation_evenABareShortLink_butAPhotoNever() {
        val guess = ShareGuesser.guess(SharedContent(url = link))
        assertEquals(ShareChoice.NOTE, guess.choice, "nothing is logged or changed until the user picks")
        assertEquals(spot, guess.location?.point)
        assertEquals("Casa da Inês", guess.location?.name)

        val bare = ShareGuesser.guess(SharedContent(text = "https://maps.app.goo.gl/x1", url = "https://maps.app.goo.gl/x1"))
        val location = assertNotNull(bare.location)
        assertNull(location.name)
        assertNull(location.point)
        assertEquals(SharePlace(link = "https://maps.app.goo.gl/x1"), SharePlace.of(location))

        val photo = SharedContent(text = link, image = SharedImage(byteArrayOf(1), "image/jpeg"))
        assertNull(ShareGuesser.guess(photo).location)
        assertNull(ShareGuesser.guess(SharedContent(url = "https://example.com")).location)
    }

    @Test
    fun weMetHere_logsAMeetupAtThePlace_takesANamedPlaceNearby_andKeepsTheLinkOnIt() = runTest {
        val named = NamedPlace("p1", "Inês’s", 38.7102, -9.1366)
        val g = TestGraph(listOf(ana))
        g.hangoutRepo.upsertPlace(named)
        val result = g.saveShared(
            SaveShareRequest(
                ShareTarget.Person(ana.id),
                SharedContent(url = link),
                ShareChoice.MET_HERE,
                date = LocalDate(2026, 3, 8),
                place = place,
            ),
        )
        val hangout = g.hangoutRepo.hangouts.value.single()
        assertEquals("p1", hangout.place?.placeId, "resolved like a check-in")
        assertEquals(spot.latitude, hangout.place?.latitude)
        val a = assertNotNull(result.attachment)
        assertEquals(link, a.url)
        assertEquals(hangout.id, a.logId)
        assertTrue(a.countsAsInteraction)

        g.undoSaveShare(result)
        assertTrue(g.hangoutRepo.hangouts.value.isEmpty())
        assertTrue(g.interactionRepo.attachments.value.isEmpty())
    }

    @Test
    fun weMetHere_withoutCoordinates_keepsJustTheName() = runTest {
        val g = TestGraph(listOf(ana))
        g.saveShared(
            SaveShareRequest(
                ShareTarget.Person(ana.id),
                SharedContent(url = "https://maps.app.goo.gl/x1"),
                ShareChoice.MET_HERE,
                place = SharePlace(name = " Tasca do Chico ", link = "https://maps.app.goo.gl/x1"),
            ),
        )
        val p = g.hangoutRepo.hangouts.value.single().place
        assertEquals("Tasca do Chico", p?.name)
        assertNull(p?.latitude)
    }

    @Test
    fun homeAndWorkAddresses_areTypedAddresses_forAPersonOnly_andUndoPutsTheOldOneBack() = runTest {
        val g = TestGraph(listOf(ana.copy(addresses = listOf(PostalAddress(AddressKind.HOME, "Old street", fromContact = true)))))
        val home = g.saveShared(SaveShareRequest(ShareTarget.Person(ana.id), SharedContent(url = link), ShareChoice.HOME_ADDRESS, place = place))
        assertEquals(listOf(PostalAddress(AddressKind.HOME, "Rua Augusta 1, Lisboa")), g.friendRepo.get(ana.id)?.addresses)
        assertNull(home.attachment, "an address is not kept as an attachment too")

        // No address in the share: the name is what the user left there.
        g.saveShared(
            SaveShareRequest(
                ShareTarget.Person(ana.id),
                SharedContent(url = link),
                ShareChoice.WORK_ADDRESS,
                place = SharePlace(name = "Escritório"),
            ),
        )
        assertEquals("Escritório", g.friendRepo.get(ana.id)?.address(AddressKind.WORK)?.text)

        g.undoSaveShare(home)
        assertEquals(listOf(PostalAddress(AddressKind.HOME, "Old street", fromContact = true)), g.friendRepo.get(ana.id)?.addresses)

        g.circles.upsert(FriendGroup("crew", "Crew", memberIds = listOf(ana.id, rui.id)))
        assertFailsWith<IllegalStateException> {
            g.saveShared(SaveShareRequest(ShareTarget.Circle("crew"), SharedContent(url = link), ShareChoice.HOME_ADDRESS, place = place))
        }
    }

    @Test
    fun aNamedPlace_needsCoordinates_andUndoForgetsIt() = runTest {
        val g = TestGraph(listOf(ana))
        val result = g.saveShared(SaveShareRequest(ShareTarget.Person(ana.id), SharedContent(url = link), ShareChoice.NAMED_PLACE, place = place))
        val named = g.hangoutRepo.places.value.single()
        assertEquals("Casa da Inês", named.name)
        assertEquals(spot, named.point)
        assertEquals(NamedPlace.DEFAULT_RADIUS_M, named.radiusM)
        g.undoSaveShare(result)
        assertTrue(g.hangoutRepo.places.value.isEmpty())

        assertFailsWith<IllegalStateException> {
            g.saveShared(
                SaveShareRequest(ShareTarget.Person(ana.id), SharedContent(url = link), ShareChoice.NAMED_PLACE, place = place.copy(point = null)),
            )
        }
        // "Plan here" is saved by the plan editor, never here.
        assertFailsWith<IllegalArgumentException> {
            g.saveShared(SaveShareRequest(ShareTarget.Person(ana.id), SharedContent(url = link), ShareChoice.PLAN_HERE, place = place))
        }
    }

    @Test
    fun aPlanKeepsTheLinkAndCoordinates_andTheMeetupLoggedWhenItClosesGetsThem() = runTest {
        val g = TestGraph(listOf(ana))
        val plan = g.savePlan.save(
            null,
            PlanInput("Lunch", listOf(ana.id), LocalDate(2026, 3, 9), place = "Casa da Inês", mapLink = link, location = spot),
        )
        assertEquals(link, plan.mapLink)
        assertEquals(spot, plan.location)

        g.confirmPlan(plan.id, happened = true)
        val p = g.hangoutRepo.hangouts.value.single().place
        assertEquals("Casa da Inês", p?.name)
        assertEquals(spot.latitude, p?.latitude)
        assertEquals(spot.longitude, p?.longitude)
    }

    @Test
    fun aPlanWithoutAPlace_keepsNoLinkOrCoordinates_andEditingKeepsWhatTheEditorPassesOn() = runTest {
        val g = TestGraph(listOf(ana))
        val bare = g.savePlan.save(null, PlanInput("Lunch", listOf(ana.id), LocalDate(2026, 3, 9), place = " ", mapLink = link, location = spot))
        assertNull(bare.mapLink)
        assertNull(bare.location)

        val withPlace = g.savePlan.save(
            null,
            PlanInput("Dinner", listOf(ana.id), LocalDate(2026, 3, 9), place = "Ramiro", mapLink = link, location = spot),
        )
        val edited = g.savePlan.save(
            withPlace.id,
            PlanInput("Dinner!", listOf(ana.id), LocalDate(2026, 3, 10), place = "Ramiro", mapLink = link, location = spot),
        )
        assertEquals(spot, edited.location)
        val removed = g.savePlan.save(withPlace.id, PlanInput("Dinner!", listOf(ana.id), LocalDate(2026, 3, 10), place = "Ramiro"))
        assertNull(removed.mapLink)
        assertNull(g.planRepo.plans.first().single { it.id == withPlace.id }.location)
    }
}
