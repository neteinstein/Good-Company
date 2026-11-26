package org.goodcompany.app.core.data

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.goodcompany.app.core.data.repository.FriendRepositoryImpl
import org.goodcompany.app.core.data.repository.GroupRepositoryImpl
import org.goodcompany.app.core.data.repository.IdeaRepositoryImpl
import org.goodcompany.app.core.data.repository.InteractionRepositoryImpl
import org.goodcompany.app.core.data.repository.SettingsRepositoryImpl
import org.goodcompany.app.core.data.source.CommunicationLogSource
import org.goodcompany.app.core.data.source.ContactsSource
import org.goodcompany.app.core.data.source.LogEntry
import org.goodcompany.app.core.data.storage.PersistentState
import org.goodcompany.app.core.domain.model.ContactRecord
import org.goodcompany.app.core.domain.model.ConversationMessage
import org.goodcompany.app.core.domain.model.FriendGroup
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.Idea
import org.goodcompany.app.core.domain.model.IdeaKind
import org.goodcompany.app.core.domain.model.InteractionType
import org.goodcompany.app.core.domain.model.ThemeMode
import org.goodcompany.app.core.testing.FixedClock
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class RepositoriesTest {
    private val settings = MapSettings()

    private class StubContacts(var records: List<ContactRecord>, override val importsNewContacts: Boolean = true) : ContactsSource {
        override suspend fun readContacts(withPhotos: Boolean) = records
    }

    @Test
    fun persistentState_survivesReloadAndIgnoresCorruptData() = runTest {
        val state = PersistentState(settings, "k", ListSerializer(String.serializer()), emptyList())
        state.update { it + "a" }
        val reloaded = PersistentState(settings, "k", ListSerializer(String.serializer()), emptyList())
        assertEquals(listOf("a"), reloaded.value)

        settings.putString("bad", "{not json")
        assertEquals(listOf("fallback"), PersistentState(settings, "bad", ListSerializer(String.serializer()), listOf("fallback")).value)
    }

    @Test
    fun friendRepository_persistsEditsAcrossInstances() = runTest {
        val repo = FriendRepositoryImpl(settings, StubContacts(emptyList()))
        repo.upsert(friend("ana") { copy(likes = listOf("jazz")) })
        repo.upsert(friend("ana") { copy(likes = listOf("jazz", "sushi")) })
        val again = FriendRepositoryImpl(settings, StubContacts(emptyList()))
        assertEquals(listOf("jazz", "sushi"), again.friends.first().single().likes)
        again.delete(FriendId("ana"))
        assertTrue(again.friends.first().isEmpty())
    }

    @Test
    fun friendRepository_emptyContactReadNeverWipesFriends() = runTest {
        val contacts = StubContacts(listOf(ContactRecord("1", "Ana", emptyList(), null)))
        val repo = FriendRepositoryImpl(settings, contacts)
        assertEquals(1, repo.syncContacts())
        contacts.records = emptyList()
        assertEquals(0, repo.syncContacts())
        assertEquals(1, repo.friends.first().size)
    }

    @Test
    fun friendRepository_pickerOnlySourceNeverImportsTheWholeAddressBook() = runTest {
        val contacts = StubContacts(
            listOf(ContactRecord("1", "Ana", emptyList(), null), ContactRecord("2", "Bruno", emptyList(), null)),
            importsNewContacts = false,
        )
        val repo = FriendRepositoryImpl(settings, contacts)
        repo.syncContacts()
        assertTrue(repo.friends.first().isEmpty())

        repo.upsert(friend("contact-1") { copy(contactId = "1", name = "Old") })
        repo.syncContacts()
        assertEquals(listOf("Ana"), repo.friends.first().map { it.name })
    }

    @Test
    fun interactions_matchCallLogByPhoneNumberAndTrimMessages() = runTest {
        val logs = object : CommunicationLogSource {
            override suspend fun read(since: Instant) = listOf(
                LogEntry("+351 912 345 678", InteractionType.CALL, at(2026, 3, 9)),
                LogEntry("000", InteractionType.CALL, at(2026, 3, 9)),
            )
        }
        val repo = InteractionRepositoryImpl(settings, logs, FixedClock())
        val ana = friend("ana") { copy(phoneNumbers = listOf("912345678")) }
        assertEquals(1, repo.syncCommunicationLogs(listOf(ana)))
        assertEquals(FriendId("ana"), repo.interactions.first().single().friendId)

        repo.addMessages((1..80).map { ConversationMessage(FriendId("ana"), Instant.fromEpochSeconds(it.toLong()), false, "m$it") })
        val kept = repo.messages.first()
        assertEquals(60, kept.size)
        assertEquals("m80", kept.last().text)
    }

    @Test
    fun ideas_dismissAndSettingsUpdate() = runTest {
        val ideas = IdeaRepositoryImpl(settings)
        ideas.addAll(listOf(Idea("i", listOf(FriendId("ana")), IdeaKind.EVENT, "Jazz", "", createdAt = at(2026, 1, 1))))
        ideas.dismiss("i")
        assertTrue(ideas.ideas.first().single().dismissed)

        val s = SettingsRepositoryImpl(settings)
        s.update { it.copy(themeMode = ThemeMode.DARK, aiApiKey = "k") }
        assertEquals(ThemeMode.DARK, SettingsRepositoryImpl(settings).settings.first().themeMode)
    }

    @Test
    fun groupsPersistAndIdeasCanBeMarkedProposed() = runTest {
        val repo = GroupRepositoryImpl(settings)
        repo.upsert(FriendGroup("g", "Crew", "🎲", listOf(FriendId("ana"), FriendId("rui")), 30))
        repo.upsert(FriendGroup("g", "Crew 2", "🎲", listOf(FriendId("ana"), FriendId("rui")), 30))
        assertEquals("Crew 2", GroupRepositoryImpl(settings).groups.first().single().name)
        repo.delete("g")
        assertTrue(repo.groups.first().isEmpty())

        val ideas = IdeaRepositoryImpl(settings)
        ideas.addAll(listOf(Idea("i", listOf(FriendId("ana")), IdeaKind.EVENT, "Jazz", "", createdAt = at(2026, 1, 1))))
        ideas.markProposed("i", at(2026, 1, 2))
        assertEquals(at(2026, 1, 2), IdeaRepositoryImpl(settings).ideas.first().single().proposedAt)
    }
}
