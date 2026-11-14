package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Everything the app knows, as one portable document (export on one device, restore on another — any platform).
 * [settings] never carries the AI key: it's stripped on export and kept from the device on restore.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Backup(
    // Always written, so a restore can tell a backup from any other JSON file. Missing on read = not a backup.
    @EncodeDefault val format: String = "",
    @EncodeDefault val version: Int = VERSION,
    val exportedAt: Instant? = null,
    val friends: List<Friend> = emptyList(),
    /** Links between people (issue #39); missing in older backups. */
    val links: List<PersonLink> = emptyList(),
    val hangouts: List<Hangout> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val interactions: List<Interaction> = emptyList(),
    val messages: List<ConversationMessage> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    /** Shared attachments (D6): thumbnails and gallery references, never full photos. */
    val attachments: List<Attachment> = emptyList(),
    val ideas: List<Idea> = emptyList(),
    val groups: List<FriendGroup> = emptyList(),
    val plans: List<Plan> = emptyList(),
    /** Named places (F11): names given to GPS spots. */
    val places: List<NamedPlace> = emptyList(),
    /** Answers to calendar suggestions ("No", "Never for this series"): ids only, never event details (D3). */
    val calendarDecisions: List<CalendarDecision> = emptyList(),
    val settings: AppSettings = AppSettings(),
) {
    companion object {
        const val FORMAT = "good-company-backup"
        const val VERSION = 1
    }
}
