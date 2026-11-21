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
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.domain.usecase.LastSeenAnswer
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.l10n.closenessLabel
import com.neteinstein.goodcompany.feature.agenda.resources.Res
import com.neteinstein.goodcompany.feature.agenda.resources.last_met_title
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
@Composable
fun LastSeenCard(
    friend: Friend,
    position: Int,
    total: Int,
    onAnswer: (LastSeenAnswer) -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LastSeenCardBody(
        leading = { FriendAvatar(friend, size = 44.dp) },
        title = stringResource(Res.string.last_seen_title, friend.displayName.substringBefore(' ')),
        detail = closenessLabel(friend.closeness),
        position = position,
        total = total,
        onAnswer = onAnswer,
        onNotNow = onNotNow,
        modifier = modifier,
    )
}

/**
 * "When did the circle last meet?" (issue #150): the same card and answers for a circle whose reminder is on and that
 * has no meetup logged. The answer only starts the circle's rhythm.
 */
@Composable
fun LastMetCard(
    circle: FriendGroup,
    members: List<Friend>,
    position: Int,
    total: Int,
    onAnswer: (LastSeenAnswer) -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LastSeenCardBody(
        leading = { StackedAvatars(members, size = 28.dp, max = 3) },
        title = stringResource(Res.string.last_met_title),
        detail = "${circle.emoji} ${circle.name}",
        position = position,
        total = total,
        onAnswer = onAnswer,
        onNotNow = onNotNow,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LastSeenCardBody(
    leading: @Composable () -> Unit,
    title: String,
    detail: String,
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
                leading()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val progress = stringResource(Res.string.last_seen_progress, position, total)
                    Text("$detail · $progress", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
