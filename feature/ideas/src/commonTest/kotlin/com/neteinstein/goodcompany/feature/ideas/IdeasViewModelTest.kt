package com.neteinstein.goodcompany.feature.ideas

import kotlinx.datetime.LocalDate
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.repository.GeneratedIdea
import com.neteinstein.goodcompany.core.domain.usecase.WeeklyIdeasOutcome
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.ideas.ui.screens.IdeasMessage
import com.neteinstein.goodcompany.feature.ideas.ui.screens.IdeasViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdeasViewModelTest {
    private val g = TestGraph(listOf(friend("ana") { copy(suggestEvents = true, likes = listOf("jazz")) }))

    private fun vm() = IdeasViewModel(
        g.observeIdeas,
        g.observeFriends,
        g.observeSettings,
        g.updateSettings,
        g.runWeeklyIdeas,
        g.dismissIdea,
        g.markIdeaProposed,
        g.savePlan,
        g.clock,
    )

    @Test
    fun withoutKeyExplainsAndDoesNotCallAi() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertFalse(state.value.hasAiKey)
        vm.onRunNow()
        assertEquals(IdeasMessage.Skipped(WeeklyIdeasOutcome.Reason.NO_API_KEY), state.value.message)
        assertTrue(g.generator.requests.isEmpty())
    }

    @Test
    fun runNowStoresIdeasWithFriendsAndDismissHidesThem() = runViewModelTest {
        g.settingsRepo.update { it.copy(aiApiKey = "k") }
        g.generator.result = Result.success(listOf(GeneratedIdea(listOf("ana"), "event", "Jazz night", "Fri", null, null)))
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(1, state.value.optedInFriends)
        vm.onRunNow()
        assertEquals(IdeasMessage.Found(1), state.value.message)
        val card = state.value.ideas.single()
        assertEquals("Ana", card.friends.single().name)
        vm.onKindFilter(IdeaKind.NEWS)
        assertTrue(state.value.ideas.isEmpty())
        vm.onKindFilter(null)
        vm.onDismiss(card.idea.id)
        assertTrue(state.value.ideas.isEmpty())
        vm.onWeeklyToggle(false)
        assertFalse(state.value.weeklyEnabled)
    }

    @Test
    fun proposingAPlanMarksItAsProposed() = runViewModelTest {
        g.settingsRepo.update { it.copy(aiApiKey = "k") }
        g.generator.result = Result.success(listOf(GeneratedIdea(listOf("ana"), "event", "Jazz night", "Fri", null, null)))
        val vm = vm()
        val state = collecting(vm.state)
        vm.onRunNow()
        val id = state.value.ideas.single().idea.id
        vm.onProposed() // nothing open yet → no-op
        assertNull(state.value.ideas.single().idea.proposedAt)
        vm.onPropose(id)
        assertEquals(id, state.value.proposing?.idea?.id)
        vm.onProposed()
        assertEquals(g.clock.now(), state.value.ideas.single().idea.proposedAt)
        vm.onDismissPropose()
        assertNull(state.value.proposing)
    }

    @Test
    fun anIdeaBecomesAPlan() = runViewModelTest {
        g.ideaRepo.addAll(listOf(Idea("i1", listOf(FriendId("ana")), IdeaKind.EVENT, "Jazz night", "", createdAt = g.clock.now())))
        val vm = vm()
        val state = collecting(vm.state)
        vm.onPlan("i1")
        assertEquals("i1", state.value.planning?.idea?.id)
        vm.onSavePlan("Jazz night", emptyList(), LocalDate(2026, 3, 13), "Hot Clube")
        assertNull(state.value.planning)
        assertEquals(IdeasMessage.PlanSaved, state.value.message)
        val plan = g.planRepo.plans.value.single()
        assertEquals("i1", plan.ideaId)
        assertEquals(listOf(FriendId("ana")), plan.friendIds)
        assertTrue(g.ideaRepo.ideas.value.single().proposedAt != null)
        vm.onPlan("i1")
        vm.onDismissPlan()
        assertNull(state.value.planning)
    }
}
