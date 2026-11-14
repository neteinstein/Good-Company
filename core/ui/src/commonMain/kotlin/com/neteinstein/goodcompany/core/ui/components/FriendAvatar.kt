package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.ui.theme.AvatarGradients
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.math.absoluteValue

/**
 * The friend's contact [photo] when they have one, otherwise initials on a stable per-name gradient (also shown when
 * the photo can't be decoded). Close friends get an animated accent ring.
 */
@Composable
fun FriendAvatar(
    name: String,
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    highlighted: Boolean = false,
    sharedKey: String? = null,
    photo: ContactPhoto? = null,
) {
    val (start, end) = avatarColors(name)
    val image = rememberPhotoBitmap(photo)
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
        if (image != null) {
            Image(image, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                text = initials,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp,
            )
        }
    }
}

/** A friend's avatar. Prefer this over passing name, initials and photo one by one. */
@Composable
fun FriendAvatar(
    friend: Friend,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    highlighted: Boolean = false,
    sharedKey: String? = null,
) = FriendAvatar(friend.name, friend.initials, modifier, size, highlighted, sharedKey, friend.photo)

/** Decoded once per photo; null (so initials show) when there's none or it isn't a readable image. */
@Composable
fun rememberPhotoBitmap(photo: ContactPhoto?): ImageBitmap? = remember(photo) {
    photo?.let { runCatching { it.bytes().takeIf(ByteArray::isNotEmpty)?.decodeToImageBitmap() }.getOrNull() }
}

/** The stable gradient pair for [name] — reuse it to tint a friend's screen so it feels like "theirs". */
fun avatarColors(name: String): Pair<Color, Color> = AvatarGradients[name.hashCode().absoluteValue % AvatarGradients.size]

/** [avatarColors] as ARGB ints, for drawing outside Compose (Android app-icon shortcut icons). */
fun avatarColorsArgb(name: String): Pair<Int, Int> = avatarColors(name).let { (a, b) -> a.toArgb() to b.toArgb() }
