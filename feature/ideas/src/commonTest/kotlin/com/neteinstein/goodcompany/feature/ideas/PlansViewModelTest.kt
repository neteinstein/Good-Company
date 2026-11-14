package com.neteinstein.goodcompany.feature.ideas

import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GiftList
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Repeat
import com.neteinstein.goodcompany.core.domain.model.RepeatUnit
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.ideas.ui.screens.plans.PlanEditing
import com.neteinstein.goodcompany.feature.ideas.ui.screens.plans.PlansMessage
import com.neteinstein.goodcompany.feature.ideas.ui.screens.plans.PlansViewModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlansViewModelTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")

    private fun graph(clock: FixedClock = FixedClock()) = TestGraph(
        friends = listOf(friend("ana"), friend("rui")),
        hangouts = listOf(Hangout("1", listOf(ana), at(2026, 3, 1))),
        clock = clock,
    )

    private fun vm(g: TestGraph) = PlansViewModel(
        g.observePlans,
        g.observeFriends,
        g.observeGroups,
        g.observeGiftLists,
        g.observeIdeas,
        g.observeSettings,
        g.observeYearReviewCard,
        g.savePlan,
        g.confirmPlan,
        g.cancelPlan,
        g.restorePlan,
        g.clock,
    )

    private fun idea(id: String) = Idea(id, listOf(ana), IdeaKind.EVENT, "Jazz $id", "", createdAt = at(2026, 3, 9))

    @Test
    fun aiIdeasShowOnlyWhileAiIsOn() = runViewModelTest {
        val g = graph()
        g.ideaRepo.addAll(listOf(idea("1"), idea("2"), idea("3")))
        val vm = vm(g)
        val state = collecting(vm.state)
        // F1: with AI off the tab still works, with no ideas at all.
        assertFalse(state.value.isLoading)
        assertFalse(state.value.aiEnabled)
        assertTrue(state.value.ideas.isEmpty())
        assertEquals(0, state.value.ideaCount)

        g.settingsRepo.update { it.copy(aiEnabled = true, aiApiKey = "key") }
        assertTrue(state.value.aiEnabled)
        assertEquals(2, state.value.ideas.size) // the card shows the first two
        assertEquals(3, state.value.ideaCount)
        assertEquals(listOf(ana), state.value.ideas.first().friends.map { it.id })

        g.settingsRepo.update { it.copy(aiEnabled = false) }
        assertTrue(state.value.ideas.isEmpty())
    }

    @Test
    fun plansAreMadeChangedSkippedAndCancelledWithUndo() = runViewModelTest {
        val g = graph()
        val vm = vm(g)
        val state = collecting(vm.state)
        assertTrue(state.value.plans.isEmpty())

        vm.onOpenEditor(null)
        assertEquals(PlanEditing(null), state.value.editing)
        vm.onSavePlan(PlanInput("Lunch", emptyList(), LocalDate(2026, 3, 12))) // nobody: not saved
        assertTrue(g.planRepo.plans.value.isEmpty())
        vm.onSavePlan(PlanInput("Lunch", listOf(ana, rui), LocalDate(2026, 3, 12)))
        assertNull(state.value.editing)
        assertEquals(PlansMessage.Saved, state.value.message)
        val lunch = state.value.plans.single()
        assertEquals(listOf("ana", "rui"), state.value.friendsOf(lunch).map { it.id.value })

        // A circle plan shows its circle.
        val choir = g.saveGroup(null, "Choir", "🎵", CircleColor.PURPLE, listOf(ana, rui), null)
        vm.onOpenEditor(null)
        vm.onSavePlan(PlanInput("Rehearsal", emptyList(), LocalDate(2026, 3, 11), circleId = choir.id))
        val rehearsal = state.value.plans.first()
        assertEquals("Rehearsal", rehearsal.title) // soonest first
        assertEquals("Choir", state.value.circleOf(rehearsal)?.name)

        vm.onOpenEditor(lunch)
        vm.onSavePlan(PlanInput("Long lunch", listOf(ana), LocalDate(2026, 3, 13)))
        assertEquals("Long lunch", g.planRepo.plans.value.single { it.id == lunch.id }.title)

        vm.onCancel(rehearsal)
        assertEquals(PlansMessage.Cancelled, state.value.message)
        assertEquals(listOf("Long lunch"), state.value.plans.map { it.title })
        vm.onUndoCancel()
        assertEquals(2, state.value.plans.size)
        vm.onMessageShown()
        assertNull(state.value.undoPlan)

        vm.onDidNotHappen(state.value.plans.single { it.id == lunch.id })
        assertEquals(PlansMessage.Closed, state.value.message)
        assertEquals(PlanStatus.SKIPPED, g.planRepo.plans.value.single { it.id == lunch.id }.status)

        vm.onOpenEditor(null)
        vm.onDismissEditor()
        assertNull(state.value.editing)
    }

    @Test
    fun skippingARecurringPlanMovesTheSeriesOn() = runViewModelTest {
        val g = graph()
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onOpenEditor(null)
        vm.onSavePlan(
            PlanInput(
                "Family lunch",
                listOf(ana),
                LocalDate(2026, 3, 15),
                repeat = Repeat(1, RepeatUnit.WEEK, LocalDate(2026, 3, 15)),
            ),
        )
        vm.onSkip(state.value.plans.single())
        assertEquals(PlansMessage.Skipped, state.value.message)
        assertEquals(LocalDate(2026, 3, 22), state.value.plans.single().date)
    }

    @Test
    fun giftListTileAndDecemberReview() = runViewModelTest {
        val december = FixedClock(LocalDateTime(2026, 12, 5, 12, 0).toInstant(TimeZone.UTC))
        val g = graph(december)
        val vm = vm(g)
        val state = collecting(vm.state)
        assertNull(state.value.giftList)
        assertEquals(2026, state.value.reviewYear)
        g.giftListRepo.upsert(GiftList("x", "Christmas 2026", LocalDate(2026, 12, 25), createdAt = at(2026, 11, 1)))
        assertEquals("Christmas 2026", state.value.giftList?.list?.title)

        val march = graph()
        val state2 = collecting(vm(march).state)
        assertNull(state2.value.reviewYear) // only in December
    }
}
