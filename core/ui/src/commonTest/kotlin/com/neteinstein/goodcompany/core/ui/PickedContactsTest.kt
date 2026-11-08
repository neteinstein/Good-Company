package com.neteinstein.goodcompany.core.ui

import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.ui.platform.PickedContactRow
import com.neteinstein.goodcompany.core.ui.platform.groupPickedContactRows
import kotlin.test.Test
import kotlin.test.assertEquals

class PickedContactsTest {
    @Test
    fun groupsSeveralPeopleKeepingPickOrder() {
        val records = groupPickedContactRows(
            listOf(
                PickedContactRow("2", "Rita", "+351 1"),
                PickedContactRow("1", "Ana", "+351 2"),
                PickedContactRow("2", "Rita", "+351 3"),
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
        val records = groupPickedContactRows(
            listOf(PickedContactRow("1", " Ana ", " +1 "), PickedContactRow("1", "Ana", "+1"), PickedContactRow("1", "Ana", " ")),
        )
        assertEquals(listOf(ContactRecord("1", "Ana", listOf("+1"), null)), records)
    }

    @Test
    fun skipsRowsWithoutKeyOrName() {
        val records = groupPickedContactRows(
            listOf(PickedContactRow(null, "Ana", "+1"), PickedContactRow("2", " ", "+2"), PickedContactRow("", "Rui", "+3")),
        )
        assertEquals(emptyList(), records)
    }
}
