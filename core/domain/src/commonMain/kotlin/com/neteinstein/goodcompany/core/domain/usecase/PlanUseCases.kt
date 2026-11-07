package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/** Open plans, soonest first. */
class ObservePlansUseCase(private val plans: PlanRepository) {
    operator fun invoke(): Flow<List<Plan>> = plans.plans.map { list ->
        list.filter { it.status == PlanStatus.PLANNED }.sortedBy { it.date }
    }

    fun forFriend(id: FriendId): Flow<List<Plan>> = invoke().map { list -> list.filter { id in it.friendIds } }
}

/** Creates (id null) or reschedules a plan. */
class SavePlanUseCase(
    private val plans: PlanRepository,
    private val ideas: IdeaRepository,
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        id: String?,
        title: String,
        friendIds: Collection<FriendId>,
        date: LocalDate,
        place: String? = null,
        ideaId: String? = null,
    ): Plan {
        require(title.isNotBlank()) { "Title required" }
        require(friendIds.isNotEmpty()) { "Pick at least one friend" }
        val existing = id?.let { pid -> plans.plans.first().firstOrNull { it.id == pid } }
        val plan = Plan(
            id = existing?.id ?: ids.next(),
            friendIds = friendIds.distinct(),
            title = title.trim(),
            date = date,
            place = place?.trim()?.ifBlank { null },
            ideaId = ideaId ?: existing?.ideaId,
            status = PlanStatus.PLANNED,
            createdAt = existing?.createdAt ?: clock.now(),
        )
        plans.upsert(plan)
        if (ideaId != null && existing == null) ideas.markProposed(ideaId, clock.now())
        return plan
    }
}

/**
 * Answers "Did you meet?". A yes logs a check-in on the plan's day (it then counts towards rhythms and warmth);
 * a no just closes the plan. Planned dates never count as met on their own.
 */
class ConfirmPlanUseCase(
    private val plans: PlanRepository,
    private val friends: FriendRepository,
    private val checkIn: CheckInUseCase,
    private val clock: AppClock,
) {
    suspend operator fun invoke(id: String, happened: Boolean) {
        val plan = plans.plans.first().firstOrNull { it.id == id } ?: return
        if (plan.status != PlanStatus.PLANNED) return
        if (happened) {
            val present = plan.friendIds.filter { friends.get(it) != null }
            if (present.isNotEmpty()) {
                val at = if (plan.date >= clock.today()) clock.now() else plan.date.atTime(LocalTime(NOON, 0)).toInstant(clock.timeZone)
                checkIn(present, placeName = plan.place, note = plan.title, at = at)
            }
        }
        plans.upsert(plan.copy(status = if (happened) PlanStatus.HAPPENED else PlanStatus.SKIPPED))
    }

    private companion object {
        const val NOON = 12
    }
}

class CancelPlanUseCase(private val plans: PlanRepository) {
    suspend operator fun invoke(id: String) = plans.delete(id)
}

/** Puts back a plan cancelled a moment ago (snackbar "Undo"). */
class RestorePlanUseCase(private val plans: PlanRepository) {
    suspend operator fun invoke(plan: Plan) = plans.upsert(plan)
}
