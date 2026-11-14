package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Attachment
import com.neteinstein.goodcompany.core.domain.model.AttachmentKind
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import com.neteinstein.goodcompany.core.domain.util.toLocalDate
import com.neteinstein.goodcompany.core.ui.format.relativeDayPhrase
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.attachment_counted_call
import com.neteinstein.goodcompany.core.ui.resources.attachment_counted_contact
import com.neteinstein.goodcompany.core.ui.resources.attachment_counted_email
import com.neteinstein.goodcompany.core.ui.resources.attachment_counted_message
import com.neteinstein.goodcompany.core.ui.resources.attachment_counted_met
import com.neteinstein.goodcompany.core.ui.resources.attachment_kind_link
import com.neteinstein.goodcompany.core.ui.resources.attachment_kind_photo
import com.neteinstein.goodcompany.core.ui.resources.attachment_kind_text
import com.neteinstein.goodcompany.core.ui.resources.attachment_note
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

/** A square thumbnail of a shared photo (or a picture icon when it can't be drawn). */
@Composable
fun AttachmentThumbnail(photo: ContactPhoto?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = rememberPhotoBitmap(photo)
    if (bitmap != null) {
        Image(bitmap, null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(MaterialTheme.shapes.medium))
    } else {
        Surface(modifier.size(size), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

/** "Photo", "Link" or "Chat text". */
@Composable
fun attachmentKindLabel(kind: AttachmentKind): String = stringResource(
    when (kind) {
        AttachmentKind.PHOTO -> Res.string.attachment_kind_photo
        AttachmentKind.LINK -> Res.string.attachment_kind_link
        AttachmentKind.TEXT -> Res.string.attachment_kind_text
    },
)

/** "counted as met", "counted as a message" … or "idea or note". */
@Composable
fun attachmentCountLabel(attachment: Attachment): String = stringResource(
    if (!attachment.countsAsInteraction) {
        Res.string.attachment_note
    } else {
        when (attachment.interactionType) {
            InteractionType.MEETUP -> Res.string.attachment_counted_met
            InteractionType.MESSAGE, InteractionType.WHATSAPP -> Res.string.attachment_counted_message
            InteractionType.CALL, InteractionType.VIDEO_CALL -> Res.string.attachment_counted_call
            InteractionType.EMAIL -> Res.string.attachment_counted_email
            null -> Res.string.attachment_counted_contact
        }
    },
)

/** The headline of an attachment: its caption, else the link title, else the start of the text, else the address. */
fun Attachment.headline(): String = caption.trim().ifEmpty { title?.trim().orEmpty() }
    .ifEmpty { text?.trim()?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim().orEmpty() }
    .ifEmpty { url.orEmpty() }

/**
 * One saved item (mockup "Saved for Rui"): a photo thumbnail with its caption, a link card with its title and host,
 * or the text quoted. The line under it says what it is, how it counted and when.
 */
@Composable
fun AttachmentRow(attachment: Attachment, today: LocalDate, zone: TimeZone, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val meta = listOf(
        attachmentKindLabel(attachment.kind),
        attachmentCountLabel(attachment),
        relativeDayPhrase(attachment.at.toLocalDate(zone), today),
    ).joinToString(" · ")
    when (attachment.kind) {
        AttachmentKind.PHOTO -> Row(
            modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttachmentThumbnail(attachment.thumbnail, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(attachment.headline(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AttachmentKind.LINK -> Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            LinkCard(attachment.title ?: attachment.caption.ifBlank { null }, attachment.host ?: attachment.url.orEmpty(), onClick)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AttachmentKind.TEXT -> Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Icon(
                    Icons.AutoMirrored.Rounded.Chat,
                    null,
                    Modifier.padding(8.dp).size(18.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                QuotedText(attachment.text.orEmpty(), onClick, maxLines = 4)
                val caption = attachment.caption.trim().takeIf { it.isNotEmpty() && it != attachment.text?.trim() }
                Text(
                    listOfNotNull(caption, meta).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A link as a card: an icon, the title (only if the sending app gave one) and the address. Never fetched. */
@Composable
fun LinkCard(title: String?, address: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().let { if (onClick != null) it.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick) else it },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(Icons.Rounded.Link, null, Modifier.padding(8.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                title?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Shared text in a bubble, in quotes. */
@Composable
fun QuotedText(text: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Surface(
        modifier = modifier.let { if (onClick != null) it.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick) else it },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            "“${text.trim()}”",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** What a timeline entry logged from a share shows under it: the thumbnail, the link card or the quoted text. */
@Composable
fun AttachmentPreview(attachment: Attachment, modifier: Modifier = Modifier) {
    when (attachment.kind) {
        AttachmentKind.PHOTO -> AttachmentThumbnail(attachment.thumbnail, 72.dp, modifier)
        AttachmentKind.LINK -> LinkCard(attachment.title, attachment.host ?: attachment.url.orEmpty(), null, modifier)
        AttachmentKind.TEXT -> QuotedText(attachment.text.orEmpty(), null, modifier, maxLines = 3)
    }
}
