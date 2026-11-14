package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.usecase.AgendaBuilder
import com.neteinstein.goodcompany.core.domain.usecase.HasSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.LoadSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.RemoveSampleDataUseCase
import com.neteinstein.goodcompany.core.domain.usecase.SampleData
import com.neteinstein.goodcompany.core.testing.FakeFriendRepository
import com.neteinstein.goodcompany.core.testing.FakeGroupRepository
import com.neteinstein.goodcompany.core.testing.FakeHangoutRepository
import com.neteinstein.goodcompany.core.testing.FakeIdeaRepository
import com.neteinstein.goodcompany.core.testing.FakeInteractionRepository
import com.neteinstein.goodcompany.core.testing.FakePlanRepository
import com.neteinstein.goodcompany.core.testing.FakeReminderRepository
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
        val agenda = AgendaBuilder.build(b.friends, b.reminders, b.hangouts, clock.today(), clock.timeZone)
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
