package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.DialogProperties

// The sheet handles the safe area itself and draws its own scrim (issue #145).
actual val sheetDialogProperties = DialogProperties(usePlatformDefaultWidth = false, usePlatformInsets = false, scrimColor = Color.Transparent)
