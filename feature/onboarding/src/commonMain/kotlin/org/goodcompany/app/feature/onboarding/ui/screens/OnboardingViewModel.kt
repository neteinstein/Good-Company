package org.goodcompany.app.feature.onboarding.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.goodcompany.app.core.domain.model.ContactRecord
import org.goodcompany.app.core.domain.usecase.ImportPickedContactsUseCase
import org.goodcompany.app.core.domain.usecase.LoadSampleDataUseCase
import org.goodcompany.app.core.domain.usecase.UpdateSettingsUseCase

data class OnboardingUiState(
    /** The contacts permission was refused: the primer explains it and keeps the system contact picker. */
    val contactListDenied: Boolean = false,
    /** The "Choose friends" list (contacts with checkboxes, decision D2) is showing. */
    val contactListOpen: Boolean = false,
    /** The contact list was refused just now, so the screen opens the system contact picker instead, once. */
    val pickerFallback: Boolean = false,
    /** People added so far, from the contact list or the system contact picker. */
    val contactsPicked: Int = 0,
    val notificationsGranted: Boolean? = null,
    val isWorking: Boolean = false,
    /** Issue #166: "Restore a backup" is open over the guide. */
    val restoreOpen: Boolean = false,
    val finished: Boolean = false,
) {
    /** Someone came in from the contact list or the contact picker, so the app won't start empty. */
    val hasPeople: Boolean get() = contactsPicked > 0
}

class OnboardingViewModel(
    private val updateSettings: UpdateSettingsUseCase,
    private val importPickedContacts: ImportPickedContactsUseCase,
    private val loadSampleData: LoadSampleDataUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /**
     * "Show my contacts": the permission prompt resolved. Granted opens the list; refused falls back to the system
     * contact picker ([OnboardingUiState.pickerFallback], cleared by [onPickerFallbackShown]; owner decision 2026-11-14).
     */
    fun onContactListPermissionResult(granted: Boolean) = _state.update {
        it.copy(contactListOpen = granted, contactListDenied = !granted, pickerFallback = !granted)
    }

    /** The screen opened the system contact picker after a refused contact list. */
    fun onPickerFallbackShown() = _state.update { it.copy(pickerFallback = false) }

    /** The contact list closed, having added [added] people (0 when the user went back). */
    fun onContactListClosed(added: Int) = _state.update {
        it.copy(contactListOpen = false, contactsPicked = it.contactsPicked + added.coerceAtLeast(0))
    }

    /** People chosen in the system contact picker; the app only ever sees those. */
    fun onContactsPicked(records: List<ContactRecord>) {
        if (records.isEmpty()) return
        viewModelScope.launch {
            val result = importPickedContacts(records)
            _state.update { it.copy(contactsPicked = it.contactsPicked + result.added) }
        }
    }

    /** Issue #166: "Restore a backup" opened ([open]) or closed with its back arrow. */
    fun onRestoreOpened(open: Boolean) = _state.update { it.copy(restoreOpen = open) }

    fun onNotificationsPermissionResult(granted: Boolean) = _state.update { it.copy(notificationsGranted = granted) }

    fun onExploreWithSample() {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            loadSampleData()
            finish()
        }
    }

    fun onFinish() {
        viewModelScope.launch { finish() }
    }

    private suspend fun finish() {
        updateSettings { it.copy(onboardingDone = true) }
        _state.update { it.copy(isWorking = false, finished = true) }
    }
}
