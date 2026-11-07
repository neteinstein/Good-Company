package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import kotlinx.datetime.LocalDate

/*
 * UI-side platform hooks. Only things Compose Multiplatform genuinely can't share live here:
 * runtime permission prompts, the system file and contact pickers, the share sheet, saving a file,
 * "add to calendar", the device lock (biometrics / PIN) and wallpaper-based dynamic colour.
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
    /** The system contact picker (no address-book permission needed). */
    val hasContactPicker: Boolean = false,
    /** A home-screen widget exists for this platform. */
    val hasHomeWidget: Boolean = false,
)

expect val platformCapabilities: PlatformCapabilities

/**
 * Returns a launcher that asks the OS for [permission] and reports whether it was granted.
 * Already-granted permissions report `true` without showing UI.
 */
@Composable
expect fun rememberPermissionRequester(permission: AppPermission, onResult: (granted: Boolean) -> Unit): () -> Unit

/** What kind of file the picker should offer. */
enum class FileKind { TEXT, VCARD, JSON }

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

/**
 * Returns a launcher for the system contact picker. The OS shows the address book and hands back only the people the
 * user picks, so no contacts permission is involved. Android picks one person at a time; iOS allows several.
 * Reports an empty list when cancelled.
 */
@Composable
expect fun rememberContactPicker(onPicked: (List<ContactRecord>) -> Unit): () -> Unit

/** An all-day event to hand to the system calendar. The user reviews and saves it there. */
data class CalendarEntry(val title: String, val date: LocalDate, val location: String? = null, val notes: String? = null)

/**
 * Returns a launcher that opens the system "new event" screen prefilled with an entry (Android calendar intent,
 * iOS event editor — neither needs calendar permission). The web downloads an .ics file instead.
 */
@Composable
expect fun rememberCalendarAdder(): (CalendarEntry) -> Unit

/** Saves a text file where the user chooses (Android "Save to", iOS share sheet / Files, web download). */
fun interface FileSaver {
    fun save(fileName: String, content: String)
}

/** [onDone] reports whether the file was handed over (false when the user cancelled, where the platform can tell). */
@Composable
expect fun rememberFileSaver(mimeType: String, onDone: (saved: Boolean) -> Unit = {}): FileSaver

/** The device's own lock: fingerprint, face or PIN. */
interface DeviceAuthenticator {
    /** False when the device has no lock set up (or on the web): the app-lock option is then hidden. */
    val isAvailable: Boolean

    fun authenticate(reason: String, onResult: (success: Boolean) -> Unit)
}

@Composable
expect fun rememberDeviceAuthenticator(): DeviceAuthenticator
