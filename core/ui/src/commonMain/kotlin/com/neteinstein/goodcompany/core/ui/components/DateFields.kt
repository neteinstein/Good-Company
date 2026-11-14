package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

/**
 * Material date picker dialog speaking kotlinx-datetime [LocalDate]. [latest] (inclusive) disables later days.
 * Choosing a day picks it and closes the dialog: there is no OK to tap (decision "Pickers · save without a Save
 * button", issue #64). Changing the year or month alone doesn't pick anything; Cancel or tapping outside keeps the old date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
    latest: LocalDate? = null,
) {
    val latestMillis = latest?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = latestMillis == null || utcTimeMillis <= latestMillis

            override fun isSelectableYear(year: Int) = latest == null || year <= latest.year
        },
    )
    val picked by rememberUpdatedState(onPicked)
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state) {
        // The first value is the initial date; the next one is the user's choice.
        val millis = snapshotFlow { state.selectedDateMillis }.drop(1).filterNotNull().first()
        picked(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date)
        dismiss()
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    ) { DatePicker(state = state) }
}
