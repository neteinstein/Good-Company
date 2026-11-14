---
name: area-notifications-widgets
description: Rules for everything Good Company shows outside the app - local notifications and the catch-up nudge budget (NudgePolicy, NotificationPlanner), the Android RemoteViews and iOS WidgetKit "Who's next" widgets, app-icon shortcuts and goodcompany:// deep links. Use before scheduling any notification, touching NotificationPlanner, NextUpWidget, WidgetSnapshot, ObserveHomeWidgetUseCase, AppShortcuts, ObserveShortcutsUseCase, AppLink or QuickEntryInbox.
---

# Area: notifications, widgets and shortcuts

When you learn something new about this area, add it here, not to AGENTS.md.

## Before you change anything

- Catch-up nudges go only through `NudgePolicy` in `NotificationPlanner`. Birthdays, events, reminders and plans are
  never throttled.
- Privacy: with private notifications or app lock on, widgets hide photos and shortcuts show nobody.
- Android and iOS widgets show the same `ObserveHomeWidgetUseCase` data; change both (iOS keeps pace with Android).
- Texts: notifications are rendered in `:core:ui` (`ResourceNotificationRenderer`); widget and shortcut labels are
  Android resources and iOS `Localizable.strings` (`add-strings`).
- A reminder that belongs to a circle (#88, `Reminder.circleId`) is planned for the circle: its text names the circle,
  then its own people (`NotificationPlanner`); who it's for is `Reminder.peopleIds(circles)`, today's members.
- Deep links are parsed in one place (`AppLink`); add new ones there with tests.

## Reference (moved from AGENTS.md)

16. **Notification budget.** Catch-up nudges go through `NudgePolicy` in `NotificationPlanner` (weekly digest or one a day,
    quieter when the app isn't opened). Don't schedule catch-up notifications anywhere else. Birthdays, events, reminders
    and plans are never throttled.
18. **Widgets.** Android: `androidApp/.../widget/NextUpWidget.kt` is plain `RemoteViews` (no Glance); its strings are Android
    resources in `androidApp/src/main/res/values-*` (all five languages). iOS: `iosApp/NextUpWidget` (WidgetKit) reads the
    `WidgetSnapshot` JSON the app writes to the app group (`startWidgetSync`); "We met" opens `goodcompany://met/<id>`.
    Both show what `ObserveHomeWidgetUseCase` returns; keep the two in step (owner: iOS keeps pace with Android and web).
28. **App-icon shortcuts (issue #23, D7).** `ObserveShortcutsUseCase` (domain, `Shortcuts.kt`) gives the three most recent
    people by `Relationships.lastContact` (no orphans) and none while app lock or private notifications are on. A picked
    shortcut is a `goodcompany://log[/<friendId>]` link (`AppLink`, shared parser, also `met/<id>` for the iOS widget)
    → `QuickEntryInbox` → `App.kt` opens `LogInteractionRoute` (under the app lock, like `ShareInbox`). An unknown id opens
    the sheet with nobody picked, and the sheet never saves an id it can't resolve. Android: `shortcuts/AppShortcuts.kt`
    (dynamic shortcuts kept in step from `GoodCompanyApp`, icons from `PhotoBitmaps` or initials on `avatarColorsArgb`),
    the `VIEW goodcompany://log` filter on `MainActivity`, labels in `androidApp/src/main/res/values-*`. Pinned person
    shortcuts outlive privacy changes (Android keeps them; we only replace the dynamic ones). iOS: Kotlin builds the
    localised `shortcutItems()` (`startShortcutSync`, compose string `shortcut_log_meetup`); `iOSApp.swift` sets
    `UIApplication.shortcutItems` on every trip to the background and handles a pick in `QuickActionSceneDelegate`
    (cold start and running), all in the existing Swift file, so the Xcode project didn't change. Web: none.
