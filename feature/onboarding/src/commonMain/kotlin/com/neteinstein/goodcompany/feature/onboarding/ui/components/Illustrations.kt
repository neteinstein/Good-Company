package com.neteinstein.goodcompany.feature.onboarding.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.ui.theme.OnColorGradients
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hero illustration: a big gradient orb with the page's main icon, orbited by small "satellite" icons
 * that bob gently. No bitmap assets, so it scales and themes perfectly.
 */
@Composable
fun OrbitIllustration(main: ImageVector, satellites: List<ImageVector>, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "orbit")
    val angle by t.animateFloat(
        0f,
        (2 * PI).toFloat(),
        infiniteRepeatable(tween(18_000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "a",
    )
    val bob by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val c = MaterialTheme.colorScheme
    Box(modifier.size(280.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(260.dp).background(
                Brush.radialGradient(listOf(c.primaryContainer.copy(alpha = 0.9f), c.background.copy(alpha = 0f))),
                CircleShape,
            ),
        )
        satellites.forEachIndexed { i, icon ->
            val a = angle + i * (2 * PI / satellites.size).toFloat()
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = (cos(a) * 112.dp.toPx())
                        translationY = (sin(a) * 92.dp.toPx()) + bob.dp.toPx() * if (i % 2 == 0) 1 else -1
                    }
                    .size(48.dp)
                    .shadow(6.dp, CircleShape)
                    .background(c.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = listOf(c.primary, c.secondary, c.tertiary)[i % 3], modifier = Modifier.size(24.dp)) }
        }
        Box(
            Modifier
                .graphicsLayer { translationY = bob.dp.toPx() }
                .size(128.dp)
                .shadow(16.dp, CircleShape, ambientColor = c.primary, spotColor = c.primary)
                .background(Brush.linearGradient(OnColorGradients.Hero), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(main, null, tint = Color.White, modifier = Modifier.size(60.dp)) }
    }
}
