package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftList
import com.neteinstein.goodcompany.core.domain.model.GiftListEntry
import com.neteinstein.goodcompany.core.domain.model.GiftListItem
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.GiftStatus
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.MetInfo
import com.neteinstein.goodcompany.core.domain.model.PersonLink
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.model.Repeat
import com.neteinstein.goodcompany.core.domain.model.RepeatUnit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
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
        val ana = database.readFriends().first { it.id.value == "ana" }

        assertEquals(listOf("jazz", "sushi"), ana.likes)
        assertEquals(listOf("cilantro"), ana.dislikes)
        assertEquals(setOf("jazz"), ana.likesAddedAt.keys)
        // Since version 10 the kid is a person of their own (issue #40).
        assertEquals("Leo", database.readFriends().single { it.id.value == "k1" }.name)
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
        assertEquals(edited, database.readFriends().first { it.id.value == "ana" })
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

    @Test
    fun version6to7_keepsGifts_andAttachmentsGoWithTheirFriendOrCircle() = runTest {
        val driver = driverAt(6)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', NULL, 1, 0, '', 0, 0, 0, 0)
            """.trimIndent(),
            0,
        ).await()
        driver.execute(null, "INSERT INTO gift VALUES ('ana', 'g1', 0, 'Book', 'Murakami', NULL, NULL, NULL)", 0).await()
        driver.execute(null, "INSERT INTO friend_group VALUES ('crew', 0, 'Crew', '👥', NULL, NULL, 'TEAL')", 0).await()
        driver.execute(null, "INSERT INTO group_member VALUES ('crew', 'ana', 0)", 0).await()

        GoodCompanyDatabase.Schema.migrate(driver, 6, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val ana = database.readFriends().single()
        assertEquals(Gift("g1", "Book", "Murakami"), ana.gifts.single())
        database.writeFriend(ana.copy(gifts = listOf(Gift("g1", "Lego", url = "https://shop.example/l", price = "€35"))), 0)
        assertEquals("€35", database.readFriends().single().gifts.single().price)

        val mine = Attachment("a1", friendId = ana.id, kind = AttachmentKind.TEXT, text = "hi", at = Instant.fromEpochSeconds(1))
        val ours = Attachment("a2", circleId = "crew", kind = AttachmentKind.LINK, url = "https://x.y", at = Instant.fromEpochSeconds(2))
        database.writeAttachment(mine)
        database.writeAttachment(ours)
        database.writeAttachment(mine.copy(id = "lost", friendId = FriendId("nobody")))
        assertEquals(listOf(mine, ours), database.readAttachments())
        // Neither or both owners is refused by the schema.
        assertTrue(
            runCatching {
                driver.execute(null, "INSERT INTO attachment (id, kind, at, created_at) VALUES ('x', 'TEXT', 1, 1)", 0).await()
            }.isFailure,
        )
        database.friendGroupQueries.delete("crew")
        assertEquals(listOf(mine), database.readAttachments())
        database.friendQueries.delete("ana")
        assertTrue(database.readAttachments().isEmpty())
        driver.close()
    }

    @Test
    fun version8to9_addsLinks_andFriendsStartWithNoRelationToMe() = runTest {
        val driver = driverAt(8)
        listOf("ana", "rui").forEachIndexed { i, id ->
            driver.execute(
                null,
                """
                INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                    is_orphaned, catch_up_paused, contact_counts_as_catch_up)
                VALUES ('$id', $i, '$id', NULL, 1, 0, '', 0, 0, 0, 0)
                """.trimIndent(),
                0,
            ).await()
        }

        GoodCompanyDatabase.Schema.migrate(driver, 8, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val friends = database.readFriends()
        assertTrue(friends.all { it.relationToMe == null && it.pronouns == null })
        database.writeFriend(friends.first().copy(relationToMe = LinkType.SIBLING, pronouns = Pronouns.SHE), 0)
        assertEquals(LinkType.SIBLING, database.readFriends().first().relationToMe)

        val link = PersonLink("l1", FriendId("rui"), FriendId("ana"), LinkType.SPOUSE, LocalDate(2015, 7, 4), remindAnniversary = true)
        database.writeLink(link)
        assertEquals(listOf(link), database.readLinks())
        // An unknown type can be stored since 16.sqm (issue #72) and is skipped when read.
        driver.execute(null, "INSERT INTO person_link (id, from_friend_id, to_friend_id, type) VALUES ('x', 'ana', 'rui', 'BOSS')", 0).await()
        assertEquals(listOf(link), database.readLinks())
        database.friendQueries.delete("ana")
        assertTrue(database.readLinks().isEmpty())
        driver.close()
    }

    @Test
    fun version9to10_turnsBirthdayRemindersOnForEveryoneWithABirthday_andAddsTheAppsNameAndPhoto() = runTest {
        val driver = driverAt(9)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, birthday_month, birthday_day, birthday_year, is_close_friend,
                notify_birthday, notes, suggest_events, is_orphaned, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana Silva', 'c1', 3, 14, NULL, 0, 0, '', 0, 0, 0, 0),
                   ('rui', 1, 'Rui', NULL, NULL, NULL, NULL, 0, 0, '', 0, 0, 0, 0)
            """.trimIndent(),
            0,
        ).await()
        driver.execute(null, "INSERT INTO friend_photo VALUES ('ana', 'AQI=')", 0).await()

        GoodCompanyDatabase.Schema.migrate(driver, 9, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val (ana, rui) = database.readFriends()
        assertTrue(ana.notifyBirthday, "on for a friend with a birthday (F10)")
        assertEquals(Birthday(3, 14, null), ana.birthday)
        assertEquals(false, rui.notifyBirthday, "a friend without a birthday is left as they were")
        assertNull(ana.customName)
        assertNull(ana.customPhoto)

        val mine = ContactPhoto("Aw==")
        database.writeFriend(ana.copy(customName = "Aninhas", customPhoto = mine), 0)
        val saved = database.readFriends().first()
        assertEquals("Aninhas", saved.displayName)
        assertEquals(ContactPhoto("AQI="), saved.photo)
        assertEquals(mine, saved.avatar)
        database.writeFriend(saved.copy(customPhoto = null), 0)
        assertEquals(ContactPhoto("AQI="), database.readFriends().first().avatar)
        database.friendQueries.delete("ana")
        assertEquals(listOf("rui"), database.readFriends().map { it.id.value })
    }

    @Test
    fun version10to11_turnsKidsIntoPeople_keepingIdsLikesAndGifts() = runTest {
        val driver = driverAt(10)
        suspend fun friendRow(id: String, position: Int, relation: String?, notify: Int) = driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, catch_up_paused, contact_counts_as_catch_up, relation_to_me)
            VALUES ('$id', $position, '$id', NULL, 0, $notify, '', 0, 0, 0, 0, ${relation?.let { "'$it'" } ?: "NULL"})
            """.trimIndent(),
            0,
        ).await()
        friendRow("ana", 0, "SIBLING", 1)
        friendRow("rui", 1, null, 0)
        friendRow("dup", 2, null, 0)
        // Ana (family) has Leo; Rui (a friend) has a kid whose id another person already has.
        driver.execute(null, "INSERT INTO kid VALUES ('ana', 'leo', 0, 'Leo', 5, 1, 2018, 1700000000000)", 0).await()
        driver.execute(null, "INSERT INTO kid_like VALUES ('ana', 'leo', 0, 'dinosaurs')", 0).await()
        driver.execute(null, "INSERT INTO kid VALUES ('rui', 'dup', 0, 'Mia', NULL, NULL, NULL, NULL)", 0).await()
        driver.execute(null, "INSERT INTO gift VALUES ('rui', 'g1', 0, 'Lego', '', 'dup', NULL, NULL, NULL, '€35')", 0).await()
        driver.execute(null, "INSERT INTO gift VALUES ('rui', 'g2', 1, 'Book', '', NULL, NULL, NULL, NULL, NULL)", 0).await()

        GoodCompanyDatabase.Schema.migrate(driver, 10, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val people = database.readFriends().associateBy { it.id.value }

        val leo = people.getValue("leo")
        assertEquals("Leo", leo.name)
        assertEquals(listOf("dinosaurs"), leo.likes)
        assertEquals(2018, leo.birthday?.year)
        assertTrue(leo.notifyBirthday) // from Ana's birthday reminder
        assertTrue(leo.showInMyList) // Ana is family
        val mia = people.getValue("rui-dup")
        assertEquals("Mia", mia.name)
        assertEquals(false, mia.showInMyList) // a friend's kid
        assertTrue(people.getValue("ana").showInMyList && people.getValue("dup").showInMyList)

        assertEquals(
            listOf(
                PersonLink("kid-link-leo", FriendId("ana"), FriendId("leo"), LinkType.CHILD, addedAt = Instant.fromEpochMilliseconds(1700000000000)),
                PersonLink("kid-link-rui-dup", FriendId("rui"), FriendId("rui-dup"), LinkType.CHILD),
            ),
            database.readLinks(),
        )
        // The gift for Mia moved to her page; Rui keeps his own.
        assertEquals(listOf(Gift("g1", "Lego", price = "€35")), mia.gifts)
        assertEquals(listOf("Book"), people.getValue("rui").gifts.map { it.title })
        driver.close()
    }

    @Test
    fun version11to12_keepsPlans_andTheyCanTakeACircleAnActivityAndARepeat() = runTest {
        val driver = driverAt(11)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', NULL, 1, 0, '', 0, 0, 0, 0)
            """.trimIndent(),
            0,
        ).await()
        driver.execute(null, "INSERT INTO plan VALUES ('p1', 0, 'Lunch', '2026-12-01', NULL, NULL, 'PLANNED', 1000)", 0).await()
        driver.execute(null, "INSERT INTO plan_friend VALUES ('p1', 'ana', 0)", 0).await()

        GoodCompanyDatabase.Schema.migrate(driver, 11, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val old = database.readPlans().single()
        assertEquals(listOf(FriendId("ana")), old.friendIds)
        assertNull(old.circleId)
        assertNull(old.repeat)
        assertNull(old.activity)

        val climbing = Plan(
            id = "p2",
            friendIds = emptyList(),
            title = "Climbing",
            date = LocalDate(2026, 12, 16),
            createdAt = Instant.fromEpochSeconds(2),
            circleId = "crew",
            activity = HangoutActivity.SPORTS,
            repeat = Repeat(15, RepeatUnit.DAY, LocalDate(2026, 12, 1), until = LocalDate(2027, 6, 1)),
            skippedDates = listOf(LocalDate(2026, 12, 31)),
        )
        database.writePlan(climbing)
        assertEquals(climbing, database.readPlans().single { it.id == "p2" })
        database.planQueries.delete("p2")
        assertTrue(database.planQueries.allPlanSkips().awaitAsList().isEmpty())
        driver.close()
    }

    @Test
    fun version13to14_aiFollowsTheSavedKey_untilChosen_andEveryActivityStaysOn() = runTest {
        val driver = driverAt(13)
        driver.execute(null, "INSERT INTO user_preferences VALUES (1, 7, 30, 90, 1, 6, 9, 1, 'Lisbon', 1, 0, NULL)", 0).await()

        GoodCompanyDatabase.Schema.migrate(driver, 13, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)

        // Someone who already had a key keeps AI; someone without one gets the new default (off).
        assertTrue(database.readPreferences(AppSettings(aiApiKey = "sk-ant-key")).aiEnabled)
        val withoutKey = database.readPreferences(AppSettings())
        assertEquals(false, withoutKey.aiEnabled)
        assertEquals(HangoutActivity.entries, withoutKey.activities)
        assertEquals("Lisbon", withoutKey.homeArea)

        // Once written, the switch is what the user chose, whatever the key; the activities keep their order.
        val chosen = withoutKey.copy(aiEnabled = false, activities = listOf(HangoutActivity.WALK, HangoutActivity.COFFEE))
        database.writePreferences(chosen)
        val read = database.readPreferences(AppSettings(aiApiKey = "sk-ant-key"))
        assertEquals(false, read.aiEnabled)
        assertEquals(listOf(HangoutActivity.WALK, HangoutActivity.COFFEE), read.activities)
        driver.close()
    }

    @Test
    fun version14to15_keepsLinks_remembersDismissedKinship_andTakesStepAndHalfFamily() = runTest {
        val driver = driverAt(14)
        listOf("ana", "rui", "duarte").forEachIndexed { i, id ->
            driver.execute(
                null,
                """
                INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                    is_orphaned, catch_up_paused, contact_counts_as_catch_up)
                VALUES ('$id', $i, '$id', NULL, 1, 0, '', 0, 0, 0, 0)
                """.trimIndent(),
                0,
            ).await()
        }
        driver.execute(
            null,
            "INSERT INTO person_link VALUES ('l1', 'rui', 'ana', 'SPOUSE', '2015-07-04', NULL, 'wedding', 1, 1700000000000)",
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 14, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val spouse = PersonLink(
            "l1",
            FriendId("rui"),
            FriendId("ana"),
            LinkType.SPOUSE,
            LocalDate(2015, 7, 4),
            note = "wedding",
            remindAnniversary = true,
            addedAt = Instant.fromEpochMilliseconds(1700000000000),
        )
        assertEquals(listOf(spouse), database.readLinks())
        assertTrue(database.readFriends().all { it.dismissedKinship.isEmpty() })

        // The new family types are accepted. Since 16.sqm (issue #72) an unknown type is too, and reads are skipped.
        val half = PersonLink("l2", FriendId("ana"), FriendId("duarte"), LinkType.HALF_SIBLING)
        database.writeLink(half)
        driver.execute(null, "INSERT INTO person_link (id, from_friend_id, to_friend_id, type) VALUES ('x', 'ana', 'rui', 'BOSS')", 0).await()
        assertEquals(listOf(spouse, half), database.readLinks())
        val duarte = database.readFriends().first { it.id.value == "duarte" }
        database.writeFriend(duarte.copy(relationToMe = LinkType.STEP_SIBLING, dismissedKinship = setOf(LinkType.SIBLING, LinkType.HALF_SIBLING)), 2)
        val saved = database.readFriends().first { it.id.value == "duarte" }
        assertEquals(LinkType.STEP_SIBLING, saved.relationToMe)
        assertEquals(setOf(LinkType.SIBLING, LinkType.HALF_SIBLING), saved.dismissedKinship)
        // Links still go with either person.
        database.friendQueries.delete("ana")
        assertTrue(database.readLinks().isEmpty())
        driver.close()
    }

    @Test
    fun version15to16_addsGiftLists_thatGoWithTheirFriend() = runTest {
        val driver = driverAt(15)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', NULL, 1, 0, '', 0, 0, 0, 0)
            """.trimIndent(),
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 15, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        assertTrue(database.readGiftLists().isEmpty())
        val list = GiftList(
            "xmas",
            "Christmas 2026",
            LocalDate(2026, 12, 25),
            Instant.fromEpochMilliseconds(1700000000000),
            GiftOccasion.CHRISTMAS,
            listOf(GiftListEntry(FriendId("ana"), 30.0, listOf(GiftListItem("g1", GiftStatus.GIVEN)))),
        )
        database.writeGiftList(list)
        assertEquals(listOf(list), database.readGiftLists())
        // Since 16.sqm (issue #72) an unknown status can be stored; it reads as "to buy".
        driver.execute(null, "UPDATE gift_list_item SET status = 'LOST'", 0).await()
        val toBuy = GiftListEntry(FriendId("ana"), 30.0, listOf(GiftListItem("g1", GiftStatus.TO_BUY)))
        assertEquals(listOf(list.copy(entries = listOf(toBuy))), database.readGiftLists())
        database.friendQueries.delete("ana")
        assertEquals(listOf(list.copy(entries = emptyList())), database.readGiftLists())
        driver.close()
    }

    @Test
    fun version17to18_friendsStartWithNoApproximateLastSeen_andItRoundTrips() = runTest {
        val driver = driverAt(17)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, circle, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', 'c1', 1, 1, 'notes', 0, 0, 'CLOSE', 0, 0)
            """.trimIndent(),
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 17, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val ana = database.readFriends().single()
        assertEquals("notes", ana.notes)
        assertEquals(Closeness.CLOSE, ana.closeness)
        assertNull(ana.approxLastSeen)

        database.writeFriend(ana.copy(approxLastSeen = LocalDate(2026, 2, 23)), 0)
        assertEquals(LocalDate(2026, 2, 23), database.readFriends().single().approxLastSeen)
        driver.close()
    }

    @Test
    fun version18to19_keepsFriendsAndTheirPhotos_andStartsWithNoPhotoOfYou() = runTest {
        val driver = driverAt(18)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, circle, catch_up_paused, contact_counts_as_catch_up, approx_last_seen)
            VALUES ('ana', 0, 'Ana', 'c1', 1, 1, 'notes', 0, 0, 'CLOSE', 0, 0, '2026-02-23')
            """.trimIndent(),
            0,
        ).await()
        driver.execute(null, "INSERT INTO friend_photo (friend_id, image_base64) VALUES ('ana', 'AQID')", 0).await()

        GoodCompanyDatabase.Schema.migrate(driver, 18, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val ana = database.readFriends().single()
        assertEquals(ContactPhoto("AQID"), ana.photo)
        assertEquals(LocalDate(2026, 2, 23), ana.approxLastSeen)
        assertNull(database.readMyPhoto())

        database.writeMyPhoto(ContactPhoto("BAU="))
        assertEquals(ContactPhoto("BAU="), database.readMyPhoto())
        database.writeMyPhoto(null)
        assertNull(database.readMyPhoto())
        driver.close()
    }

    @Test
    fun activitiesColumn_dropsUnknownNames_andEmptyMeansAll() {
        assertEquals(HangoutActivity.entries, activitiesFromColumn(null))
        assertEquals(HangoutActivity.entries, activitiesFromColumn("BOWLING"))
        assertEquals(listOf(HangoutActivity.TRIP, HangoutActivity.LUNCH), activitiesFromColumn("TRIP,BOWLING,LUNCH,TRIP"))
        assertNull(HangoutActivity.entries.toActivitiesColumn())
        assertEquals("TRIP,LUNCH", listOf(HangoutActivity.TRIP, HangoutActivity.LUNCH).toActivitiesColumn())
    }
}
