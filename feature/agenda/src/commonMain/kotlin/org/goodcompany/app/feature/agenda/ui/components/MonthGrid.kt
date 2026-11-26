package org.goodcompany.app.feature.agenda.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.goodcompany.app.core.domain.model.AgendaItem
import org.goodcompany.app.core.domain.usecase.MonthCalendar
import org.goodcompany.app.core.ui.format.longLabel
import org.goodcompany.app.core.ui.format.monthLong
import org.goodcompany.app.core.ui.format.weekdayInitial
import org.goodcompany.app.feature.agenda.resources.Res
import org.goodcompany.app.feature.agenda.resources.calendar_day_entries
import org.goodcompany.app.feature.agenda.resources.calendar_month_year
import org.goodcompany.app.feature.agenda.resources.calendar_more
import org.goodcompany.app.feature.agenda.resources.calendar_next_month
import org.goodcompany.app.feature.agenda.resources.calendar_previous_month
import org.goodcompany.app.feature.agenda.resources.calendar_today
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/** Markers a day cell shows before "+N" (issue #138). */
private const val MAX_MARKERS = 3

/**
 * The month calendar of "Everything coming up" (issue #138): ‹ month year › and Today, the weekday initials from
 * [firstDayOfWeek], then the weeks. Each day shows up to three markers in the entry colours, then "+N"; today is
 * ringed and [selected] filled. A horizontal swipe changes the month. Pure Compose, no calendar library.
 */
@Composable
fun MonthGrid(
    month: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    byDay: Map<LocalDate, List<AgendaItem>>,
    firstDayOfWeek: DayOfWeek,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = remember(month, firstDayOfWeek) { MonthCalendar.weeks(month, firstDayOfWeek) }
    val swipe = Modifier.pointerInput(month) {
        var dragged = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragged = 0f },
            onDragEnd = {
                if (abs(dragged) > SWIPE_PX) if (dragged < 0) onNextMonth() else onPreviousMonth()
            },
        ) { _, amount -> dragged += amount }
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp).then(swipe)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPreviousMonth) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(Res.string.calendar_previous_month))
            }
            Text(
                stringResource(Res.string.calendar_month_year, monthLong(month.month), month.year),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(Res.string.calendar_next_month))
            }
            TextButton(onClick = onToday, enabled = MonthCalendar.firstOfMonth(today) != month || selected != today) {
                Text(stringResource(Res.string.calendar_today))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            MonthCalendar.weekdays(firstDayOfWeek).forEach { d ->
                Text(
                    weekdayInitial(d),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        inMonth = day.month == month.month && day.year == month.year,
                        isToday = day == today,
                        isSelected = day == selected,
                        items = byDay[day].orEmpty(),
                        onClick = { onSelectDay(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    items: List<AgendaItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val entries = if (items.isEmpty()) "" else pluralStringResource(Res.plurals.calendar_day_entries, items.size, items.size)
    val label = "${day.longLabel()} $entries".trim()
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) colors.primaryContainer else colors.surface.copy(alpha = 0f),
        modifier = modifier.aspectRatio(0.8f).padding(vertical = 1.dp).semantics {
            contentDescription = label
            selected = isSelected
        },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
            Box(
                Modifier.size(26.dp).then(
                    if (isToday) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.day.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected -> colors.onPrimaryContainer
                        inMonth -> colors.onSurface
                        else -> colors.onSurfaceVariant.copy(alpha = 0.5f)
                    },
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                items.take(MAX_MARKERS).forEach { item ->
                    Box(Modifier.size(6.dp).background(item.tint(), CircleShape))
                }
            }
            if (items.size > MAX_MARKERS) {
                Text(
                    stringResource(Res.string.calendar_more, items.size - MAX_MARKERS),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** How far a drag must go to change the month. */
private const val SWIPE_PX = 120f
