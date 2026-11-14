package com.neteinstein.goodcompany.feature.hangouts

import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.usecase.PlanInput
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.free.FreePlanDraft
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.free.WhosFreeMessage
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.free.WhosFreeViewModel
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhosFreeViewModelTest {
    private val ana = FriendId("ana")
    private val g = TestGraph(
        friends = listOf(friend("ana") { copy(closeness = Closeness.CLOSE, meetEveryDays = 7) }, friend("rui")),
        hangouts = listOf(Hangout("1", listOf(ana), at(2026, 2, 1))),
    )

    private fun vm() = WhosFreeViewModel(g.observeOccasionMatches, g.observeFriends, g.observeGroups, g.savePlan, g.clock)

    @Test
    fun suggestsTaggedFriendsAndPlansWithThem() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertTrue(state.value.occasions.isEmpty())
        assertNull(state.value.selectedOccasion)
        g.updateFriend(ana) { it.copy(occasions = listOf(Occasion.WEEKDAY_LUNCH, Occasion.COFFEE)) }
        assertEquals(Occasion.WEEKDAY_LUNCH, state.value.selectedOccasion)
        vm.onSelectOccasion(Occasion.COFFEE)
        assertEquals(Occasion.COFFEE, state.value.selectedOccasion)
        assertEquals(listOf(ana), state.value.occasions.getValue(Occasion.COFFEE).map { it.friend.id })

        vm.onPlanWith(ana, Occasion.COFFEE)
        assertEquals(FreePlanDraft(ana, Occasion.COFFEE), state.value.planDraft)
        vm.onSavePlan(PlanInput("Coffee", emptyList(), LocalDate(2026, 3, 12)))
        assertNull(state.value.planDraft)
        assertEquals(WhosFreeMessage.PlanSaved, state.value.message)
        assertEquals(listOf(ana), g.planRepo.plans.value.single().friendIds) // nobody picked: the friend whose "Plan" it was
        vm.onMessageShown()
        assertNull(state.value.message)

        vm.onPlanWith(ana, Occasion.COFFEE)
        vm.onSavePlan(PlanInput(" ", emptyList(), LocalDate(2026, 3, 12)))
        assertEquals(1, g.planRepo.plans.value.size) // a plan needs a title
        vm.onDismissPlan()
        assertNull(state.value.planDraft)
    }
}
