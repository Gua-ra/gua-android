/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

// Runs every .agsl through Android's own RuntimeShader (SkSL) on a connected Android 13+ device or
// emulator, sets every uniform and, on Android 14+, draws the golden cases on the GPU and compares them
// with the goldens.
// Nothing is installed on the device: AgslDeviceCheck.java is compiled with javac and the SDK's d8 and
// run with app_process from /data/local/tmp, which is removed afterwards.
// Usage: node tools/wallpapers/shaders/check-agsl-device.mjs [--agsl <folder>] [--goldens <folder>] [--sdk <Android SDK>] [--serial <adb serial>] [--out <folder for the device drawings>]
// Needs JDK 17+ (JAVA_HOME or javac on PATH) and an Android SDK with platform-tools, build-tools and a platform.

import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { ANDROID_DIR, LIVE_ARTWORKS } from './generate.mjs';
import { CASES, HEIGHT, WIDTH, compare, decodePng, goldenName, limitsFor, readGolden } from './harness.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DEVICE_DIR = '/data/local/tmp/gua-agsl-check';

const args = {};
for (let i = 2; i < process.argv.length; i += 2) args[process.argv[i].replace(/^--/, '')] = process.argv[i + 1];
const agslDir = args.agsl || path.resolve(HERE, '..', '..', '..', ANDROID_DIR);
const goldenDir = args.goldens || path.join(HERE, 'goldens');
const sdk = args.sdk || process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || path.join(os.homedir(), 'Library', 'Android', 'sdk');

// Highest version folder under dir that holds file.
function latest(dir, file) {
  const found = fs.readdirSync(dir)
    .filter((v) => fs.existsSync(path.join(dir, v, file)))
    .sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
  if (!found.length) throw new Error(`no ${file} under ${dir}`);
  return path.join(dir, found[found.length - 1], file);
}

const adbPath = path.join(sdk, 'platform-tools', 'adb');
const adb = (...a) => execFileSync(adbPath, [...(args.serial ? ['-s', args.serial] : []), ...a], { encoding: 'utf8', maxBuffer: 16 << 20, stdio: ['ignore', 'pipe', 'pipe'] });
const javac = process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, 'bin', 'javac') : 'javac';
const androidJar = latest(path.join(sdk, 'platforms'), 'android.jar');
const d8 = latest(path.join(sdk, 'build-tools'), 'd8');

const api = Number(adb('shell', 'getprop', 'ro.build.version.sdk').trim());
if (!(api >= 33)) throw new Error(`device API ${api}: RuntimeShader needs Android 13 (API 33)`);

const work = fs.mkdtempSync(path.join(os.tmpdir(), 'gua-agsl-check-'));
let failed = false;
try {
  const classes = path.join(work, 'classes');
  fs.mkdirSync(classes);
  execFileSync(javac, ['--release', '17', '-cp', androidJar, '-d', classes, path.join(HERE, 'AgslDeviceCheck.java')], { stdio: 'inherit' });
  // d8 is a wrapper script that runs whichever java is first on PATH.
  const env = process.env.JAVA_HOME ? { ...process.env, PATH: `${path.join(process.env.JAVA_HOME, 'bin')}${path.delimiter}${process.env.PATH}` } : process.env;
  execFileSync(d8, ['--min-api', '33', '--lib', androidJar, '--output', work, path.join(classes, 'AgslDeviceCheck.class')], { stdio: 'inherit', env });

  adb('shell', 'rm', '-rf', DEVICE_DIR);
  adb('shell', 'mkdir', '-p', DEVICE_DIR);
  adb('push', path.join(work, 'classes.dex'), `${DEVICE_DIR}/check.dex`);
  for (const art of LIVE_ARTWORKS) adb('push', path.join(agslDir, `${art.id}.agsl`), `${DEVICE_DIR}/${art.id}.agsl`);

  const cases = CASES.map((c) => `${c.dark ? 'dark' : 'light'}-t${c.t}-p${Math.round(c.pulse * 100)}:${c.t}:${c.pulse}:${c.dark ? 1 : 0}`);
  const log = adb('shell', `CLASSPATH=${DEVICE_DIR}/check.dex`, 'app_process', '/system/bin', 'AgslDeviceCheck',
    DEVICE_DIR, String(WIDTH), String(HEIGHT), LIVE_ARTWORKS.map((a) => a.id).join(','), ...cases);
  process.stdout.write(log);
  if (/^FAIL /m.test(log)) failed = true;

  const pulled = args.out ? path.resolve(args.out) : path.join(work, 'out');
  fs.mkdirSync(pulled, { recursive: true });
  if (api >= 34) {
    adb('pull', `${DEVICE_DIR}/gpu`, pulled);
    console.log(`\nGPU drawing (device API ${api}), 8-bit levels after 2x box downsample:`);
    for (const art of LIVE_ARTWORKS) {
      let worstMean = 0;
      let worstMax = 0;
      let missing = 0;
      for (const c of CASES) {
        const name = goldenName(art.id, c);
        const file = path.join(pulled, 'gpu', name);
        if (!fs.existsSync(file)) { missing++; continue; }
        const shot = decodePng(fs.readFileSync(file));
        if (shot.w !== WIDTH || shot.h !== HEIGHT) throw new Error(`${name}: device drew ${shot.w}x${shot.h}`);
        const golden = readGolden(goldenDir, name);
        if (!golden) throw new Error(`missing golden ${name}`);
        const { mean, max } = compare(shot.rgba, golden);
        worstMean = Math.max(worstMean, mean);
        worstMax = Math.max(worstMax, max);
      }
      const limit = limitsFor(art.id);
      const over = missing > 0 || worstMean > limit.mean || worstMax > limit.max;
      failed = failed || over;
      console.log(`${over ? 'OVER' : 'ok  '} ${art.id.padEnd(18)} worst mean ${worstMean.toFixed(3)}  max ${worstMax.toFixed(2)}${missing ? `  (${missing} cases not drawn)` : ''}`);
    }
  }
} finally {
  try {
    adb('shell', 'rm', '-rf', DEVICE_DIR);
  } catch {
    // the device may be gone; nothing else was left on it
  }
  fs.rmSync(work, { recursive: true, force: true });
}
process.exit(failed ? 1 : 0);
