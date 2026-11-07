package com.neteinstein.goodcompany.feature.settings.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Circle
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.usecase.ContactsFileImportResult
import com.neteinstein.goodcompany.core.domain.usecase.ExportBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.HasSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ImportContactsFileUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreBackupUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RestoreResult
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

    data object BackupSaved : SettingsMessage

    data class BackupRestored(val friends: Int) : SettingsMessage

    data object BackupInvalid : SettingsMessage

    data object LockFailed : SettingsMessage
}

/** A backup ready to be handed to the platform "save file" UI. */
data class ExportFile(val name: String, val content: String)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    /** Masked form of the saved key, e.g. "sk-ant-…9f3a". */
    val maskedKey: String? = null,
    val hasSampleData: Boolean = false,
    val message: SettingsMessage? = null,
    val export: ExportFile? = null,
    /** A picked backup waiting for the "Replace everything?" confirmation. */
    val pendingRestore: String? = null,
)

class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    hasSampleData: HasSampleDataUseCase,
    private val loadSampleData: LoadSampleDataUseCase,
    private val removeSampleData: RemoveSampleDataUseCase,
    private val importContactsFile: ImportContactsFileUseCase,
    private val exportBackup: ExportBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
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

    // --- Nudges & privacy ------------------------------------------------------------------------------------------

    fun onWeeklyDigestChange(enabled: Boolean) = update { it.copy(weeklyDigest = enabled) }

    /** ISO day of week, 1 = Monday … 7 = Sunday. */
    fun onDigestDayChange(day: Int) = update { it.copy(digestDay = day.coerceIn(1, 7)) }

    fun onPrivateNotificationsChange(enabled: Boolean) = update { it.copy(privateNotifications = enabled) }

    fun onShowWarmthChange(enabled: Boolean) = update { it.copy(showWarmth = enabled) }

    /** Called after the device lock confirmed (or refused) the user; turning the lock off needs no check. */
    fun onAppLockChange(enabled: Boolean, verified: Boolean) {
        if (enabled && !verified) {
            local.update { it.copy(message = SettingsMessage.LockFailed) }
            return
        }
        update { it.copy(appLock = enabled) }
    }

    // --- Backup --------------------------------------------------------------------------------------------------

    fun onExportBackup() {
        viewModelScope.launch {
            val content = exportBackup()
            local.update { it.copy(export = ExportFile(exportBackup.fileName(), content)) }
        }
    }

    /** The platform save UI took the file (or the user cancelled). */
    fun onExportHandled(saved: Boolean) = local.update { it.copy(export = null, message = if (saved) SettingsMessage.BackupSaved else it.message) }

    fun onBackupPicked(text: String?) {
        if (text.isNullOrBlank()) return
        local.update { it.copy(pendingRestore = text) }
    }

    fun onCancelRestore() = local.update { it.copy(pendingRestore = null) }

    fun onConfirmRestore() {
        val raw = local.value.pendingRestore ?: return
        viewModelScope.launch {
            val result = restoreBackup(raw)
            local.update {
                it.copy(
                    pendingRestore = null,
                    message = when (result) {
                        is RestoreResult.Restored -> SettingsMessage.BackupRestored(result.friends)
                        RestoreResult.NotABackup -> SettingsMessage.BackupInvalid
                    },
                )
            }
        }
    }

    fun onMessageShown() = local.update { it.copy(message = null) }

    companion object {
        fun mask(key: String): String = if (key.length <= 10) "••••" else key.take(7) + "…" + key.takeLast(4)
    }
}
