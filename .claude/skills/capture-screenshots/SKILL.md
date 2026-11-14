---
name: capture-screenshots
description: Regenerate Good Company's README screenshots (docs/screenshots/*.png and banner.png) by rendering the real Compose web build in headless Chromium with sample data - works in cloud sandboxes with no emulator or Mac. Use after any visible UI change, or when asked for screenshots/previews of the app.
---

# Capture screenshots

The README images are **real renders** of the shared Compose UI (the same code that runs on Android and iOS), produced
from the wasmJs build. No bundler is needed, so this works where `wasmJsBrowserDistribution` is blocked.

## Steps (from the repo root)

1. Assemble a static web build into `build/web-preview/` (compiles wasm, copies skiko and resources, and resolves
   `@js-joda/core` with an import map):
   ```bash
   ./scripts/screenshots/assemble-web.sh
   ```
   If Gradle is OOM-killed, stop stale daemons first: `pkill -f KotlinCompileDaemon; ./gradlew --stop`.
2. Capture every scene, or just some by name:
   ```bash
   NODE_PATH=$(npm root -g) node scripts/screenshots/capture.js
   NODE_PATH=$(npm root -g) node scripts/screenshots/capture.js home friend-dark
   ```
   Scenes live in `scripts/screenshots/capture.js`. Each is a URL query parsed by
   `composeApp/src/commonMain/.../LaunchOptions.kt`:
   `demo` (load sample friends), `ai` (turn the AI switch on), `tab=today|people|circles|plans|settings` (the old
   `friends|together|agenda|ideas` still map to the tab that replaced them), `friend=<id>` (e.g. `sample-ana`),
   `theme=light|dark`, `onboarding`. `clicks: [[x, y], …]` taps CSS-pixel coordinates after load (used for the
   check-in sheet). Phone viewport is 393×852 at 2× scale.
3. Rebuild the README banner (needs Pillow):
   ```bash
   python3 scripts/screenshots/compose_banner.py
   ```
4. **Look at every image** before committing (open the PNGs). Check for clipped text, overflow, empty states and hover
   artefacts. Fix the UI, not the screenshot.
5. Commit as `docs(readme): refresh screenshots`.

## Notes

- Sample data comes from `core/domain/.../usecase/SampleData.kt` and is dated relative to today, so there's always
  something coming up. Don't hand-edit screenshots or invent UI that isn't in the code.
- Rendering uses SwiftShader (software WebGL). Loading the sample data through the OPFS worker can take from under a
  minute to several minutes, so `demo` scenes wait until the onboarding pager is gone (its "Next" button's pixels;
  7-minute cap, then a `WARNING` line) instead of a fixed time. `wait` (or `WAIT_MS`) is the minimum wait before that.
  `PORT=8766` lets two captures run side by side. Scripted taps (`clicks`) wait 4 s each so sheets finish opening;
  the first tap on the canvas can be swallowed, so start with a tap on empty space.
- Playwright isn't a repo dependency: install a version whose Chromium revision is already in
  `~/Library/Caches/ms-playwright` (e.g. `playwright@1.49.1` for `chromium-1148`) and point `NODE_PATH` at it.
- Platform-only UI (Android dynamic colour, iOS sheets) can't be captured this way. Say so if it's asked for.
