package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.neteinstein.goodcompany.core.domain.model.MetInfo
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.Properties
import kotlin.io.path.createTempFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Migrations run against the committed schema snapshots (`src/commonMain/sqldelight/databases/<n>.db`), with rows written
 * the way the older app wrote them.
 */
class MigrationTest {
    private val copies = mutableListOf<File>()

    @AfterTest
    fun cleanUp() = copies.forEach { it.delete() }

    /** A throwaway copy of the schema snapshot for [version], opened with foreign keys on (like the app). */
    private fun driverAt(version: Int): JdbcSqliteDriver {
        val copy = createTempFile("goodcompany-v$version", ".db").toFile()
        File("src/commonMain/sqldelight/databases/$version.db").copyTo(copy, overwrite = true)
        copies += copy
        return JdbcSqliteDriver("jdbc:sqlite:${copy.absolutePath}", Properties().apply { put("foreign_keys", "true") })
    }

    @Test
    fun version3to4_keepsLikesAndDislikes_andTheNewFieldsStartEmpty() = runTest {
        val driver = driverAt(3)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', 'c1', 1, 0, 'Met at uni', 0, 0, 0, 0)
            """.trimIndent(),
            0,
        ).await()
        driver.execute(null, "INSERT INTO friend_taste VALUES ('ana', 'LIKE', 0, 'jazz', 1700000000000)", 0).await()
        driver.execute(null, "INSERT INTO friend_taste VALUES ('ana', 'LIKE', 1, 'sushi', NULL)", 0).await()
        driver.execute(null, "INSERT INTO friend_taste VALUES ('ana', 'DISLIKE', 0, 'cilantro', NULL)", 0).await()
        driver.execute(null, "INSERT INTO kid VALUES ('ana', 'k1', 0, 'Leo', 5, 1, 2018, NULL)", 0).await()

        GoodCompanyDatabase.Schema.migrate(driver, 3, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val ana = database.readFriends().single()

        assertEquals(listOf("jazz", "sushi"), ana.likes)
        assertEquals(listOf("cilantro"), ana.dislikes)
        assertEquals(setOf("jazz"), ana.likesAddedAt.keys)
        assertEquals(listOf("Leo"), ana.kids.map { it.name })
        assertEquals("Met at uni", ana.notes)
        assertEquals("", ana.job)
        assertEquals("", ana.livesIn)
        assertNull(ana.met)
        assertNull(ana.introducedBy)
        assertNull(ana.preferredChannel)
        assertTrue(ana.emails.isEmpty() && ana.relatives.isEmpty() && ana.customFields.isEmpty())
        assertTrue(ana.food.isEmpty() && ana.drinks.isEmpty() && ana.interests.isEmpty())

        // The rebuilt taste table takes the new kinds, and the friend round-trips with them.
        val edited = ana.copy(food = listOf("ramen"), interests = listOf("climbing"), met = MetInfo("Uni", null))
        database.writeFriend(edited, 0)
        assertEquals(edited, database.readFriends().single())
        driver.close()
    }
}
