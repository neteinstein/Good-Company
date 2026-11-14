package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Repeat
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.GroupRepository
import com.neteinstein.goodcompany.core.domain.repository.IdeaRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/** Open plans, soonest first. */
class ObservePlansUseCase(private val plans: PlanRepository, private val groups: GroupRepository) {
    operator fun invoke(): Flow<List<Plan>> = plans.plans.map { list ->
        list.filter { it.status == PlanStatus.PLANNED }.sortedBy { it.date }
    }

    /** Plans with [id], on their own or as a member of the plan's circle. */
    fun forFriend(id: FriendId): Flow<List<Plan>> = combine(invoke(), groups.groups) { list, circles ->
        val mine = circles.filter { id in it.memberIds }.mapTo(mutableSetOf()) { it.id }
        list.filter { id in it.friendIds || it.circleId in mine }
    }

    /** A circle's plans, soonest first (its card shows the first). */
    fun forCircle(circleId: String): Flow<List<Plan>> = invoke().map { list -> list.filter { it.circleId == circleId } }
}

/** What the plan editor hands back. [repeat]'s start is ignored: a series is counted from [date]. */
data class PlanInput(
    val title: String,
    val friendIds: List<FriendId>,
    val date: LocalDate,
    val place: String? = null,
    val circleId: String? = null,
    val activity: HangoutActivity? = null,
    val repeat: Repeat? = null,
)

/**
 * Creates (id null) or changes a plan: with people, a circle or both; an activity; optionally a [repeat], which is
 * counted from [date] (the first occurrence).
 */
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
        circleId: String? = null,
        activity: HangoutActivity? = null,
        repeat: Repeat? = null,
    ): Plan {
        require(title.isNotBlank()) { "Title required" }
        require(friendIds.isNotEmpty() || circleId != null) { "Pick at least one friend or a circle" }
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
            circleId = circleId,
            activity = activity,
            repeat = repeat?.copy(start = date, until = repeat.until?.takeIf { it >= date }),
            skippedDates = if (repeat == null) emptyList() else existing?.skippedDates.orEmpty().filter { it > date },
        )
        plans.upsert(plan)
        if (ideaId != null && existing == null) ideas.markProposed(ideaId, clock.now())
        return plan
    }

    suspend fun save(id: String?, input: PlanInput, ideaId: String? = null): Plan = invoke(
        id = id,
        title = input.title,
        friendIds = input.friendIds,
        date = input.date,
        place = input.place,
        ideaId = ideaId,
        circleId = input.circleId,
        activity = input.activity,
        repeat = input.repeat,
    )
}

/**
 * Answers "Did you meet?" for one occurrence ([day], the open one when null). A yes logs a check-in on that day with
 * the plan's people and activity (it then counts towards rhythms and warmth); a no just closes it. Either way a
 * recurring plan moves on to its next occurrence ([Plan.closeOccurrence]). Planned dates never count as met on their own.
 *
 * A circle plan asks who came instead (the Log sheet, members unticked, F20): that sheet logs the meetup itself and
 * then calls this with [logCheckIn] false.
 */
class ConfirmPlanUseCase(
    private val plans: PlanRepository,
    private val friends: FriendRepository,
    private val checkIn: CheckInUseCase,
    private val clock: AppClock,
) {
    suspend operator fun invoke(id: String, happened: Boolean, day: LocalDate? = null, logCheckIn: Boolean = true) {
        val plan = plans.plans.first().firstOrNull { it.id == id } ?: return
        if (plan.status != PlanStatus.PLANNED) return
        val on = day ?: plan.date
        if (on != plan.date && on !in plan.occurrences(on, on)) return
        if (happened && logCheckIn) {
            val present = plan.friendIds.filter { friends.get(it) != null }
            if (present.isNotEmpty()) {
                val at = if (on >= clock.today()) clock.now() else on.atTime(LocalTime(NOON, 0)).toInstant(clock.timeZone)
                checkIn(present, placeName = plan.place, note = plan.title, at = at, activity = plan.activity)
            }
        }
        plans.upsert(plan.closeOccurrence(on, happened))
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
