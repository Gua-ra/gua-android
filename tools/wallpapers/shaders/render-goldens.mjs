/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

// Renders the Lab GLSL of every live artwork in headless Chromium WebGL at 180x390 and stores each case
// 2x box-downsampled in goldens/.
// Usage: node tools/wallpapers/shaders/render-goldens.mjs [--lab <Lab .html or module folder>] [--playwright <dir>] [--backend swiftshader|metal] [--out <dir>]

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { LIVE_ARTWORKS, loadLabShaders, defaultLabPath } from './generate.mjs';
import { CASES, GOLDEN_HEIGHT, GOLDEN_WIDTH, goldenName, openRenderer, encodePng, downsample2x } from './harness.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));

const args = {};
for (let i = 2; i < process.argv.length; i += 2) args[process.argv[i].replace(/^--/, '')] = process.argv[i + 1];
const labPath = args.lab || defaultLabPath();
if (!labPath) {
  console.error('usage: render-goldens.mjs --lab <Lab .html or module folder> [--playwright <dir>] [--backend swiftshader|metal] [--out <dir>]');
  process.exit(2);
}
const outDir = args.out || path.join(HERE, 'goldens');
fs.mkdirSync(outDir, { recursive: true });

const shaders = loadLabShaders(labPath);
const r = await openRenderer({ playwright: args.playwright, backend: args.backend || 'swiftshader' });
console.log(`WebGL renderer: ${r.renderer}`);
let total = 0;
try {
  for (const art of LIVE_ARTWORKS) {
    const images = await r.render(shaders.get(art.id), CASES);
    images.forEach((rgba, i) => {
      const png = encodePng(downsample2x(rgba), GOLDEN_WIDTH, GOLDEN_HEIGHT);
      fs.writeFileSync(path.join(outDir, goldenName(art.id, CASES[i])), png);
      total += png.length;
    });
    console.log(`${art.id.padEnd(18)} ${images.length} goldens`);
  }
} finally {
  await r.close();
}
console.log(`total ${(total / 1024).toFixed(0)} KB in ${outDir}`);
