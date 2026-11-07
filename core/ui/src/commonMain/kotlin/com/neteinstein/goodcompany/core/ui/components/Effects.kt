package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A burst of hearts that floats up and fades — played whenever [trigger] changes (and is > 0).
 * Purely decorative: wrap the thing that was tapped in a Box and put this on top.
 */
@Composable
fun HeartBurst(trigger: Int, modifier: Modifier = Modifier, count: Int = 9) {
    if (trigger <= 0) return
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, Color(0xFFFF8A80))
    val particles = remember(trigger) {
        val r = Random(trigger)
        List(count) { i ->
            val angle = -PI / 2 + (r.nextDouble() - 0.5) * PI * 0.9
            Particle(angle, 60f + r.nextFloat() * 70f, 14f + r.nextFloat() * 14f, colors[i % colors.size], r.nextInt(0, 140))
        }
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        particles.forEach { p ->
            val progress = remember(trigger) { Animatable(0f) }
            LaunchedEffect(trigger) {
                launch {
                    kotlinx.coroutines.delay(p.delayMs.toLong())
                    progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                }
            }
            val t = progress.value
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = p.color,
                modifier = Modifier.graphicsLayer {
                    translationX = (cos(p.angle) * p.distance * t).toFloat().dp.toPx()
                    translationY = (sin(p.angle) * p.distance * t).toFloat().dp.toPx()
                    val s = (p.size / 24f) * (0.6f + 0.6f * t)
                    scaleX = s
                    scaleY = s
                    alpha = if (t == 0f) 0f else 1f - t
                },
            )
        }
    }
}

private class Particle(val angle: Double, val distance: Float, val size: Float, val color: Color, val delayMs: Int)

/**
 * Soft, slowly drifting colour blobs used behind screen headers. Gives each tab a bit of warmth
 * without competing with content.
 */
@Composable
fun BlobBackground(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
    ),
) {
    val drift = rememberInfiniteTransition(label = "blobs")
    val phase by drift.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "phase")
    val surface = MaterialTheme.colorScheme.background
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val centers = listOf(
            Offset(w * 0.15f + 24.dp.toPx() * cos(phase), h * 0.25f + 16.dp.toPx() * sin(phase)),
            Offset(w * 0.85f + 20.dp.toPx() * sin(phase), h * 0.15f + 20.dp.toPx() * cos(phase)),
            Offset(w * 0.6f + 28.dp.toPx() * cos(phase + 1f), h * 0.85f + 12.dp.toPx() * sin(phase + 1f)),
        )
        centers.forEachIndexed { i, c ->
            val radius = maxOf(w, h) * (0.55f - i * 0.08f)
            drawCircle(
                brush = Brush.radialGradient(listOf(colors[i % colors.size].copy(alpha = 0.75f), surface.copy(alpha = 0f)), c, radius),
                radius = radius,
                center = c,
            )
        }
    }
}

/** Gentle breathing scale for "tap me" affordances. */
@Composable
fun rememberPulse(min: Float = 0.96f, max: Float = 1.04f, periodMs: Int = 1600): Float {
    val t = rememberInfiniteTransition(label = "pulse")
    val v by t.animateFloat(min, max, infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "v")
    return v
}
