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
import com.neteinstein.goodcompany.core.domain.model.Backup
import com.neteinstein.goodcompany.core.domain.model.ConversationMessage
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.Occasion
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
        assertEquals(1, snap.hangouts.size)
        assertEquals(1, snap.reminders.size)
        assertEquals(1, snap.interactions.size)
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
    fun aBackupFromBeforeTheFullProfile_restoresWithEmptyNewFields() = runTest {
        // Written by an older version: no about, family, taste-group, email or custom-field keys at all.
        val json = """
            {"format":"good-company-backup","version":1,"exportedAt":"2026-03-01T10:00:00Z","friends":[
              {"id":"ana","name":"Ana","likes":["jazz"],"dislikes":["cilantro"],"kids":[{"id":"k","name":"Leo"}]}
            ]}
        """.trimIndent()
        val to = Stores()
        assertIs<RestoreResult.Restored>(RestoreBackupUseCase(to.backup)(json))
        val ana = to.backup.snapshot().friends.single()
        assertEquals(listOf("jazz"), ana.likes)
        assertEquals(listOf("cilantro"), ana.dislikes)
        assertEquals(listOf("Leo"), ana.kids.map { it.name })
        assertEquals("", ana.job)
        assertEquals(null, ana.met)
        assertEquals(emptyList(), ana.relatives)
        assertEquals(emptyList(), ana.emails)
        assertEquals(emptyList(), ana.food + ana.drinks + ana.interests)
        assertEquals(emptyList(), ana.customFields)
    }
}
