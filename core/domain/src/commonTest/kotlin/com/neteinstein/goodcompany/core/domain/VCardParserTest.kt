package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.usecase.VCardParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VCardParserTest {
    @Test
    fun parsesGoogleStyleV3WithFoldingAndGroups() {
        val vcf = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ana Silva
            N:Silva;Ana;;;
            item1.TEL;TYPE=CELL:+351 912 345 678
            TEL;TYPE=HOME:21 000 0000
            BDAY:1990-05-04
            NOTE:a very long note that is
             folded onto the next line
            END:VCARD
            BEGIN:VCARD
            VERSION:4.0
            N:Costa;Rui;;;
            TEL;VALUE=uri:tel:+351-913-222-111
            BDAY:--0721
            END:VCARD
        """.trimIndent().replace("\n", "\r\n")
        val cards = VCardParser.parse(vcf)
        assertEquals(2, cards.size)
        assertEquals("Ana Silva", cards[0].name)
        assertEquals(listOf("+351 912 345 678", "21 000 0000"), cards[0].phoneNumbers)
        assertEquals(Birthday(5, 4, 1990), cards[0].birthday)
        assertEquals("Rui Costa", cards[1].name)
        assertEquals(listOf("+351-913-222-111"), cards[1].phoneNumbers)
        assertEquals(Birthday(7, 21), cards[1].birthday)
    }

    @Test
    fun decodesAndroidQuotedPrintable() {
        val vcf = """
            BEGIN:VCARD
            VERSION:2.1
            N;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Pereira;Jo=C3=A3o;;;
            FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Jo=C3=A3o =
            Pereira
            TEL;CELL:917000111
            END:VCARD
        """.trimIndent()
        val card = VCardParser.parse(vcf).single()
        assertEquals("João Pereira", card.name)
        assertEquals(listOf("917000111"), card.phoneNumbers)
        assertNull(card.birthday)
    }

    @Test
    fun skipsNamelessCardsAndGarbage() {
        assertTrue(VCardParser.parse("BEGIN:VCARD\nTEL:123456\nEND:VCARD\nhello").isEmpty())
        assertTrue(VCardParser.parse("not a vcard").isEmpty())
        assertEquals("A, B", VCardParser.parse("BEGIN:VCARD\nFN:A\\, B\nEND:VCARD").single().name)
    }

    @Test
    fun birthdayFormats() {
        assertEquals(Birthday(5, 4, 1990), VCardParser.parseBirthday("19900504"))
        assertEquals(Birthday(5, 4, 1990), VCardParser.parseBirthday("1990-05-04T00:00:00Z"))
        assertEquals(Birthday(5, 4), VCardParser.parseBirthday("--05-04"))
        assertNull(VCardParser.parseBirthday("May 4"))
    }
}
