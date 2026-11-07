"""Builds docs/screenshots/banner.png: a row of phone screenshots on the brand gradient for the README header.

Usage (repo root): python3 scripts/screenshots/compose_banner.py   (needs Pillow)
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
SHOTS = ROOT / "docs" / "screenshots"
ORDER = ["home", "friend", "checkin", "agenda-dark", "ideas"]

W, H = 2400, 1200
PHONE_W = 400
RADIUS = 46


def gradient(w, h, stops):
    base = Image.new("RGB", (w, h))
    px = base.load()
    for x in range(w):
        for y in range(h):
            t = (x / w * 0.7 + y / h * 0.3)
            for i in range(len(stops) - 1):
                a, b = stops[i], stops[i + 1]
                if a[0] <= t <= b[0]:
                    f = (t - a[0]) / (b[0] - a[0])
                    px[x, y] = tuple(int(a[1][c] + (b[1][c] - a[1][c]) * f) for c in range(3))
                    break
    return base


def rounded(im, r):
    mask = Image.new("L", im.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, *im.size), r, fill=255)
    out = Image.new("RGBA", im.size)
    out.paste(im, (0, 0), mask)
    return out


def main():
    bg = gradient(W, H, [(0.0, (255, 138, 128)), (0.55, (180, 55, 47)), (1.0, (106, 61, 232))]).convert("RGBA")
    phones = []
    for name in ORDER:
        im = Image.open(SHOTS / f"{name}.png").convert("RGB")
        h = int(im.height * PHONE_W / im.width)
        phones.append(rounded(im.resize((PHONE_W, h), Image.LANCZOS), RADIUS))
    gap = (W - PHONE_W * len(phones)) // (len(phones) + 1)
    for i, ph in enumerate(phones):
        x = gap + i * (PHONE_W + gap)
        # Stagger: centre phone highest.
        y = (H - ph.height) // 2 + [40, -10, -50, -10, 40][i]
        shadow = Image.new("RGBA", (ph.width + 80, ph.height + 80), (0, 0, 0, 0))
        ImageDraw.Draw(shadow).rounded_rectangle((40, 50, ph.width + 40, ph.height + 40), RADIUS, fill=(40, 0, 20, 110))
        shadow = shadow.filter(ImageFilter.GaussianBlur(24))
        bg.alpha_composite(shadow, (x - 40, y - 40))
        frame = Image.new("RGBA", (ph.width + 16, ph.height + 16), (0, 0, 0, 0))
        ImageDraw.Draw(frame).rounded_rectangle((0, 0, ph.width + 15, ph.height + 15), RADIUS + 8, fill=(20, 12, 12, 255))
        bg.alpha_composite(frame, (x - 8, y - 8))
        bg.alpha_composite(ph, (x, y))
    bg.convert("RGB").save(SHOTS / "banner.png", optimize=True)
    print("wrote", SHOTS / "banner.png")


if __name__ == "__main__":
    main()
