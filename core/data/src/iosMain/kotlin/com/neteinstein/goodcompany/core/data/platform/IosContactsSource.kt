package com.neteinstein.goodcompany.core.data.platform

import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Contacts.CNAuthorizationStatusAuthorized
import platform.Contacts.CNContact
import platform.Contacts.CNContactBirthdayKey
import platform.Contacts.CNContactEmailAddressesKey
import platform.Contacts.CNContactFamilyNameKey
import platform.Contacts.CNContactFetchRequest
import platform.Contacts.CNContactGivenNameKey
import platform.Contacts.CNContactPhoneNumbersKey
import platform.Contacts.CNContactStore
import platform.Contacts.CNContactThumbnailImageDataKey
import platform.Contacts.CNEntityType
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNPhoneNumber
import platform.Contacts.predicateForContactsWithIdentifiers
import platform.Foundation.NSDateComponentUndefined
import platform.posix.memcpy

internal class IosContactsSource : ContactsSource {
    // People are chosen (the contact list with checkboxes or the system picker, decision D2): a sync only refreshes
    // friends already linked to a contact and never turns the whole address book into friends.
    override val importsNewContacts: Boolean = false

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun readContacts(withPhotos: Boolean): List<ContactRecord> = withContext(Dispatchers.Default) {
        if (!canRead()) return@withContext emptyList()

        val keys = listOfNotNull(
            CNContactGivenNameKey,
            CNContactFamilyNameKey,
            CNContactPhoneNumbersKey,
            CNContactBirthdayKey,
            CNContactThumbnailImageDataKey.takeIf { withPhotos },
            CNContactEmailAddressesKey,
        )
        val request = CNContactFetchRequest(keysToFetch = keys)
        val out = mutableListOf<ContactRecord>()
        runCatching {
            CNContactStore().enumerateContactsWithFetchRequest(request, error = null) { contact, _ ->
                contact?.toRecord()?.let(out::add)
            }
        }
        out
    }

    /** Only the people saved from the contact list: one fetch narrowed to their identifiers, thumbnails only. */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override suspend fun readPhotos(contactIds: Set<String>): Map<String, ContactPhoto> = withContext(Dispatchers.Default) {
        if (contactIds.isEmpty() || !canRead()) return@withContext emptyMap()
        val request = CNContactFetchRequest(keysToFetch = listOf(CNContactThumbnailImageDataKey))
        request.predicate = CNContact.predicateForContactsWithIdentifiers(contactIds.toList())
        val out = HashMap<String, ContactPhoto>()
        runCatching {
            CNContactStore().enumerateContactsWithFetchRequest(request, error = null) { contact, _ ->
                contact?.let { c -> c.thumbnail()?.let { out[c.identifier] = it } }
            }
        }
        out
    }

    private fun canRead(): Boolean {
        val status = CNContactStore.authorizationStatusForEntityType(CNEntityType.CNEntityTypeContacts)
        // iOS 18 "limited" access (raw value 4) also allows reading the shared subset.
        return status == CNAuthorizationStatusAuthorized || status == LIMITED_ACCESS
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
        return ContactRecord(identifier, name, phones, birthday, thumbnail(), emails())
    }

    /** The contact's email addresses, when that key was fetched. */
    private fun CNContact.emails(): List<String> {
        // Reading a key that wasn't fetched raises an Objective-C exception, which Kotlin can't catch.
        if (!isKeyAvailable(CNContactEmailAddressesKey)) return emptyList()
        return emailAddresses.mapNotNull { ((it as? CNLabeledValue)?.value as? String)?.trim()?.takeIf(String::isNotEmpty) }
            .distinctBy(String::lowercase)
    }

    /** The contact's thumbnail (the small picture Contacts shows in lists), when it was fetched and there is one. */
    @OptIn(ExperimentalForeignApi::class)
    private fun CNContact.thumbnail(): ContactPhoto? {
        // Reading a key that wasn't fetched raises an Objective-C exception, which Kotlin can't catch.
        if (!isKeyAvailable(CNContactThumbnailImageDataKey)) return null
        val data = thumbnailImageData ?: return null
        val size = data.length.toInt()
        if (size == 0 || size > ContactPhoto.MAX_BYTES) return null
        val bytes = ByteArray(size)
        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return ContactPhoto.of(bytes)
    }

    private companion object {
        const val LIMITED_ACCESS = 4L
    }
}
