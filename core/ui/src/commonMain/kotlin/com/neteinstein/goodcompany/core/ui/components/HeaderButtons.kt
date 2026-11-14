package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.back
import com.neteinstein.goodcompany.core.ui.resources.open_settings
import org.jetbrains.compose.resources.stringResource

/**
 * The top-right button of every tab header (issue #65): opens Settings, which left the bottom bar. A gear for now;
 * "Your photo" (issue #71) takes its place once there is one.
 */
@Composable
fun HeaderSettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Rounded.Settings, stringResource(Res.string.open_settings))
    }
}

/** The back arrow for [ScreenHeader]'s `navigation` slot. */
@Composable
fun HeaderBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Res.string.back)) }
}
