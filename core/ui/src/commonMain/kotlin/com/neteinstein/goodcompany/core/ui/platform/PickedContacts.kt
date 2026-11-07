package com.neteinstein.goodcompany.core.ui.platform

import com.neteinstein.goodcompany.core.domain.model.ContactRecord

/** One phone-number row handed back by a contact picker session (one per picked number). */
internal data class PickedPhoneRow(val contactKey: String?, val name: String?, val number: String?)

/**
 * Folds picker rows into one [ContactRecord] per person, in the order they were picked. A person who shares several
 * numbers appears once with all of them; rows without a key or a name are skipped.
 */
internal fun groupPickedPhoneRows(rows: List<PickedPhoneRow>): List<ContactRecord> {
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
