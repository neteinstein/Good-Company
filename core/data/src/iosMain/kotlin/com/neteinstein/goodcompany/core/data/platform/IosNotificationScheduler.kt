package com.neteinstein.goodcompany.core.data.platform

import com.neteinstein.goodcompany.core.domain.model.NotificationAction
import com.neteinstein.goodcompany.core.domain.model.RenderedNotification
import com.neteinstein.goodcompany.core.domain.repository.NotificationScheduler
import com.neteinstein.goodcompany.core.domain.usecase.HandleNotificationActionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * UNUserNotificationCenter calendar triggers. iOS caps pending requests at 64, so we keep the soonest 60.
 * Catch-up notifications get a category with quick actions; [IosNotificationDelegate] handles them.
 */
internal class IosNotificationScheduler : NotificationScheduler {
    override suspend fun replaceAll(notifications: List<RenderedNotification>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeAllPendingNotificationRequests()
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
            val content = UNMutableNotificationContent().apply {
                setTitle(n.title)
                setBody(n.body)
                setSound(UNNotificationSound.defaultSound())
                n.actions.firstOrNull()?.let { first ->
                    setCategoryIdentifier(categoryId(n))
                    setUserInfo(mapOf(KEY_TARGET to first.action.copy(kind = NotificationAction.Kind.MET).encode()))
                }
            }
            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
            center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(n.id, content, trigger), null)
        }
    }

    private fun categoryId(n: RenderedNotification) = "catchup-" + n.actions.joinToString("-") { it.action.kind.name + it.label }.hashCode()

    internal companion object {
        const val MAX_PENDING = 60
        const val KEY_TARGET = "target"
    }
}

/** Receives quick-action taps (and shows banners while the app is open). Must be installed at launch. */
internal object IosNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
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
