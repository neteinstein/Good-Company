package com.neteinstein.goodcompany.core.data.platform

import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNContact
import platform.Contacts.CNContactBirthdayKey
import platform.Contacts.CNContactFamilyNameKey
import platform.Contacts.CNContactFetchRequest
import platform.Contacts.CNContactGivenNameKey
import platform.Contacts.CNContactPhoneNumbersKey
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNPhoneNumber
import platform.Foundation.NSDateComponentUndefined
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class IosContactsSource : ContactsSource {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun readContacts(): List<ContactRecord> = withContext(Dispatchers.Default) {
        val status = CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts)
        // iOS 18 "limited" access (raw value 4) also allows reading the shared subset.
        if (status != CNAuthorizationStatusAuthorized && status != LIMITED_ACCESS) return@withContext emptyList()

        val keys = listOf(CNContactGivenNameKey, CNContactFamilyNameKey, CNContactPhoneNumbersKey, CNContactBirthdayKey)
        val request = CNContactFetchRequest(keysToFetch = keys)
        val out = mutableListOf<ContactRecord>()
        runCatching {
            CNContactStore().enumerateContactsWithFetchRequest(request, error = null) { contact, _ ->
                contact?.toRecord()?.let(out::add)
            }
        }
        out
    }

    private fun CNContact.toRecord(): ContactRecord? {
        val name = listOf(givenName, familyName).filter { it.isNotBlank() }.joinToString(" ")
        if (name.isBlank()) return null
        val phones = phoneNumbers.mapNotNull { ((it as? CNLabeledValue)?.value as? CNPhoneNumber)?.stringValue }
        val b = birthday
        val birthday = if (b != null && b.month in 1..12 && b.day in 1..31) {
            runCatching {
                Birthday(b.month.toInt(), b.day.toInt(), b.year.takeIf { it != NSDateComponentUndefined }?.toInt())
            }.getOrNull()
        } else {
            null
        }
        return ContactRecord(identifier, name, phones, birthday)
    }

    private companion object {
        const val LIMITED_ACCESS = 4L
    }
}
