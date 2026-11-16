/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

// Shared by render-goldens.mjs, check-agsl.mjs and check-agsl-device.mjs: the golden cases, a headless Chromium WebGL
// renderer that draws a shader the way the Lab page does, PNG files and the image comparison.

import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { createRequire } from 'node:module';

// Every case is rendered at WIDTH x HEIGHT. Goldens are stored after the comparison's 2x box downsample,
// so only the candidate is downsampled when comparing.
export const WIDTH = 180;
export const HEIGHT = 390;
export const GOLDEN_WIDTH = WIDTH / 2;
export const GOLDEN_HEIGHT = HEIGHT / 2;
export const LIMIT_MEAN = 1.5;
export const LIMIT_MAX = 10;
// Thin features that float rounding moves by under a pixel (README, Goldens); the mean limit is tightened instead.
export const ALLOWANCES = { 'molten-pearl': { mean: 0.1, max: 16 }, ipe: { mean: 0.1, max: 16 } };

export function limitsFor(id) {
  return { mean: LIMIT_MEAN, max: LIMIT_MAX, ...ALLOWANCES[id] };
}

export const CASES = [];
for (const t of [0, 30]) {
  for (const pulse of [0, 0.6]) {
    for (const dark of [false, true]) CASES.push({ t, pulse, dark });
  }
}

export function goldenName(id, c) {
  return `${id}-${c.dark ? 'dark' : 'light'}-t${c.t}-p${Math.round(c.pulse * 100)}.png`;
}

// ---------------------------------------------------------------- Playwright

// No dependency is installed for this folder: point --playwright (or GUA_PLAYWRIGHT) at an existing
// playwright-core or playwright package, or a node_modules folder that holds one.
export function loadPlaywright(where) {
  const candidates = [];
  const base = where || process.env.GUA_PLAYWRIGHT;
  if (base) {
    const req = createRequire(path.join(path.resolve(base), 'noop.js'));
    candidates.push(() => req(path.resolve(base)), () => req('playwright-core'), () => req('playwright'));
  }
  const local = createRequire(import.meta.url);
  candidates.push(() => local('playwright-core'), () => local('playwright'));
  for (const load of candidates) {
    try {
      const pw = load();
      if (pw && pw.chromium) return pw;
    } catch {
      // try the next location
    }
  }
  throw new Error('Playwright not found: pass --playwright <path to playwright-core or node_modules>');
}

// Goldens use SwiftShader: IEEE float on the CPU, the same on every machine, and what Metal with fast
// math off reproduces. ANGLE's Metal backend (backend 'metal', what desktop Chrome on a Mac uses)
// behaves like fast math and differs by up to about 13 levels in a few places.
export async function openRenderer({ playwright, backend = 'swiftshader' }) {
  const { chromium } = loadPlaywright(playwright);
  const args = backend === 'metal'
    ? ['--use-angle=metal', '--enable-gpu', '--ignore-gpu-blocklist']
    : ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'];
  const browser = await chromium.launch({ args });
  const page = await browser.newPage();
  const renderer = await page.evaluate(([w, h]) => {
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    // Same context attributes and vertex shader as the Lab page.
    const gl = canvas.getContext('webgl', { alpha: false, antialias: false, depth: false, stencil: false, premultipliedAlpha: false, preserveDrawingBuffer: true });
    const buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
    window.__gl = gl;
    const info = gl.getExtension('WEBGL_debug_renderer_info');
    return info ? gl.getParameter(info.UNMASKED_RENDERER_WEBGL) : gl.getParameter(gl.RENDERER);
  }, [WIDTH, HEIGHT]);

  // Returns one RGBA buffer per case, rows top to bottom.
  async function render(frag, cases) {
    const result = await page.evaluate(([src, list, w, h]) => {
      const gl = window.__gl;
      const sh = (type, s) => {
        const o = gl.createShader(type);
        gl.shaderSource(o, s);
        gl.compileShader(o);
        if (!gl.getShaderParameter(o, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(o));
        return o;
      };
      const p = gl.createProgram();
      gl.attachShader(p, sh(gl.VERTEX_SHADER, 'attribute vec2 p;void main(){gl_Position=vec4(p,0.0,1.0);}'));
      gl.attachShader(p, sh(gl.FRAGMENT_SHADER, src));
      gl.bindAttribLocation(p, 0, 'p');
      gl.linkProgram(p);
      if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(p));
      gl.useProgram(p);
      gl.viewport(0, 0, w, h);
      gl.enableVertexAttribArray(0);
      gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
      const loc = (n) => gl.getUniformLocation(p, n);
      const out = [];
      for (const c of list) {
        gl.uniform2f(loc('u_res'), w, h);
        gl.uniform1f(loc('u_time'), c.t);
        gl.uniform1f(loc('u_dark'), c.dark ? 1 : 0);
        gl.uniform1f(loc('u_pulse'), c.pulse);
        gl.uniform2f(loc('u_tilt'), 0, 0);
        gl.uniform2f(loc('u_origin'), 0, 0);
        gl.drawArrays(gl.TRIANGLES, 0, 3);
        const px = new Uint8Array(w * h * 4);
        gl.readPixels(0, 0, w, h, gl.RGBA, gl.UNSIGNED_BYTE, px);
        let s = '';
        for (let i = 0; i < px.length; i += 0x8000) s += String.fromCharCode.apply(null, px.subarray(i, i + 0x8000));
        out.push(btoa(s));
      }
      gl.deleteProgram(p);
      return out;
    }, [frag, cases, WIDTH, HEIGHT]);
    return result.map((b64) => {
      const bottomUp = Buffer.from(b64, 'base64');
      const rgba = Buffer.alloc(bottomUp.length);
      const row = WIDTH * 4;
      for (let y = 0; y < HEIGHT; y++) bottomUp.copy(rgba, y * row, (HEIGHT - 1 - y) * row, (HEIGHT - y) * row);
      return rgba;
    });
  }

  return { renderer, render, close: () => browser.close() };
}

// ---------------------------------------------------------------- PNG (8-bit RGB, no colour chunks)

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(zlib.crc32(td));
  return Buffer.concat([len, td, crc]);
}

function paeth(a, b, c) {
  const p = a + b - c;
  const pa = Math.abs(p - a);
  const pb = Math.abs(p - b);
  const pc = Math.abs(p - c);
  return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
}

export function encodePng(rgba, w, h) {
  const stride = w * 3;
  const rgb = Buffer.alloc(w * h * 3);
  for (let i = 0, j = 0; i < rgba.length; i += 4, j += 3) {
    rgb[j] = rgba[i];
    rgb[j + 1] = rgba[i + 1];
    rgb[j + 2] = rgba[i + 2];
  }
  const raw = Buffer.alloc((stride + 1) * h);
  const cand = Buffer.alloc(stride);
  for (let y = 0; y < h; y++) {
    const cur = rgb.subarray(y * stride, (y + 1) * stride);
    const prev = y ? rgb.subarray((y - 1) * stride, y * stride) : Buffer.alloc(stride);
    let bestSum = Infinity;
    // Pick the row filter with the smallest sum of absolute residuals.
    for (let f = 0; f < 5; f++) {
      let sum = 0;
      for (let x = 0; x < stride; x++) {
        const a = x >= 3 ? cur[x - 3] : 0;
        const b = prev[x];
        const c = x >= 3 ? prev[x - 3] : 0;
        const pred = f === 0 ? 0 : f === 1 ? a : f === 2 ? b : f === 3 ? (a + b) >> 1 : paeth(a, b, c);
        const v = (cur[x] - pred) & 255;
        cand[x] = v;
        sum += v < 128 ? v : 256 - v;
      }
      if (sum < bestSum) {
        bestSum = sum;
        raw[y * (stride + 1)] = f;
        cand.copy(raw, y * (stride + 1) + 1);
      }
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8;
  ihdr[9] = 2;
  return Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9, memLevel: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// Decodes 8-bit RGB or RGBA PNGs that are not interlaced (the goldens, and Android's Bitmap.compress) to RGBA.
export function decodePng(buf) {
  let off = 8;
  let w = 0;
  let h = 0;
  let type = 0;
  const idat = [];
  while (off < buf.length) {
    const len = buf.readUInt32BE(off);
    const t = buf.toString('ascii', off + 4, off + 8);
    const data = buf.subarray(off + 8, off + 8 + len);
    if (t === 'IHDR') {
      w = data.readUInt32BE(0);
      h = data.readUInt32BE(4);
      type = data[9];
      if (data[8] !== 8 || (type !== 2 && type !== 6) || data[12] !== 0) throw new Error('unsupported PNG');
    } else if (t === 'IDAT') idat.push(data);
    off += 12 + len;
  }
  const bpp = type === 6 ? 4 : 3;
  const stride = w * bpp;
  const raw = zlib.inflateSync(Buffer.concat(idat));
  const px = Buffer.alloc(stride * h);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    for (let x = 0; x < stride; x++) {
      const v = raw[y * (stride + 1) + 1 + x];
      const a = x >= bpp ? px[y * stride + x - bpp] : 0;
      const b = y ? px[(y - 1) * stride + x] : 0;
      const c = x >= bpp && y ? px[(y - 1) * stride + x - bpp] : 0;
      const pred = f === 0 ? 0 : f === 1 ? a : f === 2 ? b : f === 3 ? (a + b) >> 1 : paeth(a, b, c);
      px[y * stride + x] = (v + pred) & 255;
    }
  }
  const rgba = Buffer.alloc(w * h * 4);
  for (let i = 0, j = 0; j < px.length; i += 4, j += bpp) {
    rgba[i] = px[j];
    rgba[i + 1] = px[j + 1];
    rgba[i + 2] = px[j + 2];
    rgba[i + 3] = bpp === 4 ? px[j + 3] : 255;
  }
  return { w, h, rgba };
}

// ---------------------------------------------------------------- comparison

// 2x box downsample of RGBA rows, each channel rounded half up; Swift's validate-msl.swift does the same.
export function downsample2x(rgba, w = WIDTH, h = HEIGHT) {
  const ow = w >> 1;
  const oh = h >> 1;
  const out = Buffer.alloc(ow * oh * 4);
  for (let y = 0; y < oh; y++) {
    for (let x = 0; x < ow; x++) {
      for (let ch = 0; ch < 3; ch++) {
        let sum = 0;
        for (const [dx, dy] of [[0, 0], [1, 0], [0, 1], [1, 1]]) sum += rgba[((2 * y + dy) * w + 2 * x + dx) * 4 + ch];
        out[(y * ow + x) * 4 + ch] = (sum + 2) >> 2;
      }
      out[(y * ow + x) * 4 + 3] = 255;
    }
  }
  return out;
}

// Mean and max absolute RGB difference in 8-bit levels between a full-size render, downsampled 2x,
// and a stored (already downsampled) golden.
export function compare(candidate, golden, w = WIDTH, h = HEIGHT) {
  const a = downsample2x(candidate, w, h);
  let sum = 0;
  let max = 0;
  let n = 0;
  for (let i = 0; i < a.length; i += 4) {
    for (let ch = 0; ch < 3; ch++) {
      const d = Math.abs(a[i + ch] - golden[i + ch]);
      sum += d;
      if (d > max) max = d;
      n++;
    }
  }
  return { mean: sum / n, max };
}

export function readGolden(dir, name) {
  const file = path.join(dir, name);
  if (!fs.existsSync(file)) return null;
  const { w, h, rgba } = decodePng(fs.readFileSync(file));
  if (w !== GOLDEN_WIDTH || h !== GOLDEN_HEIGHT) throw new Error(`${name} is ${w}x${h}; goldens are ${GOLDEN_WIDTH}x${GOLDEN_HEIGHT}. Run render-goldens.mjs.`);
  return rgba;
}
