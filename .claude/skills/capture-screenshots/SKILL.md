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
   `demo` (load sample friends), `tab=friends|together|agenda|ideas|settings`, `friend=<id>` (e.g. `sample-ana`),
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
- Rendering uses SwiftShader (software WebGL), so allow about 10 s per scene. The default wait is 22 s (string resources load lazily and slowly under software rendering); raise `wait`
  on a scene if fonts haven't loaded yet.
- Platform-only UI (Android dynamic colour, iOS sheets) can't be captured this way. Say so if it's asked for.
