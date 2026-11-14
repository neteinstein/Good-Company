package com.neteinstein.goodcompany.feature.friends.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Gift
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.ui.components.LocalDatePickerDialog
import com.neteinstein.goodcompany.core.ui.format.longLabel
import com.neteinstein.goodcompany.core.ui.l10n.occasionLabel
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.event_1_day
import com.neteinstein.goodcompany.feature.friends.resources.event_3_days
import com.neteinstein.goodcompany.feature.friends.resources.event_also_remind
import com.neteinstein.goodcompany.feature.friends.resources.event_on_day
import com.neteinstein.goodcompany.feature.friends.resources.event_title
import com.neteinstein.goodcompany.feature.friends.resources.event_week
import com.neteinstein.goodcompany.feature.friends.resources.event_what
import com.neteinstein.goodcompany.feature.friends.resources.feeds_add_title
import com.neteinstein.goodcompany.feature.friends.resources.feeds_follow
import com.neteinstein.goodcompany.feature.friends.resources.feeds_url
import com.neteinstein.goodcompany.feature.friends.resources.gifts_given_title
import com.neteinstein.goodcompany.feature.friends.resources.gifts_idea_label
import com.neteinstein.goodcompany.feature.friends.resources.gifts_mark_given
import com.neteinstein.goodcompany.feature.friends.resources.gifts_new
import com.neteinstein.goodcompany.feature.friends.resources.gifts_note_label
import com.neteinstein.goodcompany.feature.friends.resources.news_dialog_title
import com.neteinstein.goodcompany.feature.friends.resources.news_placeholder
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

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
                    listOf(
                        0 to Res.string.event_on_day,
                        1 to Res.string.event_1_day,
                        3 to Res.string.event_3_days,
                        7 to Res.string.event_week,
                    ).forEach { (d, label) ->
                        FilterChip(before == d, { before = d }, label = { Text(stringResource(label)) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, date, before) }, enabled = title.isNotBlank()) { Text(stringResource(CoreRes.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
    if (pickDate) LocalDatePickerDialog(date, onDismiss = { pickDate = false }, onPicked = { date = it })
}

/** New gift idea. A kid's gift ideas go on the kid's own page (F9). */
@Composable
fun GiftDialog(onDismiss: () -> Unit, onSave: (title: String, note: String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CardGiftcard, null) },
        title = { Text(stringResource(Res.string.gifts_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, {
                    title = it
                }, label = { Text(stringResource(Res.string.gifts_idea_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, {
                    note = it
                }, label = { Text(stringResource(Res.string.gifts_note_label)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, note) }, enabled = title.isNotBlank()) { Text(stringResource(CoreRes.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

/**
 * "Mark as given": asks for the occasion so the gift history reads well. Choosing one marks the gift given at once
 * (#64); "Mark as given" records it without an occasion. The screen offers Undo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GiftGivenDialog(gift: Gift, onDismiss: () -> Unit, onConfirm: (GiftOccasion?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CardGiftcard, null) },
        title = { Text(gift.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.gifts_given_title))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GiftOccasion.entries.forEach { o ->
                        FilterChip(selected = false, onClick = { onConfirm(o) }, label = { Text(occasionLabel(o)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(null) }) { Text(stringResource(Res.string.gifts_mark_given)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

/** "What's new with Ana?" — one line about their life (new job, a move…). */
@Composable
fun LifeUpdateDialog(firstName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.NewReleases, null) },
        title = { Text(stringResource(Res.string.news_dialog_title, firstName)) },
        text = {
            OutlinedTextField(
                text,
                { text = it },
                placeholder = { Text(stringResource(Res.string.news_placeholder)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text(stringResource(CoreRes.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

/** "Follow their blog": a link to a blog, Substack, Letterboxd… The app finds the feed behind it. */
@Composable
fun AddFeedDialog(busy: Boolean, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.RssFeed, null) },
        title = { Text(stringResource(Res.string.feeds_add_title)) },
        text = {
            OutlinedTextField(
                url,
                { url = it },
                label = { Text(stringResource(Res.string.feeds_url)) },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(url) }, enabled = url.isNotBlank() && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(Res.string.feeds_follow))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}
