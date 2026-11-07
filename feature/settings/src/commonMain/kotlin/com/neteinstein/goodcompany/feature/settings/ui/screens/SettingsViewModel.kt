package com.neteinstein.goodcompany.feature.settings.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.usecase.ContactsFileImportResult
import com.neteinstein.goodcompany.core.domain.usecase.HasSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportContactsFileUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-off feedback for the snackbar; the screen turns it into localised text. */
sealed interface SettingsMessage {
    data object KeySaved : SettingsMessage

    data object KeyRemoved : SettingsMessage

    data class Notifications(val granted: Boolean) : SettingsMessage

    data object SampleAdded : SettingsMessage

    data object SampleRemoved : SettingsMessage

    data class FileImported(val result: ContactsFileImportResult) : SettingsMessage
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** Masked form of the saved key, e.g. "sk-ant-…9f3a". */
    val maskedKey: String? = null,
    val hasSampleData: Boolean = false,
    val message: SettingsMessage? = null,
)

class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    hasSampleData: HasSampleDataUseCase,
    private val loadSampleData: LoadSampleDataUseCase,
    private val removeSampleData: RemoveSampleDataUseCase,
    private val importContactsFile: ImportContactsFileUseCase,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(observeSettings(), hasSampleData(), local) { settings, sample, ui ->
        ui.copy(settings = settings, maskedKey = settings.aiApiKey?.takeIf { it.isNotBlank() }?.let(::mask), hasSampleData = sample)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { updateSettings(transform) }
    }

    fun onSaveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        update { it.copy(aiApiKey = trimmed) }
        local.update { it.copy(message = SettingsMessage.KeySaved) }
    }

    fun onClearApiKey() {
        update { it.copy(aiApiKey = null) }
        local.update { it.copy(message = SettingsMessage.KeyRemoved) }
    }

    fun onThemeChange(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    fun onDynamicColorChange(enabled: Boolean) = update { it.copy(dynamicColor = enabled) }

    fun onWeeklyIdeasChange(enabled: Boolean) = update { it.copy(weeklyIdeasEnabled = enabled) }

    fun onHomeAreaChange(area: String) = update { it.copy(homeArea = area) }

    /** Default "meet every N days" for a circle. */
    fun onCircleDaysChange(circle: Circle, days: Int) = update {
        val d = days.coerceIn(1, 365)
        when (circle) {
            Circle.INNER -> it.copy(innerCircleDays = d)
            Circle.CLOSE -> it.copy(closeCircleDays = d)
            Circle.GOOD -> it.copy(goodFriendsDays = d)
        }
    }

    fun onContactsFilePicked(text: String?) {
        if (text.isNullOrBlank()) return
        viewModelScope.launch {
            val result = importContactsFile(text)
            local.update { it.copy(message = SettingsMessage.FileImported(result)) }
        }
    }

    fun onReminderHourChange(hour: Int) = update { it.copy(reminderHour = hour.coerceIn(0, 23)) }

    fun onNotificationPermissionResult(granted: Boolean) = local.update {
        it.copy(message = SettingsMessage.Notifications(granted))
    }

    fun onLoadSampleData() {
        viewModelScope.launch {
            loadSampleData()
            local.update { it.copy(message = SettingsMessage.SampleAdded) }
        }
    }

    fun onRemoveSampleData() {
        viewModelScope.launch {
            removeSampleData()
            local.update { it.copy(message = SettingsMessage.SampleRemoved) }
        }
    }

    /** Shows the intro again on next launch of the main screen. */
    fun onReplayIntro() = update { it.copy(onboardingDone = false) }

    fun onMessageShown() = local.update { it.copy(message = null) }

    companion object {
        fun mask(key: String): String = if (key.length <= 10) "••••" else key.take(7) + "…" + key.takeLast(4)
    }
}
