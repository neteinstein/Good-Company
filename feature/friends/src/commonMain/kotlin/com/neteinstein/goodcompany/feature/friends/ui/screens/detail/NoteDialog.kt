package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neteinstein.goodcompany.core.domain.model.NoteMedia
import com.neteinstein.goodcompany.core.ui.components.LocalDatePickerDialog
import com.neteinstein.goodcompany.core.ui.components.LogDateChoice
import com.neteinstein.goodcompany.core.ui.components.NoteMediaRow
import com.neteinstein.goodcompany.core.ui.components.rememberPhotoBitmap
import com.neteinstein.goodcompany.core.ui.resources.attachment_original_gone
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.close
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.note_add_media
import com.neteinstein.goodcompany.feature.friends.resources.note_edit_title
import com.neteinstein.goodcompany.feature.friends.resources.note_media_explained
import com.neteinstein.goodcompany.feature.friends.resources.note_new_title
import com.neteinstein.goodcompany.feature.friends.resources.note_text
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/**
 * "+ Note" (issue #119): its own popup, just the note: the text, the day (today by default) and "Add photo or video".
 * The first time media is added it says that the app keeps a small preview and the original stays in the gallery.
 */
@Composable
internal fun NoteDialog(
    editing: NoteEditing,
    today: LocalDate,
    zone: TimeZone,
    mediaExplained: Boolean,
    onAddMedia: () -> Unit,
    onRemoveMedia: (String) -> Unit,
    onSave: (text: String, date: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(editing.entry?.id) { mutableStateOf(editing.entry?.text.orEmpty()) }
    var date by remember(editing.entry?.id) { mutableStateOf(editing.entry?.at?.toLocalDateTime(zone)?.date ?: today) }
    var pickingDate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.Notes, null) },
        title = { Text(stringResource(if (editing.entry == null) Res.string.note_new_title else Res.string.note_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    text,
                    { text = it },
                    label = { Text(stringResource(Res.string.note_text)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                )
                LogDateChoice(date, today, onDate = { date = it }, onPick = { pickingDate = true })
                if (editing.media.isNotEmpty()) {
                    NoteMediaRow(editing.media, onRemove = { onRemoveMedia(it.id) })
                    if (!mediaExplained) {
                        Text(
                            stringResource(Res.string.note_media_explained),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(onClick = onAddMedia, enabled = !editing.isAddingMedia) {
                    if (editing.isAddingMedia) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.note_add_media))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text, date) },
                enabled = (text.isNotBlank() || editing.media.isNotEmpty()) && !editing.isAddingMedia,
            ) { Text(stringResource(CoreRes.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
    if (pickingDate) {
        LocalDatePickerDialog(date, onDismiss = { pickingDate = false }, onPicked = { date = it }, latest = today)
    }
}

/**
 * A note's photo or video, opened in the app (issue #119): the original when [original] was loaded, otherwise the
 * thumbnail with "Original no longer in your gallery".
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun NoteMediaViewer(media: NoteMedia, original: ByteArray?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.close)) }
                }
                val loaded = remember(original) { original?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() } }
                val bitmap = loaded ?: rememberPhotoBitmap(media.thumbnail)
                Box(
                    Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) {
                    bitmap?.let { Image(it, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                }
                if (loaded == null) {
                    Text(
                        stringResource(CoreRes.string.attachment_original_gone),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
