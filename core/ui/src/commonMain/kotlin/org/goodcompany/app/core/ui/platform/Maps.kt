package org.goodcompany.app.core.ui.platform

import org.goodcompany.app.core.ui.format.urlEncode

/** How a platform hands an address to its maps app (issue #169). */
enum class MapsLinkStyle {
    /** Android: a `geo:` intent, answered by whichever maps app the user has. */
    GEO,

    /** iOS: an Apple Maps link, which opens the Maps app. */
    APPLE,

    /** The web: a maps search page in a new tab. */
    WEB,
}

/** This platform's [MapsLinkStyle]. */
expect val mapsLinkStyle: MapsLinkStyle

/**
 * The link that opens [address] in the system maps app (issue #169, "Open in maps"). Only the text is handed over: the
 * app itself never calls a map, place-search or geocoding service (gotcha 29). Lines are joined with ", ".
 */
fun mapsLink(address: String, style: MapsLinkStyle = mapsLinkStyle): String {
    val query = urlEncode(address.lines().map(String::trim).filter(String::isNotEmpty).joinToString(", "))
    return when (style) {
        MapsLinkStyle.GEO -> "geo:0,0?q=$query"
        MapsLinkStyle.APPLE -> "https://maps.apple.com/?q=$query"
        MapsLinkStyle.WEB -> "https://www.google.com/maps/search/?api=1&query=$query"
    }
}
