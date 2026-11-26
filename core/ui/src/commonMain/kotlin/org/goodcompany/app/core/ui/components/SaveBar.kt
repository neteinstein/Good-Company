package org.goodcompany.app.core.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import org.goodcompany.app.core.ui.resources.Res
import org.goodcompany.app.core.ui.resources.save
import org.jetbrains.compose.resources.stringResource

/**
 * The one Save of a form (issue #146): a bar pinned to the bottom with a full-width primary button, padded with the
 * navigation bar and the keyboard so it is never drawn under either (edge to edge, gotcha 12). Full-screen editors put
 * it in their `Scaffold`'s `bottomBar` (Delete stays in the top bar); sheets with text fields put it under their
 * scrolling fields, never at the end of the scroll ([DialogSheet]'s `bottomBar`, or after a `weight(1f, fill = false)`
 * column in a `ModalBottomSheet`).
 *
 * The insets are read consumption-aware, so a parent that already pads for the keyboard or the navigation bar (a
 * sheet's `imePadding`) isn't counted twice.
 */
@Composable
fun SaveBar(
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = stringResource(Res.string.save),
    color: Color = MaterialTheme.colorScheme.background,
) {
    val haptics = LocalHapticFeedback.current
    Surface(modifier.fillMaxWidth(), color = color) {
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onSave()
            },
            enabled = enabled,
            modifier = Modifier.windowInsetsPadding(SaveBarDefaults.insets).padding(horizontal = 20.dp, vertical = 12.dp)
                .fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(label) }
    }
}

object SaveBarDefaults {
    /** What the bar keeps clear of: the navigation bar, or the keyboard while it is open (it covers the bar). */
    val insets: WindowInsets
        @Composable get() = WindowInsets.navigationBars.union(WindowInsets.ime)
}
