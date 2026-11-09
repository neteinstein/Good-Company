package com.neteinstein.goodcompany.core.ui.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.neteinstein.goodcompany.core.domain.model.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

@Composable
actual fun rememberAppUpdater(): AppUpdater {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidAppUpdater(context) }
}

/**
 * Sideload build only (the manifest that declares REQUEST_INSTALL_PACKAGES also declares the FileProvider used here).
 * Downloads the APK into the cache and opens Android's package installer; Android checks that it is signed with the
 * same key as the installed build, so a build signed with another key is refused there and the app keeps its data.
 */
private class AndroidAppUpdater(private val context: Context) : AppUpdater {
    override val installedVersionCode: Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    }.getOrDefault(0L)

    override suspend fun downloadAndInstall(update: AppUpdate, onProgress: (Float) -> Unit): UpdateInstallResult {
        if (!context.packageManager.canRequestPackageInstalls()) {
            // "Install unknown apps" is per app; send the user to this app's switch and let them tap Update again.
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return if (launch(settings)) UpdateInstallResult.NEEDS_INSTALL_PERMISSION else UpdateInstallResult.FAILED
        }
        val apk = try {
            withContext(Dispatchers.IO) { download(update, onProgress) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return UpdateInstallResult.FAILED
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (launch(install)) UpdateInstallResult.HANDED_OVER else UpdateInstallResult.FAILED
    }

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): File {
        val dir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // older downloads
        val target = File(dir, "good-company-${update.versionCode}.apk")
        val connection = URL(update.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
            var done = 0L
            var reported = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        val percent = total?.let { (done * 100 / it).toInt() }
                        if (percent != null && percent != reported) {
                            reported = percent
                            onProgress(percent / 100f)
                        }
                    }
                }
            }
            update.sizeBytes?.let { check(target.length() == it) { "incomplete download" } }
            return target
        } catch (e: Throwable) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val UPDATES_DIR = "updates"
        const val TIMEOUT_MS = 30_000
        const val BUFFER_BYTES = 64 * 1024
    }
}
