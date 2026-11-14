---
name: area-logging
description: Rules for recording contact in Good Company - meetups (Hangout, activity, place), calls/messages/emails (Interaction), journal entries, the one Log sheet and the "We met" panel, the timeline, warmth and rhythm counting, automatic sources and dedupe, named places from GPS, and calendar suggestions. Use before touching LogInteractionUseCase, LogInteractionSheet/Route, WeMetRoute, Timeline, Hangout/Interaction/JournalEntry, NamedPlace, CheckInUseCase, CalendarReader or CalendarSuggester.
---

# Area: logging, timeline, places and calendar

When you learn something new about this area, add it here, not to AGENTS.md.

## Before you change anything

- **All** logging goes through `LogInteractionUseCase(LogRequest)` and is undone with `UndoLogUseCase`. No screen writes
  hangouts or interactions directly.
- Choosing a circle lists its members with **nobody ticked** (F20).
- "+ Add people" on the Log sheet and We met opens `PeoplePickerSheet` (issue #89) with `requireSomeone = true`.
- Automatic rows (call log, chat import) dedupe through `Interaction.automaticId`; manual rows never dedupe.
- Places are GPS only: never call a geocoding, place-search or map service.
- Calendar events live in memory: never stored, backed up, or sent to the AI.
- Route parameters on `LogInteractionRoute` / `WeMetRoute` are documented in `composeApp/.../navigation/Routes.kt`; keep
  those KDocs current.

## Reference (moved from AGENTS.md)

26. **Timeline and logging (issue #21).** Meetups are `Hangout`s (with an optional `HangoutActivity`, F3); calls, messages,
    emails, video calls and WhatsApp days are `Interaction`s (`id`, `source`, `note`, `durationSeconds`, `batchId`,
    `messageCount`); a `JournalEntry` is a dated note that never counts as contact (own `journal_entry` table, exposed through
    `InteractionRepository`). Every non-meetup type counts like a call: warmth and the rhythm only with the friend's
    "calls and chats count" switch on. Rows from automatic sources (`InteractionSource.CALL_LOG`, `CHAT_IMPORT`) use
    `Interaction.automaticId` and a partial unique index, so a re-sync never duplicates them; manual ones never dedupe.
    Logging several people at once writes one row per person sharing a `batchId`. All logging goes through
    `LogInteractionUseCase(LogRequest)` (undo with `UndoLogUseCase`); the UI is `LogInteractionSheet` (`:core:ui`), hosted
    by the `LogInteractionRoute(friendIds, type)` dialog destination in `AppNavHost` (`type` is a `LogKind` or
    `InteractionType` name). `Timeline.build` merges hangouts, interactions, journal, "What's new" and gifts given;
    the full list is `FriendTimelineRoute`, and the WhatsApp import lives in its overflow menu. Migration `4.sqm` rebuilt
    `interaction` with ids (old rows get their automatic id and a legacy source) and added the journal and hangout activity.
29. **Named places (issue #44, F11).** GPS only: never call a place-search, geocoding or map service. A `NamedPlace`
    (`named_place` table, migration `7.sqm`, in `Backup.places`) is a name, a point and `radiusM` (100). Meetups link to
    one through `Place.placeId` (`hangout.place_id`, no foreign key). `CheckInUseCase` resolves the place: a fix inside a
    radius (`NamedPlaces.nearest`, haversine, the edge counts, overlaps → the nearest) with no name or that place's name
    takes it; a new name at a fix creates a place; no name and nothing near keeps just the coordinates. Rename and merge
    go through `HangoutRepository` (one transaction in SQL) so every linked meetup's `place_name` follows; delete only
    forgets the place (meetups keep their name and link, so Undo relinks them). "Usually here with" is
    `NamedPlaces.usualPeople` (most frequent in the last 8 meetups there): chips that add on tap, never pre-ticked.
    Both sheets draw it with `MeetupPlace` (`:core:ui`); Settings → Places is `PlacesRoute` in `:feature:settings`.
32. **Calendar suggestions (issue #45, D3).** Opt-in (`AppSettings.calendarSuggestions`, `calendarIds`, `calendarOnSince`:
    device settings, stripped from exports and kept on restore). The `CalendarReader` port (`createCalendarReader()`:
    `AndroidCalendarReader` over `CalendarContract.Instances` with `READ_CALENDAR` in the main manifest, `IosCalendarReader`
    over EventKit full access, `CalendarReader.None` on the web) is only read through `ObserveCalendarEventsUseCase`, which
    returns nothing while the feature is off, no calendar is ticked or access is missing. Events live in memory only:
    never stored, never in a backup, never in an AI request. `CalendarSuggester` (pure) matches by attendee email and
    `ShareMatcher.isNamedIn`, keeps ended events from the last 60 days, and drops "No" / "Never" answers
    (`calendar_decision` table, migration `12.sqm`, in backups; keys hold ids only) and days already logged with those
    friends. Its summary notification is planned in `NotificationPlanner` (one a day at most, silent like catch-ups);
    upcoming matched events also feed `ObserveStoryBubblesUseCase`. "Log it" opens `LogInteractionRoute` with `date` and
    `activity` (`LogPrefill`). UI: `CalendarSettingsRoute` (`:feature:settings`) and `CalendarSuggestionsRoute`
    (`:feature:agenda`, also a card on Coming up).
