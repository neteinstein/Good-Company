package com.neteinstein.goodcompany.feature.agenda.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.usecase.LastSeenAnswer
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.l10n.closenessLabel
import com.neteinstein.goodcompany.feature.agenda.resources.Res
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_dont_know
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_few_months
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_not_now
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_over_year
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_progress
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_this_month
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_this_week
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The four dated answers, in the order the card offers them; "I don't know" is a text button. */
private val datedAnswers: List<Pair<LastSeenAnswer, StringResource>> = listOf(
    LastSeenAnswer.THIS_WEEK to Res.string.last_seen_this_week,
    LastSeenAnswer.THIS_MONTH to Res.string.last_seen_this_month,
    LastSeenAnswer.FEW_MONTHS to Res.string.last_seen_few_months,
    LastSeenAnswer.OVER_A_YEAR to Res.string.last_seen_over_year,
)

/**
 * "When did you last see Rui?" (issue #68): one person at a time, one tap answers. [position] of [total] is shown as
 * "1 of 12". "Not now" hides the card until tomorrow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LastSeenCard(
    friend: Friend,
    position: Int,
    total: Int,
    onAnswer: (LastSeenAnswer) -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val answer: (LastSeenAnswer) -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onAnswer(it)
    }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FriendAvatar(friend, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(Res.string.last_seen_title, friend.displayName.substringBefore(' ')),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val progress = stringResource(Res.string.last_seen_progress, position, total)
                    Text(
                        friend.closeness?.let { "${closenessLabel(it)} · $progress" } ?: progress,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            FlowRow(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                datedAnswers.forEach { (value, label) ->
                    SuggestionChip(onClick = { answer(value) }, label = { Text(stringResource(label)) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { answer(LastSeenAnswer.DONT_KNOW) }) { Text(stringResource(Res.string.last_seen_dont_know)) }
                TextButton(onClick = onNotNow) { Text(stringResource(Res.string.last_seen_not_now)) }
            }
        }
    }
}
