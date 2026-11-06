package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
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
actual fun rememberTextFilePicker(onPicked: (text: String?) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onPicked)
    return { pickTextFile({ callback.value(it) }, { callback.value(null) }) }
}

private fun pickTextFile(onText: (String) -> Unit, onCancel: () -> Unit): Unit = js(
    """{
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = '.txt,text/plain';
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
