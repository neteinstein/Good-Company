package com.neteinstein.goodcompany.feature.friends

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.feature.friends.ui.components.RowSubtitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Issue #130: the People row's second line; "Last in touch" only for people you keep track of. */
class FriendRowSubtitleTest {
    private val yesterday = Instant.parse("2026-03-09T12:00:00Z")

    private fun summary(f: Friend, parents: List<Friend> = emptyList()) =
        FriendSummary(f, lastSeen = yesterday, lastContact = yesterday, nextBirthday = null, meetupOverdue = false, parents = parents)

    @Test
    fun aTrackedPerson_showsWhenYouWereLastInTouch() {
        assertEquals(RowSubtitle.LastInTouch(yesterday), RowSubtitle.of(summary(friend("ana"))))
    }

    @Test
    fun anUntrackedPerson_showsWhatTheRowWouldOtherwiseShow() {
        val fromContacts = friend("ana") { copy(tracksKeepInTouch = false, contactId = "c1") }
        assertEquals(RowSubtitle.FromContacts, RowSubtitle.of(summary(fromContacts)))
        val inApp = friend("rui") { copy(tracksKeepInTouch = false, contactId = null) }
        assertEquals(RowSubtitle.AddedInApp, RowSubtitle.of(summary(inApp)))
        val likes = friend("zoe") { copy(tracksKeepInTouch = false, likes = listOf("Jazz")) }
        assertEquals(RowSubtitle.Likes(listOf("Jazz")), RowSubtitle.of(summary(likes)))
    }

    @Test
    fun anUntrackedKid_saysWhoseKidTheyAre() {
        val parent = friend("delfina")
        val kid = friend("leo") { copy(tracksKeepInTouch = false) }
        assertEquals(RowSubtitle.KidOf(parent), RowSubtitle.of(summary(kid, listOf(parent))))
    }
}
