package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull

/**
 * Smoke test for the OPFS-backed SQLite worker (web `?dbcheck`): counts one more open in the database and describes
 * it. Loading the page twice must show the count going up, with the same `created` time, when the browser keeps the data.
 */
suspend fun AppDatabase.smokeCheck(): String {
    val queries = get().dbMetaQueries
    val opens = (queries.get(KEY_OPENS).awaitAsOneOrNull()?.toLongOrNull() ?: 0L) + 1
    queries.put(KEY_OPENS, opens.toString())
    return "persistent=${isPersistent()} created=${createdAt()} opens=$opens"
}

private const val KEY_OPENS = "dbcheck.opens"
