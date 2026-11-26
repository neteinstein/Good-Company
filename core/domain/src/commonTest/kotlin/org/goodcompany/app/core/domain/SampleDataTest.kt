package org.goodcompany.app.core.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.goodcompany.app.core.domain.model.AgendaItem
import org.goodcompany.app.core.domain.usecase.AgendaBuilder
import org.goodcompany.app.core.domain.usecase.HasSampleDataUseCase
import org.goodcompany.app.core.domain.usecase.LoadSampleDataUseCase
import org.goodcompany.app.core.domain.usecase.RemoveSampleDataUseCase
import org.goodcompany.app.core.domain.usecase.SampleData
import org.goodcompany.app.core.testing.FakeFriendRepository
import org.goodcompany.app.core.testing.FakeGroupRepository
import org.goodcompany.app.core.testing.FakeHangoutRepository
import org.goodcompany.app.core.testing.FakeIdeaRepository
import org.goodcompany.app.core.testing.FakeInteractionRepository
import org.goodcompany.app.core.testing.FakePlanRepository
import org.goodcompany.app.core.testing.FakeReminderRepository
import org.goodcompany.app.core.testing.FixedClock
import org.goodcompany.app.core.testing.Fixtures.friend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SampleDataTest {
    private val clock = FixedClock()
    private val mine = friend("me-ana", "My real Ana")
    private val friends = FakeFriendRepository(listOf(mine))
    private val hangouts = FakeHangoutRepository()
    private val reminders = FakeReminderRepository()
    private val ideas = FakeIdeaRepository()
    private val groups = FakeGroupRepository()
    private val plans = FakePlanRepository()
    private val interactions = FakeInteractionRepository()

    @Test
    fun loadIsIdempotentAndRemoveKeepsUserData() = runTest {
        val load = LoadSampleDataUseCase(friends, hangouts, reminders, ideas, groups, plans, interactions, clock)
        load()
        load()
        val bundle = SampleData.build(clock)
        assertEquals(bundle.friends.size + 1, friends.friends.value.size)
        assertEquals(bundle.hangouts.size, hangouts.hangouts.value.size)
        assertEquals(bundle.ideas.size, ideas.ideas.value.size)
        assertEquals(bundle.groups.size, groups.groups.value.size)
        assertEquals(bundle.plans.size, plans.plans.value.size)
        assertEquals(bundle.interactions.size, interactions.interactions.value.size)
        assertEquals(bundle.journal.size, interactions.journal.value.size)
        assertTrue(HasSampleDataUseCase(friends)().first())

        RemoveSampleDataUseCase(friends, hangouts, reminders, ideas, groups, plans, interactions)()
        assertEquals(listOf(mine), friends.friends.value)
        assertTrue(hangouts.hangouts.value.isEmpty())
        assertTrue(reminders.reminders.value.isEmpty())
        assertTrue(ideas.ideas.value.all { it.dismissed })
        assertTrue(groups.groups.value.isEmpty())
        assertTrue(plans.plans.value.isEmpty())
        assertFalse(HasSampleDataUseCase(friends)().first())
    }

    @Test
    fun sampleAlwaysHasSomethingComingUpAndOverdue() {
        val b = SampleData.build(clock)
        val agenda = AgendaBuilder.build(b.friends, b.reminders, b.hangouts, clock.today(), clock.timeZone, links = b.links)
        assertTrue(agenda.any { it is AgendaItem.FriendBirthday && it.date == clock.today() })
        assertTrue(agenda.any { it is AgendaItem.KidBirthday })
        assertTrue(agenda.any { it is AgendaItem.FriendEvent })
        assertTrue(agenda.any { it is AgendaItem.MeetupOverdue })
        assertTrue(b.friends.all { SampleData.isSample(it.id.value) })
        // Every hangout references sample friends only.
        val ids = b.friends.map { it.id }.toSet()
        assertTrue(b.hangouts.all { h -> h.friendIds.all { it in ids } })
    }
}
