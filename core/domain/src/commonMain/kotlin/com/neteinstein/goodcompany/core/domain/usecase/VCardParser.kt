package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Birthday

/**
 * Minimal, forgiving vCard 2.1 / 3.0 / 4.0 reader for contact exports (Google Contacts, iCloud, Android,
 * Outlook). Extracts only what the app uses: name, phone numbers and birthday.
 *
 * Handles line folding, `item1.` group prefixes, `QUOTED-PRINTABLE` + `CHARSET=UTF-8` (Android 2.1 exports),
 * backslash escapes, and BDAY as `19900504`, `1990-05-04`, `--0504`, `--05-04` or with a time part.
 */
object VCardParser {
    data class Card(val name: String, val phoneNumbers: List<String>, val birthday: Birthday?)

    fun parse(raw: String): List<Card> {
        val lines = unfold(raw.replace("\r\n", "\n").replace('\r', '\n'))
        val cards = mutableListOf<Card>()
        var inCard = false
        var fn: String? = null
        var n: String? = null
        var phones = mutableListOf<String>()
        var bday: Birthday? = null
        for (line in lines) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val head = line.substring(0, colon)
            val value = line.substring(colon + 1)
            val params = head.split(';')
            val prop = params.first().substringAfter('.').uppercase()
            val decoded = decode(value, params.drop(1))
            when (prop) {
                "BEGIN" -> if (value.trim().equals("VCARD", ignoreCase = true)) {
                    inCard = true
                    fn = null
                    n = null
                    phones = mutableListOf()
                    bday = null
                }
                "END" -> if (inCard && value.trim().equals("VCARD", ignoreCase = true)) {
                    inCard = false
                    val name = fn?.takeIf { it.isNotBlank() } ?: n?.let(::nameFromN)
                    if (!name.isNullOrBlank()) cards += Card(name.trim(), phones.distinct(), bday)
                }
                "FN" -> if (inCard) fn = unescape(decoded)
                "N" -> if (inCard) n = decoded
                "TEL" -> if (inCard) unescape(decoded).removePrefix("tel:").trim().takeIf { it.any(Char::isDigit) }?.let(phones::add)
                "BDAY" -> if (inCard) bday = parseBirthday(decoded.trim())
            }
        }
        return cards
    }

    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (line in text.split('\n')) {
            when {
                // RFC folding: continuation lines start with a space or tab.
                (line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty() -> out[out.lastIndex] = out.last() + line.substring(1)
                // Quoted-printable soft line breaks end with '='.
                out.isNotEmpty() && out.last().endsWith("=") && out.last().contains("QUOTED-PRINTABLE", ignoreCase = true) ->
                    out[out.lastIndex] = out.last().dropLast(1) + line
                else -> out += line
            }
        }
        return out
    }

    private fun decode(value: String, params: List<String>): String {
        val qp = params.any { it.equals("ENCODING=QUOTED-PRINTABLE", ignoreCase = true) || it.equals("QUOTED-PRINTABLE", ignoreCase = true) }
        return if (qp) decodeQuotedPrintable(value) else value
    }

    private fun decodeQuotedPrintable(value: String): String {
        val bytes = mutableListOf<Byte>()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '=' && i + 2 <= value.lastIndex) {
                val hex = value.substring(i + 1, i + 3)
                val b = hex.toIntOrNull(16)
                if (b != null) {
                    bytes += b.toByte()
                    i += 3
                    continue
                }
            }
            c.toString().encodeToByteArray().forEach { bytes += it }
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun unescape(s: String) = s.replace("\\,", ",").replace("\\;", ";").replace("\\n", " ").replace("\\N", " ").replace("\\\\", "\\")

    /** N is "Family;Given;Middle;Prefix;Suffix". */
    private fun nameFromN(n: String): String? {
        val parts = n.split(';').map { unescape(it).trim() }
        val given = parts.getOrNull(1).orEmpty()
        val family = parts.getOrNull(0).orEmpty()
        return listOf(given, family).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
    }

    private val full = Regex("""^(\d{4})-?(\d{2})-?(\d{2})""")
    private val noYear = Regex("""^--(\d{2})-?(\d{2})""")

    fun parseBirthday(raw: String): Birthday? {
        noYear.find(raw)?.let { m -> return runCatching { Birthday(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull() }
        full.find(raw)?.let { m ->
            val year = m.groupValues[1].toInt().takeIf { it in 1800..2200 }
            return runCatching { Birthday(m.groupValues[2].toInt(), m.groupValues[3].toInt(), year) }.getOrNull()
        }
        return null
    }
}
