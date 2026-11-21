package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.ActivityChoice
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.HangoutActivity
import com.neteinstein.goodcompany.core.domain.usecase.CatchUpPolicy
import com.neteinstein.goodcompany.core.domain.usecase.LogKind
import com.neteinstein.goodcompany.core.domain.usecase.ObserveActivitiesUseCase
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.log_kind_call
import com.neteinstein.goodcompany.core.ui.resources.log_kind_email
import com.neteinstein.goodcompany.core.ui.resources.log_kind_game
import com.neteinstein.goodcompany.core.ui.resources.log_kind_in_person
import com.neteinstein.goodcompany.core.ui.resources.log_kind_journal
import com.neteinstein.goodcompany.core.ui.resources.log_kind_long_chat
import com.neteinstein.goodcompany.core.ui.resources.log_kind_other
import com.neteinstein.goodcompany.core.ui.resources.log_kind_said_hi
import com.neteinstein.goodcompany.core.ui.resources.log_kind_video
import com.neteinstein.goodcompany.core.ui.resources.log_other_label
import com.neteinstein.goodcompany.core.ui.resources.log_pick_date
import com.neteinstein.goodcompany.core.ui.resources.log_today
import com.neteinstein.goodcompany.core.ui.resources.log_yesterday
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant
import androidx.compose.material.icons.automirrored.rounded.MenuBook as JournalIcon

/** What the Log popup shows (issues #21, #151). Built by [LogSheetModel]; the popup itself is stateless apart from open dialogs. */
data class LogSheetState(
    val kind: LogKind = LogKind.MEETUP,
    /** Everyone the log is for, in the order picked. */
    val people: List<Friend> = emptyList(),
    /** Everyone who can be added, A–Z. */
    val everyone: List<Friend> = emptyList(),
    val date: LocalDate,
    val today: LocalDate,
    val note: String = "",
    /** [LogKind.OTHER] only: what it was, in the user's words. */
    val label: String = "",
    /** Meetups only: where (F11), with "Usually here with". */
    val place: MeetupPlaceState = MeetupPlaceState(),
    val activity: ActivityChoice? = null,
    /** Most used first (F3): the first [ObserveActivitiesUseCase.SHOWN] are on show, the rest behind "See all" (issue #151). */
    val activities: List<ActivityChoice> = HangoutActivity.entries.map { ActivityChoice.BuiltIn(it) },
    val saving: Boolean = false,
    /** Circles to pick from (decision D1). */
    val circles: List<FriendGroup> = emptyList(),
    /** The circle picked: its members are listed to tick who came, nobody ticked to start with (F20). */
    val circle: FriendGroup? = null,
    val circleMembers: List<Friend> = emptyList(),
    /** Editing a timeline entry (issue #102): the title says so. */
    val editing: Boolean = false,
    /** Issue #116: everyone, whoever you were last in touch with first (the people picker's order). */
    val recentFirst: List<Friend> = everyone,
    /** Issue #116: the time of day chosen (null = now today, noon on an earlier day). */
    val time: LocalTime? = null,
    val now: Instant = Instant.DISTANT_PAST,
    val zone: TimeZone = TimeZone.UTC,
    /** In person only: the last meetup with everyone chosen ("Last time together: …"). */
    val lastTogether: Instant? = null,
    /** What counts as catching up (Settings → How, decision 2026-11-21). */
    val catchUp: CatchUpPolicy = CatchUpPolicy(),
) {
    /** Journal entries need text; everything else can be saved as it is. */
    val canSave: Boolean get() = people.isNotEmpty() && !saving && (kind != LogKind.JOURNAL || note.isNotBlank())

    /** The current time of day, to the minute (where "Pick a time" starts). */
    val nowTime: LocalTime get() = now.toLocalDateTime(zone).let { LocalTime(it.hour, it.minute) }

    /**
     * Someone the "doesn't restart the countdown" hint is about: a picked friend for whom this way of keeping in touch
     * doesn't count as catching up ([CatchUpPolicy], their choice in Edit person).
     */
    val countsHintFor: Friend? get() = if (kind.isRemote) {
        people.firstOrNull { !catchUp.countsWhenLogged(it, kind, label.takeIf { kind == LogKind.OTHER }) }
    } else {
        null
    }
}

@Composable
fun logKindLabel(kind: LogKind): String = stringResource(
    when (kind) {
        LogKind.MEETUP -> Res.string.log_kind_in_person
        LogKind.CALL -> Res.string.log_kind_call
        LogKind.MESSAGE -> Res.string.log_kind_said_hi
        LogKind.EMAIL -> Res.string.log_kind_email
        LogKind.VIDEO_CALL -> Res.string.log_kind_video
        LogKind.LONG_CHAT -> Res.string.log_kind_long_chat
        LogKind.GAME -> Res.string.log_kind_game
        LogKind.OTHER -> Res.string.log_kind_other
        LogKind.JOURNAL -> Res.string.log_kind_journal
    },
)

fun logKindIcon(kind: LogKind) = kind.interactionType?.let(::interactionIcon) ?: Icons.AutoMirrored.Rounded.JournalIcon

/**
 * Today / Yesterday / a picked date, as on the Log popup (also used by the share screen).
 * Issue #114: one line and one height in every language; the third chip says "Other day" and, if even that doesn't
 * fit, shows only its calendar icon (its label stays as the content description).
 */
@Composable
fun LogDateChoice(date: LocalDate, today: LocalDate, onDate: (LocalDate) -> Unit, onPick: () -> Unit) {
    val yesterday = today.minus(1, DateTimeUnit.DAY)
    val other = date != today && date != yesterday
    val chip = Modifier.height(FilterChipDefaults.Height)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(date == today, onClick = { onDate(today) }, label = { OneLine(stringResource(Res.string.log_today)) }, modifier = chip)
        FilterChip(date == yesterday, onClick = { onDate(yesterday) }, label = { OneLine(stringResource(Res.string.log_yesterday)) }, modifier = chip)
        val pickLabel = stringResource(Res.string.log_pick_date)
        var iconOnly by remember { mutableStateOf(false) }
        FilterChip(
            other,
            onClick = onPick,
            label = {
                when {
                    other -> OneLine(date.shortLabel())
                    !iconOnly -> OneLine(pickLabel, onOverflow = { iconOnly = true })
                }
            },
            leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) },
            modifier = chip.semantics { contentDescription = pickLabel },
        )
    }
}

/** A chip label that never wraps; [onOverflow] runs when it doesn't fit. */
@Composable
private fun OneLine(text: String, onOverflow: () -> Unit = {}) {
    Text(
        text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (it.hasVisualOverflow) onOverflow() },
    )
}

/** "What was it?" for [LogKind.OTHER] (issue #101): a short free label, shown on the timeline instead of "Other". */
@Composable
fun LogOtherLabel(label: String, onLabel: (String) -> Unit) {
    OutlinedTextField(
        label,
        onLabel,
        Modifier.fillMaxWidth(),
        label = { Text(stringResource(Res.string.log_other_label)) },
        singleLine = true,
    )
}
