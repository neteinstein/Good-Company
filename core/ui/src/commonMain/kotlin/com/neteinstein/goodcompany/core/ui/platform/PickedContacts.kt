package com.neteinstein.goodcompany.core.ui.platform

import com.neteinstein.goodcompany.core.domain.model.ContactRecord

/** One data row handed back by a contact picker session (a person's name row, or one of their numbers). */
internal data class PickedContactRow(val contactKey: String?, val name: String?, val number: String?)

/**
 * Folds picker rows into one [ContactRecord] per person, in the order they were picked. A person with several rows
 * appears once, with every number those rows carry; rows without a key or a name are skipped.
 */
internal fun groupPickedContactRows(rows: List<PickedContactRow>): List<ContactRecord> {
    val byKey = LinkedHashMap<String, ContactRecord>()
    rows.forEach { row ->
        val key = row.contactKey?.takeIf { it.isNotBlank() } ?: return@forEach
        val number = row.number?.trim()?.takeIf { it.isNotEmpty() }
        val existing = byKey[key]
        if (existing == null) {
            val name = row.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            byKey[key] = ContactRecord(contactId = key, name = name, phoneNumbers = listOfNotNull(number), birthday = null)
        } else if (number != null && number !in existing.phoneNumbers) {
            byKey[key] = existing.copy(phoneNumbers = existing.phoneNumbers + number)
        }
    }
    return byKey.values.toList()
}
