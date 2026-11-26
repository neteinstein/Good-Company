package com.neteinstein.goodcompany.feature.agenda.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.CalendarQuestion
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.timeLabel
import com.neteinstein.goodcompany.core.ui.l10n.closenessLabel
import com.neteinstein.goodcompany.feature.agenda.resources.Res
import com.neteinstein.goodcompany.feature.agenda.resources.cal_msg_who
import com.neteinstein.goodcompany.feature.agenda.resources.cal_msg_who_none
import com.neteinstein.goodcompany.feature.agenda.resources.cal_who_event
import com.neteinstein.goodcompany.feature.agenda.resources.cal_who_none
import com.neteinstein.goodcompany.feature.agenda.resources.cal_who_series
import com.neteinstein.goodcompany.feature.agenda.resources.cal_who_title
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_not_now
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * "Who's Ana?" (decision 2026-11-26): a calendar event names a first name several people share. One tap on a person
 * answers; "None of them" answers too; "Not now" hides it for this visit. A repeating event is answered for the series.
 */
@Composable
fun CalendarQuestionCard(
    question: CalendarQuestion,
    today: LocalDate,
    zone: TimeZone,
    onAnswer: (Set<FriendId>) -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val answer: (Set<FriendId>) -> Unit = {
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        onAnswer(it)
    }
    val title = question.event.title
    val start = question.event.start.toLocalDateTime(zone)
    val whenText = listOfNotNull(relativeDay(question.date, today), start.timeLabel().takeUnless { question.event.allDay }).joinToString(" · ")
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
            Text(
                stringResource(Res.string.cal_who_title, question.names.joinToString(", ")),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(Res.string.cal_who_event, title, whenText),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (question.isSeries) {
                Text(
                    stringResource(Res.string.cal_who_series, title),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                question.candidates.forEach { friend -> CandidateRow(friend) { answer(setOf(friend.id)) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { answer(emptySet()) }) { Text(stringResource(Res.string.cal_who_none)) }
                TextButton(onClick = onNotNow) { Text(stringResource(Res.string.last_seen_not_now)) }
            }
        }
    }
}

@Composable
private fun CandidateRow(friend: Friend, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FriendAvatar(friend, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(friend.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The contact's name, when it tells them apart better than the one given in the app.
            val detail = listOfNotNull(friend.name.takeIf { it.isNotBlank() && it != friend.displayName }, closenessLabel(friend.closeness))
            Text(detail.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The snackbar after "Who's Ana?": who [title] is with ([names]; empty = none of them). */
suspend fun calendarAnswerText(title: String, names: List<String>): String =
    if (names.isEmpty()) getString(Res.string.cal_msg_who_none, title) else getString(Res.string.cal_msg_who, title, names.joinToString(", "))
