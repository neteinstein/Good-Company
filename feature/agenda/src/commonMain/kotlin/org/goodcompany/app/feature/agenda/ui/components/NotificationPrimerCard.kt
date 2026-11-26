package org.goodcompany.app.feature.agenda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.goodcompany.app.feature.agenda.resources.Res
import org.goodcompany.app.feature.agenda.resources.notif_primer_allow
import org.goodcompany.app.feature.agenda.resources.notif_primer_body
import org.goodcompany.app.feature.agenda.resources.notif_primer_not_now
import org.goodcompany.app.feature.agenda.resources.notif_primer_title
import org.jetbrains.compose.resources.stringResource

/**
 * Today's "Turn on notifications" card (issue #98): shown while the system doesn't allow them, so nobody misses a
 * birthday because onboarding's card was skipped. "Allow" asks (or opens system settings); "Not now" hides it for good.
 */
@Composable
fun NotificationPrimerCard(onAllow: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(Res.string.notif_primer_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(Res.string.notif_primer_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onNotNow) { Text(stringResource(Res.string.notif_primer_not_now)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onAllow) { Text(stringResource(Res.string.notif_primer_allow)) }
            }
        }
    }
}
