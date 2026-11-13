package com.neteinstein.goodcompany

import com.neteinstein.goodcompany.core.data.db.AppDatabase
import com.neteinstein.goodcompany.core.data.db.smokeCheck
import org.koin.mp.KoinPlatform

/**
 * `index.html?dbcheck`: prints the SQLite/OPFS smoke test ([smokeCheck]) to the browser console. Prints the error
 * instead of throwing: it must never stop the app from starting.
 */
internal suspend fun runDatabaseCheck() {
    val result = runCatching { KoinPlatform.getKoin().get<AppDatabase>().smokeCheck() }
    println("[dbcheck] " + result.getOrElse { "failed: $it" })
}
