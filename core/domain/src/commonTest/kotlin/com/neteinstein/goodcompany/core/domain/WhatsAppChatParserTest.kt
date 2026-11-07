package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.usecase.WhatsAppChatParser
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WhatsAppChatParserTest {
    @Test
    fun parsesAndroidDayFirst24h() {
        val raw = """
            31/12/2025, 21:41 - Messages and calls are end-to-end encrypted.
            31/12/2025, 21:41 - Ana Silva: Happy new year!
            01/01/2026, 09:05 - Me: You too 🎉
            and a second line
        """.trimIndent()
        val msgs = WhatsAppChatParser.parse(raw, TimeZone.UTC)
        assertEquals(2, msgs.size)
        assertEquals(at(2025, 12, 31, 21, 41), msgs[0].at)
        assertEquals("Ana Silva", msgs[0].sender)
        assertEquals("You too 🎉\nand a second line", msgs[1].text)
    }

    @Test
    fun parsesUsMonthFirst12h() {
        val raw = "12/31/25, 9:41 PM - Ana: hey\n1/2/26, 12:15 AM - Ana: still up?"
        val msgs = WhatsAppChatParser.parse(raw, TimeZone.UTC)
        assertEquals(at(2025, 12, 31, 21, 41), msgs[0].at)
        assertEquals(at(2026, 1, 2, 0, 15), msgs[1].at)
    }

    @Test
    fun parsesIosBracketedFormat() {
        val raw = "[03/02/2026, 18:20:05] Rui: concert friday?"
        val msg = WhatsAppChatParser.parse(raw, TimeZone.UTC).single()
        assertEquals("Rui", msg.sender)
        assertEquals("concert friday?", msg.text)
        assertEquals(at(2026, 2, 3, 18, 20), kotlin.time.Instant.fromEpochSeconds(msg.at.epochSeconds - 5))
    }

    @Test
    fun parsesDottedDates() {
        val msg = WhatsAppChatParser.parse("24.12.25, 20:00 - Zoe: merry xmas", TimeZone.UTC).single()
        assertEquals(at(2025, 12, 24, 20, 0), msg.at)
    }

    @Test
    fun garbageYieldsNothing() {
        assertTrue(WhatsAppChatParser.parse("hello\nworld", TimeZone.UTC).isEmpty())
    }

    @Test
    fun picksFriendSenderByName() {
        val msgs = WhatsAppChatParser.parse("1/1/26, 10:00 - Ana Silva: a\n1/1/26, 10:01 - Pedro: b\n1/1/26, 10:02 - Pedro: c", TimeZone.UTC)
        assertEquals("Ana Silva", WhatsAppChatParser.pickFriendSender(msgs, "Ana"))
        assertEquals("Pedro", WhatsAppChatParser.pickFriendSender(msgs, "Someone Else"))
    }
}
