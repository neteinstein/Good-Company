package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

actual val platformCapabilities = PlatformCapabilities(
    name = "Web",
    hasAddressBook = false,
    hasCallLog = false,
    hasBackgroundNotifications = false,
    hasDynamicColor = false,
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
