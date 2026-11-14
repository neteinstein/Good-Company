package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.LogRequest
import com.neteinstein.goodcompany.core.domain.usecase.LogResult
import com.neteinstein.goodcompany.core.domain.usecase.NamedPlaces
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NamedPlacesTest {
    private val home = GeoPoint(38.7139, -9.1394)

    /** [metres] north of [from] (one degree of latitude is ~111.195 km on the mean-radius sphere). */
    private fun north(from: GeoPoint, metres: Double) = GeoPoint(from.latitude + metres / 111_194.93, from.longitude)

    private fun place(id: String, name: String, at: GeoPoint, radius: Int = NamedPlace.DEFAULT_RADIUS_M) =
        NamedPlace(id, name, at.latitude, at.longitude, radius)

    private fun metAt(id: String, placeId: String?, day: Int, vararg who: String) =
        Hangout(id, who.map(::FriendId), at(2026, 3, day), Place("x", home.latitude, home.longitude, placeId))

    @Test
    fun haversine_knownDistances() {
        assertEquals(0.0, NamedPlaces.distanceM(home, home))
        assertTrue(abs(NamedPlaces.distanceM(home, north(home, 1_000.0)) - 1_000.0) < 0.5)
        // Lisbon → Porto, about 274 km as the crow flies.
        val porto = GeoPoint(41.1579, -8.6291)
        assertTrue(NamedPlaces.distanceM(home, porto) in 272_000.0..276_000.0)
        // Symmetric, and fine across the antimeridian.
        val east = GeoPoint(0.0, 179.9995)
        val west = GeoPoint(0.0, -179.9995)
        assertTrue(abs(NamedPlaces.distanceM(east, west) - 111.2) < 0.5)
        assertEquals(NamedPlaces.distanceM(home, porto), NamedPlaces.distanceM(porto, home))
    }

    @Test
    fun nearest_insideTheRadiusOnly_theEdgeCounts() {
        val marta = place("p1", "Marta’s house", home)
        assertEquals("p1", NamedPlaces.nearest(listOf(marta), north(home, 99.9))?.place?.id)
        assertNull(NamedPlaces.nearest(listOf(marta), north(home, 100.1)))
        // Right at the edge: a millimetre either side.
        assertEquals("p1", NamedPlaces.nearest(listOf(marta), north(home, 99.999))?.place?.id)
        assertNull(NamedPlaces.nearest(listOf(marta), north(home, 100.001)))
        // The radius is per place.
        assertEquals("p1", NamedPlaces.nearest(listOf(marta.copy(radiusM = 200)), north(home, 150.0))?.place?.id)
        assertNull(NamedPlaces.nearest(emptyList(), home))
    }

    @Test
    fun overlappingPlaces_theNearestWins() {
        val mum = place("mum", "Mum’s", home)
        val graca = place("graca", "Graça’s place", north(home, 40.0))
        val fix = north(home, 30.0) // 30 m from Mum's, 10 m from Graça's
        val match = NamedPlaces.nearest(listOf(mum, graca), fix)
        assertEquals("graca", match?.place?.id)
        assertTrue(abs(match!!.distanceM - 10.0) < 0.5)
        assertEquals("mum", NamedPlaces.nearest(listOf(mum, graca), north(home, 15.0))?.place?.id)
        // In Settings, each is offered to the other for a merge.
        assertEquals("graca", NamedPlaces.nearbyDuplicate(mum, listOf(mum, graca))?.place?.id)
        assertNull(NamedPlaces.nearbyDuplicate(mum, listOf(mum, place("far", "Far", north(home, 500.0)))))
    }

    @Test
    fun usualPeople_mostFrequentInTheLastEightMeetupsThere_tiesByRecency() {
        val hangouts = buildList {
            // Old meetups (outside the last 8) are all with Zé: he must not win.
            repeat(5) { add(metAt("old$it", "p", day = 1 + it, "ze")) }
            add(metAt("a", "p", day = 10, "ana", "rui"))
            add(metAt("b", "p", day = 11, "ana"))
            add(metAt("c", "p", day = 12, "ana", "bea"))
            add(metAt("d", "p", day = 13, "rui"))
            add(metAt("e", "p", day = 14, "bea"))
            add(metAt("f", "p", day = 15, "ana"))
            add(metAt("g", "p", day = 16, "ze"))
            add(metAt("h", "p", day = 17, "ivo"))
            add(metAt("elsewhere", "q", day = 18, "ivo", "ivo2"))
            add(metAt("unlinked", null, day = 19, "ivo"))
        }
        // Last 8 at p: a..h → Ana 4, Rui 2, Bea 2 (Bea seen more recently), Zé 1 (day 16), Ivo 1 (day 17).
        assertEquals(listOf("ana", "bea", "rui", "ivo", "ze"), NamedPlaces.usualPeople("p", hangouts).map { it.value })
        assertEquals(listOf("bea", "rui"), NamedPlaces.usualPeople("p", hangouts, exclude = setOf(FriendId("ana")), limit = 2).map { it.value })
        assertTrue(NamedPlaces.usualPeople("nowhere", hangouts).isEmpty())
    }

    @Test
    fun secondCheckInAtInesHouse_namesItself_andSuggestsTheUsualPeople() = runTest {
        val g = TestGraph(friends = listOf(friend("ines", "Inês"), friend("rui", "Rui"), friend("ana", "Ana")))
        val first = g.logInteraction(
            LogRequest(listOf(FriendId("ines"), FriendId("rui")), LogKind.MEETUP, placeName = " Inês’s house ", location = home),
        ) as LogResult.Met
        val places = g.hangoutRepo.places.value
        assertEquals(listOf("Inês’s house"), places.map { it.name })
        assertEquals(places.single().id, first.hangout.place?.placeId)

        // Second time, 30 m away and no name typed: it names itself.
        val book = g.observePlaceBook().first()
        val match = book.match(north(home, 30.0))
        assertEquals("Inês’s house", match?.place?.name)
        assertEquals(listOf("ines", "rui"), book.usualPeople(match!!.place.id, exclude = emptyList()).map { it.value })
        assertEquals(listOf("rui"), book.usualPeople(match.place.id, exclude = listOf(FriendId("ines"))).map { it.value })
        assertEquals(1, book.recentMeetups(match.place.id))

        val second = g.logInteraction(LogRequest(listOf(FriendId("ana")), LogKind.MEETUP, location = north(home, 30.0))) as LogResult.Met
        assertEquals("Inês’s house", second.hangout.place?.name)
        assertEquals(match.place.id, second.hangout.place?.placeId)
        assertEquals(1, g.hangoutRepo.places.value.size) // no new place
        // Nobody is ticked from a suggestion: only who was picked is there.
        assertEquals(listOf("ana"), second.hangout.friendIds.map { it.value })
    }

    @Test
    fun checkIn_skipKeepsCoordinatesOnly_aDifferentNameMakesANewPlace_noGpsNoPlace() = runTest {
        val g = TestGraph(friends = listOf(friend("ana")))
        val ana = listOf(FriendId("ana"))
        // Skip: no name, nothing near → just coordinates.
        val skipped = g.checkIn(ana, location = home)
        assertEquals(Place(null, home.latitude, home.longitude, null), skipped.place)
        assertTrue(g.hangoutRepo.places.value.isEmpty())
        // A name without GPS stays free text.
        val typed = g.checkIn(ana, placeName = "Café Tati")
        assertNull(typed.place?.placeId)
        assertTrue(g.hangoutRepo.places.value.isEmpty())
        // Named once; the same name (any case) inside the radius reuses it, another name makes another place.
        g.checkIn(ana, placeName = "Café Tati", location = home)
        val again = g.checkIn(ana, placeName = "café tati", location = north(home, 20.0))
        assertEquals("Café Tati", again.place?.name)
        g.checkIn(ana, placeName = "Bar next door", location = north(home, 20.0))
        assertEquals(listOf("Café Tati", "Bar next door"), g.hangoutRepo.places.value.map { it.name })
    }

    @Test
    fun renameUpdatesEveryMeetupThere_mergeMovesThem_deleteHasUndo() = runTest {
        val g = TestGraph(friends = listOf(friend("ana"), friend("rui")))
        val mum = place("mum", "Mum’s", home)
        val graca = place("graca", "Graça’s place", north(home, 40.0))
        g.hangoutRepo.upsertPlace(mum)
        g.hangoutRepo.upsertPlace(graca)
        g.hangoutRepo.add(Hangout("h1", listOf(FriendId("ana")), at(2026, 3, 1), Place("Mum’s", home.latitude, home.longitude, "mum")))
        g.hangoutRepo.add(Hangout("h2", listOf(FriendId("rui")), at(2026, 3, 2), Place("Graça’s place", 0.0, 0.0, "graca")))
        g.hangoutRepo.add(Hangout("h3", listOf(FriendId("rui")), at(2026, 3, 3), Place("Mum’s"))) // free text, not linked

        g.renamePlace("mum", "  Mum & Dad’s ")
        g.renamePlace("mum", "   ") // ignored
        assertEquals("Mum & Dad’s", g.hangoutRepo.places.value.first { it.id == "mum" }.name)
        assertEquals(listOf("Mum & Dad’s", "Graça’s place", "Mum’s"), g.hangoutRepo.hangouts.value.map { it.place?.name })

        val summary = g.observePlaces().first()
        assertEquals(listOf("Graça’s place", "Mum & Dad’s"), summary.map { it.place.name }) // 1 meetup each: A–Z
        assertEquals(listOf("Ana"), summary.first { it.place.id == "mum" }.usually.map { it.name })
        assertEquals("graca", summary.first { it.place.id == "mum" }.nearby?.place?.id)

        g.mergePlaces("graca", "mum")
        g.mergePlaces("mum", "mum") // no-op
        assertEquals(listOf("mum"), g.hangoutRepo.places.value.map { it.id })
        assertEquals(listOf("mum", "mum", null), g.hangoutRepo.hangouts.value.map { it.place?.placeId })
        assertEquals("Mum & Dad’s", g.hangoutRepo.hangouts.value[1].place?.name)
        assertEquals(2, g.observePlaces().first().single().meetups)

        val removed = g.deletePlace("mum")
        assertEquals("mum", removed?.id)
        assertTrue(g.hangoutRepo.places.value.isEmpty())
        assertEquals("Mum & Dad’s", g.hangoutRepo.hangouts.value.first().place?.name) // meetups keep their name
        assertNull(g.deletePlace("mum"))
        g.restorePlace(removed!!)
        assertEquals(2, g.observePlaces().first().single().meetups) // undo links them again
    }

    @Test
    fun placesAreInTheBackup() = runTest {
        val g = TestGraph()
        val mum = place("mum", "Mum’s", home, radius = 150)
        g.hangoutRepo.upsertPlace(mum)
        val snapshot = g.backupRepo.snapshot()
        assertEquals(listOf(mum), snapshot.places)
        val decoded = kotlinx.serialization.json.Json.decodeFromString(
            Backup.serializer(),
            kotlinx.serialization.json.Json.encodeToString(Backup.serializer(), snapshot),
        )
        assertEquals(listOf(mum), decoded.places)
        // A backup made before named places restores with none.
        g.backupRepo.restore(Backup(format = Backup.FORMAT))
        assertTrue(g.hangoutRepo.places.value.isEmpty())
        g.backupRepo.restore(snapshot)
        assertEquals(listOf(mum), g.hangoutRepo.places.value)
    }
}
