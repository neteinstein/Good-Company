#!/usr/bin/env bash
# Assembles a static, bundler-free copy of the wasmJs app in build/web-preview/.
#
# Why: `wasmJsBrowserDistribution` needs the Kotlin JS tooling (webpack/karma via yarn), whose download from
# codeload.github.com is blocked in some sandboxes. The compiled ES modules run fine in a browser on their own;
# the only bare import is `@js-joda/core` (kotlinx-datetime), resolved here with an import map.
set -euo pipefail
cd "$(dirname "$0")/../.."

./gradlew :composeApp:wasmJsDevelopmentExecutableCompileSync :composeApp:wasmJsProcessResources --console=plain -q

OUT=build/web-preview
rm -rf "$OUT" && mkdir -p "$OUT"
cp composeApp/build/compileSync/wasmJs/main/developmentExecutable/kotlin/* "$OUT/"
SKIKO=$(find composeApp/build/compose -type d -name 'skiko-runtime-processed-wasmjs' | head -1)
cp "$SKIKO"/skiko.* "$OUT/"
cp -r composeApp/build/processedResources/wasmJs/main/* "$OUT/"

( cd scripts/screenshots && npm install --silent --no-audit --no-fund >/dev/null )
cp scripts/screenshots/node_modules/@js-joda/core/dist/js-joda.esm.js "$OUT/js-joda.esm.js"

# The SQLite worker (core/data/src/wasmJsMain/sqlite-worker), normally bundled by webpack. Workers don't see the page's
# import map, so its one bare import is pointed at a copy of @sqlite.org/sqlite-wasm (keep its version in step with
# `sqlite-wasm` in gradle/libs.versions.toml).
mkdir -p "$OUT/good-company-sqlite-worker" "$OUT/sqlite-wasm"
cp scripts/screenshots/node_modules/@sqlite.org/sqlite-wasm/dist/* "$OUT/sqlite-wasm/"
sed 's#from "@sqlite.org/sqlite-wasm"#from "../sqlite-wasm/index.mjs"#' core/data/src/wasmJsMain/sqlite-worker/sqlite.worker.js \
  > "$OUT/good-company-sqlite-worker/sqlite.worker.js"

cat > "$OUT/index.html" <<'HTML'
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Good Company (preview)</title>
  <link rel="icon" type="image/svg+xml" href="favicon.svg">
  <script type="importmap">{ "imports": { "@js-joda/core": "./js-joda.esm.js" } }</script>
  <style>html, body { margin: 0; height: 100%; overflow: hidden; background: #FFF8F6; }</style>
</head>
<body><script type="module">import './composeApp.mjs';</script></body>
</html>
HTML
echo "Web preview assembled in $OUT — serve it with: python3 -m http.server -d $OUT 8765"
