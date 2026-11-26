package org.goodcompany.app.core.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.goodcompany.app.core.domain.model.CatchUpCounts
import org.goodcompany.app.core.domain.model.Interaction
import org.goodcompany.app.core.domain.model.InteractionSource
import org.goodcompany.app.core.domain.model.InteractionType
import org.goodcompany.app.core.domain.usecase.CadenceRules
import org.goodcompany.app.core.domain.usecase.CatchUpPolicy
import org.goodcompany.app.core.domain.usecase.LogKind
import org.goodcompany.app.core.domain.usecase.LogKindOption
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Decision 2026-11-21 "Catch-up · what counts". */
class CatchUpPolicyTest {
    private val ana = friend("ana")
    private fun contact(type: InteractionType, label: String = "", source: InteractionSource = InteractionSource.MANUAL, seconds: Int? = null) =
        Interaction(ana.id, type, at(2026, 3, 1), label = label, source = source, durationSeconds = seconds)

    @Test
    fun byDefault_callsVideoLongChatsAndGamesCount_saidHiEmailAndOtherDont() {
        val policy = CatchUpPolicy()
        val counting = InteractionType.entries.filter { policy.counts(ana, contact(it)) }.toSet()
        assertEquals(
            setOf(InteractionType.MEETUP, InteractionType.CALL, InteractionType.VIDEO_CALL, InteractionType.LONG_CHAT, InteractionType.GAME),
            counting,
        )
        // A WhatsApp day is "said hi".
        assertFalse(policy.counts(ana, contact(InteractionType.WHATSAPP)))
    }

    @Test
    fun aCallFromTheCallLog_countsOnlyFromFiveMinutes() {
        val policy = CatchUpPolicy()
        assertFalse(policy.counts(ana, contact(InteractionType.CALL, source = InteractionSource.CALL_LOG, seconds = 0)))
        assertFalse(policy.counts(ana, contact(InteractionType.CALL, source = InteractionSource.CALL_LOG, seconds = 299)))
        assertFalse(policy.counts(ana, contact(InteractionType.CALL, source = InteractionSource.CALL_LOG)))
        assertTrue(policy.counts(ana, contact(InteractionType.CALL, source = InteractionSource.CALL_LOG, seconds = 300)))
        // Logged by hand, no length needed; Any contact keeps the old rule (every call).
        assertTrue(policy.counts(ana, contact(InteractionType.CALL)))
        val any = ana.copy(catchUpCounts = CatchUpCounts.ANY_CONTACT)
        assertTrue(policy.counts(any, contact(InteractionType.CALL, source = InteractionSource.CALL_LOG, seconds = 0)))
    }

    @Test
    fun theirChoiceWinsOverSettings() {
        val policy = CatchUpPolicy()
        val inPerson = ana.copy(catchUpCounts = CatchUpCounts.IN_PERSON_ONLY)
        val any = ana.copy(catchUpCounts = CatchUpCounts.ANY_CONTACT)
        assertFalse(policy.counts(inPerson, contact(InteractionType.LONG_CHAT)))
        assertTrue(policy.counts(inPerson, contact(InteractionType.MEETUP)))
        assertTrue(policy.counts(any, contact(InteractionType.EMAIL)))
        assertTrue(policy.onlyInPerson(inPerson))
        assertFalse(policy.onlyInPerson(ana))
        assertTrue(CatchUpPolicy(kinds = setOf(LogKind.MEETUP)).onlyInPerson(ana))
    }

    @Test
    fun yourOwnWays_countOnlyWhenTicked_andAnOtherWithAnyOtherLabelFollowsOther() {
        val policy = CatchUpPolicy(custom = setOf("Voice note"), own = listOf("Voice note", "Letter"))
        assertTrue(policy.counts(ana, contact(InteractionType.OTHER, label = "voice NOTE")))
        assertFalse(policy.counts(ana, contact(InteractionType.OTHER, label = "Letter")))
        assertFalse(policy.counts(ana, contact(InteractionType.OTHER, label = "Postcard")))
        val otherCounts = policy.copy(kinds = CatchUpPolicy.DEFAULT_KINDS + LogKind.OTHER)
        assertTrue(otherCounts.counts(ana, contact(InteractionType.OTHER, label = "Postcard")))
        assertFalse(otherCounts.counts(ana, contact(InteractionType.OTHER, label = "Letter")))
        assertTrue(policy.countsWhenLogged(ana, LogKind.OTHER, "Voice note"))
        assertFalse(policy.countsWhenLogged(ana, LogKind.JOURNAL))
    }

    @Test
    fun settingsTicksAndUnticks_andRenamingOneOfYourOwnKeepsItCounting() = runTest {
        val g = TestGraph(listOf(ana))
        g.setCatchUpKind(LogKindOption(LogKind.CALL), false)
        g.setCatchUpKind(LogKindOption(LogKind.MESSAGE), true)
        // In person and journal entries can't change.
        g.setCatchUpKind(LogKindOption(LogKind.MEETUP), false)
        g.setCatchUpKind(LogKindOption(LogKind.JOURNAL), true)
        assertEquals(
            setOf(LogKind.MEETUP, LogKind.VIDEO_CALL, LogKind.LONG_CHAT, LogKind.GAME, LogKind.MESSAGE),
            g.observeSettings().first().catchUpKinds,
        )

        g.customLogKinds.add("Voice note")
        g.setCatchUpKind(LogKindOption.custom("Voice note"), true)
        g.setCatchUpKind(LogKindOption.custom("voice note"), true) // no duplicate
        assertEquals(listOf("Voice note"), g.observeSettings().first().catchUpCustomKinds)
        g.customLogKinds.rename("Voice note", "Voice message")
        assertEquals(listOf("Voice message"), g.observeSettings().first().catchUpCustomKinds)
        val policy = CadenceRules.from(g.observeSettings().first()).catchUp
        assertTrue(policy.counts(ana, contact(InteractionType.OTHER, label = "Voice message")))
        g.setCatchUpKind(LogKindOption.custom("Voice message"), false)
        assertTrue(g.observeSettings().first().catchUpCustomKinds.isEmpty())
    }
}
