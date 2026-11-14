package com.neteinstein.goodcompany.feature.review.ui.screens.network

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.usecase.EdgeKind
import com.neteinstein.goodcompany.core.domain.usecase.NetworkGraph
import com.neteinstein.goodcompany.core.domain.usecase.NetworkNode
import com.neteinstein.goodcompany.core.domain.usecase.Point
import com.neteinstein.goodcompany.core.ui.components.CircleChip
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.components.LocalMyPhoto
import com.neteinstein.goodcompany.core.ui.components.avatarColors
import com.neteinstein.goodcompany.core.ui.components.circlePalette
import com.neteinstein.goodcompany.core.ui.components.rememberPhotoBitmap
import com.neteinstein.goodcompany.core.ui.resources.back
import com.neteinstein.goodcompany.feature.review.resources.Res
import com.neteinstein.goodcompany.feature.review.resources.network_description
import com.neteinstein.goodcompany.feature.review.resources.network_empty_body
import com.neteinstein.goodcompany.feature.review.resources.network_empty_title
import com.neteinstein.goodcompany.feature.review.resources.network_legend_family
import com.neteinstein.goodcompany.feature.review.resources.network_legend_friend
import com.neteinstein.goodcompany.feature.review.resources.network_legend_introduced
import com.neteinstein.goodcompany.feature.review.resources.network_legend_partner
import com.neteinstein.goodcompany.feature.review.resources.network_title
import com.neteinstein.goodcompany.feature.review.resources.network_you
import com.neteinstein.goodcompany.feature.review.resources.people_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

@Composable
fun NetworkMapScreen(
    onBack: () -> Unit,
    onOpenFriend: (FriendId) -> Unit,
    onOpenDashboard: () -> Unit,
    viewModel: NetworkMapViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NetworkMapContent(state, onBack, onOpenFriend, onOpenDashboard, viewModel::onCircle)
}

/** Labels show from this zoom up. */
private const val LABEL_SCALE = 0.9f
private const val ME_RADIUS_DP = 28f

/** Avatar radius in dp: bigger when warmer (when warmth is shown). */
private fun radiusDp(node: NetworkNode): Float = node.warmth?.let { 13f + 13f * it / 100f } ?: 18f

/**
 * "Your network" (issue #46, F6): you at the centre, people linked by family, romantic and other links and by
 * "introduced by". Colour follows the circle, size the warmth. Pinch and pan; tap a person to open their page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkMapContent(
    state: NetworkMapUiState,
    onBack: () -> Unit,
    onOpenFriend: (FriendId) -> Unit,
    onOpenDashboard: () -> Unit,
    onCircle: (String?) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.back)) }
                Text(
                    stringResource(Res.string.network_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = onOpenDashboard) { Icon(Icons.Rounded.Insights, stringResource(Res.string.people_title)) }
            }
            val map = state.map
            when {
                map == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                map.graph.nodes.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Hub,
                    title = stringResource(Res.string.network_empty_title),
                    message = stringResource(Res.string.network_empty_body),
                )
                else -> {
                    if (map.graph.circles.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            map.graph.circles.forEach { c -> CircleChip(c, selected = state.circleId == c.id, onClick = { onCircle(c.id) }) }
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        NetworkCanvas(state, onOpenFriend)
                    }
                    Legend()
                }
            }
        }
    }
}

@Composable
private fun NetworkCanvas(state: NetworkMapUiState, onOpenFriend: (FriendId) -> Unit) {
    val map = state.map ?: return
    val visible = state.visibleIds
    val nodes = map.graph.nodes.filter { it.friend.id.value in visible }
    val edges = map.graph.edges.filter { e -> (e.a == NetworkGraph.ME || e.a in visible) && (e.b == NetworkGraph.ME || e.b in visible) }
    val colors = nodes.associate { n -> n.friend.id.value to (n.circle?.let { circlePalette(it.color).accent } ?: avatarColors(n.friend.name).first) }
    val scheme = MaterialTheme.colorScheme
    val edgeColor = mapOf(
        EdgeKind.FAMILY to scheme.primary,
        EdgeKind.PARTNER to scheme.tertiary,
        EdgeKind.FRIEND to scheme.outlineVariant,
        EdgeKind.INTRODUCED to scheme.outline,
    )
    val labelColor = scheme.onSurface
    val meLabel = stringResource(Res.string.network_you)
    // Your photo (issue #71) at the centre, else "You" on the primary colour.
    val myPhoto = rememberPhotoBitmap(LocalMyPhoto.current)
    val description = pluralStringResource(Res.plurals.network_description, nodes.size, nodes.size)
    val measurer = rememberTextMeasurer()
    val localDensity = LocalDensity.current
    // Zoom and pan; zoom 0 = not touched yet: fit everyone shown.
    var zoomed by remember { mutableFloatStateOf(0f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasDp by remember { mutableStateOf(Offset.Zero) }
    val fit = remember(map.positions, visible, canvasDp) {
        fitScale(map.positions.filterKeys { it == NetworkGraph.ME || it in visible }.values, canvasDp.x, canvasDp.y)
    }
    val scale = if (zoomed > 0f) zoomed else fit
    val currentScale by rememberUpdatedState(scale)
    Canvas(
        Modifier.fillMaxSize()
            .onSizeChanged { canvasDp = Offset(it.width / localDensity.density, it.height / localDensity.density) }
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, panBy, zoom, _ ->
                    val old = currentScale
                    val next = (old * zoom).coerceIn(0.2f, 4f)
                    // Zoom around the fingers: keep the point under the centroid where it was.
                    val centre = Offset(size.width / 2f, size.height / 2f)
                    pan = (pan - (centroid - centre)) * (next / old) + (centroid - centre) + panBy
                    zoomed = next
                }
            }
            .pointerInput(map, visible, scale) {
                detectTapGestures { tap ->
                    val centre = Offset(size.width / 2f, size.height / 2f) + pan
                    val unit = density * scale
                    val hit = nodes.minByOrNull { n ->
                        val p = map.positions[n.friend.id.value] ?: return@minByOrNull Float.MAX_VALUE
                        hypot(centre.x + p.x.toFloat() * unit - tap.x, centre.y + p.y.toFloat() * unit - tap.y)
                    } ?: return@detectTapGestures
                    val p = map.positions.getValue(hit.friend.id.value)
                    val d = hypot(centre.x + p.x.toFloat() * unit - tap.x, centre.y + p.y.toFloat() * unit - tap.y)
                    if (d <= max(radiusDp(hit) * unit, 24.dp.toPx())) onOpenFriend(hit.friend.id)
                }
            },
    ) {
        val centre = Offset(size.width / 2f, size.height / 2f) + pan
        val unit = density * scale
        fun at(id: String): Offset? = map.positions[id]?.let { centre + Offset(it.x.toFloat() * unit, it.y.toFloat() * unit) }
        edges.forEach { e ->
            val a = at(e.a) ?: return@forEach
            val b = at(e.b) ?: return@forEach
            val effect = when (e.kind) {
                EdgeKind.PARTNER -> PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                EdgeKind.INTRODUCED -> PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
                else -> null
            }
            drawLine(edgeColor.getValue(e.kind), a, b, strokeWidth = 2.dp.toPx(), pathEffect = effect)
        }
        val showLabels = scale >= LABEL_SCALE
        nodes.forEach { n ->
            val c = at(n.friend.id.value) ?: return@forEach
            val r = radiusDp(n) * unit
            drawCircle(colors.getValue(n.friend.id.value), r, c)
            drawCentred(
                measurer,
                n.friend.initials,
                c,
                TextStyle(color = Color.White, fontSize = (r / density / 1.4f).sp, fontWeight = FontWeight.Bold),
            )
            if (showLabels) {
                drawCentred(
                    measurer,
                    n.friend.name.substringBefore(' '),
                    c + Offset(0f, r + 10.dp.toPx()),
                    TextStyle(color = labelColor, fontSize = 12.sp),
                )
            }
        }
        val me = at(NetworkGraph.ME) ?: return@Canvas
        val meR = ME_RADIUS_DP * unit
        drawCircle(scheme.primary, meR, me)
        if (myPhoto != null) {
            drawPhoto(myPhoto, me, meR - 3.dp.toPx() * scale)
            if (showLabels) drawCentred(measurer, meLabel, me + Offset(0f, meR + 10.dp.toPx()), TextStyle(color = labelColor, fontSize = 12.sp))
        } else {
            drawCircle(scheme.surface, meR - 3.dp.toPx() * scale, me, style = Stroke(2.dp.toPx() * scale))
            drawCentred(
                measurer,
                meLabel,
                me,
                TextStyle(color = scheme.onPrimary, fontSize = (meR / density / 2.4f).sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

/** Fits every point (plus a margin for the avatars) into a [width] × [height] dp box, at most 1.5×. */
private fun fitScale(points: Collection<Point>, width: Float, height: Float): Float {
    val extent = points.maxOfOrNull { max(kotlin.math.abs(it.x), kotlin.math.abs(it.y)) }?.toFloat() ?: 0f
    val half = min(width, height) / 2f - 40f
    return if (extent <= 0f || half <= 0f) 1f else (half / extent).coerceIn(0.2f, 1.5f)
}

/** [image] centre-cropped to a square and clipped to a circle of [radius] around [centre]. */
private fun DrawScope.drawPhoto(image: ImageBitmap, centre: Offset, radius: Float) {
    if (radius <= 0f) return
    val side = min(image.width, image.height)
    val src = IntOffset((image.width - side) / 2, (image.height - side) / 2)
    val d = (radius * 2).roundToInt()
    val path = Path().apply { addOval(Rect(centre, radius)) }
    clipPath(path) {
        drawImage(
            image,
            srcOffset = src,
            srcSize = IntSize(side, side),
            dstOffset = IntOffset((centre.x - radius).roundToInt(), (centre.y - radius).roundToInt()),
            dstSize = IntSize(d, d),
        )
    }
}

private fun DrawScope.drawCentred(measurer: androidx.compose.ui.text.TextMeasurer, text: String, at: Offset, style: TextStyle) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().padding(16.dp),
        color = scheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
    ) {
        FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(scheme.primary, null, stringResource(Res.string.network_legend_family))
            LegendItem(scheme.tertiary, floatArrayOf(6f, 4f), stringResource(Res.string.network_legend_partner))
            LegendItem(scheme.outlineVariant, null, stringResource(Res.string.network_legend_friend))
            LegendItem(scheme.outline, floatArrayOf(2f, 4f), stringResource(Res.string.network_legend_introduced))
        }
    }
}

@Composable
private fun LegendItem(color: Color, dash: FloatArray?, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 20.dp, height = 12.dp)) {
            val effect = dash?.let { d -> PathEffect.dashPathEffect(FloatArray(d.size) { d[it] * density }) }
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx(), pathEffect = effect)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
