package com.neteinstein.goodcompany

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.usecase.IsStorageTemporaryUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveMyPhotoUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.startup.AppStartup
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * [storageIsTemporary]: what's changed now is lost on reload (the web app is already open in another tab).
 * [myPhoto]: your own photo (issue #71), shown in every tab header.
 */
data class AppUiState(
    val settings: AppSettings = AppSettings(),
    val isReady: Boolean = false,
    val storageIsTemporary: Boolean = false,
    val myPhoto: ContactPhoto? = null,
)

/** Root ViewModel: theme settings and your photo for the whole app, and kicks off [AppStartup]. */
class AppViewModel(
    observeSettings: ObserveSettingsUseCase,
    isStorageTemporary: IsStorageTemporaryUseCase,
    observeMyPhoto: ObserveMyPhotoUseCase,
    startup: AppStartup,
) : ViewModel() {
    val state: StateFlow<AppUiState> =
        combine(
            observeSettings(),
            flow { emit(isStorageTemporary()) }.onStart { emit(false) },
            observeMyPhoto().onStart { emit(null) },
        ) { settings, temporary, photo ->
            AppUiState(settings, isReady = true, storageIsTemporary = temporary, myPhoto = photo)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        startup.start(viewModelScope)
    }
}
