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
| Persistence | JSON documents (kotlinx.serialization) in `multiplatform-settings` (SharedPreferences / NSUserDefaults / localStorage) |
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
feature/friends/      Home (coming up, catch up, real friends, A–Z) + friend page (quick actions, tastes, kids, events, plans, AI)
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
8. **Store policy.** Never add SMS permissions. `READ_CALL_LOG` **and `READ_CONTACTS`** belong only in
   `androidApp/src/sideload/AndroidManifest.xml`: the Play build adds people through the system contact picker.
   CI fails if the Play `release` APK declares SMS, call-log, contacts or AD_ID permissions. iOS needs usage strings in
   `Info.plist` and an entry in `PrivacyInfo.xcprivacy` for any newly used API.
9. **Persisted JSON.** Domain models are `@Serializable` and stored as JSON under the keys in
   `core/data/.../storage/PersistentState.kt` (`Keys`). Adding fields with defaults is safe. Renaming or removing fields,
   or changing types, needs a new key suffix (`.v2`) plus a migration. Corrupt data falls back to defaults instead of crashing.
   A new store must also be added to `BackupRepositoryImpl` and the domain `Backup` (export / restore).
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
21. **Known gaps / follow-ups:** the API key is stored in plain app storage (Settings), not Keychain/Keystore
    (it is excluded from Android cloud backup). iOS has no background trigger for the weekly job yet (`BGTaskScheduler`).
    Firebase isn't wired on iOS. Release signing isn't configured in CI (see CI_CD.md). The web share target needs the
    site installed as an app and can't receive images (no service worker yet).

## Skills

Recurring tasks are packaged as Claude Code skills in `.claude/skills/`:
- `new-feature-module`: scaffold a `:feature:*` module that follows these conventions.
- `verify-offline`: set up a sandbox (SDK, Maven mirror) and run every check that works without macOS or an emulator.
- `regenerate-app-icons`: re-render the iOS app icons from `docs/brand/*.svg` (the Android adaptive icon is vector XML).
- `capture-screenshots`: re-render the README screenshots from the real Compose web build in headless Chromium.
