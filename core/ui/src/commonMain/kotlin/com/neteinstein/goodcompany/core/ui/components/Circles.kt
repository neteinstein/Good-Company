package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.model.FriendGroup
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.circle_color_amber
import com.neteinstein.goodcompany.core.ui.resources.circle_color_blue
import com.neteinstein.goodcompany.core.ui.resources.circle_color_magenta
import com.neteinstein.goodcompany.core.ui.resources.circle_color_purple
import com.neteinstein.goodcompany.core.ui.resources.circle_color_rust
import com.neteinstein.goodcompany.core.ui.resources.circle_color_slate
import com.neteinstein.goodcompany.core.ui.resources.circle_color_teal
import org.jetbrains.compose.resources.stringResource

/**
 * A circle colour in the current theme: [accent] for dots, swatches and the circle's main button, [container] /
 * [onContainer] for a selected chip or the circle card, [onAccent] for text on [accent].
 */
@Immutable
data class CirclePalette(val accent: Color, val onAccent: Color, val container: Color, val onContainer: Color)

private class CircleTones(val accent: Color, val lightContainer: Color, val lightOnContainer: Color, val darkAccent: Color, val darkContainer: Color)

// One row per CircleColor: light accent, light container, text on it, dark accent, dark container. Checked for
// legible contrast in both themes; text on a dark container is the light container colour.
private fun tones(color: CircleColor): CircleTones = when (color) {
    CircleColor.PURPLE -> CircleTones(Color(0xFF6B46D9), Color(0xFFECE6FB), Color(0xFF3A2185), Color(0xFFB9A6F5), Color(0xFF3B2A72))
    CircleColor.BLUE -> CircleTones(Color(0xFF2F6FDB), Color(0xFFE2ECFB), Color(0xFF14408A), Color(0xFF9EC0F5), Color(0xFF1B3866))
    CircleColor.TEAL -> CircleTones(Color(0xFF0E7D66), Color(0xFFDDF2EC), Color(0xFF064A3C), Color(0xFF7FD3BE), Color(0xFF0F433A))
    CircleColor.AMBER -> CircleTones(Color(0xFFB86B0A), Color(0xFFFBEBD5), Color(0xFF6B3D00), Color(0xFFF3BE73), Color(0xFF55380F))
    CircleColor.RUST -> CircleTones(Color(0xFFC2461C), Color(0xFFFBE4DA), Color(0xFF7A2A0C), Color(0xFFF4A585), Color(0xFF5E2A17))
    CircleColor.MAGENTA -> CircleTones(Color(0xFFB5207A), Color(0xFFF9E1EF), Color(0xFF6E0E49), Color(0xFFF09CCB), Color(0xFF5A1B41))
    CircleColor.SLATE -> CircleTones(Color(0xFF5C6B78), Color(0xFFE6EAEE), Color(0xFF2F3A44), Color(0xFFB4C1CC), Color(0xFF34404A))
}

/** The circle colour for the current light or dark scheme (dynamic colour doesn't change it). */
@Composable
fun circlePalette(color: CircleColor): CirclePalette {
    val t = tones(color)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (dark) {
        CirclePalette(accent = t.darkAccent, onAccent = t.darkContainer, container = t.darkContainer, onContainer = t.lightContainer)
    } else {
        CirclePalette(accent = t.accent, onAccent = Color.White, container = t.lightContainer, onContainer = t.lightOnContainer)
    }
}

/** The colour's name, for screen readers (swatches have no text). */
@Composable
fun circleColorName(color: CircleColor): String = stringResource(
    when (color) {
        CircleColor.PURPLE -> Res.string.circle_color_purple
        CircleColor.BLUE -> Res.string.circle_color_blue
        CircleColor.TEAL -> Res.string.circle_color_teal
        CircleColor.AMBER -> Res.string.circle_color_amber
        CircleColor.RUST -> Res.string.circle_color_rust
        CircleColor.MAGENTA -> Res.string.circle_color_magenta
        CircleColor.SLATE -> Res.string.circle_color_slate
    },
)

/** A small dot in the circle's colour. */
@Composable
fun CircleDot(color: CircleColor, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).background(circlePalette(color).accent, CircleShape))
}

/**
 * A circle as a chip: colour dot, emoji and name. Selected = filled with the circle's colour, plus [trailing] (e.g.
 * " · 16" members). [onLongClick] opens the circle editor where that's offered.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CircleChip(
    group: FriendGroup,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    showDot: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val palette = circlePalette(group.color)
    Surface(
        modifier = modifier
            .heightIn(min = 36.dp)
            .semantics { this.selected = selected }
            .combinedClickable(role = Role.Checkbox, onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.small,
        color = if (selected) palette.container else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) palette.onContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (showDot) CircleDot(group.color, size = 8.dp)
            Text(
                listOfNotNull("${group.emoji} ${group.name}", trailing).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A round colour swatch for the circle editor; the selected one gets a ring and a tick. */
@Composable
fun CircleColorSwatch(color: CircleColor, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = circlePalette(color)
    val name = circleColorName(color)
    Box(
        modifier
            .size(44.dp)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .padding(4.dp)
            .background(palette.accent, CircleShape)
            .semantics {
                contentDescription = name
                this.selected = selected
            }
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = palette.onAccent, modifier = Modifier.size(20.dp))
    }
}
