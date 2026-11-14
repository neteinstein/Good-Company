package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.usecase.AppLink
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.QuickEntry
import com.neteinstein.goodcompany.core.domain.usecase.QuickEntryInbox
import com.neteinstein.goodcompany.core.domain.usecase.Shortcuts
import com.neteinstein.goodcompany.core.domain.usecase.toQuickEntry
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutsTest {
    private val ana = friend("ana", "Ana Silva")
    private val bea = friend("bea", "Bea Costa")
    private val rui = friend("rui", "Rui Costa")
    private val ze = friend("ze", "Zé Pinto")
    private val gone = friend("gone", "Gone Away") { copy(isOrphaned = true) }
    private val never = friend("never", "Never Met")

    private fun met(id: String, vararg who: String, day: Int) = Hangout(id, who.map(::FriendId), at(2026, 3, day))

    @Test
    fun threeMostRecentByLastContact_tiesByName_orphansAndStrangersLeftOut() {
        val hangouts = listOf(
            met("h1", "ana", day = 1),
            met("h2", "rui", "bea", day = 5), // same moment: Bea before Rui
            met("h3", "gone", day = 9),
        )
        val interactions = listOf(Interaction(FriendId("ze"), InteractionType.CALL, at(2026, 3, 7)))
        val snapshot = Shortcuts.snapshot(listOf(ana, bea, rui, ze, gone, never), hangouts, interactions, hideNames = false)

        assertEquals(listOf("ze", "bea", "rui"), snapshot.people.map { it.id.value })
        assertEquals("ZP", snapshot.people.first().initials)
        assertEquals("Bea", snapshot.people[1].firstName)
        assertEquals(false, snapshot.hideNames)
    }

    @Test
    fun hiddenNamesMeanNoPeople() {
        val snapshot = Shortcuts.snapshot(listOf(ana), listOf(met("h", "ana", day = 1)), emptyList(), hideNames = true)
        assertTrue(snapshot.hideNames)
        assertTrue(snapshot.people.isEmpty())
    }

    @Test
    fun useCaseFollowsActivityAndPrivacySettings() = runTest {
        val g = TestGraph(friends = listOf(ana, bea), hangouts = listOf(met("h", "ana", day = 1)))
        assertEquals(listOf("ana"), g.observeShortcuts().first().people.map { it.id.value })

        g.interactionRepo.addAll(listOf(Interaction(FriendId("bea"), InteractionType.MESSAGE, at(2026, 3, 2))))
        assertEquals(listOf("bea", "ana"), g.observeShortcuts().first().people.map { it.id.value })

        g.settingsRepo.update { it.copy(appLock = true) }
        assertTrue(g.observeShortcuts().first().hideNames)
        g.settingsRepo.update { AppSettings(privateNotifications = true) }
        assertTrue(g.observeShortcuts().first().people.isEmpty())
        g.settingsRepo.update { AppSettings() }
        assertEquals(2, g.observeShortcuts().first().people.size)
    }

    @Test
    fun parsesAppLinks() {
        assertEquals(AppLink.Log(null), AppLink.parse("goodcompany://log"))
        assertEquals(AppLink.Log(null), AppLink.parse("goodcompany://log/"))
        assertEquals(AppLink.Log(FriendId("ana")), AppLink.parse("goodcompany://log/ana"))
        assertEquals(AppLink.Log(FriendId("ana")), AppLink.parse("goodcompany://log/ana?x=1"))
        assertEquals(AppLink.Met(FriendId("ana")), AppLink.parse("goodcompany://met/ana"))
        assertNull(AppLink.parse("goodcompany://met"))
        assertNull(AppLink.parse("goodcompany://other/ana"))
        assertNull(AppLink.parse("https://log/ana"))
        assertEquals("goodcompany://log", AppLink.log())
        assertEquals("goodcompany://log/ana", AppLink.log(FriendId("ana")))
        assertEquals(AppLink.Log(FriendId("ana")), AppLink.parse(AppLink.log(FriendId("ana"))))
        assertEquals(QuickEntry(listOf(FriendId("ana")), LogKind.MEETUP), AppLink.Log(FriendId("ana")).toQuickEntry())
        assertEquals(QuickEntry(), AppLink.Log(null).toQuickEntry())
    }

    @Test
    fun inboxHoldsTheLatestEntryUntilCleared() {
        val inbox = QuickEntryInbox()
        assertNull(inbox.pending.value)
        inbox.offer(QuickEntry())
        inbox.offer(QuickEntry(listOf(FriendId("ana"))))
        assertEquals(QuickEntry(listOf(FriendId("ana"))), inbox.pending.value)
        inbox.clear()
        assertNull(inbox.pending.value)
    }
}
