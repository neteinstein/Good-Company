package com.neteinstein.goodcompany.core.data

import com.neteinstein.goodcompany.core.data.source.ContactRowPlanner
import com.neteinstein.goodcompany.core.data.source.ContactRowPlanner.Step
import com.neteinstein.goodcompany.core.data.source.ContactRowPlanner.Stored
import com.neteinstein.goodcompany.core.data.source.ContactRowPlanner.Wanted
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContactRowPlannerTest {
    private fun wanted(kind: String, key: String) = Wanted(kind, key, key)

    private fun plan(stored: List<Stored>, vararg w: Wanted<String>) = ContactRowPlanner.plan(stored, w.toList())

    @Test
    fun nothingStored_insertsEverything() {
        val steps = plan(emptyList(), wanted("note", "n1"), wanted("child", "Maria"))
        assertEquals(listOf<Step<String>>(Step.Insert(wanted("note", "n1")), Step.Insert(wanted("child", "Maria"))), steps)
    }

    @Test
    fun sameRows_changeNothing() {
        val stored = listOf(Stored(1, "note", "n1"), Stored(2, "child", "Maria"))
        assertTrue(plan(stored, wanted("note", "n1"), wanted("child", "Maria")).isEmpty())
    }

    @Test
    fun editedNote_updatesTheOneNoteInPlace() {
        val steps = plan(listOf(Stored(7, "note", "old")), wanted("note", "new"))
        assertEquals(listOf<Step<String>>(Step.Update(7, wanted("note", "new"))), steps)
    }

    @Test
    fun renamedKid_reusesItsRow_andLeavesTheOthersAlone() {
        val stored = listOf(Stored(1, "child", "Maria"), Stored(2, "child", "Joao"))
        val steps = plan(stored, wanted("child", "Maria"), wanted("child", "Joaquim"))
        assertEquals(listOf<Step<String>>(Step.Update(2, wanted("child", "Joaquim"))), steps)
    }

    @Test
    fun anAddedItem_isInsertedNextToTheExistingOnes() {
        val steps = plan(listOf(Stored(1, "child", "Maria")), wanted("child", "Maria"), wanted("child", "Joao"))
        assertEquals(listOf<Step<String>>(Step.Insert(wanted("child", "Joao"))), steps)
    }

    @Test
    fun anItemRemovedFromTheApp_isLeftOnTheContact() {
        // No wanted row for "Joao": nothing is planned at all, so the stored row simply stays.
        val stored = listOf(Stored(1, "child", "Maria"), Stored(2, "child", "Joao"))
        assertTrue(plan(stored, wanted("child", "Maria")).isEmpty())
        assertTrue(plan(stored).isEmpty())
    }

    @Test
    fun aRemovedItemsRow_isReusedByANewOne() {
        val stored = listOf(Stored(1, "event", "surgery"), Stored(2, "event", "exam"))
        val steps = plan(stored, wanted("event", "exam"), wanted("event", "interview"))
        assertEquals(listOf<Step<String>>(Step.Update(1, wanted("event", "interview"))), steps)
    }

    @Test
    fun kindsNeverMix() {
        // A birthday row is not reused for another kind of event, nor a note for a child.
        val stored = listOf(Stored(1, "birthday", "1990-05-04"), Stored(2, "note", "old"))
        val steps = plan(stored, wanted("event", "surgery"), wanted("child", "Maria"))
        assertEquals(listOf<Step<String>>(Step.Insert(wanted("event", "surgery")), Step.Insert(wanted("child", "Maria"))), steps)
    }

    @Test
    fun duplicateWantedRows_areWrittenOnce() {
        val steps = plan(emptyList(), wanted("child", "Maria"), wanted("child", "Maria"))
        assertEquals(1, steps.size)
    }

    @Test
    fun updatesTargetTheOldestFreeRowFirst() {
        val stored = listOf(Stored(9, "note", "b"), Stored(3, "note", "a"))
        val steps = plan(stored, wanted("note", "new"))
        assertEquals(listOf<Step<String>>(Step.Update(3, wanted("note", "new"))), steps)
    }
}
