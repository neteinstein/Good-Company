package org.goodcompany.app.core.data.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.goodcompany.app.core.domain.model.NotificationAction
import org.goodcompany.app.core.domain.model.RenderedNotification
import org.goodcompany.app.core.domain.repository.NotificationScheduler
import org.goodcompany.app.core.domain.usecase.AppLink
import org.goodcompany.app.core.domain.usecase.HandleNotificationActionUseCase
import org.goodcompany.app.core.domain.usecase.OpenInbox
import org.goodcompany.app.core.domain.usecase.QuickEntryInbox
import org.goodcompany.app.core.domain.usecase.toQuickEntry
import org.koin.mp.KoinPlatform
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * UNUserNotificationCenter calendar triggers. iOS caps pending requests at 64, so we keep the soonest 60.
 * Catch-up notifications get a category with quick actions; [IosNotificationDelegate] handles them.
 *
 * Issue #98: persistent ones are time-sensitive (they break through Focus; needs the Time Sensitive entitlement) and
 * are asked again at the next reminder hour by the planner until acted on. Delivered "while open" ones are removed
 * once their slot is no longer planned.
 */
internal class IosNotificationScheduler : NotificationScheduler {
    override suspend fun replaceAll(notifications: List<RenderedNotification>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeAllPendingNotificationRequests()
        dismissClosed(center, notifications.mapTo(HashSet()) { it.slot })
        val zone = TimeZone.currentSystemDefault()
        val upcoming = notifications.sortedBy { it.at }.take(MAX_PENDING)

        // One category per distinct set of action labels (labels are already localised).
        val categories = upcoming.filter { it.actions.isNotEmpty() }
            .associate { n -> categoryId(n) to n }
            .map { (id, n) ->
                UNNotificationCategory.categoryWithIdentifier(
                    id,
                    actions = n.actions.take(4).map {
                        UNNotificationAction.actionWithIdentifier(it.action.kind.name, it.label, UNNotificationActionOptionNone)
                    },
                    intentIdentifiers = emptyList<String>(),
                    options = UNNotificationCategoryOptionNone,
                )
            }
        center.setNotificationCategories(categories.toSet())

        upcoming.forEach { n ->
            val local = n.at.toLocalDateTime(zone)
            val components = NSDateComponents().apply {
                year = local.year.toLong()
                month = local.month.ordinal.toLong() + 1
                day = local.day.toLong()
                hour = local.hour.toLong()
                minute = local.minute.toLong()
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(n.id, content(n), trigger), null)
        }
    }

    override suspend fun showNow(notification: RenderedNotification) {
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(1.0, repeats = false)
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(UNNotificationRequest.requestWithIdentifier("now:${notification.id}", content(notification), trigger), null)
    }

    private fun content(n: RenderedNotification) = UNMutableNotificationContent().apply {
        // iOS has no separate lock-screen version: in private mode the notification itself stays neutral
        // and the details are in the app.
        setTitle(n.publicText ?: n.title)
        setBody(if (n.publicText != null) "" else n.body)
        setSound(UNNotificationSound.defaultSound())
        setThreadIdentifier(n.slot)
        if (n.persistent) setInterruptionLevel(UNNotificationInterruptionLevel.UNNotificationInterruptionLevelTimeSensitive)
        val info = mutableMapOf<Any?, Any?>(KEY_SLOT to n.slot, KEY_WHILE_OPEN to n.whileOpen)
        n.link?.let { info[KEY_LINK] = it }
        n.actions.firstOrNull()?.let { first ->
            setCategoryIdentifier(categoryId(n))
            info[KEY_TARGET] = first.action.copy(kind = NotificationAction.Kind.MET).encode()
        }
        setUserInfo(info)
    }

    private fun dismissClosed(center: UNUserNotificationCenter, planned: Set<String>) {
        center.getDeliveredNotificationsWithCompletionHandler { delivered ->
            val closed = delivered.orEmpty().filterIsInstance<UNNotification>().filter { d ->
                val info = d.request.content.userInfo
                info[KEY_WHILE_OPEN] == true && (info[KEY_SLOT] as? String)?.let { it !in planned } == true
            }
            if (closed.isNotEmpty()) center.removeDeliveredNotificationsWithIdentifiers(closed.map { it.request.identifier })
        }
    }

    private fun categoryId(n: RenderedNotification) = "catchup-" + n.actions.joinToString("-") { it.action.kind.name + it.label }.hashCode()

    internal companion object {
        const val MAX_PENDING = 60
        const val KEY_TARGET = "target"
        const val KEY_SLOT = "slot"
        const val KEY_WHILE_OPEN = "whileOpen"
        const val KEY_LINK = "link"
    }
}

/**
 * Receives quick-action taps (and shows banners while the app is open). Must be installed at launch.
 * A class, not an `object`: Kotlin/Native can't allocate an Obj-C subclass as a singleton object.
 */
internal class IosNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    // Obj-C classes can't also implement KoinComponent, so resolve through the global Koin instance.
    private val handle: HandleNotificationActionUseCase by lazy { KoinPlatform.getKoin().get() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val actionId = didReceiveNotificationResponse.actionIdentifier
        val target = didReceiveNotificationResponse.notification.request.content.userInfo[IosNotificationScheduler.KEY_TARGET] as? String
        // Issue #116: a tap on a notification with an app link (the daily log reminder) opens the Log popup.
        val link = didReceiveNotificationResponse.notification.request.content.userInfo[IosNotificationScheduler.KEY_LINK] as? String
        if (actionId == UNNotificationDefaultActionIdentifier && link != null) {
            when (val parsed = AppLink.parse(link)) {
                is AppLink.Log -> KoinPlatform.getKoin().get<QuickEntryInbox>().offer(parsed.toQuickEntry())
                null -> Unit
                // A birthday heads-up opens the person (issue #133); a reminder its circle, person or editor (#135).
                else -> KoinPlatform.getKoin().get<OpenInbox>().offer(parsed)
            }
            withCompletionHandler()
            return
        }
        val kind = NotificationAction.Kind.entries.firstOrNull { it.name == actionId }
        val action = target?.let(NotificationAction::decode)
        if (kind == null || action == null || actionId == UNNotificationDefaultActionIdentifier) {
            withCompletionHandler()
            return
        }
        scope.launch {
            try {
                handle(action.copy(kind = kind))
            } finally {
                withCompletionHandler()
            }
        }
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound)
    }
}
