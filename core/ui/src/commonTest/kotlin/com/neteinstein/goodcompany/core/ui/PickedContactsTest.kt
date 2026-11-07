package com.neteinstein.goodcompany.core.ui

import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.ui.platform.PickedPhoneRow
import com.neteinstein.goodcompany.core.ui.platform.groupPickedPhoneRows
import kotlin.test.Test
import kotlin.test.assertEquals

class PickedContactsTest {
    @Test
    fun groupsSeveralPeopleKeepingPickOrder() {
        val records = groupPickedPhoneRows(
            listOf(
                PickedPhoneRow("2", "Rita", "+351 1"),
                PickedPhoneRow("1", "Ana", "+351 2"),
                PickedPhoneRow("2", "Rita", "+351 3"),
            ),
        )
        assertEquals(
            listOf(
                ContactRecord("2", "Rita", listOf("+351 1", "+351 3"), null),
                ContactRecord("1", "Ana", listOf("+351 2"), null),
            ),
            records,
        )
    }

    @Test
    fun dropsDuplicateAndBlankNumbers() {
        val records = groupPickedPhoneRows(
            listOf(PickedPhoneRow("1", " Ana ", " +1 "), PickedPhoneRow("1", "Ana", "+1"), PickedPhoneRow("1", "Ana", " ")),
        )
        assertEquals(listOf(ContactRecord("1", "Ana", listOf("+1"), null)), records)
    }

    @Test
    fun skipsRowsWithoutKeyOrName() {
        val records = groupPickedPhoneRows(
            listOf(PickedPhoneRow(null, "Ana", "+1"), PickedPhoneRow("2", " ", "+2"), PickedPhoneRow("", "Rui", "+3")),
        )
        assertEquals(emptyList(), records)
    }
}
