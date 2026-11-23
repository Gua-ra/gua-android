/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

// Ports the Lab's live GLSL shaders to MSL (iOS, compiled at runtime) and AGSL (Android 13+).
// Usage: node tools/wallpapers/shaders/generate.mjs --ios <gua-ios checkout> --android <gua-android checkout> [--lab <Lab .html or folder of module .js files>]
// The contract both apps rely on (entry points, uniforms, buffer layout) is in README.md next to this file.

import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));

// The live rows of the shared catalog, in catalog order: { id (wire id), lab (Lab id) }.
export const LIVE_ARTWORKS = JSON.parse(fs.readFileSync(path.join(HERE, '..', 'artworks.json'), 'utf8'))
  .artworks.filter((a) => a.live).map((a) => ({ id: a.id, lab: a.lab }));

export const IOS_DIR = 'ElementX/Resources/Wallpapers';
export const ANDROID_DIR = 'libraries/matrixui/src/main/assets/wallpapers';

const UNIFORMS = [
  ['vec2', 'u_res'], ['float', 'u_time'], ['float', 'u_dark'],
  ['float', 'u_pulse'], ['vec2', 'u_tilt'], ['vec2', 'u_origin'],
];

const HOSKINS_MIT = [
  'hash12 and hash22 are "Hash without Sine" by David Hoskins, used under the MIT licence:',
  '',
  'Copyright (c) 2014 David Hoskins.',
  '',
  'Permission is hereby granted, free of charge, to any person obtaining a copy',
  'of this software and associated documentation files (the "Software"), to deal',
  'in the Software without restriction, including without limitation the rights',
  'to use, copy, modify, merge, publish, distribute, sublicense, and/or sell',
  'copies of the Software, and to permit persons to whom the Software is',
  'furnished to do so, subject to the following conditions:',
  '',
  'The above copyright notice and this permission notice shall be included in all',
  'copies or substantial portions of the Software.',
  '',
  'THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR',
  'IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,',
  'FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE',
  'AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER',
  'LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,',
  'OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE',
  'SOFTWARE.',
];
const HASH_FUNCTIONS = new Set(['hash12', 'hash22']);
const HOSKINS_BLOCK = `/*\n${HOSKINS_MIT.map((l) => (l ? ` * ${l}` : ' *')).join('\n')}\n */`;

function labScripts(labPath) {
  const stat = fs.statSync(labPath);
  if (stat.isDirectory()) {
    return fs.readdirSync(labPath).filter((f) => f.endsWith('.js')).sort()
      .map((f) => ({ name: f, code: fs.readFileSync(path.join(labPath, f), 'utf8') }));
  }
  const html = fs.readFileSync(labPath, 'utf8');
  return [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)]
    .map((m, i) => ({ name: `script ${i + 1}`, code: m[1] }));
}

// Runs the wallpaper modules the way the Lab page does, so each shader is exactly the string the
// Lab hands to WebGL (shared HEAD prelude prepended, hex() colours expanded). Modules that need the
// page (the UI script) fail here and are skipped; a missing shader is reported below.
export function loadLabShaders(labPath) {
  const window = {};
  const context = vm.createContext({ window, console: { log() {}, warn() {}, error() {} } });
  const failures = [];
  for (const s of labScripts(labPath)) {
    try {
      vm.runInContext(s.code, context, { filename: s.name });
    } catch (e) {
      failures.push(`${s.name}: ${e.message}`);
    }
  }
  const all = window.GUA_WALLPAPERS || [];
  const out = new Map();
  for (const art of LIVE_ARTWORKS) {
    const wp = all.find((w) => w.id === art.lab);
    if (!wp || typeof wp.frag !== 'string') {
      throw new Error(`Lab shader ${art.lab} not found in ${labPath}${failures.length ? ` (module errors: ${failures.join('; ')})` : ''}`);
    }
    out.set(art.id, wp.frag);
  }
  return out;
}

export function defaultLabPath() {
  const dir = path.resolve(HERE, '..', 'lab');
  return fs.existsSync(dir) ? dir : null;
}

// Top level of a Lab shader: a precision statement, the uniform block, then functions.
export function parseGlsl(src) {
  if (/^\s*#/m.test(src)) throw new Error('preprocessor directives are not supported');
  const uniforms = [];
  const functions = [];
  let i = 0;
  const n = src.length;
  while (i < n) {
    while (i < n && /\s/.test(src[i])) i++;
    if (i >= n) break;
    if (src.startsWith('//', i)) { i = src.indexOf('\n', i); if (i < 0) i = n; continue; }
    if (src.startsWith('/*', i)) { i = src.indexOf('*/', i) + 2; continue; }
    let j = i;
    let paren = 0;
    while (j < n && !(paren === 0 && (src[j] === ';' || src[j] === '{'))) {
      if (src[j] === '(') paren++;
      if (src[j] === ')') paren--;
      j++;
    }
    const head = src.slice(i, j).trim();
    if (src[j] === ';') {
      const u = head.match(/^uniform\s+(\w+)\s+(\w+)$/);
      if (u) uniforms.push([u[1], u[2]]);
      else if (!/^precision\s+\w+\s+float$/.test(head)) throw new Error(`unsupported global statement: ${head}`);
      i = j + 1;
      continue;
    }
    let depth = 0;
    let k = j;
    for (; k < n; k++) {
      // Braces inside comments must not count.
      if (src.startsWith('//', k)) { k = src.indexOf('\n', k); if (k < 0) k = n; continue; }
      if (src[k] === '{') depth++;
      if (src[k] === '}' && --depth === 0) break;
    }
    const sig = head.match(/^(\w+)\s+(\w+)\s*\(([^)]*)\)$/);
    if (!sig) throw new Error(`unsupported global: ${head}`);
    functions.push({ ret: sig[1], name: sig[2], params: sig[3].trim(), body: src.slice(j + 1, k) });
    i = k + 1;
  }
  const want = UNIFORMS.map((u) => u.join(' ')).join(', ');
  const got = uniforms.map((u) => u.join(' ')).join(', ');
  if (got !== want) throw new Error(`uniforms differ from the contract: ${got}`);
  const main = functions.find((f) => f.name === 'main');
  if (!main || main.ret !== 'void' || main.params !== '') throw new Error('missing void main()');
  // Comments are prose and may use these words ("while it turns").
  const code = src.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/[^\n]*/g, '');
  for (const t of ['mat3', 'mat4', 'ivec2', 'ivec3', 'ivec4', 'bvec2', 'bvec3', 'bvec4', 'sampler2D', 'texture2D', 'while', 'discard', 'dFdx', 'dFdy', 'fwidth']) {
    if (new RegExp(`\\b${t}\\b`).test(code)) throw new Error(`${t} is not handled by the port`);
  }
  return functions;
}

const word = (w) => new RegExp(`\\b${w}\\b`, 'g');

function indent(text, pad) {
  return text.split('\n').map((l) => (l.trim() ? pad + l : '')).join('\n');
}

function fileHeader(prefix, art) {
  return [
    `${prefix}`,
    `${prefix} Copyright 2026 Gua`,
    `${prefix}`,
    `${prefix} SPDX-License-Identifier: AGPL-3.0-only`,
    `${prefix} Please see LICENSE files in the repository root for full details.`,
    `${prefix}`,
    `${prefix} Generated by tools/wallpapers/shaders/generate.mjs (gua-android) from the Lab shader ${art.lab}.`,
    `${prefix} Do not edit; change the Lab source and run the script. Contract: tools/wallpapers/shaders/README.md.`,
    `${prefix}`,
  ].join('\n');
}

// Body text of a function without surrounding blank lines and common indentation.
function bodyText(body) {
  const lines = body.replace(/^\s*\n/, '').replace(/\s+$/, '').split('\n');
  const pad = Math.min(...lines.filter((l) => l.trim()).map((l) => l.match(/^ */)[0].length));
  return lines.map((l) => l.slice(pad)).join('\n');
}

export function toMsl(art, glsl) {
  const fns = parseGlsl(glsl);
  const members = [];
  let noticeDone = false;
  for (const f of fns) {
    const lines = [];
    if (HASH_FUNCTIONS.has(f.name) && !noticeDone) {
      lines.push(HOSKINS_BLOCK);
      noticeDone = true;
    }
    lines.push(`${f.ret} ${f.name}(${f.params}) {`, indent(bodyText(f.body), '    '), '}');
    members.push(indent(lines.join('\n'), '    '));
  }
  return `${fileHeader('//', art)}
// Each Lab GLSL function is a member of GuaLabShader, so the bodies compile unchanged as Metal C++:
// the uniforms, gl_FragCoord and gl_FragColor are members, the vec/mat names are typedefs, and the
// built-ins whose Metal meaning differs are shadowed by members below.

#include <metal_stdlib>
using namespace metal;

typedef float2 vec2;
typedef float3 vec3;
typedef float4 vec4;
typedef float2x2 mat2;

struct GuaWallpaperUniforms {
    float2 u_res;
    float2 u_tilt;
    float2 u_origin;
    float u_time;
    float u_dark;
    float u_pulse;
    float u_pad;
};

struct GuaLabShader {
    float2 u_res;
    float u_time;
    float u_dark;
    float u_pulse;
    float2 u_tilt;
    float2 u_origin;
    float4 gl_FragCoord;
    float4 gl_FragColor;

    // GLSL mod floors; Metal's fmod truncates.
    static float mod(float x, float y) { return x - y * floor(x / y); }
    static float2 mod(float2 x, float y) { return x - y * floor(x / y); }
    static float3 mod(float3 x, float y) { return x - y * floor(x / y); }
    static float atan(float y, float x) { return metal::atan2(y, x); }
    static float atan(float x) { return metal::atan(x); }
    // The Lab relies on reversed edges (edge0 > edge1), which Metal leaves undefined.
    static float smoothstep(float e0, float e1, float x) { float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0); return t * t * (3.0 - 2.0 * t); }

${members.join('\n\n')}
};

struct GuaWallpaperVertexOut {
    float4 position [[position]];
};

// One triangle that covers the viewport; draw 3 vertices with no vertex buffer.
vertex GuaWallpaperVertexOut gua_wallpaper_vertex(uint vid [[vertex_id]]) {
    GuaWallpaperVertexOut v;
    v.position = float4(vid == 1 ? 3.0 : -1.0, vid == 2 ? 3.0 : -1.0, 0.0, 1.0);
    return v;
}

fragment float4 gua_wallpaper_fragment(GuaWallpaperVertexOut in [[stage_in]],
                                       constant GuaWallpaperUniforms &u [[buffer(0)]]) {
    GuaLabShader s;
    s.u_res = u.u_res;
    s.u_time = u.u_time;
    s.u_dark = u.u_dark;
    s.u_pulse = u.u_pulse;
    s.u_tilt = u.u_tilt;
    s.u_origin = u.u_origin;
    // Metal's origin is top-left; the Lab shaders expect GL's bottom-left with y up.
    s.gl_FragCoord = float4(in.position.x, u.u_res.y - in.position.y, 0.0, 1.0);
    s.gl_FragColor = float4(0.0);
    s.main();
    return s.gl_FragColor;
}
`;
}

const AGSL_TYPES = [['vec2', 'float2'], ['vec3', 'float3'], ['vec4', 'float4'], ['mat2', 'float2x2']];

function agslRewrite(text) {
  let s = text;
  for (const [from, to] of AGSL_TYPES) s = s.replace(word(from), to);
  return s
    .replace(/\bsmoothstep\s*\(/g, 'gua_smoothstep(')
    .replace(word('gl_FragCoord'), 'gua_FragCoord')
    .replace(word('gl_FragColor'), 'gua_FragColor');
}

export function toAgsl(art, glsl) {
  const fns = parseGlsl(glsl);
  const helpers = fns.filter((f) => f.name !== 'main');
  const main = fns.find((f) => f.name === 'main');
  if (/\breturn\b/.test(main.body)) throw new Error(`${art.lab}: main() returns early; the AGSL wrapper inlines it`);

  // AGSL has no gl_FragCoord: every helper that reads it, directly or through another helper, takes it first.
  const needs = new Set();
  for (let changed = true; changed;) {
    changed = false;
    for (const f of helpers) {
      if (needs.has(f.name)) continue;
      if (/\bgl_FragCoord\b/.test(f.body) || [...needs].some((g) => new RegExp(`\\b${g}\\s*\\(`).test(f.body))) {
        needs.add(f.name);
        changed = true;
      }
    }
  }
  const threadCalls = (text) => {
    let s = text;
    for (const g of needs) {
      s = s.replace(new RegExp(`\\b${g}\\s*\\(\\s*(\\))?`, 'g'), (_m, close) => (close ? `${g}(gua_FragCoord)` : `${g}(gua_FragCoord, `));
    }
    return s;
  };

  const out = [];
  let noticeDone = false;
  for (const f of helpers) {
    if (HASH_FUNCTIONS.has(f.name) && !noticeDone) {
      out.push(HOSKINS_BLOCK);
      noticeDone = true;
    }
    let params = f.params;
    if (needs.has(f.name)) params = params ? `vec4 gl_FragCoord, ${params}` : 'vec4 gl_FragCoord';
    out.push(agslRewrite(`${f.ret} ${f.name}(${params}) {\n${indent(bodyText(threadCalls(f.body)), '    ')}\n}`));
  }

  return `${fileHeader('//', art)}
// Android 13+ RuntimeShader. fragCoord is in pixels with the origin top-left, in the same space as
// u_res; main() flips it to the Lab's GL convention (origin bottom-left, y up).

uniform float2 u_res;
uniform float u_time;
uniform float u_dark;
uniform float u_pulse;
uniform float2 u_tilt;
uniform float2 u_origin;

// The Lab relies on reversed edges (edge0 > edge1), which GLSL and SPIR-V leave undefined.
float gua_smoothstep(float e0, float e1, float x) {
    float t = clamp((x - e0) / (e1 - e0), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

${out.join('\n\n')}

half4 main(float2 fragCoord) {
    float4 gua_FragCoord = float4(fragCoord.x, u_res.y - fragCoord.y, 0.0, 1.0);
    float4 gua_FragColor = float4(0.0);
${indent(agslRewrite(bodyText(threadCalls(main.body))), '    ')}
    return half4(gua_FragColor);
}
`;
}

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith('--')) throw new Error(`unexpected argument ${a}`);
    args[a.slice(2)] = argv[++i];
  }
  return args;
}

export function generateShaders({ labPath, iosRoot, androidRoot }) {
  const shaders = loadLabShaders(labPath);
  const written = [];
  const iosDir = iosRoot && path.join(iosRoot, IOS_DIR);
  const androidDir = androidRoot && path.join(androidRoot, ANDROID_DIR);
  for (const dir of [iosDir, androidDir]) if (dir) fs.mkdirSync(dir, { recursive: true });
  for (const art of LIVE_ARTWORKS) {
    const glsl = shaders.get(art.id);
    if (iosDir) {
      const file = path.join(iosDir, `${art.id}.msl`);
      fs.writeFileSync(file, toMsl(art, glsl));
      written.push(file);
    }
    if (androidDir) {
      const file = path.join(androidDir, `${art.id}.agsl`);
      fs.writeFileSync(file, toAgsl(art, glsl));
      written.push(file);
    }
  }
  return written;
}

if (process.argv[1] && import.meta.url === pathToFileURL(fs.realpathSync(process.argv[1])).href) {
  const args = parseArgs(process.argv.slice(2));
  const labPath = args.lab || defaultLabPath();
  if (!labPath || (!args.ios && !args.android)) {
    console.error('usage: generate.mjs --ios <gua-ios checkout> --android <gua-android checkout> [--lab <Lab .html or module folder>]');
    process.exit(2);
  }
  for (const f of generateShaders({ labPath, iosRoot: args.ios, androidRoot: args.android })) {
    console.log(`${String(fs.statSync(f).size).padStart(7)}  ${f}`);
  }
}
