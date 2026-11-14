package com.neteinstein.goodcompany.feature.agenda.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.AgendaItem
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.LastSeenAnswer
import com.neteinstein.goodcompany.core.domain.usecase.SnoozeOption
import com.neteinstein.goodcompany.core.ui.components.AppSnackbarHost
import com.neteinstein.goodcompany.core.ui.components.CatchUpCard
import com.neteinstein.goodcompany.core.ui.components.DriftingCard
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.HeaderSettingsButton
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.core.ui.components.ReminderEditor
import com.neteinstein.goodcompany.core.ui.components.ScreenHeader
import com.neteinstein.goodcompany.core.ui.components.SectionHeader
import com.neteinstein.goodcompany.core.ui.components.SendMessageSheet
import com.neteinstein.goodcompany.core.ui.components.StoryBubbleChip
import com.neteinstein.goodcompany.core.ui.components.YearReviewCard
import com.neteinstein.goodcompany.core.ui.components.calendarEntry
import com.neteinstein.goodcompany.core.ui.components.showMessage
import com.neteinstein.goodcompany.core.ui.components.showUndo
import com.neteinstein.goodcompany.core.ui.format.relativeDay
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.format.shortLabelText
import com.neteinstein.goodcompany.core.ui.format.weekdayLong
import com.neteinstein.goodcompany.core.ui.l10n.aiErrorText
import com.neteinstein.goodcompany.core.ui.platform.rememberCalendarAdder
import com.neteinstein.goodcompany.core.ui.resources.plan_closed
import com.neteinstein.goodcompany.core.ui.resources.plan_skipped
import com.neteinstein.goodcompany.core.ui.resources.undo
import com.neteinstein.goodcompany.core.ui.resources.wish_default
import com.neteinstein.goodcompany.core.ui.theme.OnColorGradients
import com.neteinstein.goodcompany.feature.agenda.resources.Res
import com.neteinstein.goodcompany.feature.agenda.resources.eyebrow
import com.neteinstein.goodcompany.feature.agenda.resources.fab_reminder
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_got_dont_know
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_got_few_months
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_got_over_year
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_got_this_month
import com.neteinstein.goodcompany.feature.agenda.resources.last_seen_got_this_week
import com.neteinstein.goodcompany.feature.agenda.resources.msg_reminder_saved
import com.neteinstein.goodcompany.feature.agenda.resources.today_all
import com.neteinstein.goodcompany.feature.agenda.resources.today_before_you_meet
import com.neteinstein.goodcompany.feature.agenda.resources.today_call_logged
import com.neteinstein.goodcompany.feature.agenda.resources.today_catch_up
import com.neteinstein.goodcompany.feature.agenda.resources.today_checkin_body
import com.neteinstein.goodcompany.feature.agenda.resources.today_checkin_title
import com.neteinstein.goodcompany.feature.agenda.resources.today_drifting
import com.neteinstein.goodcompany.feature.agenda.resources.today_drifting_hint
import com.neteinstein.goodcompany.feature.agenda.resources.today_empty_action
import com.neteinstein.goodcompany.feature.agenda.resources.today_empty_body
import com.neteinstein.goodcompany.feature.agenda.resources.today_empty_title
import com.neteinstein.goodcompany.feature.agenda.resources.today_everything
import com.neteinstein.goodcompany.feature.agenda.resources.today_logged_with
import com.neteinstein.goodcompany.feature.agenda.resources.today_nothing_week
import com.neteinstein.goodcompany.feature.agenda.resources.today_snoozed
import com.neteinstein.goodcompany.feature.agenda.resources.today_this_week
import com.neteinstein.goodcompany.feature.agenda.resources.today_title
import com.neteinstein.goodcompany.feature.agenda.resources.wish_gift_hint
import com.neteinstein.goodcompany.feature.agenda.resources.wish_title
import com.neteinstein.goodcompany.feature.agenda.ui.components.AgendaRow
import com.neteinstein.goodcompany.feature.agenda.ui.components.LastSeenCard
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** Localised snackbar text for a [TodayMessage]. */
suspend fun TodayMessage.text(): String = when (this) {
    TodayMessage.ReminderSaved -> getString(Res.string.msg_reminder_saved)
    is TodayMessage.CallLogged -> getString(Res.string.today_call_logged, name)
    is TodayMessage.Snoozed -> getString(Res.string.today_snoozed, name, until.shortLabelText())
    is TodayMessage.CheckedIn -> getString(Res.string.today_logged_with, names.joinToString())
    TodayMessage.PlanClosed -> getString(CoreRes.string.plan_closed)
    TodayMessage.PlanSkipped -> getString(CoreRes.string.plan_skipped)
    is TodayMessage.AiError -> aiErrorText(error)
    is TodayMessage.LastSeenAnswered -> getString(
        when (answer) {
            LastSeenAnswer.THIS_WEEK -> Res.string.last_seen_got_this_week
            LastSeenAnswer.THIS_MONTH -> Res.string.last_seen_got_this_month
            LastSeenAnswer.FEW_MONTHS -> Res.string.last_seen_got_few_months
            LastSeenAnswer.OVER_A_YEAR -> Res.string.last_seen_got_over_year
            LastSeenAnswer.DONT_KNOW -> Res.string.last_seen_got_dont_know
        },
        name,
    )
}

/**
 * The Today tab (issue #65). [checkInSheet] is the "I'm with…" sheet, supplied by the app shell (it lives with the
 * circles): it calls `onSaved` with the first names it logged, or `onDismiss`.
 */
@Composable
fun TodayScreen(
    onOpenFriend: (FriendId) -> Unit,
    onOpenStory: (FriendId) -> Unit,
    /** "We met" on a catch-up card: the small "We met" panel (issue #47). */
    onWeMet: (FriendId) -> Unit,
    /** "We met" on an overdue circle: the panel on that circle, nobody ticked (F20). */
    onCircleMet: (String) -> Unit,
    /** "Did you meet?" → yes: the "We met" panel on that occurrence (issues #43, #47). */
    onLogPlan: (planId: String, day: LocalDate) -> Unit,
    /** "Everything coming up" and "All N": the full agenda. */
    onOpenAgenda: () -> Unit,
    onOpenSettings: () -> Unit,
    /** No people yet: the People tab, where they're added. */
    onOpenPeople: () -> Unit,
    checkInSheet: @Composable (onSaved: (List<String>) -> Unit, onDismiss: () -> Unit) -> Unit,
    /** The December "Your year in friendship" card (issue #67). */
    onOpenYearReview: (Int) -> Unit = {},
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val addToCalendar = rememberCalendarAdder()
    var checkingIn by rememberSaveable { mutableStateOf(false) }
    TodayContent(
        state = state,
        actions = TodayActions(
            onCheckIn = { checkingIn = true },
            onOpenFriend = onOpenFriend,
            onOpenStory = onOpenStory,
            onWeMet = onWeMet,
            onCalled = viewModel::onCalled,
            onSnooze = viewModel::onSnooze,
            onOpenWish = viewModel::onOpenWish,
            onGroupMet = onCircleMet,
            onConfirmPlan = { item, happened -> if (happened) onLogPlan(item.plan.id, item.occurrence) else viewModel.onPlanDidNotHappen(item) },
            onAddPlanToCalendar = { addToCalendar(it.plan.calendarEntry(it.friends, it.occurrence, it.circle)) },
            onSkipPlan = viewModel::onSkipPlan,
            onOpenAgenda = onOpenAgenda,
            onOpenSettings = onOpenSettings,
            onOpenPeople = onOpenPeople,
            onOpenYearReview = onOpenYearReview,
            onNewReminder = viewModel::onNewReminder,
            onMessageShown = viewModel::onMessageShown,
            onLastSeenAnswer = viewModel::onLastSeenAnswer,
            onLastSeenNotNow = viewModel::onLastSeenNotNow,
            onUndoLastSeen = viewModel::onUndoLastSeen,
        ),
    )
    if (checkingIn) {
        checkInSheet(
            { names ->
                checkingIn = false
                viewModel.onCheckedIn(names)
            },
            { checkingIn = false },
        )
    }
    if (state.showReminderEditor) {
        ReminderEditor(state.friends, state.today, viewModel::onDismissReminderEditor, viewModel::onSaveReminder)
    }
    state.wishFor?.let { friend ->
        val firstName = friend.displayName.substringBefore(' ')
        val ideas = friend.gifts.filter { it.isIdea }
        SendMessageSheet(
            title = stringResource(Res.string.wish_title, firstName),
            suggestion = state.wishDraft ?: stringResource(CoreRes.string.wish_default, firstName),
            phone = friend.phoneNumbers.firstOrNull(),
            preferred = friend.preferredChannel,
            email = friend.emails.firstOrNull()?.address,
            onDismiss = viewModel::onDismissWish,
            isGenerating = state.isDraftingWish,
            onPersonalise = if (state.hasAiKey) viewModel::onPersonaliseWish else null,
            header = if (ideas.isEmpty()) {
                null
            } else {
                { InfoPill(stringResource(Res.string.wish_gift_hint, ideas.joinToString(limit = 3) { it.title }), icon = Icons.Rounded.CardGiftcard) }
            },
        )
    }
}

data class TodayActions(
    val onCheckIn: () -> Unit = {},
    val onOpenFriend: (FriendId) -> Unit = {},
    val onOpenStory: (FriendId) -> Unit = {},
    val onWeMet: (FriendId) -> Unit = {},
    val onCalled: (FriendId) -> Unit = {},
    val onSnooze: (FriendId, SnoozeOption) -> Unit = { _, _ -> },
    val onOpenWish: (FriendId) -> Unit = {},
    val onGroupMet: (String) -> Unit = {},
    val onConfirmPlan: (AgendaItem.PlanToConfirm, Boolean) -> Unit = { _, _ -> },
    val onAddPlanToCalendar: (AgendaItem.PlanUpcoming) -> Unit = {},
    val onSkipPlan: (AgendaItem.PlanUpcoming) -> Unit = {},
    val onOpenAgenda: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenPeople: () -> Unit = {},
    val onOpenYearReview: (Int) -> Unit = {},
    val onNewReminder: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onLastSeenAnswer: (FriendId, LastSeenAnswer) -> Unit = { _, _ -> },
    val onLastSeenNotNow: () -> Unit = {},
    val onUndoLastSeen: (TodayMessage.LastSeenAnswered) -> Unit = {},
)

@Composable
fun TodayContent(state: TodayUiState, actions: TodayActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val undo = stringResource(CoreRes.string.undo)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        if (message is TodayMessage.LastSeenAnswered) {
            if (snackbar.showUndo(message.text(), undo) == SnackbarResult.ActionPerformed) {
                actions.onUndoLastSeen(message)
                return@LaunchedEffect
            }
        } else {
            snackbar.showMessage(message.text())
        }
        actions.onMessageShown()
    }
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (!state.isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = actions.onNewReminder,
                    icon = { Icon(Icons.Rounded.AlarmAdd, null) },
                    text = { Text(stringResource(Res.string.fab_reminder)) },
                )
            }
        },
        snackbarHost = { AppSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
        ) {
            item(key = "header") {
                ScreenHeader(
                    eyebrow = stringResource(Res.string.eyebrow, weekdayLong(state.today.dayOfWeek), state.today.shortLabel()),
                    title = stringResource(Res.string.today_title),
                    trailing = { HeaderSettingsButton(actions.onOpenSettings) },
                )
            }
            if (state.isEmpty) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.People,
                        title = stringResource(Res.string.today_empty_title),
                        message = stringResource(Res.string.today_empty_body),
                        actionLabel = stringResource(Res.string.today_empty_action),
                        onAction = actions.onOpenPeople,
                    )
                }
                return@LazyColumn
            }
            state.lastSeenAsk?.let { friend ->
                item(key = "last-seen") {
                    LastSeenCard(
                        friend,
                        state.lastSeenPosition,
                        state.lastSeenTotal,
                        onAnswer = { actions.onLastSeenAnswer(friend.id, it) },
                        onNotNow = actions.onLastSeenNotNow,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (state.showCheckIn) item(key = "check-in") { CheckInCard(actions.onCheckIn) }
            state.reviewYear?.let { year ->
                item(key = "review") { YearReviewCard(year, onClick = { actions.onOpenYearReview(year) }) }
            }

            item(key = "week-h") { SectionHeader(stringResource(Res.string.today_this_week)) }
            if (!state.isLoading && state.week.isEmpty()) {
                item(key = "week-empty") {
                    Text(
                        stringResource(Res.string.today_nothing_week),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            state.weekByDay.forEach { (day, items) ->
                item(key = "day-$day") {
                    Text(
                        relativeDay(day, state.today).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 2.dp),
                    )
                }
                items(items, key = { it.key }) {
                    AgendaRow(
                        it,
                        state.today,
                        actions.onOpenFriend,
                        onOpenWish = actions.onOpenWish,
                        onGroupMet = actions.onGroupMet,
                        onConfirmPlan = actions.onConfirmPlan,
                        onAddPlanToCalendar = actions.onAddPlanToCalendar,
                        onSkipPlan = actions.onSkipPlan,
                        modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                    )
                }
            }
            item(key = "everything") {
                TextButton(onClick = actions.onOpenAgenda, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(Res.string.today_everything))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                }
            }

            if (state.catchUp.isNotEmpty()) {
                item(key = "catchup-h") {
                    SectionHeader(
                        stringResource(Res.string.today_catch_up),
                        action = if (state.catchUpTotal > state.catchUp.size) {
                            { TextButton(onClick = actions.onOpenAgenda) { Text(stringResource(Res.string.today_all, state.catchUpTotal)) } }
                        } else {
                            null
                        },
                    )
                }
                items(state.catchUp, key = { "c-${it.friend.id.value}" }) { s ->
                    val id = s.friend.id
                    CatchUpCard(
                        s,
                        onOpen = { actions.onOpenFriend(id) },
                        onMet = { actions.onWeMet(id) },
                        onCalled = { actions.onCalled(id) },
                        onSnooze = { actions.onSnooze(id, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            if (state.stories.isNotEmpty()) {
                item(key = "stories-h") { SectionHeader(stringResource(Res.string.today_before_you_meet)) }
                item(key = "stories") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(state.stories, key = { "s-${it.friend.id.value}" }) { b ->
                            StoryBubbleChip(b, state.today, { actions.onOpenStory(b.friend.id) }, Modifier.animateItem())
                        }
                    }
                }
            }
            if (state.drifting.isNotEmpty()) {
                item(key = "drift-h") { SectionHeader(stringResource(Res.string.today_drifting)) }
                item(key = "drift-hint") {
                    Text(
                        stringResource(Res.string.today_drifting_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                    )
                }
                items(state.drifting, key = { "d-${it.friend.id.value}" }) { s ->
                    DriftingCard(
                        s,
                        onOpen = { actions.onOpenFriend(s.friend.id) },
                        onSnooze = { actions.onSnooze(s.friend.id, SnoozeOption.ONE_WEEK) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** "I'm with… · Log who you're with now": opens the check-in sheet (from Together). */
@Composable
private fun CheckInCard(onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(OnColorGradients.Hero))
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.today_checkin_title), style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(stringResource(Res.string.today_checkin_body), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        }
        Box(Modifier.size(44.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
