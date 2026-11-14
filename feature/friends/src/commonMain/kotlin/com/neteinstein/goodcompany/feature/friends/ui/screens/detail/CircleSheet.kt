package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.ui.resources.plan_people_count
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.circles_new_with
import com.neteinstein.goodcompany.feature.friends.resources.circles_sheet_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/**
 * "+ Circle" on a person's page (issue #70): every stored circle with a checkbox, each tick saving at once (#64), and
 * "New circle with <name>". The built-in Family circle isn't listed: it follows relations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CircleSheet(
    f: Friend,
    circles: List<FriendGroup>,
    onToggle: (groupId: String, member: Boolean) -> Unit,
    onNewCircle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstName = f.displayName.substringBefore(' ')
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(
                stringResource(Res.string.circles_sheet_title, firstName),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
            circles.forEach { circle ->
                val member = f.id in circle.memberIds
                ListItem(
                    headlineContent = { Text("${circle.emoji} ${circle.name}") },
                    supportingContent = {
                        Text(pluralStringResource(CoreRes.plurals.plan_people_count, circle.memberIds.size, circle.memberIds.size))
                    },
                    leadingContent = { Checkbox(checked = member, onCheckedChange = null) },
                    colors = transparent,
                    modifier = Modifier.toggleable(value = member, role = Role.Checkbox, onValueChange = { onToggle(circle.id, it) }),
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(Res.string.circles_new_with, firstName), color = MaterialTheme.colorScheme.primary) },
                leadingContent = { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) },
                colors = transparent,
                modifier = Modifier.clickable(onClick = onNewCircle),
            )
        }
    }
}
