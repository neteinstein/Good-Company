package com.neteinstein.goodcompany.feature.settings.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.NotificationSettings
import com.neteinstein.goodcompany.core.domain.model.NotificationType
import com.neteinstein.goodcompany.core.domain.usecase.ObserveSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SendTestNotificationUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateNotificationSettingsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.UpdateSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

sealed interface NotificationSettingsMessage {
    /** The answer after asking the system for notifications. */
    data class Permission(val granted: Boolean) : NotificationSettingsMessage

    /** "Send a test notification" was handed to the system. */
    data object TestSent : NotificationSettingsMessage
}

data class NotificationSettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val message: NotificationSettingsMessage? = null,
)

/**
 * Settings → Notifications (issue #98): the master switch, one switch per type, "Keep important ones on screen", the
 * hour, the weekly digest, privacy on the lock screen and a test notification. The screen owns the permission prompt.
 */
class NotificationSettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val updateNotifications: UpdateNotificationSettingsUseCase,
    private val sendTest: SendTestNotificationUseCase,
) : ViewModel() {
    private val message = MutableStateFlow<NotificationSettingsMessage?>(null)

    val state: StateFlow<NotificationSettingsUiState> = combine(observeSettings(), message) { s, m ->
        NotificationSettingsUiState(loading = false, settings = s, message = m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationSettingsUiState())

    fun onEnabledChange(enabled: Boolean) = notifications { it.copy(enabled = enabled) }

    fun onTypeChange(type: NotificationType, enabled: Boolean) = notifications { it.with(type, enabled) }

    fun onPersistentChange(enabled: Boolean) = notifications { it.copy(persistent = enabled) }

    /** Issue #116: when the daily "log your day" reminder comes. */
    fun onDailyLogTimeChange(time: LocalTime) = notifications { it.copy(dailyLogMinute = time.hour * 60 + time.minute) }

    /** Issue #133: "Heads-up before birthdays", days before (0 = off); anything but the offered choices is ignored. */
    fun onBirthdayHeadsUpChange(days: Int) {
        if (days in NotificationSettings.BIRTHDAY_HEADS_UP_CHOICES) notifications { it.copy(birthdayHeadsUpDays = days) }
    }

    fun onReminderHourChange(hour: Int) = settings { it.copy(reminderHour = hour.coerceIn(0, 23)) }

    fun onWeeklyDigestChange(enabled: Boolean) = settings { it.copy(weeklyDigest = enabled) }

    /** ISO day of week, 1 = Monday … 7 = Sunday. */
    fun onDigestDayChange(day: Int) = settings { it.copy(digestDay = day.coerceIn(1, 7)) }

    fun onPrivateChange(enabled: Boolean) = settings { it.copy(privateNotifications = enabled) }

    fun onSendTest() {
        viewModelScope.launch {
            sendTest()
            message.value = NotificationSettingsMessage.TestSent
        }
    }

    fun onPermissionResult(granted: Boolean) {
        message.value = NotificationSettingsMessage.Permission(granted)
    }

    fun onMessageShown() = message.update { null }

    private fun settings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { updateSettings(transform) }
    }

    private fun notifications(transform: (NotificationSettings) -> NotificationSettings) {
        viewModelScope.launch { updateNotifications(transform) }
    }
}
