package com.neteinstein.goodcompany.core.domain.util

/**
 * Phone-number matching that tolerates formatting and country-code differences: two numbers match when their last
 * nine digits do. Used by the call-log sync and by "Share → Good Company" (a number in the shared text).
 */
object PhoneMatcher {
    private const val SIGNIFICANT_DIGITS = 9
    private const val MIN_DIGITS = 6

    /** Candidate phone numbers in free text: runs of digits with the usual separators, at least six digits long. */
    private val NUMBER = Regex("""\+?\d[\d\s().\-]{4,}\d""")

    fun key(raw: String): String? = raw.filter(Char::isDigit).takeLast(SIGNIFICANT_DIGITS).takeIf { it.length >= MIN_DIGITS }

    fun <T> index(items: List<T>, numbersOf: (T) -> List<String>): Map<String, T> = buildMap {
        items.forEach { item -> numbersOf(item).mapNotNull(::key).forEach { put(it, item) } }
    }

    /** The match keys of every phone number written in [text]. */
    fun keysIn(text: String): Set<String> = NUMBER.findAll(text).mapNotNull { key(it.value) }.toSet()
}
