package com.neteinstein.goodcompany.core.ui.l10n

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import com.neteinstein.goodcompany.core.domain.model.AiFailure
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.NotificationAction
import com.neteinstein.goodcompany.core.domain.model.NotificationContent
import com.neteinstein.goodcompany.core.domain.model.Occasion
import com.neteinstein.goodcompany.core.domain.model.PlannedNotification
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.RenderedAction
import com.neteinstein.goodcompany.core.domain.model.RenderedNotification
import com.neteinstein.goodcompany.core.domain.repository.LanguageProvider
import com.neteinstein.goodcompany.core.domain.repository.NotificationRenderer
import com.neteinstein.goodcompany.core.domain.usecase.MissingApiKeyException
import com.neteinstein.goodcompany.core.domain.usecase.TopicHint
import com.neteinstein.goodcompany.core.domain.usecase.Warmth
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.ai_error_invalid_key
import com.neteinstein.goodcompany.core.ui.resources.ai_error_no_key
import com.neteinstein.goodcompany.core.ui.resources.ai_error_other
import com.neteinstein.goodcompany.core.ui.resources.ai_error_rate_limited
import com.neteinstein.goodcompany.core.ui.resources.ai_error_refused
import com.neteinstein.goodcompany.core.ui.resources.ai_error_too_slow
import com.neteinstein.goodcompany.core.ui.resources.ai_error_truncated
import com.neteinstein.goodcompany.core.ui.resources.ai_error_unavailable
import com.neteinstein.goodcompany.core.ui.resources.anniversary_body
import com.neteinstein.goodcompany.core.ui.resources.anniversary_title
import com.neteinstein.goodcompany.core.ui.resources.anniversary_years
import com.neteinstein.goodcompany.core.ui.resources.circle_close
import com.neteinstein.goodcompany.core.ui.resources.circle_close_short
import com.neteinstein.goodcompany.core.ui.resources.circle_good
import com.neteinstein.goodcompany.core.ui.resources.circle_good_short
import com.neteinstein.goodcompany.core.ui.resources.circle_inner
import com.neteinstein.goodcompany.core.ui.resources.circle_inner_short
import com.neteinstein.goodcompany.core.ui.resources.every_n_days
import com.neteinstein.goodcompany.core.ui.resources.names_and
import com.neteinstein.goodcompany.core.ui.resources.names_and_more
import com.neteinstein.goodcompany.core.ui.resources.notif_action_called
import com.neteinstein.goodcompany.core.ui.resources.notif_action_met
import com.neteinstein.goodcompany.core.ui.resources.notif_action_plan_no
import com.neteinstein.goodcompany.core.ui.resources.notif_action_plan_yes
import com.neteinstein.goodcompany.core.ui.resources.notif_action_snooze_week
import com.neteinstein.goodcompany.core.ui.resources.notif_action_snooze_weekend
import com.neteinstein.goodcompany.core.ui.resources.notif_bday_body
import com.neteinstein.goodcompany.core.ui.resources.notif_bday_body_age
import com.neteinstein.goodcompany.core.ui.resources.notif_bday_title
import com.neteinstein.goodcompany.core.ui.resources.notif_digest_body
import com.neteinstein.goodcompany.core.ui.resources.notif_digest_title
import com.neteinstein.goodcompany.core.ui.resources.notif_event_soon_body
import com.neteinstein.goodcompany.core.ui.resources.notif_event_soon_title
import com.neteinstein.goodcompany.core.ui.resources.notif_event_today_body
import com.neteinstein.goodcompany.core.ui.resources.notif_event_today_title
import com.neteinstein.goodcompany.core.ui.resources.notif_group_body
import com.neteinstein.goodcompany.core.ui.resources.notif_group_body_never
import com.neteinstein.goodcompany.core.ui.resources.notif_group_title
import com.neteinstein.goodcompany.core.ui.resources.notif_kid_body
import com.neteinstein.goodcompany.core.ui.resources.notif_kid_body_age
import com.neteinstein.goodcompany.core.ui.resources.notif_kid_likes
import com.neteinstein.goodcompany.core.ui.resources.notif_kid_title
import com.neteinstein.goodcompany.core.ui.resources.notif_meet_body
import com.neteinstein.goodcompany.core.ui.resources.notif_meet_body_never
import com.neteinstein.goodcompany.core.ui.resources.notif_meet_title
import com.neteinstein.goodcompany.core.ui.resources.notif_plan_ask_body
import com.neteinstein.goodcompany.core.ui.resources.notif_plan_ask_title
import com.neteinstein.goodcompany.core.ui.resources.notif_plan_today_body
import com.neteinstein.goodcompany.core.ui.resources.notif_plan_today_title
import com.neteinstein.goodcompany.core.ui.resources.notif_private
import com.neteinstein.goodcompany.core.ui.resources.notif_relative_title
import com.neteinstein.goodcompany.core.ui.resources.notif_reminder_body
import com.neteinstein.goodcompany.core.ui.resources.notif_reminder_title
import com.neteinstein.goodcompany.core.ui.resources.notif_reminder_with
import com.neteinstein.goodcompany.core.ui.resources.occasion_birthday
import com.neteinstein.goodcompany.core.ui.resources.occasion_christmas
import com.neteinstein.goodcompany.core.ui.resources.occasion_coffee
import com.neteinstein.goodcompany.core.ui.resources.occasion_culture
import com.neteinstein.goodcompany.core.ui.resources.occasion_dinner
import com.neteinstein.goodcompany.core.ui.resources.occasion_drinks
import com.neteinstein.goodcompany.core.ui.resources.occasion_family_time
import com.neteinstein.goodcompany.core.ui.resources.occasion_just_because
import com.neteinstein.goodcompany.core.ui.resources.occasion_outdoors
import com.neteinstein.goodcompany.core.ui.resources.occasion_sports
import com.neteinstein.goodcompany.core.ui.resources.occasion_thank_you
import com.neteinstein.goodcompany.core.ui.resources.occasion_weekday_lunch
import com.neteinstein.goodcompany.core.ui.resources.relation_other
import com.neteinstein.goodcompany.core.ui.resources.relation_parent
import com.neteinstein.goodcompany.core.ui.resources.relation_partner
import com.neteinstein.goodcompany.core.ui.resources.relation_pet
import com.neteinstein.goodcompany.core.ui.resources.relation_sibling
import com.neteinstein.goodcompany.core.ui.resources.topic_event_past
import com.neteinstein.goodcompany.core.ui.resources.topic_event_soon
import com.neteinstein.goodcompany.core.ui.resources.topic_kid_birthday
import com.neteinstein.goodcompany.core.ui.resources.topic_kid_birthday_age
import com.neteinstein.goodcompany.core.ui.resources.topic_like
import com.neteinstein.goodcompany.core.ui.resources.topic_new_interest
import com.neteinstein.goodcompany.core.ui.resources.topic_note
import com.neteinstein.goodcompany.core.ui.resources.topic_note_place
import com.neteinstein.goodcompany.core.ui.resources.warmth_cooling
import com.neteinstein.goodcompany.core.ui.resources.warmth_fading
import com.neteinstein.goodcompany.core.ui.resources.warmth_warm
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The UI language, e.g. "pt-PT" — used to ask the AI to answer in it. */
object ComposeLanguageProvider : LanguageProvider {
    override fun languageTag(): String = Locale.current.toLanguageTag()
}

/** Localised notification text + quick actions, from string resources. */
object ResourceNotificationRenderer : NotificationRenderer {
    override suspend fun render(notification: PlannedNotification): RenderedNotification {
        val (title, body, actions) = when (val c = notification.content) {
            is NotificationContent.FriendBirthday -> Triple(
                getString(Res.string.notif_bday_title, c.friendName),
                c.age?.let { getString(Res.string.notif_bday_body_age, c.friendName, it) } ?: getString(Res.string.notif_bday_body, c.friendName),
                emptyList(),
            )
            is NotificationContent.KidBirthday -> Triple(
                getString(Res.string.notif_kid_title, c.kidName, c.parentName),
                listOfNotNull(
                    c.age?.let { getString(Res.string.notif_kid_body_age, it) } ?: getString(Res.string.notif_kid_body),
                    c.likes.takeIf { it.isNotEmpty() }?.let { getString(Res.string.notif_kid_likes, it.joinToString()) },
                ).joinToString(" "),
                emptyList(),
            )
            is NotificationContent.RelativeBirthday -> Triple(
                getString(Res.string.notif_relative_title, c.relativeName, c.friendName),
                c.age?.let { getString(Res.string.notif_kid_body_age, it) } ?: getString(Res.string.notif_kid_body),
                emptyList(),
            )
            is NotificationContent.Anniversary -> Triple(
                getString(Res.string.anniversary_title, c.firstName, c.secondName),
                c.years?.takeIf { it > 0 }?.let { getPluralString(Res.plurals.anniversary_years, it, it) } ?: getString(Res.string.anniversary_body),
                emptyList(),
            )
            is NotificationContent.EventToday -> Triple(
                getString(Res.string.notif_event_today_title, c.friendName),
                getString(Res.string.notif_event_today_body, c.title),
                emptyList(),
            )
            is NotificationContent.EventSoon -> Triple(
                getString(Res.string.notif_event_soon_title, c.friendName, c.title),
                getPluralString(Res.plurals.notif_event_soon_body, c.daysBefore, c.daysBefore),
                emptyList(),
            )
            is NotificationContent.ReminderDue -> Triple(
                getString(Res.string.notif_reminder_title, c.title),
                if (c.withNames.isEmpty()) {
                    getString(
                        Res.string.notif_reminder_body,
                    )
                } else {
                    getString(Res.string.notif_reminder_with, c.withNames.joinToString())
                },
                emptyList(),
            )
            is NotificationContent.MeetupDue -> {
                val every = getPluralString(Res.plurals.every_n_days, c.everyDays, c.everyDays)
                Triple(
                    getString(Res.string.notif_meet_title, c.friendName),
                    c.daysSince?.let { getPluralString(Res.plurals.notif_meet_body, it, it, every) }
                        ?: getString(Res.string.notif_meet_body_never, every),
                    listOf(
                        RenderedAction(NotificationAction(NotificationAction.Kind.MET, c.friendId.value), getString(Res.string.notif_action_met)),
                        RenderedAction(
                            NotificationAction(NotificationAction.Kind.CALLED, c.friendId.value),
                            getString(Res.string.notif_action_called),
                        ),
                        RenderedAction(
                            NotificationAction(NotificationAction.Kind.SNOOZE_WEEK, c.friendId.value),
                            getString(Res.string.notif_action_snooze_week),
                        ),
                    ),
                )
            }
            is NotificationContent.GroupMeetupDue -> Triple(
                getString(Res.string.notif_group_title, c.groupName),
                c.daysSince?.let { getPluralString(Res.plurals.notif_group_body, it, it) } ?: getString(Res.string.notif_group_body_never),
                listOf(
                    RenderedAction(
                        NotificationAction(NotificationAction.Kind.MET, c.groupId, isGroup = true),
                        getString(Res.string.notif_action_met),
                    ),
                    RenderedAction(
                        NotificationAction(NotificationAction.Kind.SNOOZE_WEEKEND, c.groupId, isGroup = true),
                        getString(Res.string.notif_action_snooze_weekend),
                    ),
                    RenderedAction(
                        NotificationAction(NotificationAction.Kind.SNOOZE_WEEK, c.groupId, isGroup = true),
                        getString(Res.string.notif_action_snooze_week),
                    ),
                ),
            )
            is NotificationContent.CatchUpDigest -> Triple(
                getString(Res.string.notif_digest_title),
                getString(Res.string.notif_digest_body, namesText(c.names, c.total)),
                emptyList(),
            )
            is NotificationContent.PlanToday -> Triple(
                getString(Res.string.notif_plan_today_title, c.title),
                if (c.withNames.isEmpty()) "" else getString(Res.string.notif_plan_today_body, namesText(c.withNames, c.withNames.size)),
                emptyList(),
            )
            is NotificationContent.PlanFollowUp -> Triple(
                getString(Res.string.notif_plan_ask_title, namesText(c.withNames, c.withNames.size)),
                getString(Res.string.notif_plan_ask_body, c.title),
                // A circle plan asks who came in the app (tap the notification); only "No" works from here.
                listOfNotNull(
                    RenderedAction(
                        NotificationAction(NotificationAction.Kind.PLAN_HAPPENED, c.target),
                        getString(Res.string.notif_action_plan_yes),
                    ).takeUnless { c.isCircle },
                    RenderedAction(
                        NotificationAction(NotificationAction.Kind.PLAN_SKIPPED, c.target),
                        getString(Res.string.notif_action_plan_no),
                    ),
                ),
            )
        }
        return RenderedNotification(notification.id, notification.at, title, body, actions)
    }

    override suspend fun privateText(): String = getString(Res.string.notif_private)
}

/** "Ana", "Ana and Rui", "Ana, Rui and Cat", "Ana, Rui, Cat and 2 more" — [total] counts everyone, named or not. */
suspend fun namesText(names: List<String>, total: Int): String {
    if (names.isEmpty()) return ""
    val extra = total - names.size
    return when {
        extra > 0 -> getPluralString(Res.plurals.names_and_more, extra, names.joinToString(", "), extra)
        names.size == 1 -> names.single()
        else -> getString(Res.string.names_and, names.dropLast(1).joinToString(", "), names.last())
    }
}

@Composable
fun occasionLabel(occasion: Occasion): String = stringResource(
    when (occasion) {
        Occasion.WEEKDAY_LUNCH -> Res.string.occasion_weekday_lunch
        Occasion.COFFEE -> Res.string.occasion_coffee
        Occasion.DINNER -> Res.string.occasion_dinner
        Occasion.DRINKS -> Res.string.occasion_drinks
        Occasion.OUTDOORS -> Res.string.occasion_outdoors
        Occasion.SPORTS -> Res.string.occasion_sports
        Occasion.CULTURE -> Res.string.occasion_culture
        Occasion.FAMILY_TIME -> Res.string.occasion_family_time
    },
)

/** One offline talking point as a sentence. */
@Composable
fun topicText(topic: TopicHint): String = when (topic) {
    is TopicHint.FromNote -> stringResource(
        Res.string.topic_note,
        topic.place?.let { stringResource(Res.string.topic_note_place, topic.date.shortLabel(), it) } ?: topic.date.shortLabel(),
        topic.note,
    )
    is TopicHint.EventFollowUp -> stringResource(
        if (topic.isPast) Res.string.topic_event_past else Res.string.topic_event_soon,
        topic.title,
        topic.date.shortLabel(),
    )
    is TopicHint.KidBirthday -> topic.turning?.let { stringResource(Res.string.topic_kid_birthday_age, topic.kidName, topic.date.shortLabel(), it) }
        ?: stringResource(Res.string.topic_kid_birthday, topic.kidName, topic.date.shortLabel())
    is TopicHint.RelativeBirthday -> topic.turning?.let { stringResource(Res.string.topic_kid_birthday_age, topic.name, topic.date.shortLabel(), it) }
        ?: stringResource(Res.string.topic_kid_birthday, topic.name, topic.date.shortLabel())
    is TopicHint.NewInterest -> stringResource(Res.string.topic_new_interest, topic.interest)
    is TopicHint.Like -> stringResource(Res.string.topic_like, topic.like)
}

/** Who a relative is ("Partner", "Pet"…). */
@Composable
fun relationLabel(relation: Relation): String = stringResource(
    when (relation) {
        Relation.PARTNER -> Res.string.relation_partner
        Relation.PARENT -> Res.string.relation_parent
        Relation.SIBLING -> Res.string.relation_sibling
        Relation.PET -> Res.string.relation_pet
        Relation.OTHER -> Res.string.relation_other
    },
)

/** A localised, user-facing explanation of an AI failure. */
suspend fun aiErrorText(error: Throwable): String = getString(
    when {
        error is MissingApiKeyException -> Res.string.ai_error_no_key
        error is AiFailure -> when (error.reason) {
            AiFailure.Reason.INVALID_KEY -> Res.string.ai_error_invalid_key
            AiFailure.Reason.RATE_LIMITED -> Res.string.ai_error_rate_limited
            AiFailure.Reason.SERVICE_UNAVAILABLE -> Res.string.ai_error_unavailable
            AiFailure.Reason.REFUSED -> Res.string.ai_error_refused
            AiFailure.Reason.TRUNCATED -> Res.string.ai_error_truncated
            AiFailure.Reason.TOO_SLOW -> Res.string.ai_error_too_slow
            AiFailure.Reason.OTHER -> Res.string.ai_error_other
        }
        else -> Res.string.ai_error_other
    },
)

@Composable
fun closenessLabel(closeness: Closeness, short: Boolean = false): String = stringResource(
    when (closeness) {
        Closeness.INNER -> if (short) Res.string.circle_inner_short else Res.string.circle_inner
        Closeness.CLOSE -> if (short) Res.string.circle_close_short else Res.string.circle_close
        Closeness.GOOD -> if (short) Res.string.circle_good_short else Res.string.circle_good
    },
)

@Composable
fun warmthLabel(level: Warmth.Level): String = stringResource(
    when (level) {
        Warmth.Level.WARM -> Res.string.warmth_warm
        Warmth.Level.COOLING -> Res.string.warmth_cooling
        Warmth.Level.FADING -> Res.string.warmth_fading
    },
)

@Composable
fun occasionLabel(occasion: GiftOccasion): String = stringResource(
    when (occasion) {
        GiftOccasion.BIRTHDAY -> Res.string.occasion_birthday
        GiftOccasion.CHRISTMAS -> Res.string.occasion_christmas
        GiftOccasion.THANK_YOU -> Res.string.occasion_thank_you
        GiftOccasion.JUST_BECAUSE -> Res.string.occasion_just_because
    },
)

/** "every 14 days" / "every day". */
@Composable
fun everyNDays(days: Int): String = pluralStringResource(Res.plurals.every_n_days, days, days)
