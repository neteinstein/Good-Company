package com.neteinstein.goodcompany.core.domain.util

import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.SharedContent

/**
 * A place read from a shared maps link (issue #170): what the sharing app sent, nothing looked up. [name] and
 * [address] come from the share text or the link; [point] only when the link carries coordinates. [link] is the
 * original link, kept so it can be opened in the maps app later.
 */
data class SharedLocation(
    val link: String,
    val name: String? = null,
    val address: String? = null,
    val point: GeoPoint? = null,
)

/**
 * Recognises maps links in a share (issue #170): Google Maps (long URLs and the `maps.app.goo.gl` / `goo.gl/maps`
 * short links), Apple Maps, OpenStreetMap and `geo:` URIs. Pure and offline: a short link is **never** followed and
 * coordinates are **never** turned into an address (gotchas 19 and 29, decision 2026-11-25). The user edits the rest.
 */
object LocationLinks {
    private val LINK = Regex("""(?i)\b(?:https?://[^\s<>"]+|geo:[^\s<>"]+)""")
    private val NUMBER = """[-+]?\d{1,3}(?:\.\d+)?"""
    private val PAIR = Regex("""^\s*($NUMBER)\s*,\s*($NUMBER)""")
    private val PIN = Regex("""!3d($NUMBER)!4d($NUMBER)""")
    private val AT = Regex("""/@($NUMBER),($NUMBER)""")
    private val OSM_HASH = Regex("""map=\d{1,2}(?:\.\d+)?/($NUMBER)/($NUMBER)""")
    private val GEO_LABEL = Regex("""^\s*$NUMBER\s*,\s*$NUMBER\s*\((.*)\)\s*$""")

    /** The location in [content], or null when it holds no maps link. */
    fun parse(content: SharedContent): SharedLocation? = parse(content.text, content.url, content.title)

    fun parse(text: String?, url: String? = null, title: String? = null): SharedLocation? {
        val candidates = listOfNotNull(url?.trim()?.takeIf { it.isNotEmpty() }) + LINK.findAll(text.orEmpty()).map { cleanEnd(it.value) }
        val link = candidates.firstOrNull(::isMapLink) ?: return null
        val fromLink = fromLink(link)
        val (textName, textAddress) = fromText(text, link, title)
        return SharedLocation(
            link = link,
            name = textName ?: fromLink.name,
            address = textAddress ?: fromLink.address,
            point = fromLink.point,
        )
    }

    /** Drops sentence punctuation after a link in text, and a closing bracket that opens nowhere in it. */
    private fun cleanEnd(link: String): String {
        var l = link.trimEnd('.', ',', ';', '!', '?')
        while (l.endsWith(')') && l.count { it == ')' } > l.count { it == '(' }) l = l.dropLast(1).trimEnd('.', ',', ';')
        return l
    }

    /** Whether [link] is a maps link this parser knows. */
    fun isMapLink(link: String): Boolean {
        val l = link.trim()
        if (l.startsWith("geo:", ignoreCase = true)) return true
        val host = host(l) ?: return false
        val path = path(l)
        return host == "maps.app.goo.gl" ||
            (host == "goo.gl" && path.startsWith("/maps")) ||
            host.startsWith("maps.google.") ||
            (host.matches(Regex("""(www\.)?google\.[a-z.]+""")) && path.startsWith("/maps")) ||
            host == "maps.apple.com" || host == "maps.apple" ||
            host.endsWith("openstreetmap.org") || host == "osm.org"
    }

    private fun fromLink(link: String): SharedLocation {
        if (link.startsWith("geo:", ignoreCase = true)) return fromGeo(link)
        val host = host(link).orEmpty()
        val path = path(link)
        val query = query(link)
        val fragment = link.substringAfter('#', "")
        return when {
            host.startsWith("maps.apple") -> SharedLocation(
                link,
                name = query["name"] ?: query["q"]?.takeUnless { pair(it) != null },
                address = query["address"],
                point = pair(query["coordinate"]) ?: pair(query["ll"]) ?: pair(query["q"]) ?: pair(query["sll"]),
            )
            host.endsWith("openstreetmap.org") || host == "osm.org" -> SharedLocation(
                link,
                name = query["query"]?.takeUnless { pair(it) != null },
                point = point(query["mlat"], query["mlon"]) ?: OSM_HASH.find(fragment)?.let { point(it.groupValues[1], it.groupValues[2]) },
            )
            else -> {
                // Google Maps: /maps/place/<name>/@lat,lng,z/data=!3d<lat>!4d<lng>, ?q= / ?query= / ?ll= / ?center=.
                val segments = path.split('/').filter { it.isNotEmpty() }
                val placeName = segments.indexOf("place").takeIf { it >= 0 }?.let { segments.getOrNull(it + 1) }
                    ?.let(::decode)?.takeUnless { it.startsWith("@") || pair(it) != null }
                val searched = segments.indexOf("search").takeIf { it >= 0 }?.let { segments.getOrNull(it + 1) }?.let(::decode)
                val q = query["q"] ?: query["query"] ?: query["destination"]
                SharedLocation(
                    link,
                    name = placeName ?: q?.takeUnless { pair(it) != null } ?: searched?.takeUnless { pair(it) != null },
                    point = PIN.find(link)?.let { point(it.groupValues[1], it.groupValues[2]) }
                        ?: pair(q) ?: pair(query["ll"]) ?: pair(query["center"]) ?: pair(searched)
                        ?: AT.find(path)?.let { point(it.groupValues[1], it.groupValues[2]) },
                )
            }
        }
    }

    /** `geo:lat,lng;u=…?q=…`, where q is an address or `lat,lng(Label)`; `geo:0,0?q=…` has no coordinates. */
    private fun fromGeo(link: String): SharedLocation {
        val body = link.substring(4)
        val q = query(link)["q"]
        val label = q?.let { GEO_LABEL.find(it)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotEmpty) }
        val point = pair(body.substringBefore('?').substringBefore(';')) ?: pair(q)
        val text = q?.takeIf { label == null && pair(it) == null }
        return SharedLocation(link, name = label ?: text, point = point)
    }

    /**
     * The share text without its link(s): the [title] or first line is the name, the other lines the address. A single
     * line "Name, street, town" splits at its first comma. Nothing is guessed beyond that; the user edits both.
     */
    private fun fromText(text: String?, link: String, title: String?): Pair<String?, String?> {
        val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() && !isMapLink(it) }
        val lines = LINK.replace(text.orEmpty(), "\n").lines().map { it.trim().trim(',', '-', '·').trim() }
            .filter { it.isNotEmpty() && it != link }
        if (lines.isEmpty()) return cleanTitle to null
        if (cleanTitle != null) {
            val rest = lines.filterNot { it.equals(cleanTitle, ignoreCase = true) }
            val first = rest.firstOrNull()?.let { if (it.startsWith(cleanTitle)) it.removePrefix(cleanTitle).trim().trimStart(',').trim() else it }
            val address = (listOfNotNull(first) + rest.drop(1)).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { null }
            return cleanTitle to address
        }
        if (lines.size == 1) {
            val only = lines.single()
            val comma = only.indexOf(',')
            return if (comma > 0) only.substring(0, comma).trim() to only.substring(comma + 1).trim().ifEmpty { null } else only to null
        }
        return lines.first() to lines.drop(1).joinToString(", ")
    }

    private fun host(link: String): String? = Regex("""(?i)^https?://([^/?#:]+)""").find(link)?.groupValues?.get(1)?.lowercase()

    private fun path(link: String): String = Regex("""(?i)^https?://[^/?#]+([^?#]*)""").find(link)?.groupValues?.get(1).orEmpty()

    private fun query(link: String): Map<String, String> {
        val q = link.substringAfter('?', "").substringBefore('#')
        if (q.isEmpty()) return emptyMap()
        return q.split('&').mapNotNull { part ->
            val key = part.substringBefore('=').lowercase()
            val value = decode(part.substringAfter('=', "")).trim()
            if (key.isEmpty() || value.isEmpty()) null else key to value
        }.toMap()
    }

    private fun pair(value: String?): GeoPoint? = value?.let { PAIR.find(it) }?.let { point(it.groupValues[1], it.groupValues[2]) }

    private fun point(lat: String?, lng: String?): GeoPoint? {
        val la = lat?.toDoubleOrNull() ?: return null
        val lo = lng?.toDoubleOrNull() ?: return null
        if (la !in -90.0..90.0 || lo !in -180.0..180.0 || (la == 0.0 && lo == 0.0)) return null
        return GeoPoint(la, lo)
    }

    /** Percent-decoding (UTF-8) with `+` as a space; a broken escape is kept as it is. */
    internal fun decode(s: String): String {
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '+' -> {
                    bytes += ' '.code.toByte()
                    i++
                }
                c == '%' && i + 2 < s.length && s[i + 1].isHex() && s[i + 2].isHex() -> {
                    bytes += s.substring(i + 1, i + 3).toInt(16).toByte()
                    i += 3
                }
                else -> {
                    c.toString().encodeToByteArray().forEach { bytes += it }
                    i++
                }
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
