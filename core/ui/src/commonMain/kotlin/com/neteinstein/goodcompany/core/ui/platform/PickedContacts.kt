package com.neteinstein.goodcompany.core.ui.platform

import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ContactRecord

/** One data row handed back by a contact picker session (a person's name row, one of their numbers, or their photo). */
internal data class PickedContactRow(val contactKey: String?, val name: String?, val number: String?, val photo: ContactPhoto? = null)

/**
 * Folds picker rows into one [ContactRecord] per person, in the order they were picked. A person with several rows
 * appears once, with every number those rows carry and the first photo; rows without a key are skipped, and so is a
 * person none of whose rows has a name.
 */
internal fun groupPickedContactRows(rows: List<PickedContactRow>): List<ContactRecord> {
    val byKey = LinkedHashMap<String, ContactRecord>()
    rows.forEach { row ->
        val key = row.contactKey?.takeIf { it.isNotBlank() } ?: return@forEach
        val number = row.number?.trim()?.takeIf { it.isNotEmpty() }
        val name = row.name?.trim()?.takeIf { it.isNotEmpty() }
        val existing = byKey[key] ?: ContactRecord(contactId = key, name = "", phoneNumbers = emptyList(), birthday = null)
        byKey[key] = existing.copy(
            name = existing.name.ifEmpty { name.orEmpty() },
            phoneNumbers = if (number != null && number !in existing.phoneNumbers) existing.phoneNumbers + number else existing.phoneNumbers,
            photo = existing.photo ?: row.photo,
        )
    }
    return byKey.values.filter { it.name.isNotEmpty() }
}
