package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.LifeUpdate
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.usecase.CatchUpStoryBuilder
import com.neteinstein.goodcompany.core.domain.usecase.FreshItem
import com.neteinstein.goodcompany.core.domain.usecase.StorySlide
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatchUpStoryTest {
    // FixedClock: 2026-03-10 12:00 UTC.
    private val ana = friend("ana", "Ana Silva") {
        copy(
            birthday = Birthday(3, 15, 1990),
            likes = listOf("Jazz", "Bouldering"),
            dislikes = listOf("Crowds"),
            likesAddedAt = mapOf("Bouldering" to at(2026, 3, 8), "Jazz" to at(2025, 1, 1)),
            notes = "Partner: Tiago",
            kids = listOf(
                Kid("k1", "Leo", Birthday(3, 25, 2019), likes = listOf("Dinosaurs")),
                Kid("k2", "Mia", Birthday(1, 1, 2026), addedAt = at(2026, 3, 1)),
            ),
            importantEvents = listOf(
                ImportantEvent("e1", "Knee surgery", LocalDate(2026, 3, 12), addedAt = at(2026, 3, 9)),
                ImportantEvent("e2", "Exam", LocalDate(2026, 3, 1)),
                ImportantEvent("e3", "Far away", LocalDate(2026, 9, 1)),
            ),
            lifeUpdates = listOf(
                LifeUpdate("u1", "New job at a design studio", at(2026, 3, 9)),
                LifeUpdate("u2", "Old news", at(2026, 1, 1)),
            ),
        )
    }
    private val rui = friend("rui", "Rui Costa")
    private val lastMeetup = Hangout("h1", listOf(ana.id, rui.id), at(2026, 2, 20), Place("Hot Clube"), note = "She wants to learn piano")

    private fun graph() = TestGraph(listOf(ana, rui), hangouts = listOf(lastMeetup))

    @Test
    fun leadsWithWhatsNewSinceYouLastMet() = runTest {
        val g = graph()
        g.reminderRepo.upsert(Reminder("r1", "Return the book", listOf(ana.id), createdAt = at(2026, 3, 1)))
        g.reminderRepo.upsert(Reminder("r2", "Done already", listOf(ana.id), done = true, createdAt = at(2026, 3, 1)))
        g.ideaRepo.addAll(
            listOf(
                Idea("i1", listOf(ana.id), IdeaKind.TALKING_POINT, "Piano lessons", "Ask about it", createdAt = at(2026, 3, 9)),
                Idea("i2", listOf(ana.id), IdeaKind.EVENT, "Jazz fest", "Go together", createdAt = at(2026, 3, 9)),
            ),
        )
        val story = g.observeCatchUpStory(ana.id).first()!!
        val slides = story.slides

        val intro = assertIs<StorySlide.Intro>(slides.first())
        assertEquals(lastMeetup.at, intro.lastSeen)
        assertEquals("Hot Clube", intro.lastPlace)
        assertEquals(4, intro.freshCount)
        assertEquals(lastMeetup.at, story.freshSince)

        // Newest first; only things added after the last meetup; untimed or older items are left out.
        val fresh = slides.filterIsInstance<StorySlide.Fresh>().map { it.item }
        assertEquals(
            listOf(
                FreshItem.Update("New job at a design studio", at(2026, 3, 9)),
                FreshItem.NewEvent("Knee surgery", LocalDate(2026, 3, 12), at(2026, 3, 9)),
                FreshItem.NewLike("Bouldering", at(2026, 3, 8)),
                FreshItem.NewKid("Mia", at(2026, 3, 1)),
            ),
            fresh,
        )
        assertEquals(1, slides.indexOfFirst { it is StorySlide.Fresh })

        val bigDays = slides.filterIsInstance<StorySlide.BigDay>()
        assertEquals(listOf("Knee surgery", "Exam"), bigDays.map { it.title })
        assertEquals(listOf(false, true), bigDays.map { it.isPast })

        val birthday = assertIs<StorySlide.Birthday>(slides.single { it is StorySlide.Birthday })
        assertEquals(LocalDate(2026, 3, 15), birthday.date)
        assertEquals(36, birthday.turning)

        val kids = assertIs<StorySlide.Kids>(slides.single { it is StorySlide.Kids }).kids
        assertEquals(listOf("Leo", "Mia"), kids.map { it.name })
        assertEquals(6, kids[0].age)
        assertTrue(kids[0].birthdaySoon)

        val last = assertIs<StorySlide.LastTime>(slides.single { it is StorySlide.LastTime })
        assertEquals(listOf("Rui"), last.with)
        assertEquals("She wants to learn piano", last.note)

        assertEquals(StorySlide.Tastes(listOf("Jazz", "Bouldering"), listOf("Crowds")), slides.single { it is StorySlide.Tastes })
        assertEquals(StorySlide.Notes("Partner: Tiago"), slides.single { it is StorySlide.Notes })
        assertEquals(StorySlide.ToDo(listOf("Return the book")), slides.single { it is StorySlide.ToDo })
        assertEquals(listOf("Piano lessons"), assertIs<StorySlide.Ideas>(slides.single { it is StorySlide.Ideas }).ideas.map { it.title })

        val outro = assertIs<StorySlide.Outro>(slides.last())
        assertEquals(listOf("New job at a design studio", "Knee surgery", "Bouldering", "Mia", "Exam", "Leo"), outro.askAbout)
    }

    @Test
    fun withoutAMeetupRecentThingsCountAsNew() = runTest {
        val g = TestGraph(listOf(ana))
        val story = g.observeCatchUpStory(ana.id).first()!!
        val intro = assertIs<StorySlide.Intro>(story.slides.first())
        assertNull(intro.lastSeen)
        // 60-day window from 2026-03-10: "Old news" (Jan 1) is out, everything from March is in.
        assertEquals(4, intro.freshCount)
        assertTrue(story.slides.none { it is StorySlide.LastTime })
    }

    @Test
    fun aBareFriendStillGetsAnIntroAndAnOutro() = runTest {
        val g = TestGraph(listOf(rui))
        val story = g.observeCatchUpStory(rui.id).first()!!
        assertEquals(2, story.slides.size)
        assertEquals(StorySlide.Outro(emptyList()), story.slides.last())
        assertNull(g.observeCatchUpStory(FriendId("gone")).first())
    }

    @Test
    fun recentBirthdayIsShownAsPast() = runTest {
        val g = TestGraph(listOf(friend("bo") { copy(birthday = Birthday(3, 5, 2000)) }))
        val detail = g.observeFriendDetail(FriendId("bo")).first()!!
        val story = CatchUpStoryBuilder.build(detail, emptyList(), g.clock.now(), g.clock.timeZone)
        val b = assertIs<StorySlide.Birthday>(story.slides.single { it is StorySlide.Birthday })
        assertTrue(b.isPast)
        assertEquals(26, b.turning)
    }

    @Test
    fun bubblesShowWhoYouAreSeeingTodayOrTomorrow() = runTest {
        val g = graph()
        fun plan(id: String, date: LocalDate, who: List<FriendId>, status: PlanStatus = PlanStatus.PLANNED) =
            Plan(id, who, "Lunch", date, status = status, createdAt = at(2026, 3, 1))
        g.planRepo.upsert(plan("p1", LocalDate(2026, 3, 11), listOf(rui.id)))
        g.planRepo.upsert(plan("p2", LocalDate(2026, 3, 10), listOf(ana.id, rui.id)))
        g.planRepo.upsert(plan("p3", LocalDate(2026, 3, 12), listOf(ana.id)))
        g.planRepo.upsert(plan("p4", LocalDate(2026, 3, 10), listOf(rui.id), PlanStatus.SKIPPED))
        val bubbles = g.observeStoryBubbles().first()
        assertEquals(listOf("ana", "rui"), bubbles.map { it.friend.id.value })
        assertEquals(LocalDate(2026, 3, 10), bubbles.first().meetingOn)
        assertEquals(4, bubbles.first().freshCount)
        assertEquals(0, bubbles.last().freshCount)
    }
}
