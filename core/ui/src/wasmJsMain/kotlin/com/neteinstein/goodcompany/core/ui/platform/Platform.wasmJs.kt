package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.usecase.CalendarFile
import kotlin.time.Clock

actual val platformCapabilities = PlatformCapabilities(
    name = "Web",
    hasAddressBook = false,
    hasCallLog = false,
    hasBackgroundNotifications = false,
    hasDynamicColor = false,
    hasGoogleDrive = googleDriveClientId() != null,
)

@Composable
actual fun rememberPermissionRequester(permission: AppPermission, onResult: (granted: Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    // Geolocation prompts on first use; there is no contacts / call-log / background-notification API to ask for.
    return { callback.value(permission == AppPermission.LOCATION) }
}

@Composable
actual fun rememberTextFilePicker(kind: FileKind, onPicked: (text: String?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    val accept = when (kind) {
        FileKind.TEXT -> ".txt,text/plain"
        FileKind.VCARD -> ".vcf,.vcard,text/vcard,text/x-vcard"
        FileKind.JSON -> ".json,application/json"
    }
    return { pickTextFile(accept, { callback.value(it) }, { callback.value(null) }) }
}

@Composable
actual fun rememberTextSharer(): TextSharer = remember {
    TextSharer { text ->
        if (canWebShare()) {
            webShare(text)
            ShareOutcome.SHARE_SHEET
        } else {
            copyToClipboard(text)
            ShareOutcome.COPIED
        }
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun canWebShare(): Boolean = js("typeof navigator.share === 'function'")

@OptIn(ExperimentalWasmJsInterop::class)
private fun webShare(text: String): Unit = js("{ navigator.share({ text: text }).catch(function () {}); }")

@OptIn(ExperimentalWasmJsInterop::class)
private fun copyToClipboard(text: String): Unit = js("{ if (navigator.clipboard) navigator.clipboard.writeText(text).catch(function () {}); }")

@OptIn(ExperimentalWasmJsInterop::class)
private fun pickTextFile(accept: String, onText: (String) -> Unit, onCancel: () -> Unit): Unit = js(
    """{
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = accept;
        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file) { onCancel(); return; }
            file.text().then(t => onText(t), () => onCancel());
        };
        input.click();
    }""",
)

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? = null

/** Browsers have no contact picker this app can use (the Contact Picker API is Android-Chrome only and partial). */
@Composable
actual fun rememberContactPicker(onPicked: (List<ContactRecord>) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return { callback.value(emptyList()) }
}

/** The web downloads an .ics file the user opens in their calendar app. */
@Composable
actual fun rememberCalendarAdder(): (CalendarEntry) -> Unit = remember {
    { entry ->
        val ics = CalendarFile.allDayEvent(
            uid = "${entry.date}-${entry.title.hashCode()}",
            title = entry.title,
            date = entry.date,
            location = entry.location,
            notes = entry.notes,
            stamp = Clock.System.now(),
        )
        downloadText(CalendarFile.fileName(entry.title), "text/calendar", ics)
    }
}

@Composable
actual fun rememberFileSaver(mimeType: String, onDone: (saved: Boolean) -> Unit): FileSaver {
    val callback = rememberUpdatedState(onDone)
    return remember(mimeType) {
        FileSaver { fileName, content ->
            downloadText(fileName, mimeType, content)
            callback.value(true)
        }
    }
}

@Composable
actual fun rememberDeviceAuthenticator(): DeviceAuthenticator = remember {
    object : DeviceAuthenticator {
        override val isAvailable: Boolean = false

        override fun authenticate(reason: String, onResult: (success: Boolean) -> Unit) = onResult(true)
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun downloadText(fileName: String, mimeType: String, content: String): Unit = js(
    """{
        const blob = new Blob([content], { type: mimeType + ';charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        a.remove();
        setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
    }""",
)

@Composable
actual fun currentPlatformCapabilities(): PlatformCapabilities = platformCapabilities
