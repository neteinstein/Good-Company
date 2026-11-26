package org.goodcompany.app.core.domain.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Issue #86: everyone has a closeness and "real friend" ([Friend.isCloseFriend]) follows it. Backups and the old JSON
 * store still carry `isCloseFriend`, so:
 * - writing adds `isCloseFriend` (= the level isn't Acquaintances), so older builds read the same people as real friends;
 * - reading applies the same rule as migration `19.sqm` ([closenessFrom]).
 *
 * Decision 2026-11-21 ("Catch-up · what counts") turned the `contactCountsAsCatchUp` switch into [Friend.catchUpCounts]:
 * writing keeps the switch (on for Any contact) for older builds, and reading a backup without the new field takes
 * the switch on as Any contact and off as the default (the rule of migration `32.sqm`).
 */
object FriendSerializer : JsonTransformingSerializer<Friend>(Friend.generatedSerializer()) {
    private const val CLOSENESS = "circle"
    private const val REAL_FRIEND = "isCloseFriend"
    private const val COUNTS = "catchUpCounts"
    private const val LEGACY_COUNTS = "contactCountsAsCatchUp"

    override fun transformSerialize(element: JsonElement): JsonElement {
        val obj = element as? JsonObject ?: return element
        val level = obj[CLOSENESS]?.jsonPrimitive?.contentOrNull?.let(::closenessNamed) ?: Closeness.ACQUAINTANCE
        // Both always written: an older build reads a missing level as "not set" and a missing flag as false.
        val anyContact = obj[COUNTS]?.jsonPrimitive?.contentOrNull == CatchUpCounts.ANY_CONTACT.name
        return JsonObject(
            obj + (CLOSENESS to JsonPrimitive(level.name)) + (REAL_FRIEND to JsonPrimitive(level.isRealFriend)) +
                (LEGACY_COUNTS to JsonPrimitive(anyContact)),
        )
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val obj = element as? JsonObject ?: return element
        val stored = obj[CLOSENESS]?.let { (it as? JsonPrimitive)?.contentOrNull }?.let(::closenessNamed)
        val real = (obj[REAL_FRIEND] as? JsonPrimitive)?.booleanOrNull ?: false
        val level = closenessFrom(stored, real)
        val counts = (obj[COUNTS] as? JsonPrimitive)?.contentOrNull?.let { name -> CatchUpCounts.entries.firstOrNull { it.name == name } }
            ?: if ((obj[LEGACY_COUNTS] as? JsonPrimitive)?.booleanOrNull == true) CatchUpCounts.ANY_CONTACT else CatchUpCounts.DEFAULT
        return JsonObject(obj - REAL_FRIEND - LEGACY_COUNTS + (CLOSENESS to JsonPrimitive(level.name)) + (COUNTS to JsonPrimitive(counts.name)))
    }

    private fun closenessNamed(name: String): Closeness? = Closeness.entries.firstOrNull { it.name == name }
}

/**
 * The level of someone stored before issue #86 (a level or none, plus the old "real friend" flag), the same rule as
 * migration `19.sqm`: no level → Close for a real friend, else Acquaintances; a level without the flag → Acquaintances
 * (what the People tab showed); a real friend keeps their level.
 */
fun closenessFrom(stored: Closeness?, realFriend: Boolean): Closeness = when {
    !realFriend -> Closeness.ACQUAINTANCE
    stored == null -> Closeness.CLOSE
    else -> stored
}
