package org.goodcompany.app.feature.ideas

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.goodcompany.app.core.domain.model.CircleColor
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.GiftList
import org.goodcompany.app.core.domain.model.GiftOccasion
import org.goodcompany.app.core.domain.model.Hangout
import org.goodcompany.app.core.domain.model.Idea
import org.goodcompany.app.core.domain.model.IdeaKind
import org.goodcompany.app.core.domain.model.PlanAnswer
import org.goodcompany.app.core.domain.model.PlanStatus
import org.goodcompany.app.core.domain.model.Repeat
import org.goodcompany.app.core.domain.model.RepeatUnit
import org.goodcompany.app.core.domain.usecase.PlanInput
import org.goodcompany.app.core.testing.FixedClock
import org.goodcompany.app.core.testing.Fixtures.at
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import org.goodcompany.app.core.testing.collecting
import org.goodcompany.app.core.testing.runViewModelTest
import org.goodcompany.app.feature.ideas.ui.screens.plans.GiftListsSection
import org.goodcompany.app.feature.ideas.ui.screens.plans.PlanEditing
import org.goodcompany.app.feature.ideas.ui.screens.plans.PlansMessage
import org.goodcompany.app.feature.ideas.ui.screens.plans.PlansRow
import org.goodcompany.app.feature.ideas.ui.screens.plans.PlansViewModel
import org.goodcompany.app.feature.ideas.ui.screens.plans.indexOfGiftTarget
import org.goodcompany.app.feature.ideas.ui.screens.plans.plansRows
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
        g.closePlan,
        g.restorePlan,
        g.giftLists,
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

        // Issue #100: cancel keeps it, struck through; Undo reopens it.
        vm.onAnswer(rehearsal, PlanAnswer.CANCEL)
        assertEquals(PlansMessage.Cancelled, state.value.message)
        assertEquals(listOf("Long lunch"), state.value.plans.map { it.title })
        assertEquals(listOf(rehearsal.id), state.value.cancelled.map { it.id })
        assertEquals(PlanStatus.CANCELLED, g.planRepo.plans.value.single { it.id == rehearsal.id }.status)
        vm.onUndo()
        assertEquals(2, state.value.plans.size)
        assertTrue(state.value.cancelled.isEmpty())
        vm.onMessageShown()
        assertNull(state.value.undoPlan)

        // Delete removes it, with Undo.
        vm.onAnswer(rehearsal, PlanAnswer.DELETE)
        assertEquals(PlansMessage.Deleted, state.value.message)
        assertTrue(g.planRepo.plans.value.none { it.id == rehearsal.id })
        vm.onUndo()
        assertEquals(2, state.value.plans.size)
        vm.onMessageShown()

        // "I didn't go" on a group plan: closed, no check-in.
        vm.onAnswer(rehearsal, PlanAnswer.NOT_ATTENDED)
        assertEquals(PlansMessage.NotAttended, state.value.message)
        assertNull(state.value.undoPlan)
        assertEquals(PlanStatus.NOT_ATTENDED, g.planRepo.plans.value.single { it.id == rehearsal.id }.status)
        assertEquals(1, g.hangoutRepo.hangouts.value.size)

        // "It happened" is navigation: nothing changes here.
        vm.onAnswer(state.value.plans.single(), PlanAnswer.HAPPENED)
        assertEquals(PlanStatus.PLANNED, g.planRepo.plans.value.single { it.id == lunch.id }.status)

        vm.onAnswer(state.value.plans.single { it.id == lunch.id }, PlanAnswer.DIDNT_HAPPEN)
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
    fun giftListsSectionAndDecemberReview() = runViewModelTest {
        val december = FixedClock(LocalDateTime(2026, 12, 5, 12, 0).toInstant(TimeZone.UTC))
        val g = graph(december)
        val vm = vm(g)
        val state = collecting(vm.state)
        assertTrue(state.value.gifts.isEmpty)
        assertTrue(PlansRow.GiftsEmpty in plansRows(state.value))
        assertEquals(2026, state.value.reviewYear)
        g.giftListRepo.upsert(GiftList("x", "Christmas 2026", LocalDate(2026, 12, 25), createdAt = at(2026, 11, 1)))
        assertEquals(listOf("Christmas 2026"), state.value.gifts.current.map { it.list.title })

        val march = graph()
        val state2 = collecting(vm(march).state)
        assertNull(state2.value.reviewYear) // only in December
    }

    @Test
    fun cancelledPlans_showForAMonth_andCanBeDeleted() = runViewModelTest {
        val g = graph()
        val vm = vm(g)
        val state = collecting(vm.state)
        val old = g.savePlan(null, "Old", listOf(ana), LocalDate(2026, 1, 1))
        val recent = g.savePlan(null, "Recent", listOf(ana), LocalDate(2026, 3, 1))
        g.cancelPlan(old.id)
        g.cancelPlan(recent.id)
        assertEquals(listOf(recent.id), state.value.cancelled.map { it.id })
        vm.onDeleteCancelled(state.value.cancelled.single())
        assertEquals(PlansMessage.Deleted, state.value.message)
        assertTrue(state.value.cancelled.isEmpty())
        vm.onUndo()
        assertEquals(listOf(recent.id), state.value.cancelled.map { it.id })
    }

    // --- Gift lists on the Plans tab (issue #104) ---

    @Test
    fun aNewGiftList_opensInPlace_andTheTabScrollsToIt() = runViewModelTest {
        val g = graph()
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onNewGiftList()
        assertEquals(GiftOccasion.CHRISTMAS, state.value.gifts.creating?.occasion)
        vm.onDismissNewGiftList()
        assertNull(state.value.gifts.creating)

        vm.onNewGiftList()
        vm.onCreateGiftList("  ", null, null) // no name: nothing made, the dialog stays
        assertTrue(state.value.gifts.current.isEmpty())
        vm.onCreateGiftList("Christmas 2026", GiftOccasion.CHRISTMAS, LocalDate(2026, 12, 25))
        val id = state.value.gifts.current.single().list.id
        assertNull(state.value.gifts.creating)
        assertEquals(setOf(id), state.value.gifts.expanded)
        assertEquals(id, state.value.gifts.scrollTo)
        val rows = plansRows(state.value)
        val index = rows.indexOfGiftTarget(id)!!
        assertEquals(PlansRow.GiftList(state.value.gifts.current.single(), expanded = true, past = false), rows[index])
        assertTrue(rows.indexOf(PlansRow.GiftsHeader) < index && index < rows.indexOf(PlansRow.WhosFree))
        vm.onScrolled()
        assertNull(state.value.gifts.scrollTo)

        // Tapping the card closes it again, and opens it.
        vm.onToggleGiftList(id)
        assertTrue(state.value.gifts.expanded.isEmpty())
        vm.onToggleGiftList(id)
        assertEquals(setOf(id), state.value.gifts.expanded)
    }

    @Test
    fun aGiftListCard_editsDuplicatesAndDeletesWithUndo() = runViewModelTest {
        val g = graph()
        val old = g.giftLists.create("Christmas 2025", GiftOccasion.CHRISTMAS, LocalDate(2025, 12, 25))!!
        g.giftLists.addPeople(old, listOf(ana), 30.0)
        val vm = vm(g)
        val state = collecting(vm.state)
        assertEquals(listOf(old), state.value.gifts.past.map { it.list.id })
        assertTrue(PlansRow.PastGiftsHeader in plansRows(state.value))

        vm.onEditGiftList(old)
        assertEquals(old, state.value.gifts.editing?.id)
        vm.onDismissEditGiftList()
        assertNull(state.value.gifts.editing)
        vm.onEditGiftList(old)
        vm.onSaveGiftList("Natal 2025", GiftOccasion.CHRISTMAS, LocalDate(2025, 12, 24))
        assertNull(state.value.gifts.editing)
        assertEquals("Natal 2025", state.value.gifts.past.single().list.title)
        vm.onSaveGiftList("Nothing open", null, null) // no list being edited: nothing changes
        assertEquals("Natal 2025", g.giftListRepo.get(old)!!.title)

        vm.onDuplicateGiftList(old)
        val copy = state.value.gifts.current.single()
        assertEquals("Natal 2026", copy.list.title)
        assertEquals(listOf(ana), copy.people.map { it.id })
        assertEquals(setOf(copy.list.id), state.value.gifts.expanded)
        assertEquals(copy.list.id, state.value.gifts.scrollTo)

        vm.onDeleteGiftList(copy.list.id)
        assertEquals(PlansMessage.GiftListDeleted("Natal 2026"), state.value.message)
        assertTrue(state.value.gifts.current.isEmpty())
        assertTrue(state.value.gifts.expanded.isEmpty())
        vm.onUndo()
        vm.onMessageShown()
        assertEquals(listOf(copy.list.id), state.value.gifts.current.map { it.list.id })
        assertNull(state.value.undoGiftList)
    }

    @Test
    fun aPersonsGiftListsLink_opensTheirList_elseScrollsToTheSection() = runViewModelTest {
        val g = graph()
        val christmas = g.giftLists.create("Christmas 2026", GiftOccasion.CHRISTMAS, LocalDate(2026, 12, 25))!!
        g.giftLists.addPeople(christmas, listOf(rui))
        val vm = vm(g)
        val state = collecting(vm.state)
        vm.onShowGiftListsFor(rui)
        assertEquals(setOf(christmas), state.value.gifts.expanded)
        assertEquals(christmas, state.value.gifts.scrollTo)
        vm.onScrolled()

        vm.onShowGiftListsFor(ana) // on no list
        assertEquals(GiftListsSection.HEADER, state.value.gifts.scrollTo)
        assertEquals(plansRows(state.value).indexOf(PlansRow.GiftsHeader), plansRows(state.value).indexOfGiftTarget(GiftListsSection.HEADER))
        assertNull(plansRows(state.value).indexOfGiftTarget("gone"))
    }
}
