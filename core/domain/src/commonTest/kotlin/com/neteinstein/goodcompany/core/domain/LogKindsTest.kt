package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.JournalEntry
import com.neteinstein.goodcompany.core.domain.usecase.ActivityNameProblem
import com.neteinstein.goodcompany.core.domain.usecase.LogInteractionUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.LogKindOption
import com.neteinstein.goodcompany.core.domain.usecase.LogRequest
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
        assertEquals(mapOf(LogKindOption(LogKind.MEETUP) to 1, LogKindOption(LogKind.LONG_CHAT) to 1), usage)
    }

    @Test
    fun aConfiguredList_isRankedAsItIs() {
        // Only some kinds, their own order, one of your own; repeats are dropped.
        val configured = listOf(LogKindOption(LogKind.CALL), LogKindOption.custom("Postcard"), LogKindOption(LogKind.CALL))
        val ranked = ObserveLogKindsUseCase.ranked(mapOf(LogKindOption.custom("Postcard") to 2, LogKindOption(LogKind.MEETUP) to 9), configured)
        assertEquals(listOf(LogKindOption.custom("Postcard"), LogKindOption(LogKind.CALL)), ranked)
    }

    @Test
    fun anOtherLabelledWithYourOwnOption_countsForIt_notForOther() {
        val interactions = listOf(
            Interaction(ana, InteractionType.OTHER, at(2026, 3, 1), id = "o1", label = "voice NOTE"),
            Interaction(ana, InteractionType.OTHER, at(2026, 3, 2), id = "o2", label = "Voice note"),
            Interaction(ana, InteractionType.OTHER, at(2026, 3, 3), id = "o3", label = "Sent a postcard"),
            Interaction(ana, InteractionType.OTHER, at(2026, 3, 4), id = "o4"),
        )
        val usage = ObserveLogKindsUseCase.usage(emptyList(), interactions, emptyList(), custom = listOf("Voice note"))
        assertEquals(mapOf(LogKindOption.custom("Voice note") to 2, LogKindOption(LogKind.OTHER) to 2), usage)
    }

    @Test
    fun settingsDecideWhatShows_andTheTiebreak() = runTest {
        // Issue #152: the built-in ones turned on, in your order, then your own; the most used still first.
        val g = TestGraph(listOf(friend("ana", "Ana")))
        g.setLogKinds(listOf(LogKind.EMAIL, LogKind.MEETUP))
        g.customLogKinds.add("Letter")
        g.customLogKinds.add("Voice note")
        assertEquals(
            listOf(LogKindOption(LogKind.EMAIL), LogKindOption(LogKind.MEETUP), LogKindOption.custom("Letter"), LogKindOption.custom("Voice note")),
            g.observeLogKinds().first(),
        )
        g.interactionRepo.addAll(listOf(Interaction(ana, InteractionType.OTHER, at(2026, 3, 1), id = "v", label = "Voice note")))
        assertEquals(LogKindOption.custom("Voice note"), g.observeLogKinds().first().first())
    }

    @Test
    fun setLogKinds_neverLeavesNone() = runTest {
        val g = TestGraph(listOf(friend("ana", "Ana")))
        g.setLogKinds(listOf(LogKind.CALL, LogKind.CALL))
        g.setLogKinds(emptyList())
        assertEquals(listOf(LogKind.CALL), g.settingsRepo.settings.first().logKinds)
    }

    @Test
    fun yourOwnHowOptions_addRenameMoveRemoveAndRestore() = runTest {
        val g = TestGraph(listOf(friend("ana", "Ana")))
        val own = g.customLogKinds
        assertEquals(null, own.add(" Letter "))
        assertEquals(ActivityNameProblem.TAKEN, own.add("letter"))
        assertEquals(ActivityNameProblem.BLANK, own.add("  "))
        assertEquals(null, own.add("x".repeat(60)))
        assertEquals(listOf("Letter", "x".repeat(LogInteractionUseCase.LABEL_MAX)), g.settingsRepo.settings.first().customLogKinds)
        assertEquals(null, own.rename("Letter", "Postcard"))
        own.move(0, 1)
        assertEquals(listOf("x".repeat(LogInteractionUseCase.LABEL_MAX), "Postcard"), g.settingsRepo.settings.first().customLogKinds)
        val at = own.remove("Postcard")
        assertEquals(1, at)
        own.restore("Postcard", at)
        assertEquals(listOf("x".repeat(LogInteractionUseCase.LABEL_MAX), "Postcard"), g.settingsRepo.settings.first().customLogKinds)
        // Activities are a separate list.
        assertEquals(emptyList(), g.settingsRepo.settings.first().customActivities)
    }

    @Test
    fun loggingYourOwnHowOption_isAnOtherWithItsName() = runTest {
        val g = TestGraph(listOf(friend("ana", "Ana")))
        g.logInteraction(LogRequest(listOf(ana), LogKind.OTHER, label = "Voice note"))
        val row = g.interactionRepo.interactions.first().single()
        assertEquals(InteractionType.OTHER, row.type)
        assertEquals("Voice note", row.label)
    }
}
