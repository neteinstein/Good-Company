package com.neteinstein.goodcompany.core.data.source

/**
 * Decides how to bring the rows the app keeps on a contact up to date: **one row per item, never a delete.**
 *
 * Rows are grouped by *kind* (all notes, all children, all birthdays, all other dated events). Within a kind:
 *  1. A wanted row that is already stored (same [Wanted.key]) is left alone.
 *  2. A wanted row that isn't stored takes over a stored row of the same kind that nothing wanted any more, by
 *     updating it in place. A renamed kid, a retitled event or an edited note therefore replaces its old row instead
 *     of piling up next to it.
 *  3. If no such stored row is free, the wanted row is inserted.
 *
 * Stored rows nobody wants (and nobody can reuse) are simply left as they are. [Step] has no delete on purpose: the
 * plan cannot express taking data off a contact.
 */
internal object ContactRowPlanner {
    /** A row already on the contact. [kind] groups interchangeable rows; [key] is what makes two rows "the same". */
    data class Stored(val id: Long, val kind: String, val key: String)

    /** A row the app wants on the contact. [payload] is whatever the caller needs to write it. */
    data class Wanted<T>(val kind: String, val key: String, val payload: T)

    sealed interface Step<out T> {
        /** Overwrite stored row [storedId] with [row]. */
        data class Update<T>(val storedId: Long, val row: Wanted<T>) : Step<T>

        data class Insert<T>(val row: Wanted<T>) : Step<T>
    }

    fun <T> plan(stored: List<Stored>, wanted: List<Wanted<T>>): List<Step<T>> {
        val steps = mutableListOf<Step<T>>()
        wanted.distinctBy { it.kind to it.key }.groupBy { it.kind }.forEach { (kind, rows) ->
            val pool = stored.filter { it.kind == kind }
            val storedKeys = pool.mapTo(HashSet()) { it.key }
            val wantedKeys = rows.mapTo(HashSet()) { it.key }
            // Stored rows no wanted row matches exactly are free to be reused, oldest first.
            val free = ArrayDeque(pool.filter { it.key !in wantedKeys }.distinctBy { it.key }.sortedBy { it.id })
            rows.filter { it.key !in storedKeys }.forEach { row ->
                val slot = free.removeFirstOrNull()
                steps += if (slot != null) Step.Update(slot.id, row) else Step.Insert(row)
            }
        }
        return steps
    }
}
