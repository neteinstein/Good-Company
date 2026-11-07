package com.neteinstein.goodcompany

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.startup.AppStartup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AppUiState(val settings: AppSettings = AppSettings(), val isReady: Boolean = false)

/** Root ViewModel: theme settings for the whole app, and kicks off [AppStartup]. */
class AppViewModel(observeSettings: ObserveSettingsUseCase, startup: AppStartup) : ViewModel() {
    val state: StateFlow<AppUiState> = observeSettings()
        .map { AppUiState(it, isReady = true) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        startup.start(viewModelScope)
    }
}
