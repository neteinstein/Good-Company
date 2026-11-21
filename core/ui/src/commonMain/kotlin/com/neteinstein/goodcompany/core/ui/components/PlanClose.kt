package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanAnswer
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.edit
import com.neteinstein.goodcompany.core.ui.resources.plan_add_calendar
import com.neteinstein.goodcompany.core.ui.resources.plan_answer_cancel
import com.neteinstein.goodcompany.core.ui.resources.plan_answer_delete
import com.neteinstein.goodcompany.core.ui.resources.plan_answer_didnt_happen
import com.neteinstein.goodcompany.core.ui.resources.plan_answer_happened
import com.neteinstein.goodcompany.core.ui.resources.plan_answer_not_attended
import com.neteinstein.goodcompany.core.ui.resources.plan_cancel
import com.neteinstein.goodcompany.core.ui.resources.plan_cancelled
import com.neteinstein.goodcompany.core.ui.resources.plan_closed
import com.neteinstein.goodcompany.core.ui.resources.plan_deleted
import com.neteinstein.goodcompany.core.ui.resources.plan_logged
import com.neteinstein.goodcompany.core.ui.resources.plan_more_answers
import com.neteinstein.goodcompany.core.ui.resources.plan_not_attended
import com.neteinstein.goodcompany.core.ui.resources.plan_options
import com.neteinstein.goodcompany.core.ui.resources.plan_series_question
import com.neteinstein.goodcompany.core.ui.resources.plan_series_this_one
import com.neteinstein.goodcompany.core.ui.resources.plan_series_whole
import com.neteinstein.goodcompany.core.ui.resources.plan_skip_one
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Cancel and Delete on a recurring plan ask "just this one or the whole series?" first (issue #100). */
fun Plan.asksForSeries(answer: PlanAnswer): Boolean = isRecurring && (answer == PlanAnswer.CANCEL || answer == PlanAnswer.DELETE)

/** Every answer for [plan]: "I didn't go" only for group and circle plans. */
fun Plan.answers(): List<PlanAnswer> = PlanAnswer.entries.filter { it != PlanAnswer.NOT_ATTENDED || isGroup }

/** The answers behind ▾ next to "It happened" (issue #136): Didn't happen · I didn't go · Cancel plan · Delete. */
fun Plan.otherAnswers(): List<PlanAnswer> = answers().filter { it != PlanAnswer.HAPPENED }

/**
 * "It happened ▾" on a plan whose date has come (issues #100, #136): "It happened" in one tap, the other answers
 * ([otherAnswers]) behind ▾. On a recurring plan every answer is for that occurrence; Cancel and Delete first ask
 * "this one or the whole series?". [onAnswer] gets the answer and whether it is for the whole series.
 */
@Composable
fun PlanHappenedButton(plan: Plan, onAnswer: (PlanAnswer, wholeSeries: Boolean) -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    var series by remember { mutableStateOf<PlanAnswer?>(null) }
    val padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        FilledTonalButton(
            onClick = { onAnswer(PlanAnswer.HAPPENED, false) },
            shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 15, bottomEndPercent = 15),
            contentPadding = padding,
            modifier = Modifier.height(34.dp),
        ) {
            Icon(Icons.Rounded.Check, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.plan_answer_happened), style = MaterialTheme.typography.labelLarge)
        }
        Box {
            FilledTonalButton(
                onClick = { menu = true },
                shape = RoundedCornerShape(topStartPercent = 15, bottomStartPercent = 15, topEndPercent = 50, bottomEndPercent = 50),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.height(34.dp),
            ) { Icon(Icons.Rounded.ArrowDropDown, stringResource(Res.string.plan_more_answers), Modifier.size(20.dp)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                plan.otherAnswers().forEach { answer ->
                    PlanMenuItem(answer.menuLabel(), answer.labelAndIcon().second, destructive = answer == PlanAnswer.DELETE) {
                        menu = false
                        if (plan.asksForSeries(answer)) series = answer else onAnswer(answer, false)
                    }
                }
            }
        }
    }
    series?.let { a ->
        PlanSeriesDialog(a, onDismiss = { series = null }, onPick = { whole ->
            series = null
            onAnswer(a, whole)
        })
    }
}

/**
 * The ⋮ on an upcoming plan (issue #136): Edit · Add to calendar · Skip this one (a series) · Cancel plan · Delete.
 * Cancel and Delete on a series first ask "this one or the whole series?"; [onAnswer] gets CANCEL or DELETE and
 * whether it is for the whole series.
 */
@Composable
fun PlanUpcomingMenu(
    plan: Plan,
    onEdit: () -> Unit,
    onAddToCalendar: () -> Unit,
    onSkip: () -> Unit,
    onAnswer: (PlanAnswer, wholeSeries: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    var series by remember { mutableStateOf<PlanAnswer?>(null) }
    Box(modifier) {
        // Issue #130: the timeline's ⋮ size, so plans, reminders, birthdays and entries line up.
        IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.MoreVert, stringResource(Res.string.plan_options), Modifier.size(20.dp))
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            fun close(action: () -> Unit): () -> Unit = {
                menu = false
                action()
            }
            fun answer(a: PlanAnswer): () -> Unit = close { if (plan.asksForSeries(a)) series = a else onAnswer(a, false) }
            PlanMenuItem(Res.string.edit, Icons.Rounded.EditCalendar, onClick = close(onEdit))
            PlanMenuItem(Res.string.plan_add_calendar, Icons.Rounded.CalendarMonth, onClick = close(onAddToCalendar))
            if (plan.isRecurring) PlanMenuItem(Res.string.plan_skip_one, Icons.Rounded.EventBusy, onClick = close(onSkip))
            PlanMenuItem(Res.string.plan_cancel, Icons.Rounded.Close, onClick = answer(PlanAnswer.CANCEL))
            PlanMenuItem(Res.string.plan_answer_delete, Icons.Rounded.DeleteOutline, destructive = true, onClick = answer(PlanAnswer.DELETE))
        }
    }
    series?.let { a ->
        PlanSeriesDialog(a, onDismiss = { series = null }, onPick = { whole ->
            series = null
            onAnswer(a, whole)
        })
    }
}

@Composable
private fun PlanMenuItem(label: StringResource, icon: ImageVector, destructive: Boolean = false, onClick: () -> Unit) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    DropdownMenuItem(
        text = { Text(stringResource(label), color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified) },
        leadingIcon = { Icon(icon, null, tint = tint) },
        onClick = onClick,
    )
}

/** "Cancel plan" in the menus, so it never reads as "close this menu". */
private fun PlanAnswer.menuLabel(): StringResource = if (this == PlanAnswer.CANCEL) Res.string.plan_cancel else labelAndIcon().first

/** "Just this one or the whole series?" for Cancel and Delete on a recurring plan. */
@Composable
fun PlanSeriesDialog(answer: PlanAnswer, onDismiss: () -> Unit, onPick: (wholeSeries: Boolean) -> Unit) {
    val (label, _) = answer.labelAndIcon()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Repeat, null) },
        title = { Text(stringResource(label)) },
        text = { Text(stringResource(Res.string.plan_series_question)) },
        confirmButton = {
            TextButton(onClick = { onPick(true) }) { Text(stringResource(Res.string.plan_series_whole)) }
        },
        dismissButton = { TextButton(onClick = { onPick(false) }) { Text(stringResource(Res.string.plan_series_this_one)) } },
    )
}

/** The snackbar text once a plan is answered (issue #100). */
suspend fun PlanAnswer.doneText(): String = getString(
    when (this) {
        PlanAnswer.HAPPENED -> Res.string.plan_logged
        PlanAnswer.DIDNT_HAPPEN -> Res.string.plan_closed
        PlanAnswer.NOT_ATTENDED -> Res.string.plan_not_attended
        PlanAnswer.CANCEL -> Res.string.plan_cancelled
        PlanAnswer.DELETE -> Res.string.plan_deleted
    },
)

private fun PlanAnswer.labelAndIcon(): Pair<StringResource, ImageVector> = when (this) {
    PlanAnswer.HAPPENED -> Res.string.plan_answer_happened to Icons.Rounded.TaskAlt
    PlanAnswer.DIDNT_HAPPEN -> Res.string.plan_answer_didnt_happen to Icons.Rounded.EventBusy
    PlanAnswer.NOT_ATTENDED -> Res.string.plan_answer_not_attended to Icons.Rounded.Groups
    PlanAnswer.CANCEL -> Res.string.plan_answer_cancel to Icons.Rounded.Close
    PlanAnswer.DELETE -> Res.string.plan_answer_delete to Icons.Rounded.DeleteOutline
}
