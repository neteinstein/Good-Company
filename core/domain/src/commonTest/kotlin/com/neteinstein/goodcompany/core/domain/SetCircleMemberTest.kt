package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.usecase.FamilyCircle
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetCircleMemberTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val surf = FriendGroup("surf", "Surf", memberIds = listOf(ana), meetEveryDays = 30)

    private fun graph() = TestGraph(listOf(friend("ana"), friend("rui") { copy(relationToMe = LinkType.SIBLING) }))

    @Test
    fun addsAndRemoves_returningTheCircleAsItWas() = runTest {
        val g = graph()
        g.groupRepo.upsert(surf)
        assertEquals(surf, g.setCircleMember("surf", rui, member = true))
        assertEquals(listOf(ana, rui), g.groupRepo.groups.value.single().memberIds)
        val before = g.setCircleMember("surf", ana, member = false)
        assertEquals(listOf(ana, rui), before?.memberIds)
        assertEquals(listOf(rui), g.groupRepo.groups.value.single().memberIds)
        // Undo puts it back as it was, order included.
        g.restoreGroup(before!!)
        assertEquals(listOf(ana, rui), g.groupRepo.groups.value.single().memberIds)
    }

    @Test
    fun nothingToChange_returnsNull() = runTest {
        val g = graph()
        g.groupRepo.upsert(surf)
        assertNull(g.setCircleMember("surf", ana, member = true)) // already in
        assertNull(g.setCircleMember("surf", rui, member = false)) // already out
        assertNull(g.setCircleMember("gone", ana, member = true))
        assertEquals(surf, g.groupRepo.groups.value.single())
    }

    @Test
    fun theFamilyCircleFollowsRelations_andIsNeverWritten() = runTest {
        val g = graph()
        assertEquals(listOf(rui), g.circles.groups.first().single { it.id == FamilyCircle.ID }.memberIds)
        assertNull(g.setCircleMember(FamilyCircle.ID, ana, member = true))
        assertNull(g.setCircleMember(FamilyCircle.ID, rui, member = false))
        assertEquals(emptyList(), g.groupRepo.groups.value)
    }

    @Test
    fun takingOutTheLastMember_keepsTheCircle() = runTest {
        val g = graph()
        g.groupRepo.upsert(surf)
        g.setCircleMember("surf", ana, member = false)
        val kept = g.groupRepo.groups.value.single()
        assertEquals(emptyList(), kept.memberIds)
        assertEquals(30, kept.meetEveryDays)
    }
}
