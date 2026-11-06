package com.neteinstein.goodcompany.core.data.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.neteinstein.goodcompany.core.data.source.CommunicationLogSource
import com.neteinstein.goodcompany.core.data.source.LogEntry
import com.neteinstein.goodcompany.core.domain.model.InteractionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * Reads the call log (answered calls only).
 *
 * READ_CALL_LOG is a Google Play *restricted* permission (only default dialer apps may hold it), so it
 * is declared **only** in the `sideload` build type's manifest (androidApp/src/sideload/AndroidManifest.xml).
 * In the Play `release` build the permission check below fails and this source returns nothing.
 * SMS is deliberately not read at all (store policy) — see AGENTS.md.
 */
internal class AndroidCommunicationLogSource(private val context: Context) : CommunicationLogSource {
    override suspend fun read(since: Instant): List<LogEntry> = withContext(Dispatchers.IO) {
        calls(since)
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun calls(since: Instant): List<LogEntry> {
        if (!granted(Manifest.permission.READ_CALL_LOG)) return emptyList()
        val out = mutableListOf<LogEntry>()
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE, CallLog.Calls.DURATION),
                "${CallLog.Calls.DATE} >= ?",
                arrayOf(since.toEpochMilliseconds().toString()),
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val number = c.getString(0) ?: continue
                    if (c.getLong(2) <= 0) continue // missed / unanswered calls don't count as contact
                    out += LogEntry(number, InteractionType.CALL, Instant.fromEpochMilliseconds(c.getLong(1)))
                }
            }
        }
        return out
    }
}
