package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.CircleColor
import com.neteinstein.goodcompany.core.domain.usecase.Warmth
import com.neteinstein.goodcompany.core.ui.l10n.warmthLabel
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.legend_dots
import com.neteinstein.goodcompany.core.ui.resources.legend_no_ring
import com.neteinstein.goodcompany.core.ui.resources.legend_ring
import org.jetbrains.compose.resources.stringResource

/**
 * What the colours in the People list mean (issue #120): the ring around a photo is warmth (warm, cooling, quiet) for
 * people you keep track of, no ring otherwise; one dot after a name per circle, in the circle's colour. Shown in the
 * dashboard and once as a tip on People.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeopleLegend(modifier: Modifier = Modifier, showWarmth: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showWarmth) {
            LegendLine(stringResource(Res.string.legend_ring))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Warmth.Level.entries.forEach { level ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring(warmthColor(level))
                        Spacer(Modifier.width(6.dp))
                        Text(warmthLabel(level), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            LegendLine(stringResource(Res.string.legend_no_ring))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(CircleColor.TEAL, CircleColor.AMBER, CircleColor.PURPLE).forEach { CircleDot(it, size = 8.dp) }
            }
            Spacer(Modifier.width(8.dp))
            LegendLine(stringResource(Res.string.legend_dots))
        }
    }
}

@Composable
private fun LegendLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Ring(color: Color) {
    Box(Modifier.size(16.dp).border(2.5.dp, color, CircleShape).padding(2.dp))
}
