package com.neteinstein.goodcompany.core.ui.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

actual val platformCapabilities = PlatformCapabilities(
    name = "Android",
    hasAddressBook = true,
    hasCallLog = true,
    hasBackgroundNotifications = true,
    hasDynamicColor = true,
)

private fun AppPermission.manifestPermissions(): Array<String> = when (this) {
    AppPermission.CONTACTS -> arrayOf(Manifest.permission.READ_CONTACTS)
    AppPermission.LOCATION -> arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    AppPermission.NOTIFICATIONS -> arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    AppPermission.CALL_LOG -> arrayOf(Manifest.permission.READ_CALL_LOG)
}

/** True when the merged manifest declares [permission] (READ_CALL_LOG is only in the sideload build). */
private fun Context.declares(permission: String): Boolean = runCatching {
    packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().contains(permission)
}.getOrDefault(false)

@Composable
actual fun rememberPermissionRequester(permission: AppPermission, onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        callback.value(results.values.any { it })
    }
    return {
        val wanted = permission.manifestPermissions().filter(context::declares)
        when {
            wanted.isEmpty() -> callback.value(false)
            wanted.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } -> callback.value(true)
            else -> launcher.launch(wanted.toTypedArray())
        }
    }
}

@Composable
actual fun rememberTextFilePicker(kind: FileKind, onPicked: (text: String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val text = uri?.let {
            runCatching { context.contentResolver.openInputStream(it)?.bufferedReader()?.use { r -> r.readText() } }.getOrNull()
        }
        callback.value(text)
    }
    return {
        launcher.launch(
            when (kind) {
                FileKind.TEXT -> arrayOf("text/plain")
                // File managers label .vcf inconsistently; accept the common ones.
                FileKind.VCARD -> arrayOf("text/x-vcard", "text/vcard", "text/directory", "text/plain", "application/octet-stream")
            },
        )
    }
}

@Composable
actual fun rememberTextSharer(): TextSharer {
    val context = LocalContext.current
    return remember(context) {
        TextSharer { text ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ShareOutcome.SHARE_SHEET
        }
    }
}

@Composable
actual fun platformDynamicColorScheme(dark: Boolean): ColorScheme? {
    val context = LocalContext.current
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
