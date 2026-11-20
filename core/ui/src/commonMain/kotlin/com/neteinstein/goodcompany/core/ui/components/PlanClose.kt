package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Plan
import com.neteinstein.goodcompany.core.domain.model.PlanAnswer
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.plan_answer_cancel
import com.neteinstein.goodcompany.core.ui.resources.plan_answer_delete
import com.neteinstein.goodcompany.core.ui.resources.plan_answer_didnt_happen
import com.neteinstein.goodcompany.core.ui.resources.plan_answer_happened
import com.neteinstein.goodcompany.core.ui.resources.plan_answer_not_attended
import com.neteinstein.goodcompany.core.ui.resources.plan_cancelled
import com.neteinstein.goodcompany.core.ui.resources.plan_closed
import com.neteinstein.goodcompany.core.ui.resources.plan_deleted
import com.neteinstein.goodcompany.core.ui.resources.plan_how_did_it_go
import com.neteinstein.goodcompany.core.ui.resources.plan_logged
import com.neteinstein.goodcompany.core.ui.resources.plan_not_attended
import com.neteinstein.goodcompany.core.ui.resources.plan_series_question
import com.neteinstein.goodcompany.core.ui.resources.plan_series_this_one
import com.neteinstein.goodcompany.core.ui.resources.plan_series_whole
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Cancel and Delete on a recurring plan ask "just this one or the whole series?" first (issue #100). */
fun Plan.asksForSeries(answer: PlanAnswer): Boolean = isRecurring && (answer == PlanAnswer.CANCEL || answer == PlanAnswer.DELETE)

/** The answers the sheet offers for [plan]: "I didn't go" only for group and circle plans. */
fun Plan.answers(): List<PlanAnswer> = PlanAnswer.entries.filter { it != PlanAnswer.NOT_ATTENDED || isGroup }

/**
 * The one "How did it go?" sheet (issue #100), used everywhere a plan closes: It happened · Didn't happen ·
 * I didn't go (group and circle plans) · Cancel · Delete. On a recurring plan each answer is for that occurrence;
 * Cancel and Delete first ask "this one or the whole series?". [onAnswer] gets the answer and whether it is for the
 * whole series.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanCloseSheet(plan: Plan, onDismiss: () -> Unit, onAnswer: (PlanAnswer, wholeSeries: Boolean) -> Unit) {
    var series by remember { mutableStateOf<PlanAnswer?>(null) }
    val pending = series
    if (pending != null) {
        PlanSeriesDialog(
            answer = pending,
            onDismiss = onDismiss,
            onPick = { whole -> onAnswer(pending, whole) },
        )
        return
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                stringResource(Res.string.plan_how_did_it_go),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
            )
            Text(
                plan.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
            plan.answers().forEach { answer ->
                val (label, icon) = answer.labelAndIcon()
                val destructive = answer == PlanAnswer.DELETE
                ListItem(
                    headlineContent = { Text(stringResource(label)) },
                    leadingContent = { Icon(icon, null) },
                    colors = if (destructive) {
                        ListItemDefaults.colors(
                            headlineColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier.clickable {
                        if (plan.asksForSeries(answer)) series = answer else onAnswer(answer, false)
                    },
                )
            }
        }
    }
}

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
