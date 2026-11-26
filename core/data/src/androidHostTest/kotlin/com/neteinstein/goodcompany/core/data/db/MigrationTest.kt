package com.neteinstein.goodcompany.core.data.db

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.CalendarDecision
import com.neteinstein.goodcompany.core.domain.model.CalendarDecisionKind
import com.neteinstein.goodcompany.core.domain.model.CatchUpCounts
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
import com.neteinstein.goodcompany.core.domain.model.ReminderRepeat
import com.neteinstein.goodcompany.core.domain.model.Repeat
import com.neteinstein.goodcompany.core.domain.model.RepeatUnit
import com.neteinstein.goodcompany.core.domain.usecase.CatchUpPolicy
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import java.util.Properties
import kotlin.io.path.createTempFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun version19to20_givesEveryoneACloseness_andRealFriendFollowsIt() = runTest {
        val driver = driverAt(19)
        // (id, level, heart) → expected level, as in the table of issue #86.
        val rows = listOf(
            Triple("none-heart", null, 1) to Closeness.CLOSE,
            Triple("none", null, 0) to Closeness.ACQUAINTANCE,
            Triple("inner", "INNER", 0) to Closeness.ACQUAINTANCE,
            Triple("close", "CLOSE", 0) to Closeness.ACQUAINTANCE,
            Triple("good", "GOOD", 0) to Closeness.ACQUAINTANCE,
            Triple("inner-heart", "INNER", 1) to Closeness.INNER,
            Triple("close-heart", "CLOSE", 1) to Closeness.CLOSE,
            Triple("good-heart", "GOOD", 1) to Closeness.GOOD,
            Triple("acq", "ACQUAINTANCE", 0) to Closeness.ACQUAINTANCE,
        )
        rows.forEachIndexed { i, (row, _) ->
            val (id, level, heart) = row
            driver.execute(
                null,
                """
                INSERT INTO friend (id, position, name, is_close_friend, notify_birthday, notes, suggest_events,
                    is_orphaned, circle, catch_up_paused, contact_counts_as_catch_up)
                VALUES ('$id', $i, '$id', $heart, 1, '', 0, 0, ${level?.let { "'$it'" } ?: "NULL"}, 0, 0)
                """.trimIndent(),
                0,
            ).await()
        }

        GoodCompanyDatabase.Schema.migrate(driver, 19, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val read = database.readFriends().associateBy { it.id.value }
        rows.forEach { (row, expected) ->
            assertEquals(expected, read.getValue(row.first).closeness, row.first)
            // The stored heart keeps its value, so birthdays and rhythms reach the same people.
            assertEquals(row.third == 1, read.getValue(row.first).isCloseFriend, row.first)
        }
        val stored = driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM friend WHERE is_close_friend <> (CASE WHEN circle = 'ACQUAINTANCE' THEN 0 ELSE 1 END) OR circle IS NULL",
            { c -> app.cash.sqldelight.db.QueryResult.Value(c.next().value.let { c.getLong(0) }) },
            0,
        ).await()
        assertEquals(0L, stored)
        driver.close()
    }

    @Test
    fun version20to21_remindersStartWithNoCircle_andCirclesWithNoCover() = runTest {
        val driver = driverAt(20)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, circle, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', 1, 1, '', 0, 0, 'CLOSE', 0, 0)
            """.trimIndent(),
            0,
        ).await()
        driver.execute(null, "INSERT INTO reminder (id, position, title, due_at, done, created_at) VALUES ('r', 0, 'Call', 1000, 0, 500)", 0).await()
        driver.execute(null, "INSERT INTO reminder_friend (reminder_id, friend_id, position) VALUES ('r', 'ana', 0)", 0).await()

        GoodCompanyDatabase.Schema.migrate(driver, 20, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val reminder = database.readReminders().single()
        assertEquals("Call", reminder.title)
        assertEquals(listOf(FriendId("ana")), reminder.friendIds)
        assertNull(reminder.circleId)
        assertTrue(database.readCircleCovers().isEmpty())

        database.writeReminder(reminder.copy(circleId = "surf"))
        assertEquals("surf", database.readReminders().single().circleId)
        database.writeCircleCover("surf", ContactPhoto("AQID"))
        assertEquals(mapOf("surf" to ContactPhoto("AQID")), database.readCircleCovers())
        database.writeCircleCover("surf", ContactPhoto("BAU="))
        assertEquals(mapOf("surf" to ContactPhoto("BAU=")), database.readCircleCovers())
        database.writeCircleCover("surf", null)
        assertTrue(database.readCircleCovers().isEmpty())
        driver.close()
    }

    @Test
    fun version21to22_remindersStartOnceNotImportantNotSnoozed_andTheNewFieldsRoundTrip() = runTest {
        val driver = driverAt(21)
        driver.execute(
            null,
            "INSERT INTO reminder (id, position, title, due_at, done, created_at, circle_id) VALUES ('r', 0, 'Call', 1000, 0, 500, 'surf')",
            0,
        ).await()
        driver.execute(null, "INSERT INTO reminder (id, position, title, due_at, done, created_at) VALUES ('d', 1, 'Old', NULL, 1, 400)", 0).await()

        GoodCompanyDatabase.Schema.migrate(driver, 21, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val (open, done) = database.readReminders()
        assertEquals("Call", open.title)
        assertEquals("surf", open.circleId)
        assertNull(open.snoozedUntil)
        assertFalse(open.important)
        assertEquals(ReminderRepeat.NONE, open.repeat)
        assertNull(open.completedAt)
        assertTrue(done.done)
        assertNull(done.completedAt)

        val changed = open.copy(
            snoozedUntil = Instant.fromEpochMilliseconds(2000),
            important = true,
            repeat = ReminderRepeat.MONTHLY,
            completedAt = Instant.fromEpochMilliseconds(3000),
        )
        database.writeReminder(changed)
        assertEquals(changed, database.readReminders().first())
        database.writeReminder(changed.copy(snoozedUntil = null, important = false, repeat = ReminderRepeat.NONE, completedAt = null))
        assertEquals(open, database.readReminders().first())
        // A repeat this build doesn't know (a newer build or backup) reads as once.
        driver.execute(null, "UPDATE reminder SET repeat_every = 'HOURLY' WHERE id = 'r'", 0).await()
        assertEquals(ReminderRepeat.NONE, database.readReminders().first().repeat)
        driver.close()
    }

    @Test
    fun version22to23_interactionsStartWithNoLabel_andTheNewKindsRoundTrip() = runTest {
        val driver = driverAt(22)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, circle, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', 1, 1, '', 0, 0, 'CLOSE', 0, 0)
            """.trimIndent(),
            0,
        ).await()
        driver.execute(
            null,
            "INSERT INTO interaction (id, friend_id, type, at, note, source) VALUES ('c1', 'ana', 'CALL', 1000, 'Porto', 'MANUAL')",
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 22, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val old = database.readInteractions().single()
        assertEquals("Porto", old.note)
        assertEquals("", old.label)

        val ana = FriendId("ana")
        val at = Instant.fromEpochMilliseconds(2000)
        val other = Interaction(ana, InteractionType.OTHER, at, "o1", label = "Sent a postcard")
        database.writeInteraction(other)
        database.writeInteraction(Interaction(ana, InteractionType.LONG_CHAT, at, "l1"))
        database.writeInteraction(Interaction(ana, InteractionType.GAME, at, "g1"))
        val read = database.readInteractions().associateBy { it.id }
        assertEquals(other, read["o1"])
        assertEquals(InteractionType.LONG_CHAT, read["l1"]?.type)
        assertEquals(InteractionType.GAME, read["g1"]?.type)
        driver.close()
    }

    @Test
    fun version23to24_turnsNotesIntoOneDatedNoteEach_keepsTheText_andBlankNotesMakeNone() = runTest {
        val driver = driverAt(23)
        val notes = mapOf(
            "ana" to "Partner: Tiago.\nAllergic to peanuts.  ",
            "rui" to "",
            "bia" to " \n\t \r\n",
            "joao" to "Moved to Porto",
        )
        notes.entries.forEachIndexed { i, (id, text) ->
            driver.execute(
                null,
                """
                INSERT INTO friend (id, position, name, is_close_friend, notify_birthday, notes, suggest_events,
                    is_orphaned, circle, catch_up_paused, contact_counts_as_catch_up)
                VALUES (?, $i, ?, 1, 1, ?, 0, 0, 'CLOSE', 0, 0)
                """.trimIndent(),
                3,
            ) {
                bindString(0, id)
                bindString(1, id)
                bindString(2, text)
            }.await()
        }
        // João already has a dated note: it stays as it was.
        driver.execute(
            null,
            "INSERT INTO journal_entry (id, friend_id, at, text) VALUES ('j1', 'joao', 1000, 'Talked about the move')",
            0,
        ).await()

        val before = System.currentTimeMillis()
        GoodCompanyDatabase.Schema.migrate(driver, 23, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val journal = database.readJournal()
        assertEquals(setOf("note-ana", "note-joao", "j1"), journal.mapTo(HashSet()) { it.id })
        val ana = journal.single { it.id == "note-ana" }
        assertEquals(FriendId("ana"), ana.friendId)
        assertEquals(notes["ana"], ana.text) // exactly as typed, nothing trimmed
        assertTrue(ana.at.toEpochMilliseconds() >= before / 1000 * 1000, "dated when the migration ran")
        assertEquals("Moved to Porto", journal.single { it.id == "note-joao" }.text)
        assertEquals(Instant.fromEpochMilliseconds(1000), journal.single { it.id == "j1" }.at)
        // The old column keeps its text until the migration is verified.
        assertEquals(notes["ana"], database.readFriends().single { it.id == FriendId("ana") }.notes)
        driver.close()
    }

    @Test
    fun version24to25_everyoneStartsWithKeepInTouchTrackingOn_andTheSwitchRoundTrips() = runTest {
        val driver = driverAt(24)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, circle, catch_up_paused, contact_counts_as_catch_up, meet_every_days)
            VALUES ('ana', 0, 'Ana', 'c1', 1, 1, '', 0, 0, 'INNER', 1, 1, 14)
            """.trimIndent(),
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 24, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val ana = database.readFriends().single()
        assertTrue(ana.tracksKeepInTouch)
        assertTrue(ana.catchUpPaused)
        assertEquals(CatchUpCounts.ANY_CONTACT, ana.catchUpCounts)
        assertEquals(14, ana.meetEveryDays)

        database.writeFriend(ana.copy(tracksKeepInTouch = false), 0)
        assertFalse(database.readFriends().single().tracksKeepInTouch)
        driver.close()
    }

    @Test
    fun version25to26_socialsAndWebsitesStartEmpty_roundTrip_andAnUnknownNetworkIsSkipped() = runTest {
        val driver = driverAt(25)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, circle, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', 'c1', 1, 1, '', 0, 0, 'INNER', 0, 0)
            """.trimIndent(),
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 25, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val ana = database.readFriends().single()
        assertTrue(ana.socials.isEmpty())
        assertTrue(ana.websites.isEmpty())

        val socials = listOf(
            com.neteinstein.goodcompany.core.domain.model.SocialLink(com.neteinstein.goodcompany.core.domain.model.SocialNetwork.X, "ana"),
            com.neteinstein.goodcompany.core.domain.model.SocialLink(com.neteinstein.goodcompany.core.domain.model.SocialNetwork.INSTAGRAM, "ana.s"),
        )
        database.writeFriend(ana.copy(socials = socials, websites = listOf("https://ana.pt", "https://blog.ana.pt")), 0)
        val back = database.readFriends().single()
        assertEquals(socials, back.socials)
        assertEquals(listOf("https://ana.pt", "https://blog.ana.pt"), back.websites)

        driver.execute(null, "INSERT INTO friend_link VALUES ('ana', 'MYSPACE', 9, 'ana')", 0).await()
        assertEquals(socials, database.readFriends().single().socials)
        driver.close()
    }

    @Test
    fun version26to27_notesKeepTheirText_andMediaRoundTrips_unknownKindSkipped() = runTest {
        val driver = driverAt(26)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, circle, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', 'c1', 1, 1, '', 0, 0, 'INNER', 0, 0)
            """.trimIndent(),
            0,
        ).await()
        driver.execute(null, "INSERT INTO journal_entry (id, friend_id, at, text) VALUES ('j', 'ana', 1000, 'Hike')", 0).await()

        GoodCompanyDatabase.Schema.migrate(driver, 26, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val note = database.readJournal().single()
        assertEquals("Hike", note.text)
        assertTrue(note.media.isEmpty())

        val media = listOf(
            com.neteinstein.goodcompany.core.domain.model.NoteMedia(
                "m1",
                com.neteinstein.goodcompany.core.domain.model.MediaKind.VIDEO,
                com.neteinstein.goodcompany.core.domain.model.ContactPhoto("AQID"),
                com.neteinstein.goodcompany.core.domain.model.PhotoRef(com.neteinstein.goodcompany.core.domain.model.PhotoPlatform.IOS, "asset-1"),
            ),
            com.neteinstein.goodcompany.core.domain.model.NoteMedia(
                "m2",
                com.neteinstein.goodcompany.core.domain.model.MediaKind.PHOTO,
                com.neteinstein.goodcompany.core.domain.model.ContactPhoto("BAUG"),
            ),
        )
        database.writeJournal(note.copy(media = media))
        assertEquals(media, database.readJournal().single().media)
        // Replaced, not added to.
        database.writeJournal(note.copy(media = media.take(1)))
        assertEquals(media.take(1), database.readJournal().single().media)

        driver.execute(null, "INSERT INTO journal_media VALUES ('j', 'm9', 9, 'HOLOGRAM', 'AQID', NULL, NULL)", 0).await()
        assertEquals(media.take(1), database.readJournal().single().media)
        driver.close()
    }

    @Test
    fun version27to28_meetupsPlansAndSettingsStartWithNoOwnActivity_andTheyRoundTrip() = runTest {
        val driver = driverAt(27)
        driver.execute(null, "INSERT INTO hangout (id, position, at, note, activity) VALUES ('h1', 0, 1000, '', 'WALK')", 0).await()
        driver.execute(
            null,
            "INSERT INTO plan (id, position, title, date, status, created_at, activity) VALUES ('p1', 0, 'Lunch', '2026-12-01', 'PLANNED', 1000, 'LUNCH')",
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 27, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val walk = database.readHangouts().single()
        assertEquals(HangoutActivity.WALK, walk.activity)
        assertNull(walk.activityName)
        assertNull(database.readPlans().single().activityName)
        assertTrue(database.readPreferences(AppSettings()).customActivities.isEmpty())

        val surf = Hangout("h2", emptyList(), Instant.fromEpochSeconds(2), activityName = "Surf")
        database.writeHangout(surf)
        assertEquals("Surf", database.readHangouts().single { it.id == "h2" }.activityName)
        val plan = database.readPlans().single().copy(activity = null, activityName = "Board games")
        database.writePlan(plan)
        assertEquals(plan, database.readPlans().single())
        database.writePreferences(database.readPreferences(AppSettings()).copy(customActivities = listOf("Surf", "Board games")))
        assertEquals(listOf("Surf", "Board games"), database.readPreferences(AppSettings()).customActivities)
        driver.close()
    }

    @Test
    fun version28to29_stayInTouchTurnsOffOnceForKidsPartnersAndTypedInRelatives() = runTest {
        val driver = driverAt(28)
        // id, contact id, is_close_friend, relation to me, meet every days, approx last seen
        val people = listOf(
            "me_kid" to "'c1', 1, 'CHILD', NULL, NULL", // your own kid from contacts: off (rule 1)
            "me_step" to "'c2', 1, 'STEP_CHILD', NULL, NULL", // off (rule 1)
            "me_wife" to "'c3', 1, 'SPOUSE', NULL, NULL", // off (rule 1)
            "me_bf" to "NULL, 0, 'DATING', NULL, NULL", // off (rule 1)
            "me_mum" to "'c4', 1, 'PARENT', NULL, NULL", // a parent keeps tracking
            "me_sis" to "NULL, 1, 'SIBLING', 30, NULL", // a sibling keeps tracking
            "me_ex" to "'c5', 0, 'EX', NULL, NULL", // an ex isn't a partner: on
            "rui" to "'c6', 1, NULL, NULL, NULL", // a friend from contacts, a parent: on
            "leo" to "NULL, 0, NULL, NULL, NULL", // Rui's typed-in kid (CHILD link to): off (rule 2)
            "eva" to "NULL, 1, NULL, 14, NULL", // typed-in, stored as "Rui is Eva's parent": off (rule 2)
            "tom" to "'c7', 0, NULL, NULL, NULL", // a kid from contacts keeps tracking
            "ana" to "NULL, 0, NULL, NULL, NULL", // typed-in, Rui's sister, nothing of her own: off (rule 3)
            "bia" to "NULL, 0, NULL, NULL, NULL", // typed-in sister with a meetup: on
            "caio" to "NULL, 0, NULL, NULL, NULL", // typed-in brother with a call: on
            "dani" to "NULL, 0, NULL, NULL, '2026-10-01'", // answered "When did you last see…?": on
            "edu" to "NULL, 0, NULL, 21, NULL", // has a rhythm: on
            "fil" to "NULL, 1, NULL, NULL, NULL", // a real friend (Close) linked as a cousin: on
            "gil" to "'c8', 0, NULL, NULL, NULL", // from contacts with a plain friend link: on
            "hugo" to "NULL, 0, NULL, NULL, NULL", // typed-in with a friend link only: on
            "ines" to "NULL, 0, NULL, NULL, NULL", // typed-in, the from side of an IN_LAW link (Rui is her in-law): on
            "joao" to "NULL, 0, NULL, NULL, NULL", // typed-in, linked with a type this build doesn't know: on
        )
        people.forEachIndexed { i, (id, rest) ->
            driver.execute(
                null,
                """
                INSERT INTO friend (id, position, name, contact_id, is_close_friend, relation_to_me, meet_every_days,
                    approx_last_seen, notify_birthday, notes, suggest_events, is_orphaned, catch_up_paused,
                    contact_counts_as_catch_up)
                VALUES ('$id', $i, '$id', $rest, 1, '', 0, 0, 0, 0)
                """.trimIndent(),
                0,
            ).await()
        }
        listOf(
            "rui" to "CHILD" to "leo",
            "eva" to "PARENT" to "rui",
            "rui" to "CHILD" to "tom",
            "rui" to "SIBLING" to "ana",
            "rui" to "SIBLING" to "bia",
            "rui" to "SIBLING" to "caio",
            "rui" to "SIBLING" to "dani",
            "rui" to "SIBLING" to "edu",
            "rui" to "COUSIN" to "fil",
            "rui" to "FRIEND" to "gil",
            "rui" to "FRIEND" to "hugo",
            "ines" to "IN_LAW" to "rui",
            "rui" to "GODPARENT" to "joao",
        ).forEachIndexed { i, (pair, to) ->
            val (from, type) = pair
            driver.execute(null, "INSERT INTO person_link (id, from_friend_id, to_friend_id, type) VALUES ('l$i', '$from', '$to', '$type')", 0)
                .await()
        }
        driver.execute(null, "INSERT INTO hangout (id, position, at, note) VALUES ('h1', 0, 1000, '')", 0).await()
        driver.execute(null, "INSERT INTO hangout_friend VALUES ('h1', 'bia', 0)", 0).await()
        driver.execute(
            null,
            "INSERT INTO interaction (id, friend_id, type, at, note, source) VALUES ('i1', 'caio', 'CALL', 1000, '', 'MANUAL')",
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 28, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val off = database.readFriends().filterNot { it.tracksKeepInTouch }.mapTo(HashSet()) { it.id.value }
        assertEquals(setOf("me_kid", "me_step", "me_wife", "me_bf", "leo", "eva", "ana"), off)
        driver.close()
    }

    @Test
    fun version29to30_peopleStartWithNoBackground_andOneRoundTrips() = runTest {
        val driver = driverAt(29)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', 'c1', 1, 1, '', 0, 0, 0, 0)
            """.trimIndent(),
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 29, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        assertEquals("Ana", database.readFriends().single().name)
        assertTrue(database.readPersonBackgrounds().isEmpty())
        val ana = FriendId("ana")
        database.writePersonBackground(ana, ContactPhoto("AQID"))
        assertEquals(mapOf(ana to ContactPhoto("AQID")), database.readPersonBackgrounds())
        database.writePersonBackground(ana, ContactPhoto("BAU="))
        assertEquals(mapOf(ana to ContactPhoto("BAU=")), database.readPersonBackgrounds())
        // Someone who isn't stored gets none (the foreign key would refuse it).
        database.writePersonBackground(FriendId("nobody"), ContactPhoto("AQID"))
        assertEquals(setOf(ana), database.readPersonBackgrounds().keys)
        database.writePersonBackground(ana, null)
        assertTrue(database.readPersonBackgrounds().isEmpty())
        driver.close()
    }

    @Test
    fun version30to31_circlesStartWithNoLastMetAnswer_andOneRoundTrips() = runTest {
        val driver = driverAt(30)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', 'c1', 1, 1, '', 0, 0, 0, 0)
            """.trimIndent(),
            0,
        ).await()
        driver.execute(
            null,
            "INSERT INTO friend_group (id, position, name, emoji, meet_every_days, snoozed_until, color) VALUES ('choir', 0, 'Choir', '🎶', 15, '2026-11-30', 'TEAL')",
            0,
        ).await()
        driver.execute(null, "INSERT INTO group_member (group_id, friend_id, position) VALUES ('choir', 'ana', 0)", 0).await()

        GoodCompanyDatabase.Schema.migrate(driver, 30, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val choir = database.readGroups().single()
        assertEquals("Choir", choir.name)
        assertEquals(15, choir.meetEveryDays)
        assertEquals(LocalDate(2026, 11, 30), choir.snoozedUntil)
        assertEquals(listOf(FriendId("ana")), choir.memberIds)
        assertNull(choir.approxLastMet)

        database.writeGroup(choir.copy(approxLastMet = LocalDate(2026, 11, 6)))
        assertEquals(LocalDate(2026, 11, 6), database.readGroups().single().approxLastMet)
        database.writeGroup(choir.copy(approxLastMet = null))
        assertNull(database.readGroups().single().approxLastMet)
        driver.close()
    }

    @Test
    fun version31to32_settingsKeepTheirActivities_andStartWithEveryHowOption_andTheyRoundTrip() = runTest {
        // Issue #152: an existing preferences row, with activities of its own, migrates with the How options at defaults.
        val driver = driverAt(31)
        driver.execute(
            null,
            "INSERT INTO user_preferences (id, inner_circle_days, close_circle_days, good_friends_days, weekly_digest, digest_day, " +
                "reminder_hour, weekly_ideas_enabled, home_area, show_warmth, private_notifications, activities, custom_activities) " +
                "VALUES (1, 7, 30, 90, 1, 1, 9, 0, 'Lisbon', 1, 0, 'WALK,LUNCH', '[\"Surf\"]')",
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 31, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val before = database.readPreferences(AppSettings())
        assertEquals(listOf(HangoutActivity.WALK, HangoutActivity.LUNCH), before.activities)
        assertEquals(listOf("Surf"), before.customActivities)
        assertEquals("Lisbon", before.homeArea)
        assertEquals(LogKind.DEFAULT_ORDER, before.logKinds)
        assertTrue(before.customLogKinds.isEmpty())

        database.writePreferences(before.copy(logKinds = listOf(LogKind.EMAIL, LogKind.CALL), customLogKinds = listOf("Voice note", "Letter")))
        val after = database.readPreferences(AppSettings())
        assertEquals(listOf(LogKind.EMAIL, LogKind.CALL), after.logKinds)
        assertEquals(listOf("Voice note", "Letter"), after.customLogKinds)
        assertEquals(listOf("Surf"), after.customActivities)
        driver.close()
    }

    @Test
    fun version32to33_theCallsCountSwitchBecomesAnyContact_offIsTheDefault_andSettingsStartWithTheDefaultKinds() = runTest {
        // Decision 2026-11-21 ("Catch-up · what counts").
        val driver = driverAt(32)
        driver.execute(
            null,
            """
            INSERT INTO friend (id, position, name, contact_id, is_close_friend, notify_birthday, notes, suggest_events,
                is_orphaned, catch_up_paused, contact_counts_as_catch_up)
            VALUES ('ana', 0, 'Ana', 'c1', 1, 1, '', 0, 0, 0, 1), ('bob', 1, 'Bob', 'c2', 1, 1, '', 0, 0, 0, 0)
            """.trimIndent(),
            0,
        ).await()
        driver.execute(
            null,
            "INSERT INTO user_preferences (id, inner_circle_days, close_circle_days, good_friends_days, weekly_digest, digest_day, " +
                "reminder_hour, weekly_ideas_enabled, home_area, show_warmth, private_notifications, custom_log_kinds) " +
                "VALUES (1, 7, 30, 90, 1, 1, 9, 0, 'Lisbon', 1, 0, '[\"Voice note\"]')",
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 32, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val friends = database.readFriends().associateBy { it.id.value }
        assertEquals(CatchUpCounts.ANY_CONTACT, friends.getValue("ana").catchUpCounts)
        assertEquals(CatchUpCounts.DEFAULT, friends.getValue("bob").catchUpCounts)
        val settings = database.readPreferences(AppSettings())
        assertEquals(CatchUpPolicy.DEFAULT_KINDS, settings.catchUpKinds)
        assertTrue(settings.catchUpCustomKinds.isEmpty())

        database.writeFriend(friends.getValue("bob").copy(catchUpCounts = CatchUpCounts.IN_PERSON_ONLY), 1)
        assertEquals(CatchUpCounts.IN_PERSON_ONLY, database.readFriends().single { it.id.value == "bob" }.catchUpCounts)
        database.writePreferences(settings.copy(catchUpKinds = setOf(LogKind.MEETUP, LogKind.MESSAGE), catchUpCustomKinds = listOf("Voice note")))
        val after = database.readPreferences(AppSettings())
        assertEquals(setOf(LogKind.MEETUP, LogKind.MESSAGE), after.catchUpKinds)
        assertEquals(listOf("Voice note"), after.catchUpCustomKinds)
        driver.close()
    }

    @Test
    fun version33to34_calendarAnswersKeepTheirKind_startWithNoPeople_andWhoRoundTrips() = runTest {
        // Decision 2026-11-26 ("Calendar · who's Ana?").
        val driver = driverAt(33)
        driver.execute(
            null,
            "INSERT INTO calendar_decision (key, kind, decided_at) VALUES ('series:padel', 'NEVER', 1700000000000)",
            0,
        ).await()

        GoodCompanyDatabase.Schema.migrate(driver, 33, GoodCompanyDatabase.Schema.version).await()
        val database = GoodCompanyDatabase(driver)
        val old = database.readCalendarDecisions().single()
        assertEquals(CalendarDecisionKind.NEVER, old.kind)
        assertTrue(old.friendIds.isEmpty())

        val who = CalendarDecision("who:series:padel", CalendarDecisionKind.WHO, old.decidedAt, listOf(FriendId("ana"), FriendId("aj")))
        database.writeCalendarDecision(who)
        assertEquals(who, database.readCalendarDecisions().single { it.kind == CalendarDecisionKind.WHO })
        driver.close()
    }

    @Test
    fun catchUpKindsColumn_nullIsTheDefault_emptyIsOnlyInPerson_andInPersonAlwaysCounts() {
        assertEquals(CatchUpPolicy.DEFAULT_KINDS, catchUpKindsFromColumn(null))
        assertEquals(setOf(LogKind.MEETUP), catchUpKindsFromColumn(""))
        assertEquals(setOf(LogKind.MEETUP, LogKind.EMAIL), catchUpKindsFromColumn("EMAIL,CARRIER_PIGEON,JOURNAL"))
        assertNull(CatchUpPolicy.DEFAULT_KINDS.toCatchUpKindsColumn())
        assertEquals("", setOf(LogKind.MEETUP).toCatchUpKindsColumn())
        assertEquals("CALL,EMAIL", setOf(LogKind.EMAIL, LogKind.CALL).toCatchUpKindsColumn())
    }

    @Test
    fun logKindsColumn_dropsUnknownNames_andEmptyMeansAllInTheDefaultOrder() {
        assertEquals(LogKind.DEFAULT_ORDER, logKindsFromColumn(null))
        assertEquals(LogKind.DEFAULT_ORDER, logKindsFromColumn("CARRIER_PIGEON"))
        assertEquals(listOf(LogKind.EMAIL, LogKind.CALL), logKindsFromColumn("EMAIL,CARRIER_PIGEON, CALL,EMAIL"))
        assertNull(LogKind.DEFAULT_ORDER.toLogKindsColumn())
        assertEquals("EMAIL,CALL", listOf(LogKind.EMAIL, LogKind.CALL).toLogKindsColumn())
    }

    @Test
    fun customActivitiesColumn_unreadableIsNone_blanksAndRepeatsDropped() {
        assertEquals(emptyList(), customActivitiesFromColumn(null))
        assertEquals(emptyList(), customActivitiesFromColumn("not json"))
        assertEquals(listOf("Surf", "Chess"), customActivitiesFromColumn("""[" Surf", "", "Chess", "Surf"]"""))
        assertNull(emptyList<String>().toCustomActivitiesColumn())
        assertEquals(listOf("Surf"), customActivitiesFromColumn(listOf("Surf").toCustomActivitiesColumn()))
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
