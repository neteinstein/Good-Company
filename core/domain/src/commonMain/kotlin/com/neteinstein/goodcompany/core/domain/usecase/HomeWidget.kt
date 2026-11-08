package com.neteinstein.goodcompany.core.domain.usecase

import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.repository.SettingsRepository
import com.neteinstein.goodcompany.core.domain.util.AppClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * What the home-screen widget shows: the next thing coming up (birthday, big day, plan, reminder) and the friend
 * most due a catch-up. [isPrivate] hides names when the user asked for privacy (app lock or private notifications).
 */
data class HomeWidgetSummary(
    val next: AgendaItem?,
    val catchUp: Relationships.MeetupStatus?,
    val isPrivate: Boolean,
    val today: LocalDate,
)

object HomeWidget {
    fun summarize(
        agenda: List<AgendaItem>,
        meetups: List<Relationships.MeetupStatus>,
        today: LocalDate,
        isPrivate: Boolean,
    ): HomeWidgetSummary = HomeWidgetSummary(
        next = agenda.firstOrNull { it.isComingUp() },
        catchUp = meetups.filter { it.isOverdue(today) }.minByOrNull { it.dueDate },
        isPrivate = isPrivate,
        today = today,
    )

    private fun AgendaItem.isComingUp() = when (this) {
        is AgendaItem.FriendBirthday, is AgendaItem.KidBirthday, is AgendaItem.FriendEvent,
        is AgendaItem.ReminderDue, is AgendaItem.PlanUpcoming,
        -> true
        is AgendaItem.MeetupOverdue, is AgendaItem.GroupOverdue, is AgendaItem.PlanToConfirm -> false
    }
}

class ObserveHomeWidgetUseCase(
    private val observeAgenda: ObserveAgendaUseCase,
    private val observeMeetups: ObserveMeetupStatusUseCase,
    private val settings: SettingsRepository,
    private val clock: AppClock,
) {
    operator fun invoke(): Flow<HomeWidgetSummary> = combine(observeAgenda(), observeMeetups(), settings.settings) { agenda, meetups, s ->
        HomeWidget.summarize(agenda, meetups, clock.today(), isPrivate = s.appLock || s.privateNotifications)
    }
}

/**
 * The widget's content as plain data, for widgets that can't run the shared code (the iOS WidgetKit extension reads
 * it as JSON from the app group). Dates stay dates so the widget can say "Today / Tomorrow / In 3 days" on its own
 * as days pass; the text around them is localised by the widget.
 */
@Serializable
data class WidgetSnapshot(
    val isPrivate: Boolean = false,
    val next: Next? = null,
    val catchUp: CatchUp? = null,
) {
    @Serializable
    data class Next(
        val kind: Kind,
        /** Friend's first name (birthday, big day), or the parent's for a kid birthday. */
        val name: String = "",
        val kidName: String = "",
        /** Event / plan / reminder title. */
        val title: String = "",
        val date: LocalDate,
    )

    @Serializable
    enum class Kind { BIRTHDAY, KID_BIRTHDAY, EVENT, PLAN, REMINDER }

    @Serializable
    data class CatchUp(val friendId: String, val firstName: String)

    companion object {
        fun from(summary: HomeWidgetSummary): WidgetSnapshot {
            if (summary.isPrivate) return WidgetSnapshot(isPrivate = true)
            fun first(name: String) = name.substringBefore(' ')
            val next = when (val item = summary.next) {
                is AgendaItem.FriendBirthday -> Next(Kind.BIRTHDAY, name = first(item.friend.name), date = item.date)
                is AgendaItem.KidBirthday -> Next(Kind.KID_BIRTHDAY, name = first(item.parent.name), kidName = item.kid.name, date = item.date)
                is AgendaItem.FriendEvent -> Next(Kind.EVENT, name = first(item.friend.name), title = item.event.title, date = item.date)
                is AgendaItem.PlanUpcoming -> Next(Kind.PLAN, title = item.plan.title, date = item.date)
                is AgendaItem.ReminderDue -> Next(Kind.REMINDER, title = item.reminder.title, date = item.date)
                else -> null
            }
            return WidgetSnapshot(
                next = next,
                catchUp = summary.catchUp?.let { CatchUp(it.friend.id.value, first(it.friend.name)) },
            )
        }
    }
}
