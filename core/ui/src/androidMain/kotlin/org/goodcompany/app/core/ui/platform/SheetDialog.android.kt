package org.goodcompany.app.core.ui.platform

import androidx.compose.ui.window.DialogProperties

// Without decorFitsSystemWindows = false the dialog window stops above the navigation bar (issue #145).
actual val sheetDialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
