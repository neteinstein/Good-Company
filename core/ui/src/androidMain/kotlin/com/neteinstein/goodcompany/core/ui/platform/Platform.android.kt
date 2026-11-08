package com.neteinstein.goodcompany.core.ui.platform

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.ContactsPickerSessionContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.usecase.DRIVE_FILE_SCOPE
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
    // Needs an Android OAuth client (package + signing SHA-1) in the Google Cloud project; without it the
    // authorizer reports NotConfigured and the Settings row explains it.
    hasGoogleDrive = true,
    hasHomeWidget = true,
)

private fun AppPermission.manifestPermissions(): Array<String> = when (this) {
    // WRITE_CONTACTS exists only in the sideload build (it is filtered out where the manifest lacks it).
    AppPermission.CONTACTS -> arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    AppPermission.LOCATION -> arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    AppPermission.NOTIFICATIONS -> arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    AppPermission.CALL_LOG -> arrayOf(Manifest.permission.READ_CALL_LOG)
}

@Composable
actual fun currentPlatformCapabilities(): PlatformCapabilities {
    val context = LocalContext.current
    return remember(context) {
        platformCapabilities.copy(
            hasAddressBook = context.declares(Manifest.permission.READ_CONTACTS),
            hasCallLog = context.declares(Manifest.permission.READ_CALL_LOG),
            hasContactWriteBack = context.declares(Manifest.permission.READ_CONTACTS) &&
                context.declares(Manifest.permission.WRITE_CONTACTS),
        )
    }
}

/** True when the merged manifest declares [permission] (READ_CONTACTS, WRITE_CONTACTS and READ_CALL_LOG: sideload build only). */
private fun Context.declares(permission: String): Boolean = runCatching {
    packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().contains(permission)
}.getOrDefault(false)

@Composable
actual fun rememberPermissionRequester(permission: AppPermission, onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    fun isGranted(name: String) = ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Re-check instead of trusting the result map: it only lists what was asked just now.
        callback.value(permission.manifestPermissions().filter(context::declares).any(::isGranted))
    }
    return {
        val wanted = permission.manifestPermissions().filter(context::declares)
        // Contacts need everything the build declares (sideload: read *and* write); the others need any one.
        val missing = if (permission == AppPermission.CONTACTS) wanted.filterNot(::isGranted) else emptyList()
        when {
            wanted.isEmpty() -> callback.value(false)
            permission == AppPermission.CONTACTS && missing.isNotEmpty() -> launcher.launch(missing.toTypedArray())
            wanted.any(::isGranted) -> callback.value(true)
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
    // Both pickers grant temporary read access to the picked rows only: no READ_CONTACTS needed.
    val sessionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK }
        callback.value(uri?.let { readPickerSession(context, it) }.orEmpty())
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data?.takeIf { result.resultCode == Activity.RESULT_OK }
        callback.value(listOfNotNull(uri?.let { readPickedContact(context, it) }))
    }
    val launchLegacy = {
        runCatching {
            legacyLauncher.launch(Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE))
        }.onFailure { callback.value(emptyList()) }
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            // Android 17's system Contact Picker: several people in one go, phone numbers only.
            runCatching { sessionLauncher.launch(multiContactPickerIntent()) }.onFailure { launchLegacy() }
        } else {
            launchLegacy()
        }
    }
}

private fun multiContactPickerIntent(): Intent = Intent(ContactsPickerSessionContract.ACTION_PICK_CONTACTS)
    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    .putStringArrayListExtra(
        ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
        arrayListOf(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
    )

/**
 * Reads the session URI the Android 17 picker returns: one `ContactsContract.Data` row per picked number. Keys people
 * by `CONTACT_ID` (like the legacy picker and the address-book import, so re-picking someone updates them), falling
 * back to `LOOKUP_KEY`. The session URI rejects `selection`, so rows are filtered by MIME type here.
 */
private fun readPickerSession(context: Context, uri: Uri): List<ContactRecord> = runCatching {
    val rows = mutableListOf<PickedPhoneRow>()
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val contactId = c.getColumnIndex(ContactsContract.Data.CONTACT_ID)
        val lookupKey = c.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)
        val name = c.getColumnIndex(ContactsContract.Data.DISPLAY_NAME_PRIMARY)
        val mimeType = c.getColumnIndex(ContactsContract.Data.MIMETYPE)
        val number = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (c.moveToNext()) {
            val mime = c.stringOrNull(mimeType)
            if (mime != null && mime != ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) continue
            rows += PickedPhoneRow(
                contactKey = c.stringOrNull(contactId) ?: c.stringOrNull(lookupKey),
                name = c.stringOrNull(name),
                number = c.stringOrNull(number),
            )
        }
    }
    groupPickedPhoneRows(rows)
}.getOrDefault(emptyList())

private fun Cursor.stringOrNull(index: Int): String? = if (index >= 0 && !isNull(index)) getString(index) else null

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

@Composable
actual fun rememberGoogleDriveAuthorizer(onResult: (DriveAuthResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            callback.value(DriveAuthResult.Cancelled)
            return@rememberLauncherForActivityResult
        }
        val outcome = runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data) }
        callback.value(outcome.fold({ it.toDriveResult() }, { it.toDriveFailure() }))
    }
    return {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE))).build()
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    consent.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    callback.value(result.toDriveResult())
                }
            }
            .addOnFailureListener { callback.value(it.toDriveFailure()) }
    }
}

private fun AuthorizationResult.toDriveResult(): DriveAuthResult =
    accessToken?.let { DriveAuthResult.Token(it) } ?: DriveAuthResult.Failed("No access token")

/** DEVELOPER_ERROR = no OAuth client registered for this package + signing certificate. */
private fun Throwable.toDriveFailure(): DriveAuthResult = when {
    this is ApiException && statusCode == CommonStatusCodes.DEVELOPER_ERROR -> DriveAuthResult.NotConfigured
    this is ApiException && statusCode == CommonStatusCodes.CANCELED -> DriveAuthResult.Cancelled
    else -> DriveAuthResult.Failed(message)
}
