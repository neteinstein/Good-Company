package com.neteinstein.goodcompany

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.usecase.IsStorageTemporaryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.startup.AppStartup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** [storageIsTemporary]: what's changed now is lost on reload (the web app is already open in another tab). */
data class AppUiState(
    val settings: AppSettings = AppSettings(),
    val isReady: Boolean = false,
    val storageIsTemporary: Boolean = false,
)

/** Root ViewModel: theme settings for the whole app, and kicks off [AppStartup]. */
class AppViewModel(
    observeSettings: ObserveSettingsUseCase,
    isStorageTemporary: IsStorageTemporaryUseCase,
    startup: AppStartup,
) : ViewModel() {
    val state: StateFlow<AppUiState> =
        combine(observeSettings(), flow { emit(isStorageTemporary()) }.onStart { emit(false) }) { settings, temporary ->
            AppUiState(settings, isReady = true, storageIsTemporary = temporary)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        startup.start(viewModelScope)
    }
}
