package com.neteinstein.goodcompany.core.data.platform

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.neteinstein.goodcompany.core.data.source.ContactBirthdayParser
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidContactsSource(private val context: Context) : ContactsSource {
    // READ_CONTACTS exists only in the sideload build (the contact list with checkboxes and the write-back). People are
    // chosen one by one (that list or the system contact picker), so a sync must never turn the whole address book
    // into friends.
    override val importsNewContacts: Boolean = false

    override suspend fun readContacts(withPhotos: Boolean): List<ContactRecord> = withContext(Dispatchers.IO) {
        if (!canRead()) return@withContext emptyList()
        val resolver = context.contentResolver
        val names = LinkedHashMap<String, String>()
        val starred = HashSet<String>()
        val photoIds = HashMap<String, Long>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_ID,
                ContactsContract.Contacts.STARRED,
            ),
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                val id = c.getString(0)
                names[id] = name
                if (!c.isNull(2)) photoIds[id] = c.getLong(2)
                if (c.getInt(3) != 0) starred += id
            }
        }
        val photos = if (withPhotos) readThumbnails(resolver, photoIds) else emptyMap()

        val phones = HashMap<String, MutableList<String>>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.CONTACT_ID, ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(1) ?: continue
                phones.getOrPut(c.getString(0)) { mutableListOf() } += number
            }
        }

        val emails = HashMap<String, MutableList<String>>()
        resolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Email.CONTACT_ID, ContactsContract.CommonDataKinds.Email.ADDRESS),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val address = c.getString(1)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                emails.getOrPut(c.getString(0)) { mutableListOf() } += address
            }
        }

        val birthdays = HashMap<String, Birthday>()
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.CommonDataKinds.Event.START_DATE),
            "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
            arrayOf(
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString(),
            ),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                ContactBirthdayParser.parse(c.getString(1))?.let { birthdays[c.getString(0)] = it }
            }
        }

        // The sideload build mirrors app data onto contacts through a raw contact of its own (AndroidContactsWriter).
        // It is joined to the person's real contact, but should that ever fail it must not show up as a new friend.
        val mirrorOnly = mirrorOnlyContactIds(resolver)
        names.filterKeys {
            it !in mirrorOnly
        }.map { (id, name) ->
            ContactRecord(id, name, phones[id].orEmpty(), birthdays[id], photos[id], emails[id].orEmpty(), starred = id in starred)
        }
    }

    /** Only the people saved from the contact list: their photo ids, then their thumbnails. */
    override suspend fun readPhotos(contactIds: Set<String>): Map<String, ContactPhoto> = withContext(Dispatchers.IO) {
        if (contactIds.isEmpty() || !canRead()) return@withContext emptyMap()
        val resolver = context.contentResolver
        val photoIds = HashMap<String, Long>()
        contactIds.chunked(MAX_QUERY_ARGS).forEach { chunk ->
            runCatching {
                resolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.PHOTO_ID),
                    "${ContactsContract.Contacts._ID} IN (${chunk.joinToString(",") { "?" }})",
                    chunk.toTypedArray(),
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        if (!c.isNull(1)) photoIds[c.getString(0)] = c.getLong(1)
                    }
                }
            }
        }
        readThumbnails(resolver, photoIds)
    }

    private fun canRead(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /**
     * Each contact's primary photo as the thumbnail the provider keeps in the data row (about 96 px), read in a few
     * batched queries. A contact photo changed in the Contacts app gets a new row, so the next sync picks it up.
     */
    private fun readThumbnails(resolver: ContentResolver, photoIds: Map<String, Long>): Map<String, ContactPhoto> {
        if (photoIds.isEmpty()) return emptyMap()
        val contactByPhoto = photoIds.entries.associate { (contact, photo) -> photo to contact }
        val out = HashMap<String, ContactPhoto>()
        contactByPhoto.keys.chunked(MAX_QUERY_ARGS).forEach { chunk ->
            runCatching {
                resolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data._ID, ContactsContract.CommonDataKinds.Photo.PHOTO),
                    "${ContactsContract.Data._ID} IN (${chunk.joinToString(",") { "?" }})",
                    chunk.map(Long::toString).toTypedArray(),
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val contact = contactByPhoto[c.getLong(0)] ?: continue
                        ContactPhoto.of(if (c.isNull(1)) null else c.getBlob(1))?.let { out[contact] = it }
                    }
                }
            }
        }
        return out
    }

    private companion object {
        /** SQLite's default limit on `?` arguments is 999. */
        const val MAX_QUERY_ARGS = 500
    }

    /** Contacts made up solely of raw contacts this app created. */
    private fun mirrorOnlyContactIds(resolver: ContentResolver): Set<String> {
        val ours = HashSet<String>()
        val others = HashSet<String>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.CONTACT_ID, ContactsContract.RawContacts.SOURCE_ID),
            "${ContactsContract.RawContacts.DELETED} = 0",
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val contactId = c.getString(0) ?: continue
                if (c.getString(1)?.startsWith(OwnRawContacts.PREFIX) == true) ours += contactId else others += contactId
            }
        }
        return ours - others
    }
}
