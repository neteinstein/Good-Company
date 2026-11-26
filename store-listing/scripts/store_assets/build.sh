#!/usr/bin/env bash
# Rebuilds every Play Store graphic: graphics/icon-512.png, graphics/feature-graphic-1024x500.png
# and screenshots/{phone,sevenInchTablet,tenInchTablet}/*.png.
# The app screens inside them are the real renders in docs/screenshots/ (capture-screenshots skill):
# refresh those first if the UI changed.
# Needs Node + Playwright with Chromium, and Python 3 + Pillow.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
cd "$here"
NODE_PATH="${NODE_PATH:-$(npm root -g)}" node render.js "$@"
# Play asks for a 32-bit PNG (with alpha) for the hi-res icon, and 24-bit PNGs without
# alpha for screenshots and the feature graphic.
python3 - <<'PY'
import glob
from PIL import Image
icon = "../../graphics/icon-512.png"
Image.open(icon).convert("RGBA").save(icon)
for p in glob.glob("../../screenshots/*/*.png") + ["../../graphics/feature-graphic-1024x500.png"]:
    im = Image.open(p)
    if im.mode != "RGB":
        im.convert("RGB").save(p)
PY
