package com.neteinstein.goodcompany.feature.review.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.usecase.TimelineFilter
import com.neteinstein.goodcompany.core.domain.usecase.YearInReview
import com.neteinstein.goodcompany.core.ui.components.AnimatedNumber
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.components.avatarColors
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.format.longLabel
import com.neteinstein.goodcompany.core.ui.format.monthLong
import com.neteinstein.goodcompany.core.ui.format.weekdayLong
import com.neteinstein.goodcompany.core.ui.platform.ShareOutcome
import com.neteinstein.goodcompany.core.ui.platform.rememberTextSharer
import com.neteinstein.goodcompany.core.ui.theme.OnColorGradients
import com.neteinstein.goodcompany.feature.review.resources.Res
import com.neteinstein.goodcompany.feature.review.resources.back
import com.neteinstein.goodcompany.feature.review.resources.biggest_body
import com.neteinstein.goodcompany.feature.review.resources.biggest_title
import com.neteinstein.goodcompany.feature.review.resources.contacts_body
import com.neteinstein.goodcompany.feature.review.resources.contacts_calls
import com.neteinstein.goodcompany.feature.review.resources.contacts_messages
import com.neteinstein.goodcompany.feature.review.resources.contacts_title
import com.neteinstein.goodcompany.feature.review.resources.copied
import com.neteinstein.goodcompany.feature.review.resources.empty_body
import com.neteinstein.goodcompany.feature.review.resources.empty_title
import com.neteinstein.goodcompany.feature.review.resources.hero_friends
import com.neteinstein.goodcompany.feature.review.resources.hero_kicker
import com.neteinstein.goodcompany.feature.review.resources.hero_meetups
import com.neteinstein.goodcompany.feature.review.resources.hero_meetups_word
import com.neteinstein.goodcompany.feature.review.resources.new_body
import com.neteinstein.goodcompany.feature.review.resources.new_title
import com.neteinstein.goodcompany.feature.review.resources.people_title
import com.neteinstein.goodcompany.feature.review.resources.places_none
import com.neteinstein.goodcompany.feature.review.resources.places_title
import com.neteinstein.goodcompany.feature.review.resources.reminders_done
import com.neteinstein.goodcompany.feature.review.resources.rhythm_month
import com.neteinstein.goodcompany.feature.review.resources.rhythm_streak
import com.neteinstein.goodcompany.feature.review.resources.rhythm_title
import com.neteinstein.goodcompany.feature.review.resources.rhythm_weekday
import com.neteinstein.goodcompany.feature.review.resources.share
import com.neteinstein.goodcompany.feature.review.resources.share_footer
import com.neteinstein.goodcompany.feature.review.resources.share_header
import com.neteinstein.goodcompany.feature.review.resources.share_meetups
import com.neteinstein.goodcompany.feature.review.resources.share_place
import com.neteinstein.goodcompany.feature.review.resources.share_top
import com.neteinstein.goodcompany.feature.review.resources.times
import com.neteinstein.goodcompany.feature.review.resources.title
import com.neteinstein.goodcompany.feature.review.resources.top_body
import com.neteinstein.goodcompany.feature.review.resources.top_title
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun YearReviewScreen(
    year: Int?,
    onBack: () -> Unit,
    onOpenPeople: () -> Unit = {},
    viewModel: YearReviewViewModel = koinViewModel(key = "review-$year") { parametersOf(year) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharer = rememberTextSharer()
    val shareText = state.review?.takeUnless { it.isEmpty }?.let { shareText(it) }
    YearReviewContent(
        state = state,
        onBack = onBack,
        onShare = shareText?.let { text -> { if (sharer.share(text) == ShareOutcome.COPIED) viewModel.onCopied() } },
        onCopiedShown = viewModel::onCopiedShown,
        onOpenPeople = onOpenPeople,
    )
}

/** Plain-text summary for the share sheet. */
@Composable
fun shareText(r: YearInReview): String = listOfNotNull(
    stringResource(Res.string.share_header, r.year),
    stringResource(
        Res.string.share_meetups,
        pluralStringResource(Res.plurals.hero_meetups, r.meetups, r.meetups),
        pluralStringResource(Res.plurals.hero_friends, r.friendsSeen, r.friendsSeen),
    ),
    r.topFriends.takeIf { it.isNotEmpty() }?.let { top ->
        stringResource(Res.string.share_top, top.take(3).joinToString { it.first.displayName.substringBefore(' ') })
    },
    r.topPlaces.firstOrNull()?.let { stringResource(Res.string.share_place, it.first) },
    stringResource(Res.string.share_footer),
).joinToString("\n")

@Composable
fun YearReviewContent(
    state: YearReviewUiState,
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    onCopiedShown: () -> Unit = {},
    onOpenPeople: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.copied) {
        if (state.copied) {
            snackbar.showMessage(getString(Res.string.copied))
            onCopiedShown()
        }
    }
    Scaffold(snackbarHost = { AppSnackbarHost(snackbar) }) { padding ->
        val r = state.review
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.back)) }
                Text(
                    r?.let { stringResource(Res.string.title, it.year) }.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenPeople) { Icon(Icons.Rounded.Insights, stringResource(Res.string.people_title)) }
                if (onShare != null) {
                    FilledTonalButton(onClick = onShare, modifier = Modifier.padding(end = 8.dp)) {
                        Icon(Icons.Rounded.IosShare, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.share))
                    }
                }
            }
            when {
                r == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                r.isEmpty -> EmptyState(
                    icon = Icons.Rounded.IosShare,
                    title = stringResource(Res.string.empty_title),
                    message = stringResource(Res.string.empty_body),
                )
                else -> ReviewCards(r, state.zone, PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp))
            }
        }
    }
}

@Composable
private fun ReviewCards(r: YearInReview, zone: TimeZone, contentPadding: PaddingValues) {
    LazyColumn(contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "hero") {
            Story(OnColorGradients.Hero, index = 0) {
                Text(
                    stringResource(Res.string.hero_kicker).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(r.year.toString(), style = MaterialTheme.typography.displayLarge, color = Color.White, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedNumber(r.meetups, style = MaterialTheme.typography.displayMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }
                Text(
                    pluralStringResource(Res.plurals.hero_meetups_word, r.meetups),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(pluralStringResource(Res.plurals.hero_friends, r.friendsSeen, r.friendsSeen), color = Color.White.copy(alpha = 0.9f))
            }
        }
        if (r.topFriends.isNotEmpty()) {
            item(key = "top") {
                val (a, b) = avatarColors(r.topFriends.first().first.displayName)
                Story(listOf(a, b), index = 1) {
                    Text(stringResource(Res.string.top_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(stringResource(Res.string.top_body), color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(8.dp))
                    r.topFriends.forEachIndexed { i, (friend, count) ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.width(28.dp))
                            FriendAvatar(friend, size = if (i == 0) 52.dp else 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                friend.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                            )
                            Text(pluralStringResource(Res.plurals.times, count, count), color = Color.White.copy(alpha = 0.9f))
                        }
                    }
                }
            }
        }
        item(key = "places") {
            Story(OnColorGradients.Teal, index = 2) {
                Text(stringResource(Res.string.places_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                if (r.topPlaces.isEmpty()) {
                    Text(stringResource(Res.string.places_none), color = Color.White.copy(alpha = 0.9f))
                }
                r.topPlaces.forEach { (place, count) ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(10.dp))
                        Text(place, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                        Text(pluralStringResource(Res.plurals.times, count, count), color = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        }
        item(key = "rhythm") {
            Story(OnColorGradients.Indigo, index = 3) {
                Text(stringResource(Res.string.rhythm_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                r.busiestMonth?.let { (month, n) -> Fact("📅", stringResource(Res.string.rhythm_month, monthLong(month), n)) }
                r.favouriteWeekday?.let { Fact("💛", stringResource(Res.string.rhythm_weekday, weekdayLong(it))) }
                if (r.longestWeekStreak > 0) {
                    Fact("🔥", pluralStringResource(Res.plurals.rhythm_streak, r.longestWeekStreak, r.longestWeekStreak))
                }
            }
        }
        if (r.contacts > 0) {
            item(key = "contacts") {
                Story(OnColorGradients.Ideas, index = 4) {
                    Text(stringResource(Res.string.contacts_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Text(pluralStringResource(Res.plurals.contacts_body, r.contacts, r.contacts), color = Color.White.copy(alpha = 0.9f))
                    val calls = TimelineFilter.CALL_TYPES.sumOf { r.contactsByType[it] ?: 0 }
                    val messages = TimelineFilter.MESSAGE_TYPES.sumOf { r.contactsByType[it] ?: 0 }
                    if (calls > 0) Fact("📞", pluralStringResource(Res.plurals.contacts_calls, calls, calls))
                    if (messages > 0) Fact("💬", pluralStringResource(Res.plurals.contacts_messages, messages, messages))
                }
            }
        }
        r.biggestHangout?.let { h ->
            item(key = "biggest") {
                Story(OnColorGradients.Sunset, index = 4) {
                    Text(stringResource(Res.string.biggest_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    val n = r.biggestHangoutFriends.size
                    Text(
                        pluralStringResource(Res.plurals.biggest_body, n, n, h.at.toLocalDateTime(zone).date.longLabel()),
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    h.place?.label?.let { Fact("📍", it) }
                    Spacer(Modifier.height(8.dp))
                    StackedAvatars(r.biggestHangoutFriends, size = 40.dp, max = 8)
                }
            }
        }
        if (r.newFaces.isNotEmpty() || r.remindersDone > 0) {
            item(key = "new") {
                Story(OnColorGradients.Amber, index = 5) {
                    if (r.newFaces.isNotEmpty()) {
                        Text(stringResource(Res.string.new_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Text(pluralStringResource(Res.plurals.new_body, r.newFaces.size, r.newFaces.size), color = Color.White.copy(alpha = 0.9f))
                        Spacer(Modifier.height(8.dp))
                        StackedAvatars(r.newFaces, size = 40.dp, max = 8)
                    }
                    if (r.remindersDone > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            pluralStringResource(Res.plurals.reminders_done, r.remindersDone, r.remindersDone),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

/** A full-width gradient "story" card that fades and rises in. */
@Composable
private fun Story(colors: List<Color>, index: Int, content: @Composable ColumnScope.() -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f, tween(500, delayMillis = 90 * index))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 48f
            }
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(colors))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
private fun Fact(emoji: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
            Text(emoji, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}
