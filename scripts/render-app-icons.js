// Renders the iOS App Store icons (1024×1024, light + dark) from the SVG sources in docs/brand/.
// Usage (repo root): NODE_PATH=$(npm root -g) node scripts/render-app-icons.js
// Requires Playwright + Chromium (preinstalled in Claude Code cloud containers).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const out = path.join(root, 'iosApp/iosApp/Assets.xcassets/AppIcon.appiconset');
const jobs = [
  ['docs/brand/app-icon.svg', 'AppIcon.png'],
  ['docs/brand/app-icon-dark.svg', 'AppIcon-dark.png'],
];

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1024, height: 1024 }, deviceScaleFactor: 1 });
  for (const [src, name] of jobs) {
    await page.setContent(`<html><body style="margin:0">${fs.readFileSync(path.join(root, src), 'utf8')}</body></html>`);
    await page.locator('svg').screenshot({ path: path.join(out, name) });
    console.log('wrote', path.join(out, name));
  }
  await browser.close();
})();
