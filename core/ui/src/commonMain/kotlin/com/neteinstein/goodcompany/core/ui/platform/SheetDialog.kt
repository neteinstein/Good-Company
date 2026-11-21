package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.ui.window.DialogProperties

/**
 * Issue #145: dialog properties for a destination that draws its own bottom sheet ([DialogSheet]). Full width, edge to
 * edge (the scrim covers the system bars and the sheet sits flush with the bottom of the screen; the sheet pads its
 * content with the insets itself) and no platform scrim, since the sheet draws its own.
 */
expect val sheetDialogProperties: DialogProperties
