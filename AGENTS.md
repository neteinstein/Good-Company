# AGENTS.md — Good Company

Guidance for coding agents (and humans) working in this repo. CI/CD details live in [CI_CD.md](CI_CD.md).

**Good Company** is a "CRM for friends": friends come from the address book (plus manual entries), and the app
stores what they like and dislike, their kids, important events, when you last met, "meet every N days" rules,
reminders, and AI-generated talking points and weekly event plans.

## Source of truth for features

The product spec is **[docs/PRODUCT_BRIEF.md](docs/PRODUCT_BRIEF.md)**. Its "Brief (verbatim)" section is
canonical; the "Decisions" table records later owner decisions (e.g. *never read SMS*).

- Don't reword, "clean up" or extend the brief. Don't invent features, copy or data that aren't in it.
- When behaviour and the brief disagree, the brief wins unless a dated Decision overrides it.
- New product decisions go into the Decisions table, with a date.
- The brief came from the request that created this repo; no external spreadsheet or design doc exists,
  so there are no origin links to fetch.

## Stack

| Concern | Choice |
|---|---|
| Language / UI | Kotlin 2.4 · Kotlin Multiplatform · Compose Multiplatform 1.12 · Material 3 (CMP `material3` 1.9.0) |
| Targets | Android (AGP 9.5, compile/target SDK 37, min SDK 32), `iosArm64` + `iosSimulatorArm64`, `wasmJs` (web) |
| Architecture | Clean architecture (domain / data / ui) · MVVM · Coroutines + `StateFlow` |
| DI | Koin 4.2 (`koin-compose-viewmodel`) |
| Navigation | JetBrains `navigation-compose` 2.10, type-safe `@Serializable` routes, shared-element transitions |
| Persistence | SQLite through SQLDelight 2 (async drivers; OPFS worker on the web); device settings as JSON in `multiplatform-settings` |
| Networking / AI | Ktor 3 → Claude Messages API (raw HTTP; the official SDK is JVM-only), user-supplied API key |
| Tests | `kotlin.test` + `kotlinx-coroutines-test` in `commonTest`, run on the JVM; Kover for coverage |
| Quality | Spotless + ktlint, Android Lint, R8 (release) |

## Module layout (and why)

```
androidApp/           com.android.application — manifest, MainActivity, Application (Koin + Firebase init), widget, icons, R8 rules
composeApp/           KMP library — App(), navigation, di/AppModule.kt (the ONLY Koin module), di/InitKoin.kt,
                      iOS MainViewController, wasm main() + index.html. Produces ComposeApp.framework for iOS.
core/domain/          Pure Kotlin: models, repository interfaces ("ports"), use cases. No Compose, no platform types. Ever.
core/data/            Repository implementations, JSON persistence, platform data sources (expect/actual), Claude client.
core/ui/              Design system: theme (light/dark/dynamic), shared components, formatters, UI platform hooks.
core/testing/         Fakes + FixedClock + ViewModel test helpers, shared by every module's commonTest.
feature/onboarding/   First-run intro pager, permission primers, "explore with sample friends"
feature/friends/      Home (coming up, catch up, real friends, A–Z) + friend page (quick actions, tastes, kids, events, plans, AI),
                      friend timeline, the "Log" sheet destination
feature/hangouts/     "I'm with…" check-in, history, "last time with these people", catch-up rules, "Who's free for…?"
feature/agenda/       "Coming up" (birthdays, kids, events, overdue meetups) + reminders
feature/ideas/        AI weekly plans
feature/settings/     Theme, circles, API key, notifications (digest, privacy), .vcf import, backup (file + Google Drive), app lock
feature/review/       "Your year in friendship" (story cards + share)
iosApp/               Hand-authored Xcode project: SwiftUI shell + Share Extension + "Who's next" WidgetKit widget
build-logic/          Convention plugins: goodcompany.kmp.library / .kmp.compose / .kmp.feature
```

- **Why `androidApp` is separate from `composeApp`:** AGP 9 forbids applying `org.jetbrains.kotlin.multiplatform`
  and `com.android.application` to the same module. Shared code sits in a KMP *library* (`com.android.kotlin.multiplatform.library`);
  the APK is built by `androidApp`.
- **Why one module per feature:** features build and test in isolation and never depend on each other.
  Cross-feature navigation goes through callbacks wired in `composeApp/ui/navigation/AppNavHost.kt`.
- **commonMain first.** Code goes in `commonMain/kotlin/<package>/` by default. Use `androidMain`/`iosMain`/`wasmJsMain`
  or `expect`/`actual` only for things a platform genuinely can't share. Today that's:
  - `core/data/.../platform/`: contacts, call log, location, local notifications (`create*()` factories)
  - `core/ui/.../platform/`: permission prompts, text-file picker, contact picker, file saver, add-to-calendar,
    device lock (biometrics/PIN), Google Drive sign-in, dynamic colour, `platformCapabilities` /
    `currentPlatformCapabilities()` (use the latter in composables: on Android it reflects the installed build type)
  - entry points (`MainViewController`, wasm `main`)
- Packages: `com.neteinstein.goodcompany.<module path>` (e.g. `...feature.friends.ui.screens.detail`). Inside a module:
  `domain/` (models, use cases, repository interfaces), `data/` (repositories, data sources),
  `ui/{screens,components,theme,navigation}/`.

## Conventions

### MVVM
- A **screen** composable (`FooScreen`) gets its ViewModel with `koinViewModel()`, collects `state` with
  `collectAsStateWithLifecycle()`, and handles platform launchers (permissions, file picker).
- A **content** composable (`FooContent`) is stateless: it takes the state plus callbacks (or an `Actions` data class),
  so it can be previewed and tested. No business logic in composables.
- A ViewModel exposes **one immutable `StateFlow<FooUiState>`** (a `data class`). UI events come back as plain method
  calls (`onToggleClose()`, `onSaveReminder(...)`). One-off messages are a `message: String?` field that the UI
  clears with `onMessageShown()`.
- ViewModels depend on **use cases** only (plus `AppClock` / `IdGenerator`), never on repositories or data sources.
- Time is always injected through `AppClock`; ids through `IdGenerator`. Never call `Clock.System` in logic.

### Strings and languages
- Supported: English (`values/`), European Portuguese (`values-pt/`), Spanish (`values-es/`), French (`values-fr/`),
  Romanian (`values-ro/`). **No user-facing string literals in Kotlin.**
- Each Compose module has its own `src/commonMain/composeResources/values*/strings.xml` and a generated
  `<module namespace>.resources.Res` (e.g. `com.neteinstein.goodcompany.feature.friends.resources.Res`). Shared words,
  dates, notification text, sample data and AI errors live in `:core:ui` (import its `Res` as `CoreRes` in features).
- When you add an English string, add it to all four translations in the same change. Plurals: Romanian needs
  `one`/`few`/`other`; the others `one`/`other`. Use typographic apostrophes (’), not `\'`.
- Domain code never formats text: it emits structured data (`NotificationContent`, `AiFailure.Reason`, `SampleTexts`
  via `SampleTextsProvider`) that `:core:ui` renders (`ResourceNotificationRenderer`, `aiErrorText`,
  `SampleTextsFromResources`). AI requests carry `LanguageProvider.languageTag()` so Claude answers in the UI language.
- Platform strings: Android notification channel in `core/data/src/androidMain/res/values-*`, per-app language list via
  `generateLocaleConfig` in `androidApp`; iOS permission prompts in `iosApp/iosApp/<lang>.lproj/InfoPlist.strings`
  (a `PBXVariantGroup` in the hand-authored project) and `CFBundleLocalizations` in `Info.plist`.

### Koin
- **Every** registration lives in `composeApp/src/commonMain/.../di/AppModule.kt`. Don't add per-platform modules.
  Platform-specific objects are built by calling the `expect` factories from `:core:data` inside that module.
- The bootstrap (`initKoin`) lives in `di/InitKoin.kt`, separate from the module definitions.
- New ViewModel → `viewModelOf(::FooViewModel)`; one with runtime params → `viewModel { (id: FriendId) -> ... }`.
- `AppModuleTest` (composeApp commonTest) resolves every ViewModel. Add yours there.

### Testing
- All shared tests live in `commonTest` and use `kotlin.test`. They run on the JVM via the Android host-test
  compilation. `./gradlew testDebugUnitTest` is an alias that runs `testAndroidHostTest` in every KMP module.
- New domain or data code arrives with tests. CI can verify those on every PR without an emulator.
- Use the fakes in `:core:testing` (`FakeFriendRepository`, `FixedClock`, `SequentialIds`, …).
  `TestGraph` wires every fake repository to every use case, so a ViewModel test builds its subject in one line
  (`FooViewModel(g.observeFoo, g.doBar, g.clock)`) and asserts against `g.friendRepo` etc.
  For ViewModels, use `runViewModelTest { }` and `collecting(vm.state)`.
- ViewModels never hold user-facing text. One-off messages are a sealed `FooMessage` type; the screen turns it into a
  string with a `suspend fun FooMessage.text()` (compose-resources `getString`). Tests assert the message type.
- Coverage is gated: `koverVerify` fails below the minimum set in the root `build.gradle.kts`. Raise it when coverage
  grows; never lower it to get a green build.

### Commits
Use Conventional Commits: `feat(friends): …`, `fix(data): …`, `docs(agents): …`, `build(deps): …`, `ci: …`, `test(domain): …`.
The scope is the module or area.

## Build & verify

```bash
./gradlew testDebugUnitTest                 # all unit tests (JVM)
./gradlew koverXmlReport koverVerify        # coverage report + gate (build/reports/kover/)
./gradlew spotlessCheck                     # ktlint (spotlessApply to fix)
./gradlew :androidApp:lintDebug             # Android Lint incl. library modules
./gradlew :androidApp:assembleDebug         # debug APK
./gradlew :androidApp:assembleRelease       # R8-minified release APK (needs google-services.json, see below)
./gradlew :composeApp:compileKotlinIosArm64 # type-check iOS Kotlin (works on Linux too, see gotchas)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web dev server
./gradlew :composeApp:wasmJsBrowserDistribution     # web production bundle
open iosApp/iosApp.xcodeproj                # iOS (macOS only), scheme "iosApp"
```

## Cloud / sandboxed agents: what you can and can't check

Containers (like Claude Code cloud sessions) often can't build everything. **CI on the PR is the real build signal.**
Seen in practice:

- **No Android SDK by default.** Install command-line tools plus `platforms;android-37.0` and `platform-tools` from
  `dl.google.com`, then write `sdk.dir=` to `local.properties` (gitignored). The `verify-offline` skill has the steps.
- **Maven Central may return HTTP 429** through the agent proxy. Use a *local* Gradle init script
  (`~/.gradle/init.d/`) that points Maven Central at `https://maven-central.storage-download.googleapis.com/maven2/`.
  Never commit that.
- **iOS:** linking frameworks and `xcodebuild` need macOS. With `kotlin.native.enableKlibsCrossCompilation=true`,
  `compileKotlinIosArm64` / `compileKotlinIosSimulatorArm64` **do** run on Linux and type-check all iOS interop code.
- **Web bundle:** `wasmJsBrowserDistribution` / `*Run` need the Kotlin JS tooling, which downloads a package from
  `codeload.github.com`; sandbox proxies may block that (HTTP 403). `compileKotlinWasmJs` works without it.
- **No emulator / device** (no `/dev/kvm`), so UI can't be exercised. Unit tests, lint and APK builds all work.
- Say in the PR's test plan exactly what you ran and what you couldn't run.

## Gotchas

1. **iOS Koin init name.** Swift calls `InitKoinKt.doInitKoin()`. Kotlin/Native's Obj-C exporter prefixes `do` to
   top-level functions whose names start with `init`. Keep the parameterless `initKoin()` overload, since Swift can't
   pass the Kotlin lambda.
2. **Version catalog only.** Every version lives in `gradle/libs.versions.toml`, never inline in a build file
   (convention plugins read it through `libs`). Before bumping, check KMP/CMP compatibility:
   - CMP `material3` is versioned separately from CMP (1.9.0 is the latest *stable*; 1.10+ are alphas).
   - Kotlin, the Compose compiler plugin and KGP move together (`kotlin` version).
   - JetBrains lifecycle/navigation must match what CMP's own artifacts depend on (check the `.module` files).
   - Prefer stable releases; no `-alpha`/`-beta`/`-rc` without a written reason.
3. **No `iosX64`.** It was removed upstream; only `iosArm64` + `iosSimulatorArm64`.
4. **Hand-authored Xcode project.** `iosApp/iosApp.xcodeproj/project.pbxproj` is written by hand, not exported from
   Xcode, and there's no CocoaPods or xcodegen. Three targets: the app, `ShareExtension` and `NextUpWidget` (both pure
   Swift, embedded by the app's "Embed Foundation Extensions" phase). In the app target the "Compile Kotlin Framework" Run Script phase runs
   `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` *before* Compile Sources. `ENABLE_USER_SCRIPT_SANDBOXING = NO`
   is required for that. When adding a Swift file, add a `PBXFileReference`, a `PBXBuildFile`, the group child and the
   Sources entry, all with new unique 24-hex IDs. Settings (team, bundle id, version) live in
   `iosApp/Configuration/Config.xcconfig`; put a personal `TEAM_ID` in the gitignored `Signing.xcconfig`.
   The three targets share the app group `APP_GROUP` (xcconfig → each `Info.plist` `AppGroupId` + `.entitlements`);
   register it in the Apple Developer account. Extensions can't run Kotlin: they talk to the app only through the group
   (see `composeApp/src/iosMain/.../IosBridge.kt`). Their strings are `Localizable.strings` variant groups (five languages).
5. **Compose resources on Android.** AGP 9's KMP library plugin has Android resources **off** by default, and Compose
   Multiplatform only packages `composeResources` (strings, fonts) into the APK's `assets/` when they're on. The
   `goodcompany.kmp.compose` convention sets `androidResources { enable = true }`; never drop it. Without it the web
   and iOS builds and every JVM test still pass, but the Android app crashes on open (first `stringResource()`).
   CI's "Verify APKs bundle Compose resources" step guards this.
6. **Unit-test task name.** With the AGP 9 KMP library plugin the JVM test task is `testAndroidHostTest`; the convention
   plugin adds the `testDebugUnitTest` alias. `testDebugUnitTest` in `androidApp` is the real AGP task.
7. **Secrets are gitignored.** `androidApp/google-services.json` is never committed. CI copies
   `androidApp/google-services.json.ci` (a placeholder) for build validation; real builds use the `GOOGLE_SERVICES_JSON`
   secret. Without the file, the Google Services plugin is skipped and `FirebaseApp.initializeApp` is a no-op.
8. **Store policy.** Never add SMS permissions. `READ_CALL_LOG`, `READ_CONTACTS`, `WRITE_CONTACTS` **and
   `REQUEST_INSTALL_PACKAGES`** (Settings → "App updates") belong only in `androidApp/src/sideload/AndroidManifest.xml`:
   people are added through the system contact picker in both Android builds, and the sideload build's contacts
   permissions exist only for the write-back (see gotcha 22).
   CI fails if the Play `release` APK declares SMS, call-log, contacts, install-packages or AD_ID permissions. iOS needs usage strings in
   `Info.plist` and an entry in `PrivacyInfo.xcprivacy` for any newly used API.
9. **Persisted data.** App data lives in SQLite (gotcha 24); the `.sq` files are the schema. Adding a nullable column
   or one with a default is a new numbered `.sqm` migration. A new table also needs its read/write in `db/*Tables.kt`,
   its name in the matching `*_TABLES` list (observers), and a place in `SqlBackupRepository` / `replaceAllData` and the
   domain `Backup` (export / restore). SQLDelight can't parse a `CHECK` on a column added with `ALTER TABLE … ADD COLUMN`
   when it names the new column, so check such values in the mapper (e.g. `preferred_channel`). A column called `value` is
   generated as `value_`. Device settings that must be readable before the database opens (theme, app lock,
   onboarding, API key) are `DeviceSettings`, JSON under `device_settings.v1`; the rest of `AppSettings` is the
   `user_preferences` row. The old `*.v1` JSON keys in `storage/PersistentState.kt` (`Keys`) are only read by the
   one-off `LegacyJsonImport`.
10. **Claude API.** `AnthropicIdeaGenerator` calls `POST /v1/messages` with model `claude-opus-5-5`,
   `anthropic-version: 2026-10-21`, the `web_search_20260209` server tool, `fallbacks: "default"`
   (beta `server-side-fallback-2026-07-01`) and `anthropic-dangerous-direct-browser-access: true` (needed for the
   wasm build). It handles `pause_turn` and `refusal`. Don't add `temperature`/`top_p` (rejected on this API version)
   or `thinking: disabled`.
11. **Shared elements.** Features tag elements with `Modifier.sharedElement("avatar-<id>")` from `core:ui`; the actual
    `SharedTransitionLayout` wiring is in `AppNavHost`. Don't import navigation types into feature modules.
12. **Edge-to-edge.** Android calls `enableEdgeToEdge()`, iOS uses `.ignoresSafeArea()`; Compose `Scaffold`s handle
    insets. Don't add hard-coded status-bar padding.
13. **Design system first.** Reuse `core:ui` building blocks (`ScreenHeader`, `SectionCard`, `QuickAction`, `ProgressRing`,
    `DateBadge`, `StackedAvatars`, `HeartBurst`, `avatarColors`, `TagEditor`, `ReminderEditor`) and theme tokens
    (`MaterialTheme.colorScheme/typography/shapes`), not ad-hoc colours or sizes. The typeface is Plus Jakarta Sans
    (`core/ui/src/commonMain/composeResources/font`, OFL licence in `docs/licenses/`). Destructive actions get an
    **Undo** snackbar; primary actions get haptics.
14. **Sample data & launch options.** `SampleData` (domain) builds a demo circle whose ids all start with `sample-`
    (Load/Remove use cases; Settings → Your data). The web build reads URL options (`?demo&tab=…&friend=…&theme=…`)
    through `LaunchOptions`. It exists for demos and the screenshot pipeline; mobile builds ignore it.
15. **Screenshots are real renders.** After a visible UI change, refresh `docs/screenshots/` with the
    `capture-screenshots` skill and look at every image. Never hand-edit or fake them.
16. **Notification budget.** Catch-up nudges go through `NudgePolicy` in `NotificationPlanner` (weekly digest or one a day,
    quieter when the app isn't opened). Don't schedule catch-up notifications anywhere else. Birthdays, events, reminders
    and plans are never throttled.
17. **Google Drive backup.** Scope `drive.file` only (`DRIVE_FILE_SCOPE`), one file named `DRIVE_BACKUP_FILE_NAME`,
    content = `ExportBackupUseCase` (never the API key). OAuth clients live in a Google Cloud project, not in the repo
    (see `docs/GOOGLE_DRIVE.md`): Android needs none in code; iOS reads `GOOGLE_DRIVE_IOS_CLIENT_ID` (xcconfig →
    Info.plist `GoogleDriveClientId`); web reads `<meta name="google-drive-client-id">`. Empty → the row is hidden.
    Obj-C helper classes (e.g. the `ASWebAuthenticationSession` anchor) must be `class`, never `object`.
18. **Widgets.** Android: `androidApp/.../widget/NextUpWidget.kt` is plain `RemoteViews` (no Glance); its strings are Android
    resources in `androidApp/src/main/res/values-*` (all five languages). iOS: `iosApp/NextUpWidget` (WidgetKit) reads the
    `WidgetSnapshot` JSON the app writes to the app group (`startWidgetSync`); "We met" opens `goodcompany://met/<id>`.
    Both show what `ObserveHomeWidgetUseCase` returns; keep the two in step (owner: iOS keeps pace with Android and web).
19. **Share into the app.** "Share → Good Company" lands in `ShareInbox` (domain), which opens the "Save to a friend"
    screen. Android: `ACTION_SEND` in `MainActivity` (`ShareIntents`, images downscaled to 1568 px JPEG). iOS: the Share
    Extension writes `ShareInbox/*.json` (+ `.jpg`) into the app group; the app reads it when it becomes active. Web: the
    manifest's `share_target` (installed PWA only) → `?share_title&share_text&share_url` → `LaunchOptions.shared`.
    Shared content is never stored as-is: only the "What's new" line the user saves.
20. **Blogs & feeds.** `FeedParser` (domain) is a tiny RSS/Atom reader; `KtorFeedFetcher` fetches straight from the
    device. Feeds live on `Friend.feeds` (so backup covers them) and refresh at most every 20 h on app start. In the
    browser many feeds fail because of CORS; that's expected.
21. **Sideload self-update.** Settings → "App updates" exists only where the manifest declares `REQUEST_INSTALL_PACKAGES`
    (`PlatformCapabilities.hasSelfUpdate`, so only the sideload build). `GitHubReleaseUpdateSource` reads the rolling
    `test-build` release (title `Test build #<run>`, run number = `versionCode`, asset `good-company-sideload-test.apk`);
    `CheckForUpdateUseCase` compares it with the installed `versionCode`; `AppUpdater` (Android) downloads the APK to
    `cache/updates/` and opens the system installer through the sideload-only FileProvider. Keep the release title and
    asset name in `.github/workflows/test-apk.yml` in step with the source. Android only installs over a build signed with
    the same key (see CI_CD.md "Signing").
22. **Contact write-back (Android sideload only).** `FriendRepositoryImpl` mirrors every friend that has a `contactId` onto
    their contact through the `ContactsWriter` port (`ContactCard.from(friend)` is the projection; a write happens only when
    the card changed, and `syncContacts` backfills friends with details). `AndroidContactsWriter` keeps it all in one raw
    contact the app owns (`SOURCE_ID` = `goodcompany:<friendId>`, no account, so it never syncs to Google) joined to the real
    contact with an aggregation exception. Never edit the user's own contact rows. The mirror
    **never deletes** (owner decision): one row per item (one note, one birthday, one row per kid/event), a changed detail
    updates the app's own earlier row in place (`ContactRowPlanner`, pure and tested; its `Step` has no delete), and a detail
    removed in the app, a cleared friend or a deleted friend leaves the contact as it is. The user's own contact rows are only read.
    `AndroidContactsSource` skips contacts made only of our raw contacts and has `importsNewContacts = false`: people are added with
    the system contact picker (UI asks for READ/WRITE_CONTACTS right before opening it, `hasContactWriteBack`), and a sync only
    refreshes friends that already have a `contactId`, never importing the whole address book. New data that fits on a contact goes into
    `ContactCard` (domain, tested) and `AndroidContactsWriter.rowsFor`; its labels are Android string resources in
    `core/data/src/androidMain/res/values-*` (all five languages). Other platforms use `ContactsWriter.None`.
    What is written (issue #20): relatives as `Relation` rows (partner → `TYPE_PARTNER`, parent → `TYPE_PARENT`; sibling, pet
    and other → `TYPE_CUSTOM` with a localized label, since Android has no sibling type), "introduced by" as `TYPE_REFERRED_BY`,
    and job, lives in, how we met, food, drinks, interests and custom fields as labelled lines in the one note (`NoteLabels`).
    **Emails are never written back** (owner decision); they are read from the contact (`ContactRecord.emails`, `fromContact`)
    and refreshed on sync, while typed ones are kept.
23. **Known gaps / follow-ups:** the API key is stored in plain app storage (Settings), not Keychain/Keystore
    (it is excluded from Android cloud backup). iOS has no background trigger for the weekly job yet (`BGTaskScheduler`).
    Firebase isn't wired on iOS. Release signing isn't configured in CI (see CI_CD.md). The web share target needs the
    site installed as an app and can't receive images (no service worker yet).
24. **SQLite (SQLDelight 2, issue #14).** `:core:data` has `GoodCompanyDatabase`
    (`src/commonMain/sqldelight/`, `generateAsync = true`, so every query is `suspend`), opened lazily through
    `AppDatabase` from the `createDatabaseDriverFactory()` expect/actual. The repositories are
    `repository/SqlRepositories.kt` (mappers in `db/*Tables.kt`). On first open `LegacyJsonImport` moves the old JSON
    documents in, in one transaction with a `db_meta` mark, then deletes the JSON (owner decision 2026-11-13), but only
    when the database is persistent; unreadable keys and a failed import keep the JSON. The JSON `*RepositoryImpl`
    classes remain only as the reference the SQL ones are checked against: both must pass `RepositoryContract`
    (commonTest; the SQL subclass is in androidHostTest).
    Go through `AppDatabase.access`/`transaction`/`observe`, never `get()` directly: the web driver can't run two
    statements at once. Schema changes go in numbered `.sqm` migrations; after one, run
    `generate<SourceSet>GoodCompanyDatabaseSchema` and commit the new `databases/<n>.db` (`verifySqlDelightMigration`
    checks them). iOS: `ComposeApp.framework` is static, so the app target links `-lsqlite3` itself (`OTHER_LDFLAGS`).
    Web: our own worker (`core/data/src/wasmJsMain/sqlite-worker`, official `@sqlite.org/sqlite-wasm`, OPFS
    "SAH pool" VFS, no COOP/COEP needed) replaces SQLDelight's in-memory one. It's an npm `file:` package, so the
    build uses a copy taken at install time: after editing it, bump `version` in its `package.json` (or rerun
    `kotlinNpmInstall`), or the bundle keeps the old worker; the database works in one tab at a time,
    and a second tab falls back to memory (`AppDatabase.isPersistent()` is false; the app shows a "won’t be saved" banner). `index.html?dbcheck` prints a
    persistence smoke test to the console (open count must go up across reloads). Booleans: SQLite stores them as
    0/1 and the worker returns numbers, while the web driver's `getBoolean` expects a JS boolean, so store flags as
    `INTEGER` and convert in the mappers (or prove a `Boolean` column works on the web before relying on it).
25. **Contact photos (issue #15).** `Friend.photo` is a `ContactPhoto` (base64 thumbnail, at most `ContactPhoto.MAX_BYTES`),
    stored in its own `friend_photo` table and carried in backups. It is contact-owned like the birthday: a sync or a re-pick
    replaces it, a contact without one never clears it (`ContactMerger`), and `.vcf` imports only fill a missing one.
    Sources: Android `AndroidContactsSource` (the `Photo.PHOTO` thumbnail of `Contacts.PHOTO_ID`) and both Android pickers
    (best effort through the picker's grant; the Android 17 picker isn't asked for the photo field, so it never turns into
    a field chooser); iOS `CNContactThumbnailImageDataKey` (check `isKeyAvailable` first: an unfetched key is an Obj-C
    exception); `VCardParser` reads inline `PHOTO`, never links. Larger pictures go through the `PhotoShrinker` port
    (`createPhotoShrinker()` in `:core:ui`: Android graphics, Skia on iOS/web) to `MAX_SIDE_PX`. Render with
    `FriendAvatar(friend)` / `StackedAvatars(friends)`. Widgets show the catch-up friend's photo (`WidgetSnapshot.CatchUp.photo`
    on iOS) only when not private. The contact write-back never writes photos. A friend without a photo can get one from the
    gallery (`rememberImagePicker` → `SetFriendPhotoUseCase`): each picker returns an upright JPEG of at most 512 px (UIKit
    redraw on iOS, EXIF rotation on Android, `createImageBitmap` on the web), since the Skia decoder ignores EXIF.
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

## Skills

Recurring tasks are packaged as Claude Code skills in `.claude/skills/`:
- `new-feature-module`: scaffold a `:feature:*` module that follows these conventions.
- `verify-offline`: set up a sandbox (SDK, Maven mirror) and run every check that works without macOS or an emulator.
- `regenerate-app-icons`: re-render the iOS app icons from `docs/brand/*.svg` (the Android adaptive icon is vector XML).
- `capture-screenshots`: re-render the README screenshots from the real Compose web build in headless Chromium.
