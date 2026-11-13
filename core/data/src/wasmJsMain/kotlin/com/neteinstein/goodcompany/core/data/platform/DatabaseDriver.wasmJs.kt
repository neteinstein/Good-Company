package com.neteinstein.goodcompany.core.data.platform

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import com.neteinstein.goodcompany.core.data.db.DatabaseDriverFactory
import com.neteinstein.goodcompany.core.data.db.OpenedDriver
import org.koin.core.scope.Scope
import org.w3c.dom.Worker

/**
 * SQLite in a web worker, stored in the Origin Private File System. The worker is our own
 * (`src/wasmJsMain/sqlite-worker/sqlite.worker.js`, the official SQLite WebAssembly build with the OPFS "SAH pool"
 * VFS, which needs no COOP/COEP headers), because the one bundled with SQLDelight keeps the database in memory.
 *
 * Unlike the Android and iOS drivers, the web driver doesn't create or migrate the schema itself; this does, using
 * `PRAGMA user_version`, inside one transaction.
 */
actual fun Scope.createDatabaseDriverFactory(): DatabaseDriverFactory = DatabaseDriverFactory { schema ->
    val driver = WebWorkerDriver(sqliteWorker())
    val version = driver.userVersion()
    if (version < schema.version) {
        driver.inTransaction {
            if (version == 0L) {
                schema.create(driver).await()
            } else {
                schema.migrate(driver, version, schema.version).await()
            }
            driver.execute(null, "PRAGMA user_version = ${schema.version}", 0).await()
        }
    }
    OpenedDriver(driver, isPersistent = driver.mainDatabaseFile().isNotEmpty())
}

private suspend fun SqlDriver.userVersion(): Long = executeQuery(
    identifier = null,
    sql = "PRAGMA user_version",
    mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
    parameters = 0,
).await()

/** The main database's file path; empty for the in-memory fallback the worker uses when OPFS is unavailable. */
private suspend fun SqlDriver.mainDatabaseFile(): String = executeQuery(
    identifier = null,
    sql = "SELECT file FROM pragma_database_list WHERE name = 'main'",
    mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getString(0).orEmpty() else "") },
    parameters = 0,
).await()

private suspend fun SqlDriver.inTransaction(body: suspend () -> Unit) {
    object : SuspendingTransacterImpl(this) {}.transaction { body() }
}

/** Webpack bundles the worker (a local npm package, see core/data/build.gradle.kts) and its SQLite .wasm asset. */
private fun sqliteWorker(): Worker =
    js("""new Worker(new URL("good-company-sqlite-worker/sqlite.worker.js", import.meta.url), { type: "module" })""")
