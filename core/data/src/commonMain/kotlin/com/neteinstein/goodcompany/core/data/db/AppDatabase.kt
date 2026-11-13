package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** File name of the SQLite database on Android and iOS (and the OPFS file name on the web). */
const val DATABASE_FILE_NAME = "goodcompany.db"

/**
 * Opens the platform's SQLite driver with the schema created or migrated and foreign keys on.
 * Built by `createDatabaseDriverFactory()` (expect/actual in `platform/`) inside the shared Koin module.
 */
fun interface DatabaseDriverFactory {
    suspend fun open(schema: SqlSchema<QueryResult.AsyncValue<Unit>>): OpenedDriver
}

/**
 * [isPersistent] is false only on the web when the browser can't give us OPFS storage (some private-browsing
 * modes, old browsers, or the database already open in another tab): the worker then falls back to an in-memory
 * database, and anything written is lost when the page closes.
 */
class OpenedDriver(val driver: SqlDriver, val isPersistent: Boolean)

/**
 * The app's single SQLite database. It is opened lazily, once, on the first call: opening is asynchronous on the web
 * (the worker has to start and attach OPFS), so callers suspend instead of Koin building it eagerly.
 */
class AppDatabase(private val factory: DatabaseDriverFactory, private val clock: AppClock) {
    private val mutex = Mutex()
    private var opened: Opened? = null

    suspend fun get(): GoodCompanyDatabase = open().database

    /** False when data written now won't survive a restart (web without OPFS, see [OpenedDriver.isPersistent]). */
    suspend fun isPersistent(): Boolean = open().isPersistent

    /** When this database file was first created on this device (epoch milliseconds). */
    suspend fun createdAt(): Long? = get().dbMetaQueries.get(KEY_CREATED_AT).awaitAsOneOrNull()?.toLongOrNull()

    private suspend fun open(): Opened = mutex.withLock {
        opened ?: run {
            val result = factory.open(GoodCompanyDatabase.Schema)
            val database = GoodCompanyDatabase(result.driver)
            database.dbMetaQueries.putIfAbsent(KEY_CREATED_AT, clock.now().toEpochMilliseconds().toString())
            Opened(database, result.isPersistent).also { opened = it }
        }
    }

    private class Opened(val database: GoodCompanyDatabase, val isPersistent: Boolean)

    companion object {
        const val KEY_CREATED_AT = "created_at"
    }
}
