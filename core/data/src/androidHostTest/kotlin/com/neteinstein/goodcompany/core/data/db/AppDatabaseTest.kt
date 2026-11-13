package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.neteinstein.goodcompany.core.testing.FixedClock
import kotlinx.coroutines.test.runTest
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** JVM tests on SQLDelight's JDBC driver: same generated schema and queries as the app, in memory. */
class AppDatabaseTest {
    private val clock = FixedClock()

    /** Like the platform factories: creates the schema and turns foreign keys on. Counts how often it's asked to open. */
    private class InMemoryFactory(private val persistent: Boolean = true) : DatabaseDriverFactory {
        var opens = 0
        lateinit var driver: SqlDriver

        override suspend fun open(schema: app.cash.sqldelight.db.SqlSchema<QueryResult.AsyncValue<Unit>>): OpenedDriver {
            opens++
            driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
            schema.create(driver).await()
            return OpenedDriver(driver, persistent)
        }
    }

    @Test
    fun opensOnceAndRecordsWhenTheDatabaseWasCreated() = runTest {
        val factory = InMemoryFactory()
        val database = AppDatabase(factory, clock)

        val first = database.get()
        clock.instant += 3.days
        val second = database.get()

        assertSame(first, second)
        assertEquals(1, factory.opens)
        // Set once on first open; later opens never overwrite it.
        assertEquals(FixedClock().now().toEpochMilliseconds(), database.createdAt())
    }

    @Test
    fun metaValuesRoundTripAndPutIfAbsentKeepsTheFirstValue() = runTest {
        val queries = AppDatabase(InMemoryFactory(), clock).get().dbMetaQueries

        queries.put("flag", "1")
        queries.put("flag", "2")
        queries.putIfAbsent("flag", "3")

        assertEquals("2", queries.get("flag").awaitAsOneOrNull())
        assertEquals(null, queries.get("missing").awaitAsOneOrNull())
    }

    @Test
    fun transactionsRollBackOnFailure() = runTest {
        val database = AppDatabase(InMemoryFactory(), clock).get()

        runCatching {
            database.transaction {
                database.dbMetaQueries.put("half-written", "yes")
                error("boom")
            }
        }

        assertEquals(null, database.dbMetaQueries.get("half-written").awaitAsOneOrNull())
    }

    @Test
    fun foreignKeysAreEnforced() = runTest {
        val factory = InMemoryFactory()
        AppDatabase(factory, clock).get()

        val enabled = factory.driver.executeQuery(
            identifier = null,
            sql = "PRAGMA foreign_keys",
            mapper = { cursor -> QueryResult.Value(cursor.next().value && cursor.getLong(0) == 1L) },
            parameters = 0,
        ).value
        assertTrue(enabled)
    }

    @Test
    fun reportsWhetherTheDataSurvivesARestart() = runTest {
        assertTrue(AppDatabase(InMemoryFactory(persistent = true), clock).isPersistent())
        assertFalse(AppDatabase(InMemoryFactory(persistent = false), clock).isPersistent())
    }
}
