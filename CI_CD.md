# CI/CD

Workflow: [`.github/workflows/ci.yml`](.github/workflows/ci.yml). It runs on every pull request, on pushes to `main`,
and on manual dispatch. Newer runs cancel older ones for the same PR or branch.

## Jobs (all run in parallel)

| Job | Runner | What it does | Fails when |
|---|---|---|---|
| **Build (Android)** | ubuntu-latest | `assembleDebug`, `assembleRelease` (R8 minify + resource shrink), `assembleSideload`; dumps the release APK's permissions with `aapt2` | compile/R8 error; Play release APK declares SMS, `READ_CALL_LOG` or `AD_ID` permissions |
| **Unit tests + coverage** | ubuntu-latest | `testDebugUnitTest` (every module's `commonTest` on the JVM), `koverXmlReport`, `koverHtmlReport`, `koverVerify`; writes a coverage table to the job summary | any test fails; aggregated line coverage below the `koverVerify` minimum (root `build.gradle.kts`) |
| **Lint** | ubuntu-latest | `spotlessCheck` (ktlint), then `:androidApp:lintDebug` (`checkDependencies = true`, so library modules are linted too) | formatting or ktlint violation; Android Lint error |
| **Build (iOS)** | macos-latest | `xcodebuild` on the hand-authored `iosApp.xcodeproj`, scheme `iosApp`, iOS Simulator, `CODE_SIGNING_ALLOWED=NO`. The Run Script phase compiles and embeds `ComposeApp.framework` through Gradle | Kotlin/Native compile/link error; Swift compile error; broken pbxproj |
| **Build (Web)** | ubuntu-latest | `:composeApp:wasmJsBrowserDistribution` | Wasm compile or webpack error |

## Artifacts

| Artifact | From | Retention |
|---|---|---|
| `android-apks` | debug, release-unsigned, sideload-unsigned APKs | 7 days |
| `r8-mapping` | `mapping.txt` for de-obfuscating release crashes | 30 days |
| `test-and-coverage-reports` | JUnit XML/HTML + Kover XML/HTML | 14 days |
| `lint-reports` | Android Lint HTML/XML/SARIF | 14 days |
| `web-dist` | production Wasm bundle (static site) | 7 days |

## Secrets

| Secret | Used by | Notes |
|---|---|---|
| `GOOGLE_SERVICES_JSON` | Build (Android) on `push`/`workflow_dispatch` | Full contents of the real `google-services.json`. **Pull requests always use the committed placeholder** `androidApp/google-services.json.ci`, so secrets never reach PR builds (including from forks). If the secret is missing, the placeholder is used everywhere. |
| *(planned)* `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | release signing | Not wired yet. `androidApp/build.gradle.kts` already reads a gitignored `keystore.properties` if one exists. |
| *(planned)* App Store Connect API key, distribution certificate + provisioning profile | iOS archive/upload | Not wired yet. |

Nothing else is secret. The Anthropic API key is supplied by each user in the app and never touches CI.

## Signing status per platform

| Platform | Debug | Release | Distribution |
|---|---|---|---|
| Android (Play `release`) | debug keystore (AGP default) | **unsigned** in CI. Locally, signed if `keystore.properties` exists | not automated. Upload a signed AAB manually (`./gradlew :androidApp:bundleRelease` with a keystore) |
| Android (`sideload`) | n/a | **unsigned** in CI; same `keystore.properties` mechanism | manual (direct APK). Never upload this build to Play (it declares `READ_CALL_LOG`) |
| iOS | CI builds the simulator app **without signing** | not built in CI | manual: set `TEAM_ID` in `iosApp/Configuration/Signing.xcconfig` (gitignored), Archive in Xcode |
| Web | n/a | unsigned static bundle (`web-dist` artifact) | not deployed automatically. Any static host works (needs `application/wasm` MIME type) |

## Local equivalents

```bash
cp androidApp/google-services.json.ci androidApp/google-services.json   # only needed for release builds
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:assembleSideload
./gradlew testDebugUnitTest koverXmlReport koverVerify
./gradlew spotlessCheck :androidApp:lintDebug
./gradlew :composeApp:wasmJsBrowserDistribution
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build   # macOS only
```

## Conventions

- PRs use [`.github/pull_request_template.md`](.github/pull_request_template.md): Summary / Changes / Test plan / Risk.
  The test plan must say what couldn't be run and why.
- Commit messages follow Conventional Commits (`feat(scope): …`, `fix(scope): …`, `docs(scope): …`, `ci: …`).
- Don't skip, disable or quarantine tests to get a green build, and don't lower the coverage gate.
