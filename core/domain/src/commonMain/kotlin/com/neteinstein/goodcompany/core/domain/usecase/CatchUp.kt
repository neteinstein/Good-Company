package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.CatchUpCounts
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.Interaction
import com.neteinstein.goodcompany.core.domain.model.InteractionSource
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * Which contacts at a distance restart the rhythm of someone on [CatchUpCounts.DEFAULT] (Settings → How). [kinds] are the
 * built-in ones that count ([LogKind.MEETUP] always does, [LogKind.JOURNAL] never); [custom] the How options of your own
 * that count, out of all of them ([own]). A contact logged with one of your own is an [InteractionType.OTHER] with its name
 * as the label; an Other whose label is none of yours follows [LogKind.OTHER]. A call or video call read from the call
 * log counts only from [CALL_LOG_MIN_SECONDS] (a missed call or a quick callback isn't catching up).
 */
data class CatchUpPolicy(
    val kinds: Set<LogKind> = DEFAULT_KINDS,
    val custom: Set<String> = emptySet(),
    val own: List<String> = emptyList(),
) {
    private val customKeys = custom.mapTo(HashSet(), Tags::normalize)
    private val ownKeys = own.mapTo(HashSet(), Tags::normalize)

    /** Whether [interaction] with [friend] restarts their rhythm. */
    fun counts(friend: Friend, interaction: Interaction): Boolean = when {
        interaction.type == InteractionType.MEETUP -> true
        friend.catchUpCounts == CatchUpCounts.IN_PERSON_ONLY -> false
        friend.catchUpCounts == CatchUpCounts.ANY_CONTACT -> true
        else -> countsByDefault(interaction)
    }

    /** The Settings → How rule, for someone on [CatchUpCounts.DEFAULT]. */
    fun countsByDefault(interaction: Interaction): Boolean {
        if (interaction.type == InteractionType.MEETUP) return true
        if (interaction.isShortLoggedCall()) return false
        val kind = LogKind.parse(interaction.type.name) ?: return false
        if (kind == LogKind.OTHER && interaction.label.isNotBlank()) {
            val key = Tags.normalize(interaction.label)
            if (key in ownKeys) return key in customKeys
        }
        return kind in kinds
    }

    /** Whether nothing at a distance can count for [friend] (In person only, or nothing ticked in Settings). */
    fun onlyInPerson(friend: Friend): Boolean = when (friend.catchUpCounts) {
        CatchUpCounts.IN_PERSON_ONLY -> true
        CatchUpCounts.ANY_CONTACT -> false
        CatchUpCounts.DEFAULT -> kinds.none { it.isRemote } && customKeys.none { it in ownKeys }
    }

    /** Whether a [kind] logged by hand (one of your own when [customName] is set) restarts [friend]'s rhythm. */
    fun countsWhenLogged(friend: Friend, kind: LogKind, customName: String? = null): Boolean {
        val type = kind.interactionType ?: return false
        return counts(friend, Interaction(friend.id, type, Instant.DISTANT_PAST, label = customName.orEmpty()))
    }

    private fun Interaction.isShortLoggedCall(): Boolean = source == InteractionSource.CALL_LOG &&
        (type == InteractionType.CALL || type == InteractionType.VIDEO_CALL) &&
        (durationSeconds ?: 0) < CALL_LOG_MIN_SECONDS

    companion object {
        /** Five minutes. */
        const val CALL_LOG_MIN_SECONDS = 300

        /** The built-in ways of keeping in touch that count with nothing set in Settings. */
        val DEFAULT_KINDS: Set<LogKind> =
            setOf(LogKind.MEETUP, LogKind.VIDEO_CALL, LogKind.CALL, LogKind.LONG_CHAT, LogKind.GAME)

        /** The built-in kinds whose box can be ticked in Settings: every contact at a distance. */
        val CHOOSABLE: List<LogKind> = LogKind.DEFAULT_ORDER.filter { it.isRemote }
    }
}

/** What counts as catching up, as Settings has it now ([CadenceRules.catchUp]). */
class ObserveCatchUpPolicyUseCase(private val settings: SettingsRepository) {
    operator fun invoke(): Flow<CatchUpPolicy> = settings.settings.map { CadenceRules.from(it).catchUp }.distinctUntilChanged()
}
