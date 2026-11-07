package com.neteinstein.goodcompany.core.data.source

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import kotlin.time.Instant

/** The device address book. Returns an empty list when unavailable (web) or not permitted. */
fun interface ContactsSource {
    suspend fun readContacts(): List<ContactRecord>
}

/** A call from the OS call log. Only Android exposes it, and only with a restricted permission. */
data class LogEntry(val phoneNumber: String, val type: InteractionType, val at: Instant)

interface CommunicationLogSource {
    suspend fun read(since: Instant): List<LogEntry>

    object None : CommunicationLogSource {
        override suspend fun read(since: Instant): List<LogEntry> = emptyList()
    }
}

/** Phone-number matching that tolerates formatting and country-code differences. */
object PhoneMatcher {
    private const val SIGNIFICANT_DIGITS = 9

    fun key(raw: String): String? = raw.filter(Char::isDigit).takeLast(SIGNIFICANT_DIGITS).takeIf { it.length >= 6 }

    fun <T> index(items: List<T>, numbersOf: (T) -> List<String>): Map<String, T> = buildMap {
        items.forEach { item -> numbersOf(item).mapNotNull(::key).forEach { put(it, item) } }
    }
}

/**
 * Parses address-book birthday strings: `1990-05-04`, `--05-04` (no year, Android/vCard),
 * `19900504` and `1990-05-04T00:00:00Z`. Returns null for anything else.
 */
object ContactBirthdayParser {
    private val full = Regex("""^(\d{4})-?(\d{2})-?(\d{2})""")
    private val noYear = Regex("""^--(\d{2})-?(\d{2})""")

    fun parse(raw: String?): Birthday? {
        val s = raw?.trim() ?: return null
        noYear.find(s)?.let { m -> return birthday(m.groupValues[1], m.groupValues[2], null) }
        full.find(s)?.let { m -> return birthday(m.groupValues[2], m.groupValues[3], m.groupValues[1].toInt()) }
        return null
    }

    private fun birthday(month: String, day: String, year: Int?): Birthday? =
        runCatching { Birthday(month.toInt(), day.toInt(), year?.takeIf { it in 1800..2200 }) }.getOrNull()
}
