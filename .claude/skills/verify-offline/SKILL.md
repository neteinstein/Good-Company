---
name: verify-offline
description: Prepare a sandboxed/cloud container (Android SDK, Maven Central mirror for HTTP 429s) and run every Good Company check that works without macOS or an emulator - unit tests, coverage gate, ktlint, Android Lint, debug/R8 release APKs, iOS and wasm Kotlin compilation. Use before opening or updating a PR from a cloud session, or when Gradle fails with 429/SDK-missing errors.
---

# Verify in a sandbox

CI on the PR is the real signal. This gets you as close as a Linux container allows.

## 1. Environment (once per container)

```bash
# Android SDK (skip if `sdk.dir` in local.properties already points at one)
if [ ! -d /opt/android-sdk/platforms/android-37.0 ]; then
  mkdir -p /opt/android-sdk/cmdline-tools && cd /opt/android-sdk
  curl -sSo clt.zip https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
  unzip -q clt.zip -d cmdline-tools && mv cmdline-tools/cmdline-tools cmdline-tools/latest && rm clt.zip
  yes | cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null
  cmdline-tools/latest/bin/sdkmanager "platforms;android-37.0" "platform-tools"
  cd -
fi
echo "sdk.dir=/opt/android-sdk" > local.properties   # gitignored
```

If Gradle reports `Received status code 429` from `repo.maven.apache.org`, add this **local-only** init script.
Never commit it.

```bash
mkdir -p ~/.gradle/init.d && cat > ~/.gradle/init.d/mirror.gradle.kts <<'KTS'
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
fun RepositoryHandler.swap() = all {
    if (this is MavenArtifactRepository && url.toString().startsWith("https://repo.maven.apache.org")) url = uri(mirror)
}
beforeSettings {
    pluginManagement.repositories.maven(mirror)
    pluginManagement.repositories.gradlePluginPortal()
    pluginManagement.repositories.swap()
    dependencyResolutionManagement.repositories.swap()
}
allprojects { buildscript.repositories.swap(); repositories.swap() }
KTS
```

## 2. Checks (run from the repo root; use a long timeout or run in the background — first run downloads a lot)

```bash
./gradlew testDebugUnitTest koverXmlReport koverVerify          # tests + coverage gate
./gradlew spotlessCheck :androidApp:lintDebug                    # ktlint + Android Lint
./gradlew :androidApp:assembleDebug
cp androidApp/google-services.json.ci androidApp/google-services.json && ./gradlew :androidApp:assembleRelease   # R8
./gradlew :composeApp:compileKotlinIosArm64 :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileKotlinWasmJs
```

Remove the copied `androidApp/google-services.json` afterwards, or leave it: it's gitignored either way.

## 3. Known to NOT work in sandboxes (say so in the PR test plan)

- `xcodebuild` and iOS framework linking need macOS. Rely on CI's "Build (iOS)" job.
- `:composeApp:wasmJsBrowserDistribution` / `wasmJsBrowserDevelopmentRun`: the Kotlin JS tooling downloads from
  `codeload.github.com`, which proxies often block (HTTP 403). Rely on CI's "Build (Web)" job.
- Running the app or UI tests: there's no emulator (`/dev/kvm` is missing).

## 4. Read results

- Test counts: `find . -name 'TEST-*.xml' -path '*testAndroidHostTest*' | xargs grep -ho 'tests="[0-9]*"'`
- Coverage: `build/reports/kover/report.xml` (HTML in `build/reports/kover/html/`)
- Lint: `androidApp/build/reports/lint-results-debug.txt`
