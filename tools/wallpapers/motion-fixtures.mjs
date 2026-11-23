#!/usr/bin/env node
/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

// Writes live-wallpaper-motion-fixtures.json: the reference for the wallpaper tilt, the still's parallax rect and the send pulse,
// which the Android and iOS unit tests both read. Usage: node motion-fixtures.mjs <output file>

import fs from 'node:fs';

const TILT = { maxAngleDegrees: 20, recentreSeconds: 2, smoothingSeconds: 0.06, maxStepSeconds: 0.1 };
const STILL = { width: 1290, height: 2796, shiftShare: 0.015 };
const PULSE = { rise: 0.25, decay: 0.9, end: 4.2 };

const round = (x) => Number(x.toFixed(12)) + 0;

function cross(a, b) {
  return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
}

function normalize(v) {
  const l = Math.hypot(v[0], v[1], v[2]);
  return [v[0] / l, v[1] / l, v[2] / l];
}

function makeTracker() {
  return { v: [0, 0, 1], tilt: [0, 0], last: null };
}

// One gyroscope sample: rate in rad/s around the device's x (right), y (top) and z (out of the screen) axes, right-hand rule.
function sample(s, t, rate) {
  if (s.last === null) {
    s.last = t;
    return s.tilt;
  }
  const dt = Math.min(Math.max(t - s.last, 0), TILT.maxStepSeconds);
  s.last = t;
  // The viewer's direction is fixed in the world, so in the device's frame it turns against the device.
  const turn = cross(rate, s.v);
  s.v = normalize([s.v[0] - turn[0] * dt, s.v[1] - turn[1] * dt, s.v[2] - turn[2] * dt]);
  const k = 1 - Math.exp(-dt / TILT.recentreSeconds);
  s.v = normalize([s.v[0] * (1 - k), s.v[1] * (1 - k), s.v[2] * (1 - k) + k]);
  const full = Math.sin((TILT.maxAngleDegrees * Math.PI) / 180);
  const target = [Math.tanh(s.v[0] / full), Math.tanh(s.v[1] / full)];
  const a = 1 - Math.exp(-dt / TILT.smoothingSeconds);
  s.tilt = [s.tilt[0] + (target[0] - s.tilt[0]) * a, s.tilt[1] + (target[1] - s.tilt[1]) * a];
  return s.tilt;
}

function tiltCase(name, segments) {
  // segments: [{ seconds, rate, hz }] or { stop: true } or { gap: seconds }
  const s = makeTracker();
  const events = [];
  const expected = [];
  let t = 1000;
  for (const seg of segments) {
    if (seg.stop) {
      events.push({ stop: true });
      s.last = null;
      continue;
    }
    if (seg.gap) {
      t += seg.gap;
      continue;
    }
    const step = 1 / seg.hz;
    const n = Math.round(seg.seconds * seg.hz);
    for (let i = 0; i < n; i++) {
      t += step;
      events.push({ t: round(t), rate: seg.rate });
      const out = sample(s, round(t), seg.rate);
      expected.push([round(out[0]), round(out[1])]);
    }
  }
  return { name, events, expected };
}

const tiltCases = [
  tiltCase('held still', [{ seconds: 0.5, rate: [0, 0, 0], hz: 50 }]),
  tiltCase('right edge turns toward the viewer, then rests', [
    { seconds: 0.3, rate: [0, -0.8, 0], hz: 50 },
    { seconds: 1.2, rate: [0, 0, 0], hz: 50 },
  ]),
  tiltCase('top edge turns toward the viewer at 60 Hz', [
    { seconds: 0.4, rate: [0.6, 0, 0], hz: 60 },
    { seconds: 0.4, rate: [0, 0, 0], hz: 60 },
  ]),
  tiltCase('a fast turn saturates', [{ seconds: 0.5, rate: [0, 3, 0], hz: 50 }]),
  tiltCase('spinning on the screen axis turns an existing tilt', [
    { seconds: 0.25, rate: [0, -1, 0], hz: 50 },
    { seconds: 0.5, rate: [0, 0, 1.5], hz: 50 },
  ]),
  tiltCase('a late sample advances at most 0.1 s', [
    { seconds: 0.2, rate: [0.5, 0.5, 0], hz: 50 },
    { gap: 0.4 },
    { seconds: 0.1, rate: [0.5, 0.5, 0], hz: 50 },
  ]),
  tiltCase('a stop keeps the tilt and the next sample only restarts the clock', [
    { seconds: 0.3, rate: [0, -0.8, 0.2], hz: 50 },
    { stop: true },
    { gap: 3 },
    { seconds: 0.4, rate: [0, 0, 0], hz: 50 },
  ]),
];

// rotation: the screen's rotation from the device's natural portrait, Android's Surface rotation (90 when the device is turned a quarter
// turn counter-clockwise; iOS's landscapeRight).
function toInterface(rotation, d) {
  switch (rotation) {
    case 0: return [d[0], d[1]];
    case 90: return [-d[1], d[0]];
    case 180: return [-d[0], -d[1]];
    case 270: return [d[1], -d[0]];
  }
  throw new Error(`rotation ${rotation}`);
}

const interfaceCases = [0, 90, 180, 270].map((rotation) => ({ rotation, device: [0.3, -0.2], expected: toInterface(rotation, [0.3, -0.2]).map((x) => round(x)) }));

function stillRect(view, anchor, tilt) {
  const shift = STILL.shiftShare * Math.max(view[0], view[1]);
  const box = [view[0] + 2 * shift, view[1] + 2 * shift];
  const scale = Math.max(box[0] / STILL.width, box[1] / STILL.height);
  const size = [STILL.width * scale, STILL.height * scale];
  const x = -shift - (size[0] - box[0]) * anchor[0] - tilt[0] * shift;
  const y = -shift - (size[1] - box[1]) * anchor[1] + tilt[1] * shift;
  return { shift: round(shift), rect: [round(x), round(y), round(size[0]), round(size[1])] };
}

const stillCases = [];
for (const view of [[393, 852], [852, 393], [1024, 1366]]) {
  for (const anchor of [[0.5, 0.5], [0.3, 0.7]]) {
    for (const tilt of [[0, 0], [1, 0], [0, -1], [-0.5, 0.25]]) {
      stillCases.push({ view, anchor, tilt, expected: stillRect(view, anchor, tilt) });
    }
  }
}

function envelope(seconds) {
  if (seconds < 0 || seconds > PULSE.end) return 0;
  const r = Math.min(1, seconds / PULSE.rise);
  return r * r * (3 - 2 * r) * Math.exp(-Math.max(0, seconds - PULSE.rise) / PULSE.decay);
}

// A send while a pulse plays starts the new one where the rising edge has the current value, so the pulse never jumps.
function startAfterSend(start, now) {
  const v = start === null ? 0 : envelope(now - start);
  const rising = 0.5 - Math.sin(Math.asin(1 - 2 * v) / 3);
  return now - PULSE.rise * rising;
}

function pulseCase(name, sends, times) {
  let start = null;
  let next = 0;
  const samples = [];
  for (const t of times) {
    while (next < sends.length && sends[next] <= t) {
      start = startAfterSend(start, sends[next]);
      next++;
    }
    samples.push([t, start === null ? 0 : round(envelope(t - start))]);
  }
  return { name, sends, samples };
}

const steps = (from, to, step) => {
  const out = [];
  for (let t = from; t <= to + 1e-9; t += step) out.push(round(t));
  return out;
};

const pulseCases = [
  pulseCase('one send', [10], steps(9.9, 14.5, 0.05)),
  pulseCase('a second send while the first decays', [10, 11], steps(9.9, 16, 0.05)),
  pulseCase('a second send while the first rises', [10, 10.1], steps(9.9, 11, 0.025)),
  pulseCase('a send after the pulse ended', [10, 15], steps(14.8, 16, 0.05)),
  pulseCase('three quick sends', [10, 10.3, 10.6], steps(9.9, 12, 0.025)),
];

const fixtures = {
  comment: 'Generated by tools/wallpapers/motion-fixtures.mjs in gua-android; the same bytes are in gua-ios. Do not edit by hand.',
  tilt: { ...TILT, cases: tiltCases },
  interface: interfaceCases,
  still: { ...STILL, cases: stillCases },
  pulse: { ...PULSE, cases: pulseCases },
};

const out = process.argv[2];
if (!out) {
  console.error('usage: node motion-fixtures.mjs <output file>');
  process.exit(1);
}
// Objects and arrays holding only scalars or numeric pairs and triples stay on one line, so one event or sample is one line.
function format(value, indent = '') {
  const inline = (v) => v === null || typeof v !== 'object' || (Array.isArray(v) && v.every((x) => typeof x === 'number'));
  if (inline(value)) return JSON.stringify(value);
  const flat = Array.isArray(value) ? value.every(inline) : Object.values(value).every(inline);
  if (flat && JSON.stringify(value).length <= 100) return JSON.stringify(value).replace(/,(?=["{\[\d-])/g, ', ').replace(/":/g, '": ');
  const inner = indent + '  ';
  const items = Array.isArray(value)
    ? value.map((v) => inner + format(v, inner))
    : Object.entries(value).map(([k, v]) => `${inner}${JSON.stringify(k)}: ${format(v, inner)}`);
  return Array.isArray(value) ? `[\n${items.join(',\n')}\n${indent}]` : `{\n${items.join(',\n')}\n${indent}}`;
}

fs.writeFileSync(out, format(fixtures) + '\n');
