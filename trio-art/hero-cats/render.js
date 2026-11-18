// Renders every hero-*.svg to a 768 px PNG with a transparent background. Usage: node render.js <outDir>
const { chromium } = require('playwright'); const fs = require('fs'), path = require('path');
const SIZE = 768;
(async () => {
  const out = process.argv[2]; fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: SIZE, height: SIZE } });
  for (const f of fs.readdirSync(__dirname).filter(f => /^hero-.*\.svg$/.test(f))) {
    await p.setContent(`<html><body style="margin:0;background:transparent"><style>svg{width:${SIZE}px;height:${SIZE}px;display:block}</style>${fs.readFileSync(path.join(__dirname, f), 'utf8')}</body></html>`);
    await p.screenshot({ path: path.join(out, f.replace('.svg', '.png')), omitBackground: true, clip: { x: 0, y: 0, width: SIZE, height: SIZE } });
  }
  await b.close();
})();
