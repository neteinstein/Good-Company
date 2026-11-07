package com.neteinstein.goodcompany.feature.onboarding

import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SampleData
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeIdeaRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import com.neteinstein.goodcompany.core.testing.FakeReminderRepository
import com.neteinstein.goodcompany.core.testing.FakeSettingsRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.onboarding.ui.screens.OnboardingViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingViewModelTest {
    private val friends = FakeFriendRepository(contacts = listOf(ContactRecord("1", "Ana", emptyList(), null)))
    private val settings = FakeSettingsRepository()
    private fun vm() = OnboardingViewModel(
        UpdateSettingsUseCase(settings),
        SyncContactsUseCase(friends, FakeInteractionRepository()),
        LoadSampleDataUseCase(friends, FakeHangoutRepository(), FakeReminderRepository(), FakeIdeaRepository(), FixedClock()),
    )

    @Test
    fun importContactsThenFinish() = runViewModelTest {
        val vm = vm()
        vm.onContactsPermissionResult(false)
        assertTrue(vm.state.value.contactsDenied)
        vm.onContactsPermissionResult(true)
        assertEquals(1, vm.state.value.contactsImported)
        vm.onNotificationsPermissionResult(true)
        vm.onFinish()
        assertTrue(vm.state.value.finished)
        assertTrue(settings.settings.value.onboardingDone)
    }

    @Test
    fun exploreWithSampleLoadsDataAndFinishes() = runViewModelTest {
        val vm = vm()
        vm.onExploreWithSample()
        assertTrue(vm.state.value.finished)
        assertTrue(friends.friends.value.any { SampleData.isSample(it.id.value) })
    }
}
