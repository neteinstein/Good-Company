package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkCategory
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.usecase.of
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.LocalDatePickerDialog
import com.neteinstein.goodcompany.core.ui.components.PersonPickerSheet
import com.neteinstein.goodcompany.core.ui.components.SaveSheetHeader
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.format.monthYearLabel
import com.neteinstein.goodcompany.core.ui.l10n.linkCategoryLabel
import com.neteinstein.goodcompany.core.ui.l10n.linkWord
import com.neteinstein.goodcompany.core.ui.l10n.pronounsLabel
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.link_change
import com.neteinstein.goodcompany.feature.friends.resources.link_ended
import com.neteinstein.goodcompany.feature.friends.resources.link_ended_value
import com.neteinstein.goodcompany.feature.friends.resources.link_is_of
import com.neteinstein.goodcompany.feature.friends.resources.link_no_people
import com.neteinstein.goodcompany.feature.friends.resources.link_preview
import com.neteinstein.goodcompany.feature.friends.resources.link_remind_anniversary
import com.neteinstein.goodcompany.feature.friends.resources.link_remove
import com.neteinstein.goodcompany.feature.friends.resources.link_row_since
import com.neteinstein.goodcompany.feature.friends.resources.link_row_years
import com.neteinstein.goodcompany.feature.friends.resources.link_search
import com.neteinstein.goodcompany.feature.friends.resources.link_sheet_edit_title
import com.neteinstein.goodcompany.feature.friends.resources.link_sheet_title
import com.neteinstein.goodcompany.feature.friends.resources.link_since
import com.neteinstein.goodcompany.feature.friends.resources.link_since_value
import com.neteinstein.goodcompany.feature.friends.resources.link_words_for
import com.neteinstein.goodcompany.feature.friends.resources.links_other_title
import com.neteinstein.goodcompany.feature.friends.resources.relation_to_me_clear
import com.neteinstein.goodcompany.feature.friends.resources.relation_to_me_family_note
import com.neteinstein.goodcompany.feature.friends.resources.relation_to_me_title
import org.jetbrains.compose.resources.stringResource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

// Links between people (issue #39, F6, F18): rows on the friend page, the link sheet and the "is your…" sheet.

/** One linked person: their name and what they are to the page's friend; opens their page, the pencil edits the link. */
@Composable
internal fun LinkRow(linked: LinkedPerson, onOpen: (FriendId) -> Unit, onEdit: (LinkedPerson) -> Unit) {
    val person = linked.person
    val link = linked.link
    ListItem(
        modifier = Modifier.clickable { onOpen(person.id) },
        leadingContent = { FriendAvatar(person, size = 44.dp) },
        headlineContent = { Text(person.displayName, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            val start = link.startedOn
            val end = link.endedOn
            val dates = when {
                start != null && end != null -> stringResource(Res.string.link_row_years, start.year, end.year)
                start != null -> stringResource(Res.string.link_row_since, start.monthYearLabel())
                else -> null
            }
            Text(listOfNotNull(linkWord(linked.role, person.pronouns), dates).joinToString(" · "))
        },
        trailingContent = {
            IconButton(onClick = { onEdit(linked) }) { Icon(Icons.Rounded.Edit, stringResource(Res.string.link_change)) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Friends and colleagues: the links that aren't family or romantic, in their own card. Hidden when there are none. */
@Composable
internal fun OtherLinksCard(links: List<LinkedPerson>, onOpen: (FriendId) -> Unit, onEdit: (LinkedPerson) -> Unit) {
    if (links.isEmpty()) return
    SectionCard(stringResource(Res.string.links_other_title), icon = Icons.Rounded.Handshake) {
        links.forEach { LinkRow(it, onOpen, onEdit) }
    }
}

/**
 * "Link <name> to someone": one sheet for every link (from the friend's side). Pick the person, the words for them
 * and what they are to the friend; romantic links get a start date and the anniversary reminder, an ex also an end
 * date. The other side's wording is previewed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LinkSheet(
    friend: Friend,
    others: List<Friend>,
    editing: LinkedPerson?,
    onDismiss: () -> Unit,
    onSave: (LinkEdit) -> Unit,
    onRemove: (String) -> Unit,
    circles: List<FriendGroup> = emptyList(),
) {
    var other by remember { mutableStateOf(editing?.person) }
    var role by remember { mutableStateOf(editing?.role ?: LinkType.DATING) }
    var pronouns by remember { mutableStateOf(editing?.person?.pronouns) }
    var startedOn by remember { mutableStateOf(editing?.link?.startedOn) }
    var endedOn by remember { mutableStateOf(editing?.link?.endedOn) }
    var remind by remember { mutableStateOf(editing?.link?.remindAnniversary ?: false) }
    // The person comes from the people picker in single mode (issue #89), opened straight away for a new link.
    var choosing by remember { mutableStateOf(editing == null && others.isNotEmpty()) }
    var picking by remember { mutableStateOf<DateField?>(null) }
    val firstName = friend.displayName.substringBefore(' ')

    val draft = other?.let { LinkEdit(it.id, role, startedOn, endedOn, remind, pronouns, editing?.link?.id) }
    val save = { draft?.let(onSave) ?: Unit }
    // Closing the sheet saves it once a person is picked (#64); an edit that changed nothing just closes.
    val changed = editing == null ||
        editing.role != role ||
        editing.link.startedOn != startedOn ||
        editing.link.endedOn != endedOn ||
        editing.link.remindAnniversary != remind ||
        editing.person.pronouns != pronouns
    ModalBottomSheet(onDismissRequest = { if (draft != null && changed) save() else onDismiss() }) {
        SaveSheetHeader(
            if (editing == null) {
                stringResource(Res.string.link_sheet_title, firstName)
            } else {
                stringResource(Res.string.link_sheet_edit_title, firstName, editing.person.displayName.substringBefore(' '))
            },
            onSave = save,
            saveEnabled = draft != null,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val picked = other
            if (picked == null) {
                if (others.isEmpty()) {
                    Text(stringResource(Res.string.link_no_people), style = MaterialTheme.typography.bodyMedium)
                } else {
                    OutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(Res.string.link_search)) }
                }
                return@Column
            }
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FriendAvatar(picked, size = 32.dp)
                Text(picked.displayName, Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.titleSmall)
                if (editing == null) {
                    TextButton(onClick = {
                        other = null
                        choosing = true
                    }) { Text(stringResource(Res.string.link_change)) }
                }
            }
            val otherFirst = picked.displayName.substringBefore(' ')

            Text(stringResource(Res.string.link_words_for, otherFirst), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pronouns.entries.forEach { p ->
                    val selected = pronouns == p || (p == Pronouns.THEY && pronouns == null)
                    FilterChip(selected, { pronouns = p }, label = { Text(pronounsLabel(p)) })
                }
            }

            Text(stringResource(Res.string.link_is_of, otherFirst, firstName), style = MaterialTheme.typography.labelLarge)
            listOf(LinkCategory.ROMANTIC, LinkCategory.FAMILY, LinkCategory.OTHER).forEach { category ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkType.of(category).forEach { t ->
                        FilterChip(role == t, { role = t }, label = { Text(linkWord(t, pronouns)) })
                    }
                }
            }

            if (role.hasDates) {
                OutlinedButton(onClick = { picking = DateField.START }, modifier = Modifier.fillMaxWidth()) {
                    Text(startedOn?.let { stringResource(Res.string.link_since_value, it.monthYearLabel()) } ?: stringResource(Res.string.link_since))
                }
                if (role == LinkType.EX) {
                    OutlinedButton(onClick = { picking = DateField.END }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            endedOn?.let {
                                stringResource(Res.string.link_ended_value, it.monthYearLabel())
                            } ?: stringResource(Res.string.link_ended),
                        )
                    }
                } else if (startedOn != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.link_remind_anniversary), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(remind, { remind = it })
                    }
                }
            }

            Text(
                stringResource(Res.string.link_preview, otherFirst, firstName, linkWord(role.inverse, friend.pronouns)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (editing != null) TextButton(onClick = { onRemove(editing.link.id) }) { Text(stringResource(Res.string.link_remove)) }
        }
    }
    if (choosing) {
        PersonPickerSheet(others, circles, title = stringResource(Res.string.link_search), onDismiss = { choosing = false }, onPick = {
            other = it
            pronouns = it.pronouns
            choosing = false
        })
    }
    when (picking) {
        DateField.START -> LocalDatePickerDialog(startedOn, onDismiss = { picking = null }, onPicked = { startedOn = it })
        DateField.END -> LocalDatePickerDialog(endedOn, onDismiss = { picking = null }, onPicked = { endedOn = it })
        null -> Unit
    }
}

private enum class DateField { START, END }

/**
 * "Rui is your…" (F6): what a friend is to you, separate from links between people. Family ones put them in the
 * built-in Family circle. The words follow the pronouns chosen here.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RelationToMeSheet(friend: Friend, onDismiss: () -> Unit, onSave: (LinkType?, Pronouns?) -> Unit) {
    val relation = friend.relationToMe
    var pronouns by remember { mutableStateOf(friend.pronouns) }
    val firstName = friend.displayName.substringBefore(' ')
    // Choosing a relation saves it and closes (#64); closing after changing only the words saves those.
    ModalBottomSheet(onDismissRequest = { if (pronouns != friend.pronouns) onSave(relation, pronouns) else onDismiss() }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.relation_to_me_title, firstName), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(Res.string.link_words_for, firstName), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pronouns.entries.forEach { p ->
                    val selected = pronouns == p || (p == Pronouns.THEY && pronouns == null)
                    FilterChip(selected, { pronouns = p }, label = { Text(pronounsLabel(p)) })
                }
            }
            listOf(LinkCategory.FAMILY, LinkCategory.ROMANTIC).forEach { category ->
                Text(linkCategoryLabel(category), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkType.of(category).forEach { t ->
                        FilterChip(relation == t, { onSave(if (relation == t) null else t, pronouns) }, label = { Text(linkWord(t, pronouns)) })
                    }
                }
            }
            Text(
                stringResource(Res.string.relation_to_me_family_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (relation != null) {
                TextButton(onClick = { onSave(null, pronouns) }) { Text(stringResource(Res.string.relation_to_me_clear)) }
            }
        }
    }
}
