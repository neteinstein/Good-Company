package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.ui.platform.PhotoCrop
import com.neteinstein.goodcompany.core.ui.platform.cropPhoto
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.crop_hint
import com.neteinstein.goodcompany.core.ui.resources.crop_title
import com.neteinstein.goodcompany.core.ui.resources.crop_use
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The frame the crop editor shows: round for a person's photo, a wide 16:9 rectangle for a circle's cover (issue #88). */
enum class CropShape(
    /** Frame width for each unit of height. */
    val aspect: Float,
    /** The size of the cropped picture, in px. */
    val outWidth: Int,
    val outHeight: Int,
) {
    ROUND(1f, ContactPhoto.MAX_SIDE_PX, ContactPhoto.MAX_SIDE_PX),
    WIDE(
        ContactPhoto.COVER_ASPECT,
        ContactPhoto.COVER_MAX_SIDE_PX,
        (ContactPhoto.COVER_MAX_SIDE_PX / ContactPhoto.COVER_ASPECT).roundToInt(),
    ),
}

/**
 * The crop editor's geometry (F14): a picture of [width] × [height] px shown under a frame [frame] px wide and
 * [frame] / [aspect] px high (round when [aspect] is 1, wide for a cover). At zoom 1 the picture just covers the frame;
 * [offsetX]/[offsetY] move it from the centre, in screen px.
 */
data class CropGeometry(val width: Int, val height: Int, val frame: Float, val aspect: Float = 1f) {
    private val frameWidth: Float get() = frame
    private val frameHeight: Float get() = frame / aspect
    private val baseScale: Float get() = max(frameWidth / width, frameHeight / height)

    /** [zoom] kept between 1 (the picture covers the frame) and [MAX_ZOOM]. */
    fun clampZoom(zoom: Float): Float = zoom.coerceIn(1f, MAX_ZOOM)

    /** [offset] kept so that the picture always covers the whole frame at [zoom]. */
    fun clampOffset(offset: Offset, zoom: Float): Offset {
        val scale = baseScale * zoom
        val maxX = ((width * scale - frameWidth) / 2).coerceAtLeast(0f)
        val maxY = ((height * scale - frameHeight) / 2).coerceAtLeast(0f)
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    /** The part of the picture, in its own pixels, that sits under the frame (a square when [aspect] is 1). */
    fun crop(offset: Offset, zoom: Float): PhotoCrop {
        val scale = baseScale * clampZoom(zoom)
        val clamped = clampOffset(offset, zoom)
        val w = (frameWidth / scale).roundToInt().coerceIn(1, width)
        val h = (frameHeight / scale).roundToInt().coerceIn(1, height)
        val centerX = width / 2f - clamped.x / scale
        val centerY = height / 2f - clamped.y / scale
        val left = (centerX - w / 2f).roundToInt().coerceIn(0, width - w)
        val top = (centerY - h / 2f).roundToInt().coerceIn(0, height - h)
        return PhotoCrop(left, top, w, h)
    }

    companion object {
        const val MAX_ZOOM = 5f
    }
}

/**
 * A full-screen editor that crops [image] to [shape]: the avatar's round frame, or a circle cover's wide one (issue
 * #88). Pinch (or scroll) to zoom, drag to move (F14). [onCropped] gets a JPEG of [CropShape.outWidth] ×
 * [CropShape.outHeight], or empty bytes when the picture can't be read.
 */
@Composable
fun PhotoCropDialog(image: ByteArray, onDismiss: () -> Unit, onCropped: (ByteArray) -> Unit, shape: CropShape = CropShape.ROUND) {
    val bitmap = remember(image) { runCatching { image.decodeToImageBitmap() }.getOrNull() }
    if (bitmap == null) {
        LaunchedEffect(image) { onCropped(ByteArray(0)) }
        return
    }
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var geometry by remember { mutableStateOf<CropGeometry?>(null) }
    var saving by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Text(
                stringResource(Res.string.crop_title),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
            )
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            val g = geometry ?: return@detectTransformGestures
                            zoom = g.clampZoom(zoom * zoomChange)
                            offset = g.clampOffset(offset + pan, zoom)
                        }
                    }
                    .pointerInput(Unit) {
                        // A mouse wheel or trackpad zooms too (the web build has no pinch on a desktop).
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val g = geometry ?: continue
                                if (event.type == PointerEventType.Scroll) {
                                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    zoom = g.clampZoom(zoom * (1f - delta * SCROLL_ZOOM_STEP))
                                    offset = g.clampOffset(offset, zoom)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                // The frame's width: as wide as fits, and short enough for its height to fit too.
                val framePx = with(density) { (min(maxWidth.value, maxHeight.value * shape.aspect) * FRAME_SHARE).dp.toPx() }
                val frameHeightPx = framePx / shape.aspect
                val g = CropGeometry(bitmap.width, bitmap.height, framePx, shape.aspect)
                if (geometry != g) geometry = g
                val scale = max(framePx / bitmap.width, frameHeightPx / bitmap.height) * zoom
                Image(
                    bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .requiredSize(with(density) { (bitmap.width * scale).toDp() }, with(density) { (bitmap.height * scale).toDp() })
                        .graphicsLayer {
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
                Canvas(Modifier.fillMaxSize()) {
                    val frameRect = androidx.compose.ui.geometry.Rect(
                        center.x - framePx / 2,
                        center.y - frameHeightPx / 2,
                        center.x + framePx / 2,
                        center.y + frameHeightPx / 2,
                    )
                    val outline = Path().apply { if (shape == CropShape.ROUND) addOval(frameRect) else addRect(frameRect) }
                    clipPath(outline, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = SCRIM_ALPHA)) }
                    drawPath(outline, Color.White, style = Stroke(width = 2.dp.toPx()))
                }
                Text(
                    stringResource(Res.string.crop_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel), color = Color.White) }
                Box {
                    Button(
                        enabled = !saving,
                        onClick = {
                            val g = geometry ?: return@Button
                            saving = true
                            scope.launch {
                                onCropped(cropPhoto(image, g.crop(offset, zoom), shape.outWidth, shape.outHeight) ?: ByteArray(0))
                            }
                        },
                    ) { Text(stringResource(Res.string.crop_use)) }
                }
            }
        }
    }
}

private const val FRAME_SHARE = 0.8f
private const val SCRIM_ALPHA = 0.6f
private const val SCROLL_ZOOM_STEP = 0.1f
