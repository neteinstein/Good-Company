package org.goodcompany.app.core.domain

import org.goodcompany.app.core.domain.model.GeoPoint
import org.goodcompany.app.core.domain.model.SharedContent
import org.goodcompany.app.core.domain.util.LocationLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #170: maps links as the sharing apps send them, parsed on the device with nothing fetched. */
class LocationLinksTest {
    private data class Sample(
        val app: String,
        val content: SharedContent,
        val name: String?,
        val address: String?,
        val point: GeoPoint?,
    )

    private val samples = listOf(
        Sample(
            "Google Maps Android, Share place (name, address, short link)",
            SharedContent(text = "Cervejaria Ramiro\nAv. Almirante Reis 1, 1150-007 Lisboa\nhttps://maps.app.goo.gl/kQ9yC3Zx8nG2"),
            "Cervejaria Ramiro",
            "Av. Almirante Reis 1, 1150-007 Lisboa",
            null,
        ),
        Sample(
            "Google Maps Android, one line with the subject as title",
            SharedContent(
                text = "Cervejaria Ramiro, Av. Almirante Reis 1, 1150-007 Lisboa https://maps.app.goo.gl/kQ9yC3Zx8nG2",
                url = "https://maps.app.goo.gl/kQ9yC3Zx8nG2",
                title = "Cervejaria Ramiro",
            ),
            "Cervejaria Ramiro",
            "Av. Almirante Reis 1, 1150-007 Lisboa",
            null,
        ),
        Sample(
            "Google Maps, old goo.gl/maps short link, no text",
            SharedContent(text = "https://goo.gl/maps/AbCdEf123", url = "https://goo.gl/maps/AbCdEf123"),
            null,
            null,
            null,
        ),
        Sample(
            "Google Maps long URL, place with pin and viewport",
            SharedContent(
                url = "https://www.google.com/maps/place/Jardim+da+Estrela/@38.7134,-9.1601,17z/" +
                    "data=!3m1!4b1!4m6!3m5!1s0xd19!8m2!3d38.7139!4d-9.1597!16s",
            ),
            "Jardim da Estrela",
            null,
            GeoPoint(38.7139, -9.1597),
        ),
        Sample(
            "Google Maps long URL, accents percent-encoded, only the viewport",
            SharedContent(url = "https://www.google.pt/maps/place/Past%C3%A9is+de+Bel%C3%A9m/@38.6975,-9.2032,17z"),
            "Pastéis de Belém",
            null,
            GeoPoint(38.6975, -9.2032),
        ),
        Sample(
            "Google Maps ?q=lat,lng (a dropped pin)",
            SharedContent(text = "Dropped pin\nhttps://maps.google.com/?q=41.1496,-8.6109"),
            "Dropped pin",
            null,
            GeoPoint(41.1496, -8.6109),
        ),
        Sample(
            "Google Maps URLs API, ?api=1&query=",
            SharedContent(url = "https://www.google.com/maps/search/?api=1&query=Time+Out+Market+Lisboa"),
            "Time Out Market Lisboa",
            null,
            null,
        ),
        Sample(
            "Google Maps URLs API, query is coordinates",
            SharedContent(url = "https://www.google.com/maps/search/?api=1&query=38.7071%2C-9.1456"),
            null,
            null,
            GeoPoint(38.7071, -9.1456),
        ),
        Sample(
            "Apple Maps iOS, classic URL with title",
            SharedContent(
                url = "https://maps.apple.com/?address=Avenida%20Almirante%20Reis%201,%201150-007%20Lisboa,%20Portugal" +
                    "&auid=123&ll=38.7208,-9.1355&lsp=9902&q=Cervejaria%20Ramiro",
                title = "Cervejaria Ramiro",
            ),
            "Cervejaria Ramiro",
            "Avenida Almirante Reis 1, 1150-007 Lisboa, Portugal",
            GeoPoint(38.7208, -9.1355),
        ),
        Sample(
            "Apple Maps iOS 18, /place with coordinate and name",
            SharedContent(
                url = "https://maps.apple.com/place?address=Rua%20Augusta,%20Lisboa&coordinate=38.7101,-9.1366" +
                    "&name=Arco%20da%20Rua%20Augusta&place-id=I1",
            ),
            "Arco da Rua Augusta",
            "Rua Augusta, Lisboa",
            GeoPoint(38.7101, -9.1366),
        ),
        Sample(
            "Apple Maps short link, nothing else",
            SharedContent(url = "https://maps.apple/p/AbC.dEf"),
            null,
            null,
            null,
        ),
        Sample(
            "geo: with a label",
            SharedContent(text = "geo:38.7223,-9.1393?q=38.7223,-9.1393(Casa%20da%20In%C3%AAs)"),
            "Casa da Inês",
            null,
            GeoPoint(38.7223, -9.1393),
        ),
        Sample(
            "geo:0,0 with an address only",
            SharedContent(text = "geo:0,0?q=Rua+de+Santa+Catarina+112,+Porto"),
            "Rua de Santa Catarina 112, Porto",
            null,
            null,
        ),
        Sample(
            "geo: with uncertainty",
            SharedContent(text = "geo:37.786971,-122.399677;u=35"),
            null,
            null,
            GeoPoint(37.786971, -122.399677),
        ),
        Sample(
            "OpenStreetMap marker",
            SharedContent(url = "https://www.openstreetmap.org/?mlat=38.6916&mlon=-9.2160#map=17/38.6916/-9.2160"),
            null,
            null,
            GeoPoint(38.6916, -9.2160),
        ),
        Sample(
            "OpenStreetMap, only the map view",
            SharedContent(text = "Torre de Belém https://www.openstreetmap.org/#map=18/38.69158/-9.21598"),
            "Torre de Belém",
            null,
            GeoPoint(38.69158, -9.21598),
        ),
    )

    @Test
    fun everySample_isALocation_withWhatTheShareCarries() {
        samples.forEach { s ->
            val loc = LocationLinks.parse(s.content)
            checkNotNull(loc) { "${s.app}: not recognised" }
            assertEquals(s.name, loc.name, s.app)
            assertEquals(s.address, loc.address, s.app)
            assertEquals(s.point, loc.point, s.app)
        }
    }

    @Test
    fun theOriginalLinkIsKept_asSent() {
        val text = "Cervejaria Ramiro\nhttps://maps.app.goo.gl/kQ9yC3Zx8nG2"
        assertEquals("https://maps.app.goo.gl/kQ9yC3Zx8nG2", LocationLinks.parse(text)?.link)
    }

    @Test
    fun otherLinks_andPlainText_areNotLocations() {
        assertNull(LocationLinks.parse(SharedContent(url = "https://www.example.com/maps/place/x")))
        assertNull(LocationLinks.parse(SharedContent(text = "Lunch at Ramiro on Friday?")))
        assertNull(LocationLinks.parse(SharedContent(url = "https://www.google.com/search?q=ramiro")))
        assertFalse(LocationLinks.isMapLink("https://notgoogle.com/maps"))
        assertTrue(LocationLinks.isMapLink("https://maps.google.co.uk/?q=1,2"))
    }

    @Test
    fun impossibleCoordinates_areDropped_butTheLinkStaysALocation() {
        val loc = LocationLinks.parse(SharedContent(url = "https://maps.google.com/?q=123.4,-9.1"))
        assertNull(loc?.point)
        assertEquals("https://maps.google.com/?q=123.4,-9.1", loc?.link)
    }
}
