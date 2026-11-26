package org.goodcompany.app.core.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.goodcompany.app.core.domain.model.ContactPhoto
import org.goodcompany.app.core.domain.model.ContactRecord
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.usecase.RestoreResult
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The background image of a person's page (issue #144): set, replace, remove, Undo, shrink, backups. */
class PersonBackgroundTest {
    private val ana = FriendId("ana")

    @Test
    fun setReplaceRemoveAndRestore() = runTest {
        val g = TestGraph(listOf(friend("ana"), friend("rui")))
        assertNull(g.personBackgrounds.observe(ana).first())
        assertNull(g.personBackgrounds.set(ana, byteArrayOf(1, 2, 3)).getOrThrow())
        val first = g.personBackgrounds.observe(ana).first()
        assertEquals(ContactPhoto.cover(byteArrayOf(1, 2, 3)), first)
        assertEquals(first, g.personBackgrounds.set(ana, byteArrayOf(4, 5)).getOrThrow())
        assertNull(g.personBackgrounds.observe(FriendId("rui")).first()) // one person's background only

        val removed = g.personBackgrounds.remove(ana)
        assertEquals(ContactPhoto.cover(byteArrayOf(4, 5)), removed)
        assertNull(g.personBackgrounds.observe(ana).first())
        assertNull(g.personBackgrounds.remove(ana))
        g.personBackgrounds.restore(ana, removed)
        assertEquals(removed, g.personBackgrounds.observe(ana).first())
    }

    @Test
    fun anUnreadablePictureChangesNothing() = runTest {
        val g = TestGraph(listOf(friend("ana")))
        g.personBackgrounds.set(ana, byteArrayOf(7))
        assertTrue(g.personBackgrounds.set(ana, null).isFailure)
        assertTrue(g.personBackgrounds.set(ana, ByteArray(0)).isFailure)
        assertEquals(ContactPhoto.cover(byteArrayOf(7)), g.personBackgrounds.observe(ana).first())
    }

    @Test
    fun aLargePictureIsShrunkLikeACircleCover() = runTest {
        val g = TestGraph(listOf(friend("ana")))
        val large = ByteArray(ContactPhoto.COVER_MAX_BYTES + 1) { 3 }
        g.personBackgrounds.set(ana, large).getOrThrow()
        assertEquals(16, g.personBackgrounds.observe(ana).first()!!.bytes().size) // TestGraph's shrinker keeps 16 bytes
    }

    @Test
    fun aContactSyncOrAnEditLeavesItAlone_andDeletingThePersonTakesIt() = runTest {
        val g = TestGraph(listOf(friend("ana") { copy(contactId = "c1") }))
        g.friendRepo.contacts = listOf(ContactRecord("c1", "Ana", emptyList(), null, photo = ContactPhoto("AQID")))
        g.personBackgrounds.set(ana, byteArrayOf(9, 9))
        g.updateFriend(ana) { it.copy(name = "Ana Maria") }
        g.syncContacts()
        assertEquals(ContactPhoto("AQID"), g.friendRepo.get(ana)!!.photo) // the sync did run
        assertNotNull(g.personBackgrounds.observe(ana).first())
        g.deleteFriend(ana)
        assertNull(g.personBackgrounds.observe(ana).first())
        assertTrue(g.backgroundRepo.backgrounds.first().isEmpty())
    }

    @Test
    fun aBackgroundTravelsInABackupFile_andOlderFilesHaveNone() = runTest {
        val from = TestGraph(listOf(friend("ana"), friend("rui")))
        from.personBackgrounds.set(ana, byteArrayOf(3, 1, 4))
        val json = from.exportBackup()

        val to = TestGraph()
        assertIs<RestoreResult.Restored>(to.restoreBackup(json))
        assertEquals(mapOf(ana to ContactPhoto.cover(byteArrayOf(3, 1, 4))!!), to.backgroundRepo.backgrounds.first())

        val old = """{"format":"good-company-backup","version":1,"friends":[{"id":"ana","name":"Ana"}]}"""
        val again = TestGraph()
        assertIs<RestoreResult.Restored>(again.restoreBackup(old))
        assertTrue(again.backupRepo.restored!!.personBackgrounds.isEmpty())
        assertTrue(again.backgroundRepo.backgrounds.first().isEmpty())
    }
}
