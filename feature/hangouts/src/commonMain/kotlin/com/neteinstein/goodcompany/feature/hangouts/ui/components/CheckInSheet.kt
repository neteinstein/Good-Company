package com.neteinstein.goodcompany.feature.hangouts.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.feature.hangouts.ui.screens.CheckInDraft

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CheckInSheet(
    draft: CheckInDraft,
    friends: List<Friend>,
    onToggleFriend: (FriendId) -> Unit,
    onQueryChange: (String) -> Unit,
    onPlaceChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onUseLocation: () -> Unit,
    onClearLocation: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("I'm with…", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = draft.query,
                onValueChange = onQueryChange,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Find a friend") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            )
            val q = draft.query.trim().lowercase()
            val shown = friends
                .filter { it.id in draft.selected || q.isEmpty() || it.name.lowercase().contains(q) }
                .sortedWith(compareByDescending<Friend> { it.id in draft.selected }.thenByDescending { it.isCloseFriend })
                .take(MAX_CHIPS)
            FlowRow(
                Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shown.forEach { f ->
                    val selected = f.id in draft.selected
                    FilterChip(
                        selected = selected,
                        onClick = { onToggleFriend(f.id) },
                        label = { Text(f.name) },
                        leadingIcon = {
                            AnimatedContent(selected) { on ->
                                if (on) {
                                    Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize))
                                } else {
                                    FriendAvatar(f.name, f.initials, size = 20.dp)
                                }
                            }
                        },
                    )
                }
            }
            OutlinedTextField(
                value = draft.place,
                onValueChange = onPlaceChange,
                leadingIcon = { Icon(Icons.Rounded.Place, null) },
                label = { Text("Where? (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val location = draft.location
                when {
                    draft.isLocating -> {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Finding you…")
                    }
                    location != null -> InputChip(
                        selected = true,
                        onClick = onClearLocation,
                        label = { Text(Place(null, location.latitude, location.longitude).label.orEmpty()) },
                        leadingIcon = { Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp)) },
                        trailingIcon = { Icon(Icons.Rounded.Close, "Clear location", Modifier.size(18.dp)) },
                    )
                    else -> AssistChip(
                        onClick = onUseLocation,
                        label = { Text("Use my location") },
                        leadingIcon = { Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp)) },
                    )
                }
            }
            OutlinedTextField(
                value = draft.note,
                onValueChange = onNoteChange,
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
            Button(onClick = onSave, enabled = draft.canSave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (draft.selected.isEmpty()) "Pick who you're with" else "Log it · ${draft.selected.size}")
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private const val MAX_CHIPS = 60
