package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    /** Google Drive backup is set up for this build (an OAuth client exists for it; see docs/GOOGLE_DRIVE.md). */
    val hasGoogleDrive: Boolean = false,
    /** A home-screen widget exists for this platform. */
    val hasHomeWidget: Boolean = false,
)

expect val platformCapabilities: PlatformCapabilities

/**
 * [platformCapabilities] adjusted for the build that's actually installed. On Android, the Play build has no
 * READ_CONTACTS or READ_CALL_LOG (only the sideload build does), so address-book sync and call matching are off
 * there and people are added with the contact picker. Prefer this in composables.
 */
@Composable
expect fun currentPlatformCapabilities(): PlatformCapabilities

/**
 * Returns a launcher that asks the OS for [permission] and reports whether it was granted.
 * Already-granted permissions report `true` without showing UI.
 */
@Composable
expect fun rememberPermissionRequester(permission: AppPermission, onResult: (granted: Boolean) -> Unit): () -> Unit

/**
 * Returns a check of whether the app may post notifications right now (the user can change it in system settings at
 * any time). Always false on the web, which has no background notifications.
 */
@Composable
expect fun rememberNotificationsAllowedCheck(): suspend () -> Boolean

/**
 * Whether the app may post notifications: null until the first check, then re-checked each time the screen resumes
 * and whenever [refreshKey] changes (bump it after asking for the permission).
 */
@Composable
fun rememberNotificationsAllowed(refreshKey: Any? = null): Boolean? {
    val check = rememberNotificationsAllowedCheck()
    var allowed by remember { mutableStateOf<Boolean?>(null) }
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    LaunchedEffect(resumes, refreshKey) { allowed = check() }
    return allowed
}

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
 * user picks, so no contacts permission is involved. iOS and Android 17+ (the system Contact Picker) allow several
 * people at once; older Android versions pick one at a time. It picks people, never a specific phone number.
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

/** Outcome of asking the user to let the app use its own file in their Google Drive. */
sealed interface DriveAuthResult {
    data class Token(val accessToken: String) : DriveAuthResult

    /** The user closed the Google sheet. */
    data object Cancelled : DriveAuthResult

    /** No OAuth client for this build (Google Cloud project not set up): see docs/GOOGLE_DRIVE.md. */
    data object NotConfigured : DriveAuthResult

    data class Failed(val detail: String?) : DriveAuthResult
}

/**
 * Returns a launcher that asks Google for an access token with the `drive.file` scope (shows Google's consent the
 * first time). Android: Play services authorization; iOS: Google sign-in in the system browser sheet (PKCE);
 * web: Google Identity Services.
 */
@Composable
expect fun rememberGoogleDriveAuthorizer(onResult: (DriveAuthResult) -> Unit): () -> Unit
