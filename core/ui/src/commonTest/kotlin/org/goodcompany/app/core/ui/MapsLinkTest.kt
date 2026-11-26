package org.goodcompany.app.core.ui

import org.goodcompany.app.core.ui.platform.MapsLinkStyle
import org.goodcompany.app.core.ui.platform.mapsLink
import kotlin.test.Test
import kotlin.test.assertEquals

/** Issue #169: "Open in maps" hands only the address text to the system maps app. */
class MapsLinkTest {
    private val address = " Rua São Bento 5\n\n4000-001 Porto "

    @Test
    fun eachPlatformGetsItsLink_withTheLinesJoinedAndEncoded() {
        val query = "Rua%20S%C3%A3o%20Bento%205%2C%204000-001%20Porto"
        assertEquals("geo:0,0?q=$query", mapsLink(address, MapsLinkStyle.GEO))
        assertEquals("https://maps.apple.com/?q=$query", mapsLink(address, MapsLinkStyle.APPLE))
        assertEquals("https://www.google.com/maps/search/?api=1&query=$query", mapsLink(address, MapsLinkStyle.WEB))
    }

    @Test
    fun symbolsThatWouldBreakTheLinkAreEncoded() {
        assertEquals("geo:0,0?q=A%20%26%20B%20%231%3F", mapsLink("A & B #1?", MapsLinkStyle.GEO))
    }
}
