package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neteinstein.goodcompany.core.ui.theme.AvatarGradients
import kotlin.math.absoluteValue

/** Initials on a stable per-name gradient; close friends get an animated accent ring. */
@Composable
fun FriendAvatar(
    name: String,
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    highlighted: Boolean = false,
    sharedKey: String? = null,
) {
    val (start, end) = AvatarGradients[name.hashCode().absoluteValue % AvatarGradients.size]
    val ring by animateDpAsState(if (highlighted) 2.5.dp else 0.dp)
    Box(
        modifier = modifier
            .sharedElement(sharedKey)
            .size(size)
            .border(ring, MaterialTheme.colorScheme.primary, CircleShape)
            .padding(ring + if (highlighted) 2.dp else 0.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(start, end))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}
