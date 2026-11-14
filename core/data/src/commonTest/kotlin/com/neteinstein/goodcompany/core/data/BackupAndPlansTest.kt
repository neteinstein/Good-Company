package com.neteinstein.goodcompany.core.data

import com.neteinstein.goodcompany.core.data.repository.BackupRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.FriendRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.GroupRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.HangoutRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.IdeaRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.InteractionRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.PlanRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.ReminderRepositoryImpl
import com.neteinstein.goodcompany.core.data.repository.SettingsRepositoryImpl
import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.ContactsSource
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.PhotoPlatform
import com.neteinstein.goodcompany.core.domain.model.PhotoRef
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.usecase.ExportBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreResult
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
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
        from.groups.upsert(FriendGroup("g", "Crew", memberIds = listOf(ana)))
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
}
