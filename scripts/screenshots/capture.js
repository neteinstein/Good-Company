// Captures README screenshots from build/web-preview (run assemble-web.sh first).
// Usage (repo root): NODE_PATH=$(npm root -g) node scripts/screenshots/capture.js [sceneName...]
// Each scene is a URL query understood by composeApp/.../LaunchOptions.kt.
const { chromium } = require('playwright');
const http = require('http');
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const root = path.resolve(__dirname, '../..');
const site = path.join(root, 'build/web-preview');
const out = path.join(root, 'docs/screenshots');
const PORT = Number(process.env.PORT) || 8765;

const phone = { width: 393, height: 852 };
const scenes = [
  { name: 'onboarding', query: '?onboarding&theme=light', viewport: phone },
  { name: 'home', query: '?demo&theme=light&tab=people', viewport: phone },
  { name: 'friend', query: '?demo&theme=light&friend=sample-ana', viewport: phone },
  { name: 'together', query: '?demo&theme=light&tab=circles', viewport: phone },
  // Taps the "I'm with…" card on Today (CSS px) to show the check-in sheet.
  { name: 'checkin', query: '?demo&theme=light&tab=today', viewport: phone, clicks: [[300, 30], [335, 144], [76, 316], [240, 364]] },
  { name: 'agenda', query: '?demo&theme=light&tab=today', viewport: phone },
  { name: 'ideas', query: '?demo&ai&theme=light&tab=plans', viewport: phone },
  { name: 'settings', query: '?demo&theme=light&tab=settings', viewport: phone },
  { name: 'home-dark', query: '?demo&theme=dark&tab=people', viewport: phone },
  { name: 'friend-dark', query: '?demo&theme=dark&friend=sample-sofia', viewport: phone },
  { name: 'together-dark', query: '?demo&theme=dark&tab=circles', viewport: phone },
  { name: 'agenda-dark', query: '?demo&theme=dark&tab=today', viewport: phone },
  { name: 'desktop', query: '?demo&theme=light', viewport: { width: 1280, height: 800 } },
  { name: 'review', query: '?demo&theme=light&review', viewport: phone, wait: 92000 },
  { name: 'story', query: '?demo&theme=light&story=sample-ana', viewport: phone },
  { name: 'share', query: '?demo&theme=light&share_text=Marta%20just%20posted%3A%20last%20day%20at%20the%20agency%2C%20starting%20at%20a%20ceramics%20studio%20next%20month!&share_url=https%3A%2F%2Finstagram.com%2Fp%2Fexample', viewport: phone },
  // Localised renders: the browser locale drives the app language.
  { name: 'home-pt', query: '?demo&theme=light', viewport: phone, locale: 'pt-PT' },
  { name: 'friend-es', query: '?demo&theme=light&friend=sample-rui', viewport: phone, locale: 'es-ES' },
];

const types = { '.html': 'text/html', '.mjs': 'text/javascript', '.js': 'text/javascript', '.wasm': 'application/wasm', '.svg': 'image/svg+xml', '.ttf': 'font/ttf', '.json': 'application/json' };
const server = http.createServer((req, res) => {
  const file = path.join(site, decodeURIComponent(req.url.split('?')[0]).replace(/\/$/, '/index.html'));
  if (!file.startsWith(site) || !fs.existsSync(file)) { res.writeHead(404); return res.end(); }
  res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
});

// One CSS pixel of the page as [r, g, b] (1×1 PNG: inflate the IDAT, skip the filter byte).
async function pixel(page, x, y) {
  const png = await page.screenshot({ clip: { x, y, width: 1, height: 1 }, scale: 'css' });
  const chunks = [];
  for (let i = 8; i < png.length;) {
    const len = png.readUInt32BE(i);
    if (png.toString('ascii', i + 4, i + 8) === 'IDAT') chunks.push(png.subarray(i + 8, i + 8 + len));
    i += 12 + len;
  }
  const raw = zlib.inflateSync(Buffer.concat(chunks));
  return [raw[1], raw[2], raw[3]];
}

// True while the onboarding pager's wide "Next" button (a saturated colour in light and dark) is on screen.
async function onOnboarding(page, viewport) {
  const [y, xs] = viewport.width > 600 ? [748, [420, 860]] : [804, [40, 350]];
  const [a, b] = [await pixel(page, xs[0], y), await pixel(page, xs[1], y)];
  const flat = a.every((v, i) => Math.abs(v - b[i]) < 6);
  return flat && a[0] - a[1] >= 50 && a[0] - a[2] >= 40;
}

(async () => {
  if (!fs.existsSync(path.join(site, 'index.html'))) throw new Error('Run scripts/screenshots/assemble-web.sh first');
  fs.mkdirSync(out, { recursive: true });
  await new Promise((r) => server.listen(PORT, r));
  const wanted = process.argv.slice(2);
  const browser = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader'] });
  for (const scene of scenes.filter((s) => wanted.length === 0 || wanted.includes(s.name))) {
    // Fresh context per scene = fresh localStorage, so every scene starts from the same state.
    const context = await browser.newContext({
      viewport: scene.viewport,
      deviceScaleFactor: 2,
      colorScheme: scene.name.includes('dark') ? 'dark' : 'light',
      locale: scene.locale || 'en-GB',
    });
    const page = await context.newPage();
    page.on('pageerror', (e) => console.log(`[${scene.name}] pageerror: ${e.message.slice(0, 200)}`));
    await page.goto(`http://localhost:${PORT}/index.html${scene.query}`);
    await page.waitForSelector('canvas', { timeout: 60000 });
    // Fonts, sample data and entry animations. Writing the sample circle through the SQLite (OPFS) worker takes a while
    // in headless Chromium, so the onboarding screen can still be up after a minute.
    // The first-run screen stays up until the sample data is in, which can take minutes, so wait until it's gone.
    await page.waitForTimeout(scene.wait || Number(process.env.WAIT_MS) || 15000);
    if (scene.query.includes('demo')) {
      const deadline = Date.now() + 420000;
      while ((await onOnboarding(page, scene.viewport)) && Date.now() < deadline) await page.waitForTimeout(5000);
      if (await onOnboarding(page, scene.viewport)) console.log(`[${scene.name}] WARNING: still on onboarding`);
      await page.waitForTimeout(8000); // entry animations, images
    }
    for (const [x, y] of scene.clicks || []) {
      await page.mouse.click(x, y);
      await page.waitForTimeout(4000); // let sheets finish opening before the next tap
    }
    if (scene.clicks) { await page.mouse.move(1, 1); await page.waitForTimeout(600); } // drop hover highlights
    await page.screenshot({ path: path.join(out, `${scene.name}.png`) });
    console.log('captured', scene.name);
    await context.close();
  }
  await browser.close();
  server.close();
})().catch((e) => { console.error(e); server.close(); process.exit(1); });
