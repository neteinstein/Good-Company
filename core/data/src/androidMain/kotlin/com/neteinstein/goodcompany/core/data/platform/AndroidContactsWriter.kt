package com.neteinstein.goodcompany.core.data.platform

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Relation
import androidx.core.content.ContextCompat
import com.neteinstein.goodcompany.core.data.R
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How the app recognises the raw contacts it created. */
internal object OwnRawContacts {
    const val PREFIX = "goodcompany:"

    fun sourceId(friendId: FriendId): String = PREFIX + friendId.value
}

/**
 * Mirrors a friend's kids, events, birthday and notes onto their address-book entry, so the contact itself shows them.
 * Only the Android **sideload** build declares WRITE_CONTACTS; elsewhere [write] does nothing.
 *
 * **Append-only, by design: nothing is ever deleted, updated or overwritten here.** New details are added as extra
 * rows; rows already on the contact stay, even when the app's own data later changes or goes away (an edited event
 * title therefore appears next to the old one). There is deliberately no call here that deletes or edits a data row (the
 * one `update` is the aggregation exception that links our raw contact to the real one), so a bug can add noise but
 * can never take data off a contact. The user's own contact (their Notes, birthday, phones…)
 * is only ever read.
 *
 * The rows go into one extra *raw contact* that this app owns (no account, so it never syncs to Google; its
 * `SOURCE_ID` is `goodcompany:<friendId>`), kept together with the person's real contact by an aggregation
 * exception. Contacts apps show the combined result.
 */
internal class AndroidContactsWriter(private val context: Context) : ContactsWriter {
    override suspend fun write(contactId: String, friendId: FriendId, card: ContactCard) {
        withContext(Dispatchers.IO) {
            if (!canWrite()) return@withContext
            val resolver = context.contentResolver
            val own = findOwnRawContact(resolver, friendId)
            // A birthday the contact already has (ours or the user's own) always wins: never add a second one.
            val includeBirthday = card.birthday != null && !birthdayExists(resolver, contactId)
            val wanted = rowsFor(card, includeBirthday)
            if (own == null) {
                if (wanted.isNotEmpty()) create(resolver, contactId, friendId, wanted)
            } else {
                val stored = readRows(resolver, own).map(Row::key).toSet()
                val missing = wanted.distinctBy(Row::key).filter { it.key !in stored }
                if (missing.isNotEmpty()) append(resolver, own, missing)
            }
        }
    }

    private fun canWrite(): Boolean = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // --- What goes on the contact ---

    /** A `ContactsContract.Data` row: its MIME type plus the columns we set. */
    private class Row(val values: ContentValues) {
        val mime: String get() = values.getAsString(ContactsContract.Data.MIMETYPE)

        /** What identifies the row when comparing with what's stored (providers normalise the other columns). */
        val key: String
            get() = listOf(
                mime,
                values.getAsString(ContactsContract.Data.DATA1),
                if (mime == Event.CONTENT_ITEM_TYPE) values.getAsString(ContactsContract.Data.DATA3) else null,
            ).joinToString("\u0000")
    }

    private fun rowsFor(card: ContactCard, includeBirthday: Boolean): List<Row> = buildList {
        if (includeBirthday) card.birthday?.let { add(eventRow(Event.TYPE_BIRTHDAY, null, it.toContactDate())) }
        card.kids.forEach { kid ->
            add(
                Row(
                    ContentValues().apply {
                        put(ContactsContract.Data.MIMETYPE, Relation.CONTENT_ITEM_TYPE)
                        put(Relation.NAME, kid.name)
                        put(Relation.TYPE, Relation.TYPE_CHILD)
                    },
                ),
            )
            kid.birthday?.let {
                add(eventRow(Event.TYPE_CUSTOM, context.getString(R.string.contact_kid_birthday, kid.name), it.toContactDate()))
            }
        }
        card.events.forEach { add(eventRow(Event.TYPE_CUSTOM, it.title, it.date.toString())) }
        card.noteText(context.getString(R.string.contact_likes), context.getString(R.string.contact_dislikes))?.let { text ->
            add(
                Row(
                    ContentValues().apply {
                        put(ContactsContract.Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
                        put(Note.NOTE, text)
                    },
                ),
            )
        }
    }

    private fun eventRow(type: Int, label: String?, date: String) = Row(
        ContentValues().apply {
            put(ContactsContract.Data.MIMETYPE, Event.CONTENT_ITEM_TYPE)
            put(Event.START_DATE, date)
            put(Event.TYPE, type)
            if (label != null) put(Event.LABEL, label)
        },
    )

    /**
     * `1990-05-04`, or `--05-04` when the year is unknown (the address-book convention). Padded by hand: `String.format`
     * follows the device locale and would write non-ASCII digits on e.g. Arabic or Persian phones.
     */
    private fun Birthday.toContactDate(): String {
        val md = "${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        return if (year != null) "${year.toString().padStart(4, '0')}-$md" else "--$md"
    }

    // --- Content provider plumbing ---

    private fun findOwnRawContact(resolver: ContentResolver, friendId: FriendId): Long? = resolver.query(
        ContactsContract.RawContacts.CONTENT_URI,
        arrayOf(ContactsContract.RawContacts._ID),
        "${ContactsContract.RawContacts.SOURCE_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
        arrayOf(OwnRawContacts.sourceId(friendId)),
        null,
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    /** One of the person's own raw contacts (not ours): what the mirror gets joined to. */
    private fun findAnchorRawContact(resolver: ContentResolver, contactId: String): Long? = resolver.query(
        ContactsContract.RawContacts.CONTENT_URI,
        arrayOf(ContactsContract.RawContacts._ID),
        "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0 AND " +
            "(${ContactsContract.RawContacts.SOURCE_ID} IS NULL OR ${ContactsContract.RawContacts.SOURCE_ID} NOT LIKE ?)",
        arrayOf(contactId, OwnRawContacts.PREFIX + "%"),
        null,
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    /** True when the contact already shows a birthday, whoever wrote it. */
    private fun birthdayExists(resolver: ContentResolver, contactId: String): Boolean = resolver.query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(ContactsContract.Data._ID),
        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
        arrayOf(contactId, Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()),
        null,
    )?.use { it.moveToFirst() } ?: false

    private fun readRows(resolver: ContentResolver, rawContactId: Long): List<Row> = buildList {
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA3),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ?",
            arrayOf(rawContactId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                add(
                    Row(
                        ContentValues().apply {
                            put(ContactsContract.Data.MIMETYPE, c.getString(0))
                            put(ContactsContract.Data.DATA1, c.getString(1))
                            put(ContactsContract.Data.DATA3, c.getString(2))
                        },
                    ),
                )
            }
        }
    }

    private fun create(resolver: ContentResolver, contactId: String, friendId: FriendId, rows: List<Row>) {
        val anchor = findAnchorRawContact(resolver, contactId) ?: return // the contact is gone
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
            .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
            .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            .withValue(ContactsContract.RawContacts.SOURCE_ID, OwnRawContacts.sourceId(friendId))
            .build()
        rows.forEach { row ->
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValues(row.values)
                .build()
        }
        // Join it to the person's real contact, so Contacts shows one entry with everything on it.
        ops += ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
            .withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
            .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, anchor)
            .withValueBackReference(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, 0)
            .build()
        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }

    /** Adds rows to the app's own raw contact. Insert only: existing rows are never touched. */
    private fun append(resolver: ContentResolver, rawContactId: Long, rows: List<Row>) {
        val ops = ArrayList<ContentProviderOperation>()
        rows.forEach { row ->
            ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                .withValues(row.values)
                .build()
        }
        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }
}
