package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Idea
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanStatus
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.domain.repository.FriendRepository
import com.neteinstein.goodcompany.core.domain.repository.HangoutRepository
import com.neteinstein.goodcompany.core.domain.repository.PlanRepository
import com.neteinstein.goodcompany.core.domain.repository.ReminderRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Something added about a friend recently: the "new since you last met" part of their catch-up story.
 * Carries the user's own words; the UI adds the labels.
 */
sealed interface FreshItem {
    val addedAt: Instant

    data class Update(val text: String, override val addedAt: Instant) : FreshItem

    data class NewKid(val name: String, override val addedAt: Instant) : FreshItem

    data class NewEvent(val title: String, val date: LocalDate, override val addedAt: Instant) : FreshItem

    data class NewLike(val like: String, override val addedAt: Instant) : FreshItem
}

/** One kid line on the "Ask about the kids" slide. */
data class StoryKid(val name: String, val age: Int?, val nextBirthday: LocalDate?, val birthdaySoon: Boolean, val likes: List<String>)

/** One full-screen card of a friend's catch-up story, in the order shown. Rendered by the UI. */
sealed interface StorySlide {
    /** "Catching up with Ana": when you last met, where, and how much is new since. */
    data class Intro(val lastSeen: Instant?, val lastPlace: String?, val nextPlan: Plan?, val freshCount: Int) : StorySlide

    /** One thing added since you last met, newest first. */
    data class Fresh(val item: FreshItem) : StorySlide

    /** A big day that just happened ("How did it go?") or is coming up. */
    data class BigDay(val title: String, val date: LocalDate, val isPast: Boolean) : StorySlide

    /** Their birthday just happened or is coming up. */
    data class Birthday(val date: LocalDate, val turning: Int?, val isPast: Boolean) : StorySlide

    data class Kids(val kids: List<StoryKid>) : StorySlide

    /** The last meetup: when, where, who else, and what you noted. */
    data class LastTime(val date: LocalDate, val place: String?, val note: String?, val with: List<String>) : StorySlide

    data class Tastes(val likes: List<String>, val dislikes: List<String>) : StorySlide

    data class Notes(val text: String) : StorySlide

    /** Open reminders that involve them. */
    data class ToDo(val reminders: List<String>) : StorySlide

    /** AI talking points / news you saved for them. */
    data class Ideas(val ideas: List<Idea>) : StorySlide

    /** "Don't forget to ask about…" — the fresh items and big days, in one list. */
    data class Outro(val askAbout: List<String>) : StorySlide
}

data class CatchUpStory(
    val friend: Friend,
    val slides: List<StorySlide>,
    /** Things count as "new" when added after this (last meetup, or a fallback window when you never met). */
    val freshSince: Instant,
)

/**
 * Builds the Instagram-style catch-up story shown before meeting a friend (or on demand): everything we know
 * about them, leading with what was added since you last met so you remember to ask about it. Pure; no AI.
 */
object CatchUpStoryBuilder {
    /** Without a logged meetup, things added in this window count as new. */
    const val FALLBACK_FRESH_DAYS = 60
    const val MAX_FRESH = 6
    private const val EVENT_PAST_DAYS = 30
    private const val EVENT_SOON_DAYS = 21
    private const val BIRTHDAY_WINDOW_DAYS = 14
    private const val KID_BIRTHDAY_SOON_DAYS = 30
    private const val MAX_TASTES = 8
    private const val MAX_TODO = 4
    private const val MAX_IDEAS = 3
    private const val MAX_ASK = 6

    fun freshItems(friend: Friend): List<FreshItem> = buildList {
        friend.lifeUpdates.forEach { add(FreshItem.Update(it.text, it.addedAt)) }
        friend.kids.forEach { kid -> kid.addedAt?.let { add(FreshItem.NewKid(kid.name, it)) } }
        friend.importantEvents.forEach { e -> e.addedAt?.let { add(FreshItem.NewEvent(e.title, e.date, it)) } }
        friend.likes.forEach { like -> friend.likesAddedAt[like]?.let { add(FreshItem.NewLike(like, it)) } }
    }.sortedByDescending { it.addedAt }

    fun build(detail: FriendDetail, reminders: List<Reminder>, now: Instant, zone: TimeZone): CatchUpStory {
        val friend = detail.friend
        val today = now.toLocalDate(zone)
        val since = detail.lastSeen ?: (now - FALLBACK_FRESH_DAYS.days)
        val fresh = freshItems(friend).filter { it.addedAt > since }.take(MAX_FRESH)
        val slides = mutableListOf<StorySlide>()
        val ask = mutableListOf<String>()
        val last = detail.hangouts.firstOrNull()

        slides += StorySlide.Intro(detail.lastSeen, last?.hangout?.place?.label, detail.plans.firstOrNull(), fresh.size)
        fresh.forEach { item ->
            slides += StorySlide.Fresh(item)
            when (item) {
                is FreshItem.Update -> ask += item.text
                is FreshItem.NewEvent -> ask += item.title
                is FreshItem.NewKid -> ask += item.name
                is FreshItem.NewLike -> ask += item.like
            }
        }

        val freshEvents = fresh.filterIsInstance<FreshItem.NewEvent>().map { it.title }.toSet()
        friend.importantEvents
            .filter { it.date in today.minus(EVENT_PAST_DAYS, DateTimeUnit.DAY)..today.plus(EVENT_SOON_DAYS, DateTimeUnit.DAY) }
            .sortedBy { kotlin.math.abs(it.date.daysUntil(today)) }
            .forEach { e ->
                slides += StorySlide.BigDay(e.title, e.date, isPast = e.date < today)
                if (e.title !in freshEvents) ask += e.title
            }

        friend.birthday?.let { b ->
            val next = b.nextOccurrence(today)
            val previous = b.nextOccurrence(today.minus(BIRTHDAY_WINDOW_DAYS, DateTimeUnit.DAY))
            when {
                today.daysUntil(next) <= BIRTHDAY_WINDOW_DAYS -> slides += StorySlide.Birthday(next, b.ageOn(next), isPast = false)
                previous < today -> slides += StorySlide.Birthday(previous, b.ageOn(previous), isPast = true)
            }
        }

        if (friend.kids.isNotEmpty()) {
            slides += StorySlide.Kids(
                friend.kids.map { kid ->
                    val next = kid.birthday?.nextOccurrence(today)
                    StoryKid(
                        name = kid.name,
                        age = kid.birthday?.ageOn(today),
                        nextBirthday = next,
                        birthdaySoon = next != null && today.daysUntil(next) <= KID_BIRTHDAY_SOON_DAYS,
                        likes = kid.likes,
                    )
                },
            )
            ask += friend.kids.map { it.name }.filterNot { it in ask }
        }

        last?.let { h ->
            slides += StorySlide.LastTime(
                date = h.hangout.at.toLocalDate(zone),
                place = h.hangout.place?.label,
                note = h.hangout.note.trim().takeIf { it.isNotEmpty() },
                with = h.friends.filterNot { it.id == friend.id }.map { it.name.substringBefore(' ') },
            )
        }

        if (friend.likes.isNotEmpty() || friend.dislikes.isNotEmpty()) {
            slides += StorySlide.Tastes(friend.likes.take(MAX_TASTES), friend.dislikes.take(MAX_TASTES))
        }
        friend.notes.trim().takeIf { it.isNotEmpty() }?.let { slides += StorySlide.Notes(it) }

        val todo = reminders.filter { !it.done && friend.id in it.friendIds }.sortedBy { it.dueAt ?: Instant.DISTANT_FUTURE }
        if (todo.isNotEmpty()) slides += StorySlide.ToDo(todo.take(MAX_TODO).map { it.title })

        val ideas = detail.ideas.filter { it.kind != IdeaKind.EVENT }.take(MAX_IDEAS)
        if (ideas.isNotEmpty()) slides += StorySlide.Ideas(ideas)

        slides += StorySlide.Outro(ask.distinct().take(MAX_ASK))
        return CatchUpStory(friend, slides, since)
    }

    /** Friend ids you're seeing today or tomorrow (planned, not yet confirmed), soonest first. */
    fun meetingSoon(plans: List<Plan>, today: LocalDate): List<Pair<FriendId, LocalDate>> = plans
        .filter { it.status == PlanStatus.PLANNED && it.date >= today && it.date <= today.plus(1, DateTimeUnit.DAY) }
        .sortedBy { it.date }
        .flatMap { p -> p.friendIds.map { it to p.date } }
        .distinctBy { it.first }
}

/** A friend's catch-up story, kept up to date as you add things. Null when the friend is gone. */
class ObserveCatchUpStoryUseCase(
    private val observeDetail: ObserveFriendDetailUseCase,
    private val reminders: ReminderRepository,
    private val clock: AppClock,
) {
    operator fun invoke(id: FriendId): Flow<CatchUpStory?> = combine(observeDetail(id), reminders.reminders) { detail, rs ->
        detail?.let { CatchUpStoryBuilder.build(it, rs, clock.now(), clock.timeZone) }
    }
}

/** A friend in the home "Before you meet" row. */
data class StoryBubble(val friend: Friend, val meetingOn: LocalDate, val freshCount: Int)

/** Friends you're seeing today or tomorrow, for the home stories row. */
class ObserveStoryBubblesUseCase(
    private val friends: FriendRepository,
    private val plans: PlanRepository,
    private val hangouts: HangoutRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<List<StoryBubble>> = combine(friends.friends, plans.plans, hangouts.hangouts) { all, ps, hs ->
        val now = clock.now()
        val byId = all.associateBy { it.id }
        val seen = Relationships.lastSeen(hs)
        CatchUpStoryBuilder.meetingSoon(ps, clock.today()).mapNotNull { (id, date) ->
            val friend = byId[id] ?: return@mapNotNull null
            val since = seen[id] ?: (now - CatchUpStoryBuilder.FALLBACK_FRESH_DAYS.days)
            StoryBubble(friend, date, CatchUpStoryBuilder.freshItems(friend).count { it.addedAt > since })
        }
    }
}
