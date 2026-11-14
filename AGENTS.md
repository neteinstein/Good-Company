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
- **Don't read the whole Decisions table.** Read the verbatim brief (short), then use the "Index by area" at
  the top of the file to read only the rows for the area you're changing. Skip rows marked *superseded*.
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
feature/friends/      People tab (one list by closeness, "Your people" card) + friend page (quick actions, tastes, kids, events, plans, AI),
                      friend timeline, the "Log" sheet destination
feature/hangouts/     "I'm with…" check-in, history, "last time with these people", circles + the circle editor, "Who's free for…?", gift lists
feature/agenda/       Today (the first tab: check-in, the next 7 days, catch-ups) + "Everything coming up" (birthdays, kids, events, overdue meetups) + reminders
feature/ideas/        AI weekly plans
feature/settings/     Theme, closeness rhythms, API key, notifications (digest, privacy), .vcf import, backup (file + Google Drive), app lock
feature/review/       "Your year in friendship" (story cards + share), the network map and the "Your people" dashboard
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
- Every English string has all four translations in the same PR. While building, write English only. Once the
  wording has settled, add the translations in one pass at the end (`add-strings`). `python3 scripts/check-strings.py`
  lists missing and extra keys and fails when any exist. Plurals: Romanian needs `one`/`few`/`other`; the others
  `one`/`other`. Use typographic apostrophes (’), not `\'`.
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

### While iterating: the fast loop

Gradle runs on this repo cost minutes, so run the smallest task that proves the change. Don't run the full suite
after every edit.

```bash
./gradlew :core:domain:testAndroidHostTest            # one module's tests (swap in the module you touched)
./gradlew :feature:friends:testAndroidHostTest --tests '*FriendDetailViewModelTest*'   # one test class
./gradlew :feature:friends:compileAndroidMain         # compile one module, no tests
```

- Test the modules you changed plus `:composeApp` if you touched Koin or navigation (`AppModuleTest`).
- Run `./gradlew spotlessApply` once at the end, not between edits.
- Write English strings while building and fill in the translations once at the end (see "Strings and languages").
  `scripts/check-strings.py` lists what's missing.
- **Before you push** (once): `./gradlew testDebugUnitTest koverVerify spotlessCheck`. Leave Android Lint, the
  release APK and the iOS and web builds to CI unless you changed manifests, `iosApp/`, `expect`/`actual` code or a
  platform source set. When you did, compile that platform (e.g. `:composeApp:compileKotlinIosSimulatorArm64`).
- Prefer working in an existing worktree over cutting a fresh one: a new checkout starts with a cold build.

### Everything (what CI runs)

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

Short rules here; the full notes for each area live in a skill (see [Skills](#skills)). **Load the matching skill
before changing that area**, and put new area-specific notes in that skill, not here. Numbers stay stable, so
"gotcha N" references keep working.

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
4. **Hand-authored Xcode project.** `iosApp/iosApp.xcodeproj/project.pbxproj` is written by hand (no Xcode export,
   CocoaPods or xcodegen); three targets share the app group. Edit it only with `ios-project-edit`.
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
   `REQUEST_INSTALL_PACKAGES`** (Settings → "App updates") belong only in `androidApp/src/sideload/AndroidManifest.xml`.
   The sideload build (and iOS) offer the contact list with checkboxes as "Pick from contacts" (decision D2, issue #35: a refused permission falls back to the system picker; `ChooseContactsRoute`, gated on
   `PlatformCapabilities.hasContactList` = "the manifest declares READ_CONTACTS") plus the write-back (gotcha 22).
   **The Play build stays picker-only** until Google Play's `READ_CONTACTS` policy has been checked; enabling the list
   there is a manifest + CI change, no code change.
   CI fails if the Play `release` APK declares SMS, call-log, contacts, install-packages or AD_ID permissions. iOS needs usage strings in
   `Info.plist` and an entry in `PrivacyInfo.xcprivacy` for any newly used API.
9. **Persisted data.** Every stored-data change (table, column, `CHECK`, backup field, device setting) follows
   `persistence-change`: numbered `.sqm` migration, committed schema snapshot, `*_TABLES`, backup export/restore,
   migration and contract tests.
10. **Claude API.** Raw HTTP to `POST /v1/messages`; no `temperature`/`top_p` or `thinking: disabled`. Details in
    `area-ai`.
11. **Shared elements.** Features tag elements with `Modifier.sharedElement("avatar-<id>")` from `core:ui`; the actual
    `SharedTransitionLayout` wiring is in `AppNavHost`. Don't import navigation types into feature modules.
12. **Edge-to-edge.** Android calls `enableEdgeToEdge()`, iOS uses `.ignoresSafeArea()`; Compose `Scaffold`s handle
    insets. Don't add hard-coded status-bar padding.
13. **Design system first.** Reuse `core:ui` building blocks (`ScreenHeader`, `SectionCard`, `QuickAction`, `ProgressRing`,
    `DateBadge`, `StackedAvatars`, `HeartBurst`, `avatarColors`, `TagEditor`, `ReminderEditor`, `SaveSheetHeader`) and theme tokens
    (`MaterialTheme.colorScheme/typography/shapes`), not ad-hoc colours or sizes. The typeface is Plus Jakarta Sans
    (`core/ui/src/commonMain/composeResources/font`, OFL licence in `docs/licenses/`). Destructive actions get an
    **Undo** snackbar (`showUndo`, hosted by `AppSnackbarHost`: never call `showSnackbar` directly); primary actions get haptics.
14. **Sample data & launch options.** `SampleData` (domain) builds a demo set of friends whose ids all start with `sample-`
    (Load/Remove use cases; Settings → Your data). The web build reads URL options (`?demo&tab=…&friend=…&theme=…`)
    through `LaunchOptions`. It exists for demos and the screenshot pipeline; mobile builds ignore it.
15. **Screenshots are real renders.** `docs/screenshots/` is refreshed in its own `docs(screenshots)` PR with
    the `capture-screenshots` skill, after a batch of UI changes has merged. It is not part of each feature PR. In a
    feature PR, list the screens that changed under "Screenshots to refresh" in the description. Look at every image
    when you do refresh them, and never hand-edit or fake them.
16. **Notification budget.** Catch-up nudges go only through `NudgePolicy` in `NotificationPlanner`; birthdays, events,
    reminders and plans are never throttled. Details in `area-notifications-widgets`.
17. **Google Drive backup.** Scope `drive.file` only, one file, never the API key. Details in `area-backup-sync`.
18. **Widgets.** Android `RemoteViews` and iOS WidgetKit show the same `ObserveHomeWidgetUseCase`; keep them in step.
    Details in `area-notifications-widgets`.
19. **Share into the app.** One `ShareInbox` fed by three platforms; the app never fetches a shared link and keeps only
    photo thumbnails. Details in `area-sharing-gifts`.
20. **Blogs & feeds.** Fetched from the device, at most every 20 h; CORS failures on the web are expected. Details in
    `area-backup-sync`.
21. **Sideload self-update.** Only in the sideload build; tied to the `test-build` release title and asset in
    `test-apk.yml`. Details in `area-backup-sync`.
22. **Contact write-back (Android sideload only).** The mirror never deletes, never edits the user's own contact rows
    and never writes emails or photos. Details in `area-contacts`.
23. **Known gaps / follow-ups:** the API key is stored in plain app storage (Settings), not Keychain/Keystore
    (it is excluded from Android cloud backup). iOS has no background trigger for the weekly job yet (`BGTaskScheduler`).
    Firebase isn't wired on iOS. Release signing isn't configured in CI (see CI_CD.md). The web share target needs the
    site installed as an app and can't receive images (no service worker yet).
24. **SQLite.** Always go through `AppDatabase.access`/`transaction`/`observe`, never `get()` (the web driver can't run
    two statements at once); flags are `INTEGER`. Details in `persistence-change`.
25. **Contact photos.** Thumbnails only, contact-owned like the birthday, never written back. Details in
    `area-contacts`.
26. **Timeline and logging.** All logging goes through `LogInteractionUseCase` (undo: `UndoLogUseCase`). Details in
    `area-logging`.
27. **Closeness and circles.** Closeness (`Closeness`, stored as `circle`) is not a circle (`FriendGroup`); never rename
    stored names. Details in `area-people`.
28. **App-icon shortcuts.** `goodcompany://` links parsed by `AppLink`; none while app lock or private notifications are
    on. Details in `area-notifications-widgets`.
29. **Named places.** GPS only: never call a place-search, geocoding or map service. Details in `area-logging`.
30. **Links between people.** A `PersonLink` is stored once; read the other side with `LinkType.inverse`. Details in
    `area-people`.
31. **Kids are people.** A kid is a `Friend` with a `CHILD` link; `Friend.kids` is legacy. Details in `area-people`.
32. **Calendar suggestions.** Opt-in; events stay in memory, never stored, backed up or sent to the AI. Details in
    `area-logging`.
33. **AI switch.** Every AI entry point checks `AppSettings.usableAiKey` / `aiEnabled`; nothing AI shows while it is
    off. Details in `area-ai`.
34. **Network map and "Your people".** Pure domain code, no graph library, only "your people". Details in
    `area-people`.
35. **Automatic kinship.** Proposed, never set automatically; reuse `ParseKinshipQueryUseCase`. Details in
    `area-people`.
36. **Gift lists.** One gift store (the person's `Gift`s); lists point at them; never guess a currency. Details in
    `area-sharing-gifts`.

## Skills

**Say which skills you loaded.** When you start implementing something, tell the user in one line which skills you
loaded and why, e.g. `Skills: persistence-change (new table), area-people (links)`, or `Skills: none`. If you load
another one later, say so when you do. Repeat the final list in the PR description, in the test plan under
**Skills loaded**.

Recurring tasks are packaged as Claude Code skills in `.claude/skills/`:
- `new-feature-module`: scaffold a `:feature:*` module that follows these conventions.
- `verify-offline`: set up a sandbox (SDK, Maven mirror) and run every check that works without macOS or an emulator.
- `regenerate-app-icons`: re-render the iOS app icons from `docs/brand/*.svg` (the Android adaptive icon is vector XML).
- `capture-screenshots`: re-render the README screenshots from the real Compose web build in headless Chromium.
- `build-in-sequence`: implement a list of planned issues one session at a time, each branch cut from the previous one
  (stacked PRs), with every question asked up front so the sessions run unattended.

Checklists for changes that keep coming back:
- `persistence-change`: a table, column, `CHECK`, backup field or setting, end to end (gotchas 9, 24).
- `new-screen`: a screen or sheet in an existing feature module (ViewModel, route, Koin, tests).
- `add-strings`: user-facing text in all five languages, and which `strings.xml` it belongs in.
- `refresh-branch`: merge `main` (or a stack parent) into a branch; renumber colliding migrations.
- `ios-project-edit`: edit the hand-authored `project.pbxproj`, Info.plist, entitlements (gotcha 4).

Area notes (load before touching the area):
- `area-contacts`: picker/list, sync, photos, sideload write-back (gotchas 22, 25).
- `area-people`: closeness vs. circles, person links, kids, kinship, network map (27, 30, 31, 34, 35).
- `area-logging`: Log sheet, timeline, places, calendar suggestions (26, 29, 32).
- `area-sharing-gifts`: share into the app, attachments, gifts and gift lists (19, 36).
- `area-notifications-widgets`: nudge budget, widgets, app-icon shortcuts, deep links (16, 18, 28).
- `area-ai`: the Claude client and the AI switch (10, 33).
- `area-backup-sync`: Drive backup, feeds, sideload self-update (17, 20, 21).
