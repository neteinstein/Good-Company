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
