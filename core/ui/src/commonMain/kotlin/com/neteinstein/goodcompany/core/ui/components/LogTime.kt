package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.usecase.LogTimeOfDay
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.log_last_together
import com.neteinstein.goodcompany.core.ui.resources.log_time_afternoon
import com.neteinstein.goodcompany.core.ui.resources.log_time_evening
import com.neteinstein.goodcompany.core.ui.resources.log_time_lunch
import com.neteinstein.goodcompany.core.ui.resources.log_time_morning
import com.neteinstein.goodcompany.core.ui.resources.log_time_now
import com.neteinstein.goodcompany.core.ui.resources.log_time_pick
import com.neteinstein.goodcompany.core.ui.resources.save
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Issue #116: the one icon for "Log" everywhere ("Caught up", "We met" and "I'm with…" became one action), so people
 * learn what it means. Pair it with the word Log (`log_action`).
 */
val LogIcon: ImageVector get() = Icons.Rounded.EditNote

/** "09:05": a time of day in 24-hour form, as the rest of the app writes times. */
fun LocalTime.hhmm(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/**
 * Issue #116: when it happened, under the date chips. "Now" (today only, and the default), Morning 09:00 · Lunch 13:00
 * · Afternoon 16:00 · Evening 20:00, and "Pick a time". On an earlier day nothing chosen means noon. Today's chips later
 * than now are disabled: nothing is logged in the future. One scrolling line, like the date chips.
 */
@Composable
fun LogTimeChoice(time: LocalTime?, date: LocalDate, now: Instant, zone: TimeZone, onTime: (LocalTime?) -> Unit, onPick: () -> Unit) {
    val current = now.toLocalDateTime(zone)
    val isToday = date == current.date
    val nowTime = LocalTime(current.hour, current.minute)
    val picked = time?.takeIf { LogTimeOfDay.of(it) == null }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isToday) {
            FilterChip(time == null, onClick = { onTime(null) }, label = { Text(stringResource(Res.string.log_time_now), maxLines = 1) })
        }
        LogTimeOfDay.entries.forEach { t ->
            FilterChip(
                time == t.time,
                onClick = { onTime(t.time.takeUnless { time == it }) },
                enabled = !isToday || t.time <= nowTime,
                label = { Text("${timeOfDayLabel(t)} ${t.time.hhmm()}", maxLines = 1) },
            )
        }
        FilterChip(
            picked != null,
            onClick = onPick,
            label = { Text(picked?.hhmm() ?: stringResource(Res.string.log_time_pick), maxLines = 1) },
            leadingIcon = { Icon(Icons.Rounded.AccessTime, null, Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun timeOfDayLabel(t: LogTimeOfDay): String = stringResource(
    when (t) {
        LogTimeOfDay.MORNING -> Res.string.log_time_morning
        LogTimeOfDay.LUNCH -> Res.string.log_time_lunch
        LogTimeOfDay.AFTERNOON -> Res.string.log_time_afternoon
        LogTimeOfDay.EVENING -> Res.string.log_time_evening
    },
)

/** "Pick a time": the Material time picker in a dialog, starting on [initial]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalTimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AccessTime, null) },
        title = { Text(stringResource(Res.string.log_time_pick)) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = {
                onPicked(LocalTime(state.hour, state.minute))
                onDismiss()
            }) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/** In person: "Last time together: 3 weeks ago" for the people chosen (issue #116); nothing when they never met. */
@Composable
fun LogLastTogether(lastTogether: Instant?, now: Instant, zone: TimeZone) {
    lastTogether ?: return
    Text(
        stringResource(Res.string.log_last_together, relativePast(lastTogether, now, zone)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
