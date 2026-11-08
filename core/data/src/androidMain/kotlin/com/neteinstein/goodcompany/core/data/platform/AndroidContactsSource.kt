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
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidContactsSource(private val context: Context) : ContactsSource {
    override suspend fun readContacts(): List<ContactRecord> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }
        val resolver = context.contentResolver
        val names = LinkedHashMap<String, String>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                names[c.getString(0)] = name
            }
        }

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
        names.filterKeys { it !in mirrorOnly }.map { (id, name) -> ContactRecord(id, name, phones[id].orEmpty(), birthdays[id]) }
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
