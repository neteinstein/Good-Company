package com.neteinstein.goodcompany.feature.ideas

import com.neteinstein.goodcompany.core.domain.repository.GeneratedIdea
import com.neteinstein.goodcompany.core.domain.usecase.DismissIdeaUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveFriendsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RunWeeklyIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeIdeaGenerator
import com.neteinstein.goodcompany.core.testing.FakeIdeaRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import com.neteinstein.goodcompany.core.testing.FakeLocationProvider
import com.neteinstein.goodcompany.core.testing.FakeSettingsRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.SequentialIds
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.ideas.ui.screens.IdeasViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdeasViewModelTest {
    private val friends = FakeFriendRepository(listOf(friend("ana") { copy(suggestEvents = true, likes = listOf("jazz")) }))
    private val ideas = FakeIdeaRepository()
    private val settings = FakeSettingsRepository()
    private val generator = FakeIdeaGenerator()
    private val clock = FixedClock()
    private val interactions = FakeInteractionRepository()

    private fun vm() = IdeasViewModel(
        ObserveIdeasUseCase(ideas),
        ObserveFriendsUseCase(friends, FakeHangoutRepository(), interactions, clock),
        ObserveSettingsUseCase(settings),
        UpdateSettingsUseCase(settings),
        RunWeeklyIdeasUseCase(friends, interactions, settings, ideas, generator, FakeLocationProvider(), clock, SequentialIds()),
        DismissIdeaUseCase(ideas),
        clock,
    )

    @Test
    fun withoutKeyExplainsAndDoesNotCallAi() = runViewModelTest {
        val vm = vm()
        val state = collecting(vm.state)
        assertFalse(state.value.hasAiKey)
        vm.onRunNow()
        assertEquals("Add your API key in Settings first.", state.value.message)
        assertTrue(generator.requests.isEmpty())
    }

    @Test
    fun runNowStoresIdeasWithFriendsAndDismissHidesThem() = runViewModelTest {
        settings.update { it.copy(aiApiKey = "k") }
        generator.result = Result.success(listOf(GeneratedIdea(listOf("ana"), "event", "Jazz night", "Fri", null, null)))
        val vm = vm()
        val state = collecting(vm.state)
        assertEquals(1, state.value.optedInFriends)
        vm.onRunNow()
        assertEquals("1 new ideas ✨", state.value.message)
        val card = state.value.ideas.single()
        assertEquals("Ana", card.friends.single().name)
        vm.onDismiss(card.idea.id)
        assertTrue(state.value.ideas.isEmpty())
        vm.onWeeklyToggle(false)
        assertFalse(state.value.weeklyEnabled)
    }
}
