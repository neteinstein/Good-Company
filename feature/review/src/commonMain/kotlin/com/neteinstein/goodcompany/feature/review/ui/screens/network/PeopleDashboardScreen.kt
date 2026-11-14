package com.neteinstein.goodcompany.feature.review.ui.screens.network

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.PeopleDashboard
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.FriendAvatar
import com.neteinstein.goodcompany.core.ui.components.SectionCard
import com.neteinstein.goodcompany.core.ui.components.StackedAvatars
import com.neteinstein.goodcompany.core.ui.components.circlePalette
import com.neteinstein.goodcompany.core.ui.format.monthShort
import com.neteinstein.goodcompany.core.ui.l10n.closenessLabel
import com.neteinstein.goodcompany.core.ui.resources.back
import com.neteinstein.goodcompany.feature.review.resources.Res
import com.neteinstein.goodcompany.feature.review.resources.network_title
import com.neteinstein.goodcompany.feature.review.resources.people_avg_warmth
import com.neteinstein.goodcompany.feature.review.resources.people_by_circle
import com.neteinstein.goodcompany.feature.review.resources.people_by_closeness
import com.neteinstein.goodcompany.feature.review.resources.people_circle_cooling
import com.neteinstein.goodcompany.feature.review.resources.people_circles
import com.neteinstein.goodcompany.feature.review.resources.people_cooling
import com.neteinstein.goodcompany.feature.review.resources.people_cooling_row
import com.neteinstein.goodcompany.feature.review.resources.people_count
import com.neteinstein.goodcompany.feature.review.resources.people_empty
import com.neteinstein.goodcompany.feature.review.resources.people_meetups_90
import com.neteinstein.goodcompany.feature.review.resources.people_members
import com.neteinstein.goodcompany.feature.review.resources.people_never
import com.neteinstein.goodcompany.feature.review.resources.people_never_hint
import com.neteinstein.goodcompany.feature.review.resources.people_per_month
import com.neteinstein.goodcompany.feature.review.resources.people_title
import com.neteinstein.goodcompany.feature.review.resources.people_trend
import com.neteinstein.goodcompany.feature.review.resources.people_trend_caption
import com.neteinstein.goodcompany.feature.review.resources.people_trend_down
import com.neteinstein.goodcompany.feature.review.resources.people_trend_flat
import com.neteinstein.goodcompany.feature.review.resources.people_trend_up
import com.neteinstein.goodcompany.feature.review.resources.people_warmth_off
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

@Composable
fun PeopleDashboardScreen(
    onBack: () -> Unit,
    onOpenFriend: (FriendId) -> Unit,
    onOpenMap: () -> Unit,
    viewModel: PeopleDashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PeopleDashboardContent(state, onBack, onOpenFriend, onOpenMap)
}

/** "Your people" (issue #46). With *Show warmth* off it shows counts only. */
@Composable
fun PeopleDashboardContent(state: PeopleDashboardUiState, onBack: () -> Unit, onOpenFriend: (FriendId) -> Unit, onOpenMap: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.back)) }
                Text(
                    stringResource(Res.string.people_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onOpenMap) { Icon(Icons.Rounded.Hub, stringResource(Res.string.network_title)) }
            }
            val d = state.dashboard
            when {
                d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                d.people == 0 -> EmptyState(
                    icon = Icons.Rounded.Group,
                    title = stringResource(Res.string.people_title),
                    message = stringResource(Res.string.people_empty),
                )
                else -> Dashboard(d, onOpenFriend)
            }
        }
    }
}

@Composable
private fun Dashboard(d: PeopleDashboard, onOpenFriend: (FriendId) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    d.meetupsLast90Days.toString(),
                    pluralStringResource(Res.plurals.people_meetups_90, d.meetupsLast90Days),
                    Modifier.weight(1f),
                )
                val avg = d.averageWarmth
                if (avg != null) {
                    StatTile(avg.toString(), stringResource(Res.string.people_avg_warmth), Modifier.weight(1f))
                } else {
                    StatTile(d.people.toString(), pluralStringResource(Res.plurals.people_count, d.people), Modifier.weight(1f))
                }
            }
        }
        if (d.byCircle.isNotEmpty()) {
            item {
                SectionCard(
                    stringResource(if (d.showWarmth) Res.string.people_by_circle else Res.string.people_circles),
                    icon = Icons.Rounded.BarChart,
                ) {
                    d.byCircle.forEach { row ->
                        WarmthRow(
                            label = "${row.circle.emoji} ${row.circle.name}",
                            warmth = row.warmth,
                            count = row.members,
                            color = circlePalette(row.circle.color).accent,
                            note = if (row.isCooling) stringResource(Res.string.people_circle_cooling, abs(row.change ?: 0)) else null,
                        )
                    }
                }
            }
        }
        if (d.byCloseness.isNotEmpty()) {
            item {
                SectionCard(stringResource(Res.string.people_by_closeness), icon = Icons.Rounded.Group) {
                    d.byCloseness.forEach { row ->
                        WarmthRow(closenessLabel(row.closeness), row.warmth, row.people, MaterialTheme.colorScheme.primary, null)
                    }
                }
            }
        }
        if (d.trend.size >= 2) {
            item {
                val delta = d.trendDelta ?: 0
                SectionCard(
                    stringResource(Res.string.people_trend),
                    icon = Icons.AutoMirrored.Rounded.ShowChart,
                    action = {
                        Text(
                            when {
                                delta > 0 -> stringResource(Res.string.people_trend_up, delta)
                                delta < 0 -> stringResource(Res.string.people_trend_down, -delta)
                                else -> stringResource(Res.string.people_trend_flat)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (delta < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    },
                ) {
                    TrendChart(d.trend, Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 16.dp))
                    Text(
                        stringResource(Res.string.people_trend_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        if (d.cooling.isNotEmpty()) {
            item {
                SectionCard(stringResource(Res.string.people_cooling), icon = Icons.Rounded.NotificationsActive) {
                    d.cooling.forEach { c ->
                        val detail = listOfNotNull(stringResource(Res.string.people_cooling_row, c.before, c.now), c.circle?.name).joinToString(" · ")
                        ListItem(
                            modifier = Modifier.clickable { onOpenFriend(c.friend.id) },
                            leadingContent = { FriendAvatar(c.friend, size = 40.dp) },
                            headlineContent = { Text(c.friend.name) },
                            supportingContent = { Text(detail) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
        item {
            SectionCard(stringResource(Res.string.people_per_month), icon = Icons.Rounded.CalendarMonth) {
                MonthBars(d.meetupsPerMonth)
            }
        }
        if (d.neverLogged.isNotEmpty()) {
            item {
                SectionCard(
                    stringResource(Res.string.people_never),
                    icon = Icons.Rounded.PersonOff,
                    action = {
                        Text(
                            pluralStringResource(Res.plurals.people_members, d.neverLogged.size, d.neverLogged.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StackedAvatars(d.neverLogged, size = 36.dp, max = 4)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(Res.string.people_never_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                    d.neverLogged.take(MAX_NEVER).forEach { f ->
                        Text(
                            f.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable { onOpenFriend(f.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        if (!d.showWarmth) {
            item {
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        stringResource(Res.string.people_warmth_off),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

private const val MAX_NEVER = 8

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A circle or closeness: its warmth bar and number, or just how many people when warmth is hidden. */
@Composable
private fun WarmthRow(label: String, warmth: Int?, count: Int, color: Color, note: String?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
        if (warmth != null) {
            Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(warmth / 100f).fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
            }
            Text(warmth.toString(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(40.dp).padding(start = 12.dp))
        } else {
            Text(pluralStringResource(Res.plurals.people_members, count, count), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TrendChart(points: List<Int>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier) {
        val lo = (points.min() - 5).coerceAtLeast(0).toFloat()
        val hi = (points.max() + 5).coerceAtMost(100).toFloat().coerceAtLeast(lo + 1)
        fun at(i: Int) = Offset(size.width * i / (points.size - 1), size.height * (1 - (points[i] - lo) / (hi - lo)))
        val path = Path().apply {
            moveTo(at(0).x, at(0).y)
            for (i in 1 until points.size) lineTo(at(i).x, at(i).y)
        }
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, fill)
        drawPath(path, line, style = Stroke(2.5.dp.toPx()))
    }
}

@Composable
private fun MonthBars(months: List<PeopleDashboard.MonthCount>) {
    val most = months.maxOf { it.meetups }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        months.forEachIndexed { i, m ->
            val label = monthShort(Month(m.month))
            Column(
                Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { contentDescription = "$label ${m.meetups}" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(m.meetups.toString(), style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(0.75f * m.meetups / most + 0.02f)
                        .background(
                            if (i == months.lastIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                        ),
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
