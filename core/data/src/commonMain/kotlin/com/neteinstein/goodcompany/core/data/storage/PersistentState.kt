package com.neteinstein.goodcompany.core.data.storage

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * A JSON document persisted in a key-value [Settings] store (SharedPreferences / NSUserDefaults /
 * localStorage) and exposed as a hot [StateFlow].
 *
 * App data moved to SQLite (issue #14). This now holds only [DeviceSettings][com.neteinstein.goodcompany.core.data.repository.DeviceSettings]
 * (needed before the database opens) and backs the JSON reference repositories that the SQLite ones are tested against.
 *
 * Corrupt or schema-incompatible data falls back to [default] rather than crashing the app.
 */
class PersistentState<T>(
    private val settings: Settings,
    private val key: String,
    private val serializer: KSerializer<T>,
    private val default: T,
    private val json: Json = DefaultJson,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(load())
    val flow: StateFlow<T> = state.asStateFlow()

    val value: T get() = state.value

    suspend fun update(transform: (T) -> T) {
        mutex.withLock {
            val next = transform(state.value)
            if (next != state.value) {
                settings.putString(key, json.encodeToString(serializer, next))
                state.value = next
            }
        }
    }

    private fun load(): T = settings.getStringOrNull(key)
        ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
        ?: default

    companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }
    }
}

/** The JSON keys app data used to live under. Read only by `LegacyJsonImport` (and the reference repositories). */
internal object Keys {
    const val FRIENDS = "friends.v1"
    const val HANGOUTS = "hangouts.v1"
    const val REMINDERS = "reminders.v1"
    const val INTERACTIONS = "interactions.v1"
    const val MESSAGES = "messages.v1"
    const val JOURNAL = "journal.v1" // only the JSON reference repository: journal entries came after SQLite
    const val IDEAS = "ideas.v1"
    const val SETTINGS = "settings.v1"
    const val GROUPS = "groups.v1"
    const val PLANS = "plans.v1"
}
