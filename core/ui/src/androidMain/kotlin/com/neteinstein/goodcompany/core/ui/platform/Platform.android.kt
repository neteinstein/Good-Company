package com.neteinstein.goodcompany.core.ui.platform

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentUris
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.neteinstein.goodcompany.core.domain.model.ContactPhoto
import com.neteinstein.goodcompany.core.domain.model.ContactRecord
import com.neteinstein.goodcompany.core.domain.usecase.DRIVE_FILE_SCOPE
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus

actual val platformCapabilities = PlatformCapabilities(
    name = "Android",
    // People are always added with the system contact picker, never by importing the whole address book, so the
    // "import address book" UI is off in every Android build. The sideload build's READ/WRITE_CONTACTS are only for
    // the write-back (hasContactWriteBack).
    hasAddressBook = false,
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
            hasCallLog = context.declares(Manifest.permission.READ_CALL_LOG),
            // Only the sideload manifest asks to install packages (Google Play restricts that permission).
            hasSelfUpdate = context.declares(Manifest.permission.REQUEST_INSTALL_PACKAGES),
            hasContactWriteBack = context.declares(Manifest.permission.READ_CONTACTS) &&
                context.declares(Manifest.permission.WRITE_CONTACTS),
        )
    }
}

/** True when the merged manifest declares [permission] (READ_CONTACTS, WRITE_CONTACTS, READ_CALL_LOG and REQUEST_INSTALL_PACKAGES: sideload build only). */
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
actual fun rememberNotificationsAllowedCheck(): suspend () -> Boolean {
    val context = LocalContext.current
    // Covers both the Android 13+ runtime permission and the app's notification toggle in system settings.
    return remember<suspend () -> Boolean>(context) { { NotificationManagerCompat.from(context).areNotificationsEnabled() } }
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
        // Pick a person (Contacts), not a phone number (Phone would make the user choose one of their numbers).
        runCatching { legacyLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)) }
            .onFailure { callback.value(emptyList()) }
    }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            // Android 17's system Contact Picker: several people in one go.
            runCatching { sessionLauncher.launch(multiContactPickerIntent()) }.onFailure { launchLegacy() }
        } else {
            launchLegacy()
        }
    }
}

/**
 * Asks only for each person's name. The picker lets the user choose which of the requested fields to share, so asking
 * for phone numbers turns "pick people" into "pick one of their numbers".
 */
private fun multiContactPickerIntent(): Intent = Intent(ContactsPickerSessionContract.ACTION_PICK_CONTACTS)
    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    .putStringArrayListExtra(
        ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
        arrayListOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
    )

/**
 * Reads the session URI the Android 17 picker returns: `ContactsContract.Data` rows for the picked people. Keys people
 * by `CONTACT_ID` (like the legacy picker and the address-book import, so re-picking someone updates them), falling
 * back to `LOOKUP_KEY`. The session URI rejects `selection`, so phone numbers (if any come back) are told apart by
 * MIME type here.
 */
private fun readPickerSession(context: Context, uri: Uri): List<ContactRecord> = runCatching {
    val rows = mutableListOf<PickedContactRow>()
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val contactId = c.getColumnIndex(ContactsContract.Data.CONTACT_ID)
        val lookupKey = c.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)
        val name = c.getColumnIndex(ContactsContract.Data.DISPLAY_NAME_PRIMARY)
        val mimeType = c.getColumnIndex(ContactsContract.Data.MIMETYPE)
        val number = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val photo = c.getColumnIndex(ContactsContract.CommonDataKinds.Photo.PHOTO)
        while (c.moveToNext()) {
            val type = c.stringOrNull(mimeType)
            val isPhoto = type == ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE && photo >= 0 && !c.isNull(photo)
            rows += PickedContactRow(
                contactKey = c.stringOrNull(contactId) ?: c.stringOrNull(lookupKey),
                name = c.stringOrNull(name),
                number = if (type == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) c.stringOrNull(number) else null,
                photo = if (isPhoto) runCatching { ContactPhoto.of(c.getBlob(photo)) }.getOrNull() else null,
            )
        }
    }
    groupPickedContactRows(rows).map { record ->
        val id = record.contactId.toLongOrNull()
        if (record.photo != null || id == null) {
            record
        } else {
            record.copy(photo = readPickedPhoto(context, ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)))
        }
    }
}.getOrDefault(emptyList())

private fun Cursor.stringOrNull(index: Int): String? = if (index >= 0 && !isNull(index)) getString(index) else null

/** Reads the contact URI the legacy picker returns: the person's id and name, plus their numbers when readable. */
private fun readPickedContact(context: Context, uri: Uri): ContactRecord? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
        null,
        null,
        null,
    )?.use { c ->
        if (!c.moveToFirst()) return@use null
        val name = c.stringOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return@use null
        ContactRecord(
            contactId = c.getLong(0).toString(),
            name = name,
            phoneNumbers = readPickedNumbers(context, uri),
            birthday = null,
            photo = readPickedPhoto(context, uri),
        )
    }
}.getOrNull()

/**
 * Best effort, like the numbers: the contact's thumbnail when the picker's grant (or, in the sideload build,
 * READ_CONTACTS) lets us read it. Otherwise the person is added without one, and the sideload sync fills it in later.
 */
private fun readPickedPhoto(context: Context, contactUri: Uri): ContactPhoto? = runCatching {
    ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, contactUri, false)?.use { input ->
        // Thumbnails are a few KB; stop reading past the limit instead of loading something huge.
        val buffer = ByteArray(ContactPhoto.MAX_BYTES + 1)
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n < 0) break
            read += n
        }
        ContactPhoto.of(buffer.copyOf(read))
    }
}.getOrNull()

/**
 * Best effort: the grant covers the picked contact, and some contacts apps extend it to the contact's data rows. When
 * they don’t, the person is still added, just without numbers.
 */
private fun readPickedNumbers(context: Context, contactUri: Uri): List<String> = runCatching {
    val numbers = mutableListOf<String>()
    context.contentResolver.query(
        Uri.withAppendedPath(contactUri, ContactsContract.Contacts.Data.CONTENT_DIRECTORY),
        arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null,
        null,
        null,
    )?.use { c ->
        while (c.moveToNext()) {
            if (c.stringOrNull(0) != ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) continue
            c.stringOrNull(1)?.trim()?.takeIf { it.isNotEmpty() && it !in numbers }?.let(numbers::add)
        }
    }
    numbers.toList()
}.getOrDefault(emptyList())

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
