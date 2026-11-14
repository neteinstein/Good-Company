package com.neteinstein.goodcompany.feature.onboarding

import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.usecase.ImportPickedContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SampleData
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
        ImportPickedContactsUseCase(friends),
        LoadSampleDataUseCase(
            friends,
            FakeHangoutRepository(),
            FakeReminderRepository(),
            FakeIdeaRepository(),
            FakeGroupRepository(),
            FakePlanRepository(),
            FakeInteractionRepository(),
            FixedClock(),
        ),
    )

    @Test
    fun theContactListOpensOnlyWithThePermission_andCountsWhoItAdded() = runViewModelTest {
        val vm = vm()
        vm.onContactListPermissionResult(false)
        assertTrue(vm.state.value.contactListDenied)
        assertFalse(vm.state.value.contactListOpen)
        // A refusal falls back to the system contact picker, once.
        assertTrue(vm.state.value.pickerFallback)
        vm.onPickerFallbackShown()
        assertFalse(vm.state.value.pickerFallback)
        vm.onContactListPermissionResult(true)
        assertFalse(vm.state.value.pickerFallback)
        assertTrue(vm.state.value.contactListOpen)
        assertFalse(vm.state.value.contactListDenied)
        vm.onContactListClosed(0) // went back without saving
        assertFalse(vm.state.value.contactListOpen)
        assertFalse(vm.state.value.hasPeople)
        vm.onContactListPermissionResult(true)
        vm.onContactListClosed(3)
        assertEquals(3, vm.state.value.contactsPicked)
        assertTrue(vm.state.value.hasPeople)
        // Onboarding never imports the address book by itself (D2): only the people chosen are friends.
        assertTrue(friends.friends.value.isEmpty())
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
