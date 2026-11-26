---
name: area-notifications-widgets
description: Rules for everything Good Company shows outside the app - local notifications and the catch-up nudge budget (NudgePolicy, NotificationPlanner), the Android RemoteViews and iOS WidgetKit "Who's next" widgets, app-icon shortcuts and goodcompany:// deep links. Use before scheduling any notification, touching NotificationPlanner, NextUpWidget, WidgetSnapshot, ObserveHomeWidgetUseCase, AppShortcuts, ObserveShortcutsUseCase, AppLink or QuickEntryInbox.
---

# Area: notifications, widgets and shortcuts

When you learn something new about this area, add it here, not to AGENTS.md.

## Before you change anything

- Catch-up nudges go only through `NudgePolicy` in `NotificationPlanner`. Birthdays, events, reminders and plans are
  never throttled. Nor is the daily "log your day" reminder (issue #116, `NotificationType.DAILY_LOG`, on by default at
  21:00, `NotificationSettings.dailyLogMinute`): 3 days ahead in slot `daily-log`, REMINDERS channel, and it carries
  `PlannedNotification.link = AppLink.log()`, which Android puts on the tap intent and iOS hands to `QuickEntryInbox`.
- Privacy: with private notifications or app lock on, widgets hide photos and shortcuts show nobody.
- Android and iOS widgets show the same `ObserveHomeWidgetUseCase` data; change both (iOS keeps pace with Android).
- Texts: notifications are rendered in `:core:ui` (`ResourceNotificationRenderer`); widget and shortcut labels are
  Android resources and iOS `Localizable.strings` (`add-strings`).
- A reminder that belongs to a circle (#88, `Reminder.circleId`) is planned for the circle: its text names the circle,
  then its own people (`NotificationPlanner`); who it's for is `Reminder.peopleIds(circles)`, today's members.
- A plan occurrence's day and "did it happen?" notifications share the slot `plan:<occurrence>` and are both
  "while open" (issue #100), so a persistent one goes as soon as the plan is cancelled, deleted or answered. The ask
  offers "I didn't go" (`PLAN_NOT_ATTENDED`) for group and circle plans (`PlanFollowUp.isGroup`).
- A plan's time (issue #167) is only in the day notification's text (`PlanToday.time` → `notif_plan_today_at_title`,
  `clockLabel()`); the notification itself still comes at the reminder hour on the day (owner decision).
- Deep links are parsed in one place (`AppLink`); add new ones there with tests.
- "We met" is labelled **"Caught up"** everywhere outside the app too (issue #101): the notification quick action
  (`notif_action_met`), the widget button (Android `widget_met`, iOS `widget.met`) and the generic app-icon shortcut
  (`shortcut_log_meetup` in `androidApp` and `composeApp`). The `MET` notification action and the widget button
  (`ACTION_MET` on Android, `goodcompany://met/<id>` on iOS) still log an in-person meetup straight away, with no panel;
  the generic shortcut opens the Log popup, which has every kind (issue #151). The How? row lives in the in-app popup.
- Reminders (#99): plan and sort by `Reminder.nextAt` (`snoozedUntil ?: dueAt`), never `dueAt` alone. Snooze moves only
  `snoozedUntil`; Done (`CompleteReminderUseCase`) closes a one-off with `completedAt`, or rolls a repeating one to
  `nextDueAfter` and clears the snooze. `Reminder.important` + the notification setting "keep on screen" is what makes a
  reminder notification `persistent`. Reminder notifications carry Done and "Snooze 1 hour" actions
  (`NotificationAction.Kind.REMINDER_DONE/REMINDER_SNOOZE`, handled in `HandleNotificationActionUseCase`); the platform
  receivers only pass kind + id, so a new action needs no platform code.
- **Settings → Notifications (issue #98).** `AppSettings.notifications` (`NotificationSettings`: master switch, one
  switch per `NotificationType`, `persistent` = "Keep important ones on screen") lives in `DeviceSettings` (no schema
  change) and travels in backups; `notificationPrimerDismissed` (Today's card) is device-only. `NotificationPlanner`
  checks `notifications.allows(content.type)` for every notification: a new `NotificationContent` needs a `type`.
- **Birthday heads-up (issue #133).** `NotificationSettings.birthdayHeadsUpDays` (0 = off, one of
  `BIRTHDAY_HEADS_UP_CHOICES`, 7 by default; in the notification settings JSON, so no schema change and it travels in
  backups). The planner adds `bday-soon:<id>[:<relative id>]:<date>` N days before each birthday that notifies on the
  day, as `NotificationContent.BirthdaySoon` (type BIRTHDAYS, never persistent, never throttled) with
  `link = AppLink.person(id)`.
- **Pages opened from a notification (issues #133, #135).** `goodcompany://person/<id>`, `circle/<id>` and
  `reminder/<id>` go to `OpenInbox` (Android `MainActivity`, iOS `IosNotificationScheduler` and `handleAppUrl`), which
  `App.kt` turns into the person's page, the circle's page or `AgendaRoute(editReminderId)` (the reminder's editor).
  Reminder notifications carry `ReminderTarget.linkFor(reminder)`; every reminder tap in the app goes through
  `ReminderTarget.of` (circle → its page, one person → them, several or nobody → the editor) via
  `ReminderCallbacks.onOpen`.
- **Persistent notifications (issue #98).** With `persistent` on, the planner marks these `PlannedNotification.persistent`
  with `until` = the end of their day: reminders marked important (`Reminder.important`, a stub returning false until
  #99 adds the field), overdue reminders (`ReminderOverdue`, asked at the reminder hour each day, `OVERDUE_ASKS` planned
  ahead), today's birthdays/anniversaries/big days, `PlanToday` and `PlanFollowUp`. Notifications with the same `slot`
  replace each other on screen (an overdue reminder shows once); `whileOpen` ones are taken off the screen when their
  slot is no longer planned (reminder done/deleted, plan answered). Persistent = channel `IMPORTANT`.
- **Android delivery (issue #98).** Check `mayPostNotifications()` (`areNotificationsEnabled()`, plus
  `POST_NOTIFICATIONS` only on API 33+: minSdk 32 has no runtime permission). Channels: `reminders` (old id kept),
  `birthdays`, `plans`, `catch_ups` (low), `important` (high); names in `core/data/src/androidMain/res`. Posts use the
  slot as the notification tag (id 0). Persistent = `setOngoing` + `setOnlyAlertOnce` + `setTimeoutAfter`. Re-planning
  also runs from `ReplanNotificationsWorker` (daily) and after every notification quick action
  (`KeepNotificationsScheduledUseCase.once()`), not only while the UI is alive.
- **iOS delivery (issue #98).** Persistent = `UNNotificationInterruptionLevelTimeSensitive`, which needs the
  `com.apple.developer.usernotifications.time-sensitive` entitlement (`iosApp/iosApp/iosApp.entitlements`); the slot is
  the `threadIdentifier` and goes in `userInfo` with `whileOpen`. "Send a test notification" is `showNow` on every
  scheduler (a 1 s trigger on iOS, a no-op on the web).
- **Asking for permission.** Use `rememberNotificationsEnabler` (core:ui): it asks, and opens the system's notification
  settings when the system won't ask again (refused before, or Android 12L). Today shows `NotificationPrimerCard`
  while notifications are blocked, until "Not now".

- **Background jobs (Android).** `GoodCompanyApp` schedules `WeeklyIdeasWorker`, `ReplanNotificationsWorker` and
  `DriveAutoBackupWorker` (issue #166, daily, network; see `area-backup-sync`). Each decides itself whether there is
  work; keep them idempotent and unique (`ExistingPeriodicWorkPolicy.KEEP`).

## Reference (moved from AGENTS.md)

16. **Notification budget.** Catch-up nudges go through `NudgePolicy` in `NotificationPlanner` (weekly digest or one a day,
    quieter when the app isn't opened). Don't schedule catch-up notifications anywhere else. Birthdays, events, reminders
    and plans are never throttled.
18. **Widgets.** Android: `androidApp/.../widget/NextUpWidget.kt` is plain `RemoteViews` (no Glance); its strings are Android
    resources in `androidApp/src/main/res/values-*` (all five languages). iOS: `iosApp/NextUpWidget` (WidgetKit) reads the
    `WidgetSnapshot` JSON the app writes to the app group (`startWidgetSync`); "Caught up" opens `goodcompany://met/<id>`.
    Both show what `ObserveHomeWidgetUseCase` returns; keep the two in step (owner: iOS keeps pace with Android and web).
28. **App-icon shortcuts (issue #23, D7).** `ObserveShortcutsUseCase` (domain, `Shortcuts.kt`) gives the three most recent
    people by `Relationships.lastContact` (no orphans) and none while app lock or private notifications are on. A picked
    shortcut is a `goodcompany://log[/<friendId>]` link (`AppLink`, shared parser, also `met/<id>` for the iOS widget)
    → `QuickEntryInbox` → `App.kt` opens `WeMetRoute` (the Log popup, under the app lock, like `ShareInbox`). An unknown id opens
    the sheet with nobody picked, and the sheet never saves an id it can't resolve. Android: `shortcuts/AppShortcuts.kt`
    (dynamic shortcuts kept in step from `GoodCompanyApp`, icons from `PhotoBitmaps` or initials on `avatarColorsArgb`),
    the `VIEW goodcompany://log` filter on `MainActivity`, labels in `androidApp/src/main/res/values-*`. Pinned person
    shortcuts outlive privacy changes (Android keeps them; we only replace the dynamic ones). iOS: Kotlin builds the
    localised `shortcutItems()` (`startShortcutSync`, compose string `shortcut_log_meetup`); `iOSApp.swift` sets
    `UIApplication.shortcutItems` on every trip to the background and handles a pick in `QuickActionSceneDelegate`
    (cold start and running), all in the existing Swift file, so the Xcode project didn't change. Web: none.
