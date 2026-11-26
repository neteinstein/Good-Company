# Data safety cheat-sheet (Play Console → App content → Data safety)

A starting point for the **Data safety** questionnaire, derived from the app's code and the owner decisions in
[docs/PRODUCT_BRIEF.md](../docs/PRODUCT_BRIEF.md). It is **not legal advice**: complete the live questionnaire in
Play Console yourself, since wording and categories change.

**This form covers the Play build only** (`org.goodcompany.app`). The sideload build is never uploaded to Play.

The key rule Google uses: **"collected" means transmitted off the device** to the developer or a third party.
Data the app only reads and stores on the device (contacts picked with the system picker, calendar events,
location, everything the user types) is **not** "collected" for this form.

## What leaves the device, and when

| Flow | Off by default? | Where it goes | What |
|---|---|---|---|
| **AI features** (talking points, weekly plans, gift and message suggestions, reading a shared screenshot) | **Yes.** AI is off until the user turns it on and enters their own Anthropic API key (`AppSettings.aiEnabled`, `usableAiKey`) | `api.anthropic.com`, with the **user's own** API key | For the friends involved: name, likes and dislikes, kids, work, where they live, family, food, drinks, interests, what's new, notes from recent conversations and meetups, recent chat lines the user imported; the user's typed home area; the UI language; a shared image when the user asks the AI to read it. Never the calendar, the API key, the backup key, phone numbers or emails (`IdeaPrompts.kt`, `AnthropicAssistant.kt`) |
| **Google Drive backup** | **Yes.** The user signs in to Google and sets a backup password | The user's own Google Drive (`drive.appdata` private folder; manual export via `drive.file`) | The whole database, **encrypted on the device** with a key derived from a password only the user knows (D4). Never the API key or the backup key |
| **Blog / newsletter feeds** | **Yes.** Only for feeds the user adds to a friend | The feed's own website, fetched by the device at most every 20 h | A plain GET of the URL the user entered. Nothing about the user is sent |
| **Firebase Analytics** | **Yes**, and nothing turns it on today | Google (Firebase) | `firebase_analytics_collection_enabled=false` and `google_analytics_adid_collection_enabled=false` in the manifest; the `AD_ID` permission is removed. No code logs events. See "Firebase" below |
| Messages, calls, WhatsApp, e-mail the user starts from the app | n/a | The app the user picks (WhatsApp, SMS app, dialer, share sheet) | Drafted text the user sees and sends themselves. User-initiated, so not "sharing" |

## Suggested answers

**Does your app collect or share any of the required user data types?**
→ Yes: only when the user turns on the optional AI features with their own key (and, conservatively, the encrypted
Drive backup). Everything else stays on the device.

⚠️ **The AI features are the judgment call.** The data goes to Anthropic, a third party, using the user's own API
key and account, only when the user taps an AI feature or enables weekly plans. Google's definition of "sharing"
excludes transfers the user initiates and expects, and the processing is on the user's own Anthropic account, which
argues for "not shared". **Declare it anyway, as collected and shared for App functionality, optional.** An
under-declaration a reviewer disagrees with is an app-removal risk; an over-declaration is not.

| Data type | Collected? | Shared? | Optional? | Purpose | Notes |
|---|---|---|---|---|---|
| Personal info → Name | Yes (AI only) | Yes (Anthropic) | Yes | App functionality | Friends' names, sent with an AI request |
| Personal info → Other info | Yes (AI only) | Yes (Anthropic) | Yes | App functionality | Friends' tastes, kids, family, work, where they live, notes |
| Messages → Other in-app messages | Yes (AI only) | Yes (Anthropic) | Yes | App functionality | Recent chat lines from a WhatsApp export the user imported, and notes |
| Photos and videos → Photos | Yes (AI only) | Yes (Anthropic) | Yes | App functionality | Only a screenshot the user shares into the app and asks the AI to read |
| Location → Approximate location | Yes (AI only) | Yes (Anthropic) | Yes | App functionality | The home area the user **types** in Settings, for "events near you". GPS coordinates never leave the device |
| Location → Precise location | No | No | — | — | GPS is used on the device to name places; never sent (gotcha 29: no geocoding or map service) |
| Contacts | No | No | — | — | Picked with the system contact picker, stored on the device. Phone numbers and e-mails are never sent to the AI |
| Calendar events | No | No | — | — | Calendar suggestions are opt-in, read on the device, never stored, backed up or sent (D3, gotcha 32) |
| App activity, App info and performance, Device or other IDs | No | No | — | — | Firebase Analytics is disabled (see below). No crash reporting SDK |
| Financial info | No | No | — | — | Gift budgets are typed by the user and stay on the device (or in the encrypted backup) |
| Files and docs | See Drive | No | Yes | App functionality, Backup | Only as the encrypted backup |

**Google Drive backup.** The backup is end-to-end encrypted: the key is derived on the device from the user's
password, and neither the developer nor Google can read it. Play's guidance exempts data sent with end-to-end
encryption that no one but the user can read, and it goes to the user's own Drive. You can leave it out of the
table; if you'd rather be conservative, declare "Files and docs" as collected, optional, for "App functionality",
encrypted in transit.

## Security practices section

- **Data encrypted in transit:** Yes. The Anthropic API, Google Drive and Firebase are HTTPS only. Feeds are
  fetched over whatever URL the user typed (usually HTTPS) and carry no user data.
- **Users can request data deletion:** Yes. There is no developer-side account or server. The user removes
  everything by: deleting people in the app; clearing the app's storage (Android Settings → Apps → Good Company →
  Storage → Clear storage) or uninstalling; turning off Drive backup and deleting the app's data in Google Drive
  (Drive → Settings → Manage apps → Good Company → Delete hidden app data); and deleting their Anthropic API key in
  the Anthropic Console. (There is no single "delete all my data" button in the app today.) Put this in the "data deletion" free-text field.
- **Committed to the Play Families Policy / independent security review:** not applicable; answer per your
  practices.

## Firebase

Firebase is initialised (`GoodCompanyApp`), but Analytics collection is off in the manifest and nothing in the
code turns it on or logs an event, so no Firebase data is collected today. If an analytics opt-in is ever added,
this form, the privacy policy and the store description's "no ads" line must be revisited, and Google's per-SDK
mapping applies: <https://firebase.google.com/docs/android/play-data-disclosure>
