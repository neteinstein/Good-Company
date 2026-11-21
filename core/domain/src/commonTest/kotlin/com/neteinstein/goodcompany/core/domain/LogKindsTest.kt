package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.LogKindOption
import com.neteinstein.goodcompany.core.domain.usecase.ObserveLogKindsUseCase
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Issue #151: the Log popup's "How?" options, most used first. */
class LogKindsTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")

    @Test
    fun nothingLogged_keepsTheDefaultOrder_withEveryKind() = runTest {
        val g = TestGraph(listOf(friend("ana", "Ana")))
        val kinds = g.observeLogKinds().first().map { it.kind }
        assertEquals(ObserveLogKindsUseCase.DEFAULT_ORDER, kinds)
        assertEquals(LogKind.entries.toSet(), kinds.toSet())
        assertEquals(listOf(LogKind.MEETUP, LogKind.CALL, LogKind.VIDEO_CALL, LogKind.MESSAGE), kinds.take(ObserveLogKindsUseCase.SHOWN))
    }

    @Test
    fun theMostUsedComeFirst_tiesKeepTheConfiguredOrder() {
        val interactions = listOf(
            Interaction(ana, InteractionType.EMAIL, at(2026, 3, 1), id = "e1"),
            Interaction(ana, InteractionType.EMAIL, at(2026, 3, 2), id = "e2"),
            Interaction(ana, InteractionType.GAME, at(2026, 3, 3), id = "g1"),
            // WhatsApp counts as "said hi".
            Interaction(ana, InteractionType.WHATSAPP, at(2026, 3, 4), id = "w1"),
        )
        val journal = listOf(JournalEntry("j1", ana, at(2026, 3, 5), "Moved"))
        val usage = ObserveLogKindsUseCase.usage(emptyList(), interactions, journal)
        val ranked = ObserveLogKindsUseCase.ranked(usage).map { it.kind }
        assertEquals(listOf(LogKind.EMAIL, LogKind.MESSAGE, LogKind.GAME, LogKind.JOURNAL, LogKind.MEETUP), ranked.take(5))
    }

    @Test
    fun automaticRowsDontCount_andABatchCountsOnce() {
        val interactions = listOf(
            Interaction(ana, InteractionType.CALL, at(2026, 3, 1), id = "c1", source = InteractionSource.CALL_LOG),
            Interaction(ana, InteractionType.CALL, at(2026, 3, 2), id = "c2", source = InteractionSource.CALL_LOG),
            Interaction(ana, InteractionType.LONG_CHAT, at(2026, 3, 3), id = "l1", batchId = "b"),
            Interaction(rui, InteractionType.LONG_CHAT, at(2026, 3, 3), id = "l2", batchId = "b"),
        )
        val hangouts = listOf(Hangout("h1", listOf(ana, rui), at(2026, 3, 9)))
        val usage = ObserveLogKindsUseCase.usage(hangouts, interactions, emptyList())
        assertEquals(mapOf(LogKind.MEETUP to 1, LogKind.LONG_CHAT to 1), usage)
    }

    @Test
    fun aConfiguredList_isRankedAsItIs() {
        // What the configurable How list (issue #152) will hand in: only some kinds, its own order, a name of your own.
        val configured = listOf(LogKindOption(LogKind.CALL), LogKindOption(LogKind.OTHER, "Postcard"), LogKindOption(LogKind.CALL))
        val ranked = ObserveLogKindsUseCase.ranked(mapOf(LogKind.OTHER to 2, LogKind.MEETUP to 9), configured)
        assertEquals(listOf(LogKindOption(LogKind.OTHER, "Postcard"), LogKindOption(LogKind.CALL)), ranked)
    }
}
