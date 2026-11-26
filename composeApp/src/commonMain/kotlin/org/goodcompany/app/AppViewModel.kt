package org.goodcompany.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.goodcompany.app.core.domain.model.ActivityChoice
import org.goodcompany.app.core.domain.model.AppSettings
import org.goodcompany.app.core.domain.model.ContactPhoto
import org.goodcompany.app.core.domain.usecase.IsStorageTemporaryUseCase
import org.goodcompany.app.core.domain.usecase.KeepLanguageInStepUseCase
import org.goodcompany.app.core.domain.usecase.ObserveActivitiesUseCase
import org.goodcompany.app.core.domain.usecase.ObserveMyPhotoUseCase
import org.goodcompany.app.core.domain.usecase.ObserveSettingsUseCase
import org.goodcompany.app.startup.AppStartup

/**
 * [storageIsTemporary]: what's changed now is lost on reload (the web app is already open in another tab).
 * [myPhoto]: your own photo (issue #71), shown in every tab header.
 */
data class AppUiState(
    val settings: AppSettings = AppSettings(),
    val isReady: Boolean = false,
    val storageIsTemporary: Boolean = false,
    val myPhoto: ContactPhoto? = null,
    /** What the plan editors offer (issue #121): the built-in activities that are on and your own. */
    val activities: List<ActivityChoice>? = null,
)

/** Root ViewModel: theme settings and your photo for the whole app, and kicks off [AppStartup]. */
class AppViewModel(
    observeSettings: ObserveSettingsUseCase,
    isStorageTemporary: IsStorageTemporaryUseCase,
    observeMyPhoto: ObserveMyPhotoUseCase,
    observeActivities: ObserveActivitiesUseCase,
    startup: AppStartup,
    private val keepLanguageInStep: KeepLanguageInStepUseCase,
) : ViewModel() {
    val state: StateFlow<AppUiState> =
        combine(
            observeSettings(),
            flow { emit(isStorageTemporary()) }.onStart { emit(false) },
            observeMyPhoto().onStart { emit(null) },
            observeActivities().onStart { emit(emptyList()) },
        ) { settings, temporary, photo, activities ->
            AppUiState(settings, isReady = true, storageIsTemporary = temporary, myPhoto = photo, activities = activities.ifEmpty { null })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    init {
        startup.start(viewModelScope)
    }

    /** Back in the foreground: a language picked in the system's per-app setting meanwhile becomes the app's (#174). */
    fun onForeground() {
        viewModelScope.launch { runCatching { keepLanguageInStep.reconcile() } }
    }
}
