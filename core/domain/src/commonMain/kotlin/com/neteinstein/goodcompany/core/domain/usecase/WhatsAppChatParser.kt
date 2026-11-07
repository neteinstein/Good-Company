package com.neteinstein.goodcompany.core.domain.usecase

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Parses a WhatsApp "Export chat → Without media" .txt file.
 *
 * WhatsApp has no public API for reading messages (neither has Messenger), so an export is the only
 * compliant way to bring conversations in. The export format depends on the phone's locale; we handle:
 *
 * - `31/12/2023, 21:41 - Ana: hi`           (Android, D/M/Y 24h)
 * - `12/31/23, 9:41 PM - Ana: hi`           (Android, M/D/Y 12h)
 * - `[31/12/2023, 21:41:05] Ana: hi`        (iOS)
 * - `31.12.23, 21:41 - Ana: hi`             (dotted dates)
 *
 * Day/month order is inferred from the whole file (any first field > 12 ⇒ D/M; any second field > 12 ⇒ M/D;
 * otherwise D/M, the most common worldwide). Continuation lines are appended to the previous message;
 * system lines without a sender are skipped.
 */
object WhatsAppChatParser {
    data class ParsedMessage(val at: Instant, val sender: String, val text: String)

    private val lineRegex = Regex(
        """^‎?\[?(\d{1,2})[./-](\d{1,2})[./-](\d{2,4}),?\s+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*([AaPp]\.?\s?[Mm]\.?)?\]?\s*(?:-\s*)?([^:]{1,80}?):\s(.*)$""",
    )

    fun parse(raw: String, zone: TimeZone): List<ParsedMessage> {
        val lines = raw.replace("\r\n", "\n").split('\n')
        val matches = lines.map { lineRegex.matchEntire(it.trim('﻿')) }
        val dayFirst = inferDayFirst(matches.filterNotNull())
        val out = mutableListOf<ParsedMessage>()
        lines.forEachIndexed { index, line ->
            val m = matches[index]
            if (m == null) {
                if (line.isNotBlank() && out.isNotEmpty()) {
                    val last = out.removeAt(out.lastIndex)
                    out += last.copy(text = last.text + "\n" + line)
                }
                return@forEachIndexed
            }
            val g = m.groupValues
            val a = g[1].toInt()
            val b = g[2].toInt()
            val (day, month) = if (dayFirst) a to b else b to a
            var year = g[3].toInt()
            if (year < 100) year += 2000
            var hour = g[4].toInt()
            val minute = g[5].toInt()
            val second = g[6].toIntOrNull() ?: 0
            val ampm = g[7].lowercase().filter { it == 'a' || it == 'p' }
            if (ampm == "p" && hour < 12) hour += 12
            if (ampm == "a" && hour == 12) hour = 0
            val at = runCatching { LocalDateTime(year, month, day, hour, minute, second).toInstant(zone) }.getOrNull()
                ?: return@forEachIndexed
            out += ParsedMessage(at, g[8].trim(), g[9].trim('‎'))
        }
        return out
    }

    private fun inferDayFirst(matches: List<MatchResult>): Boolean {
        if (matches.any { it.groupValues[1].toInt() > 12 }) return true
        if (matches.any { it.groupValues[2].toInt() > 12 }) return false
        return true
    }

    /**
     * Picks which sender is the friend: the one whose name best matches [friendName]
     * (exact, then first-name containment), falling back to the most frequent non-"You" sender.
     */
    fun pickFriendSender(messages: List<ParsedMessage>, friendName: String): String? {
        val senders = messages.groupingBy { it.sender }.eachCount()
        if (senders.isEmpty()) return null
        val target = friendName.trim().lowercase()
        val first = target.substringBefore(' ')
        return senders.keys.firstOrNull { it.lowercase() == target }
            ?: senders.keys.firstOrNull { it.lowercase().contains(first) && first.isNotBlank() }
            ?: senders.filterKeys { it.lowercase() !in setOf("you", "tu", "tú", "você") }.maxByOrNull { it.value }?.key
    }
}
