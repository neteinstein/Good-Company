package org.goodcompany.app.feature.agenda.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.LocalDate
import org.goodcompany.app.core.domain.model.AgendaItem
import org.goodcompany.app.core.domain.model.Closeness
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.FriendId
import org.goodcompany.app.core.domain.model.ImportantEvent
import org.goodcompany.app.core.domain.model.Reminder
import org.goodcompany.app.core.ui.components.ReminderCallbacks
import org.goodcompany.app.core.ui.theme.GoodCompanyTheme
import kotlin.time.Instant

@Preview
@Composable
private fun AgendaPreview() {
    val ana = Friend(FriendId("ana"), "Ana Silva", closeness = Closeness.CLOSE, likes = listOf("jazz"))
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
            reminders = ReminderCallbacks(onDone = {}, onSnooze = { _, _ -> }, onEdit = {}, onDelete = {}),
            onMessageShown = {},
        )
    }
}
