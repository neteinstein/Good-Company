# CI/CD

Three workflows:

- [`.github/workflows/ci.yml`](.github/workflows/ci.yml) (**CI**) runs on every pull request, on pushes to `main`,
  and on manual dispatch. Newer runs cancel older ones for the same PR or branch.
- [`.github/workflows/test-apk.yml`](.github/workflows/test-apk.yml) (**Test APK**) runs after CI succeeds on a
  push to `main` (every merged PR), and on manual dispatch. It publishes installable test APKs; see
  [Releases](#releases). With the Play secrets it also uploads the Play bundle to Google Play's internal track.
- [`.github/workflows/publish-play-store.yml`](.github/workflows/publish-play-store.yml) (**Publish to Play Store**),
  manual only, promotes that build to closed testing, open testing or production; see [Google Play](#google-play).

## CI jobs (all run in parallel)

| Job | Runner | What it does | Fails when |
|---|---|---|---|
| **Build (Android)** | ubuntu-latest | `assembleDebug`, `assembleRelease` (R8 minify + resource shrink), `assembleSideload`; dumps the release APK's permissions with `aapt2` | compile/R8 error; Play release APK declares SMS, `READ_CALL_LOG`, `READ_CONTACTS`, `WRITE_CONTACTS`, `REQUEST_INSTALL_PACKAGES` or `AD_ID` permissions |
| **Unit tests + coverage** | ubuntu-latest | `testDebugUnitTest` (every module's `commonTest` on the JVM), `koverXmlReport`, `koverHtmlReport`, `koverVerify`; writes a coverage table to the job summary | any test fails; aggregated line coverage below the `koverVerify` minimum (root `build.gradle.kts`) |
| **Lint** | ubuntu-latest | `scripts/check-strings.py`, the Play upload script tests, `spotlessCheck` (ktlint), then `:androidApp:lintDebug` (`checkDependencies = true`, so library modules are linted too) | formatting or ktlint violation; Android Lint error |
| **Build (iOS)** | macos-latest | `xcodebuild` on the hand-authored `iosApp.xcodeproj`, scheme `iosApp` (app + Share Extension + widget), iOS Simulator, `CODE_SIGNING_ALLOWED=NO`. The Run Script phase compiles and embeds `ComposeApp.framework` through Gradle. JDK: Temurin, falling back to Zulu, then the runner's preinstalled JDK, because GitHub release downloads sometimes return 504 | Kotlin/Native compile/link error; Swift compile error; broken pbxproj |
| **Build (Web)** | ubuntu-latest | `:composeApp:wasmJsBrowserDistribution` | Wasm compile or webpack error |

## Releases

Every merge to `main` publishes a GitHub release (marked **latest**, not a pre-release), once CI is green on that commit.
Versions start at `0.1.0` and the patch goes up by one per release (`v0.1.0`, `v0.1.1`, …); the workflow reads the newest
`v0.1.*` release and adds one. Two R8-optimized, signed APKs are attached:

| File | Build type | Use it to test |
|---|---|---|
| `good-company-play.apk` | `release` | exactly what goes to Play: contact picker only, no call log |
| `good-company-sideload.apk` | `sideload` (`.sideload` app id, installs next to the Play build) | everything, incl. full address-book sync, call matching and writing kids/events/notes back to contacts |

**Where to get them:** the [latest release](../../releases/latest); stable links are
`…/releases/latest/download/good-company-play.apk` and `…/good-company-sideload.apk`. Each run also keeps
a `test-apks-<run>` artifact (APKs + R8 `mapping-play.txt`) for 30 days.

**Versions:** `versionCode` = the Test APK run number, `versionName` = `0.1.<n>` (Gradle properties
`goodcompany.versionCode` / `goodcompany.versionName`; local builds keep `1` / `0.1.0`). To move to `0.2.x` or `1.0.0`,
change the `v0.1.` prefix in the "Next version" step of `test-apk.yml`.

**Signing:**

- With the `ANDROID_KEYSTORE_*` secrets set, both APKs are signed with that keystore, so every test build installs
  over the previous one and keeps its data.
- Without them, the workflow passes `-Pgoodcompany.signWithDebugKey=true`, which signs with the runner's debug key.
  The APKs still install, but each runner has its own debug key, so testers must **uninstall the previous test build**
  first, which loses its data. The run shows a warning.

Either way, the workflow verifies both signatures with `apksigner` and re-checks the Play APK's permissions before
publishing.

**In-app updates:** the sideload build's Settings → **App updates** reads the latest release (title
`<version> (build #<run>)`, asset `good-company-sideload.apk`) and offers the build when its run number is above the
installed `versionCode`. So don't rename that title or asset without changing `GitHubReleaseUpdateSource`, and sign every
build with the same keystore, or Android refuses the update (the debug-key fallback differs per runner).

**Safety:** the job runs only for a CI run whose event was `push` on `main`, so a pull request (even from a fork
whose branch is called `main`) can never reach the secrets. It checks out the exact commit CI tested.

Run it by hand from **Actions → Test APK → Run workflow**, for example after adding the signing secrets.

## Google Play

The Play listing itself (text, graphics, Data safety) is in [`store-listing/`](store-listing/README.md). Uploads work
like in neteinstein/sms-redirect, with one difference: other tracks get the **same** build by promotion, not a rebuild,
because `versionCode` is the Test APK run number (the sideload self-update depends on it) and Play never takes a
versionCode twice.

```
merge to main → CI green → Test APK (run N) ─┬─ GitHub release v0.1.x (APKs)
                                             └─ bundleRelease, versionCode N → Play internal track
Actions → Publish to Play Store (by hand)  ───── newest internal release → alpha / beta / production
```

- **Automatic, every merge:** after the GitHub release, Test APK builds `:androidApp:bundleRelease` (the same
  `release` build as `good-company-play.apk`, whose permissions were just checked) and uploads it to the **internal**
  track with its R8 mapping (for readable crash reports in Play Console) and the "What's new" text from
  `store-listing/text/<locale>/release_notes.txt`. It is skipped, with a notice, while `PLAY_STORE_SERVICE_ACCOUNT_JSON`
  is missing or the build is signed with the debug key. A Play failure fails the run, but the GitHub release is
  already out.
- **By hand, other tracks:** **Actions → Publish to Play Store → Run workflow**, pick `track` (`alpha` = closed
  testing, `beta` = open testing, `production`), `from` (normally `internal`) and `status` (`completed` rolls it out;
  `draft` leaves it in Play Console to roll out, e.g. staged, from there). The release notes come from the branch you
  run it from, so update `release_notes.txt` first.
- **The uploader** is `.github/scripts/play_publish.py` (unit tests next to it, run by both workflows): it commits the
  edit with or without `changesNotSentForReview`, whichever Play asks for, creates a draft while the app itself is
  still a draft, and explains errors only Play Console can fix.

### One-time setup

1. **Create the app** in [Play Console](https://play.google.com/console) with package `org.goodcompany.app`, and
   fill in the listing, content rating, Data safety and App access from `store-listing/`.
2. **Upload the first bundle by hand** (Google requires it), signed with your **upload key**:
   `./gradlew :androidApp:bundleRelease` with a `keystore.properties`, or take the AAB from a Test APK run. Enrol in
   Play App Signing when asked.
3. **Use the upload key for CI:** the `ANDROID_KEYSTORE_*` secrets must hold that same upload keystore, or Play
   rejects every upload ("wrong key").
4. **Service account:** in Google Cloud (the project linked under Play Console → Setup → API access), create a service
   account and a JSON key. In Play Console → **Users and permissions → Invite new users**, add the service account's
   e-mail and, under **App permissions**, give it this app with *Release to production, exclude devices, and use Play
   App Signing* and *Release apps to testing tracks*. Service accounts need no invite acceptance.
5. Save the JSON key's full contents as the `PLAY_STORE_SERVICE_ACCOUNT_JSON` secret.

### Troubleshooting

- **403 / "does not have permission":** the service account lacks access to the app (step 4).
- **"has already been used" (versionCode):** that run's build is already on Play; re-run Test APK for a new one.
- **"Package not found":** the app doesn't exist in Play Console yet, or the first manual upload is missing (steps 1–2).
- **"upload key" / wrong signature:** the keystore secrets aren't the upload key Play expects (step 3).
- **"draft app":** until the app's first release is reviewed, Play only accepts drafts; the script creates one and
  says so. Roll it out from Play Console.
- **Upload failed but the GitHub release exists:** re-run the failed Test APK job (same run number, same
  versionCode), or merge again.

## Artifacts

| Artifact | From | Retention |
|---|---|---|
| `android-apks` (CI) | debug, release-unsigned, sideload-unsigned APKs | 7 days |
| `test-apks-<run>` (Test APK) | signed `good-company-play.apk`, `good-company-sideload.apk`, `mapping-play.txt` | 30 days (latest also on the latest release) |
| `r8-mapping` | `mapping.txt` for de-obfuscating release crashes | 30 days |
| `test-and-coverage-reports` | JUnit XML/HTML + Kover XML/HTML | 14 days |
| `lint-reports` | Android Lint HTML/XML/SARIF | 14 days |
| `web-dist` | production Wasm bundle (static site) | 7 days |

## Secrets

| Secret | Used by | Notes |
|---|---|---|
| `GOOGLE_SERVICES_JSON` | Build (Android) on `push`/`workflow_dispatch`, and Test APK | Full contents of the real `google-services.json`. **Pull requests always use the committed placeholder** `androidApp/google-services.json.ci`, so secrets never reach PR builds (including from forks). If the secret is missing, the placeholder is used everywhere. |
| `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | Test APK | Optional. `base64 -w0 your.jks` for the first; the key password falls back to the store password if empty. The workflow writes them into a temporary `keystore.properties`, the same file local signed builds use. Use your **upload key**, never the Play App Signing key: Play uploads (below) are only accepted with the upload key. |
| `PLAY_STORE_SERVICE_ACCOUNT_JSON` | Test APK, Publish to Play Store | Optional. The service account's JSON key ([Google Play](#google-play), setup step 4). Without it, nothing is uploaded to Play. |
| *(planned)* App Store Connect API key, distribution certificate + provisioning profile | iOS archive/upload | Not wired yet. |

Nothing else is secret. The Anthropic API key is supplied by each user in the app and never touches CI.

## Signing status per platform

| Platform | Debug | Release | Distribution |
|---|---|---|---|
| Android (Play `release`) | debug keystore (AGP default) | **unsigned** in PR CI. **Signed** test APK on every merge (keystore secrets, else debug key). Locally, signed if `keystore.properties` exists | test APK on the latest release. Play: the AAB goes to the internal track on every merge (with the Play secrets), other tracks by hand with **Publish to Play Store** |
| Android (`sideload`) | n/a | **unsigned** in PR CI. **Signed** test APK on every merge; same `keystore.properties` mechanism | test APK on the latest release. Never upload this build to Play (it declares `READ_CALL_LOG`, `READ_CONTACTS`, `WRITE_CONTACTS` and `REQUEST_INSTALL_PACKAGES`) |
| iOS | CI builds the simulator app **without signing** | not built in CI | manual: set `TEAM_ID` in `iosApp/Configuration/Signing.xcconfig` (gitignored), Archive in Xcode |
| Web | n/a | unsigned static bundle (`web-dist` artifact) | not deployed automatically. Any static host works (needs `application/wasm` MIME type) |

## Local equivalents

```bash
cp androidApp/google-services.json.ci androidApp/google-services.json   # only needed for release builds
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:assembleSideload
# What the Test APK workflow builds, signed with the debug key when there's no keystore.properties:
./gradlew :androidApp:assembleRelease :androidApp:assembleSideload \
  -Pgoodcompany.versionCode=42 -Pgoodcompany.versionName=0.1.42 -Pgoodcompany.signWithDebugKey=true
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
