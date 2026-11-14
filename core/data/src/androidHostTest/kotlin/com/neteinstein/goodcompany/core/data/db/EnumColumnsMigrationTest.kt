package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.GiftStatus
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Relation
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
 * Migration `16.sqm` (issue #72): every enum column loses its CHECK. The tables are rebuilt with foreign keys on (as
 * the apps migrate), so nothing that references a rebuilt table may lose a row; afterwards unknown names can be stored
 * and every mapper reads them as its fallback or skips the row.
 */
class EnumColumnsMigrationTest {
    private val copies = mutableListOf<File>()

    @AfterTest
    fun cleanUp() = copies.forEach { it.delete() }

    private fun driverAt(version: Int): JdbcSqliteDriver {
        val copy = createTempFile("goodcompany-v$version", ".db").toFile()
        File("src/commonMain/sqldelight/databases/$version.db").copyTo(copy, overwrite = true)
        copies += copy
        return JdbcSqliteDriver("jdbc:sqlite:${copy.absolutePath}", Properties().apply { put("foreign_keys", "true") })
    }

    private suspend fun JdbcSqliteDriver.exec(sql: String) {
        execute(null, sql, 0).await()
    }

    private suspend fun JdbcSqliteDriver.strings(sql: String): List<String> = executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            val out = mutableListOf<String>()
            while (cursor.next().value) out += cursor.getString(0).orEmpty()
            QueryResult.Value(out)
        },
        parameters = 0,
    ).await()

    /** Every row of every table, as quoted values, sorted: equal dumps mean no row was lost or changed. */
    private suspend fun JdbcSqliteDriver.dump(): Map<String, List<String>> =
        strings("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name").associateWith { table ->
            val columns = strings("SELECT name FROM pragma_table_info('$table') ORDER BY name")
            strings("SELECT ${columns.joinToString(" || '|' || ") { "quote($it)" }} FROM $table").sorted()
        }

    /** One row in every table at version 16, all hanging off Ana. */
    private suspend fun JdbcSqliteDriver.fillVersion16() {
        exec(
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, circle, catch_up_paused, contact_counts_as_catch_up, job, relation_to_me, show_in_my_list)
            VALUES ('ana', 0, 'Ana', 'c1', 1, 0, 'uni', 0, 0, 'INNER', 0, 0, 'Nurse', 'SIBLING', 1),
                   ('rui', 1, 'Rui', NULL, 0, 0, '', 0, 0, NULL, 0, 0, '', NULL, 1)
            """.trimIndent(),
        )
        listOf(
            "INSERT INTO friend_phone VALUES ('ana', 0, '+351 912 345 678', '912345678')",
            "INSERT INTO friend_taste VALUES ('ana', 'FOOD', 0, 'sushi', 1700000000000)",
            "INSERT INTO friend_taste VALUES ('ana', 'LIKE', 0, 'jazz', NULL)",
            "INSERT INTO friend_occasion VALUES ('ana', 'COFFEE', 0)",
            "INSERT INTO important_event VALUES ('ana', 'e1', 0, 'Graduation', '2026-07-01', 3, NULL)",
            "INSERT INTO gift VALUES ('ana', 'g1', 0, 'Book', '', NULL, 'BIRTHDAY', NULL, NULL)",
            "INSERT INTO life_update VALUES ('ana', 'u1', 0, 'New job', 1700000000000)",
            "INSERT INTO feed VALUES ('ana', 'https://ana.example/feed', 0, 'Ana', NULL, NULL)",
            "INSERT INTO feed_item VALUES ('ana', 'https://ana.example/feed', 'i1', 0, 'Post', NULL, NULL)",
            "INSERT INTO friend_photo VALUES ('ana', 'AAAA')",
            "INSERT INTO friend_custom_photo VALUES ('ana', 'BBBB')",
            "INSERT INTO friend_email VALUES ('ana', 0, 'ana@example.com', 1)",
            "INSERT INTO relative VALUES ('ana', 'r1', 0, 'PET', 'Bolinhas', NULL, NULL, NULL, '', 0, NULL)",
            "INSERT INTO relative_like VALUES ('ana', 'r1', 0, 'balls')",
            "INSERT INTO custom_field VALUES ('ana', 0, 'cf1', 'Shoe size', '38')",
            "INSERT INTO friend_group (id, position, name, emoji, color) VALUES ('crew', 0, 'Crew', '🎓', 'PURPLE')",
            "INSERT INTO group_member VALUES ('crew', 'ana', 0)",
            "INSERT INTO gift_list VALUES ('xmas', 'Christmas', 'CHRISTMAS', '2026-12-25', 1700000000000)",
            "INSERT INTO gift_list_entry VALUES ('xmas', 'ana', 30.0, 0)",
            "INSERT INTO gift_list_item VALUES ('xmas', 'ana', 'g1', 'BOUGHT', 0)",
            "INSERT INTO hangout (id, position, at, note) VALUES ('h1', 0, 1700000000000, '')",
            "INSERT INTO hangout_friend VALUES ('h1', 'ana', 0)",
            "INSERT INTO idea VALUES ('i1', 0, 'EVENT', 'Concert', '', NULL, NULL, 1700000000000, 0, NULL)",
            "INSERT INTO idea_friend VALUES ('i1', 'ana', 0)",
            "INSERT INTO interaction VALUES ('x1', 'ana', 'CALL', 1700000000000, '', 'CALL_LOG', 60, NULL, NULL)",
            "INSERT INTO conversation_message VALUES ('ana', 1700000000000, 1, 'hi')",
            "INSERT INTO journal_entry VALUES ('j1', 'ana', 1700000000000, 'note')",
            "INSERT INTO plan (id, position, title, date, idea_id, status, created_at) VALUES ('p1', 0, 'Concert', '2026-12-01', 'i1', 'PLANNED', 1)",
            "INSERT INTO plan_friend VALUES ('p1', 'ana', 0)",
            "INSERT INTO plan_skip VALUES ('p1', '2026-12-08')",
            "INSERT INTO reminder VALUES ('rem', 0, 'Call', NULL, 0, 1700000000000)",
            "INSERT INTO reminder_friend VALUES ('rem', 'ana', 0)",
            "INSERT INTO attachment (id, friend_id, kind, text, at, created_at) VALUES ('a1', 'ana', 'TEXT', 'hello', 1, 1)",
            "INSERT INTO person_link VALUES ('l1', 'rui', 'ana', 'SPOUSE', NULL, NULL, '', 0, NULL)",
            "INSERT INTO calendar_decision VALUES ('k1', 'NEVER', 1700000000000)",
        ).forEach { exec(it) }
    }

    @Test
    fun version16to17_keepsEveryRow_withForeignKeysOn() = runTest {
        val driver = driverAt(16)
        driver.fillVersion16()
        val before = driver.dump()
        assertTrue(before.filterKeys { it != "db_meta" && it != "user_preferences" && it != "named_place" }.values.all { it.isNotEmpty() })

        GoodCompanyDatabase.Schema.migrate(driver, 16, GoodCompanyDatabase.Schema.version).await()

        assertEquals(before, driver.dump())
        assertTrue(driver.strings("SELECT \"table\" FROM pragma_foreign_key_check").isEmpty())
        assertTrue(driver.strings("SELECT name FROM sqlite_master WHERE name LIKE 'keep_%'").isEmpty())
        // The plan still points at its idea (dropping idea would have set it to null).
        assertEquals(listOf("i1"), driver.strings("SELECT idea_id FROM plan"))

        // Cascades still work on the rebuilt tables.
        val database = GoodCompanyDatabase(driver)
        database.friendQueries.delete("ana")
        val left = driver.dump().filterValues { it.isNotEmpty() }.keys
        assertEquals(setOf("friend", "friend_group", "gift_list", "hangout", "idea", "plan", "plan_skip", "reminder", "calendar_decision"), left)
        driver.close()
    }

    @Test
    fun unknownEnumNames_canBeStored_andReadAsTheirFallback() = runTest {
        val driver = driverAt(16)
        driver.fillVersion16()
        GoodCompanyDatabase.Schema.migrate(driver, 16, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        assertEquals(Closeness.INNER, database.readFriends().first { it.id.value == "ana" }.closeness)

        listOf(
            "UPDATE friend SET circle = 'BEST' WHERE id = 'ana'",
            "INSERT INTO friend_taste VALUES ('ana', 'HOBBY', 0, 'chess', NULL)",
            "INSERT INTO friend_occasion VALUES ('ana', 'BRUNCH', 1)",
            "UPDATE gift SET occasion = 'EASTER'",
            "UPDATE relative SET relation = 'GODPARENT'",
            "UPDATE plan SET status = 'MAYBE'",
            "UPDATE gift_list SET occasion = 'EASTER'",
            "UPDATE gift_list_item SET status = 'WRAPPED'",
            "INSERT INTO interaction VALUES ('x2', 'ana', 'FAX', 2, '', 'MANUAL', NULL, NULL, NULL)",
            "INSERT INTO interaction VALUES ('x3', 'ana', 'CALL', 3, '', 'PIGEON', NULL, NULL, NULL)",
            "INSERT INTO attachment (id, friend_id, kind, at, created_at) VALUES ('a2', 'ana', 'VIDEO', 2, 2)",
            """
            INSERT INTO attachment (id, friend_id, kind, photo_platform, photo_ref, at, counts_as_interaction, interaction_type, created_at)
            VALUES ('a3', 'ana', 'PHOTO', 'WEB', 'ref', 3, 1, 'FAX', 3)
            """.trimIndent(),
            "INSERT INTO idea VALUES ('i2', 1, 'GAME', 'Board games', '', NULL, NULL, 2, 0, NULL)",
            "INSERT INTO person_link VALUES ('l2', 'ana', 'rui', 'BOSS', NULL, NULL, '', 0, NULL)",
            "INSERT INTO calendar_decision VALUES ('k2', 'MAYBE', 2)",
        ).forEach { driver.exec(it) }

        val ana = database.readFriends().first { it.id.value == "ana" }
        assertNull(ana.closeness) // closeness → not set
        assertEquals(listOf("chess", "jazz"), ana.likes.sorted()) // taste kind → like
        assertEquals(listOf("sushi"), ana.food)
        assertEquals(listOf(Occasion.COFFEE), ana.occasions) // occasion → left out
        assertNull(ana.gifts.single().occasion) // gift occasion → none
        assertEquals(Relation.OTHER, ana.relatives.single().relation) // relation → other
        assertEquals(PlanStatus.PLANNED, database.readPlans().single().status) // plan status → planned
        val list = database.readGiftLists().single()
        assertNull(list.occasion) // gift list occasion → none
        assertEquals(GiftStatus.TO_BUY, list.entries.single().items.single().status) // item status → to buy
        assertEquals(listOf("x1"), database.readInteractions().map { it.id }) // unknown type or source → skipped
        val attachments = database.readAttachments()
        assertEquals(listOf("a1", "a3"), attachments.map { it.id }.sorted()) // unknown kind → skipped
        val photo = attachments.first { it.id == "a3" }
        assertEquals(AttachmentKind.PHOTO, photo.kind)
        assertNull(photo.photoRef) // unknown platform → no photo reference
        assertNull(photo.interactionType) // unknown interaction type → none
        assertEquals(listOf("i1"), database.readIdeas().map { it.id }) // unknown kind → skipped
        assertEquals(listOf("l1"), database.readLinks().map { it.id }) // unknown type → skipped
        assertEquals(listOf("k1"), database.readCalendarDecisions().map { it.key }) // unknown kind → skipped

        // Skipped rows stay in their tables.
        assertEquals(3, driver.strings("SELECT id FROM interaction").size)
        assertEquals(listOf("GAME"), driver.strings("SELECT kind FROM idea WHERE id = 'i2'"))
        driver.close()
    }
}
