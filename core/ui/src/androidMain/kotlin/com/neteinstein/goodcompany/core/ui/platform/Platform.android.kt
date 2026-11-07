package com.neteinstein.goodcompany.core.ui.platform

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.CancellationSignal
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

actual val platformCapabilities = PlatformCapabilities(
    name = "Android",
    hasAddressBook = true,
    hasCallLog = true,
    hasBackgroundNotifications = true,
    hasDynamicColor = true,
    hasContactPicker = true,
    hasHomeWidget = true,
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
                FileKind.JSON -> arrayOf("application/json", "text/plain", "application/octet-stream")
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

@Composable
actual fun rememberContactPicker(onPicked: (List<ContactRecord>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    // Picking a phone number grants temporary read access to that one row: no READ_CONTACTS needed.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        callback.value(listOfNotNull(uri?.let { readPickedContact(context, it) }))
    }
    return {
        runCatching {
            launcher.launch(Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE))
        }.onFailure { callback.value(emptyList()) }
    }
}

private fun readPickedContact(context: Context, uri: Uri): ContactRecord? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        ),
        null,
        null,
        null,
    )?.use { c ->
        if (!c.moveToFirst()) return@use null
        ContactRecord(
            contactId = c.getLong(0).toString(),
            name = c.getString(1).orEmpty(),
            phoneNumbers = listOfNotNull(c.getString(2)?.takeIf { it.isNotBlank() }),
            birthday = null,
        )
    }
}.getOrNull()

@Composable
actual fun rememberCalendarAdder(): (CalendarEntry) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { entry ->
            val zone = TimeZone.currentSystemDefault()
            val start = entry.date.atStartOfDayIn(zone).toEpochMilliseconds()
            val end = entry.date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone).toEpochMilliseconds()
            val intent = Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.Events.TITLE, entry.title)
                .apply {
                    entry.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                    entry.notes?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
                }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // No calendar app installed: nothing sensible to do.
            }
        }
    }
}

@Composable
actual fun rememberFileSaver(mimeType: String, onDone: (saved: Boolean) -> Unit): FileSaver {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onDone)
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        val content = pending
        pending = null
        val saved = uri != null && content != null && runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) } != null
        }.getOrDefault(false)
        callback.value(saved)
    }
    return remember(launcher) {
        FileSaver { fileName, content ->
            pending = content
            launcher.launch(fileName)
        }
    }
}

@Composable
actual fun rememberDeviceAuthenticator(): DeviceAuthenticator {
    val context = LocalContext.current
    return remember(context) { AndroidDeviceAuthenticator(context) }
}

private class AndroidDeviceAuthenticator(private val context: Context) : DeviceAuthenticator {
    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override val isAvailable: Boolean
        get() = context.getSystemService(BiometricManager::class.java)?.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    override fun authenticate(reason: String, onResult: (success: Boolean) -> Unit) {
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(reason)
            .setAllowedAuthenticators(authenticators)
            .build()
        runCatching {
            prompt.authenticate(
                CancellationSignal(),
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
                },
            )
        }.onFailure { onResult(false) }
    }
}
