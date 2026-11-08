package com.neteinstein.goodcompany.feature.friends.ui.screens.story

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.FreshItem
import com.neteinstein.goodcompany.core.domain.usecase.StorySlide
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.avatarColors
import com.neteinstein.goodcompany.core.ui.format.longLabel
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.relativeDayPhrase
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.detail_missing
import com.neteinstein.goodcompany.feature.friends.resources.kids_loves
import com.neteinstein.goodcompany.feature.friends.resources.story_added
import com.neteinstein.goodcompany.feature.friends.resources.story_bigday_past
import com.neteinstein.goodcompany.feature.friends.resources.story_bigday_soon
import com.neteinstein.goodcompany.feature.friends.resources.story_bigday_today
import com.neteinstein.goodcompany.feature.friends.resources.story_birthday_past
import com.neteinstein.goodcompany.feature.friends.resources.story_birthday_soon
import com.neteinstein.goodcompany.feature.friends.resources.story_birthday_turned
import com.neteinstein.goodcompany.feature.friends.resources.story_birthday_turns
import com.neteinstein.goodcompany.feature.friends.resources.story_done
import com.neteinstein.goodcompany.feature.friends.resources.story_fresh_ask
import com.neteinstein.goodcompany.feature.friends.resources.story_fresh_count
import com.neteinstein.goodcompany.feature.friends.resources.story_fresh_event
import com.neteinstein.goodcompany.feature.friends.resources.story_fresh_kid
import com.neteinstein.goodcompany.feature.friends.resources.story_fresh_like
import com.neteinstein.goodcompany.feature.friends.resources.story_fresh_update
import com.neteinstein.goodcompany.feature.friends.resources.story_ideas_title
import com.neteinstein.goodcompany.feature.friends.resources.story_kicker
import com.neteinstein.goodcompany.feature.friends.resources.story_kid_age
import com.neteinstein.goodcompany.feature.friends.resources.story_kid_birthday
import com.neteinstein.goodcompany.feature.friends.resources.story_kids_title
import com.neteinstein.goodcompany.feature.friends.resources.story_last_title
import com.neteinstein.goodcompany.feature.friends.resources.story_last_with
import com.neteinstein.goodcompany.feature.friends.resources.story_never_met
import com.neteinstein.goodcompany.feature.friends.resources.story_new_badge
import com.neteinstein.goodcompany.feature.friends.resources.story_notes_title
import com.neteinstein.goodcompany.feature.friends.resources.story_nothing_new
import com.neteinstein.goodcompany.feature.friends.resources.story_open_profile
import com.neteinstein.goodcompany.feature.friends.resources.story_outro_ask
import com.neteinstein.goodcompany.feature.friends.resources.story_outro_empty
import com.neteinstein.goodcompany.feature.friends.resources.story_outro_title
import com.neteinstein.goodcompany.feature.friends.resources.story_plan
import com.neteinstein.goodcompany.feature.friends.resources.story_seen
import com.neteinstein.goodcompany.feature.friends.resources.story_seen_at
import com.neteinstein.goodcompany.feature.friends.resources.story_tastes_avoid
import com.neteinstein.goodcompany.feature.friends.resources.story_tastes_title
import com.neteinstein.goodcompany.feature.friends.resources.story_todo_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes
import com.neteinstein.goodcompany.core.ui.resources.close as closeLabel

/** How long a slide stays up before the story moves on by itself. */
private const val SLIDE_MILLIS = 6_500

@Composable
fun CatchUpStoryScreen(
    friendId: FriendId,
    onClose: () -> Unit,
    onOpenFriend: (() -> Unit)?,
    viewModel: CatchUpStoryViewModel = koinViewModel(key = "story-${friendId.value}") { parametersOf(friendId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onClose() }
    CatchUpStoryContent(
        state = state,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onPause = viewModel::onPause,
        onClose = onClose,
        onOpenFriend = onOpenFriend,
    )
}

/** Full-screen, Instagram-style: progress segments on top, tap right/left to move, hold to pause. */
@Composable
fun CatchUpStoryContent(
    state: CatchUpStoryUiState,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPause: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOpenFriend: (() -> Unit)?,
    autoAdvance: Boolean = true,
) {
    val story = state.story
    if (story == null) {
        Box(Modifier.fillMaxSize().background(Color(0xFF14121A)), contentAlignment = Alignment.Center) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                )
            } else {
                Text(stringResource(Res.string.detail_missing), color = Color.White)
            }
        }
        return
    }
    val friend = story.friend
    val slide = story.slides[state.index]
    val colors = slideColors(slide, friend)
    val haptics = LocalHapticFeedback.current
    val next by rememberUpdatedState(onNext)
    val pause by rememberUpdatedState(onPause)
    val progress = remember(state.index) { Animatable(0f) }
    val holds = slide is StorySlide.Outro || !autoAdvance
    LaunchedEffect(state.index, state.paused, holds) {
        if (holds || state.paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * SLIDE_MILLIS).toInt()
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        next()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors))
            .pointerInput(state.index) {
                detectTapGestures(
                    onPress = {
                        pause(true)
                        tryAwaitRelease()
                        pause(false)
                    },
                    onTap = { offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (offset.x < size.width / 3f) onPrevious() else onNext()
                    },
                )
            },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Segments(state.slideCount, state.index, if (holds) 1f else progress.value)
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FriendAvatar(friend.name, friend.initials, size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    friend.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(CoreRes.string.closeLabel), tint = Color.White) }
            }
            AnimatedContent(
                targetState = state.index,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { i ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                ) {
                    SlideBody(story.slides[i], friend, state)
                }
            }
            if (slide is StorySlide.Outro) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (onOpenFriend != null) {
                        OutlinedButton(onClick = onOpenFriend, modifier = Modifier.weight(1f)) {
                            Text(stringResource(Res.string.story_open_profile), color = Color.White)
                        }
                    }
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = colors.first()),
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(Res.string.story_done)) }
                }
            }
        }
    }
}

@Composable
private fun Segments(count: Int, index: Int, progress: Float) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            val fill = when {
                i < index -> 1f
                i == index -> progress
                else -> 0f
            }
            Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
                Box(Modifier.fillMaxWidth(fill).height(3.dp).background(Color.White))
            }
        }
    }
}

private fun slideColors(slide: StorySlide, friend: Friend): List<Color> = when (slide) {
    is StorySlide.Intro, is StorySlide.Outro -> avatarColors(friend.name).let { (a, b) -> listOf(a, b) }
    is StorySlide.Fresh -> listOf(Color(0xFFE5574F), Color(0xFFF2A100))
    is StorySlide.BigDay -> listOf(Color(0xFF6A3DE8), Color(0xFFD81B60))
    is StorySlide.Birthday -> listOf(Color(0xFFFF7043), Color(0xFFEC407A))
    is StorySlide.Kids -> listOf(Color(0xFF00897B), Color(0xFF43A047))
    is StorySlide.LastTime -> listOf(Color(0xFF3949AB), Color(0xFF1E88E5))
    is StorySlide.Tastes -> listOf(Color(0xFFD81B60), Color(0xFF8E24AA))
    is StorySlide.Notes -> listOf(Color(0xFF455A64), Color(0xFF26A69A))
    is StorySlide.ToDo -> listOf(Color(0xFFF57C00), Color(0xFFE53935))
    is StorySlide.Ideas -> listOf(Color(0xFF5E35B1), Color(0xFF00ACC1))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SlideBody(slide: StorySlide, friend: Friend, state: CatchUpStoryUiState) {
    val firstName = friend.name.substringBefore(' ')
    when (slide) {
        is StorySlide.Intro -> {
            Box(Modifier.align(Alignment.CenterHorizontally)) { FriendAvatar(friend.name, friend.initials, size = 128.dp, highlighted = true) }
            Spacer(Modifier.height(8.dp))
            Kicker(stringResource(Res.string.story_kicker))
            Headline(friend.name)
            Body(
                slide.lastSeen?.let { seen ->
                    val base = stringResource(Res.string.story_seen, relativePast(seen, state.now, state.zone))
                    slide.lastPlace?.let { "$base " + stringResource(Res.string.story_seen_at, it) } ?: base
                } ?: stringResource(Res.string.story_never_met),
            )
            slide.nextPlan?.let { Fact("📅", stringResource(Res.string.story_plan, it.title, relativeDay(it.date, state.today))) }
            Fact(
                "✨",
                if (slide.freshCount > 0) {
                    pluralStringResource(Res.plurals.story_fresh_count, slide.freshCount, slide.freshCount)
                } else {
                    stringResource(Res.string.story_nothing_new)
                },
            )
        }
        is StorySlide.Fresh -> {
            Badge(stringResource(Res.string.story_new_badge))
            val item = slide.item
            Kicker(
                stringResource(
                    when (item) {
                        is FreshItem.Update -> Res.string.story_fresh_update
                        is FreshItem.NewKid -> Res.string.story_fresh_kid
                        is FreshItem.NewEvent -> Res.string.story_fresh_event
                        is FreshItem.NewLike -> Res.string.story_fresh_like
                    },
                ),
            )
            Headline(
                when (item) {
                    is FreshItem.Update -> item.text
                    is FreshItem.NewKid -> item.name
                    is FreshItem.NewEvent -> item.title
                    is FreshItem.NewLike -> item.like
                },
            )
            if (item is FreshItem.NewEvent) Body(relativeDay(item.date, state.today))
            Body(stringResource(Res.string.story_added, relativePast(item.addedAt, state.now, state.zone)))
            if (item is FreshItem.Update) Fact("💬", stringResource(Res.string.story_fresh_ask))
        }
        is StorySlide.BigDay -> {
            Emoji("🗓️")
            Kicker(relativeDay(slide.date, state.today))
            Headline(slide.title)
            Body(
                stringResource(
                    when {
                        slide.date == state.today -> Res.string.story_bigday_today
                        slide.isPast -> Res.string.story_bigday_past
                        else -> Res.string.story_bigday_soon
                    },
                ),
            )
        }
        is StorySlide.Birthday -> {
            Emoji("🎂")
            Headline(
                stringResource(
                    if (slide.isPast) Res.string.story_birthday_past else Res.string.story_birthday_soon,
                    relativeDayPhrase(slide.date, state.today),
                ),
            )
            slide.turning?.let {
                Body(stringResource(if (slide.isPast) Res.string.story_birthday_turned else Res.string.story_birthday_turns, it))
            }
        }
        is StorySlide.Kids -> {
            Emoji("🧸")
            Headline(stringResource(Res.string.story_kids_title))
            slide.kids.forEach { kid ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FriendAvatar(kid.name, kid.name.take(1).uppercase(), size = 48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        val age = kid.age?.let { pluralStringResource(Res.plurals.story_kid_age, it, it) }
                        Text(listOfNotNull(kid.name, age).joinToString(" · "), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        kid.nextBirthday?.takeIf { kid.birthdaySoon }?.let { next ->
                            Small(stringResource(Res.string.story_kid_birthday, relativeDayPhrase(next, state.today)))
                        }
                        if (kid.likes.isNotEmpty()) Small(stringResource(Res.string.kids_loves, kid.likes.joinToString()))
                    }
                }
            }
        }
        is StorySlide.LastTime -> {
            Emoji("📍")
            Kicker(stringResource(Res.string.story_last_title))
            Headline(slide.place ?: slide.date.longLabel())
            if (slide.place != null) Body(slide.date.longLabel())
            if (slide.with.isNotEmpty()) Body(stringResource(Res.string.story_last_with, slide.with.joinToString()))
            slide.note?.let { Quote(it) }
        }
        is StorySlide.Tastes -> {
            Emoji("💛")
            Headline(stringResource(Res.string.story_tastes_title))
            Chips(slide.likes)
            if (slide.dislikes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Kicker(stringResource(Res.string.story_tastes_avoid))
                Chips(slide.dislikes, alpha = 0.12f)
            }
        }
        is StorySlide.Notes -> {
            Emoji("📝")
            Headline(stringResource(Res.string.story_notes_title))
            Quote(slide.text)
        }
        is StorySlide.ToDo -> {
            Emoji("⏰")
            Headline(stringResource(Res.string.story_todo_title))
            slide.reminders.forEach { Fact("•", it) }
        }
        is StorySlide.Ideas -> {
            Emoji("💡")
            Headline(stringResource(Res.string.story_ideas_title))
            slide.ideas.forEach { idea ->
                Text(idea.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Small(idea.description)
            }
        }
        is StorySlide.Outro -> {
            Box(Modifier.align(Alignment.CenterHorizontally)) { FriendAvatar(friend.name, friend.initials, size = 96.dp, highlighted = true) }
            Headline(stringResource(Res.string.story_outro_title, firstName))
            if (slide.askAbout.isEmpty()) {
                Body(stringResource(Res.string.story_outro_empty))
            } else {
                Kicker(stringResource(Res.string.story_outro_ask))
                slide.askAbout.forEach { Fact("💬", it) }
            }
        }
    }
}

@Composable
private fun Kicker(text: String) = Text(
    text.uppercase(),
    color = Color.White.copy(alpha = 0.85f),
    style = MaterialTheme.typography.labelLarge,
    fontWeight = FontWeight.Bold,
)

@Composable
private fun Headline(text: String) = Text(
    text,
    color = Color.White,
    style = MaterialTheme.typography.headlineLarge,
    fontWeight = FontWeight.ExtraBold,
)

@Composable
private fun Body(text: String) = Text(text, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.titleMedium)

@Composable
private fun Small(text: String) = Text(text, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)

@Composable
private fun Emoji(text: String) = Text(text, style = MaterialTheme.typography.displayMedium)

@Composable
private fun Badge(text: String) = Text(
    text.uppercase(),
    color = Color.White,
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
    modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.25f)).padding(horizontal = 12.dp, vertical = 4.dp),
)

@Composable
private fun Quote(text: String) = Text(
    "“$text”",
    color = Color.White,
    style = MaterialTheme.typography.titleLarge,
    textAlign = TextAlign.Start,
    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Color.White.copy(alpha = 0.15f)).padding(16.dp),
)

@Composable
private fun Fact(emoji: String, text: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
            Text(emoji, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(items: List<String>, alpha: Float = 0.25f) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach {
            Text(
                it,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = alpha)).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}
