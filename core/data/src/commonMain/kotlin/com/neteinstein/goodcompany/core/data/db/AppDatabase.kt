package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.neteinstein.goodcompany.core.domain.repository.StorageStatus
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

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
 *
 * There is exactly one per driver: the lock below is what keeps statements from interleaving.
 *
 * **Every read and write goes through [access] / [transaction]**, one at a time. On the web each statement is a
 * message to the worker and really suspends, and the driver tracks a single open transaction, so two coroutines
 * interleaving would run one's statements inside the other's transaction. Blocks must not call [access] again (the
 * lock isn't reentrant) and should not suspend on anything but the database: on Android and iOS the drivers are
 * synchronous, so a block runs on one thread from start to end.
 *
 * [prepare] runs once right after opening, before any other access (the one-off JSON import). It gets the database
 * directly and must not call back into this class.
 */
class AppDatabase(
    private val factory: DatabaseDriverFactory,
    private val clock: AppClock,
    private val context: CoroutineContext = Dispatchers.Default,
    private val prepare: suspend (database: GoodCompanyDatabase, isPersistent: Boolean) -> Unit = { _, _ -> },
) : StorageStatus {
    private val scope = CoroutineScope(SupervisorJob() + context)
    private val openLock = Mutex()
    private val accessLock = Mutex()
    private var opened: Opened? = null

    suspend fun get(): GoodCompanyDatabase = open().database

    /** False when data written now won't survive a restart (web without OPFS, see [OpenedDriver.isPersistent]). */
    override suspend fun isPersistent(): Boolean = open().isPersistent

    /** When this database file was first created on this device (epoch milliseconds). */
    suspend fun createdAt(): Long? = access { it.dbMetaQueries.get(KEY_CREATED_AT).awaitAsOneOrNull()?.toLongOrNull() }

    /** Runs [block] with the database to itself. */
    suspend fun <T> access(block: suspend (GoodCompanyDatabase) -> T): T {
        val database = get()
        return withContext(context) { accessLock.withLock { block(database) } }
    }

    /** Runs [block] in one transaction: all of it is written, or none of it (it rolls back when [block] throws). */
    suspend fun <T> transaction(block: suspend (GoodCompanyDatabase) -> T): T = access { database ->
        database.transactionWithResult { block(database) }
    }

    /**
     * Emits [load]'s result now and again whenever one of [tables] changes (after the writing transaction commits).
     * Changes that land while a load runs are folded into one reload; equal results aren't emitted twice.
     *
     * Collectors share one live query (a repository calls this once per flow, and several screens and services
     * watch the same data). A shared result is never handed out once a later write has committed: every change bumps
     * [version] synchronously inside the commit, and results loaded before it are skipped. So `flow.first()` right
     * after a write always sees that write, as it did with the JSON stores.
     */
    fun <T> observe(vararg tables: String, load: suspend (GoodCompanyDatabase) -> T): Flow<T> {
        val version = MutableStateFlow(0L)
        val shared = callbackFlow {
            val driver = open().driver
            val listener = Query.Listener {
                version.update { it + 1 }
                trySend(Unit)
            }
            driver.addListener(*tables, listener = listener)
            send(Unit)
            awaitClose { driver.removeListener(*tables, listener = listener) }
        }.conflate()
            .map {
                val loadedAt = version.value
                Versioned(loadedAt, transaction(load))
            }
            .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000, replayExpirationMillis = 0), replay = 1)
        return shared.filter { it.version == version.value }.map { it.value }.distinctUntilChanged()
    }

    /** Stops every shared query. Only for tests that open a second [AppDatabase] over the same driver. */
    fun close() = scope.cancel()

    private class Versioned<T>(val version: Long, val value: T)

    private suspend fun open(): Opened = openLock.withLock {
        opened ?: run {
            val result = factory.open(GoodCompanyDatabase.Schema)
            val database = GoodCompanyDatabase(result.driver)
            database.dbMetaQueries.putIfAbsent(KEY_CREATED_AT, clock.now().toEpochMilliseconds().toString())
            withContext(context) { prepare(database, result.isPersistent) }
            Opened(database, result.driver, result.isPersistent).also { opened = it }
        }
    }

    private class Opened(val database: GoodCompanyDatabase, val driver: SqlDriver, val isPersistent: Boolean)

    companion object {
        const val KEY_CREATED_AT = "created_at"
    }
}
