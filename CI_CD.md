# CI/CD

Two workflows:

- [`.github/workflows/ci.yml`](.github/workflows/ci.yml) (**CI**) runs on every pull request, on pushes to `main`,
  and on manual dispatch. Newer runs cancel older ones for the same PR or branch.
- [`.github/workflows/test-apk.yml`](.github/workflows/test-apk.yml) (**Test APK**) runs after CI succeeds on a
  push to `main` (every merged PR), and on manual dispatch. It publishes installable test APKs; see
  [Test builds](#test-builds).

## CI jobs (all run in parallel)

| Job | Runner | What it does | Fails when |
|---|---|---|---|
| **Build (Android)** | ubuntu-latest | `assembleDebug`, `assembleRelease` (R8 minify + resource shrink), `assembleSideload`; dumps the release APK's permissions with `aapt2` | compile/R8 error; Play release APK declares SMS, `READ_CALL_LOG`, `READ_CONTACTS`, `WRITE_CONTACTS`, `REQUEST_INSTALL_PACKAGES` or `AD_ID` permissions |
| **Unit tests + coverage** | ubuntu-latest | `testDebugUnitTest` (every module's `commonTest` on the JVM), `koverXmlReport`, `koverHtmlReport`, `koverVerify`; writes a coverage table to the job summary | any test fails; aggregated line coverage below the `koverVerify` minimum (root `build.gradle.kts`) |
| **Lint** | ubuntu-latest | `spotlessCheck` (ktlint), then `:androidApp:lintDebug` (`checkDependencies = true`, so library modules are linted too) | formatting or ktlint violation; Android Lint error |
| **Build (iOS)** | macos-latest | `xcodebuild` on the hand-authored `iosApp.xcodeproj`, scheme `iosApp` (app + Share Extension + widget), iOS Simulator, `CODE_SIGNING_ALLOWED=NO`. The Run Script phase compiles and embeds `ComposeApp.framework` through Gradle. JDK: Temurin, falling back to Zulu, then the runner's preinstalled JDK, because GitHub release downloads sometimes return 504 | Kotlin/Native compile/link error; Swift compile error; broken pbxproj |
| **Build (Web)** | ubuntu-latest | `:composeApp:wasmJsBrowserDistribution` | Wasm compile or webpack error |

## Test builds

Every merge to `main` produces two installable, R8-optimized test APKs, once CI is green on that commit:

| File | Build type | Use it to test |
|---|---|---|
| `good-company-play-test.apk` | `release` | exactly what goes to Play: contact picker only, no call log |
| `good-company-sideload-test.apk` | `sideload` (`.sideload` app id, installs next to the Play build) | everything, incl. full address-book sync, call matching and writing kids/events/notes back to contacts |

**Where to get them:** the rolling pre-release
[`test-build`](../../releases/tag/test-build) is replaced on every merge, so the download links stay the same:
`…/releases/download/test-build/good-company-play-test.apk` and `…/good-company-sideload-test.apk`. Each run also keeps
a `test-apks-<run>` artifact (APKs + R8 `mapping-play.txt`) for 30 days, so older builds stay available for bisecting.

**Versions:** `versionCode` = the Test APK run number, `versionName` = `1.0.0-test.<run>` (Gradle properties
`goodcompany.versionCode` / `goodcompany.versionSuffix`; local builds keep `1` / `1.0.0`).

**Signing:**

- With the `ANDROID_KEYSTORE_*` secrets set, both APKs are signed with that keystore, so every test build installs
  over the previous one and keeps its data.
- Without them, the workflow passes `-Pgoodcompany.signWithDebugKey=true`, which signs with the runner's debug key.
  The APKs still install, but each runner has its own debug key, so testers must **uninstall the previous test build**
  first, which loses its data. The run shows a warning.

Either way, the workflow verifies both signatures with `apksigner` and re-checks the Play APK's permissions before
publishing.

**In-app updates:** the sideload build's Settings → **App updates** reads the `test-build` release (title
`Test build #<run>`, asset `good-company-sideload-test.apk`) and offers the build when its run number is above the
installed `versionCode`. So don't rename that title or asset without changing `GitHubReleaseUpdateSource`, and sign every
build with the same keystore, or Android refuses the update (the debug-key fallback differs per runner).

**Safety:** the job runs only for a CI run whose event was `push` on `main`, so a pull request (even from a fork
whose branch is called `main`) can never reach the secrets. It checks out the exact commit CI tested.

Run it by hand from **Actions → Test APK → Run workflow**, for example after adding the signing secrets.

## Artifacts

| Artifact | From | Retention |
|---|---|---|
| `android-apks` (CI) | debug, release-unsigned, sideload-unsigned APKs | 7 days |
| `test-apks-<run>` (Test APK) | signed `good-company-play-test.apk`, `good-company-sideload-test.apk`, `mapping-play.txt` | 30 days (latest also on the `test-build` release) |
| `r8-mapping` | `mapping.txt` for de-obfuscating release crashes | 30 days |
| `test-and-coverage-reports` | JUnit XML/HTML + Kover XML/HTML | 14 days |
| `lint-reports` | Android Lint HTML/XML/SARIF | 14 days |
| `web-dist` | production Wasm bundle (static site) | 7 days |

## Secrets

| Secret | Used by | Notes |
|---|---|---|
| `GOOGLE_SERVICES_JSON` | Build (Android) on `push`/`workflow_dispatch`, and Test APK | Full contents of the real `google-services.json`. **Pull requests always use the committed placeholder** `androidApp/google-services.json.ci`, so secrets never reach PR builds (including from forks). If the secret is missing, the placeholder is used everywhere. |
| `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | Test APK | Optional. `base64 -w0 your.jks` for the first; the key password falls back to the store password if empty. The workflow writes them into a temporary `keystore.properties`, the same file local signed builds use. Use your upload or a dedicated test key, never the Play App Signing key. Play store uploads remain manual. |
| *(planned)* App Store Connect API key, distribution certificate + provisioning profile | iOS archive/upload | Not wired yet. |

Nothing else is secret. The Anthropic API key is supplied by each user in the app and never touches CI.

## Signing status per platform

| Platform | Debug | Release | Distribution |
|---|---|---|---|
| Android (Play `release`) | debug keystore (AGP default) | **unsigned** in PR CI. **Signed** test APK on every merge (keystore secrets, else debug key). Locally, signed if `keystore.properties` exists | test APK on the `test-build` pre-release. Play: upload a signed AAB manually (`./gradlew :androidApp:bundleRelease` with a keystore) |
| Android (`sideload`) | n/a | **unsigned** in PR CI. **Signed** test APK on every merge; same `keystore.properties` mechanism | test APK on the `test-build` pre-release. Never upload this build to Play (it declares `READ_CALL_LOG`, `READ_CONTACTS`, `WRITE_CONTACTS` and `REQUEST_INSTALL_PACKAGES`) |
| iOS | CI builds the simulator app **without signing** | not built in CI | manual: set `TEAM_ID` in `iosApp/Configuration/Signing.xcconfig` (gitignored), Archive in Xcode |
| Web | n/a | unsigned static bundle (`web-dist` artifact) | not deployed automatically. Any static host works (needs `application/wasm` MIME type) |

## Local equivalents

```bash
cp androidApp/google-services.json.ci androidApp/google-services.json   # only needed for release builds
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:assembleSideload
# What the Test APK workflow builds, signed with the debug key when there's no keystore.properties:
./gradlew :androidApp:assembleRelease :androidApp:assembleSideload \
  -Pgoodcompany.versionCode=42 -Pgoodcompany.versionSuffix=test.42 -Pgoodcompany.signWithDebugKey=true
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
