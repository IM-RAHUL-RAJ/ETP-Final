// Renders the pixel mascots to animated GIFs for e-mail (most mail clients show no SVG and no script, but they do
// play a GIF), straight from the same frames the app draws. No browser needed for the GIFs.
// Usage: node scripts/render-mascots.mjs [contact-sheet.png]   (the sheet needs a Playwright Chromium)
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import gifenc from 'gifenc';
const { GIFEncoder } = gifenc;
import { ANIMATIONS, ANIMATION_NAMES, GRID_H, GRID_W, MASCOT_TYPES, frameSvg, paletteOf, renderGrid } from '../src/app/core/mascot/pixel-mascot.ts';

// The e-mail templates ask for these by name, and each is an animation: cheer for a fill, think for an alert...
const POSES = ['cheer', 'happy', 'think', 'sad', 'wave', 'dance'];
const OUT = '../services/trade-api/src/main/resources/email/mascots';
const SCALE = 4;

const hex = (value) => [1, 3, 5].map((i) => parseInt(value.slice(i, i + 2), 16));

mkdirSync(OUT, { recursive: true });

for (const type of MASCOT_TYPES) {
  const colours = paletteOf(type);
  // Index 0 is "see through"; the rest are the palette characters.
  const keys = Object.keys(colours);
  const palette = [[255, 255, 255], ...keys.map((key) => hex(colours[key]))];
  const indexOf = Object.fromEntries(keys.map((key, i) => [key, i + 1]));

  for (const pose of POSES) {
    const encoder = GIFEncoder();
    for (const frame of ANIMATIONS[pose]) {
      const grid = renderGrid(type, frame);
      const width = GRID_W * SCALE;
      const height = GRID_H * SCALE;
      const pixels = new Uint8Array(width * height);
      for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
          const c = grid[Math.floor(y / SCALE)][Math.floor(x / SCALE)];
          pixels[y * width + x] = c === ' ' ? 0 : (indexOf[c] ?? 0);
        }
      }
      encoder.writeFrame(pixels, width, height, { palette, delay: frame.ms, transparent: true, transparentIndex: 0, repeat: 0 });
    }
    encoder.finish();
    writeFileSync(join(OUT, `${type}-${pose}.gif`), encoder.bytes());
  }
}
console.log(`Wrote ${MASCOT_TYPES.length * POSES.length} GIFs to ${OUT}`);

const sheetPath = process.argv[2];
if (sheetPath) {
  const { chromium } = await import('@playwright/test');
  const cells = MASCOT_TYPES.flatMap((type) =>
    ANIMATION_NAMES.flatMap((name) => {
      const count = ANIMATIONS[name].length;
      const shown = name === 'dance' || name === 'cheer' ? [0, 2, 4] : [0, Math.min(2, count - 1)];
      return shown.map((i) => `<figure style="margin:0;text-align:center;font:11px monospace;color:#333">${frameSvg(type, name, i).replace('<svg ', '<svg width="112" height="112" ')}<figcaption>${type} ${name} ${i}</figcaption></figure>`);
    })
  );
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1250, height: 1000 } });
  await page.setContent(`<body style="margin:8px;background:#fff;display:grid;grid-template-columns:repeat(11,112px);gap:6px">${cells.join('')}</body>`);
  await page.screenshot({ path: sheetPath, fullPage: true });
  await browser.close();
}
