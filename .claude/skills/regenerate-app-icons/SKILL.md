---
name: regenerate-app-icons
description: Re-render Good Company's iOS App Store icons (1024x1024 light + dark PNGs) from the SVG sources in docs/brand/, and keep the Android adaptive-icon vectors and web favicon in sync. Use when the brand mark, colours or icon artwork change.
---

# Regenerate app icons

Sources of truth (edit these, never the PNGs):
- `docs/brand/app-icon.svg`: light icon (coral gradient, two hearts)
- `docs/brand/app-icon-dark.svg`: dark-appearance icon

Derived outputs:
| Output | How |
|---|---|
| `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png`, `AppIcon-dark.png` | rendered by the script below |
| `composeApp/src/wasmJsMain/resources/favicon.svg` | copy of the light SVG with `rx="230"` on both full-size `<rect>`s |
| `androidApp/src/main/res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` | hand-kept vector paths. The two heart `pathData` strings are the same as the SVG `<path d>` values (108-dp viewport). Update them if the hearts change |
| `androidApp/src/main/res/values/colors.xml` → `ic_launcher_background` | the SVG's mid gradient stop |

## Steps

```bash
NODE_PATH=$(npm root -g) node scripts/render-app-icons.js   # Playwright + Chromium (preinstalled in cloud containers)
```

Then look at the result before committing (e.g. open the PNGs, or downscale both side by side). Keep the artwork centred
in the 1024 canvas and inside the central ~80% (iOS rounds the corners, and Android's adaptive mask crops to a ~72-dp
circle out of 108 dp). Commit as `chore(brand): regenerate app icons`.
