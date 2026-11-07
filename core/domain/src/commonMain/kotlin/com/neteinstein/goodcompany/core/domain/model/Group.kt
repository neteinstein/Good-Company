package com.neteinstein.goodcompany.core.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** A saved group ("Football crew") with an optional "meet all together every N days" rhythm. */
@Serializable
data class FriendGroup(
    val id: String,
    val name: String,
    val emoji: String = "👥",
    val memberIds: List<FriendId> = emptyList(),
    val meetEveryDays: Int? = null,
    val snoozedUntil: LocalDate? = null,
)
