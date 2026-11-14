package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.closenessFrom
import com.neteinstein.goodcompany.core.domain.usecase.CadenceRules
import com.neteinstein.goodcompany.core.domain.usecase.ImportPickedContactsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LastSeenQuestions
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.domain.usecase.SetClosenessUseCase
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #69: the fourth closeness level, Acquaintances. Not real friends, no rhythm unless Settings gives one. */
class AcquaintanceTest {
    private val today = LocalDate(2026, 3, 10)
    private val ana = FriendId("ana")

    @Test
    fun onlyAcquaintancesAreNotRealFriends() {
        assertEquals(listOf(Closeness.ACQUAINTANCE), Closeness.entries.filterNot { it.isRealFriend })
    }

    @Test
    fun acquaintancesHaveNoRhythmByDefault_butSettingsCanGiveThemOne() {
        val acquaintance = friend("ana") { copy(closeness = Closeness.ACQUAINTANCE) }
        assertNull(CadenceRules().daysFor(Closeness.ACQUAINTANCE))
        assertNull(CadenceRules().cadenceFor(acquaintance))
        assertNull(CadenceRules().sourceFor(acquaintance))

        val rules = CadenceRules.from(AppSettings(acquaintanceDays = 180))
        assertEquals(180, rules.cadenceFor(acquaintance))
        assertEquals(CadenceRules.Source.CLOSENESS, rules.sourceFor(acquaintance))
        // A rhythm on their page still wins; zero or less means none.
        assertEquals(14, rules.cadenceFor(acquaintance.copy(meetEveryDays = 14)))
        assertNull(CadenceRules(acquaintanceDays = 0).cadenceFor(acquaintance))
    }

    @Test
    fun anAcquaintanceIsNeverOverdue_byDefault() {
        val friends = listOf(friend("ana") { copy(closeness = Closeness.ACQUAINTANCE) })
        val hangouts = listOf(Hangout("h", listOf(ana), at(2025, 1, 1)))
        assertTrue(Relationships.meetupStatuses(friends, hangouts, today, TimeZone.UTC, CadenceRules()).isEmpty())
        val withDefault = Relationships.meetupStatuses(friends, hangouts, today, TimeZone.UTC, CadenceRules(acquaintanceDays = 90))
        assertTrue(withDefault.single().isOverdue(today))
    }

    @Test
    fun theLastSeenCardAsksAboutAcquaintancesOnlyForTheirOwnRhythm() {
        val friends = listOf(
            friend("ana") { copy(closeness = Closeness.ACQUAINTANCE) },
            friend("bob") { copy(closeness = Closeness.ACQUAINTANCE, meetEveryDays = 30) },
            friend("cat") { copy(closeness = Closeness.GOOD) },
        )
        val asked = LastSeenQuestions.pending(friends, emptyList(), emptyList(), CadenceRules(acquaintanceDays = 60))
        assertEquals(listOf("cat", "bob"), asked.map { it.id.value })
    }

    @Test
    fun choosingAcquaintanceMakesSomeoneNotARealFriend_andBackAgain() = runTest {
        val friends = FakeFriendRepository(listOf(friend("ana") { copy(closeness = Closeness.CLOSE) }))
        val setCloseness = SetClosenessUseCase(friends)
        setCloseness(ana, Closeness.ACQUAINTANCE)
        assertEquals(Closeness.ACQUAINTANCE, friends.get(ana)!!.closeness)
        assertFalse(friends.get(ana)!!.isCloseFriend)

        setCloseness(ana, Closeness.INNER)
        assertTrue(friends.get(ana)!!.isCloseFriend)
    }

    @Test
    fun pickingAnAcquaintanceFromContactsAgainKeepsThemAnAcquaintance() = runTest {
        val friends = FakeFriendRepository(listOf(friend("ana") { copy(contactId = "c1", closeness = Closeness.ACQUAINTANCE) }))
        ImportPickedContactsUseCase(friends)(listOf(ContactRecord("c1", "Ana", listOf("+351 900"), null)))
        val ana = friends.get(ana)!!
        assertEquals(Closeness.ACQUAINTANCE, ana.closeness)
        assertFalse(ana.isCloseFriend)
    }

    @Test
    fun everyoneStartsAsAnAcquaintance_andRealFriendFollowsTheLevel() {
        assertEquals(Closeness.ACQUAINTANCE, friend("a").closeness)
        assertFalse(friend("a").isCloseFriend)
        Closeness.entries.forEach { level ->
            assertEquals(level != Closeness.ACQUAINTANCE, friend("a") { copy(closeness = level) }.isCloseFriend)
        }
    }

    @Test
    fun birthdaysStillRemindForAcquaintances_andMeetupsFollowTheSettingsDefault() = runTest {
        val g = TestGraph(
            listOf(friend("ana") { copy(closeness = Closeness.ACQUAINTANCE, approxLastSeen = LocalDate(2025, 1, 1)) }),
        )
        assertTrue(g.friendRepo.get(ana)!!.notifyBirthday)
        assertTrue(g.observeMeetups().first().isEmpty())
        g.settingsRepo.update { it.copy(acquaintanceDays = 180) }
        assertEquals(180, g.observeMeetups().first().single().cadenceDays)
    }

    @Test
    fun theOldHeartAndLevelBecomeOneLevel_likeMigration19() {
        assertEquals(Closeness.CLOSE, closenessFrom(null, realFriend = true))
        assertEquals(Closeness.ACQUAINTANCE, closenessFrom(null, realFriend = false))
        Closeness.entries.forEach { level ->
            assertEquals(Closeness.ACQUAINTANCE, closenessFrom(level, realFriend = false))
            assertEquals(level, closenessFrom(level, realFriend = true))
        }
    }

    @Test
    fun birthdaysAndRhythmsReachTheSamePeopleAfterTheMigration() {
        // Before #86: (level, heart). After: closenessFrom(level, heart). Who counts as a real friend (birthdays,
        // AgendaBuilder.caresAbout) must not change, nor who has a rhythm from their level.
        val rules = CadenceRules()
        val levels: List<Closeness?> = listOf(null) + Closeness.entries
        levels.forEach { level ->
            listOf(true, false).forEach { heart ->
                val oldDays = level?.takeIf { heart || !it.isRealFriend }?.let(rules::daysFor)
                val migrated = friend("a") { copy(closeness = closenessFrom(level, heart)) }
                // The one change is the table's own: a real friend with no level is now in Close, with its rhythm.
                val expectedDays = if (level == null && heart) rules.closeDays else oldDays
                assertEquals(expectedDays, rules.cadenceFor(migrated), "$level/$heart")
                assertEquals(heart && level != Closeness.ACQUAINTANCE, migrated.isCloseFriend, "$level/$heart")
            }
        }
    }
}
