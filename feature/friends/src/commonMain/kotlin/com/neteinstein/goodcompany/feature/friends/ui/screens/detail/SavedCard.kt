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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.ui.components.AttachmentRow
import com.neteinstein.goodcompany.core.ui.components.LinkCard
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.components.headline
import com.neteinstein.goodcompany.core.ui.components.rememberPhotoBitmap
import com.neteinstein.goodcompany.core.ui.resources.attachment_copy
import com.neteinstein.goodcompany.core.ui.resources.attachment_open_link
import com.neteinstein.goodcompany.core.ui.resources.attachment_original_gone
import com.neteinstein.goodcompany.core.ui.resources.close
import com.neteinstein.goodcompany.core.ui.resources.remove_item
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.saved_add_photo
import com.neteinstein.goodcompany.feature.friends.resources.saved_empty
import com.neteinstein.goodcompany.feature.friends.resources.saved_see_all
import com.neteinstein.goodcompany.feature.friends.resources.saved_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** How many attachments the card shows before "See all". */
private const val SAVED_ON_CARD = 3

/** "Saved for <name>" (D6, mockup): the newest three attachments, "See all", and "Add a photo". */
@Composable
internal fun SavedCard(
    firstName: String,
    attachments: List<Attachment>,
    today: LocalDate,
    zone: TimeZone,
    onAddPhoto: () -> Unit,
    onOpen: (Attachment) -> Unit,
    onSeeAll: () -> Unit,
) {
    SectionCard(
        stringResource(Res.string.saved_title, firstName),
        icon = Icons.Rounded.IosShare,
        action = {
            Row {
                if (attachments.size > SAVED_ON_CARD) {
                    TextButton(onClick = onSeeAll) { Text(stringResource(Res.string.saved_see_all)) }
                }
                IconButton(onClick = onAddPhoto) { Icon(Icons.Rounded.AddPhotoAlternate, stringResource(Res.string.saved_add_photo)) }
            }
        },
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (attachments.isEmpty()) {
                Text(
                    stringResource(Res.string.saved_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            attachments.take(SAVED_ON_CARD).forEach { a -> AttachmentRow(a, today, zone, onClick = { onOpen(a) }) }
        }
    }
}

/** "See all": every attachment, newest first, each with a remove button (Undo in the snackbar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedListSheet(
    firstName: String,
    attachments: List<Attachment>,
    today: LocalDate,
    zone: TimeZone,
    onOpen: (Attachment) -> Unit,
    onRemove: (Attachment) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(Res.string.saved_title, firstName),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            items(attachments, key = { it.id }) { a ->
                Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    AttachmentRow(a, today, zone, onClick = { onOpen(a) }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(a) }) {
                        Icon(Icons.Rounded.Delete, stringResource(CoreRes.string.remove_item, a.headline()))
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** What a tap opens in the app: a photo (the original, or the thumbnail when it's gone) or the whole text. */
internal sealed interface AttachmentView {
    val attachment: Attachment

    data class Photo(override val attachment: Attachment, val original: ByteArray?) : AttachmentView {
        override fun equals(other: Any?) = other is Photo && other.attachment == attachment && other.original.contentEquals(original)

        override fun hashCode() = attachment.hashCode()
    }

    data class Text(override val attachment: Attachment) : AttachmentView
}

/** Full-screen view of a saved photo or text: copy for text, remove for both. */
@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun AttachmentViewer(view: AttachmentView, onOpenLink: (String) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val a = view.attachment
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.close)) }
                    Text(
                        a.headline(),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    if (view is AttachmentView.Text) {
                        @Suppress("DEPRECATION")
                        val clipboard = LocalClipboardManager.current
                        IconButton(onClick = { clipboard.setText(AnnotatedString(a.text.orEmpty())) }) {
                            Icon(Icons.Rounded.ContentCopy, stringResource(CoreRes.string.attachment_copy))
                        }
                    }
                    IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, stringResource(CoreRes.string.remove_item, a.headline())) }
                }
                when (view) {
                    is AttachmentView.Photo -> {
                        val original = remember(view.original) {
                            view.original?.let { runCatching { it.decodeToImageBitmap() }.getOrNull() }
                        }
                        val bitmap = original ?: rememberPhotoBitmap(a.thumbnail)
                        Box(
                            Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest),
                            contentAlignment = Alignment.Center,
                        ) {
                            bitmap?.let { Image(it, a.headline(), contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                        }
                        if (original == null) {
                            Text(
                                stringResource(CoreRes.string.attachment_original_gone),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    is AttachmentView.Text -> Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SelectionContainer { Text(a.text.orEmpty(), style = MaterialTheme.typography.bodyLarge) }
                        a.url?.let { url ->
                            LinkCard(a.title, url, onClick = { onOpenLink(url) })
                            TextButton(onClick = { onOpenLink(url) }) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(CoreRes.string.attachment_open_link))
                            }
                        }
                    }
                }
            }
        }
    }
}
