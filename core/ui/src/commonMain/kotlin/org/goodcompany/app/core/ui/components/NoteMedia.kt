package org.goodcompany.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.goodcompany.app.core.domain.model.MediaKind
import org.goodcompany.app.core.domain.model.NoteMedia
import org.goodcompany.app.core.ui.resources.Res
import org.goodcompany.app.core.ui.resources.remove
import org.jetbrains.compose.resources.stringResource

/**
 * A note's photos and videos as a row of thumbnails (issue #119); a video carries a play badge. [onOpen] opens the
 * original from the gallery; [onRemove] puts a × on each (the note popup).
 */
@Composable
fun NoteMediaRow(
    media: List<NoteMedia>,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    onOpen: ((NoteMedia) -> Unit)? = null,
    onRemove: ((NoteMedia) -> Unit)? = null,
) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        media.forEach { m ->
            Box(if (onOpen != null) Modifier.clickable { onOpen(m) } else Modifier) {
                AttachmentThumbnail(m.thumbnail, size)
                if (m.kind == MediaKind.VIDEO) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.Center).size(28.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape).padding(2.dp),
                    )
                }
                if (onRemove != null) {
                    Icon(
                        Icons.Rounded.Close,
                        stringResource(Res.string.remove),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(22.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                            .clickable { onRemove(m) }.padding(3.dp),
                    )
                }
            }
        }
    }
}
