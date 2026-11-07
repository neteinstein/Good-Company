package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Something to bring up next time, found in what you already wrote down. No AI, no network. Rendered by the UI. */
sealed interface TopicHint {
    /** "Last time (12 Oct, at Hot Clube) you noted: …" */
    data class FromNote(val note: String, val date: LocalDate, val place: String?) : TopicHint

    /** A big day that just happened ("How did the knee surgery go?") or is coming up ("Good luck with…"). */
    data class EventFollowUp(val title: String, val date: LocalDate, val isPast: Boolean) : TopicHint

    /** One of their kids has a birthday soon. */
    data class KidBirthday(val kidName: String, val date: LocalDate, val turning: Int?) : TopicHint

    /** Something they love. */
    data class Like(val like: String) : TopicHint
}

object OfflineTopics {
    const val MAX = 5
    private const val NOTES = 2
    private const val EVENT_PAST_DAYS = 21
    private const val EVENT_SOON_DAYS = 14
    private const val KID_BIRTHDAY_DAYS = 30
    private const val LIKES = 2

    /**
     * Up to [MAX] hints, most timely first: recent big days and what's coming up, notes from your last meetups,
     * kids' birthdays, then a couple of their tastes (rotating weekly so the list doesn't feel static).
     */
    fun build(friend: Friend, hangouts: List<Hangout>, today: LocalDate, zone: TimeZone): List<TopicHint> {
        val out = mutableListOf<TopicHint>()
        friend.importantEvents
            .filter { it.date in today.minus(EVENT_PAST_DAYS, DateTimeUnit.DAY)..today.plus(EVENT_SOON_DAYS, DateTimeUnit.DAY) }
            .sortedBy { kotlin.math.abs(it.date.daysUntil(today)) }
            .forEach { out += TopicHint.EventFollowUp(it.title, it.date, isPast = it.date < today) }
        hangouts.filter { friend.id in it.friendIds && it.note.isNotBlank() }
            .sortedByDescending { it.at }
            .take(NOTES)
            .forEach { h -> out += TopicHint.FromNote(h.note.trim(), h.at.toLocalDate(zone), h.place?.label) }
        friend.kids.forEach { kid ->
            val b = kid.birthday ?: return@forEach
            val next = b.nextOccurrence(today)
            if (today.daysUntil(next) <= KID_BIRTHDAY_DAYS) out += TopicHint.KidBirthday(kid.name, next, b.ageOn(next))
        }
        if (friend.likes.isNotEmpty()) {
            val week = (today.toEpochDays() / 7).toInt()
            val start = week % friend.likes.size
            (friend.likes.drop(start) + friend.likes.take(start)).take(LIKES).forEach { out += TopicHint.Like(it) }
        }
        return out.take(MAX)
    }
}
