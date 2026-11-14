package com.neteinstein.goodcompany.feature.agenda.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.ImportantEvent
import com.neteinstein.goodcompany.core.domain.model.Reminder
import com.neteinstein.goodcompany.core.ui.theme.GoodCompanyTheme
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

@Preview
@Composable
private fun AgendaPreview() {
    val ana = Friend(FriendId("ana"), "Ana Silva", isCloseFriend = true, likes = listOf("jazz"))
    val today = LocalDate(2026, 3, 10)
    GoodCompanyTheme(dynamicColor = false) {
        AgendaContent(
            state = AgendaUiState(
                agenda = listOf(
                    AgendaItem.MeetupOverdue(ana, 37, today),
                    AgendaItem.FriendEvent(ana, ImportantEvent("e", "Knee surgery", LocalDate(2026, 3, 12))),
                    AgendaItem.KidBirthday(ana, Friend(FriendId("leo"), "Leo", likes = listOf("dinosaurs")), LocalDate(2026, 3, 15), 6),
                ),
                reminders = listOf(
                    Reminder("r", "Book concert tickets", listOf(ana.id), Instant.parse("2026-03-11T18:00:00Z"), createdAt = Instant.DISTANT_PAST),
                ),
                friends = listOf(ana),
                isLoading = false,
                today = today,
            ),
            onOpenFriend = {},
            onNewReminder = {},
            onToggleDone = {},
            onDeleteReminder = {},
            onMessageShown = {},
        )
    }
}
