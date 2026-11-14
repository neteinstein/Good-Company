package com.neteinstein.goodcompany.feature.friends.ui.screens.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.Closeness
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.ThemeMode
import com.neteinstein.goodcompany.core.domain.usecase.FriendSummary
import com.neteinstein.goodcompany.core.ui.theme.GoodCompanyTheme
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

private val now = Instant.parse("2026-03-10T12:00:00Z")
private val sample = listOf(
    FriendSummary(
        Friend(
            FriendId("ana"),
            "Ana Silva",
            isCloseFriend = true,
            closeness = Closeness.INNER,
            likes = listOf("jazz", "sushi"),
            birthday = Birthday(3, 14, 1990),
        ),
        lastSeen = Instant.parse("2026-02-20T20:00:00Z"),
        lastContact = Instant.parse("2026-03-08T09:00:00Z"),
        nextBirthday = LocalDate(2026, 3, 14),
        meetupOverdue = true,
    ),
    FriendSummary(
        Friend(FriendId("rui"), "Rui Costa", isCloseFriend = true, closeness = Closeness.INNER, likes = listOf("F1")),
        null,
        null,
        null,
        false,
    ),
    FriendSummary(Friend(FriendId("zoe"), "Zoe", contactId = "1"), null, null, null, false),
)

@Composable
private fun FriendsPreview(dark: Boolean) = GoodCompanyTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = false) {
    FriendsContent(
        state = FriendsUiState(
            friends = sample,
            sections = listOf(PeopleSection.Level(Closeness.INNER) to sample.take(2), PeopleSection.Level(null) to sample.drop(2)),
            levels = listOf(LevelCount(Closeness.INNER, 2), LevelCount(null, 1)),
            listedCount = 3,
            yourPeople = YourPeopleSummary(people = 3, meetupsLast90Days = 4, cooling = listOf(sample[1].friend)),
            totalCount = 3,
            isLoading = false,
            now = now,
            today = LocalDate(2026, 3, 10),
        ),
        canSyncContacts = true,
        actions = FriendsActions(),
    )
}

@Preview
@Composable
private fun FriendsLightPreview() = FriendsPreview(dark = false)

@Preview
@Composable
private fun FriendsDarkPreview() = FriendsPreview(dark = true)

@Preview
@Composable
private fun FriendsEmptyPreview() = GoodCompanyTheme(dynamicColor = false) {
    FriendsContent(FriendsUiState(isLoading = false), true, FriendsActions())
}
