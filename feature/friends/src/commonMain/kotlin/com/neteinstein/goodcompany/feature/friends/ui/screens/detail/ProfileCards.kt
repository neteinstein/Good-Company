package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.ContactChannel
import com.neteinstein.goodcompany.core.domain.model.CustomField
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Introducer
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.LinkedPerson
import com.neteinstein.goodcompany.core.domain.model.MetInfo
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.Relative
import com.neteinstein.goodcompany.core.domain.usecase.KidDraft
import com.neteinstein.goodcompany.core.domain.usecase.PersonLinks
import com.neteinstein.goodcompany.core.ui.components.BirthdayDraft
import com.neteinstein.goodcompany.core.ui.components.BirthdayFields
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.core.ui.components.LocalDatePickerDialog
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.format.longLabel
import com.neteinstein.goodcompany.core.ui.format.monthYearLabel
import com.neteinstein.goodcompany.core.ui.format.parseTags
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.l10n.relationLabel
import com.neteinstein.goodcompany.core.ui.resources.add
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.edit
import com.neteinstein.goodcompany.core.ui.resources.relation_kid
import com.neteinstein.goodcompany.core.ui.resources.remove_item
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.about_email_add
import com.neteinstein.goodcompany.feature.friends.resources.about_email_contact_note
import com.neteinstein.goodcompany.feature.friends.resources.about_email_from_contacts
import com.neteinstein.goodcompany.feature.friends.resources.about_email_typed
import com.neteinstein.goodcompany.feature.friends.resources.about_emails
import com.neteinstein.goodcompany.feature.friends.resources.about_hint
import com.neteinstein.goodcompany.feature.friends.resources.about_introduced_by
import com.neteinstein.goodcompany.feature.friends.resources.about_introduced_by_line
import com.neteinstein.goodcompany.feature.friends.resources.about_introduced_pick
import com.neteinstein.goodcompany.feature.friends.resources.about_job
import com.neteinstein.goodcompany.feature.friends.resources.about_lives_in
import com.neteinstein.goodcompany.feature.friends.resources.about_met_clear
import com.neteinstein.goodcompany.feature.friends.resources.about_met_day_unknown
import com.neteinstein.goodcompany.feature.friends.resources.about_met_label
import com.neteinstein.goodcompany.feature.friends.resources.about_met_on
import com.neteinstein.goodcompany.feature.friends.resources.about_met_place
import com.neteinstein.goodcompany.feature.friends.resources.about_met_when
import com.neteinstein.goodcompany.feature.friends.resources.about_preferred
import com.neteinstein.goodcompany.feature.friends.resources.about_preferred_badge
import com.neteinstein.goodcompany.feature.friends.resources.about_preferred_hint
import com.neteinstein.goodcompany.feature.friends.resources.about_preferred_none
import com.neteinstein.goodcompany.feature.friends.resources.about_title
import com.neteinstein.goodcompany.feature.friends.resources.add_as_person
import com.neteinstein.goodcompany.feature.friends.resources.channel_call
import com.neteinstein.goodcompany.feature.friends.resources.channel_email
import com.neteinstein.goodcompany.feature.friends.resources.channel_sms
import com.neteinstein.goodcompany.feature.friends.resources.channel_whatsapp
import com.neteinstein.goodcompany.feature.friends.resources.family_add_title
import com.neteinstein.goodcompany.feature.friends.resources.family_birthday_line
import com.neteinstein.goodcompany.feature.friends.resources.family_birthday_optional
import com.neteinstein.goodcompany.feature.friends.resources.family_birthday_set
import com.neteinstein.goodcompany.feature.friends.resources.family_edit_title
import com.neteinstein.goodcompany.feature.friends.resources.family_hint
import com.neteinstein.goodcompany.feature.friends.resources.family_likes
import com.neteinstein.goodcompany.feature.friends.resources.family_likes_line
import com.neteinstein.goodcompany.feature.friends.resources.family_name
import com.neteinstein.goodcompany.feature.friends.resources.family_note
import com.neteinstein.goodcompany.feature.friends.resources.family_notify
import com.neteinstein.goodcompany.feature.friends.resources.family_remove
import com.neteinstein.goodcompany.feature.friends.resources.family_title
import com.neteinstein.goodcompany.feature.friends.resources.kid_add_title
import com.neteinstein.goodcompany.feature.friends.resources.kid_show_hint_family
import com.neteinstein.goodcompany.feature.friends.resources.kid_show_hint_friend
import com.neteinstein.goodcompany.feature.friends.resources.link_add
import com.neteinstein.goodcompany.feature.friends.resources.more_add
import com.neteinstein.goodcompany.feature.friends.resources.more_edit
import com.neteinstein.goodcompany.feature.friends.resources.more_hint
import com.neteinstein.goodcompany.feature.friends.resources.more_label
import com.neteinstein.goodcompany.feature.friends.resources.more_new
import com.neteinstein.goodcompany.feature.friends.resources.more_title
import com.neteinstein.goodcompany.feature.friends.resources.more_value
import com.neteinstein.goodcompany.feature.friends.resources.show_in_my_list
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.stringResource
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** "Sep 2011", or a full date when the day is known. */
@Composable
internal fun MetInfo.dateLabel(): String? = on?.let { if (dayKnown) it.longLabel() else it.monthYearLabel() }

@Composable
internal fun channelLabel(channel: ContactChannel): String = stringResource(
    when (channel) {
        ContactChannel.WHATSAPP -> Res.string.channel_whatsapp
        ContactChannel.CALL -> Res.string.channel_call
        ContactChannel.SMS -> Res.string.channel_sms
        ContactChannel.EMAIL -> Res.string.channel_email
    },
)

internal fun channelIcon(channel: ContactChannel): ImageVector = when (channel) {
    ContactChannel.WHATSAPP -> Icons.AutoMirrored.Rounded.Chat
    ContactChannel.CALL -> Icons.Rounded.Call
    ContactChannel.SMS -> Icons.Rounded.Sms
    ContactChannel.EMAIL -> Icons.Rounded.Email
}

// --- About ------------------------------------------------------------------------------------------------------------

/** The "About <name>" card: one line per detail, each hidden when empty. */
@Composable
internal fun AboutCard(f: Friend, others: List<Friend>, onEdit: () -> Unit, onOpenFriend: (FriendId) -> Unit) {
    val firstName = f.displayName.substringBefore(' ')
    SectionCard(
        stringResource(Res.string.about_title, firstName),
        icon = Icons.Rounded.People,
        action = { TextButton(onClick = onEdit) { Text(stringResource(CoreRes.string.edit)) } },
    ) {
        val met = f.met?.takeUnless { it.isEmpty }
        val introducer = f.introducedBy
        val empty = f.job.isBlank() && f.livesIn.isBlank() && met == null && introducer == null && f.preferredChannel == null &&
            f.emails.isEmpty()
        if (empty) Hint(stringResource(Res.string.about_hint))
        if (f.job.isNotBlank()) AboutLine(Icons.Rounded.Work, f.job, stringResource(Res.string.about_job))
        if (f.livesIn.isNotBlank()) AboutLine(Icons.Rounded.Place, f.livesIn, stringResource(Res.string.about_lives_in))
        if (met != null || introducer != null) {
            val headline = listOfNotNull(met?.place?.takeIf { it.isNotBlank() }, met?.dateLabel()).joinToString(" · ")
                .ifEmpty { stringResource(Res.string.about_met_label) }
            val linked = introducer?.friendId?.let { id -> others.firstOrNull { it.id == id } }
            ListItem(
                leadingContent = { Icon(Icons.Rounded.Handshake, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                headlineContent = { Text(headline, style = MaterialTheme.typography.titleSmall) },
                supportingContent = {
                    if (introducer != null) {
                        val text = stringResource(Res.string.about_introduced_by_line, linked?.displayName ?: introducer.name)
                        if (linked != null) {
                            Text(
                                text,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onOpenFriend(linked.id) },
                            )
                        } else {
                            Text(text)
                        }
                    } else {
                        Text(stringResource(Res.string.about_met_label))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        f.preferredChannel?.let { channel ->
            ListItem(
                leadingContent = { Icon(channelIcon(channel), null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(channelLabel(channel), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(8.dp))
                        InfoPill(stringResource(Res.string.about_preferred_badge), emphasized = true)
                    }
                },
                supportingContent = { Text(stringResource(Res.string.about_preferred_hint)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        f.emails.forEach { email ->
            AboutLine(
                Icons.Rounded.Email,
                email.address,
                stringResource(if (email.fromContact) Res.string.about_email_from_contacts else Res.string.about_email_typed),
            )
        }
    }
}

@Composable
private fun AboutLine(icon: ImageVector, headline: String, label: String) {
    ListItem(
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(headline, style = MaterialTheme.typography.titleSmall) },
        supportingContent = { Text(label) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Edits everything on the "About" card at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AboutDialog(f: Friend, others: List<Friend>, onDismiss: () -> Unit, onSave: (AboutEdit) -> Unit) {
    var job by remember { mutableStateOf(f.job) }
    var livesIn by remember { mutableStateOf(f.livesIn) }
    var metPlace by remember { mutableStateOf(f.met?.place.orEmpty()) }
    var metOn by remember { mutableStateOf(f.met?.on) }
    var dayKnown by remember { mutableStateOf(f.met?.dayKnown ?: true) }
    var introducerName by remember { mutableStateOf(f.introducedBy?.name.orEmpty()) }
    var introducerId by remember { mutableStateOf(f.introducedBy?.friendId) }
    var preferred by remember { mutableStateOf(f.preferredChannel) }
    var emails by remember { mutableStateOf(f.emails.filterNot { it.fromContact }.map { it.address }) }
    var newEmail by remember { mutableStateOf("") }
    var pickDate by remember { mutableStateOf(false) }

    fun commitEmail() {
        val e = newEmail.trim()
        if (e.isNotEmpty() && emails.none { it.equals(e, ignoreCase = true) }) emails = emails + e
        newEmail = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.People, null) },
        title = { Text(stringResource(Res.string.about_title, f.displayName.substringBefore(' '))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(job, {
                    job = it
                }, label = { Text(stringResource(Res.string.about_job)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    livesIn,
                    { livesIn = it },
                    label = { Text(stringResource(Res.string.about_lives_in)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    metPlace,
                    { metPlace = it },
                    label = { Text(stringResource(Res.string.about_met_place)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth()) {
                    val date = metOn?.let { if (dayKnown) it.longLabel() else it.monthYearLabel() }
                    Text(date?.let { stringResource(Res.string.about_met_on, it) } ?: stringResource(Res.string.about_met_when))
                }
                if (metOn != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = !dayKnown, onCheckedChange = { dayKnown = !it })
                        Text(stringResource(Res.string.about_met_day_unknown), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { metOn = null }) { Text(stringResource(Res.string.about_met_clear)) }
                    }
                }
                OutlinedTextField(
                    introducerName,
                    {
                        introducerName = it
                        // Typing changes who it is: only a friend picked from the list stays linked.
                        introducerId = null
                    },
                    label = { Text(stringResource(Res.string.about_introduced_by)) },
                    supportingText = { Text(stringResource(Res.string.about_introduced_pick)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val query = introducerName.trim()
                if (query.isNotEmpty() && introducerId == null) {
                    val matches = others.filter { it.displayName.contains(query, ignoreCase = true) && !it.isOrphaned }.take(4)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        matches.forEach { other ->
                            SuggestionChip(
                                onClick = {
                                    introducerName = other.displayName
                                    introducerId = other.id
                                },
                                label = { Text(other.displayName) },
                                icon = { FriendAvatar(other, size = 18.dp) },
                            )
                        }
                    }
                }
                Text(stringResource(Res.string.about_preferred), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(preferred == null, { preferred = null }, label = { Text(stringResource(Res.string.about_preferred_none)) })
                    ContactChannel.entries.forEach { c ->
                        FilterChip(preferred == c, { preferred = c }, label = { Text(channelLabel(c)) })
                    }
                }
                Text(stringResource(Res.string.about_emails), style = MaterialTheme.typography.labelLarge)
                f.emails.filter { it.fromContact }.forEach { e ->
                    Column {
                        Text(e.address, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(Res.string.about_email_contact_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    emails.forEach { e ->
                        InputChip(
                            selected = false,
                            onClick = { emails = emails - e },
                            label = { Text(e) },
                            trailingIcon = { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.remove_item, e), Modifier.size(16.dp)) },
                        )
                    }
                }
                OutlinedTextField(
                    newEmail,
                    { newEmail = it },
                    label = { Text(stringResource(Res.string.about_email_add)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    trailingIcon = {
                        TextButton(onClick = ::commitEmail, enabled = newEmail.isNotBlank()) { Text(stringResource(CoreRes.string.add)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                commitEmail()
                val met = MetInfo(metPlace, metOn?.let { if (dayKnown) it else LocalDate(it.year, it.month, 1) }, dayKnown || metOn == null)
                onSave(
                    AboutEdit(
                        job = job,
                        livesIn = livesIn,
                        met = met.takeUnless { it.isEmpty },
                        introducedBy = introducerName.trim().takeIf { it.isNotEmpty() }?.let { Introducer(introducerId, it) },
                        preferredChannel = preferred,
                        emails = emails,
                    ),
                )
            }) { Text(stringResource(CoreRes.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
    if (pickDate) LocalDatePickerDialog(metOn, onDismiss = { pickDate = false }, onPicked = { metOn = it })
}

// --- Family -----------------------------------------------------------------------------------------------------------

/** Partners, kids (people of their own, F9), other family links and the relatives named inside this friend. */
@Composable
internal fun FamilyCard(
    f: Friend,
    today: LocalDate,
    onAdd: () -> Unit,
    onEditRelative: (Relative) -> Unit,
    onRemoveRelative: (String) -> Unit,
    links: List<LinkedPerson> = emptyList(),
    onOpenPerson: (FriendId) -> Unit = {},
    onEditLink: (LinkedPerson) -> Unit = {},
    onAddLink: () -> Unit = {},
    onAddAsPerson: (String) -> Unit = {},
) {
    // Partners first (F18), then exes, then kids and family; friends and colleagues have their own card.
    val romantic = links.filter { it.role.isRomantic }
    val kids = links.filter { it.role == LinkType.CHILD }
    val family = links.filter { it.role.isFamily && it.role != LinkType.CHILD }
    SectionCard(
        stringResource(Res.string.family_title),
        icon = Icons.Rounded.People,
        action = { TextButton(onClick = onAdd) { Text(stringResource(CoreRes.string.add)) } },
    ) {
        if (f.relatives.isEmpty() && romantic.isEmpty() && kids.isEmpty() && family.isEmpty()) Hint(stringResource(Res.string.family_hint))
        romantic.forEach { LinkRow(it, onOpenPerson, onEditLink) }
        f.relatives.filter { it.relation == Relation.PARTNER }.forEach { RelativeRow(it, today, onEditRelative, onRemoveRelative, onAddAsPerson) }
        kids.forEach { LinkRow(it, onOpenPerson, onEditLink) }
        family.forEach { LinkRow(it, onOpenPerson, onEditLink) }
        f.relatives.filter { it.relation != Relation.PARTNER }.forEach { RelativeRow(it, today, onEditRelative, onRemoveRelative, onAddAsPerson) }
        TextButton(onClick = onAddLink, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.link_add))
        }
    }
}

@Composable
private fun RelativeRow(
    r: Relative,
    today: LocalDate,
    onEdit: (Relative) -> Unit,
    onRemove: (String) -> Unit,
    onAddAsPerson: (String) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable { onEdit(r) },
        leadingContent = {
            val initials = r.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
            FriendAvatar(r.name, if (r.relation == Relation.PET) "🐾" else initials, size = 44.dp)
        },
        headlineContent = { Text(r.name, style = MaterialTheme.typography.titleSmall) },
        supportingContent = {
            val parts = listOfNotNull(
                relationLabel(r.relation),
                r.note.takeIf { it.isNotBlank() },
                r.birthday?.let { stringResource(Res.string.family_birthday_line, relativeDay(it.nextOccurrence(today), today)) },
                r.likes.takeIf { it.isNotEmpty() }?.let { stringResource(Res.string.family_likes_line, it.joinToString()) },
            )
            Text(parts.joinToString(" · "))
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A relative who is only a name can become a person with their own page (question 1).
                if (PersonLinks.typeFor(r.relation) != null) {
                    TextButton(onClick = { onAddAsPerson(r.id) }) { Text(stringResource(Res.string.add_as_person)) }
                }
                IconButton(onClick = { onRemove(r.id) }) { Icon(Icons.Rounded.Close, stringResource(Res.string.family_remove, r.name)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** What the family sheet edits: something new (a kid or a relative) or a relative. A kid is edited on their own page. */
internal sealed interface FamilyEditing {
    data object New : FamilyEditing

    data class EditRelative(val relative: Relative) : FamilyEditing
}

/**
 * "Add to <name>'s family": one sheet for every relation. Choosing Kid adds a person of their own (F9), linked to this
 * friend, who shows in your list when [parentIsFamily]; the others are [Relative]s named inside this friend.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FamilySheet(
    firstName: String,
    editing: FamilyEditing,
    latestYear: Int,
    onDismiss: () -> Unit,
    onAddKid: (KidDraft) -> Unit,
    onSaveRelative: (RelativeEdit) -> Unit,
    parentIsFamily: Boolean = false,
) {
    val relative = (editing as? FamilyEditing.EditRelative)?.relative
    // null relation = Kid.
    var relation by remember { mutableStateOf<Relation?>(relative?.relation ?: Relation.PARTNER) }
    var name by remember { mutableStateOf(relative?.name.orEmpty()) }
    var likes by remember { mutableStateOf(relative?.likes.orEmpty().joinToString()) }
    var note by remember { mutableStateOf(relative?.note.orEmpty()) }
    var notify by remember { mutableStateOf(relative?.notifyBirthday ?: true) }
    var showInList by remember { mutableStateOf(parentIsFamily) }
    var birthday by remember { mutableStateOf(BirthdayDraft.of(relative?.birthday)) }
    val isNew = editing == FamilyEditing.New

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when {
                    !isNew -> stringResource(Res.string.family_edit_title, name)
                    relation == null -> stringResource(Res.string.kid_add_title, firstName)
                    else -> stringResource(Res.string.family_add_title, firstName)
                },
                style = MaterialTheme.typography.titleLarge,
            )
            // A kid becomes a person with their own page; a relative stays a name inside this friend.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Relation.PARTNER, null, Relation.PARENT, Relation.SIBLING, Relation.PET, Relation.OTHER)
                    .filter { isNew || it != null }
                    .forEach { r ->
                        FilterChip(
                            selected = relation == r,
                            onClick = { relation = r },
                            label = { Text(if (r == null) stringResource(CoreRes.string.relation_kid) else relationLabel(r)) },
                        )
                    }
            }
            OutlinedTextField(name, {
                name = it
            }, label = { Text(stringResource(Res.string.family_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(stringResource(Res.string.family_birthday_optional), style = MaterialTheme.typography.labelLarge)
            BirthdayFields(birthday, { birthday = it }, latestYear)
            OutlinedTextField(likes, { likes = it }, label = { Text(stringResource(Res.string.family_likes)) }, modifier = Modifier.fillMaxWidth())
            val current = relation
            if (current != null) {
                OutlinedTextField(note, {
                    note = it
                }, label = { Text(stringResource(Res.string.family_note)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                // F9: on for family's kids, off for friends' kids (who still show on this page and in birthdays).
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.show_in_my_list), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (parentIsFamily) Res.string.kid_show_hint_family else Res.string.kid_show_hint_friend, firstName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = showInList, onCheckedChange = { showInList = it })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.family_notify), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = notify, onCheckedChange = { notify = it })
            }
            Button(
                onClick = {
                    val date = birthday.toBirthday(latestYear)
                    if (current == null) {
                        onAddKid(
                            KidDraft(
                                name = name,
                                birthday = date,
                                likes = parseTags(likes),
                                showInMyList = showInList,
                                notifyBirthday = notify,
                            ),
                        )
                    } else {
                        onSaveRelative(RelativeEdit(relative?.id, current, name, date, parseTags(likes), note, notify))
                    }
                },
                enabled = name.isNotBlank() && birthday.isValid(latestYear),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(CoreRes.string.save)) }
        }
    }
}

// --- More about (custom fields) ---------------------------------------------------------------------------------------

@Composable
internal fun MoreAboutCard(f: Friend, onAdd: () -> Unit, onEdit: (CustomField) -> Unit, onRemove: (String) -> Unit) {
    SectionCard(
        stringResource(Res.string.more_title, f.displayName.substringBefore(' ')),
        icon = Icons.AutoMirrored.Rounded.List,
        action = { TextButton(onClick = onAdd) { Text(stringResource(Res.string.more_add)) } },
    ) {
        if (f.customFields.isEmpty()) Hint(stringResource(Res.string.more_hint))
        f.customFields.forEach { field ->
            ListItem(
                modifier = Modifier.clickable { onEdit(field) },
                headlineContent = { Text(field.value, style = MaterialTheme.typography.titleSmall) },
                supportingContent = { Text(field.label) },
                trailingContent = {
                    IconButton(onClick = { onRemove(field.id) }) {
                        Icon(Icons.Rounded.Close, stringResource(CoreRes.string.remove_item, field.label))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

/** Adds or edits one "More about" line; the label offers ones already used on other friends. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomFieldDialog(
    field: CustomField?,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (label: String, value: String) -> Unit,
) {
    var label by remember { mutableStateOf(field?.label.orEmpty()) }
    var value by remember { mutableStateOf(field?.value.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.List, null) },
        title = { Text(stringResource(if (field == null) Res.string.more_new else Res.string.more_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(label, {
                    label = it
                }, label = { Text(stringResource(Res.string.more_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val matching = suggestions.filter {
                    it.contains(label.trim(), ignoreCase = true) && !it.equals(label.trim(), ignoreCase = true)
                }.take(6)
                if (matching.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        matching.forEach { s -> SuggestionChip(onClick = { label = s }, label = { Text(s) }) }
                    }
                }
                OutlinedTextField(value, { value = it }, label = { Text(stringResource(Res.string.more_value)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(label, value) }, enabled = label.isNotBlank() && value.isNotBlank()) {
                Text(stringResource(CoreRes.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
