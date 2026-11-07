package com.neteinstein.goodcompany.core.ui.format

import androidx.compose.runtime.Composable
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.date_long
import com.neteinstein.goodcompany.core.ui.resources.date_short
import com.neteinstein.goodcompany.core.ui.resources.day_days_ago
import com.neteinstein.goodcompany.core.ui.resources.day_in_days
import com.neteinstein.goodcompany.core.ui.resources.day_today
import com.neteinstein.goodcompany.core.ui.resources.day_tomorrow
import com.neteinstein.goodcompany.core.ui.resources.day_yesterday
import com.neteinstein.goodcompany.core.ui.resources.months_long
import com.neteinstein.goodcompany.core.ui.resources.months_short
import com.neteinstein.goodcompany.core.ui.resources.past_days
import com.neteinstein.goodcompany.core.ui.resources.past_hours
import com.neteinstein.goodcompany.core.ui.resources.past_just_now
import com.neteinstein.goodcompany.core.ui.resources.past_minutes
import com.neteinstein.goodcompany.core.ui.resources.past_months
import com.neteinstein.goodcompany.core.ui.resources.past_over_a_year
import com.neteinstein.goodcompany.core.ui.resources.past_weeks
import com.neteinstein.goodcompany.core.ui.resources.past_yesterday
import com.neteinstein.goodcompany.core.ui.resources.phrase_in_days
import com.neteinstein.goodcompany.core.ui.resources.phrase_on_date
import com.neteinstein.goodcompany.core.ui.resources.phrase_today
import com.neteinstein.goodcompany.core.ui.resources.phrase_tomorrow
import com.neteinstein.goodcompany.core.ui.resources.phrase_yesterday
import com.neteinstein.goodcompany.core.ui.resources.weekdays_initial
import com.neteinstein.goodcompany.core.ui.resources.weekdays_long
import com.neteinstein.goodcompany.core.ui.resources.weekdays_short
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/*
 * Date wording in the user's language. The *classification* (is it "tomorrow"? "3 weeks ago"?) is pure Kotlin and
 * unit-tested; the composable functions below only turn it into localised text via string resources.
 */

/** How far a calendar day is from today, for labels like "Tomorrow" or "In 4 days". */
sealed interface RelativeDay {
    data object Today : RelativeDay

    data object Tomorrow : RelativeDay

    data object Yesterday : RelativeDay

    data class InDays(val days: Int) : RelativeDay

    data class DaysAgo(val days: Int) : RelativeDay

    /** Further than a week either way — show the date itself. */
    data class On(val date: LocalDate) : RelativeDay

    companion object {
        fun of(date: LocalDate, today: LocalDate): RelativeDay = when (val d = today.daysUntil(date)) {
            0 -> Today
            1 -> Tomorrow
            -1 -> Yesterday
            in 2..6 -> InDays(d)
            in -6..-2 -> DaysAgo(-d)
            else -> On(date)
        }
    }
}

/** How long ago an instant was: "just now", "3h ago", "2 weeks ago"… */
sealed interface RelativePast {
    data object JustNow : RelativePast

    data class Minutes(val n: Int) : RelativePast

    data class Hours(val n: Int) : RelativePast

    data object Yesterday : RelativePast

    data class Days(val n: Int) : RelativePast

    data class Weeks(val n: Int) : RelativePast

    data class Months(val n: Int) : RelativePast

    data object OverAYear : RelativePast

    companion object {
        fun of(instant: Instant, now: Instant, zone: TimeZone): RelativePast {
            val minutes = (now - instant).inWholeMinutes
            if (minutes < 1) return JustNow
            if (minutes < 60) return Minutes(minutes.toInt())
            val then = instant.toLocalDateTime(zone).date
            val today = now.toLocalDateTime(zone).date
            if (minutes < 24 * 60 && then == today) return Hours((minutes / 60).toInt())
            val days = then.daysUntil(today)
            return when {
                days <= 1 -> Yesterday
                days < 14 -> Days(days)
                days < 60 -> Weeks(days / 7)
                days < 365 -> Months(days / 30)
                else -> OverAYear
            }
        }
    }
}

/*
 * stringArrayResource is empty on the first frame while the web build loads resources asynchronously,
 * so these never index blindly.
 */
@Composable
fun monthShort(month: Month): String = stringArrayResource(Res.array.months_short).getOrElse(month.ordinal) { "" }

@Composable
fun monthLong(month: Month): String = stringArrayResource(Res.array.months_long).getOrElse(month.ordinal) { "" }

@Composable
fun weekdayShort(day: DayOfWeek): String = stringArrayResource(Res.array.weekdays_short).getOrElse(day.ordinal) { "" }

@Composable
fun weekdayLong(day: DayOfWeek): String = stringArrayResource(Res.array.weekdays_long).getOrElse(day.ordinal) { "" }

@Composable
fun weekdayInitial(day: DayOfWeek): String = stringArrayResource(Res.array.weekdays_initial).getOrElse(day.ordinal) { "" }

/** "Mar 10" / "10 mar" depending on the language. */
@Composable
fun LocalDate.shortLabel(): String = stringResource(Res.string.date_short, monthShort(month), day)

/** [shortLabel] outside composition (snackbars, shared text). */
suspend fun LocalDate.shortLabelText(): String = getString(Res.string.date_short, getStringArray(Res.array.months_short)[month.ordinal], day)

/** "Mar 10, 2026" / "10 mar 2026". */
@Composable
fun LocalDate.longLabel(): String = stringResource(Res.string.date_long, monthShort(month), day, year)

fun LocalDateTime.timeLabel(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/** "Today", "Tomorrow", "In 5 days", "Mar 20" — capitalised, for headers and chips. */
@Composable
fun relativeDay(date: LocalDate, today: LocalDate): String = when (val r = RelativeDay.of(date, today)) {
    RelativeDay.Today -> stringResource(Res.string.day_today)
    RelativeDay.Tomorrow -> stringResource(Res.string.day_tomorrow)
    RelativeDay.Yesterday -> stringResource(Res.string.day_yesterday)
    is RelativeDay.InDays -> pluralStringResource(Res.plurals.day_in_days, r.days, r.days)
    is RelativeDay.DaysAgo -> pluralStringResource(Res.plurals.day_days_ago, r.days, r.days)
    is RelativeDay.On -> r.date.shortLabel()
}

/** Lower-case, for use mid-sentence: "today", "in 4 days", "on Mar 30". */
@Composable
fun relativeDayPhrase(date: LocalDate, today: LocalDate): String = when (val r = RelativeDay.of(date, today)) {
    RelativeDay.Today -> stringResource(Res.string.phrase_today)
    RelativeDay.Tomorrow -> stringResource(Res.string.phrase_tomorrow)
    RelativeDay.Yesterday -> stringResource(Res.string.phrase_yesterday)
    is RelativeDay.InDays -> pluralStringResource(Res.plurals.phrase_in_days, r.days, r.days)
    is RelativeDay.DaysAgo -> pluralStringResource(Res.plurals.day_days_ago, r.days, r.days)
    is RelativeDay.On -> stringResource(Res.string.phrase_on_date, r.date.shortLabel())
}

/** True when [relativeDay] would just print the date. */
fun isFarDate(date: LocalDate, today: LocalDate) = RelativeDay.of(date, today) is RelativeDay.On

@Composable
fun relativePast(instant: Instant, now: Instant, zone: TimeZone): String = when (val r = RelativePast.of(instant, now, zone)) {
    RelativePast.JustNow -> stringResource(Res.string.past_just_now)
    is RelativePast.Minutes -> stringResource(Res.string.past_minutes, r.n)
    is RelativePast.Hours -> stringResource(Res.string.past_hours, r.n)
    RelativePast.Yesterday -> stringResource(Res.string.past_yesterday)
    is RelativePast.Days -> pluralStringResource(Res.plurals.past_days, r.n, r.n)
    is RelativePast.Weeks -> pluralStringResource(Res.plurals.past_weeks, r.n, r.n)
    is RelativePast.Months -> pluralStringResource(Res.plurals.past_months, r.n, r.n)
    RelativePast.OverAYear -> stringResource(Res.string.past_over_a_year)
}

/** Splits a comma/newline separated entry into clean tags. */
fun parseTags(raw: String): List<String> = raw.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** Percent-encodes text for `wa.me/?text=` and `sms:?body=` links (RFC 3986 unreserved characters kept). */
fun urlEncode(text: String): String = buildString {
    text.encodeToByteArray().forEach { b ->
        val c = (b.toInt() and 0xFF).toChar()
        if ((c.isLetterOrDigit() && c.code < 128) ||
            c in "-._~"
        ) {
            append(c)
        } else {
            append('%').append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
