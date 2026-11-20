package com.neteinstein.goodcompany.feature.friends.ui.screens.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AlarmAdd
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.ContactChannel
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.usecase.FamilyAround
import com.neteinstein.goodcompany.core.domain.usecase.FamilyMember
import com.neteinstein.goodcompany.core.domain.usecase.StoryRing
import com.neteinstein.goodcompany.core.ui.components.CircleActionChip
import com.neteinstein.goodcompany.core.ui.components.CircleChip
import com.neteinstein.goodcompany.core.ui.components.FamilyRing
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.InfoPill
import com.neteinstein.goodcompany.core.ui.components.LogIcon
import com.neteinstein.goodcompany.core.ui.components.QuickAction
import com.neteinstein.goodcompany.core.ui.components.StoryRingBox
import com.neteinstein.goodcompany.core.ui.components.WarmthMeter
import com.neteinstein.goodcompany.core.ui.components.avatarColors
import com.neteinstein.goodcompany.core.ui.components.closenessIcon
import com.neteinstein.goodcompany.core.ui.components.warmthColor
import com.neteinstein.goodcompany.core.ui.format.isFarDate
import com.neteinstein.goodcompany.core.ui.format.relativeDayPhrase
import com.neteinstein.goodcompany.core.ui.format.relativePast
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.l10n.closenessLabel
import com.neteinstein.goodcompany.core.ui.l10n.relationToMeLabel
import com.neteinstein.goodcompany.core.ui.l10n.warmthLabel
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.about_preferred_badge
import com.neteinstein.goodcompany.feature.friends.resources.circles_edit
import com.neteinstein.goodcompany.feature.friends.resources.closeness_change
import com.neteinstein.goodcompany.feature.friends.resources.detail_add_birthday
import com.neteinstein.goodcompany.feature.friends.resources.detail_add_photo
import com.neteinstein.goodcompany.feature.friends.resources.detail_back
import com.neteinstein.goodcompany.feature.friends.resources.detail_birthday_turns
import com.neteinstein.goodcompany.feature.friends.resources.detail_birthday_when
import com.neteinstein.goodcompany.feature.friends.resources.detail_caught_up
import com.neteinstein.goodcompany.feature.friends.resources.detail_more_options
import com.neteinstein.goodcompany.feature.friends.resources.detail_orphaned
import com.neteinstein.goodcompany.feature.friends.resources.detail_seen
import com.neteinstein.goodcompany.feature.friends.resources.edit_person
import com.neteinstein.goodcompany.feature.friends.resources.message_sheet_title
import com.neteinstein.goodcompany.feature.friends.resources.news_add
import com.neteinstein.goodcompany.feature.friends.resources.photo_sheet_title
import com.neteinstein.goodcompany.feature.friends.resources.qa_call
import com.neteinstein.goodcompany.feature.friends.resources.qa_email
import com.neteinstein.goodcompany.feature.friends.resources.qa_message
import com.neteinstein.goodcompany.feature.friends.resources.qa_remind
import com.neteinstein.goodcompany.feature.friends.resources.qa_text
import com.neteinstein.goodcompany.feature.friends.resources.qa_we_met
import com.neteinstein.goodcompany.feature.friends.resources.qa_whatsapp
import com.neteinstein.goodcompany.feature.friends.resources.remove_person
import com.neteinstein.goodcompany.feature.friends.resources.story_open
import com.neteinstein.goodcompany.feature.friends.resources.tab_about
import com.neteinstein.goodcompany.feature.friends.resources.tab_together
import com.neteinstein.goodcompany.feature.friends.resources.timeline_log
import com.neteinstein.goodcompany.feature.friends.resources.warmth_title
import org.jetbrains.compose.resources.stringResource

// The top of a person's page (issues #90, #103): the top bar, the family around the photo (with the story ring), the
// info box (closeness, warmth, birthday, last seen, circles) and the actions; the bar the top shrinks into and the
// tabs pinned under it; and the Message sheet.

/** What the top of the page does; the content composable wires these to sheets and the ViewModel. */
internal class PersonTopActions(
    val onBack: () -> Unit,
    /** The pencil: the Edit person popup (issue #103). */
    val onEditPerson: () -> Unit,
    val onRemove: () -> Unit,
    /** The camera badge: pick a photo, or replace, crop or remove it. */
    val onPhoto: () -> Unit,
    /** The photo itself: their catch-up story (issue #103, the Instagram model). */
    val onOpenStory: () -> Unit,
    val onEditBirthday: () -> Unit,
    val onPickCloseness: () -> Unit,
    val onOpenCircle: (String) -> Unit,
    val onEditCircles: () -> Unit,
    val onOpenFamily: (FamilyMember) -> Unit,
    val onMoreFamily: () -> Unit,
    val onMessage: () -> Unit,
    val onWeMet: () -> Unit,
    val onRemind: () -> Unit,
    val onAddNews: () -> Unit,
    val onLog: () -> Unit,
)

/** The phone number Call dials, or null without one. */
internal fun callLink(
    f: Friend,
): String? = f.phoneNumbers.firstOrNull()?.filter { it.isDigit() || it == '+' }?.takeIf { it.isNotEmpty() }?.let { "tel:$it" }

/** Warmth shows for real friends while Settings → Show warmth is on (the same rule everywhere on the page). */
internal fun showsWarmth(f: Friend, state: FriendDetailUiState): Boolean =
    state.showWarmth && f.isCloseFriend && f.tracksKeepInTouch && state.detail?.warmth != null

@Composable
internal fun PersonTop(f: Friend, state: FriendDetailUiState, family: FamilyAround, actions: PersonTopActions) {
    // Issue #103: the area behind the photo is tinted by warmth (theme tokens only); without warmth, their colours.
    val warmth = state.detail?.warmth?.takeIf { showsWarmth(f, state) }
    val (start, end) = if (warmth != null) {
        warmthColor(warmth.level).let { it to it }
    } else {
        avatarColors(f.displayName)
    }
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(start.copy(alpha = 0.4f), end.copy(alpha = 0.14f), MaterialTheme.colorScheme.background)),
            ),
        )
        Column(Modifier.fillMaxWidth().statusBarsPadding().animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            ViewBar(f, actions)
            val photoLabel = if (f.avatar == null) {
                stringResource(Res.string.detail_add_photo)
            } else {
                stringResource(Res.string.photo_sheet_title, f.displayName.substringBefore(' '))
            }
            val storyLabel = stringResource(Res.string.story_open, f.displayName.substringBefore(' '))
            FamilyRing(
                family = family,
                today = state.today,
                onOpen = actions.onOpenFamily,
                onMore = actions.onMoreFamily,
                photo = {
                    // The photo opens their story; the camera badge keeps the photo options (F14).
                    Box {
                        StoryRingBox(state.storyRing) {
                            FriendAvatar(
                                f,
                                size = 112.dp,
                                highlighted = f.isCloseFriend && state.storyRing == StoryRing.NONE,
                                sharedKey = "avatar-${f.id.value}",
                                modifier = Modifier.clip(CircleShape).clickable(onClickLabel = storyLabel, onClick = actions.onOpenStory),
                            )
                        }
                        Surface(
                            onClick = actions.onPhoto,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                            modifier = Modifier.align(Alignment.BottomEnd).size(34.dp),
                        ) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = photoLabel, modifier = Modifier.padding(7.dp))
                        }
                    }
                },
                name = {
                    Text(
                        f.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp).padding(top = 12.dp),
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            InfoBox(f, state, actions)
            Spacer(Modifier.height(12.dp))
            TopActions(f, actions)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ViewBar(f: Friend, actions: PersonTopActions) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val glass = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
        FilledTonalIconButton(onClick = actions.onBack, colors = glass) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.detail_back))
        }
        Spacer(Modifier.weight(1f))
        FilledTonalIconButton(onClick = actions.onEditPerson, colors = glass) {
            Icon(Icons.Rounded.Edit, stringResource(Res.string.edit_person, f.displayName.substringBefore(' ')))
        }
        RemoveMenu(f, actions.onRemove) { open ->
            FilledTonalIconButton(onClick = open, colors = glass) {
                Icon(Icons.Rounded.MoreVert, stringResource(Res.string.detail_more_options))
            }
        }
    }
}

/** ⋮ with "Remove <name>": Delete left the top bar (issue #90). */
@Composable
private fun RemoveMenu(f: Friend, onRemove: () -> Unit, anchor: @Composable (open: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        anchor { open = true }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.remove_person, f.displayName)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

/**
 * The box under the photo and family (issue #103): closeness with its icon (opens the closeness picker) and, next to
 * it, the warmth bar; then the birthday, "Your sister", the last catch-up; then their circles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoBox(f: Friend, state: FriendDetailUiState, actions: PersonTopActions) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ClosenessButton(f, actions.onPickCloseness)
                val warmth = state.detail?.warmth
                if (warmth != null && showsWarmth(f, state)) {
                    WarmthMeter(
                        warmth,
                        label = stringResource(Res.string.warmth_title, warmth.value) + " · " + warmthLabel(warmth.level),
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            Pills(f, state, actions)
            Circles(state, actions)
        }
    }
}

/** Their level with its icon; a tap opens the closeness picker (issue #86). */
@Composable
private fun ClosenessButton(f: Friend, onPick: () -> Unit) {
    val changeLevel = stringResource(Res.string.closeness_change)
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(onClickLabel = changeLevel, onClick = onPick)
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(closenessIcon(f.closeness), null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(closenessLabel(f.closeness), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Icon(Icons.Rounded.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Pills(f: Friend, state: FriendDetailUiState, actions: PersonTopActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val birthdayText = f.birthday?.let { b ->
            val next = b.nextOccurrence(state.today)
            val base = if (isFarDate(next, state.today)) {
                next.shortLabel()
            } else {
                stringResource(Res.string.detail_birthday_when, next.shortLabel(), relativeDayPhrase(next, state.today))
            }
            b.ageOn(next)?.let { stringResource(Res.string.detail_birthday_turns, base, it) } ?: base
        } ?: stringResource(Res.string.detail_add_birthday)
        Box(Modifier.clip(CircleShape).clickable(onClick = actions.onEditBirthday)) {
            InfoPill(birthdayText, icon = if (f.birthday != null) Icons.Rounded.Cake else Icons.Rounded.Edit, emphasized = f.birthday != null)
        }
        // "Your sister" only when it is set (issue #90); "Related to you?" is asked in Edit person.
        relationToMeLabel(f)?.let { InfoPill(it, icon = Icons.Rounded.FavoriteBorder, emphasized = true) }
        // Same rule as the rhythm (issue #101): with calls and chats counting, a call is the last catch-up too.
        state.detail?.let { d ->
            d.lastCaughtUp?.let {
                val ago = relativePast(it, state.now, state.zone)
                if (it == d.lastSeen) {
                    InfoPill(stringResource(Res.string.detail_seen, ago), icon = Icons.Rounded.Place)
                } else {
                    InfoPill(stringResource(Res.string.detail_caught_up, ago), icon = Icons.Rounded.Forum)
                }
            }
        }
        if (f.isOrphaned) InfoPill(stringResource(Res.string.detail_orphaned))
    }
}

/** Their circles as plain chips (tap opens the circle) and "Circles", which opens the tick list (issues #70, #90). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Circles(state: FriendDetailUiState, actions: PersonTopActions) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.detail?.groups.orEmpty().forEach { g ->
            CircleChip(g, selected = true, onClick = { actions.onOpenCircle(g.id) }, showDot = false)
        }
        // Same height and style as the circle chips beside it (issue #114).
        CircleActionChip(stringResource(Res.string.circles_edit), Icons.Rounded.Edit, onClick = actions.onEditCircles)
    }
}

/** Message · Call · Caught up · Remind · Add news (decision "Person page · one Message button"; Add news: issue #103). */
@Composable
private fun TopActions(f: Friend, actions: PersonTopActions) {
    val uri = LocalUriHandler.current
    val haptics = LocalHapticFeedback.current
    val lead = MessageOptions.lead(f.preferredChannel)
    val canMessage = MessageOptions.of(f).isNotEmpty()
    val call = callLink(f)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        QuickAction(
            Icons.AutoMirrored.Rounded.Chat,
            stringResource(Res.string.qa_message),
            onClick = actions.onMessage,
            enabled = canMessage,
            emphasized = lead == LeadAction.MESSAGE,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            Icons.Rounded.Call,
            stringResource(Res.string.qa_call),
            onClick = { call?.let { runCatching { uri.openUri(it) } } },
            enabled = call != null,
            emphasized = lead == LeadAction.CALL,
            modifier = Modifier.weight(1f),
        )
        QuickAction(LogIcon, stringResource(Res.string.qa_we_met), onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            actions.onWeMet()
        }, emphasized = lead == LeadAction.WE_MET, modifier = Modifier.weight(1f))
        QuickAction(Icons.Rounded.AlarmAdd, stringResource(Res.string.qa_remind), onClick = actions.onRemind, modifier = Modifier.weight(1f))
        QuickAction(Icons.Rounded.NewReleases, stringResource(Res.string.news_add), onClick = actions.onAddNews, modifier = Modifier.weight(1f))
    }
}

/**
 * Pinned under the top: the bar the top shrinks into once it has scrolled away ([collapsed]: back, small avatar,
 * name, Message, Call; "+" for the Log sheet on Together), then the two tabs.
 */
@Composable
internal fun PinnedTabs(f: Friend, tab: PersonTab, onTab: (PersonTab) -> Unit, collapsed: Boolean, actions: PersonTopActions) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            AnimatedVisibility(collapsed) {
                Column(Modifier.statusBarsPadding()) { CollapsedBar(f, tab, actions) }
            }
            PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = Color.Transparent) {
                PersonTab.entries.forEach { t ->
                    Tab(
                        selected = t == tab,
                        onClick = { onTab(t) },
                        text = {
                            Text(
                                stringResource(
                                    when (t) {
                                        PersonTab.TOGETHER -> Res.string.tab_together
                                        PersonTab.ABOUT -> Res.string.tab_about
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedBar(f: Friend, tab: PersonTab, actions: PersonTopActions) {
    val uri = LocalUriHandler.current
    val call = callLink(f)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.detail_back)) }
        FriendAvatar(f, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            f.displayName,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (tab == PersonTab.TOGETHER) {
            IconButton(onClick = actions.onLog) { Icon(Icons.Rounded.Add, stringResource(Res.string.timeline_log)) }
        }
        IconButton(onClick = actions.onMessage, enabled = MessageOptions.of(f).isNotEmpty()) {
            Icon(Icons.AutoMirrored.Rounded.Chat, stringResource(Res.string.qa_message))
        }
        IconButton(onClick = { call?.let { runCatching { uri.openUri(it) } } }, enabled = call != null) {
            Icon(Icons.Rounded.Call, stringResource(Res.string.qa_call))
        }
    }
}

/**
 * "Message <name>": WhatsApp and Text when there is a number, Email when there is an address, their preferred one
 * first with a badge. Each opens the app with nothing typed; nothing is sent by the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageSheet(f: Friend, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(
                stringResource(Res.string.message_sheet_title, f.displayName.substringBefore(' ')),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            val options = MessageOptions.of(f)
            options.forEachIndexed { i, option ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                ListItem(
                    modifier = Modifier.clickable {
                        onDismiss()
                        runCatching { uri.openUri(option.link) }
                    }.padding(horizontal = 8.dp),
                    leadingContent = {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(
                                if (option.preferred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                channelIcon(option.channel),
                                null,
                                tint = if (option.preferred) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    },
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(
                                    when (option.channel) {
                                        ContactChannel.WHATSAPP -> Res.string.qa_whatsapp
                                        ContactChannel.SMS -> Res.string.qa_text
                                        ContactChannel.EMAIL -> Res.string.qa_email
                                        ContactChannel.CALL -> Res.string.qa_call
                                    },
                                ),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (option.preferred) {
                                Spacer(Modifier.width(8.dp))
                                InfoPill(stringResource(Res.string.about_preferred_badge), emphasized = true)
                            }
                        }
                    },
                    supportingContent = { Text(option.address) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
