package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanAnswer
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

    /** Cancelled plans (issue #100: kept and shown struck through), latest date first. */
    fun cancelled(): Flow<List<Plan>> = plans.plans.map { list ->
        list.filter { it.status == PlanStatus.CANCELLED }.sortedByDescending { it.date }
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
    /** One of your own activities, by name (issue #121), when [activity] is null. */
    val activityName: String? = null,
    /** The time of day (issue #167); null = just the day. */
    val time: LocalTime? = null,
    /** Issue #170: the maps link the place came from (a share), and its coordinates when the link had them. */
    val mapLink: String? = null,
    val location: GeoPoint? = null,
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
        activityName: String? = null,
        time: LocalTime? = null,
        mapLink: String? = null,
        location: GeoPoint? = null,
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
            activityName = activityName?.trim()?.ifBlank { null }.takeIf { activity == null },
            repeat = repeat?.copy(start = date, until = repeat.until?.takeIf { it >= date }),
            skippedDates = if (repeat == null) emptyList() else existing?.skippedDates.orEmpty().filter { it > date },
            // Minutes only: a picked time never carries seconds into storage or the calendar.
            time = time?.let { LocalTime(it.hour, it.minute) },
            // Issue #170: the link and its point describe the place, so a plan without a place keeps neither.
            mapLink = mapLink?.trim()?.ifBlank { null }.takeIf { place?.isNotBlank() == true },
            latitude = location?.latitude.takeIf { place?.isNotBlank() == true },
            longitude = location?.longitude.takeIf { place?.isNotBlank() == true },
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
        activityName = input.activityName,
        time = input.time,
        mapLink = input.mapLink,
        location = input.location,
    )
}

/**
 * Answers "Did you meet?" for one occurrence ([day], the open one when null). A yes logs a check-in on that day with
 * the plan's people and activity (it then counts towards rhythms and warmth); a no just closes it. Either way a
 * recurring plan moves on to its next occurrence ([Plan.closeOccurrence]). Planned dates never count as met on their own.
 *
 * A circle plan asks who came instead (the Log popup, members unticked, F20): that sheet logs the meetup itself and
 * then calls this with [logCheckIn] false.
 */
class ConfirmPlanUseCase(
    private val plans: PlanRepository,
    private val friends: FriendRepository,
    private val checkIn: CheckInUseCase,
    private val clock: AppClock,
) {
    suspend operator fun invoke(id: String, happened: Boolean, day: LocalDate? = null, logCheckIn: Boolean = true) {
        val status = if (happened) PlanStatus.HAPPENED else PlanStatus.SKIPPED
        val (plan, on) = openOccurrence(id, day) ?: return
        if (happened && logCheckIn) {
            val present = plan.friendIds.filter { friends.get(it) != null }
            if (present.isNotEmpty()) {
                val at = if (on >= clock.today()) clock.now() else on.atTime(LocalTime(NOON, 0)).toInstant(clock.timeZone)
                checkIn(
                    present,
                    placeName = plan.place,
                    location = plan.location,
                    note = plan.title,
                    at = at,
                    activity = plan.activity,
                    activityName = plan.activityName,
                )
            }
        }
        plans.upsert(plan.closeOccurrence(on, status))
    }

    /**
     * "I didn't go" (issue #100): the group met without me. Closes that occurrence as [PlanStatus.NOT_ATTENDED] with no
     * check-in, so it counts for nobody's warmth or rhythm. Only for group and circle plans ([Plan.isGroup]).
     * Returns the plan as it was (for Undo), or null when nothing changed.
     */
    suspend fun notAttended(id: String, day: LocalDate? = null): Plan? {
        val (plan, on) = openOccurrence(id, day) ?: return null
        if (!plan.isGroup) return null
        plans.upsert(plan.closeOccurrence(on, PlanStatus.NOT_ATTENDED))
        return plan
    }

    private suspend fun openOccurrence(id: String, day: LocalDate?): Pair<Plan, LocalDate>? {
        val plan = plans.plans.first().firstOrNull { it.id == id } ?: return null
        if (plan.status != PlanStatus.PLANNED) return null
        val on = day ?: plan.date
        if (on != plan.date && on !in plan.occurrences(on, on)) return null
        return plan to on
    }

    private companion object {
        const val NOON = 12
    }
}

/**
 * Cancels a plan (issue #100). A cancelled plan is kept, as [PlanStatus.CANCELLED], and shown struck through. On a
 * series, [wholeSeries] false cancels only the occurrence on [day] (the open one when null): it is taken out and the
 * series goes on. Returns the plan as it was (for Undo), or null when nothing changed.
 */
class CancelPlanUseCase(private val plans: PlanRepository) {
    suspend operator fun invoke(id: String, day: LocalDate? = null, wholeSeries: Boolean = false): Plan? {
        val plan = plans.plans.first().firstOrNull { it.id == id } ?: return null
        if (plan.status != PlanStatus.PLANNED) return null
        val next = if (plan.isRecurring && !wholeSeries) {
            plan.closeOccurrence(day ?: plan.date, PlanStatus.CANCELLED)
        } else {
            plan.copy(status = PlanStatus.CANCELLED)
        }
        if (next == plan) return null
        plans.upsert(next)
        return plan
    }
}

/**
 * Deletes a plan for good (was `CancelPlanUseCase`; Undo puts it back through [RestorePlanUseCase]). On a series,
 * [wholeSeries] false deletes only the occurrence on [day] (the open one when null): it is taken out and the series
 * goes on. Returns the plan as it was (for Undo), or null when there was nothing to delete.
 */
class DeletePlanUseCase(private val plans: PlanRepository) {
    suspend operator fun invoke(id: String, day: LocalDate? = null, wholeSeries: Boolean = true): Plan? {
        val plan = plans.plans.first().firstOrNull { it.id == id } ?: return null
        if (plan.isRecurring && !wholeSeries && plan.status == PlanStatus.PLANNED) {
            val next = plan.closeOccurrence(day ?: plan.date, PlanStatus.SKIPPED)
            if (next == plan) return null
            plans.upsert(next)
        } else {
            plans.delete(id)
        }
        return plan
    }
}

/** Puts back a plan cancelled or deleted a moment ago (snackbar "Undo"). */
class RestorePlanUseCase(private val plans: PlanRepository) {
    suspend operator fun invoke(plan: Plan) = plans.upsert(plan)
}

/**
 * The "How did it go?" sheet (issue #100), for one occurrence ([day], the open one when null): Didn't happen,
 * I didn't go, Cancel or Delete ([wholeSeries] picks "the whole series" for the last two on a recurring plan).
 * "It happened" logs a check-in here too, but the screens open the "We met" panel for it instead.
 * Returns the plan as it was, for Undo (null when nothing changed, or for the answers Undo doesn't cover).
 */
class ClosePlanUseCase(
    private val confirm: ConfirmPlanUseCase,
    private val cancel: CancelPlanUseCase,
    private val delete: DeletePlanUseCase,
) {
    suspend operator fun invoke(id: String, answer: PlanAnswer, day: LocalDate? = null, wholeSeries: Boolean = false): Plan? =
        when (answer) {
            PlanAnswer.HAPPENED -> confirm(id, happened = true, day = day).let { null }
            PlanAnswer.DIDNT_HAPPEN -> confirm(id, happened = false, day = day).let { null }
            PlanAnswer.NOT_ATTENDED -> confirm.notAttended(id, day)
            PlanAnswer.CANCEL -> cancel(id, day, wholeSeries)
            PlanAnswer.DELETE -> delete(id, day, wholeSeries)
        }
}
