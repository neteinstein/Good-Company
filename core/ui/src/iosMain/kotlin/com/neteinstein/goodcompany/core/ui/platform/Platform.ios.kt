package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import platform.Contacts.CNContact
import platform.Contacts.CNContactStore
import platform.Contacts.CNContactThumbnailImageDataKey
import platform.Contacts.CNEntityType
import platform.Contacts.CNLabeledValue
import platform.Contacts.CNPhoneNumber
import platform.ContactsUI.CNContactPickerDelegateProtocol
import platform.ContactsUI.CNContactPickerViewController
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKitUI.EKEventEditViewAction
import platform.EventKitUI.EKEventEditViewController
import platform.EventKitUI.EKEventEditViewDelegateProtocol
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponentUndefined
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToFile
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UniformTypeIdentifiers.UTTypeText
import platform.UniformTypeIdentifiers.UTTypeVCard
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

actual val platformCapabilities = PlatformCapabilities(
    name = "iOS",
    hasAddressBook = true,
    hasCallLog = false,
    hasBackgroundNotifications = true,
    hasDynamicColor = false,
    smsBodySeparator = "&",
    hasContactPicker = true,
    hasGoogleDrive = googleDriveClientId != null,
)

@Composable
actual fun rememberPermissionRequester(permission: AppPermission, onResult: (granted: Boolean) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onResult)

    // iOS calls back on a background queue; hop to Main before touching Compose state.
    fun report(granted: Boolean) {
        scope.launch(Dispatchers.Main) { callback.value(granted) }
    }
    return {
        when (permission) {
            AppPermission.CONTACTS ->
                CNContactStore().requestAccessForEntityType(CNEntityType.CNEntityTypeContacts) { granted, _ -> report(granted) }
            AppPermission.NOTIFICATIONS ->
                UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                    UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
                ) { granted, _ -> report(granted) }
            // CLLocationManager prompts on first use inside the location provider itself.
            AppPermission.LOCATION -> report(true)
            AppPermission.CALL_LOG -> report(false)
        }
    }
}

@Composable
actual fun rememberNotificationsAllowedCheck(): suspend () -> Boolean = remember<suspend () -> Boolean> {
    {
        suspendCoroutine { cont ->
            UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
                val status = settings?.authorizationStatus
                cont.resume(
                    status == UNAuthorizationStatusAuthorized ||
                        status == UNAuthorizationStatusProvisional ||
                        status == UNAuthorizationStatusEphemeral,
                )
            }
        }
    }
}

@Composable
actual fun rememberTextFilePicker(kind: FileKind, onPicked: (text: String?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val delegate = remember { PickerDelegate { callback.value(it) } }
    DisposableEffect(Unit) { onDispose { delegate.onPicked = {} } }
    return {
        val types = when (kind) {
            FileKind.TEXT -> listOf(UTTypePlainText, UTTypeText)
            FileKind.VCARD -> listOf(UTTypeVCard, UTTypeText)
            FileKind.JSON -> listOf(UTTypeJSON, UTTypeText)
        }
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)
        picker.delegate = delegate
        picker.allowsMultipleSelection = false
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private class PickerDelegate(var onPicked: (String?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        val text = url?.let {
            val scoped = it.startAccessingSecurityScopedResource()
            try {
                NSString.stringWithContentsOfURL(it, NSUTF8StringEncoding, null)
            } finally {
                if (scoped) it.stopAccessingSecurityScopedResource()
            }
        }
        onPicked(text)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = onPicked(null)
}

internal fun keyWindow(): UIWindow? = UIApplication.sharedApplication.connectedScenes
    .filterIsInstance<UIWindowScene>()
    .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
    .firstOrNull { it.isKeyWindow() }

internal fun topViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

@Composable
actual fun rememberTextSharer(): TextSharer = remember {
    TextSharer { text ->
        val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        val top = topViewController()
        // iPad needs an anchor for the popover.
        controller.popoverPresentationController?.sourceView = top?.view
        top?.presentViewController(controller, animated = true, completion = null)
        ShareOutcome.SHARE_SHEET
    }
}

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? = null

@Composable
actual fun rememberContactPicker(onPicked: (List<ContactRecord>) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val delegate = remember { ContactPickerDelegate { callback.value(it) } }
    DisposableEffect(Unit) { onDispose { delegate.onPicked = {} } }
    return {
        val picker = CNContactPickerViewController()
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}

/** Implementing only the plural callback makes the picker multi-select. Picked contacts need no permission. */
private class ContactPickerDelegate(var onPicked: (List<ContactRecord>) -> Unit) : NSObject(), CNContactPickerDelegateProtocol {
    override fun contactPicker(picker: CNContactPickerViewController, didSelectContacts: List<*>) {
        onPicked(didSelectContacts.filterIsInstance<CNContact>().mapNotNull { it.toRecord() })
    }

    override fun contactPickerDidCancel(picker: CNContactPickerViewController) = onPicked(emptyList())
}

private fun CNContact.toRecord(): ContactRecord? = runCatching {
    val name = listOf(givenName, familyName).filter { it.isNotBlank() }.joinToString(" ")
    if (name.isBlank()) return@runCatching null
    val phones = phoneNumbers.mapNotNull { ((it as? CNLabeledValue)?.value as? CNPhoneNumber)?.stringValue }
    val b = birthday
    val birthday = if (b != null && b.month in 1..12 && b.day in 1..31) {
        Birthday(b.month.toInt(), b.day.toInt(), b.year.takeIf { it != NSDateComponentUndefined }?.toInt())
    } else {
        null
    }
    ContactRecord(identifier, name, phones, birthday, thumbnail())
}.getOrNull()

/** The picked contact's thumbnail. The picker hands over what it showed, so check the key before reading it. */
@OptIn(ExperimentalForeignApi::class)
private fun CNContact.thumbnail(): ContactPhoto? {
    // Reading a key that wasn't fetched raises an Objective-C exception, which runCatching can't catch.
    if (!isKeyAvailable(CNContactThumbnailImageDataKey)) return null
    val data = thumbnailImageData ?: return null
    val size = data.length.toInt()
    if (size == 0 || size > ContactPhoto.MAX_BYTES) return null
    val bytes = ByteArray(size)
    bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
    return ContactPhoto.of(bytes)
}

@Composable
actual fun rememberCalendarAdder(): (CalendarEntry) -> Unit {
    val delegate = remember { EventEditDelegate() }
    return remember {
        { entry ->
            // iOS 17+: the system event editor runs out of process and needs no calendar permission.
            val store = EKEventStore()
            val event = EKEvent.eventWithEventStore(store)
            val start = entry.date.atStartOfDayIn(TimeZone.currentSystemDefault()).epochSeconds.toDouble()
            event.title = entry.title
            event.allDay = true
            event.startDate = NSDate.dateWithTimeIntervalSince1970(start)
            event.endDate = NSDate.dateWithTimeIntervalSince1970(start)
            event.location = entry.location
            event.notes = entry.notes
            val editor = EKEventEditViewController()
            editor.eventStore = store
            editor.event = event
            editor.editViewDelegate = delegate
            topViewController()?.presentViewController(editor, animated = true, completion = null)
        }
    }
}

private class EventEditDelegate : NSObject(), EKEventEditViewDelegateProtocol {
    override fun eventEditViewController(controller: EKEventEditViewController, didCompleteWithAction: EKEventEditViewAction) {
        controller.dismissViewControllerAnimated(true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun rememberFileSaver(mimeType: String, onDone: (saved: Boolean) -> Unit): FileSaver {
    val callback = rememberUpdatedState(onDone)
    return remember {
        FileSaver { fileName, content ->
            val path = NSTemporaryDirectory() + fileName

            @Suppress("CAST_NEVER_SUCCEEDS")
            val written = (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
            if (!written) {
                callback.value(false)
                return@FileSaver
            }
            val controller = UIActivityViewController(activityItems = listOf(NSURL.fileURLWithPath(path)), applicationActivities = null)
            val top = topViewController()
            controller.popoverPresentationController?.sourceView = top?.view
            controller.completionWithItemsHandler = { _, completed, _, _ -> callback.value(completed) }
            top?.presentViewController(controller, animated = true, completion = null)
        }
    }
}

@Composable
actual fun rememberDeviceAuthenticator(): DeviceAuthenticator {
    val scope = rememberCoroutineScope()
    return remember { IosDeviceAuthenticator { block -> scope.launch(Dispatchers.Main) { block() } } }
}

private class IosDeviceAuthenticator(private val onMain: (() -> Unit) -> Unit) : DeviceAuthenticator {
    @OptIn(ExperimentalForeignApi::class)
    override val isAvailable: Boolean
        get() = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)

    override fun authenticate(reason: String, onResult: (success: Boolean) -> Unit) {
        LAContext().evaluatePolicy(LAPolicyDeviceOwnerAuthentication, localizedReason = reason) { success, _ ->
            onMain { onResult(success) }
        }
    }
}

@Composable
actual fun currentPlatformCapabilities(): PlatformCapabilities = platformCapabilities

@Composable
actual fun rememberAppUpdater(): AppUpdater = NoAppUpdater
