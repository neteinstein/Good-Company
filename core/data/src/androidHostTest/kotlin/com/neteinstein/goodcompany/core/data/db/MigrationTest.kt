package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
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
import kotlin.time.Instant

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

    @Test
    fun version4to5_keepsOldInteractions_withStableIds_andCallLogSyncStaysIdempotent() = runTest {
        val driver = driverAt(4)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', NULL, 1, 0, '', 0, 0, 0, 0)
            """.trimIndent(),
            0,
        ).await()
        driver.execute(null, "INSERT INTO interaction VALUES ('ana', 'CALL', 1700000000000)", 0).await()
        driver.execute(null, "INSERT INTO interaction VALUES ('ana', 'WHATSAPP', 1700000100000)", 0).await()
        driver.execute(null, "INSERT INTO hangout VALUES ('h', 0, 1700000200000, 'Café', NULL, NULL, 'Lunch')", 0).await()

        GoodCompanyDatabase.Schema.migrate(driver, 4, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val ana = FriendId("ana")
        val call = Instant.fromEpochMilliseconds(1700000000000)
        val chat = Instant.fromEpochMilliseconds(1700000100000)
        assertEquals(
            listOf(
                Interaction(
                    ana,
                    InteractionType.CALL,
                    call,
                    Interaction.automaticId(ana, InteractionType.CALL, call),
                    source = InteractionSource.CALL_LOG,
                ),
                Interaction(
                    ana,
                    InteractionType.WHATSAPP,
                    chat,
                    Interaction.automaticId(ana, InteractionType.WHATSAPP, chat),
                    source = InteractionSource.CHAT_IMPORT,
                ),
            ),
            database.readInteractions(),
        )
        assertNull(database.readHangouts().single().activity)
        assertTrue(database.readJournal().isEmpty())

        // The same call read again from the call log (as a sync after the update does) is not stored twice.
        database.writeInteraction(Interaction(ana, InteractionType.CALL, call, "other-id", source = InteractionSource.CALL_LOG, durationSeconds = 60))
        assertEquals(2, database.readInteractions().size)
        // New types and a manual log at the same time are fine.
        database.writeInteraction(Interaction(ana, InteractionType.VIDEO_CALL, call, "v1", note = "Gerês"))
        database.writeInteraction(Interaction(ana, InteractionType.CALL, call, "c1"))
        assertEquals(4, database.readInteractions().size)
        database.writeHangout(Hangout("h2", listOf(ana), chat, activity = HangoutActivity.LUNCH))
        assertEquals(HangoutActivity.LUNCH, database.readHangouts().single { it.id == "h2" }.activity)
        driver.close()
    }

    @Test
    fun version5to6_givesExistingCirclesColoursInTurn_andHomeStartsOnAll() = runTest {
        val driver = driverAt(5)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', NULL, 1, 0, '', 0, 0, 0, 0)
            """.trimIndent(),
            0,
        ).await()
        // Nine groups, inserted out of order: colours follow position, and wrap around after seven.
        (8 downTo 0).forEach { i ->
            val every = if (i == 0) "30" else "NULL"
            driver.execute(null, "INSERT INTO friend_group VALUES ('g$i', $i, 'Group $i', '👥', $every, NULL)", 0).await()
        }
        driver.execute(null, "INSERT INTO group_member VALUES ('g0', 'ana', 0)", 0).await()
        driver.execute(
            null,
            "INSERT INTO user_preferences VALUES (1, 7, 30, 90, 1, 6, 9, 1, 'Lisbon', 1, 0)",
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 5, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val groups = database.readGroups()

        assertEquals(
            listOf(
                CircleColor.PURPLE, CircleColor.BLUE, CircleColor.TEAL, CircleColor.AMBER, CircleColor.RUST,
                CircleColor.MAGENTA, CircleColor.SLATE, CircleColor.PURPLE, CircleColor.BLUE,
            ),
            groups.sortedBy { it.id }.map { it.color },
        )
        // An existing group keeps its rhythm and members.
        val first = groups.single { it.id == "g0" }
        assertEquals(30, first.meetEveryDays)
        assertEquals(listOf(FriendId("ana")), first.memberIds)
        val settings = database.readPreferences(AppSettings())
        assertNull(settings.homeCircleFilter)
        assertEquals("Lisbon", settings.homeArea)

        // Both new columns round-trip.
        database.writeGroup(first.copy(color = CircleColor.TEAL))
        assertEquals(CircleColor.TEAL, database.readGroups().single { it.id == "g0" }.color)
        database.writePreferences(settings.copy(homeCircleFilter = "g0"))
        assertEquals("g0", database.readPreferences(AppSettings()).homeCircleFilter)
        driver.close()
    }
}
