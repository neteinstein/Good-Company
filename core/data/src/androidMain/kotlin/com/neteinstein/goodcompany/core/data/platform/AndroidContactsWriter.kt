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
import com.neteinstein.goodcompany.core.data.source.ContactRowPlanner
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.ContactCard
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.NoteLabels
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.repository.ContactsWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.neteinstein.goodcompany.core.domain.model.Relation as FamilyRelation

/** How the app recognises the raw contacts it created. */
internal object OwnRawContacts {
    const val PREFIX = "goodcompany:"

    fun sourceId(friendId: FriendId): String = PREFIX + friendId.value
}

/**
 * Which rows are interchangeable: all notes, birthdays, other dated events, and relations of one type (all children,
 * all partners…; custom relations by their label, so a pet never takes over an "other" row).
 */
private fun kindOf(mime: String, data2: String?, data3: String? = null): String = when {
    mime == Event.CONTENT_ITEM_TYPE -> "$mime/$data2"
    mime == Relation.CONTENT_ITEM_TYPE && data2 == Relation.TYPE_CUSTOM.toString() -> "$mime/$data2/$data3"
    mime == Relation.CONTENT_ITEM_TYPE -> "$mime/$data2"
    else -> mime
}

/** Only events carry identity in DATA3 (their label); for the rest DATA1 (name / note text) says it all. */
private fun keyOf(mime: String, data1: String?, data3: String?): String =
    listOf(mime, data1, if (mime == Event.CONTENT_ITEM_TYPE) data3 else null).joinToString("\u0000")

/**
 * Mirrors a friend's kids, events, birthday and notes onto their address-book entry, so the contact itself shows them.
 * Only the Android **sideload** build declares WRITE_CONTACTS; elsewhere [write] does nothing.
 *
 * **Never deletes.** The app keeps one row per item (one note, one birthday, one row per kid or event): a changed
 * detail updates the row the app wrote earlier instead of adding a second one next to it (see [ContactRowPlanner]), and
 * a detail that disappears from the app is simply left on the contact. There is deliberately no delete call in this
 * class, so a bug can leave stale rows behind but can never take data off a contact. Updates only ever touch rows of
 * the app's own entry (each update is scoped to its raw contact); the user's own contact (their Notes, birthday,
 * phones…) is only ever read.
 *
 * The rows live in one extra *raw contact* that this app owns (no account, so it never syncs to Google; its
 * `SOURCE_ID` is `goodcompany:<friendId>`), kept together with the person's real contact by an aggregation
 * exception. Contacts apps show the combined result.
 */
internal class AndroidContactsWriter(private val context: Context) : ContactsWriter {
    override suspend fun write(contactId: String, friendId: FriendId, card: ContactCard) {
        withContext(Dispatchers.IO) {
            if (!canWrite()) return@withContext
            val resolver = context.contentResolver
            val own = findOwnRawContact(resolver, friendId)
            // A birthday the user's own contact already has always wins: never add a second one next to it.
            val includeBirthday = card.birthday != null && !otherBirthdayExists(resolver, contactId, own)
            val wanted = rowsFor(card, includeBirthday)
            if (own == null) {
                if (wanted.isNotEmpty()) create(resolver, contactId, friendId, wanted)
            } else {
                val steps = ContactRowPlanner.plan(
                    stored = readStored(resolver, own),
                    wanted = wanted.map { ContactRowPlanner.Wanted(it.kind, it.key, it) },
                )
                if (steps.isNotEmpty()) apply(resolver, own, steps)
            }
        }
    }

    private fun canWrite(): Boolean = listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    // --- What goes on the contact ---

    /** A `ContactsContract.Data` row: its MIME type plus the columns we set. */
    private class Row(val values: ContentValues) {
        private val mime: String get() = values.getAsString(ContactsContract.Data.MIMETYPE)

        /** Which rows are interchangeable (all notes, all children, birthdays, other dated events). */
        val kind: String
            get() = kindOf(mime, values.getAsString(ContactsContract.Data.DATA2), values.getAsString(ContactsContract.Data.DATA3))

        /** What makes two rows "the same" (providers normalise the other columns). */
        val key: String
            get() = keyOf(mime, values.getAsString(ContactsContract.Data.DATA1), values.getAsString(ContactsContract.Data.DATA3))
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
        card.relatives.forEach { r ->
            when (r.relation) {
                FamilyRelation.PARTNER -> add(relationRow(r.name, Relation.TYPE_PARTNER))
                FamilyRelation.PARENT -> add(relationRow(r.name, Relation.TYPE_PARENT))
                // Android only has brother / sister, and the app doesn't know which.
                FamilyRelation.SIBLING ->
                    add(relationRow(r.name, Relation.TYPE_CUSTOM, context.getString(R.string.contact_relation_sibling)))
                FamilyRelation.PET ->
                    add(relationRow(r.name, Relation.TYPE_CUSTOM, context.getString(R.string.contact_relation_pet)))
                FamilyRelation.OTHER ->
                    add(relationRow(r.name, Relation.TYPE_CUSTOM, context.getString(R.string.contact_relation_other)))
            }
        }
        // Links to other people in the app (issue #39): Android's own types where it has one (gendered ones only when the
        // person's pronouns say so), a labelled custom relation otherwise.
        card.links.forEach { link ->
            val (type, label) = when (link.role) {
                LinkType.PARENT -> when (link.pronouns) {
                    Pronouns.SHE -> Relation.TYPE_MOTHER to null
                    Pronouns.HE -> Relation.TYPE_FATHER to null
                    else -> Relation.TYPE_PARENT to null
                }
                LinkType.CHILD -> Relation.TYPE_CHILD to null
                LinkType.SIBLING -> when (link.pronouns) {
                    Pronouns.SHE -> Relation.TYPE_SISTER to null
                    Pronouns.HE -> Relation.TYPE_BROTHER to null
                    else -> Relation.TYPE_CUSTOM to R.string.contact_relation_sibling
                }
                LinkType.SPOUSE -> Relation.TYPE_SPOUSE to null
                LinkType.DATING, LinkType.PARTNER -> Relation.TYPE_PARTNER to null
                LinkType.FRIEND -> Relation.TYPE_FRIEND to null
                LinkType.GRANDPARENT -> Relation.TYPE_CUSTOM to R.string.contact_relation_grandparent
                LinkType.GRANDCHILD -> Relation.TYPE_CUSTOM to R.string.contact_relation_grandchild
                LinkType.AUNT_UNCLE -> Relation.TYPE_CUSTOM to R.string.contact_relation_aunt_uncle
                LinkType.NIECE_NEPHEW -> Relation.TYPE_CUSTOM to R.string.contact_relation_niece_nephew
                LinkType.COUSIN -> Relation.TYPE_CUSTOM to R.string.contact_relation_cousin
                LinkType.IN_LAW -> Relation.TYPE_CUSTOM to R.string.contact_relation_in_law
                LinkType.EX -> Relation.TYPE_CUSTOM to R.string.contact_relation_ex
                LinkType.COLLEAGUE -> Relation.TYPE_CUSTOM to R.string.contact_relation_colleague
                LinkType.STEP_PARENT -> Relation.TYPE_CUSTOM to R.string.contact_relation_step_parent
                LinkType.STEP_CHILD -> Relation.TYPE_CUSTOM to R.string.contact_relation_step_child
                LinkType.HALF_SIBLING -> Relation.TYPE_CUSTOM to R.string.contact_relation_half_sibling
                LinkType.STEP_SIBLING -> Relation.TYPE_CUSTOM to R.string.contact_relation_step_sibling
            }
            add(relationRow(link.name, type, label?.let(context::getString)))
        }
        card.referredBy?.let { add(relationRow(it, Relation.TYPE_REFERRED_BY)) }
        card.events.forEach { add(eventRow(Event.TYPE_CUSTOM, it.title, it.date.toString())) }
        val labels = NoteLabels(
            likes = context.getString(R.string.contact_likes),
            dislikes = context.getString(R.string.contact_dislikes),
            job = context.getString(R.string.contact_job),
            livesIn = context.getString(R.string.contact_lives_in),
            met = context.getString(R.string.contact_met),
            food = context.getString(R.string.contact_food),
            drinks = context.getString(R.string.contact_drinks),
            interests = context.getString(R.string.contact_interests),
        )
        card.noteText(labels)?.let { text ->
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

    private fun relationRow(name: String, type: Int, label: String? = null) = Row(
        ContentValues().apply {
            put(ContactsContract.Data.MIMETYPE, Relation.CONTENT_ITEM_TYPE)
            put(Relation.NAME, name)
            put(Relation.TYPE, type)
            if (label != null) put(Relation.LABEL, label)
        },
    )

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

    /** True when the person's own contact (not our raw contact [own]) already has a birthday. */
    private fun otherBirthdayExists(resolver: ContentResolver, contactId: String, own: Long?): Boolean {
        val selection = StringBuilder(
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
        )
        val args = mutableListOf(contactId, Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString())
        if (own != null) {
            selection.append(" AND ${ContactsContract.Data.RAW_CONTACT_ID} != ?")
            args += own.toString()
        }
        return resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID),
            selection.toString(),
            args.toTypedArray(),
            null,
        )?.use { it.moveToFirst() } ?: false
    }

    private fun readStored(resolver: ContentResolver, rawContactId: Long): List<ContactRowPlanner.Stored> = buildList {
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data._ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
                ContactsContract.Data.DATA3,
            ),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ?",
            arrayOf(rawContactId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(1) ?: continue
                add(ContactRowPlanner.Stored(c.getLong(0), kindOf(mime, c.getString(3), c.getString(4)), keyOf(mime, c.getString(2), c.getString(4))))
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

    /** Inserts new rows and updates the app's own earlier ones in place. There is no delete here, by design. */
    private fun apply(resolver: ContentResolver, rawContactId: Long, steps: List<ContactRowPlanner.Step<Row>>) {
        val ops = ArrayList<ContentProviderOperation>()
        steps.forEach { step ->
            when (step) {
                is ContactRowPlanner.Step.Insert -> ops += ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValues(step.row.payload.values)
                    .build()

                is ContactRowPlanner.Step.Update -> ops += ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    // Scoped to our own raw contact: a row of the user's own contact can never match.
                    .withSelection(
                        "${ContactsContract.Data._ID} = ? AND ${ContactsContract.Data.RAW_CONTACT_ID} = ?",
                        arrayOf(step.storedId.toString(), rawContactId.toString()),
                    )
                    // The MIME type of an existing row is never changed.
                    .withValues(ContentValues(step.row.payload.values).apply { remove(ContactsContract.Data.MIMETYPE) })
                    .build()
            }
        }
        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
    }
}
