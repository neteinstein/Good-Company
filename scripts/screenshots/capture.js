// Captures README screenshots from build/web-preview (run assemble-web.sh first).
// Usage (repo root): NODE_PATH=$(npm root -g) node scripts/screenshots/capture.js [sceneName...]
// Each scene is a URL query understood by composeApp/.../LaunchOptions.kt.
const { chromium } = require('playwright');
const http = require('http');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '../..');
const site = path.join(root, 'build/web-preview');
const out = path.join(root, 'docs/screenshots');
const PORT = 8765;

const phone = { width: 393, height: 852 };
const scenes = [
  { name: 'onboarding', query: '?onboarding&theme=light', viewport: phone },
  { name: 'home', query: '?demo&theme=light', viewport: phone },
  { name: 'friend', query: '?demo&theme=light&friend=sample-ana', viewport: phone },
  { name: 'together', query: '?demo&theme=light&tab=together', viewport: phone },
  // Taps the big "I'm with…" button (CSS px) to show the check-in sheet.
  { name: 'checkin', query: '?demo&theme=light&tab=together', viewport: phone, clicks: [[196, 214], [86, 335], [89, 431]] },
  { name: 'agenda', query: '?demo&theme=light&tab=agenda', viewport: phone },
  { name: 'ideas', query: '?demo&theme=light&tab=ideas', viewport: phone },
  { name: 'settings', query: '?demo&theme=light&tab=settings', viewport: phone },
  { name: 'home-dark', query: '?demo&theme=dark', viewport: phone },
  { name: 'friend-dark', query: '?demo&theme=dark&friend=sample-sofia', viewport: phone },
  { name: 'together-dark', query: '?demo&theme=dark&tab=together', viewport: phone },
  { name: 'agenda-dark', query: '?demo&theme=dark&tab=agenda', viewport: phone },
  { name: 'desktop', query: '?demo&theme=light', viewport: { width: 1280, height: 800 } },
  { name: 'review', query: '?demo&theme=light&review', viewport: phone, wait: 11000 },
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
    await page.waitForTimeout(scene.wait || 9000); // fonts, sample data and entry animations
    for (const [x, y] of scene.clicks || []) {
      await page.mouse.click(x, y);
      await page.waitForTimeout(1800);
    }
    if (scene.clicks) { await page.mouse.move(1, 1); await page.waitForTimeout(600); } // drop hover highlights
    await page.screenshot({ path: path.join(out, `${scene.name}.png`) });
    console.log('captured', scene.name);
    await context.close();
  }
  await browser.close();
  server.close();
})().catch((e) => { console.error(e); server.close(); process.exit(1); });
