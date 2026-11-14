package com.neteinstein.goodcompany.feature.friends.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.usecase.KinshipProposal
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.l10n.kidOfLabel
import com.neteinstein.goodcompany.core.ui.l10n.linkWord
import com.neteinstein.goodcompany.core.ui.l10n.relationToMeText
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.kinship_ask_title
import com.neteinstein.goodcompany.feature.friends.resources.kinship_because
import com.neteinstein.goodcompany.feature.friends.resources.kinship_not_family
import com.neteinstein.goodcompany.feature.friends.resources.kinship_not_right
import com.neteinstein.goodcompany.feature.friends.resources.kinship_title
import com.neteinstein.goodcompany.feature.friends.resources.kinship_yes
import org.jetbrains.compose.resources.stringResource

/**
 * A kinship proposal (F7): "Leonor Costa is your niece · She’s Rui’s daughter, and Rui is your brother" with
 * "Yes, niece / Not right", or, when it's ambiguous, "How is Duarte related to you?" with a chip per option and
 * "Not family". Never sets anything by itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KinshipCard(
    proposal: KinshipProposal,
    onConfirm: (LinkType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val person = proposal.person
    val via = proposal.via
    val viaFirst = via.displayName.substringBefore(' ')
    val because = proposal.via.relationToMe?.let { viaToMe ->
        stringResource(
            Res.string.kinship_because,
            // "Rui’s daughter": the same words as a kid's line, for any relation.
            kidOfLabel(person, via, proposal.roleToVia).replaceFirstChar { it.uppercase() },
            viaFirst,
            relationToMeText(viaToMe, via.pronouns).replaceFirstChar { it.lowercase() },
        )
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                FriendAvatar(person, size = 48.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val proposed = proposal.proposed
                    Text(
                        if (proposed != null) {
                            stringResource(
                                Res.string.kinship_title,
                                person.displayName,
                                relationToMeText(proposed, person.pronouns).replaceFirstChar { it.lowercase() },
                            )
                        } else {
                            stringResource(Res.string.kinship_ask_title, person.displayName.substringBefore(' '))
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    because?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val proposed = proposal.proposed
            if (proposed != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onConfirm(proposed) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.kinship_yes, linkWord(proposed, person.pronouns).lowercase()))
                    }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.kinship_not_right))
                    }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    proposal.options.forEach { type ->
                        SuggestionChip(onClick = { onConfirm(type) }, label = { Text(linkWord(type, person.pronouns)) })
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.kinship_not_family)) }
            }
        }
    }
}
