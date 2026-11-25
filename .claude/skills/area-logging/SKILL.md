---
name: area-logging
description: Rules for recording contact in Good Company - meetups (Hangout, activity, place), calls/messages/emails (Interaction), journal entries, the one Log popup (WeMetPanel), the timeline, warmth and rhythm counting, automatic sources and dedupe, named places from GPS, and calendar suggestions. Use before touching LogInteractionUseCase, LogSheetModel, WeMetRoute/WeMetPanel, ObserveLogKindsUseCase, Timeline, Hangout/Interaction/JournalEntry, NamedPlace, CheckInUseCase, CalendarReader or CalendarSuggester.
---

# Area: logging, timeline, places and calendar

When you learn something new about this area, add it here, not to AGENTS.md.

## Before you change anything

- **All** logging goes through `LogInteractionUseCase(LogRequest)` and is undone with `UndoLogUseCase`. No screen writes
  hangouts or interactions directly.
- Choosing a circle lists its members with **nobody ticked** (F20).
- **One place to log (issue #151).** The Log popup (`WeMetRoute` → `WeMetPanelScreen`, `:feature:hangouts`) is the
  only logging UI: `LogInteractionRoute`, `LogInteractionScreen`/`ViewModel` (`:feature:friends`) and the
  `LogInteractionSheet` composable are gone. Its order is Who → How? → When → What, each under a small `Section` title,
  then a collapsed "Where · Note" row (`WeMetUiState.detailsOpen`; "Note" alone for remote kinds; open by itself with a
  note, a place or a journal entry). How? is `ObserveLogKindsUseCase` (every `LogKind`, most used first by what you
  logged by hand: meetups, manual contacts once per batch, journal entries; automatic rows don't count; ties and nothing
  logged keep the configured order). What is `ObserveActivitiesUseCase`. Both show `SHOWN` = 4 plus "See all".
- **How? options in Settings (issue #152).** `ObserveLogKindsUseCase` ranks `AppSettings.logKinds` (built-in, in your
  order, never empty, default `LogKind.DEFAULT_ORDER`; column `user_preferences.log_kinds`, migration `31.sqm`), then
  `customLogKinds` (`custom_log_kinds`, a JSON array, edited by `CustomLogKindsUseCase`, at most `LABEL_MAX` characters).
  One of your own is `LogKindOption.custom(name)`: logged as `LogKind.OTHER` with the name as `Interaction.label` (no new
  type), so renaming or removing it keeps past entries; usage counts an Other by its label (`Tags.normalize`). The popup
  selects by option (`WeMetViewModel.onKind(LogKindOption)`, `WeMetUiState.selected`), asks for a label only on the
  built-in Other, and always shows the chosen chip even when it is off. Settings → Your data → How sits next to
  Activities; both screens are `OrderedOptionsContent` (`:feature:settings`), so keep them in step. `LogPrefill` (in the met package) carries `type`, `date`,
  `activity`/`activityName` and `edit`.
- "+ Add people" on the Log popup opens `PeoplePickerSheet` (issue #89) with `requireSomeone = true`.
- Automatic rows (call log, chat import) dedupe through `Interaction.automaticId`; manual rows never dedupe.
- Places are GPS only: never call a geocoding, place-search or map service.
- "Use my location" goes through `rememberPermissionRequester(AppPermission.LOCATION, …onLocationPermissionResult)` in
  every sheet (the Log popup, "I'm with…"), never straight to the provider: without the permission the
  Android provider returns null and nothing happened (issue #114). `LogSheetModel` sets `LocationProblem` (denied / no
  fix) and `MeetupPlace` says it under the button. `AndroidLocationProvider` falls back to the newest last-known fix of
  any enabled provider after its 10 s timeout.
- The date chips (`LogDateChoice`) stay one line and one height in every language: "Other day" (`log_pick_date`)
  falls back to its calendar icon when it doesn't fit (issue #114). Keep that label short.
- Calendar events live in memory: never stored, backed up, or sent to the AI.
- Today's last-seen card also asks circles "When did the circle last meet?" (issue #150, after the people); the answer
  (`FriendGroup.approxLastMet`) is never a meetup or a timeline entry. Details in `area-people`.
- **Editing an entry (issue #102)** goes through `EditTimelineEntryUseCase(entry, LogRequest)` (`usecase/TimelineEdit.kt`);
  delete stays `DeleteTimelineEntryUseCase` with `RestoreTimelineEntryUseCase` for Undo. The Log popup edits meetups,
  contacts and journal entries (`WeMetRoute.edit(friendId, key)` → `LogPrefill.edit` → `LogSheetModel.startEditing`,
  prefilled by `TimelineEntry.asLogRequest`); news and gifts given edit in their own dialogs; circle shares are delete only.
  Same day keeps the time, another day is noon (today: now), unless a new time is picked (issue #116: then that time). Contact rows keep their ids (an automatic call keeps its
  automatic id, so a re-sync doesn't re-add it); a single row that gains people batches under its own id. A kind change
  across meetup / contact / journal deletes and re-logs at the same instant, and the share it came from is relinked.
  The rows are `EditableTimelineEntry` (`:core:ui`: tap edits, long press or ⋮ → `EditDeleteMenu`), so any screen can host
  the timeline. Edits have no Undo (they save on Save); deletes do.
- The Log popup is drawn with `DialogSheet` (`:core:ui`, issue #145): scrim, handle, drag-to-dismiss (on
  the sheet, or on its content once scrolled to the top) and the edge-to-edge insets. Their dialog destinations use
  `sheetDialogProperties` (Android `decorFitsSystemWindows = false`; iOS/web `usePlatformInsets = false`, no platform
  scrim), or the scrim stops above the navigation bar. Don't swap in Material's `ModalBottomSheet`: it opens a window of
  its own inside the dialog. A new sheet-in-a-dialog destination should reuse both.
- Their Save is a `SaveBar` in `DialogSheet`'s `bottomBar` slot (issue #146): pinned under the scrolling fields,
  above the navigation bar and the keyboard. Don't put Save back at the end of the scroll; the "I'm with…" check-in
  sheet does the same after a `weight(1f, fill = false)` column.
- Route parameters on `WeMetRoute` are documented in `composeApp/.../navigation/Routes.kt`; keep
  those KDocs current.
- **Together timeline (issue #117).** The person page's Together tab has no Coming up card: `PersonTimeline.build`
  (`usecase/PersonTimeline.kt`) gives `TogetherTimeline(upcoming, past, hasMore)` of `TogetherItem`s. Top to bottom:
  "+ Make plans", upcoming (open plans, open reminders, birthday and moments in the next 30
  days; soonest last), "Today", then every dated thing (logged entries, unlogged shares, closed plans, done
  reminders, past birthdays and moments), newest first, cut at `SHOWN` = 12 with a fade and "Show all" (the full
  timeline screen). A plan that happened is its meetup. The Conversations box's Add imports a chat export
  (`FriendDetailViewModel.onChatPicked`).
- **Together polish (issue #130).** No catch-up status on Together: the ring, last-together line, Called, snooze and
  last-contact chips are gone; snooze (weekend / a week), un-snooze and pause sit in Edit person under the rhythm
  (`CatchUpPause`). Reminders use `ReminderTimelineRow` (`:core:ui`, plan layout, actions in ⋮). Rows that aren't a
  `TimelineEntryRow` take `Modifier.timelineRail(color, fromCentre, toCentre)` so one line runs at the entry rail's x
  (36 dp) through both halves; an open plan that's past skips it (its answers sit under the badge). Trailing icons and
  ⋮ are 40 dp boxes with 20 dp icons everywhere on the timeline.
- **One Log action (issue #116).** "Caught up", "We met" and "I'm with…" are one **Log** action with one icon, `LogIcon`
  (`Icons.Rounded.EditNote`, `:core:ui` `LogTime.kt`), always beside the word Log. Today's button, quick entries and
  app-icon shortcuts open `WeMetRoute`; with nobody given (`WeMetUiState.startsWithNobody`) it opens on the people
  picker (recent first, `LogSheetState.recentFirst`). The old check-in sheet (`CheckInSheetHost`) is gone.
- **Time of day (issue #116).** `LogRequest.time: LocalTime?` and `logInstant(date, time, now, zone)` in `Logging.kt`:
  no time = now today, noon earlier; a time = that time, capped at now (never in the future). Chips `LogTimeOfDay`
  (Morning 09:00, Lunch 13:00, Afternoon 16:00, Evening 20:00) plus Now and "Pick a time" (`LogTimeChoice`,
  `LocalTimePickerDialog`); today's later chips are disabled. In person shows "Last time together" from
  `PlaceBook.lastTogether(people)`.
- **"Caught up" (issue #101, was "We met").** The panel behind `WeMetRoute` (code names keep "WeMet"/`MET`) starts with
  **How?**: in person (default), call, video, said hi (`MESSAGE`), long chat, played online (`GAME`), other, and since
  issue #151 email and journal too (`ObserveLogKindsUseCase.DEFAULT_ORDER`; `LogKind.CAUGHT_UP` is gone). A remote kind (`LogKind.isRemote`) is an `Interaction` through `LogInteractionUseCase` and hides
  activity and place; date and note stay. `OTHER` carries `Interaction.label` (`interaction.label`, migration `22.sqm`,
  at most `LogInteractionUseCase.LABEL_MAX`, kept only for `OTHER`; the timeline shows it instead of "Kept in touch").
  A plan's "It happened" answered with a remote kind still
  closes that occurrence; `LogResult.Contacts.plan` lets Undo reopen it. Meetups and contacts both go to
  `LoggedMeetupInbox` (the "Logged time with … · Undo" snackbar); journal entries don't.
- **Warmth weights (issue #101).** `Warmth.weight(type)`: in person and long chat 1.0, video 0.8, call / game / email /
  WhatsApp / other 0.6, said hi 0.3. Remote contact **always** adds to warmth; what counts as catching up only decides
  the rhythm (`Relationships.meetupStatuses`). The person page's ring and pill use `FriendDetail.lastCaughtUp`, the same
  rule.
- **What counts as catching up (decision 2026-11-21).** `CatchUpPolicy` (`usecase/CatchUp.kt`) lives in
  `CadenceRules.catchUp`, so everything built from `CadenceRules.from(settings)` (Today, the agenda, nudges, the
  last-seen card, the person page) follows it; `Relationships.lastCaughtUp` is the one place that applies it. Per person
  `Friend.catchUpCounts` (`CatchUpCounts`: DEFAULT / IN_PERSON_ONLY / ANY_CONTACT; column `friend.catch_up_counts`,
  migration `32.sqm`; the old `contact_counts_as_catch_up` is still written, 1 = ANY_CONTACT, and `FriendSerializer` maps
  the old backup key). DEFAULT follows `AppSettings.catchUpKinds` (`user_preferences.catch_up_kinds`; NULL = 
  `CatchUpPolicy.DEFAULT_KINDS`: call, video, long chat, game) and `catchUpCustomKinds` (your own How options that count;
  renaming one in `CustomLogKindsUseCase` keeps it counting). In person always counts, journal never; WhatsApp days are
  "said hi"; call-log calls under `CALL_LOG_MIN_SECONDS` (5 min) don't count on DEFAULT. Settings → How shows the
  switches below "Your own" (`OrderedOptionsContent`'s `footer`, `SetCatchUpKindUseCase`); Edit person shows the
  three chips (`CatchUpCountsChoice`). The Log popup's `LogSheetState.countsHintFor` uses the same policy.

- **How a plan ends (issue #100).** `PlanStatus` is PLANNED, HAPPENED, SKIPPED ("Didn't happen"), CANCELLED (kept,
  struck through on the Plans tab for a month) and NOT_ATTENDED ("I didn't go": group and circle plans only,
  `Plan.isGroup`; no check-in, so no warmth or rhythm). Every place a plan closes (Plans tab `PlanRow`, person page,
  Today and Agenda `AgendaRow`) uses the one `PlanHappenedButton` (`:core:ui`, issue #136: [ It happened | ▾ ], the
  other answers from `Plan.otherAnswers()` in the ▾ menu; no sheet); upcoming rows use `PlanUpcomingMenu` (⋮: Edit ·
  Add to calendar · Skip this one · Cancel plan · Delete). "It happened" stays one tap and opens the
  "We met" panel, the rest go through `ClosePlanUseCase` (Confirm / `CancelPlanUseCase` / `DeletePlanUseCase`), which
  returns the plan as it was for Undo (`RestorePlanUseCase`). On a series every answer is for that occurrence; Cancel
  and Delete ask "this one or the whole series" (`PlanSeriesDialog`), and "this one" just takes the date out.
- **A plan's time (issue #167).** `Plan.time: LocalTime?` (column `plan.time`, "HH:MM", migration `33.sqm`; minutes
  only, an unreadable value reads as no time), the same for every occurrence; set in `PlanEditor` (`PlanTimeChoice`,
  "Add a time" under the day, `LocalTimePickerDialog(is24Hour = uses24HourClock())`). It is display-only: logging an
  "It happened" still uses now / noon, and the day notification stays at the reminder hour (owner). Add to calendar
  is timed for `CalendarEntry.TIMED_MINUTES` (60) when set (`CalendarEntry.start()`; web `CalendarFile.event`, floating
  local time), all day otherwise. Show a plan time with `LocalTime.clockLabel()` (`:core:ui` platform: the device's
  12/24-hour convention; Android reads the setting after `initTimeFormat` in `GoodCompanyApp`), never `hhmm()`.
- **Places without GPS (issue #121).** Settings → Your data → Places also lists `TypedPlaces.of`: place names typed on
  meetups (no `placeId`, or one whose `NamedPlace` is gone) and plans, spellings kept apart. Rename/merge is
  `RenameTypedPlaceUseCase` (text only, GPS places untouched); "Add coordinates" is `PinTypedPlaceUseCase` with the
  phone's current location (a new `NamedPlace`, its meetups linked; plans keep their text). Still no geocoding.

## Reference (moved from AGENTS.md)

26. **Timeline and logging (issue #21).** Meetups are `Hangout`s (with an optional `HangoutActivity`, F3); calls, messages,
    emails, video calls, WhatsApp days, long chats, games and "other" are `Interaction`s (`id`, `source`, `note`,
    `durationSeconds`, `batchId`, `messageCount`, `label`); a `JournalEntry` is a dated note that never counts as contact
    (own `journal_entry` table, exposed through `InteractionRepository`). Every non-meetup type always adds to warmth
    (weighted, see above) and resets the rhythm only when `CatchUpPolicy` counts it for that friend (see above). Rows from automatic sources (`InteractionSource.CALL_LOG`, `CHAT_IMPORT`) use
    `Interaction.automaticId` and a partial unique index, so a re-sync never duplicates them; manual ones never dedupe.
    Logging several people at once writes one row per person sharing a `batchId`. All logging goes through
    `LogInteractionUseCase(LogRequest)` (undo with `UndoLogUseCase`); the UI is the Log popup (`WeMetPanel`, issue #151), hosted
    by the `WeMetRoute(friendIds, circleId, plan, type, …)` dialog destination in `AppNavHost` (`type` is a `LogKind` or
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
    upcoming matched events also feed `ObserveStoryBubblesUseCase`. "Log it" opens `WeMetRoute` with `date` and
    `activity` (`LogPrefill`). UI: `CalendarSettingsRoute` (`:feature:settings`) and `CalendarSuggestionsRoute`
    (`:feature:agenda`, also a card on Coming up).
- **Dated notes (issue #103).** A person's Notes card (About tab) lists their `JournalEntry`s newest first with their
  day; a tap edits in the Log popup (`onEditEntry` → `WeMetRoute.edit`), ⋮
  deletes with Undo. The old free-text `Friend.notes` was copied into one entry `note-<friendId>` (migration `23.sqm`,
  dated when it ran; blank notes make none) and is no longer shown or written by the page; the column and its text stay
  until the migration is verified. Older backups (`Backup.notesInJournal` false) and the old JSON store get the same
  rule through `NotesToJournal.convert` (dated at the backup's `exportedAt`); exports set the flag so a restore never
  converts twice. "Add as a person" turns the relative's note into a dated note. The story's Notes slide shows the
  `note-<id>` entry while it exists and isn't the last talk.

