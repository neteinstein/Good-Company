package com.neteinstein.goodcompany.core.data

import com.neteinstein.goodcompany.core.data.source.ContactBirthdayParser
import com.neteinstein.goodcompany.core.domain.util.PhoneMatcher
import com.neteinstein.goodcompany.core.domain.model.Birthday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourcesTest {
    @Test
    fun birthdayFormats() {
        assertEquals(Birthday(5, 4, 1990), ContactBirthdayParser.parse("1990-05-04"))
        assertEquals(Birthday(5, 4), ContactBirthdayParser.parse("--05-04"))
        assertEquals(Birthday(5, 4, 1990), ContactBirthdayParser.parse("19900504"))
        assertEquals(Birthday(12, 31, 1985), ContactBirthdayParser.parse("1985-12-31T00:00:00.000Z"))
        assertNull(ContactBirthdayParser.parse("May 4"))
        assertNull(ContactBirthdayParser.parse("1990-13-40"))
        assertNull(ContactBirthdayParser.parse(null))
    }

    @Test
    fun phoneKeysIgnoreFormattingAndCountryCode() {
        assertEquals(PhoneMatcher.key("+351 912-345-678"), PhoneMatcher.key("912345678"))
        assertNull(PhoneMatcher.key("112"))
    }
}
