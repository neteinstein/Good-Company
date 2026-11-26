// Renders every Play Store graphic from store.html with headless Chromium (Playwright).
//   node render.js                     -> all assets into store-listing/graphics + screenshots
//   node render.js --only 02-friend    -> just that screenshot (all three sizes)
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const ROOT = path.resolve(__dirname, '..', '..');
const OUT = {
  graphics: path.join(ROOT, 'graphics'),
  phone: path.join(ROOT, 'screenshots', 'phone'),
  seven: path.join(ROOT, 'screenshots', 'sevenInchTablet'),
  ten: path.join(ROOT, 'screenshots', 'tenInchTablet'),
};

(async () => {
  const onlyAt = process.argv.indexOf('--only');
  const only = onlyAt > 0 ? process.argv[onlyAt + 1] : null;
  Object.values(OUT).forEach(d => fs.mkdirSync(d, { recursive: true }));
  // The fonts are loaded from the repo (core/ui), which Chromium only allows from a file:// page with this flag.
  const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
  const url = 'file://' + path.join(__dirname, 'store.html');

  async function shoot(kind, index, width, height, scale, file) {
    const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: scale });
    page.on('pageerror', e => { console.error('PAGEERR', e.message); process.exit(1); });
    await page.goto(url);
    await page.evaluate(([k, i]) => window.renderAsset(k, i), [kind, index]);
    await page.evaluate(() => document.fonts.ready);
    await page.evaluate(() => Promise.all([...document.images].map(i => i.decode())));
    await page.waitForTimeout(150);
    await page.screenshot({ path: file, clip: { x: 0, y: 0, width, height }, omitBackground: false });
    await page.close();
    console.log('Wrote', path.relative(ROOT, file));
  }

  if (!only) {
    await shoot('icon', 0, 512, 512, 1, path.join(OUT.graphics, 'icon-512.png'));
    await shoot('feature', 0, 1024, 500, 1, path.join(OUT.graphics, 'feature-graphic-1024x500.png'));
  }
  const page = await browser.newPage();
  await page.goto(url);
  const keys = await page.evaluate(() => window.SHOT_KEYS);
  await page.close();
  for (let i = 0; i < keys.length; i++) {
    if (only && keys[i] !== only) continue;
    const name = keys[i] + '.png';
    await shoot('phone', i, 1080, 1920, 1, path.join(OUT.phone, name));
    await shoot('tablet', i, 1920, 1080, 1, path.join(OUT.seven, name));
    await shoot('tablet', i, 1920, 1080, 4 / 3, path.join(OUT.ten, name));
  }
  await browser.close();
})();
