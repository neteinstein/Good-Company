package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto

/**
 * A wide cover picture (cropped like a circle's cover, [ContactPhoto.COVER_ASPECT]) across the top of a header, fading
 * into the screen's background below it (issue #144: the background of a person's page). Give it the header's size
 * (e.g. `Modifier.matchParentSize()`) and draw the header's own gradient over it. The veil is the theme's background
 * colour, so the text on top stays readable in light and dark. Draws nothing when [cover] is null or unreadable.
 */
@Composable
fun FadedCover(cover: ContactPhoto?, modifier: Modifier = Modifier) {
    val image = rememberPhotoBitmap(cover) ?: return
    val bg = MaterialTheme.colorScheme.background
    Box(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(ContactPhoto.COVER_ASPECT).align(Alignment.TopCenter)) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to bg.copy(alpha = FADED_COVER_TOP_VEIL),
                        0.55f to bg.copy(alpha = FADED_COVER_MIDDLE_VEIL),
                        1f to bg,
                    ),
                ),
            )
        }
    }
}

/** How much of the background lies over the top of the picture: the back and ⋮ buttons sit there. */
private const val FADED_COVER_TOP_VEIL = 0.25f

/** Halfway down the picture, where the photo and name start, the veil is thicker so they stand out. */
private const val FADED_COVER_MIDDLE_VEIL = 0.55f
