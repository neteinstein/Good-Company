package com.neteinstein.goodcompany.feature.onboarding.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val contactsImported: Int? = null,
    val contactsDenied: Boolean = false,
    val notificationsGranted: Boolean? = null,
    val isWorking: Boolean = false,
    val finished: Boolean = false,
)

class OnboardingViewModel(
    private val updateSettings: UpdateSettingsUseCase,
    private val syncContacts: SyncContactsUseCase,
    private val loadSampleData: LoadSampleDataUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun onContactsPermissionResult(granted: Boolean) {
        if (!granted) {
            _state.update { it.copy(contactsDenied = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            val count = runCatching { syncContacts().contacts }.getOrDefault(0)
            _state.update { it.copy(isWorking = false, contactsImported = count, contactsDenied = false) }
        }
    }

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
