package com.neteinstein.goodcompany.feature.review

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.review.ui.screens.YearReviewViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YearReviewViewModelTest {
    private val ana = FriendId("ana")
    private val rui = FriendId("rui")
    private val g = TestGraph(
        friends = listOf(friend("ana"), friend("rui")),
        hangouts = listOf(
            Hangout("1", listOf(ana, rui), at(2026, 1, 10)),
            Hangout("2", listOf(ana), at(2026, 2, 14)),
            Hangout("3", listOf(rui), at(2025, 12, 31)),
        ),
    )

    @Test
    fun showsTheCurrentYearByDefault() = runViewModelTest {
        val vm = YearReviewViewModel(null, g.observeYearInReview, g.clock)
        val state = collecting(vm.state)
        val r = state.value.review!!
        assertFalse(state.value.isLoading)
        assertEquals(2026, r.year)
        assertEquals(2, r.meetups)
        assertEquals("Ana", r.topFriends.first().first.name)
    }

    @Test
    fun aPastYearCanBeOpenedAndCopyFeedbackClears() = runViewModelTest {
        val vm = YearReviewViewModel(2024, g.observeYearInReview, g.clock)
        val state = collecting(vm.state)
        assertTrue(state.value.review!!.isEmpty)
        vm.onCopied()
        assertTrue(state.value.copied)
        vm.onCopiedShown()
        assertFalse(state.value.copied)
    }
}
