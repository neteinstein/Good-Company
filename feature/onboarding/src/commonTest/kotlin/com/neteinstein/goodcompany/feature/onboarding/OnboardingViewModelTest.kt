package com.neteinstein.goodcompany.feature.onboarding

import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.usecase.ImportPickedContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SampleData
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeGroupRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeIdeaRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import com.neteinstein.goodcompany.core.testing.FakePlanRepository
import com.neteinstein.goodcompany.core.testing.FakeReminderRepository
import com.neteinstein.goodcompany.core.testing.FakeSettingsRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.onboarding.ui.screens.OnboardingViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingViewModelTest {
    private val friends = FakeFriendRepository(contacts = listOf(ContactRecord("1", "Ana", emptyList(), null)))
    private val settings = FakeSettingsRepository()
    private fun vm() = OnboardingViewModel(
        UpdateSettingsUseCase(settings),
        SyncContactsUseCase(friends, FakeInteractionRepository()),
        ImportPickedContactsUseCase(friends),
        LoadSampleDataUseCase(
            friends,
            FakeHangoutRepository(),
            FakeReminderRepository(),
            FakeIdeaRepository(),
            FakeGroupRepository(),
            FakePlanRepository(),
            FixedClock(),
        ),
    )

    @Test
    fun importContactsThenFinish() = runViewModelTest {
        val vm = vm()
        vm.onContactsPermissionResult(false)
        assertTrue(vm.state.value.contactsDenied)
        vm.onContactsPermissionResult(true)
        assertEquals(1, vm.state.value.contactsImported)
        assertTrue(vm.state.value.hasPeople)
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

    @Test
    fun pickedContactsAreAddedAndCounted() = runViewModelTest {
        val vm = vm()
        vm.onContactsPicked(emptyList())
        assertEquals(0, vm.state.value.contactsPicked)
        assertFalse(vm.state.value.hasPeople)
        vm.onContactsPicked(listOf(ContactRecord("7", "Rui", listOf("+351 912"), null)))
        vm.onContactsPicked(listOf(ContactRecord("8", "Bea", emptyList(), null)))
        assertEquals(2, vm.state.value.contactsPicked)
        assertTrue(vm.state.value.hasPeople)
        assertTrue(friends.friends.value.any { it.name == "Rui" })
    }
}
