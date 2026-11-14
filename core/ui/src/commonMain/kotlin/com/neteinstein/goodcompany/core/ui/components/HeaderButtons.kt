package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.back
import com.neteinstein.goodcompany.core.ui.resources.open_settings
import org.jetbrains.compose.resources.stringResource

/**
 * Your own photo (issue #71), provided once at the app root from its ViewModel; null while none is set (and in previews
 * and tests). Read by the tab headers, the network map and the year in review.
 */
val LocalMyPhoto = compositionLocalOf<ContactPhoto?> { null }

/**
 * The top-right button of every tab header (issue #65): opens Settings, which left the bottom bar. Shows your photo
 * ([LocalMyPhoto], issue #71) once there is one, else a gear (also when the photo can't be decoded).
 */
@Composable
fun HeaderSettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val photo = rememberPhotoBitmap(LocalMyPhoto.current)
    val label = stringResource(Res.string.open_settings)
    if (photo != null) {
        IconButton(onClick = onClick, modifier = modifier) {
            Image(photo, contentDescription = label, modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        }
    } else {
        FilledTonalIconButton(onClick = onClick, modifier = modifier) {
            Icon(Icons.Rounded.Settings, label)
        }
    }
}

/** The back arrow for [ScreenHeader]'s `navigation` slot. */
@Composable
fun HeaderBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.back)) }
}
