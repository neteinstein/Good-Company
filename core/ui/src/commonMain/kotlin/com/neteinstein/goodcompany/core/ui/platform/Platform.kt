package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/*
 * UI-side platform hooks. Only things Compose Multiplatform genuinely can't share live here:
 * runtime permission prompts, the system file picker and wallpaper-based dynamic colour.
 */

enum class AppPermission { CONTACTS, LOCATION, NOTIFICATIONS, CALL_LOG }

/** What this platform can do, so the UI can adapt copy and hide impossible actions. */
data class PlatformCapabilities(
    val name: String,
    val hasAddressBook: Boolean,
    val hasCallLog: Boolean,
    val hasBackgroundNotifications: Boolean,
    val hasDynamicColor: Boolean,
)

expect val platformCapabilities: PlatformCapabilities

/**
 * Returns a launcher that asks the OS for [permission] and reports whether it was granted.
 * Already-granted permissions report `true` without showing UI.
 */
@Composable
expect fun rememberPermissionRequester(permission: AppPermission, onResult: (granted: Boolean) -> Unit): () -> Unit

/** Returns a launcher for the system document picker that reads a plain-text file (null if cancelled/unreadable). */
@Composable
expect fun rememberTextFilePicker(onPicked: (text: String?) -> Unit): () -> Unit

/** Wallpaper-derived colours where supported (Android 12+), otherwise null. */
@Composable
expect fun platformDynamicColorScheme(dark: Boolean): ColorScheme?
