package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository

/** Why a name of your own (an activity, issue #121, or a How option, issue #152) wasn't taken. */
enum class ActivityNameProblem { BLANK, TAKEN }

/**
 * Your own activities (issue #121, Settings → Your data → Activities): add, rename, reorder and remove. They are a list
 * of names in [com.neteinstein.goodcompany.core.domain.model.AppSettings.customActivities]. Meetups and plans store the
 * name itself, so renaming or removing one changes only what the pickers offer: past entries keep their label.
 * Names are compared ignoring case and accents ([Tags.normalize]).
 */
class CustomActivitiesUseCase(settings: SettingsRepository) :
    CustomNamesUseCase(settings, AppSettings::customActivities, { s, list -> s.copy(customActivities = list) })

/**
 * Your own "How?" options (issue #152, Settings → Your data → How): add, rename, reorder and remove, like
 * [CustomActivitiesUseCase]. They are [AppSettings.customLogKinds]; an entry logged with one is an "Other" contact with
 * the name as its label, so renaming or removing one keeps the name on past entries. A name is at most
 * [LogInteractionUseCase.LABEL_MAX] characters, the longest label an entry keeps.
 */
class CustomLogKindsUseCase(settings: SettingsRepository) :
    CustomNamesUseCase(
        settings,
        AppSettings::customLogKinds,
        { s, list -> s.copy(customLogKinds = list) },
        maxLength = LogInteractionUseCase.LABEL_MAX,
    )

/** A list of names of your own kept in [AppSettings] ([read] / [write]); names longer than [maxLength] are cut. */
abstract class CustomNamesUseCase(
    private val settings: SettingsRepository,
    private val read: (AppSettings) -> List<String>,
    private val write: (AppSettings, List<String>) -> AppSettings,
    private val maxLength: Int = Int.MAX_VALUE,
) {
    /** Adds [name] at the end; null when added, else why not. */
    suspend fun add(name: String): ActivityNameProblem? = edit { list ->
        val typed = name.trim().take(maxLength).trim()
        problem(list, typed, except = null)?.let { return@edit it to list }
        null to list + typed
    }

    /** Renames [from] to [to] (in place); null when done, else why not. Past entries keep [from]. */
    suspend fun rename(from: String, to: String): ActivityNameProblem? = edit { list ->
        val typed = to.trim().take(maxLength).trim()
        val at = list.indexOf(from)
        if (at < 0 || typed == from) return@edit null to list
        problem(list, typed, except = from)?.let { return@edit it to list }
        null to list.toMutableList().apply { set(at, typed) }
    }

    /** Removes [name] from the pickers; returns where it was (for Undo), or -1. */
    suspend fun remove(name: String): Int {
        var at = -1
        settings.update { s ->
            val list = read(s)
            at = list.indexOf(name)
            if (at < 0) s else write(s, list - name)
        }
        return at
    }

    /** Undo for [remove]: puts [name] back at [at] (or at the end). */
    suspend fun restore(name: String, at: Int) {
        settings.update { s ->
            val list = read(s)
            if (list.any { Tags.normalize(it) == Tags.normalize(name) }) {
                s
            } else {
                write(s, list.toMutableList().apply { add(at.coerceIn(0, size), name) })
            }
        }
    }

    /** Moves the one at [from] to [to]. */
    suspend fun move(from: Int, to: Int) {
        settings.update { s ->
            val list = read(s)
            if (from !in list.indices || to !in list.indices ||
                from == to
            ) {
                s
            } else {
                write(s, list.toMutableList().apply { add(to, removeAt(from)) })
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
            val current = read(s)
            val (problem, list) = change(current)
            result = problem
            if (list == current) s else write(s, list)
        }
        return result
    }
}
