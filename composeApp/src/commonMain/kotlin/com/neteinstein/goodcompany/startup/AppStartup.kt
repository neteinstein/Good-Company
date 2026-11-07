package com.neteinstein.goodcompany.startup

import com.neteinstein.goodcompany.core.domain.usecase.KeepNotificationsScheduledUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RunWeeklyIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Work that runs while the app is open:
 * - keep OS notifications in sync with the data,
 * - quietly refresh contacts (no-op without permission),
 * - catch up on the weekly AI ideas if they're due (the only trigger on iOS/web; Android also has WorkManager).
 */
class AppStartup(
    private val keepNotificationsScheduled: KeepNotificationsScheduledUseCase,
    private val syncContacts: SyncContactsUseCase,
    private val runWeeklyIdeas: RunWeeklyIdeasUseCase,
) {
    private var started = false

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        scope.launch { keepNotificationsScheduled() }
        scope.launch {
            runCatching { syncContacts() }
            runCatching { runWeeklyIdeas() }
        }
    }
}
