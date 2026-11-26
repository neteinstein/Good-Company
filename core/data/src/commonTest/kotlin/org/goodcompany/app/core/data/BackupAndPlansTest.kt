package org.goodcompany.app.core.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.goodcompany.app.core.data.repository.BackupRepositoryImpl
import org.goodcompany.app.core.data.repository.FriendRepositoryImpl
import org.goodcompany.app.core.data.repository.GroupRepositoryImpl
import org.goodcompany.app.core.data.repository.HangoutRepositoryImpl
import org.goodcompany.app.core.data.repository.IdeaRepositoryImpl
import org.goodcompany.app.core.data.repository.InteractionRepositoryImpl
import org.goodcompany.app.core.data.repository.PlanRepositoryImpl
import org.goodcompany.app.core.data.repository.ReminderRepositoryImpl
import org.goodcompany.app.core.data.repository.SettingsRepositoryImpl
import org.goodcompany.app.core.data.source.CommunicationLogSource
import org.goodcompany.app.core.data.source.ContactsSource
import org.goodcompany.app.core.domain.model.Attachment
import org.goodcompany.app.core.domain.model.AttachmentKind
import org.goodcompany.app.core.domain.model.Backup
import org.goodcompany.app.core.domain.model.CatchUpCounts
import org.goodcompany.app.core.domain.model.CircleColor
import org.goodcompany.app.core.domain.model.Closeness
import org.goodcompany.app.core.domain.model.ContactPhoto
import org.goodcompany.app.core.domain.model.ConversationMessage
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Gift
import org.goodcompany.app.core.domain.model.Hangout
import org.goodcompany.app.core.domain.model.HangoutActivity
import org.goodcompany.app.core.domain.model.Idea
import org.goodcompany.app.core.domain.model.IdeaKind
import org.goodcompany.app.core.domain.model.Interaction
import org.goodcompany.app.core.domain.model.InteractionSource
import org.goodcompany.app.core.domain.model.InteractionType
import org.goodcompany.app.core.domain.model.JournalEntry
import org.goodcompany.app.core.domain.model.Occasion
import org.goodcompany.app.core.domain.model.PhotoPlatform
import org.goodcompany.app.core.domain.model.PhotoRef
import org.goodcompany.app.core.domain.model.Plan
import org.goodcompany.app.core.domain.model.PlanStatus
import org.goodcompany.app.core.domain.model.Reminder
import org.goodcompany.app.core.domain.model.ReminderRepeat
import org.goodcompany.app.core.domain.usecase.ExportBackupUseCase
import org.goodcompany.app.core.domain.usecase.RestoreBackupUseCase
import org.goodcompany.app.core.domain.usecase.RestoreResult
import org.goodcompany.app.core.testing.FixedClock
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupAndPlansTest {
    private class Stores(val settings: MapSettings = MapSettings()) {
        val clock = FixedClock()
        val friends = FriendRepositoryImpl(settings, ContactsSource { emptyList() })
        val hangouts = HangoutRepositoryImpl(settings)
        val reminders = ReminderRepositoryImpl(settings)
        val interactions = InteractionRepositoryImpl(settings, CommunicationLogSource.None, clock)
        val ideas = IdeaRepositoryImpl(settings)
        val groups = GroupRepositoryImpl(settings)
        val plans = PlanRepositoryImpl(settings)
        val appSettings = SettingsRepositoryImpl(settings)
        val backup = BackupRepositoryImpl(friends, hangouts, reminders, interactions, ideas, groups, plans, appSettings)
    }

    @Test
    fun plansPersistAndOldAnsweredOnesAreTrimmed() = runTest {
        val s = Stores()
        s.plans.upsert(Plan("p", listOf(FriendId("ana")), "Lunch", LocalDate(2026, 3, 12), createdAt = at(2026, 3, 1)))
        assertEquals("Lunch", PlanRepositoryImpl(s.settings).plans.first().single().title)
        repeat(205) { i ->
            s.plans.upsert(Plan("old$i", emptyList(), "x", LocalDate(2025, 1, 1), status = PlanStatus.SKIPPED, createdAt = at(2025, 1, 1)))
        }
        val all = s.plans.plans.first()
        assertEquals(201, all.size)
        assertTrue(all.any { it.id == "p" })
        s.plans.delete("p")
        assertTrue(s.plans.plans.first().none { it.id == "p" })
    }

    @Test
    fun exportThenRestoreOnAnotherDevice() = runTest {
        val from = Stores()
        val ana = FriendId("ana")
        from.friends.upsert(friend("ana", "Ana") { copy(occasions = listOf(Occasion.COFFEE)) })
        from.hangouts.add(Hangout("h", listOf(ana), at(2026, 3, 1)))
        from.reminders.upsert(Reminder("r", "Tickets", createdAt = at(2026, 3, 1)))
        from.interactions.addAll(listOf(Interaction(ana, InteractionType.CALL, at(2026, 3, 2))))
        from.interactions.addMessages(listOf(ConversationMessage(ana, at(2026, 3, 2), fromMe = true, text = "hi")))
        val email = Interaction(ana, InteractionType.EMAIL, at(2026, 3, 3), id = "e1", note = "flat listings", batchId = "b")
        from.interactions.addAll(listOf(email))
        from.interactions.upsertJournal(JournalEntry("j1", ana, at(2026, 3, 4), "Talked about Porto"))
        from.hangouts.add(Hangout("h2", listOf(ana), at(2026, 3, 5), activity = HangoutActivity.LUNCH))
        from.ideas.addAll(listOf(Idea("i", listOf(ana), IdeaKind.EVENT, "Jazz", "", createdAt = at(2026, 3, 1))))
        from.groups.upsert(FriendGroup("g", "Crew", memberIds = listOf(ana), meetEveryDays = 15, approxLastMet = LocalDate(2026, 2, 1)))
        from.plans.upsert(Plan("p", listOf(ana), "Lunch", LocalDate(2026, 3, 12), createdAt = at(2026, 3, 1)))
        from.appSettings.update { it.copy(aiApiKey = "sk-secret", homeArea = "Lisbon") }

        val json = ExportBackupUseCase(from.backup, from.clock)()
        assertFalse("sk-secret" in json)

        val to = Stores()
        to.friends.upsert(friend("zed"))
        to.appSettings.update { it.copy(aiApiKey = "sk-mine") }
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))

        val snap: Backup = to.backup.snapshot()
        assertEquals(listOf("Ana"), snap.friends.map { it.name })
        assertEquals(listOf(Occasion.COFFEE), snap.friends.single().occasions)
        assertEquals(2, snap.hangouts.size)
        assertEquals(HangoutActivity.LUNCH, snap.hangouts.single { it.id == "h2" }.activity)
        assertEquals(1, snap.reminders.size)
        assertEquals(2, snap.interactions.size)
        assertEquals(email, snap.interactions.single { it.id == "e1" })
        assertEquals(listOf("Talked about Porto"), snap.journal.map { it.text })
        assertEquals(1, snap.messages.size)
        assertEquals(1, snap.ideas.size)
        // Issue #150: the circle's "When did it last meet?" answer survives a backup.
        assertEquals(LocalDate(2026, 2, 1), snap.groups.single().approxLastMet)
        assertEquals(1, snap.groups.size)
        assertEquals(1, snap.plans.size)
        assertEquals("Lisbon", snap.settings.homeArea)
        assertEquals("sk-mine", snap.settings.aiApiKey)
        // Flows see the restored data straight away.
        assertEquals("Ana", to.friends.friends.first().single().name)
    }

    @Test
    fun aBackupFromBeforeTheTimeline_restoresInteractionsWithIdsAndNoJournal() = runTest {
        // Written by an older version: interactions without id, source or note, no journal, hangouts without activity.
        val json = """
            {"format":"good-company-backup","version":1,"exportedAt":"2026-03-01T10:00:00Z",
             "friends":[{"id":"ana","name":"Ana"}],
             "hangouts":[{"id":"h","friendIds":["ana"],"at":"2026-02-01T12:00:00Z"}],
             "interactions":[
               {"friendId":"ana","type":"CALL","at":"2026-02-02T12:00:00Z"},
               {"friendId":"ana","type":"WHATSAPP","at":"2026-02-03T12:00:00Z"}
             ]}
        """.trimIndent()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        val snap = to.backup.snapshot()
        assertEquals(listOf(InteractionSource.CALL_LOG, InteractionSource.CHAT_IMPORT), snap.interactions.map { it.source })
        assertTrue(snap.interactions.all { it.id.isNotBlank() })
        assertEquals(emptyList(), snap.journal)
        assertEquals(null, snap.hangouts.single().activity)
    }

    @Test
    fun aBackupFromBeforeTheFullProfile_restoresWithEmptyNewFields() = runTest {
        // Written by an older version: no about, family, taste-group, email or custom-field keys at all.
        val json = """
            {"format":"good-company-backup","version":1,"exportedAt":"2026-03-01T10:00:00Z","friends":[
              {"id":"ana","name":"Ana","likes":["jazz"],"dislikes":["cilantro"],"kids":[{"id":"k","name":"Leo"}]}
            ]}
        """.trimIndent()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        val restored = to.backup.snapshot().friends
        val ana = restored.first { it.id.value == "ana" }
        assertEquals(listOf("jazz"), ana.likes)
        assertEquals(listOf("cilantro"), ana.dislikes)
        // Kids are people now (issue #40): the old kid comes back as a person of their own, keeping their id.
        assertTrue(ana.kids.isEmpty())
        assertEquals("Leo", restored.single { it.id.value == "k" }.name)
        assertEquals("", ana.job)
        assertEquals(null, ana.met)
        assertEquals(emptyList(), ana.relatives)
        assertEquals(emptyList(), ana.emails)
        assertEquals(emptyList(), ana.food + ana.drinks + ana.interests)
        assertEquals(emptyList(), ana.customFields)
    }

    @Test
    fun aBackupFromBeforeCircles_restoresGroupsAndCloseness() = runTest {
        // Written by an older version: groups without a colour, the tier stored as "circle", no Home circle filter.
        val json = """
            {"format":"good-company-backup","version":1,"exportedAt":"2026-03-01T10:00:00Z",
             "friends":[{"id":"ana","name":"Ana","isCloseFriend":true,"circle":"INNER"},{"id":"rui","name":"Rui"}],
             "groups":[{"id":"g","name":"Crew","emoji":"🎲","memberIds":["ana","rui"],"meetEveryDays":21}],
             "settings":{"innerCircleDays":10}}
        """.trimIndent()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        val snap = to.backup.snapshot()
        assertEquals(Closeness.INNER, snap.friends.single { it.id.value == "ana" }.closeness)
        val crew = snap.groups.single()
        assertEquals(CircleColor.PURPLE, crew.color)
        assertEquals(21, crew.meetEveryDays) // an existing group keeps its rhythm
        assertEquals(listOf(FriendId("ana"), FriendId("rui")), crew.memberIds)
        assertEquals(10, snap.settings.innerCircleDays)
        assertEquals(null, snap.settings.homeCircleFilter)
    }

    @Test
    fun closenessIsStillWrittenAsCircle_andCircleColoursRoundTrip() = runTest {
        val from = Stores()
        from.friends.upsert(friend("ana", "Ana") { copy(closeness = Closeness.GOOD) })
        from.groups.upsert(FriendGroup("g", "Choir", "🎵", listOf(FriendId("ana")), color = CircleColor.MAGENTA))
        from.appSettings.update { it.copy(homeCircleFilter = "g") }
        val json = ExportBackupUseCase(from.backup, from.clock)()
        assertTrue(Regex("\"circle\":\\s*\"GOOD\"").containsMatchIn(json), json)
        assertFalse("\"closeness\"" in json)
        assertTrue(Regex("\"color\":\\s*\"MAGENTA\"").containsMatchIn(json), json)

        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        val snap = to.backup.snapshot()
        assertEquals(Closeness.GOOD, snap.friends.single().closeness)
        assertEquals(CircleColor.MAGENTA, snap.groups.single().color)
        assertEquals("g", snap.settings.homeCircleFilter)
    }

    @Test
    fun acquaintancesAndTheirRhythm_roundTripThroughABackup() = runTest {
        val from = Stores()
        from.friends.upsert(friend("ana", "Ana") { copy(closeness = Closeness.ACQUAINTANCE) })
        from.appSettings.update { it.copy(acquaintanceDays = 180) }
        val json = ExportBackupUseCase(from.backup, from.clock)()
        assertTrue(Regex("\"circle\":\\s*\"ACQUAINTANCE\"").containsMatchIn(json), json)

        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        val snap = to.backup.snapshot()
        assertEquals(Closeness.ACQUAINTANCE, snap.friends.single().closeness)
        assertFalse(snap.friends.single().isCloseFriend)
        assertEquals(180, snap.settings.acquaintanceDays)
    }

    @Test
    fun keepInTouchTracking_roundTripsThroughABackup_andOlderBackupsReadAsOn() = runTest {
        val from = Stores()
        from.friends.upsert(friend("kid", "Kid") { copy(tracksKeepInTouch = false) })
        from.friends.upsert(friend("ana", "Ana"))
        val json = ExportBackupUseCase(from.backup, from.clock)()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        val byId = to.backup.snapshot().friends.associateBy { it.id.value }
        assertFalse(byId.getValue("kid").tracksKeepInTouch)
        assertTrue(byId.getValue("ana").tracksKeepInTouch)

        val old = """{"format":"good-company-backup","version":1,"exportedAt":"2026-03-01T10:00:00Z","friends":[{"id":"ana","name":"Ana"}]}"""
        val older = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(older.backup)(old))
        assertTrue(older.backup.snapshot().friends.single().tracksKeepInTouch)
    }

    @Test
    fun socialsAndWebsites_roundTripThroughABackup() = runTest {
        val from = Stores()
        val socials = listOf(
            org.goodcompany.app.core.domain.model.SocialLink(
                org.goodcompany.app.core.domain.model.SocialNetwork.MASTODON,
                "@ana@mastodon.social",
            ),
        )
        from.friends.upsert(friend("ana", "Ana") { copy(socials = socials, websites = listOf("https://ana.pt")) })
        val json = ExportBackupUseCase(from.backup, from.clock)()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        val ana = to.backup.snapshot().friends.single()
        assertEquals(socials, ana.socials)
        assertEquals(listOf("https://ana.pt"), ana.websites)
    }

    @Test
    fun noteMedia_roundTripsThroughABackup_thumbnailsAndReferencesOnly() = runTest {
        val from = Stores()
        from.friends.upsert(friend("ana", "Ana"))
        val media = listOf(
            org.goodcompany.app.core.domain.model.NoteMedia(
                "m",
                org.goodcompany.app.core.domain.model.MediaKind.PHOTO,
                org.goodcompany.app.core.domain.model.ContactPhoto("AQID"),
                org.goodcompany.app.core.domain.model.PhotoRef(
                    org.goodcompany.app.core.domain.model.PhotoPlatform.ANDROID,
                    "content://x",
                ),
            ),
        )
        from.interactions.upsertJournal(
            org.goodcompany.app.core.domain.model.JournalEntry(
                "j",
                org.goodcompany.app.core.domain.model.FriendId("ana"),
                from.clock.now(),
                "Hike",
                media,
            ),
        )
        val json = ExportBackupUseCase(from.backup, from.clock)()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        assertEquals(media, to.backup.snapshot().journal.single().media)
    }

    @Test
    fun aBackupFromBeforeDatedNotes_turnsEachNoteIntoOneDatedNote_andBlankNotesMakeNone() = runTest {
        // Written by a build before issue #103: notes are one free-text field per person.
        val old = """
            {"format": "good-company-backup", "version": 1, "exportedAt": "2026-03-01T10:00:00Z", "friends": [
                {"id": "ana", "name": "Ana", "notes": "Partner: Tiago.\nAllergic to peanuts."},
                {"id": "bob", "name": "Bob", "notes": "  \n "},
                {"id": "cat", "name": "Cat"}
            ], "journal": [{"id": "j1", "friendId": "cat", "at": "2026-02-01T10:00:00Z", "text": "New job"}]}
        """.trimIndent()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup, to.clock)(old))
        val journal = to.backup.snapshot().journal
        assertEquals(setOf("note-ana", "j1"), journal.mapTo(HashSet()) { it.id })
        val note = journal.single { it.id == "note-ana" }
        assertEquals("Partner: Tiago.\nAllergic to peanuts.", note.text)
        assertEquals(kotlin.time.Instant.parse("2026-03-01T10:00:00Z"), note.at) // dated when the backup was made

        // A backup from this build says its notes are already dated: restoring it adds nothing.
        val json = ExportBackupUseCase(to.backup, to.clock)()
        assertTrue("\"notesInJournal\": true" in json, json)
        val again = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(again.backup, again.clock)(json))
        assertEquals(journal.sortedBy { it.id }, again.backup.snapshot().journal.sortedBy { it.id })
    }

    @Test
    fun aBackupFromBeforeEveryoneHadACloseness_getsTheMigrationRule_andExportsStillCarryTheHeart() = runTest {
        // Written by a build before issue #86: a level is optional and "isCloseFriend" is the heart.
        val old = """
            {"format": "good-company-backup", "version": 1, "friends": [
                {"id": "ana", "name": "Ana", "isCloseFriend": true},
                {"id": "bob", "name": "Bob"},
                {"id": "cat", "name": "Cat", "circle": "INNER"},
                {"id": "dan", "name": "Dan", "circle": "GOOD", "isCloseFriend": true},
                {"id": "eve", "name": "Eve", "circle": "BEST", "isCloseFriend": true}
            ]}
        """.trimIndent()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(old))
        val levels = to.backup.snapshot().friends.associate { it.id.value to it.closeness }
        assertEquals(
            mapOf(
                "ana" to Closeness.CLOSE,
                "bob" to Closeness.ACQUAINTANCE,
                "cat" to Closeness.ACQUAINTANCE,
                "dan" to Closeness.GOOD,
                "eve" to Closeness.CLOSE, // a level this build doesn't know
            ),
            levels,
        )

        val json = ExportBackupUseCase(to.backup, to.clock)()
        assertTrue(Regex("\"id\":\\s*\"ana\"[^}]*\"circle\":\\s*\"CLOSE\"[^}]*\"isCloseFriend\":\\s*true").containsMatchIn(json), json)
        assertTrue(Regex("\"id\":\\s*\"bob\"[^}]*\"isCloseFriend\":\\s*false").containsMatchIn(json), json)
    }

    @Test
    fun aBackupWithTheOldCallsCountSwitch_becomesAnyContact_andExportsStillCarryTheSwitch() = runTest {
        // Decision 2026-11-21 ("Catch-up · what counts"): the switch on is Any contact, off the default.
        val old = """
            {"format": "good-company-backup", "version": 1, "friends": [
                {"id": "ana", "name": "Ana", "contactCountsAsCatchUp": true},
                {"id": "bob", "name": "Bob", "contactCountsAsCatchUp": false},
                {"id": "cat", "name": "Cat", "catchUpCounts": "IN_PERSON_ONLY"}
            ]}
        """.trimIndent()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(old))
        val counts = to.backup.snapshot().friends.associate { it.id.value to it.catchUpCounts }
        assertEquals(
            mapOf("ana" to CatchUpCounts.ANY_CONTACT, "bob" to CatchUpCounts.DEFAULT, "cat" to CatchUpCounts.IN_PERSON_ONLY),
            counts,
        )
        val json = ExportBackupUseCase(to.backup, to.clock)()
        assertTrue(Regex("\"id\":\\s*\"ana\"[^}]*\"contactCountsAsCatchUp\":\\s*true").containsMatchIn(json), json)
        assertTrue(Regex("\"id\":\\s*\"cat\"[^}]*\"contactCountsAsCatchUp\":\\s*false").containsMatchIn(json), json)

        val again = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(again.backup)(json))
        assertEquals(counts, again.backup.snapshot().friends.associate { it.id.value to it.catchUpCounts })
    }

    @Test
    fun attachmentsRoundTripWithThumbnailAndReference_andOlderBackupsHaveNone() = runTest {
        val from = Stores()
        from.friends.upsert(friend("ana", "Ana") { copy(gifts = listOf(Gift("g1", "Lego", url = "https://shop.example/l", price = "€35"))) })
        val photo = Attachment(
            id = "a1",
            friendId = FriendId("ana"),
            kind = AttachmentKind.PHOTO,
            photoRef = PhotoRef(PhotoPlatform.IOS, "ABC/L0/001"),
            thumbnail = ContactPhoto("AQID"),
            caption = "Lunch",
            at = at(2026, 3, 2),
            countsAsInteraction = true,
            interactionType = InteractionType.MEETUP,
            logId = "h1",
        )
        from.interactions.upsertAttachment(photo)
        val json = ExportBackupUseCase(from.backup, from.clock)()
        assertTrue("\"thumbnail\"" in json && "ABC/L0/001" in json, json)

        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        val snap = to.backup.snapshot()
        assertEquals(listOf(photo), snap.attachments)
        assertEquals("€35", snap.friends.single().gifts.single().price)

        val old = """{"format":"good-company-backup","version":1,"friends":[{"id":"ana","name":"Ana","gifts":[{"id":"g","title":"Book"}]}]}"""
        val again = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(again.backup)(old))
        assertEquals(emptyList(), again.backup.snapshot().attachments)
        assertEquals(null, again.backup.snapshot().friends.single().gifts.single().url)
    }

    @Test
    fun reminderFieldsRoundTripInABackupFile_andOlderBackupsReadAsPlainOnes() = runTest {
        // Issue #99: snooze, repeat, important and the done time are in the backup; older files restore without them.
        val from = Stores()
        val reminder = Reminder(
            "r",
            "Water plants",
            dueAt = at(2026, 3, 20, 18),
            createdAt = at(2026, 3, 1),
            snoozedUntil = at(2026, 3, 21, 9),
            important = true,
            repeat = ReminderRepeat.MONTHLY,
            completedAt = null,
        )
        from.reminders.upsert(reminder)
        val json = ExportBackupUseCase(from.backup, from.clock)()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        assertEquals(listOf(reminder), to.backup.snapshot().reminders)

        val oldReminder = """{"id":"r","title":"Call","createdAt":"2026-03-01T12:00:00Z","done":true}"""
        val old = """{"format":"good-company-backup","version":1,"reminders":[$oldReminder]}"""
        val again = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(again.backup)(old))
        val restored = again.backup.snapshot().reminders.single()
        assertEquals(ReminderRepeat.NONE, restored.repeat)
        assertEquals(false, restored.important)
        assertEquals(null, restored.snoozedUntil)
        assertEquals(null, restored.completedAt)
        assertTrue(restored.done)
    }

    @Test
    fun aPlansTime_roundTripsThroughABackupFile_andOlderBackupsHaveNoTime() = runTest {
        // Issue #167.
        val from = Stores()
        val plan = Plan("p", listOf(FriendId("ana")), "Dinner", LocalDate(2026, 3, 12), createdAt = at(2026, 3, 1), time = LocalTime(19, 30))
        from.plans.upsert(plan)
        val json = ExportBackupUseCase(from.backup, from.clock)()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        assertEquals(listOf(plan), to.backup.snapshot().plans)

        val oldPlan = """{"id":"p","friendIds":["ana"],"title":"Lunch","date":"2026-03-12","createdAt":"2026-03-01T12:00:00Z"}"""
        val old = """{"format":"good-company-backup","version":1,"plans":[$oldPlan]}"""
        val again = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(again.backup)(old))
        assertEquals(null, again.backup.snapshot().plans.single().time)
    }

    @Test
    fun aPlansMapLinkAndPoint_roundTripThroughABackupFile() = runTest {
        // Issue #170. Older backups have neither (see the time test above: no field, no value).
        val from = Stores()
        val plan = Plan(
            "p",
            listOf(FriendId("ana")),
            "Dinner",
            LocalDate(2026, 3, 12),
            place = "Ramiro",
            createdAt = at(2026, 3, 1),
            mapLink = "https://maps.apple.com/?ll=38.72,-9.13",
            latitude = 38.72,
            longitude = -9.13,
        )
        from.plans.upsert(plan)
        val json = ExportBackupUseCase(from.backup, from.clock)()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        assertEquals(listOf(plan), to.backup.snapshot().plans)
    }
}
