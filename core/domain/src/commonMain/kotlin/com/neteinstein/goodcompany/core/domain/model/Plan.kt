package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A meetup you've planned. It doesn't count as "met" until you confirm it happened: the day after, the app asks
 * "Did you meet?" and a yes turns it into a check-in.
 */
@Serializable
data class Plan(
    val id: String,
    val friendIds: List<FriendId>,
    val title: String,
    val date: LocalDate,
    val place: String? = null,
    /** The AI idea this came from, if any. */
    val ideaId: String? = null,
    val status: PlanStatus = PlanStatus.PLANNED,
    val createdAt: Instant,
)

enum class PlanStatus { PLANNED, HAPPENED, SKIPPED }
