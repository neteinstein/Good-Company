package org.goodcompany.app.feature.onboarding

import org.goodcompany.app.core.domain.model.ContactRecord
import org.goodcompany.app.core.domain.usecase.ImportPickedContactsUseCase
import org.goodcompany.app.core.domain.usecase.LoadSampleDataUseCase
import org.goodcompany.app.core.domain.usecase.SampleData
import org.goodcompany.app.core.domain.usecase.UpdateSettingsUseCase
import org.goodcompany.app.core.testing.FakeFriendRepository
import org.goodcompany.app.core.testing.FakeGroupRepository
import org.goodcompany.app.core.testing.FakeHangoutRepository
import org.goodcompany.app.core.testing.FakeIdeaRepository
import org.goodcompany.app.core.testing.FakeInteractionRepository
import org.goodcompany.app.core.testing.FakePlanRepository
import org.goodcompany.app.core.testing.FakeReminderRepository
import org.goodcompany.app.core.testing.FakeSettingsRepository
import org.goodcompany.app.core.testing.FixedClock
import org.goodcompany.app.core.testing.runViewModelTest
import org.goodcompany.app.feature.onboarding.ui.screens.OnboardingViewModel
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
    fun restoreABackupOpensAndClosesOverTheGuide() = runViewModelTest {
        val vm = vm()
        assertFalse(vm.state.value.restoreOpen)
        vm.onRestoreOpened(true)
        assertTrue(vm.state.value.restoreOpen)
        vm.onRestoreOpened(false)
        assertFalse(vm.state.value.restoreOpen)
        assertFalse(vm.state.value.finished)
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
