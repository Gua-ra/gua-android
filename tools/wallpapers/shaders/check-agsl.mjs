/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

// Checks the generated .agsl files without an Android device, in two passes:
// 1. A lint against the AGSL (SkSL runtime effect, GLSL ES 1.00 subset) rules: globals, main()
//    signature, ES2 built-ins only, declare-before-use, loop shape, reserved words, swizzles.
// 2. Each file compiled as GLSL ES 1.00 behind a few type #defines and rendered in headless Chromium
//    for every golden case, which type-checks the code and proves the port (y flip, fragCoord
//    threading) still draws the goldens. check-agsl-device.mjs runs the files through Android's own SkSL.
// Usage: node tools/wallpapers/shaders/check-agsl.mjs [--agsl <folder>] [--goldens <folder>] [--playwright <dir>] [--lint-only true]

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { ANDROID_DIR, LIVE_ARTWORKS } from './generate.mjs';
import { CASES, compare, goldenName, limitsFor, openRenderer, readGolden } from './harness.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));

const TYPES = new Set([
  'void', 'bool', 'int', 'float', 'half',
  'bool2', 'bool3', 'bool4', 'int2', 'int3', 'int4',
  'float2', 'float3', 'float4', 'half2', 'half3', 'half4',
  'float2x2', 'float3x3', 'float4x4', 'half2x2', 'half3x3', 'half4x4',
]);
const UNIFORM_TYPES = new Set(['float', 'float2', 'float3', 'float4', 'half', 'half2', 'half3', 'half4', 'float2x2', 'float3x3', 'float4x4']);
const KEYWORDS = new Set(['if', 'else', 'for', 'return', 'break', 'continue', 'const', 'in', 'out', 'inout', 'uniform', 'true', 'false']);
// Built-ins available to runtime effects (ES2 level; ES3-only ones such as round or trunc are not).
const BUILTINS = new Set([
  'radians', 'degrees', 'sin', 'cos', 'tan', 'asin', 'acos', 'atan', 'pow', 'exp', 'log', 'exp2', 'log2',
  'sqrt', 'inversesqrt', 'abs', 'sign', 'floor', 'ceil', 'fract', 'mod', 'min', 'max', 'clamp', 'saturate',
  'mix', 'step', 'smoothstep', 'length', 'distance', 'dot', 'cross', 'normalize', 'faceforward', 'reflect',
  'refract', 'matrixCompMult', 'lessThan', 'lessThanEqual', 'greaterThan', 'greaterThanEqual', 'equal',
  'notEqual', 'any', 'all', 'not', 'unpremul', 'toLinearSrgb', 'fromLinearSrgb',
]);
// GLSL and SkSL reserved words that are not usable as identifiers.
const RESERVED = new Set([
  'attribute', 'varying', 'precision', 'invariant', 'asm', 'class', 'union', 'enum', 'typedef', 'template',
  'this', 'packed', 'goto', 'switch', 'default', 'case', 'inline', 'noinline', 'volatile', 'public', 'static',
  'extern', 'external', 'interface', 'long', 'short', 'double', 'fixed', 'unsigned', 'superp', 'input', 'output',
  'hvec2', 'hvec3', 'hvec4', 'dvec2', 'dvec3', 'dvec4', 'fvec2', 'fvec3', 'fvec4', 'sampler1D', 'sampler2D',
  'sampler3D', 'samplerCube', 'sampler2DRect', 'sampler3DRect', 'sampler2DRectShadow', 'sizeof', 'cast',
  'namespace', 'using', 'while', 'do', 'discard', 'struct', 'lowp', 'mediump', 'highp', 'flat', 'smooth',
  'layout', 'shader', 'colorFilter', 'blender', 'sample', 'uint', 'buffer', 'readonly', 'writeonly', 'pure',
  'vec2', 'vec3', 'vec4', 'mat2', 'mat3', 'mat4', 'ivec2', 'ivec3', 'ivec4', 'bvec2', 'bvec3', 'bvec4',
  'texture2D', 'texture', 'dFdx', 'dFdy', 'fwidth', 'round', 'trunc', 'roundEven',
]);

function tokenize(src) {
  const code = src.replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' ')).replace(/\/\/[^\n]*/g, '');
  const tokens = [];
  const re = /\s+|(\d+\.\d*(?:[eE][-+]?\d+)?|\.\d+(?:[eE][-+]?\d+)?|\d+(?:[eE][-+]?\d+)?)|([A-Za-z_]\w*)|(\+\+|--|&&|\|\||==|!=|<=|>=|\+=|-=|\*=|\/=|<<|>>|[-+*/%<>=!?:;,.(){}[\]&|^~#])|(.)/gy;
  let m;
  let line = 1;
  while ((m = re.exec(code))) {
    if (m[0].includes('\n')) line += m[0].split('\n').length - 1;
    if (m[1]) tokens.push({ t: 'num', v: m[1], line });
    else if (m[2]) tokens.push({ t: 'id', v: m[2], line });
    else if (m[3]) tokens.push({ t: 'op', v: m[3], line });
    else if (m[4]) tokens.push({ t: 'bad', v: m[4], line });
  }
  return tokens;
}

export function lintAgsl(src) {
  const errors = [];
  const err = (tok, msg) => errors.push(`line ${tok ? tok.line : '?'}: ${msg}`);
  const toks = tokenize(src);
  const uniforms = new Set();
  const functions = new Map();
  let i = 0;
  const peek = (k = 0) => toks[i + k];

  for (const tok of toks) {
    if (tok.t === 'bad') err(tok, `unexpected character ${tok.v}`);
    if (tok.t === 'op' && ['#', '%', '<<', '>>', '&', '|', '^', '~', '[', ']'].includes(tok.v)) err(tok, `${tok.v} is not allowed in AGSL runtime effects`);
    if (tok.t === 'id' && RESERVED.has(tok.v)) err(tok, `${tok.v} is reserved or not available in AGSL`);
    if (tok.t === 'id' && (/^(gl|sk)_/.test(tok.v) || tok.v.includes('__'))) err(tok, `identifier ${tok.v} uses a reserved prefix`);
  }

  while (i < toks.length) {
    const t0 = peek();
    if (t0.v === 'uniform') {
      const [type, name, semi] = [peek(1), peek(2), peek(3)];
      if (!UNIFORM_TYPES.has(type.v) || name.t !== 'id' || semi.v !== ';') err(t0, 'malformed uniform');
      uniforms.add(name.v);
      i += 4;
      continue;
    }
    // Function definition: type name ( params ) { body }
    const [ret, name, open] = [peek(0), peek(1), peek(2)];
    if (!TYPES.has(ret.v) || name.t !== 'id' || open.v !== '(') {
      err(t0, `only uniforms and functions may appear at the top level (found ${t0.v})`);
      break;
    }
    i += 3;
    const params = [];
    while (peek() && peek().v !== ')') {
      const q = [];
      while (peek().v !== ',' && peek().v !== ')') q.push(toks[i++]);
      if (peek().v === ',') i++;
      const ids = q.filter((x) => x.t === 'id');
      const qual = ids.filter((x) => ['in', 'out', 'inout', 'const'].includes(x.v));
      const rest = ids.filter((x) => !qual.includes(x));
      if (rest.length !== 2 || !TYPES.has(rest[0].v)) err(name, `bad parameter in ${name.v}`);
      else params.push(rest[1].v);
    }
    i++;
    if (peek().v !== '{') { err(name, `${name.v}: prototypes are not used here`); break; }
    const bodyStart = i;
    let depth = 0;
    for (; i < toks.length; i++) {
      if (toks[i].v === '{') depth++;
      if (toks[i].v === '}' && --depth === 0) break;
    }
    const body = toks.slice(bodyStart + 1, i);
    i++;
    if (functions.has(name.v) && functions.get(name.v).arity === params.length) err(name, `${name.v} defined twice`);
    if (BUILTINS.has(name.v)) err(name, `${name.v} redefines a built-in`);
    lintBody(name, params, body, uniforms, functions, err);
    functions.set(name.v, { arity: params.length, ret: ret.v, params });
    if (name.v === 'main' && (ret.v !== 'half4' && ret.v !== 'float4' || params.length !== 1)) err(name, 'main must be half4 main(float2)');
  }
  if (!functions.has('main')) err(null, 'no main()');
  return errors;
}

function lintBody(fn, params, body, uniforms, functions, err) {
  // Scopes: each { opens one; a for header's declarations live in the loop's scope.
  const scopes = [new Set(params)];
  const declared = (v) => scopes.some((s) => s.has(v));
  for (let k = 0; k < body.length; k++) {
    const tok = body[k];
    const prev = body[k - 1];
    const next = body[k + 1];
    if (tok.v === '{') scopes.push(new Set());
    if (tok.v === '}') scopes.pop();
    if (tok.v === 'for') {
      const header = [];
      let j = k + 2;
      for (let depth = 1; j < body.length && depth; j++) {
        if (body[j].v === '(') depth++;
        if (body[j].v === ')') depth--;
        if (depth) header.push(body[j].v);
      }
      const h = header.join(' ');
      const m = h.match(/^int (\w+) = (\d+) ; \1 (<|<=|>|>=|!=) (\d+) ; (\1 \+\+|\+\+ \1|\1 --|-- \1|\1 \+= \d+|\1 -= \d+)$/);
      if (!m) err(tok, `${fn.v}: loop header "${h}" is not of the form for(int i=K;i<N;i++) with constants`);
      else {
        scopes.push(new Set([m[1]]));
        // The loop body must not assign the index.
        const after = body.slice(j);
        let depth = 0;
        for (let a = 0; a < after.length; a++) {
          if (after[a].v === '{') depth++;
          if (after[a].v === '}' && --depth === 0) break;
          if (after[a].v === m[1] && after[a + 1] && /^(=|\+=|-=|\*=|\/=|\+\+|--)$/.test(after[a + 1].v)) err(after[a], `${fn.v}: loop index ${m[1]} is modified`);
        }
        // Pop the loop scope with the body's closing brace.
        let d = 0;
        for (let a = j; a < body.length; a++) {
          if (body[a].v === '{') d++;
          if (body[a].v === '}' && --d === 0) { body[a] = { ...body[a], popLoop: true }; break; }
        }
      }
      k = j - 1;
      continue;
    }
    if (tok.popLoop) scopes.pop();
    if (tok.t !== 'id') continue;
    if (prev && prev.v === '.') {
      checkSwizzle(tok);
      continue;
    }
    if (TYPES.has(tok.v)) {
      // Declarations: type name [= ...] {, name [= ...]} ;
      if (next && next.t === 'id' && !(prev && prev.v === '(' && body[k + 2] && body[k + 2].v === ')')) {
        let a = k + 1;
        let depth = 0;
        for (; a < body.length; a++) {
          const b = body[a];
          if (b.v === '(') depth++;
          if (b.v === ')') depth--;
          if (depth === 0 && b.v === ';') break;
          const isName = b.t === 'id' && (a === k + 1 || (depth === 0 && body[a - 1].v === ','));
          if (isName) {
            if (BUILTINS.has(b.v) || functions.has(b.v)) err(b, `${fn.v}: local ${b.v} hides a function`);
            if (uniforms.has(b.v)) err(b, `${fn.v}: local ${b.v} hides a uniform`);
            scopes[scopes.length - 1].add(b.v);
          } else if (b.t === 'id' && body[a - 1].v === '.') {
            checkSwizzle(b);
          } else if (b.t === 'id') {
            checkUse(b, body[a + 1]);
          }
        }
        k = a;
      }
      continue;
    }
    checkUse(tok, next);
  }

  function checkSwizzle(tok) {
    if (!/^([xyzw]{1,4}|[rgba]{1,4})$/.test(tok.v)) err(tok, `${fn.v}: bad swizzle .${tok.v}`);
  }

  function checkUse(tok, next) {
    if (KEYWORDS.has(tok.v) || TYPES.has(tok.v)) return;
    if (next && next.v === '(') {
      if (BUILTINS.has(tok.v)) return;
      if (functions.has(tok.v)) return;
      err(tok, `${fn.v}: call to ${tok.v}, which is not a built-in or an earlier function`);
      return;
    }
    if (declared(tok.v) || uniforms.has(tok.v)) return;
    err(tok, `${fn.v}: ${tok.v} is not declared before use`);
  }
}

// AGSL types spelled as GLSL ES 1.00, and AGSL's main(fragCoord) driven from gl_FragCoord the way
// Android passes it (pixel centres, origin top-left).
export function agslAsGlsl(src) {
  return `precision highp float;
#define float2 vec2
#define float3 vec3
#define float4 vec4
#define float2x2 mat2
#define half float
#define half2 vec2
#define half3 vec3
#define half4 vec4
#define main agsl_main
${src}
#undef main
void main() {
    gl_FragColor = vec4(agsl_main(vec2(gl_FragCoord.x, u_res.y - gl_FragCoord.y)));
}
`;
}

if (process.argv[1] && import.meta.url === pathToFileURL(fs.realpathSync(process.argv[1])).href) {
  const args = {};
  for (let i = 2; i < process.argv.length; i += 2) args[process.argv[i].replace(/^--/, '')] = process.argv[i + 1];
  const agslDir = args.agsl || path.resolve(HERE, '..', '..', '..', ANDROID_DIR);
  const goldenDir = args.goldens || path.join(HERE, 'goldens');

  let failed = false;
  const sources = new Map();
  for (const art of LIVE_ARTWORKS) {
    const src = fs.readFileSync(path.join(agslDir, `${art.id}.agsl`), 'utf8');
    sources.set(art.id, src);
    const errors = lintAgsl(src);
    console.log(`lint ${art.id.padEnd(18)} ${errors.length ? `${errors.length} problems` : 'ok'}`);
    for (const e of errors) console.log(`  ${e}`);
    failed = failed || errors.length > 0;
  }

  if (args['lint-only'] !== 'true') {
    const r = await openRenderer({ playwright: args.playwright, backend: args.backend });
    console.log(`\nGLSL shim render (${r.renderer}), 8-bit levels after 2x box downsample:`);
    try {
      for (const art of LIVE_ARTWORKS) {
        let images;
        try {
          images = await r.render(agslAsGlsl(sources.get(art.id)), CASES);
        } catch (e) {
          console.log(`${art.id}: does not compile as GLSL ES 1.00: ${e.message}`);
          failed = true;
          continue;
        }
        let worstMean = 0;
        let worstMax = 0;
        images.forEach((rgba, i) => {
          const golden = readGolden(goldenDir, goldenName(art.id, CASES[i]));
          if (!golden) throw new Error(`missing golden ${goldenName(art.id, CASES[i])}`);
          const { mean, max } = compare(rgba, golden);
          worstMean = Math.max(worstMean, mean);
          worstMax = Math.max(worstMax, max);
        });
        const limit = limitsFor(art.id);
        const over = worstMean > limit.mean || worstMax > limit.max;
        failed = failed || over;
        console.log(`${over ? 'OVER' : 'ok  '} ${art.id.padEnd(18)} worst mean ${worstMean.toFixed(3)}  max ${worstMax.toFixed(2)}`);
      }
    } finally {
      await r.close();
    }
  }
  process.exit(failed ? 1 : 0);
}
