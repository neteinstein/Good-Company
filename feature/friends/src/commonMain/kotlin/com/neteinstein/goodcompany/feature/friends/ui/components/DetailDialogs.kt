package com.neteinstein.goodcompany.feature.friends.ui.components

import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.event_1_day
import com.neteinstein.goodcompany.feature.friends.resources.event_3_days
import com.neteinstein.goodcompany.feature.friends.resources.event_also_remind
import com.neteinstein.goodcompany.feature.friends.resources.event_on_day
import com.neteinstein.goodcompany.feature.friends.resources.event_title
import com.neteinstein.goodcompany.feature.friends.resources.event_week
import com.neteinstein.goodcompany.feature.friends.resources.event_what
import com.neteinstein.goodcompany.feature.friends.resources.gifts_for
import com.neteinstein.goodcompany.feature.friends.resources.gifts_for_them
import com.neteinstein.goodcompany.feature.friends.resources.gifts_given_title
import com.neteinstein.goodcompany.feature.friends.resources.gifts_idea_label
import com.neteinstein.goodcompany.feature.friends.resources.gifts_mark_given
import com.neteinstein.goodcompany.feature.friends.resources.gifts_new
import com.neteinstein.goodcompany.feature.friends.resources.gifts_note_label
import com.neteinstein.goodcompany.feature.friends.resources.kid_add
import com.neteinstein.goodcompany.feature.friends.resources.kid_birthday
import com.neteinstein.goodcompany.feature.friends.resources.kid_edit
import com.neteinstein.goodcompany.feature.friends.resources.kid_likes
import com.neteinstein.goodcompany.feature.friends.resources.kid_name
import com.neteinstein.goodcompany.feature.friends.resources.kid_set_birthday
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.save
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.CardGiftcard
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.ui.l10n.occasionLabel

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
        title = { Text(if (kid == null) stringResource(Res.string.kid_add) else stringResource(Res.string.kid_edit, kid.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(Res.string.kid_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(birthday?.let { stringResource(Res.string.kid_birthday, it.longLabel()) } ?: stringResource(Res.string.kid_set_birthday))
                }
                OutlinedTextField(
                    likes,
                    { likes = it },
                    label = { Text(stringResource(Res.string.kid_likes)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, birthday, parseTags(likes)) }, enabled = name.isNotBlank()) { Text(stringResource(CoreRes.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
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
        title = { Text(stringResource(Res.string.event_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text(stringResource(Res.string.event_what)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) { Text(date.longLabel()) }
                Text(stringResource(Res.string.event_also_remind))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to Res.string.event_on_day, 1 to Res.string.event_1_day, 3 to Res.string.event_3_days, 7 to Res.string.event_week).forEach { (d, label) ->
                        FilterChip(before == d, { before = d }, label = { Text(stringResource(label)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, date, before) }, enabled = title.isNotBlank()) { Text(stringResource(CoreRes.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
    if (pickDate) LocalDatePickerDialog(date, onDismiss = { pickDate = false }, onPicked = { date = it })
}

/** New gift idea, optionally for one of the friend's kids. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GiftDialog(friend: Friend, onDismiss: () -> Unit, onSave: (title: String, note: String, forKidId: String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var kidId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CardGiftcard, null) },
        title = { Text(stringResource(Res.string.gifts_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(Res.string.gifts_idea_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(Res.string.gifts_note_label)) }, modifier = Modifier.fillMaxWidth())
                if (friend.kids.isNotEmpty()) {
                    Text(stringResource(Res.string.gifts_for))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(kidId == null, { kidId = null }, label = { Text(stringResource(Res.string.gifts_for_them, friend.name.substringBefore(' '))) })
                        friend.kids.forEach { kid -> FilterChip(kidId == kid.id, { kidId = kid.id }, label = { Text(kid.name) }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, note, kidId) }, enabled = title.isNotBlank()) { Text(stringResource(CoreRes.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

/** "Mark as given": asks for the occasion so the gift history reads well. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GiftGivenDialog(gift: Gift, onDismiss: () -> Unit, onConfirm: (GiftOccasion?) -> Unit) {
    var occasion by remember { mutableStateOf<GiftOccasion?>(GiftOccasion.BIRTHDAY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CardGiftcard, null) },
        title = { Text(gift.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.gifts_given_title))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GiftOccasion.entries.forEach { o -> FilterChip(occasion == o, { occasion = if (occasion == o) null else o }, label = { Text(occasionLabel(o)) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(occasion) }) { Text(stringResource(Res.string.gifts_mark_given)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}
