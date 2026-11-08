@file:OptIn(ExperimentalForeignApi::class)

package com.neteinstein.goodcompany

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.SharedContent
import com.neteinstein.goodcompany.core.domain.model.SharedImage
import com.neteinstein.goodcompany.core.domain.usecase.CheckInUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ObserveHomeWidgetUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ShareInbox
import com.neteinstein.goodcompany.core.domain.usecase.WidgetSnapshot
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.posix.memcpy

/*
 * Glue between the Swift side of the iOS app (main app, Share Extension, WidgetKit widget) and the shared code.
 * The extensions can't run Kotlin, so they talk to the app through the app group (AppGroupId in Info.plist):
 *  - Share Extension → `ShareInbox/<id>.json` (+ `<id>.jpg`) files, picked up by [receivePendingShares].
 *  - Widget ← the `widget.snapshot` JSON in the group's UserDefaults, written by [startWidgetSync].
 */

private const val INBOX_DIR = "ShareInbox"
private const val WIDGET_KEY = "widget.snapshot"
private const val DEBOUNCE_MS = 500L

private val json = Json { ignoreUnknownKeys = true }

private fun appGroupId(): String? = (NSBundle.mainBundle.objectForInfoDictionaryKey("AppGroupId") as? String)?.takeIf { it.isNotBlank() }

private fun groupContainerPath(): String? = appGroupId()?.let {
    NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(it)?.path
}

/** What the Share Extension writes (see ShareViewController.swift). */
@Serializable
private data class InboxEntry(val text: String? = null, val url: String? = null, val image: String? = null, val mediaType: String? = null)

/** Called by Swift each time the app becomes active: hands the newest share to the "Save to a friend" screen. */
fun receivePendingShares() {
    val container = groupContainerPath() ?: return
    val fm = NSFileManager.defaultManager
    val dir = "$container/$INBOX_DIR"
    val files = fm.contentsOfDirectoryAtPath(dir, null)?.filterIsInstance<String>().orEmpty()
    val entries = files.filter { it.endsWith(".json") }.sorted()
    if (entries.isEmpty()) return
    // Newest wins (names start with a timestamp); everything is removed so nothing is offered twice.
    val newest = entries.last()
    val entry = NSString.stringWithContentsOfFile("$dir/$newest", NSUTF8StringEncoding, null)
        ?.let { runCatching { json.decodeFromString(InboxEntry.serializer(), it) }.getOrNull() }
    val image = entry?.image?.let { name ->
        NSData.dataWithContentsOfFile("$dir/$name")?.toByteArray()?.let { SharedImage(it, entry.mediaType ?: "image/jpeg") }
    }
    files.forEach { fm.removeItemAtPath("$dir/$it", null) }
    entry ?: return
    KoinPlatform.getKoin().get<ShareInbox>().offer(SharedContent(text = entry.text, url = entry.url, image = image))
}

/**
 * Keeps the widget's snapshot in the app group up to date for as long as the app runs; [reload] asks WidgetKit to
 * redraw (WidgetCenter is Swift-only, so Swift passes it in).
 */
@OptIn(FlowPreview::class)
fun startWidgetSync(reload: () -> Unit) {
    val defaults = appGroupId()?.let { NSUserDefaults(suiteName = it) } ?: return
    val observe = KoinPlatform.getKoin().get<ObserveHomeWidgetUseCase>()
    MainScope().launch {
        observe().map(WidgetSnapshot::from).distinctUntilChanged().debounce(DEBOUNCE_MS).collect { snapshot ->
            defaults.setObject(json.encodeToString(WidgetSnapshot.serializer(), snapshot), WIDGET_KEY)
            reload()
        }
    }
}

/** `goodcompany://met/<friendId>` (the widget's "We met"). Returns true when handled. */
fun handleAppUrl(url: String): Boolean {
    val friendId = url.removePrefix("goodcompany://").takeIf { it.startsWith("met/") }?.removePrefix("met/")?.takeIf { it.isNotBlank() }
        ?: return false
    MainScope().launch { runCatching { KoinPlatform.getKoin().get<CheckInUseCase>()(listOf(FriendId(friendId))) } }
    return true
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
