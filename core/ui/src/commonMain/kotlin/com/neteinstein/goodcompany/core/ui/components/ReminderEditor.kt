package com.neteinstein.goodcompany.core.ui.components

import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.reminder_evening
import com.neteinstein.goodcompany.core.ui.resources.reminder_label
import com.neteinstein.goodcompany.core.ui.resources.reminder_lunch
import com.neteinstein.goodcompany.core.ui.resources.reminder_morning
import com.neteinstein.goodcompany.core.ui.resources.reminder_new
import com.neteinstein.goodcompany.core.ui.resources.reminder_night
import com.neteinstein.goodcompany.core.ui.resources.reminder_no_date
import com.neteinstein.goodcompany.core.ui.resources.reminder_placeholder
import com.neteinstein.goodcompany.core.ui.resources.reminder_slot
import com.neteinstein.goodcompany.core.ui.resources.reminder_when
import com.neteinstein.goodcompany.core.ui.resources.reminder_with
import com.neteinstein.goodcompany.core.ui.resources.save
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.ui.format.longLabel
import kotlinx.datetime.LocalDate

private val HOURS = listOf(9 to Res.string.reminder_morning, 13 to Res.string.reminder_lunch, 18 to Res.string.reminder_evening, 21 to Res.string.reminder_night)

/** "Remind me to book tickets for X to go with Y." */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderEditor(
    friends: List<Friend>,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, friendIds: List<FriendId>, date: LocalDate?, hour: Int) -> Unit,
    preselected: Set<FriendId> = emptySet(),
    initialTitle: String = "",
) {
    var title by remember { mutableStateOf(initialTitle) }
    var selected by remember { mutableStateOf(preselected) }
    var date by remember { mutableStateOf<LocalDate?>(today) }
    var hour by remember { mutableStateOf(18) }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.AlarmAdd, null) },
        title = { Text(stringResource(Res.string.reminder_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text(stringResource(Res.string.reminder_label)) },
                    placeholder = { Text(stringResource(Res.string.reminder_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(Res.string.reminder_with), style = MaterialTheme.typography.labelLarge)
                FlowRow(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    friends.sortedWith(
                        compareByDescending<Friend> {
                            it.id in preselected
                        }.thenByDescending { it.isCloseFriend },
                    ).take(40).forEach { f ->
                        FilterChip(
                            f.id in selected,
                            { selected = if (f.id in selected) selected - f.id else selected + f.id },
                            label = { Text(f.name.substringBefore(' ')) },
                        )
                    }
                }
                Text(stringResource(Res.string.reminder_when), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }) { Text(date?.longLabel() ?: stringResource(Res.string.reminder_no_date)) }
                    if (date != null) TextButton(onClick = { date = null }) { Text(stringResource(Res.string.reminder_no_date)) }
                }
                if (date != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HOURS.forEach { (h, label) -> FilterChip(hour == h, { hour = h }, label = { Text(stringResource(Res.string.reminder_slot, stringResource(label), h)) }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, selected.toList(), date, hour) }, enabled = title.isNotBlank()) { Text(stringResource(Res.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
    if (pickDate) LocalDatePickerDialog(date ?: today, onDismiss = { pickDate = false }, onPicked = { date = it })
}
