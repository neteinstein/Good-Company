package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.review_card_body
import com.neteinstein.goodcompany.core.ui.resources.review_card_cta
import com.neteinstein.goodcompany.core.ui.resources.review_card_title
import com.neteinstein.goodcompany.core.ui.theme.OnColorGradients
import org.jetbrains.compose.resources.stringResource

/** "Your <year> in friendship": opens the year in review. Shown on Today and Plans in December (issue #67). */
@Composable
fun YearReviewCard(year: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
    ) {
        Row(
            Modifier.background(Brush.linearGradient(OnColorGradients.Indigo)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✨", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.review_card_title, year), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(stringResource(Res.string.review_card_body), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(Res.string.review_card_cta), tint = Color.White)
        }
    }
}
