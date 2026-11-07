package com.neteinstein.goodcompany.feature.ideas.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.feature.ideas.ui.screens.IdeaCard

internal fun IdeaKind.accent(): Pair<Color, Color> = when (this) {
    IdeaKind.EVENT -> Color(0xFFFF8A80) to Color(0xFFE5574F)
    IdeaKind.NEWS -> Color(0xFF64B5F6) to Color(0xFF3949AB)
    IdeaKind.TALKING_POINT -> Color(0xFF4DB6AC) to Color(0xFF00897B)
}

@Composable
fun IdeaCardView(card: IdeaCard, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val idea = card.idea
    val (start, end) = idea.kind.accent()
    val (icon, label) = when (idea.kind) {
        IdeaKind.EVENT -> Icons.Rounded.Event to "Plan together"
        IdeaKind.NEWS -> Icons.Rounded.Newspaper to "They'd love this news"
        IdeaKind.TALKING_POINT -> Icons.Rounded.Forum to "Bring it up"
    }
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(6.dp).background(Brush.horizontalGradient(listOf(start, end))))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).background(end.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = end, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = end, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss idea") }
                }
                Text(idea.title, style = MaterialTheme.typography.titleLarge)
                if (idea.description.isNotBlank()) {
                    Text(idea.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StackedAvatars(card.friends.map { it.name to it.initials }, size = 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(card.friends.joinToString { it.name.substringBefore(' ') }, style = MaterialTheme.typography.labelLarge)
                        idea.whenText?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Schedule, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    idea.url?.let { url ->
                        FilledTonalButton(onClick = { uri.openUri(url) }) {
                            Text("Open")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
