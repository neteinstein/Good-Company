package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/*
 * UI-side platform hooks. Only things Compose Multiplatform genuinely can't share live here:
 * runtime permission prompts, the system file picker, the share sheet and wallpaper-based dynamic colour.
 */

enum class AppPermission { CONTACTS, LOCATION, NOTIFICATIONS, CALL_LOG }

/** What this platform can do, so the UI can adapt copy and hide impossible actions. */
data class PlatformCapabilities(
    val name: String,
    val hasAddressBook: Boolean,
    val hasCallLog: Boolean,
    val hasBackgroundNotifications: Boolean,
    val hasDynamicColor: Boolean,
    /** Separator before `body=` in `sms:` links (`?` on Android/web, `&` on iOS). */
    val smsBodySeparator: String = "?",
)

expect val platformCapabilities: PlatformCapabilities

/**
 * Returns a launcher that asks the OS for [permission] and reports whether it was granted.
 * Already-granted permissions report `true` without showing UI.
 */
@Composable
expect fun rememberPermissionRequester(permission: AppPermission, onResult: (granted: Boolean) -> Unit): () -> Unit

/** What kind of file the picker should offer. */
enum class FileKind { TEXT, VCARD }

/** Returns a launcher for the system document picker that reads a text file (null if cancelled/unreadable). */
@Composable
expect fun rememberTextFilePicker(kind: FileKind = FileKind.TEXT, onPicked: (text: String?) -> Unit): () -> Unit

/** Result of [TextSharer.share]. */
enum class ShareOutcome { SHARE_SHEET, COPIED }

/** Opens the system share sheet with plain text (web without Web Share: copies to the clipboard instead). */
fun interface TextSharer {
    fun share(text: String): ShareOutcome
}

@Composable
expect fun rememberTextSharer(): TextSharer

/** Wallpaper-derived colours where supported (Android 12+), otherwise null. */
@Composable
expect fun platformDynamicColorScheme(dark: Boolean): ColorScheme?
