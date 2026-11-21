package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository

/** Why an activity name wasn't taken (issue #121). */
enum class ActivityNameProblem { BLANK, TAKEN }

/**
 * Your own activities (issue #121, Settings → Your data → Activities): add, rename, reorder and remove. They are a list
 * of names in [com.neteinstein.goodcompany.core.domain.model.AppSettings.customActivities]. Meetups and plans store the
 * name itself, so renaming or removing one changes only what the pickers offer: past entries keep their label.
 * Names are compared ignoring case and accents ([Tags.normalize]).
 */
class CustomActivitiesUseCase(private val settings: SettingsRepository) {
    /** Adds [name] at the end; null when added, else why not. */
    suspend fun add(name: String): ActivityNameProblem? = edit { list ->
        val typed = name.trim()
        problem(list, typed, except = null)?.let { return@edit it to list }
        null to list + typed
    }

    /** Renames [from] to [to] (in place); null when done, else why not. Past entries keep [from]. */
    suspend fun rename(from: String, to: String): ActivityNameProblem? = edit { list ->
        val typed = to.trim()
        val at = list.indexOf(from)
        if (at < 0 || typed == from) return@edit null to list
        problem(list, typed, except = from)?.let { return@edit it to list }
        null to list.toMutableList().apply { set(at, typed) }
    }

    /** Removes [name] from the pickers; returns where it was (for Undo), or -1. */
    suspend fun remove(name: String): Int {
        var at = -1
        settings.update { s ->
            at = s.customActivities.indexOf(name)
            if (at < 0) s else s.copy(customActivities = s.customActivities - name)
        }
        return at
    }

    /** Undo for [remove]: puts [name] back at [at] (or at the end). */
    suspend fun restore(name: String, at: Int) {
        settings.update { s ->
            if (s.customActivities.any { Tags.normalize(it) == Tags.normalize(name) }) {
                s
            } else {
                s.copy(customActivities = s.customActivities.toMutableList().apply { add(at.coerceIn(0, size), name) })
            }
        }
    }

    /** Moves the one at [from] to [to]. */
    suspend fun move(from: Int, to: Int) {
        settings.update { s ->
            val list = s.customActivities
            if (from !in list.indices || to !in list.indices ||
                from == to
            ) {
                s
            } else {
                s.copy(customActivities = list.toMutableList().apply { add(to, removeAt(from)) })
            }
        }
    }

    private fun problem(list: List<String>, typed: String, except: String?): ActivityNameProblem? = when {
        typed.isEmpty() -> ActivityNameProblem.BLANK
        list.any { it != except && Tags.normalize(it) == Tags.normalize(typed) } -> ActivityNameProblem.TAKEN
        else -> null
    }

    private suspend fun edit(change: (List<String>) -> Pair<ActivityNameProblem?, List<String>>): ActivityNameProblem? {
        var result: ActivityNameProblem? = null
        settings.update { s ->
            val (problem, list) = change(s.customActivities)
            result = problem
            if (list == s.customActivities) s else s.copy(customActivities = list)
        }
        return result
    }
}
