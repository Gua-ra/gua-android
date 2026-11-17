#!/usr/bin/env node
/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

// Bakes the chat wallpaper stills, the grain tile and manifest.json from the Lab modules in lab/.
// Usage and pinned tool versions: README.md.

import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import zlib from 'node:zlib';

const HERE = path.dirname(fileURLToPath(import.meta.url));

export const GENERATOR_VERSION = 1;
export const WIDTH = 1290;
export const HEIGHT = 2796;
// The Lab designs every wallpaper for a phone 390 points wide.
const DESIGN_WIDTH = 390;
const NOISE_SIZE = 128;
const BUDGET_TARGET = 4_000_000;
const BUDGET_LIMIT = 5_000_000;
const PINNED = { node: 22, chromium: '139.0.7258.5', cwebp: '1.6.0' };
const WEBP_ARGS = ['-q', '80', '-m', '6'];
const LAB_FILES = ['lab/1-live.js', 'lab/2-nature.js', 'lab/3-minimal-patterns.js'];
// Asset folders inside each checkout; the files keep the same names on both platforms.
const IOS_DIR = 'ElementX/Resources/Wallpapers';
const ANDROID_DIR = 'libraries/matrixui/src/main/assets/wallpapers';
// The Lab page's full-screen triangle (its page script is not copied).
const VERT = 'attribute vec2 p;void main(){gl_Position=vec4(p,0.0,1.0);}';
// The Lab's full-size preview canvas: a phone 860 points tall at 2x.
export const LAB_PREVIEW = [794, 1720];

// Patches applied to the Lab modules as they load. Each one only acts while the bake sets its
// flag, so with every flag off the page draws exactly what the Lab draws.
const LAB_PATCHES = [
  // Film grain: the apps draw noise.webp at runtime because lossy WebP smears baked grain.
  // Nature's grain also lifts dark scenes a little, so a flat mid-grey layer keeps that tone.
  {
    file: 'lab/2-nature.js',
    find: "ctx.fillStyle = ctx.createPattern(GRAIN, 'repeat');",
    replace: "ctx.fillStyle = window.GUA_BAKE_NO_GRAIN ? '#808080' : ctx.createPattern(GRAIN, 'repeat');",
  },
  {
    file: 'lab/3-minimal-patterns.js',
    find: 'if (grain) n += grain(x, y);',
    replace: 'if (grain && !window.GUA_BAKE_NO_GRAIN) n += grain(x, y);',
  },
  // Nature's star counts grow with the canvas's pixel area, so the bake counts them as the Lab's preview does.
  {
    file: 'lab/2-nature.js',
    find: '(w * h) / (390 * 844)',
    replace: '(window.GUA_BAKE_STAR_AREA || w * h) / (390 * 844)',
    count: 11,
  },
  // A crescent's shadow is cut out of the whole canvas, which leaves a hole showing the page behind
  // it. The bake paints the sky from before the moon back through the shadow instead.
  {
    file: 'lab/2-nature.js',
    find: 'glow(ctx, x, y, r * 2.6, glowCol || col, (glowA == null ? 0.16 : glowA) * 1.3);',
    replace: 'glow(ctx, x, y, r * 2.6, glowCol || col, (glowA == null ? 0.16 : glowA) * 1.3); var bakeSky = phase && window.GUA_BAKE_MOON_FIX ? window.GUA_BAKE_SNAPSHOT(ctx) : null;',
  },
  {
    file: 'lab/2-nature.js',
    find: "ctx.globalCompositeOperation = 'destination-out';",
    replace: "if (bakeSky) ctx.globalAlpha = shadowAlpha == null ? 0.93 : shadowAlpha; else ctx.globalCompositeOperation = 'destination-out';",
  },
  {
    file: 'lab/2-nature.js',
    find: "ctx.fillStyle = css('#000000', shadowAlpha == null ? 0.93 : shadowAlpha);",
    replace: "ctx.fillStyle = bakeSky || css('#000000', shadowAlpha == null ? 0.93 : shadowAlpha);",
  },
];

// Light fills of the original ten presets, as in ChatBackgroundPreset on both platforms.
// Only these may be a fallback, because every build since chat backgrounds shipped knows them.
export const FALLBACK_PRESETS = {
  aurora: { glows: { base: 0xb2d8c2, glows: [[0x8fcdab, 0.30, 0.25, 0.75], [0x93cbcf, 0.75, 0.80, 0.65]] } },
  mint: { solid: 0xadd7bf },
  sky: { solid: 0xb6d1eb },
  sand: { solid: 0xdcc9a9 },
  blush: { solid: 0xe8c3ca },
  lavender: { solid: 0xd0c6eb },
  graphite: { solid: 0xc5cad3 },
  sunset: { linear: [0.5, 0, 0.5, 1], stops: [[0xf0c6a6, 0], [0xeab5c2, 0.5], [0xcbb5e4, 1]] },
  ocean: { linear: [0, 0, 1, 1], stops: [[0xa4d5d9, 0], [0xa7c6eb, 1]] },
  night: { linear: [0.5, 0, 0.5, 1], stops: [[0xc1c6ec, 0], [0xd4c0e6, 1]] },
};

// ---------------------------------------------------------------- colour

// sRGB 8-bit to OKLab (Björn Ottosson's matrices). Also injected into the page.
export function oklab(r, g, b) {
  const lin = (c) => {
    c /= 255;
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  };
  const R = lin(r), G = lin(g), B = lin(b);
  const l = Math.cbrt(0.4122214708 * R + 0.5363325363 * G + 0.0514459929 * B);
  const m = Math.cbrt(0.2119034982 * R + 0.6806995451 * G + 0.1073969566 * B);
  const s = Math.cbrt(0.0883024619 * R + 0.2817188376 * G + 0.6299787005 * B);
  return [
    0.2104542553 * l + 0.793617785 * m - 0.0040720468 * s,
    1.9779984951 * l - 2.428592205 * m + 0.4505937099 * s,
    0.0259040371 * l + 0.7827717662 * m - 0.808675766 * s,
  ];
}

function rgb(hex) {
  return [(hex >> 16) & 255, (hex >> 8) & 255, hex & 255];
}

function mixRgb(a, b, t) {
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

// The colour of a preset's light fill at a point of a WIDTH x HEIGHT frame, drawn the way the apps draw it.
function presetColour(fill, x, y) {
  if (fill.solid !== undefined) return rgb(fill.solid);
  if (fill.linear) {
    const [sx, sy, ex, ey] = fill.linear;
    const dx = (ex - sx) * WIDTH, dy = (ey - sy) * HEIGHT;
    const t = Math.min(1, Math.max(0, ((x - sx * WIDTH) * dx + (y - sy * HEIGHT) * dy) / (dx * dx + dy * dy)));
    const stops = fill.stops;
    for (let i = 1; i < stops.length; i++) {
      if (t <= stops[i][1]) return mixRgb(rgb(stops[i - 1][0]), rgb(stops[i][0]), (t - stops[i - 1][1]) / (stops[i][1] - stops[i - 1][1]));
    }
    return rgb(stops[stops.length - 1][0]);
  }
  let c = rgb(fill.glows.base);
  const longest = Math.max(WIDTH, HEIGHT);
  for (const [hex, cx, cy, radius] of fill.glows.glows) {
    const d = Math.hypot(x - cx * WIDTH, y - cy * HEIGHT) / (radius * longest);
    if (d < 1) c = mixRgb(c, rgb(hex), 1 - d);
  }
  return c;
}

export function presetMeanOklab(fill) {
  const acc = [0, 0, 0];
  const nx = 129, ny = 280;
  for (let j = 0; j < ny; j++) {
    for (let i = 0; i < nx; i++) {
      const c = presetColour(fill, ((i + 0.5) / nx) * WIDTH, ((j + 0.5) / ny) * HEIGHT);
      const o = oklab(Math.round(c[0]), Math.round(c[1]), Math.round(c[2]));
      acc[0] += o[0]; acc[1] += o[1]; acc[2] += o[2];
    }
  }
  return acc.map((v) => v / (nx * ny));
}

// Matched by hue: the presets share one lightness while most artworks are far paler, so a full OKLab
// distance would pick the palest preset. Graphite, the one grey, is for an artwork with no clear hue.
const NEUTRAL_CHROMA = 0.005;
const PRESET_MEANS = new Map();

function hueDegrees(o) {
  return ((Math.atan2(o[2], o[1]) * 180) / Math.PI + 360) % 360;
}

export function nearestPreset(mean) {
  const chroma = Math.hypot(mean[1], mean[2]);
  if (chroma < NEUTRAL_CHROMA) return { id: 'graphite', hueDelta: null, chroma };
  let best = null;
  for (const [id, fill] of Object.entries(FALLBACK_PRESETS)) {
    if (id === 'graphite') continue;
    if (!PRESET_MEANS.has(id)) PRESET_MEANS.set(id, presetMeanOklab(fill));
    const d = Math.abs(hueDegrees(PRESET_MEANS.get(id)) - hueDegrees(mean));
    const hueDelta = Math.min(d, 360 - d);
    if (!best || hueDelta < best.hueDelta) best = { id, hueDelta, chroma };
  }
  return best;
}

function describeNearest(n) {
  return `${n.id}, ${n.hueDelta === null ? 'grey' : `hue ${n.hueDelta.toFixed(1)} deg away`}, chroma ${n.chroma.toFixed(3)}`;
}

// ---------------------------------------------------------------- page

// Runs inside the page after the Lab modules have registered window.GUA_WALLPAPERS.
function installPageHelpers(vert, designWidth) {
  const byId = new Map(window.GUA_WALLPAPERS.map((w) => [w.id, w]));
  const glCanvas = document.createElement('canvas');
  const gl = glCanvas.getContext('webgl', { alpha: false, antialias: false, depth: false, stencil: false, premultipliedAlpha: false, preserveDrawingBuffer: true });
  const programs = new Map();
  if (gl) {
    gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
  }

  function program(wp) {
    if (programs.has(wp.id)) return programs.get(wp.id);
    const shader = (type, src) => {
      const s = gl.createShader(type);
      gl.shaderSource(s, src);
      gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(`Shader ${wp.id}: ${gl.getShaderInfoLog(s)}`);
      return s;
    };
    const p = gl.createProgram();
    gl.attachShader(p, shader(gl.VERTEX_SHADER, vert));
    gl.attachShader(p, shader(gl.FRAGMENT_SHADER, wp.frag));
    gl.bindAttribLocation(p, 0, 'p');
    gl.linkProgram(p);
    if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(`Link ${wp.id}: ${gl.getProgramInfoLog(p)}`);
    const u = (n) => gl.getUniformLocation(p, n);
    const entry = { p, res: u('u_res'), time: u('u_time'), dark: u('u_dark'), pulse: u('u_pulse'), tilt: u('u_tilt'), origin: u('u_origin') };
    programs.set(wp.id, entry);
    return entry;
  }

  function meanOklab(px) {
    const acc = [0, 0, 0];
    const n = px.length / 4;
    for (let i = 0; i < px.length; i += 4) {
      const o = window.GUA_BAKE_OKLAB(px[i], px[i + 1], px[i + 2]);
      acc[0] += o[0]; acc[1] += o[1]; acc[2] += o[2];
    }
    return acc.map((v) => v / n);
  }

  // A copy of a canvas as a pattern that lines up with its device pixels under any transform.
  window.GUA_BAKE_SNAPSHOT = function (ctx) {
    const copy = document.createElement('canvas');
    copy.width = ctx.canvas.width;
    copy.height = ctx.canvas.height;
    copy.getContext('2d').drawImage(ctx.canvas, 0, 0);
    const pattern = ctx.createPattern(copy, 'no-repeat');
    pattern.setTransform(ctx.getTransform().inverse());
    return pattern;
  };

  function draw(wp, w, h, opts) {
    if (wp.kind === 'webgl') {
      if (!gl) throw new Error('WebGL is not available');
      glCanvas.width = w;
      glCanvas.height = h;
      const pr = program(wp);
      gl.viewport(0, 0, w, h);
      gl.useProgram(pr.p);
      gl.enableVertexAttribArray(0);
      gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
      gl.uniform2f(pr.res, w, h);
      gl.uniform1f(pr.time, opts.time);
      gl.uniform1f(pr.dark, opts.dark ? 1 : 0);
      gl.uniform1f(pr.pulse, 0);
      gl.uniform2f(pr.tilt, 0, 0);
      if (pr.origin) gl.uniform2f(pr.origin, 0, 0);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      return glCanvas;
    }
    const c = document.createElement('canvas');
    c.width = w;
    c.height = h;
    // Like the Lab: a CPU-backed context, which every per-pixel renderer reads back.
    const ctx = c.getContext('2d', { willReadFrequently: true });
    window.GUA_BAKE_NO_GRAIN = !!opts.noGrain;
    window.GUA_BAKE_STAR_AREA = opts.starArea || 0;
    window.GUA_BAKE_MOON_FIX = !!opts.moonFix;
    try {
      wp.render(ctx, w, h, { dark: !!opts.dark, t: 0, pulse: 0, option: opts.option || undefined, scale: w / designWidth });
    } finally {
      window.GUA_BAKE_NO_GRAIN = false;
      window.GUA_BAKE_STAR_AREA = 0;
      window.GUA_BAKE_MOON_FIX = false;
    }
    return c;
  }

  // opts: { dark, option, time, noGrain, starArea, moonFix, renderSize, stats }.
  // renderSize draws at that size first and scales up to w x h.
  // Returns a PNG data URL and, if asked, the mean OKLab colour.
  window.GUA_BAKE = function (labId, w, h, opts) {
    const wp = byId.get(labId);
    if (!wp) throw new Error(`No Lab wallpaper ${labId}`);
    let out = draw(wp, opts.renderSize ? opts.renderSize[0] : w, opts.renderSize ? opts.renderSize[1] : h, opts);
    if (opts.renderSize || opts.stats) {
      const c = document.createElement('canvas');
      c.width = w;
      c.height = h;
      const g = c.getContext('2d', { willReadFrequently: true });
      g.imageSmoothingEnabled = true;
      g.imageSmoothingQuality = 'high';
      g.drawImage(out, 0, 0, w, h);
      out = c;
    }
    const url = out.toDataURL('image/png');
    const px = out === glCanvas ? null : out.getContext('2d').getImageData(0, 0, w, h).data;
    let transparent = 0;
    if (px) for (let i = 3; i < px.length; i += 4) if (px[i] < 255) transparent++;
    return { png: url, oklab: opts.stats ? meanOklab(px) : null, transparent };
  };
}

function patchedLabSource(file) {
  let src = fs.readFileSync(path.join(HERE, file), 'utf8');
  for (const p of LAB_PATCHES.filter((x) => x.file === file)) {
    const parts = src.split(p.find);
    const want = p.count || 1;
    if (parts.length - 1 !== want) throw new Error(`${file}: expected "${p.find}" ${want} times, found ${parts.length - 1}. Update LAB_PATCHES for the new Lab code.`);
    src = parts.join(p.replace);
  }
  return src;
}

function loadPlaywright(dir) {
  const from = dir || process.env.PLAYWRIGHT_CORE;
  const require = createRequire(from ? path.join(path.resolve(from), 'package.json') : import.meta.url);
  return require(from ? path.resolve(from) : 'playwright-core');
}

// Opens headless Chromium with the Lab modules loaded. SwiftShader keeps WebGL output independent of the GPU.
export async function openLab({ playwrightCore } = {}) {
  const { chromium } = loadPlaywright(playwrightCore);
  const browser = await chromium.launch({ headless: true, args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--disable-gpu'] });
  const page = await browser.newPage();
  await page.route('**/*', (route) => route.abort());
  await page.setContent('<!doctype html><html><head><meta charset="utf-8"></head><body></body></html>');
  for (const file of LAB_FILES) await page.addScriptTag({ content: patchedLabSource(file) });
  await page.addScriptTag({ content: `window.GUA_BAKE_OKLAB = ${oklab.toString()};` });
  await page.evaluate(`(${installPageHelpers.toString()})(${JSON.stringify(VERT)}, ${DESIGN_WIDTH})`);
  const render = async (labId, w, h, opts) => {
    const r = await page.evaluate(([id, ww, hh, o]) => window.GUA_BAKE(id, ww, hh, o), [labId, w, h, opts]);
    return { png: Buffer.from(r.png.slice(r.png.indexOf(',') + 1), 'base64'), oklab: r.oklab, transparent: r.transparent };
  };
  return { browser, page, render, chromiumVersion: browser.version() };
}

// ---------------------------------------------------------------- files

function png(width, height, rgbBytes) {
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(zlib.crc32(td));
    return Buffer.concat([len, td, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8;
  ihdr[9] = 2;
  const raw = Buffer.alloc((width * 3 + 1) * height);
  for (let y = 0; y < height; y++) Buffer.from(rgbBytes.buffer, y * width * 3, width * 3).copy(raw, y * (width * 3 + 1) + 1);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw)), chunk('IEND', Buffer.alloc(0))]);
}

function hash01(x, y, seed) {
  let h = Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263) ^ Math.imul(seed | 0, 2246822519);
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}

// Grey grain centred on mid-grey: fine triangular noise with a little clumping at about 4 px.
// Every term is periodic in NOISE_SIZE, so the tile repeats without seams.
export function noiseTileRgb() {
  const N = NOISE_SIZE, CELL = 4, L = N / CELL;
  const out = new Uint8Array(N * N * 3);
  const lattice = (i, j) => hash01(((i % L) + L) % L, ((j % L) + L) % L, 3);
  for (let y = 0; y < N; y++) {
    for (let x = 0; x < N; x++) {
      const gx = x / CELL, gy = y / CELL, i = Math.floor(gx), j = Math.floor(gy);
      const fx = gx - i, fy = gy - j, u = fx * fx * (3 - 2 * fx), v = fy * fy * (3 - 2 * fy);
      const a = lattice(i, j), b = lattice(i + 1, j), c = lattice(i, j + 1), d = lattice(i + 1, j + 1);
      const clump = (a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v) * 2 - 1;
      const fine = hash01(x, y, 1) + hash01(x, y, 2) - 1;
      const g = Math.max(0, Math.min(255, Math.round(128 + 127 * (0.8 * fine + 0.2 * clump))));
      out.set([g, g, g], (y * N + x) * 3);
    }
  }
  return out;
}

function sha256(buf) {
  return crypto.createHash('sha256').update(buf).digest('hex');
}

function cwebp(bin, args, input, output) {
  execFileSync(bin, [...args, '-quiet', input, '-o', output], { stdio: ['ignore', 'ignore', 'inherit'] });
  return fs.readFileSync(output);
}

function cwebpVersion(bin) {
  return execFileSync(bin, ['-version'], { encoding: 'utf8' }).split('\n')[0].trim();
}

// ---------------------------------------------------------------- main

function parseArgs(argv) {
  const a = { android: null, ios: null, playwrightCore: null, cwebp: 'cwebp', png: null, only: null, imagesOnly: false };
  for (let i = 0; i < argv.length; i++) {
    const k = argv[i], v = argv[i + 1];
    if (k === '--android') { a.android = v; i++; }
    else if (k === '--ios') { a.ios = v; i++; }
    else if (k === '--playwright-core') { a.playwrightCore = v; i++; }
    else if (k === '--cwebp') { a.cwebp = v; i++; }
    else if (k === '--png') { a.png = v; i++; }
    else if (k === '--only') { a.only = v.split(','); i++; }
    else if (k === '--images-only') a.imagesOnly = true;
    else throw new Error(`Unknown argument ${k}. See README.md.`);
  }
  if (!a.only && (!a.android || !a.ios)) throw new Error('Pass --android <gua-android checkout> and --ios <gua-ios checkout>. See README.md.');
  if (a.only && !a.png) throw new Error('--only renders a subset for review and needs --png <dir>; it writes nothing else.');
  return a;
}

// The live shader ports from shaders/generate.mjs, when that folder is present and wanted.
async function shaderPorts(imagesOnly) {
  const file = path.join(HERE, 'shaders', 'generate.mjs');
  if (imagesOnly || !fs.existsSync(file)) return null;
  const mod = await import(pathToFileURL(file).href);
  for (const name of ['loadLabShaders', 'toMsl', 'toAgsl']) {
    if (typeof mod[name] !== 'function') throw new Error(`shaders/generate.mjs no longer exports ${name}; update bake.mjs`);
  }
  const glsl = mod.loadLabShaders(path.join(HERE, 'lab'));
  return (art) => ({ msl: mod.toMsl(art, glsl.get(art.id)), agsl: mod.toAgsl(art, glsl.get(art.id)) });
}

// How the bake draws one variant of an artwork from artworks.json.
export function bakeOptions(art, variant) {
  return {
    dark: variant === 'dark',
    option: art.option,
    time: art.still_time,
    noGrain: !art.lab_texture,
    starArea: LAB_PREVIEW[0] * LAB_PREVIEW[1],
    moonFix: true,
    renderSize: art.lab_size ? LAB_PREVIEW : undefined,
    stats: variant === 'light',
  };
}

function kb(n) {
  return (n / 1024).toFixed(1).padStart(7);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const config = JSON.parse(fs.readFileSync(path.join(HERE, 'artworks.json'), 'utf8'));
  const artworks = args.only ? config.artworks.filter((a) => args.only.includes(a.id)) : config.artworks;
  if (args.only && artworks.length !== args.only.length) throw new Error(`Unknown id in --only ${args.only.join(',')}`);

  const nodeMajor = Number(process.versions.node.split('.')[0]);
  if (nodeMajor !== PINNED.node) console.warn(`warning: Node ${process.versions.node}, pinned major is ${PINNED.node}`);
  const cwebpVer = cwebpVersion(args.cwebp);
  if (cwebpVer !== PINNED.cwebp) throw new Error(`cwebp ${cwebpVer}; the outputs are pinned to cwebp ${PINNED.cwebp}`);
  const ports = args.only ? null : await shaderPorts(args.imagesOnly);

  const work = fs.mkdtempSync(path.join(os.tmpdir(), 'gua-wallpapers-'));
  if (args.png) fs.mkdirSync(args.png, { recursive: true });
  const lab = await openLab({ playwrightCore: args.playwrightCore });
  if (lab.chromiumVersion !== PINNED.chromium) console.warn(`warning: Chromium ${lab.chromiumVersion}, pinned is ${PINNED.chromium}; outputs may differ`);

  // name -> bytes; shared files go to both platforms, shader ports only to their own.
  const shared = new Map(), ios = new Map(), android = new Map();
  const entries = [];
  try {
    for (const art of artworks) {
      const entry = { id: art.id, section: art.section, live: art.live, files: {}, sha256: {} };
      const add = (role, name, buf, target) => {
        target.set(name, buf);
        entry.files[role] = name;
        entry.sha256[name] = sha256(buf);
      };
      let lightMean = null;
      for (const variant of ['light', 'dark']) {
        const name = `${art.id}-${variant}.webp`;
        const started = Date.now();
        const r = await lab.render(art.lab, WIDTH, HEIGHT, bakeOptions(art, variant));
        if (r.transparent) throw new Error(`${name}: ${r.transparent} pixels are not opaque`);
        if (variant === 'light') lightMean = r.oklab;
        const pngPath = path.join(args.png || work, `${art.id}-${variant}.png`);
        fs.writeFileSync(pngPath, r.png);
        if (!args.only) {
          add(variant, name, cwebp(args.cwebp, WEBP_ARGS, pngPath, path.join(work, name)), shared);
          if (!args.png) fs.rmSync(pngPath);
        }
        process.stderr.write(`${name} ${Date.now() - started} ms\n`);
      }
      if (art.live && ports) {
        const port = ports(art);
        add('msl', `${art.id}.msl`, Buffer.from(port.msl), ios);
        add('agsl', `${art.id}.agsl`, Buffer.from(port.agsl), android);
      }
      const nearest = nearestPreset(lightMean);
      const fallback = art.fallback_preset || nearest.id;
      if (fallback !== nearest.id) console.warn(`note: ${art.id} keeps fallback ${fallback}; the nearest preset now is ${nearest.id}`);
      entry.anchor = art.anchor;
      entry.fallback_preset = fallback;
      entry.bubble = art.bubble;
      if (art.live) entry.still_time = art.still_time;
      entry.nearest = nearest;
      entries.push(entry);
    }
  } finally {
    await lab.browser.close();
  }

  if (args.only) {
    for (const e of entries) console.log(`${e.id}: nearest preset ${describeNearest(e.nearest)}`);
    fs.rmSync(work, { recursive: true, force: true });
    return;
  }

  const noisePng = path.join(work, 'noise.png');
  fs.writeFileSync(noisePng, png(NOISE_SIZE, NOISE_SIZE, noiseTileRgb()));
  const noise = cwebp(args.cwebp, ['-lossless', '-z', '9', '-exact'], noisePng, path.join(work, 'noise.webp'));
  shared.set('noise.webp', noise);
  fs.rmSync(work, { recursive: true, force: true });

  const manifest = {
    generator: { script: 'gua-android tools/wallpapers/bake.mjs', version: GENERATOR_VERSION, chromium: lab.chromiumVersion, cwebp: cwebpVer },
    noise: { file: 'noise.webp', sha256: sha256(noise), width: NOISE_SIZE, height: NOISE_SIZE },
    artworks: entries.map(({ nearest, ...e }) => e),
  };
  shared.set('manifest.json', Buffer.from(JSON.stringify(manifest, null, 2) + '\n'));

  const platforms = [
    { name: 'iOS', dir: path.join(args.ios, IOS_DIR), files: new Map([...shared, ...ios]) },
    { name: 'Android', dir: path.join(args.android, ANDROID_DIR), files: new Map([...shared, ...android]) },
  ];
  const size = (name) => (shared.get(name) || ios.get(name) || android.get(name)).length;
  const total = (p) => [...p.files.values()].reduce((s, b) => s + b.length, 0);

  console.log(`\n${'artwork'.padEnd(18)} ${'light KB'.padStart(9)} ${'dark KB'.padStart(9)} ${'msl KB'.padStart(8)} ${'agsl KB'.padStart(8)}  ${'fallback'.padEnd(9)} nearest by hue`);
  for (const e of entries) {
    const opt = (role) => (e.files[role] ? kb(size(e.files[role])) : '      -');
    console.log(`${e.id.padEnd(18)} ${opt('light')}   ${opt('dark')}  ${opt('msl')}  ${opt('agsl')}    ${e.fallback_preset.padEnd(9)} ${describeNearest(e.nearest)}`);
  }
  console.log(`${'noise.webp'.padEnd(18)} ${kb(noise.length)}`);
  console.log(`${'manifest.json'.padEnd(18)} ${kb(shared.get('manifest.json').length)}`);
  console.log('');
  for (const p of platforms) {
    console.log(`Total ${p.name.padEnd(8)} ${(total(p) / 1e6).toFixed(2)} MB (${total(p)} bytes) in ${p.files.size} files`);
  }
  console.log(`Target ${BUDGET_TARGET / 1e6} MB per platform, limit ${BUDGET_LIMIT / 1e6} MB`);
  if (platforms.some((p) => total(p) > BUDGET_LIMIT)) {
    console.error('Over the 5 MB budget; nothing written.');
    process.exit(1);
  }
  if (platforms.some((p) => total(p) > BUDGET_TARGET)) console.warn('warning: above the 4 MB target');

  for (const p of platforms) {
    fs.mkdirSync(p.dir, { recursive: true });
    for (const [name, buf] of p.files) fs.writeFileSync(path.join(p.dir, name), buf);
    const extra = fs.readdirSync(p.dir).filter((f) => !p.files.has(f));
    if (extra.length) {
      const why = args.imagesOnly ? '; --images-only leaves shader ports out, so do not commit them with this manifest' : '';
      console.warn(`warning: ${p.dir} also holds ${extra.join(', ')}, which manifest.json does not list${why}`);
    }
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(fs.realpathSync(process.argv[1])).href) {
  main().catch((e) => {
    console.error(e.stack || String(e));
    process.exit(1);
  });
}
