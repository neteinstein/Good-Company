package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.usecase.FamilyAround
import com.neteinstein.goodcompany.core.domain.usecase.FamilyMember
import com.neteinstein.goodcompany.core.ui.l10n.linkWord
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.family_ring_kid_age
import com.neteinstein.goodcompany.core.ui.resources.family_ring_more
import com.neteinstein.goodcompany.core.ui.resources.family_ring_more_short
import com.neteinstein.goodcompany.core.ui.resources.family_ring_person
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A person's family as a small network around their photo (issue #90; decision "Person page · family around the
 * photo"): [FamilyAround.parents] above [photo], current partners beside it, and the kids (up to four, then "+N")
 * under [name]. Pure layout, no graph library. With no family it is just [photo] and [name].
 *
 * Tapping someone with a page calls [onOpen] with that member; [onMore] is the "+N" chip.
 */
@Composable
fun FamilyRing(
    family: FamilyAround,
    today: LocalDate,
    onOpen: (FamilyMember) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    photo: @Composable () -> Unit,
    name: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (family.parents.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                family.parents.forEach { RingMember(it, 40.dp, today, onOpen) }
            }
        }
        if (family.partners.isEmpty()) {
            photo()
        } else {
            // Partners beside the photo: the first on the left, any others on the right, the photo kept centred.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
                    RingMember(family.partners.first(), 48.dp, today, onOpen)
                }
                photo()
                Row(
                    Modifier.weight(1f).padding(start = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    family.partners.drop(1).forEach { RingMember(it, 48.dp, today, onOpen) }
                }
            }
        }
        name()
        if (family.kids.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                family.kids.forEach { RingMember(it, 40.dp, today, onOpen) }
                if (family.moreKids > 0) MoreChip(family.moreKids, onMore)
            }
        }
    }
}

@Composable
private fun RingMember(member: FamilyMember, size: Dp, today: LocalDate, onOpen: (FamilyMember) -> Unit) {
    val relation = linkWord(member.role, member.pronouns)
    val description = stringResource(Res.string.family_ring_person, member.name, relation)
    val age = member.birthday?.ageOn(today)?.takeIf { member.role.isKid && it >= 0 }
    val label = age?.let { stringResource(Res.string.family_ring_kid_age, member.firstName, it) } ?: member.firstName
    Column(
        Modifier
            .widthIn(max = 72.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button) { onOpen(member) }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    onOpen(member)
                    true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val person = member.person
        if (person != null) {
            FriendAvatar(person, size = size)
        } else {
            val initials = member.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
            FriendAvatar(member.name, initials, size = size)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun MoreChip(count: Int, onMore: () -> Unit) {
    val description = pluralStringResource(Res.plurals.family_ring_more, count, count)
    Surface(
        onClick = onMore,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.size(40.dp).clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick {
                onMore()
                true
            }
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(stringResource(Res.string.family_ring_more_short, count), style = MaterialTheme.typography.labelLarge)
        }
    }
}

private val LinkType.isKid: Boolean get() = this == LinkType.CHILD || this == LinkType.STEP_CHILD
