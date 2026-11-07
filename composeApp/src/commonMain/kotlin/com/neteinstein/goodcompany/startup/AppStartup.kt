package com.neteinstein.goodcompany.startup

import com.neteinstein.goodcompany.core.domain.usecase.KeepNotificationsScheduledUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RecordAppOpenedUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RunWeeklyIdeasUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SyncContactsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Work that runs while the app is open:
 * - keep OS notifications in sync with the data,
 * - quietly refresh contacts (no-op without permission),
 * - remember that the app was opened (ignored nudges get quieter),
 * - catch up on the weekly AI ideas if they're due (the only trigger on iOS/web; Android also has WorkManager).
 */
class AppStartup(
    private val keepNotificationsScheduled: KeepNotificationsScheduledUseCase,
    private val syncContacts: SyncContactsUseCase,
    private val runWeeklyIdeas: RunWeeklyIdeasUseCase,
    private val recordAppOpened: RecordAppOpenedUseCase,
) {
    private var job: Job? = null

    /**
     * Starts the work in [scope] (the root ViewModel's scope). Safe to call repeatedly: it's a no-op while a previous
     * run is still alive, and restarts when the app UI is re-created after the old scope was cancelled.
     */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            // Lets catch-up nudges back off when the app isn't being opened (see NudgePolicy).
            runCatching { recordAppOpened() }
            launch { keepNotificationsScheduled() }
            launch {
                runCatching { syncContacts() }
                runCatching { runWeeklyIdeas() }
            }
        }
    }
}
