package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.usecase.FilteredPeople
import com.neteinstein.goodcompany.core.domain.usecase.PeopleFilter
import com.neteinstein.goodcompany.core.domain.usecase.PeopleFiltering
import com.neteinstein.goodcompany.core.domain.usecase.isBuiltIn
import com.neteinstein.goodcompany.core.ui.l10n.closenessLabel
import com.neteinstein.goodcompany.core.ui.l10n.relationToMeText
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.log_search
import com.neteinstein.goodcompany.core.ui.resources.people_filter_all
import com.neteinstein.goodcompany.core.ui.resources.people_filter_count
import com.neteinstein.goodcompany.core.ui.resources.people_filter_everyone
import com.neteinstein.goodcompany.core.ui.resources.people_picker_add
import com.neteinstein.goodcompany.core.ui.resources.people_picker_chosen
import com.neteinstein.goodcompany.core.ui.resources.people_picker_chosen_n
import com.neteinstein.goodcompany.core.ui.resources.people_picker_done
import com.neteinstein.goodcompany.core.ui.resources.people_picker_nobody
import com.neteinstein.goodcompany.core.ui.resources.people_picker_tick_all
import com.neteinstein.goodcompany.core.ui.resources.people_picker_title
import com.neteinstein.goodcompany.core.ui.resources.people_picker_untick_all
import com.neteinstein.goodcompany.core.ui.resources.remove_item
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The People tab's filter row, shared with the people picker (issue #89): the "All" chip, which opens a menu of
 * Everyone and the closeness levels with their counts, then one [CircleChip] per circle, scrolling sideways. One choice
 * at a time: a level or a circle ([PeopleFilter]). Tapping the selected circle clears it.
 */
@Composable
fun PeopleFilterRow(
    circles: List<FriendGroup>,
    filter: PeopleFilter,
    everyoneCount: Int,
    levelCounts: Map<Closeness, Int>,
    onFilter: (PeopleFilter) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    onLongClickCircle: ((String) -> Unit)? = null,
) {
    LazyRow(modifier, contentPadding = contentPadding, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") { ClosenessMenuChip(filter, everyoneCount, levelCounts, onFilter) }
        items(circles, key = { it.id }) { c ->
            val selected = filter.circleId == c.id
            CircleChip(
                c,
                selected = selected,
                onClick = { onFilter(filter.withCircle(c.id.takeUnless { selected })) },
                onLongClick = onLongClickCircle?.let { { it(c.id) } },
                trailing = if (selected) c.memberIds.size.toString() else null,
            )
        }
    }
}

/** "All · 38 ▾" (or "Close · 9 ▾" with a level chosen): opens Everyone and the four levels, each with its count. */
@Composable
private fun ClosenessMenuChip(filter: PeopleFilter, everyoneCount: Int, levelCounts: Map<Closeness, Int>, onFilter: (PeopleFilter) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        val level = filter.closeness
        AllChip(
            if (level == null) {
                stringResource(Res.string.people_filter_all, everyoneCount)
            } else {
                stringResource(Res.string.people_filter_count, closenessLabel(level, short = true), levelCounts[level] ?: 0)
            },
            selected = filter.circleId == null,
            onClick = { open = true },
            showMenuArrow = true,
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.people_filter_everyone, everyoneCount)) },
                onClick = {
                    open = false
                    onFilter(PeopleFilter.Everyone)
                },
            )
            Closeness.entries.forEach { c ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.people_filter_count, closenessLabel(c, short = true), levelCounts[c] ?: 0)) },
                    onClick = {
                        open = false
                        onFilter(filter.withCloseness(c))
                    },
                )
            }
        }
    }
}

/** Whether the picker ticks several people (Done returns them) or returns the first one tapped. */
enum class PeoplePickerMode { MULTIPLE, SINGLE }

/**
 * The one people picker (issue #89, decision "Choosing people · one picker"): a tall sheet with a name search (and
 * kinship, "my cousins", when [kinshipOf] is given), the shared [PeopleFilterRow] (it stays while typing: the search
 * works inside the filter), then everyone grouped by closeness, no cap. People [chosen] when it opened stay on top
 * under "Chosen · N"; "Tick all N" ticks what a filter or search shows. Cancel and "Done · N" stay in reach.
 * [requireSomeone] disables Done with nobody ticked (the Log sheet, We met). [extra] sits above the footer (the gift
 * list's budget).
 */
@Composable
fun PeoplePickerSheet(
    everyone: List<Friend>,
    circles: List<FriendGroup>,
    chosen: List<FriendId>,
    onDismiss: () -> Unit,
    onDone: (List<FriendId>) -> Unit,
    title: String = stringResource(Res.string.people_picker_title),
    requireSomeone: Boolean = false,
    exclude: Set<FriendId> = emptySet(),
    kinshipOf: (suspend (String) -> Set<LinkType>?)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    // Ids as strings so the selection survives rotation.
    var ticked by rememberSaveable { mutableStateOf(chosen.map { it.value }) }
    val atOpen = remember { chosen.toSet() }
    val tickedIds = ticked.map(::FriendId)
    PickerSheet(
        mode = PeoplePickerMode.MULTIPLE,
        everyone = everyone,
        circles = circles,
        title = title,
        ticked = tickedIds.toSet(),
        chosenAtOpen = atOpen,
        onToggle = { id -> ticked = if (id.value in ticked) ticked - id.value else ticked + id.value },
        onTickAll = { ids, on ->
            val values = ids.map { it.value }
            ticked = if (on) ticked + values.filterNot { it in ticked } else ticked - values.toSet()
        },
        onDismiss = onDismiss,
        onPick = {},
        exclude = exclude,
        kinshipOf = kinshipOf,
        footer = {
            extra?.invoke()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(stringResource(Res.string.cancel)) }
                Button(
                    onClick = { onDone(tickedIds) },
                    enabled = !requireSomeone || ticked.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.people_picker_done, ticked.size)) }
            }
        },
    )
}

/** The people picker in single mode: no ticks and no footer; tapping someone returns them and closes. */
@Composable
fun PersonPickerSheet(
    everyone: List<Friend>,
    circles: List<FriendGroup>,
    title: String,
    onDismiss: () -> Unit,
    onPick: (Friend) -> Unit,
    exclude: Set<FriendId> = emptySet(),
) = PickerSheet(
    mode = PeoplePickerMode.SINGLE,
    everyone = everyone,
    circles = circles,
    title = title,
    ticked = emptySet(),
    chosenAtOpen = emptySet(),
    onToggle = {},
    onTickAll = { _, _ -> },
    onDismiss = onDismiss,
    onPick = onPick,
    exclude = exclude,
    kinshipOf = null,
    footer = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    mode: PeoplePickerMode,
    everyone: List<Friend>,
    circles: List<FriendGroup>,
    title: String,
    ticked: Set<FriendId>,
    chosenAtOpen: Set<FriendId>,
    onToggle: (FriendId) -> Unit,
    onTickAll: (List<FriendId>, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onPick: (Friend) -> Unit,
    exclude: Set<FriendId>,
    kinshipOf: (suspend (String) -> Set<LinkType>?)?,
    footer: (@Composable () -> Unit)?,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var circleId by rememberSaveable { mutableStateOf<String?>(null) }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = PeopleFilter(circleId, level?.let { name -> Closeness.entries.firstOrNull { it.name == name } })
    // What the query means as kinship, kept with the query it was worked out for.
    var kinship by remember { mutableStateOf<Pair<String, Set<LinkType>?>>("" to null) }
    if (kinshipOf != null) LaunchedEffect(query) { kinship = query to kinshipOf(query) }
    val result = PeopleFiltering.apply(everyone, filter, circles, query, kinship.second.takeIf { kinship.first == query }, exclude)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                if (mode == PeoplePickerMode.MULTIPLE) {
                    Text(
                        pluralStringResource(Res.plurals.people_picker_chosen_n, ticked.size, ticked.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(stringResource(Res.string.log_search)) },
            )
            PeopleFilterRow(
                circles = circles,
                filter = filter,
                everyoneCount = result.everyoneCount,
                levelCounts = result.levelCounts,
                onFilter = {
                    circleId = it.circleId
                    level = it.closeness?.name
                },
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 8.dp)) {
                val shown = result.people
                val searching = query.isNotBlank() || !filter.isEveryone
                if (mode == PeoplePickerMode.MULTIPLE && searching && shown.isNotEmpty()) {
                    val all = shown.all { it.id in ticked }
                    item(key = "tick-all") {
                        TextButton(onClick = { onTickAll(shown.map { it.id }, !all) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(
                                if (all) {
                                    stringResource(Res.string.people_picker_untick_all)
                                } else {
                                    stringResource(Res.string.people_picker_tick_all, shown.size)
                                },
                            )
                        }
                    }
                }
                if (shown.isEmpty() && query.isNotBlank()) {
                    item(key = "nobody") {
                        Text(
                            stringResource(Res.string.people_picker_nobody, query.trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
                val onTop = shown.filter { it.id in chosenAtOpen }
                if (onTop.isNotEmpty()) {
                    pickerHeading("chosen") { stringResource(Res.string.people_picker_chosen, onTop.size) }
                    items(onTop, key = { "chosen-" + it.id.value }) { f ->
                        PersonPickRow(f, circles, mode, f.id in ticked, onClick = {
                            if (mode ==
                                PeoplePickerMode.SINGLE
                            ) {
                                onPick(f)
                            } else {
                                onToggle(f.id)
                            }
                        })
                    }
                }
                peopleByCloseness(
                    result.copy(people = shown.filterNot { it.id in chosenAtOpen }),
                    circles = circles,
                    mode = mode,
                    ticked = ticked,
                    onClick = { f -> if (mode == PeoplePickerMode.SINGLE) onPick(f) else onToggle(f.id) },
                )
            }
            if (footer != null) {
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    footer()
                }
            }
        }
    }
}

/**
 * The picker's list body: [people] under their closeness headings, each with its count ("Close · 9"), one
 * [PersonPickRow] each. Share to… embeds it under its own suggestions.
 */
fun LazyListScope.peopleByCloseness(
    people: FilteredPeople<Friend>,
    circles: List<FriendGroup>,
    mode: PeoplePickerMode,
    ticked: Set<FriendId>,
    onClick: (Friend) -> Unit,
    keyPrefix: String = "",
) {
    people.byCloseness { it.closeness }.forEach { (level, group) ->
        pickerHeading("$keyPrefix level-${level.name}") {
            stringResource(Res.string.people_filter_count, closenessLabel(level, short = true), group.size)
        }
        items(group, key = { keyPrefix + it.id.value }) { f -> PersonPickRow(f, circles, mode, f.id in ticked, onClick = { onClick(f) }) }
    }
}

private fun LazyListScope.pickerHeading(key: String, text: @Composable () -> String) {
    item(key = "h-$key") {
        Text(
            text(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
        )
    }
}

/** One person in the picker: avatar, full name, relation to you and circles, and a tick in multiple mode. */
@Composable
fun PersonPickRow(
    friend: Friend,
    circles: List<FriendGroup>,
    mode: PeoplePickerMode,
    ticked: Boolean,
    onClick: () -> Unit,
    supporting: String? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = if (mode == PeoplePickerMode.MULTIPLE) Role.Checkbox else Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(friend, size = 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(friend.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = supporting ?: listOfNotNull(
                friend.relationToMe?.let { relationToMeText(it, friend.pronouns) },
                circles.filter { !it.isBuiltIn && friend.id in it.memberIds }.joinToString(", ") { "${it.emoji} ${it.name}" }.ifEmpty { null },
            ).joinToString(" · ").ifEmpty { null }
            line?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (mode == PeoplePickerMode.MULTIPLE) Checkbox(ticked, onCheckedChange = { onClick() })
    }
}

/**
 * "With": the chosen people as avatar chips with their full name and a ×, then "+ People" (issue #89). Used by the
 * reminder and plan editors; the picker opens from "+ People".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChosenPeople(people: List<Friend>, onRemove: (FriendId) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        people.forEach { f ->
            val remove = stringResource(Res.string.remove_item, f.displayName)
            InputChip(
                selected = false,
                onClick = onAdd,
                label = { Text(f.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                avatar = { FriendAvatar(f, size = 24.dp) },
                trailingIcon = {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = remove,
                        modifier = Modifier.size(20.dp).clip(CircleShape).clickable(role = Role.Button) { onRemove(f.id) },
                    )
                },
            )
        }
        AssistChip(
            onClick = onAdd,
            label = { Text(stringResource(Res.string.people_picker_add)) },
            leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
        )
    }
}
