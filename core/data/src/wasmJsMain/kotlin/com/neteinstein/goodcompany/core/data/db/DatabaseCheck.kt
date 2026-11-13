package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.neteinstein.goodcompany.core.data.repository.LegacyJsonImport

/**
 * Smoke test for the OPFS-backed SQLite worker (web `?dbcheck`): counts one more open in the database and describes
 * it, plus the one-off JSON import's record. Loading the page twice must show the count going up, with the same `created` time, when the browser keeps the data.
 */
suspend fun AppDatabase.smokeCheck(): String {
    val (opens, legacy) = transaction { db ->
        val meta = db.dbMetaQueries
        val next = (meta.get(KEY_OPENS).awaitAsOneOrNull()?.toLongOrNull() ?: 0L) + 1
        meta.put(KEY_OPENS, next.toString())
        val imported = meta.get(LegacyJsonImport.KEY_IMPORTED).awaitAsOneOrNull()
        val failed = meta.get(LegacyJsonImport.KEY_FAILED).awaitAsOneOrNull()
        next to (failed?.let { "failed: $it" } ?: imported ?: "not yet")
    }
    return "persistent=${isPersistent()} created=${createdAt()} opens=$opens jsonImport=[$legacy]"
}

private const val KEY_OPENS = "dbcheck.opens"
