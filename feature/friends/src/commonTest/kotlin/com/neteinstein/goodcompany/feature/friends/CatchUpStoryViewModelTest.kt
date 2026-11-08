package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.usecase.StorySlide
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.friends.ui.screens.story.CatchUpStoryViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CatchUpStoryViewModelTest {
    private val g = TestGraph(
        listOf(friend("ana") { copy(likes = listOf("jazz"), lifeUpdates = listOf(LifeUpdate("u1", "New job", at(2026, 3, 9)))) }),
    )

    @Test
    fun tapsThroughTheStoryAndFinishesAfterTheLastSlide() = runViewModelTest {
        val vm = CatchUpStoryViewModel(FriendId("ana"), g.observeCatchUpStory, g.clock)
        val state = collecting(vm.state)
        // Intro, the new job, tastes, outro.
        assertEquals(4, state.value.slideCount)
        assertIs<StorySlide.Intro>(state.value.story!!.slides[state.value.index])

        vm.onPrevious()
        assertEquals(0, state.value.index)
        vm.onNext()
        assertIs<StorySlide.Fresh>(state.value.story!!.slides[state.value.index])
        vm.onPause(true)
        assertTrue(state.value.paused)
        vm.onPause(false)
        vm.onNext()
        vm.onNext()
        assertTrue(state.value.isLast)
        assertFalse(state.value.finished)
        vm.onNext()
        assertTrue(state.value.finished)
    }

    @Test
    fun missingFriend() = runViewModelTest {
        val vm = CatchUpStoryViewModel(FriendId("nobody"), g.observeCatchUpStory, g.clock)
        val state = collecting(vm.state)
        assertTrue(state.value.notFound)
        assertEquals(0, state.value.slideCount)
    }
}
