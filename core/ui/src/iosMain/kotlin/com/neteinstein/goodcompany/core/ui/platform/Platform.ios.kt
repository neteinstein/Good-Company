package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import platform.Contacts.CNContactStore
import platform.Contacts.CNEntityType
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.UniformTypeIdentifiers.UTTypeText
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject

actual val platformCapabilities = PlatformCapabilities(
    name = "iOS",
    hasAddressBook = true,
    hasCallLog = false,
    hasBackgroundNotifications = true,
    hasDynamicColor = false,
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
actual fun rememberTextFilePicker(onPicked: (text: String?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val delegate = remember { PickerDelegate { callback.value(it) } }
    DisposableEffect(Unit) { onDispose { delegate.onPicked = {} } }
    return {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePlainText, UTTypeText))
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

private fun topViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? = null
