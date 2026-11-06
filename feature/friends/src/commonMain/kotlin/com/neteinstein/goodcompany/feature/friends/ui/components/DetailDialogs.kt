package com.neteinstein.goodcompany.feature.friends.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.ui.components.LocalDatePickerDialog
import com.neteinstein.goodcompany.core.ui.format.longLabel
import com.neteinstein.goodcompany.core.ui.format.parseTags
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

@Composable
fun KidDialog(
    kid: Kid?,
    onDismiss: () -> Unit,
    onSave: (name: String, birthday: LocalDate?, likes: List<String>) -> Unit,
) {
    var name by remember { mutableStateOf(kid?.name.orEmpty()) }
    var likes by remember { mutableStateOf(kid?.likes?.joinToString().orEmpty()) }
    var birthday by remember {
        mutableStateOf(kid?.birthday?.let { b -> LocalDate(b.year ?: 2020, Month(b.month), b.day) })
    }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ChildCare, null) },
        title = { Text(if (kid == null) "Add a kid" else "Edit ${kid.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(birthday?.let { "Birthday · ${it.longLabel()}" } ?: "Set birthday")
                }
                OutlinedTextField(
                    likes,
                    { likes = it },
                    label = { Text("Likes (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, birthday, parseTags(likes)) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickDate) LocalDatePickerDialog(birthday, onDismiss = { pickDate = false }, onPicked = { birthday = it })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportantEventDialog(
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (title: String, date: LocalDate, remindDaysBefore: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today) }
    var before by remember { mutableStateOf(1) }
    var pickDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Event, null) },
        title = { Text("Important moment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("What's happening? (surgery, exam…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text(date.longLabel()) }
                Text("Also remind me")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Only on the day", 1 to "1 day before", 3 to "3 days before", 7 to "A week before").forEach { (d, label) ->
                        FilterChip(before == d, { before = d }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, date, before) }, enabled = title.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickDate) LocalDatePickerDialog(date, onDismiss = { pickDate = false }, onPicked = { date = it })
}
