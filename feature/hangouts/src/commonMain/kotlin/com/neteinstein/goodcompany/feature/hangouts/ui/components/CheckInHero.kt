package com.neteinstein.goodcompany.feature.hangouts.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.usecase.Relationships
import com.neteinstein.goodcompany.core.ui.components.AnimatedNumber
import com.neteinstein.goodcompany.core.ui.components.HeartBurst
import com.neteinstein.goodcompany.feature.hangouts.resources.Res
import com.neteinstein.goodcompany.feature.hangouts.resources.hero_cta
import com.neteinstein.goodcompany.feature.hangouts.resources.hero_cta_description
import com.neteinstein.goodcompany.feature.hangouts.resources.hero_friends
import com.neteinstein.goodcompany.feature.hangouts.resources.hero_hint
import com.neteinstein.goodcompany.feature.hangouts.resources.hero_meetups
import com.neteinstein.goodcompany.feature.hangouts.resources.hero_spot
import org.jetbrains.compose.resources.stringResource

/** Gradient hero card: the big pulsing "I'm with…" button plus this month's numbers. */
@Composable
fun CheckInHero(stats: Relationships.MonthStats, celebrate: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val cta = stringResource(Res.string.hero_cta_description)
    val halo by pulse.animateFloat(0.9f, 1.18f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "halo")
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(Color(0xFFFF8A80), c.primary, Color(0xFF8E2DE2).copy(alpha = 0.85f)))),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(132.dp).scale(halo).graphicsLayer { alpha = 1.5f - halo }.background(Color.White.copy(alpha = 0.25f), CircleShape))
                Box(
                    Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClick()
                        }
                        .semantics { contentDescription = cta },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Diversity3, null, tint = c.primary, modifier = Modifier.size(52.dp)) }
                HeartBurst(celebrate, Modifier.size(132.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(Res.string.hero_cta), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(stringResource(Res.string.hero_hint), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(stats.meetups, stringResource(Res.string.hero_meetups))
                Stat(stats.friendsSeen, stringResource(Res.string.hero_friends))
                Column(Modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stats.topPlace ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(Res.string.hero_spot),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedNumber(value, style = MaterialTheme.typography.headlineMedium.copy(color = Color.White))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
