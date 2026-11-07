# Good Company — product brief (canonical)

> **This file is the authoritative source for what the app does.** It reproduces, verbatim, the feature
> list from the original request that created this repository, followed by product decisions the owner made
> afterwards. Do not "clean up", reword or extend the brief section; if behaviour and brief disagree, the brief
> wins unless a later **Decision** below says otherwise. Add new decisions at the bottom with a date — never
> silently invent features that aren't here.

## Brief (verbatim, 2026-11-06)

* Call it Good Company: Think of it as a CRM for friends. It fetches data from Contacts and uses that as it's main database, but can also have internal app database.
* You can define who are real friends and pick who you should be notified about birthdays
* Store what your friends like, dislike
* With a tap of button mark if you are or have been with a friend (and define where or use location), also match that with SMS, Calls, Whatsapp messages etc.
   * See the last time(s) you have been with X people
   * Place reminders: "If I don't meet with X in Y time, remind me"
* Set reminders: "Remind me to book tickets for X event to go with person Y"
* Match tastes with news and conversations (check if we can pull whatsapp info, messenger info etc) - Require AI token
* Once a week run the news and match events that according to tastes and location could be a good thing to propose to go together. Enable this to be enable or disabled by friend. - Require AI token
* Reminder of friends kids, names, birthdays and what each ones likes (to be able to check when going to a party etc)
* Remind of an important event (surgery whatever) to ping that friend

## Decisions

| Date | Decision | Why |
|---|---|---|
| 2026-11-06 | **SMS are never read.** No `READ_SMS`/`RECEIVE_SMS` permission in any build; the SMS interaction type was removed. | Owner: SMS access would get the app into trouble with store review (Google Play restricts it to default SMS apps). |
| 2026-11-06 | **Call log only in the `sideload` Android build type.** `READ_CALL_LOG` is declared in `androidApp/src/sideload/AndroidManifest.xml` only; the Play `release` build never has it. iOS has no call-log API. | Same Play restricted-permission policy as SMS; keeps the brief's "match with Calls" available outside the Play Store. |
| 2026-11-06 | **WhatsApp / Messenger: import, don't pull.** Neither offers an API to read messages. WhatsApp's own "Export chat → Without media" `.txt` can be imported per friend; it feeds last-contact dates and AI context. Messenger has no export path that's practical, so it isn't supported. | Answers the brief's "check if we can pull whatsapp info, messenger info". |
| 2026-11-06 | **"AI token" = the user's own Anthropic API key**, stored on device, used for both AI features (talking points per friend, weekly event plans). Model: Claude (`claude-opus-5-5`) with server-side web search. | Bring-your-own-key keeps the app serverless. |
| 2026-11-06 | **Web build has no address book, call log or background notifications** (browser limitations). Friends are added manually there; the agenda shows reminders. | Platform reality. |
| 2026-11-06 | Weekly job: Android uses WorkManager (daily check, 7-day cadence enforced in `RunWeeklyIdeasUseCase`); iOS and web catch up on app launch. | iOS `BGTaskScheduler` is a follow-up. |
| 2026-11-07 | **Circles with default rhythms**: real friends sit in Inner (every 7 days), Close (30) or Good friends (90), editable in Settings; a friend's own "meet every N days" overrides the circle. Marking someone a real friend puts them in Close. | Owner asked for circles after the competitor review; makes "meet every N days" work with one tap. |
| 2026-11-07 | **Snooze and "Called"**: catch-up nudges can be snoozed a week or to Saturday; "Called" logs a call and snoozes a week. Android/iOS catch-up notifications carry these as quick actions. | Owner request (competitor review item 2). |
| 2026-11-07 | **Warmth score** (0–100) from meetups, calls and chats in the last 90 days against the friend's rhythm; real friends who fade show under "Drifting apart". | Owner request. |
| 2026-11-07 | **Messages are drafted, never sent by the app**: birthday wishes and "Propose to…" plans open WhatsApp (`wa.me`), an `sms:` link or the share sheet with prefilled text. AI personalisation uses the same bring-your-own key. | Keeps the no-SMS-permission decision; owner request (competitor review item 3, birthday message). |
| 2026-11-07 | **Saved groups** with their own rhythm, **gift ideas & history** (with AI suggestions), **vCard (.vcf) import** (the address-book path on the web) and a **year in review** screen. | Owner request. |
| 2026-11-07 | **Languages**: English, European Portuguese, Spanish, French and Romanian, following the device locale; AI output is requested in the same language. | Owner request. |
| 2026-11-07 | **Catch-up nudges have a budget**: by default one weekly digest ("people to catch up with this week", day chosen in Settings) instead of one notification per friend; with the digest off, at most one catch-up nudge a day. Nudges get quieter after 21 days without opening the app and stop after 90. Birthdays, big days, reminders and plans are never throttled. | Owner approved the feature batch from the competitor review (2nd round); ignored nudges should get quieter, never louder. |
| 2026-11-07 | **Plans**: a dated meetup with friends (from a friend's page, "Who's free for…?" or an AI idea). Notified on the day; the day after, "Did you meet?" (also a notification quick action). Only a "yes" logs a check-in — a planned date never counts as met on its own. "Add to calendar" hands the event to the system editor (Android intent, iOS event editor, .ics on the web); the calendar is never read. | Same batch. Write-only calendar keeps it permission-free. |
| 2026-11-07 | **Pause** a friend's catch-ups with no end date (history and birthdays kept), and a per-friend switch so **calls and chats count as catching up** (default off: they only add to warmth). | Same batch; default chosen so a call doesn't silently reset the meet rhythm. |
| 2026-11-07 | **Occasions** ("good company for" weekday lunch, coffee, dinner, drinks, outdoors, sports, culture, family time) and **"Who's free for…?"** on the Together tab, due friends first. **Offline talking points** from notes, recent/upcoming big days and kids' birthdays work without an AI key. | Same batch. |
| 2026-11-07 | **Privacy options**: private lock-screen notifications (Android public version; on iOS the notification itself stays neutral), an optional **app lock** with the device's biometrics/PIN, and **"Show warmth"** (off hides the warmth dots, score and "Drifting apart"). Copy for overdue/fading states is softer ("Worth a hello", "Quiet"). | Same batch. "Drifting apart" keeps its name (earlier decision); hiding it is opt-in. |
| 2026-11-07 | **Backup**: export everything to one JSON file and restore it on any platform. The API key is never exported; a restore keeps this device's key and app-lock setting. | Same batch: moving between phones, iOS and the web without a server. |
| 2026-11-07 | **Contact picker**: pick people from the system contact picker without the contacts permission (Android: one at a time; iOS: several). Full address-book sync stays in every build for now; whether the Play build should move to picker-only depends on Google's contacts-permission policy, which the owner will confirm. | Same batch; reported Play restrictions on broad contacts access. |
| 2026-11-07 | **Android home-screen widget** "Who's next": next birthday/big day/plan/reminder and the friend most due a catch-up, with "We met". Names hidden when app lock or private notifications are on. An iOS WidgetKit extension is a follow-up (needs an app group and a new target in the hand-authored Xcode project). | Same batch. |
