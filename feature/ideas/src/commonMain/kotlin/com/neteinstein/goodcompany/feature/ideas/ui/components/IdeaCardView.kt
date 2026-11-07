package com.neteinstein.goodcompany.feature.ideas.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.IdeaKind
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.feature.ideas.ui.screens.IdeaCard

@Composable
fun IdeaCardView(card: IdeaCard, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    val idea = card.idea
    ElevatedCard(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, label) = when (idea.kind) {
                    IdeaKind.EVENT -> Icons.Rounded.Event to "Plan"
                    IdeaKind.NEWS -> Icons.Rounded.Newspaper to "News"
                    IdeaKind.TALKING_POINT -> Icons.Rounded.Forum to "Talk about"
                }
                InfoPill(label, icon = icon, emphasized = idea.kind == IdeaKind.EVENT)
                idea.whenText?.let {
                    Spacer(Modifier.width(8.dp))
                    InfoPill(it)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Dismiss") }
            }
            Text(idea.title, style = MaterialTheme.typography.titleLarge)
            if (idea.description.isNotBlank()) Text(idea.description, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    card.friends.take(5).forEach { FriendAvatar(it.name, it.initials, size = 32.dp) }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    card.friends.joinToString { it.name.substringBefore(' ') },
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                idea.url?.let { url ->
                    TextButton(onClick = { uri.openUri(url) }) {
                        Text("Open")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
