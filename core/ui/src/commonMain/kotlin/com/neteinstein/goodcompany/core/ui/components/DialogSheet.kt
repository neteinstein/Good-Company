package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A bottom sheet drawn inside a dialog destination (issue #145), for the Log sheet and the "We met" panel: a scrim that
 * dismisses it on a tap, a handle, and a sheet that follows a downward drag (on the sheet, or on its content once that
 * is scrolled to the top) and closes when let go far or fast enough. Host it with
 * [com.neteinstein.goodcompany.core.ui.platform.sheetDialogProperties] so it draws edge to edge: the scrim covers the
 * system bars and the sheet sits flush with the bottom of the screen, its content padded with the navigation-bar inset.
 *
 * Unlike Material's `ModalBottomSheet` it opens no window of its own, so it works the same in a dialog on Android, iOS
 * and the web.
 */
@Composable
fun DialogSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    val density = LocalDensity.current
    val velocityThreshold = with(density) { SheetDrag.VELOCITY_THRESHOLD.toPx() }
    val positionalThreshold = with(density) { SheetDrag.POSITIONAL_THRESHOLD.toPx() }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    var height by remember { mutableIntStateOf(0) }
    var offset by remember { mutableFloatStateOf(0f) }
    // Set while the sheet animates back open or away, so a second settle doesn't fight the first.
    var settling by remember { mutableStateOf(false) }

    fun dragBy(delta: Float): Float {
        val next = (offset + delta).coerceIn(0f, height.toFloat().coerceAtLeast(0f))
        val used = next - offset
        offset = next
        return used
    }

    suspend fun settle(velocity: Float) {
        if (settling) return
        settling = true
        if (SheetDrag.shouldDismiss(offset, height.toFloat(), velocity, positionalThreshold, velocityThreshold)) {
            animate(offset, height.toFloat(), initialVelocity = velocity) { value, _ -> offset = value }
            dismiss()
        } else {
            animate(offset, 0f, initialVelocity = velocity) { value, _ -> offset = value }
            settling = false
        }
    }

    val nestedScroll = remember {
        object : NestedScrollConnection {
            // Dragging up while the sheet is pulled down lifts the sheet back before the content scrolls.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (available.y < 0 && offset > 0f && source == NestedScrollSource.UserInput) Offset(0f, dragBy(available.y)) else Offset.Zero

            // Dragging down with the content at the top pulls the sheet down.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (available.y > 0 && source == NestedScrollSource.UserInput) Offset(0f, dragBy(available.y)) else Offset.Zero

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }
    // A scroll with no fling (a mouse wheel, a slow drag) still has to land open or closed.
    LaunchedEffect(scroll.isScrollInProgress) {
        if (!scroll.isScrollInProgress && offset > 0f) settle(0f)
    }

    val progress = if (height > 0) 1f - offset / height else 1f
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f * progress))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { dismiss() }),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            Modifier.statusBarsPadding().padding(top = 24.dp).widthIn(max = 640.dp).fillMaxWidth().imePadding()
                .onSizeChanged { height = it.height }
                // Before the offset, so the drag is measured in coordinates that don't move with the sheet.
                .draggable(
                    state = rememberDraggableState { if (!settling) dragBy(it) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity -> scope.launch { settle(velocity) } },
                )
                .offset { IntOffset(0, offset.roundToInt()) }
                // Taps on the sheet itself must not reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .nestedScroll(nestedScroll),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            SheetColumn(scroll, content)
        }
    }
}

@Composable
private fun SheetColumn(scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.verticalScroll(scroll).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
        }
        content()
    }
}

/** When a let-go [DialogSheet] closes (issue #145). Pure, so it can be tested. */
object SheetDrag {
    /** A downward fling faster than this closes the sheet, wherever it is. */
    val VELOCITY_THRESHOLD = 600.dp

    /** Pulled down further than this (or than a third of the sheet, if that is less) closes it. */
    val POSITIONAL_THRESHOLD = 120.dp

    fun shouldDismiss(offset: Float, height: Float, velocity: Float, positionalThreshold: Float, velocityThreshold: Float): Boolean = when {
        offset <= 0f || height <= 0f -> false
        velocity >= velocityThreshold -> true
        velocity <= -velocityThreshold -> false
        else -> offset >= minOf(positionalThreshold, height / 3f)
    }
}
