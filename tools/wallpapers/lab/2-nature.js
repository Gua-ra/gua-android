/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 *
 * Copied verbatim from the Gua Wallpaper Lab page, script 2 of 4 (the Nature section).
 * Do not edit here: change the Lab, then copy the whole script again.
 */

/*
 * Gua chat wallpapers: Nature section.
 * Original illustrations written from scratch for Gua. No third-party code or art.
 *
 * Each wallpaper is one place drawn twice: a day look for light appearance and a
 * dusk or night look of the same place for dark appearance.
 *
 * render(ctx, w, h, { dark }) paints the full wallpaper into a 2D context whose
 * drawable area is w x h (identity transform expected). Output is deterministic:
 * every random choice comes from seeded hashes, never Math.random.
 */
(function (root) {
  'use strict';
  var WP = (root.GUA_WALLPAPERS = root.GUA_WALLPAPERS || []);

  // ---------------------------------------------------------------- math

  function rng(seed) {
    var a = seed | 0;
    return function () {
      a = (a + 0x6d2b79f5) | 0;
      var t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function h1(i, s) {
    var h = (Math.imul(i | 0, 374761393) + Math.imul(s | 0, 668265263)) | 0;
    h = Math.imul(h ^ (h >>> 13), 1274126177);
    h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  }
  function h2(i, j, s) {
    var h = (Math.imul(i | 0, 374761393) + Math.imul(j | 0, 668265263) + Math.imul(s | 0, 1440662683)) | 0;
    h = Math.imul(h ^ (h >>> 13), 1274126177);
    h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  }
  function sm(t) { return t * t * (3 - 2 * t); }
  function lerp(a, b, t) { return a + (b - a) * t; }
  function clamp(x, a, b) { return x < a ? a : x > b ? b : x; }
  function sstep(a, b, x) { var t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); }
  function n1(x, s) { var i = Math.floor(x); return lerp(h1(i, s), h1(i + 1, s), sm(x - i)) * 2 - 1; }
  function f1(x, s, oct, gain) {
    gain = gain || 0.5;
    var sum = 0, amp = 1, norm = 0, fr = 1;
    for (var o = 0; o < oct; o++) { sum += amp * n1(x * fr + o * 17.31, s + o * 101); norm += amp; amp *= gain; fr *= 2.07; }
    return sum / norm;
  }
  function n2(x, y, s) {
    var i = Math.floor(x), j = Math.floor(y), fx = sm(x - i), fy = sm(y - j);
    var a = h2(i, j, s), b = h2(i + 1, j, s), c = h2(i, j + 1, s), d = h2(i + 1, j + 1, s);
    return (lerp(lerp(a, b, fx), lerp(c, d, fx), fy)) * 2 - 1;
  }
  function f2(x, y, s, oct, gain) {
    gain = gain || 0.5;
    var sum = 0, amp = 1, norm = 0;
    for (var o = 0; o < oct; o++) {
      sum += amp * n2(x, y, s + o * 37);
      norm += amp; amp *= gain;
      // rotate and scale between octaves so the value-noise grid does not line up
      var nx = x * 1.6 - y * 1.2 + 5.2, ny = x * 1.2 + y * 1.6 + 1.3;
      x = nx; y = ny;
    }
    return sum / norm;
  }
  // distance to nearest jittered cell point, 0 at a cell centre
  function worley(x, y, s) {
    var i = Math.floor(x), j = Math.floor(y), best = 9;
    for (var dj = -1; dj <= 1; dj++) for (var di = -1; di <= 1; di++) {
      var ci = i + di, cj = j + dj;
      var px = ci + 0.15 + 0.7 * h2(ci, cj, s), py = cj + 0.15 + 0.7 * h2(ci, cj, s + 9);
      var dx = px - x, dy = py - y, d = dx * dx + dy * dy;
      if (d < best) best = d;
    }
    return Math.sqrt(best);
  }
  // nearest jittered cell point: [distance, dx, dy, cell hash], dx dy point from the cell point
  var WV = [0, 0, 0, 0];
  function worleyV(x, y, s) {
    var i = Math.floor(x), j = Math.floor(y), best = 9;
    for (var dj = -1; dj <= 1; dj++) for (var di = -1; di <= 1; di++) {
      var ci = i + di, cj = j + dj;
      var px = ci + 0.15 + 0.7 * h2(ci, cj, s), py = cj + 0.15 + 0.7 * h2(ci, cj, s + 9);
      var dx = x - px, dy = y - py, d = dx * dx + dy * dy;
      if (d < best) { best = d; WV[1] = dx; WV[2] = dy; WV[3] = h2(ci, cj, s + 17); }
    }
    WV[0] = Math.sqrt(best);
    return WV;
  }

  // ---------------------------------------------------------------- colour

  var HC = {};
  function hx(c) {
    if (typeof c !== 'string') return c;
    var v = HC[c];
    if (v) return v;
    var s = c.charAt(0) === '#' ? c.slice(1) : c;
    v = [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16)];
    HC[c] = v;
    return v;
  }
  function mix(a, b, t) { a = hx(a); b = hx(b); return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]; }
  function css(c, a) {
    c = hx(c);
    return 'rgba(' + Math.round(c[0]) + ',' + Math.round(c[1]) + ',' + Math.round(c[2]) + ',' + (a == null ? 1 : Math.round(clamp(a, 0, 1) * 1000) / 1000) + ')';
  }
  function lin(ctx, x0, y0, x1, y1, stops) {
    var g = ctx.createLinearGradient(x0, y0, x1, y1);
    for (var i = 0; i < stops.length; i++) g.addColorStop(clamp(stops[i][0], 0, 1), css(stops[i][1], stops[i][2]));
    return g;
  }
  function vlin(ctx, y0, y1, stops) { return lin(ctx, 0, y0, 0, y1, stops); }
  function rad(ctx, x, y, r, stops) {
    var g = ctx.createRadialGradient(x, y, 0, x, y, r);
    for (var i = 0; i < stops.length; i++) g.addColorStop(clamp(stops[i][0], 0, 1), css(stops[i][1], stops[i][2]));
    return g;
  }
  // Gaussian-like falloff; plain two-stop radials read as hard discs.
  function glow(ctx, x, y, r, col, a, op) {
    ctx.save();
    if (op) ctx.globalCompositeOperation = op;
    ctx.fillStyle = rad(ctx, x, y, r, [[0, col, a], [0.1, col, a * 0.8], [0.25, col, a * 0.47], [0.45, col, a * 0.2], [0.68, col, a * 0.06], [1, col, 0]]);
    ctx.fillRect(x - r, y - r, 2 * r, 2 * r);
    ctx.restore();
  }
  // Elliptical glow: same falloff squashed vertically.
  function eglow(ctx, x, y, rx, ry, col, a, op) {
    ctx.save();
    ctx.translate(x, y);
    ctx.scale(1, ry / rx);
    glow(ctx, 0, 0, rx, col, a, op);
    ctx.restore();
  }

  // ---------------------------------------------------------------- canvas

  function mk(w, h) {
    w = Math.max(1, Math.round(w)); h = Math.max(1, Math.round(h));
    // a DOM canvas when there is a document (faster to composite in Chrome), else OffscreenCanvas
    if (typeof document !== 'undefined' && document.createElement) {
      var c = document.createElement('canvas');
      c.width = w; c.height = h;
      return c;
    }
    return new OffscreenCanvas(w, h);
  }
  // Low resolution pixel field, upscaled with smoothing. fn(u, v, out) gets
  // normalised coordinates and writes straight RGBA 0..255 into out.
  function field(w, h, cols, fn) {
    var cw = Math.max(2, Math.round(cols)), ch = Math.max(2, Math.round(cols * h / w));
    var c = mk(cw, ch), g = c.getContext('2d'), id = g.createImageData(cw, ch), d = id.data, out = [0, 0, 0, 0];
    for (var y = 0; y < ch; y++) {
      var v = y / (ch - 1);
      for (var x = 0; x < cw; x++) {
        fn(x / (cw - 1), v, out);
        var k = (y * cw + x) * 4;
        d[k] = out[0]; d[k + 1] = out[1]; d[k + 2] = out[2]; d[k + 3] = out[3];
      }
    }
    g.putImageData(id, 0, 0);
    return c;
  }
  // Height field with directional relief light. hf(u, v) -> height; cf(height, light, u, v, out) -> RGBA.
  function relief(w, h, cols, hf, cf) {
    var cw = Math.max(3, Math.round(cols)), ch = Math.max(3, Math.round(cols * h / w)), H = new Float32Array(cw * ch), x, y;
    for (y = 0; y < ch; y++) for (x = 0; x < cw; x++) H[y * cw + x] = hf(x / (cw - 1), y / (ch - 1));
    var c = mk(cw, ch), g = c.getContext('2d'), id = g.createImageData(cw, ch), d = id.data, out = [0, 0, 0, 0];
    for (y = 0; y < ch; y++) for (x = 0; x < cw; x++) {
      var x0 = x > 0 ? x - 1 : x, x1 = x < cw - 1 ? x + 1 : x, y0 = y > 0 ? y - 1 : y, y1 = y < ch - 1 ? y + 1 : y;
      var lt = (H[y0 * cw + x0] - H[y1 * cw + x1]) + 0.5 * (H[y * cw + x0] - H[y * cw + x1]);
      cf(H[y * cw + x], lt, x / (cw - 1), y / (ch - 1), out);
      var k = (y * cw + x) * 4;
      d[k] = out[0]; d[k + 1] = out[1]; d[k + 2] = out[2]; d[k + 3] = out[3];
    }
    g.putImageData(id, 0, 0);
    return c;
  }
  function blit(ctx, c, x, y, w, h, alpha, op) {
    ctx.save();
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'low';
    if (alpha != null) ctx.globalAlpha = alpha;
    if (op) ctx.globalCompositeOperation = op;
    // half-texel inset so the upscaled edges do not fade against transparent borders
    ctx.drawImage(c, 0.5, 0.5, c.width - 1, c.height - 1, x, y, w, h);
    ctx.restore();
  }

  var GRAIN = null;
  // One pass of fine film grain. Overlay keeps mid and light tones neutral; near-black
  // scenes need a plain blend instead, since overlay barely moves dark pixels.
  function grain(ctx, w, h, a, dark) {
    if (!GRAIN) {
      var N = 256, c = mk(N, N), g = c.getContext('2d'), id = g.createImageData(N, N), r = rng(9127);
      for (var k = 0; k < N * N * 4; k += 4) {
        var v = ((r() + r() + r()) / 3) * 255;
        id.data[k] = id.data[k + 1] = id.data[k + 2] = v;
        id.data[k + 3] = 255;
      }
      g.putImageData(id, 0, 0);
      GRAIN = c;
    }
    ctx.save();
    ctx.globalAlpha = dark ? a * 0.3 : a;
    ctx.globalCompositeOperation = dark ? 'source-over' : 'overlay';
    ctx.fillStyle = ctx.createPattern(GRAIN, 'repeat');
    ctx.fillRect(0, 0, w, h);
    ctx.restore();
  }

  // ---------------------------------------------------------------- shapes

  // Sample a skyline. fn(xs, xn) returns y in units of h, xs is x in units of S.
  function ridge(w, h, S, fn) {
    var n = Math.max(80, Math.ceil(w / 2.5)), ys = new Float32Array(n + 1), top = Infinity;
    for (var i = 0; i <= n; i++) {
      var y = fn((i / n) * w / S, i / n) * h;
      ys[i] = y;
      if (y < top) top = y;
    }
    return { n: n, y: ys, w: w, h: h, top: top };
  }
  function ridgePath(ctx, r, bottom) {
    var b = bottom == null ? r.h + 2 : bottom;
    ctx.beginPath();
    ctx.moveTo(-2, b);
    ctx.lineTo(-2, r.y[0]);
    for (var i = 0; i <= r.n; i++) ctx.lineTo((i / r.n) * r.w, r.y[i]);
    ctx.lineTo(r.w + 2, r.y[r.n]);
    ctx.lineTo(r.w + 2, b);
    ctx.closePath();
  }
  function ridgeAt(r, xn) {
    var f = clamp(xn, 0, 1) * r.n, i = Math.min(r.n - 1, Math.floor(f));
    return lerp(r.y[i], r.y[i + 1], f - i);
  }
  function fillRidge(ctx, r, style) { ridgePath(ctx, r); ctx.fillStyle = style; ctx.fill(); }

  // Slope light for soft landforms. Faces turned to the light brighten from the
  // skyline down; each run of faces turned away gets a lens-shaped shadow (zero
  // depth at the crest and at the trough) so lee faces read as faces, not stripes.
  // Painted per column into a small buffer, then upscaled under the skyline clip.
  function slopeShade(ctx, r, depth, lightCol, darkCol, kL, kD, gain, fromRight) {
    var n = r.n, dx = r.w / n, s = new Float32Array(n), t = new Float32Array(n), i, k;
    for (i = 0; i < n; i++) s[i] = ((r.y[i + 1] - r.y[i]) / dx) * (fromRight ? -1 : 1);
    for (i = 0; i < n; i++) {
      var acc = 0, cnt = 0;
      for (k = -2; k <= 2; k++) { var j = i + k; if (j >= 0 && j < n) { acc += s[j]; cnt++; } }
      t[i] = acc / cnt;
    }
    var dep = new Float32Array(n), st = -1;
    for (i = 0; i <= n; i++) {
      var sh = i < n && t[i] * gain > 0.015;
      if (sh && st < 0) st = i;
      if (!sh && st >= 0) {
        var drop = Math.abs(r.y[i] - r.y[st]), len = i - st;
        for (k = st; k < i; k++) {
          var pr = (k - st + 0.5) / len, pk = fromRight ? 1 - pr : pr;
          dep[k] = Math.min(depth * 3, depth * 0.3 + drop * 2.2) * Math.pow(Math.sin(Math.PI * Math.min(1, pk * 0.8 + 0.1)), 0.8);
        }
        st = -1;
      }
    }
    var top = r.top, bot = Math.min(r.h, top + depth * 3 + (r.h - top)), rs = 4;
    var ch = Math.max(2, Math.ceil((bot - top) / rs)), c = mk(n, ch), g = c.getContext('2d'), id = g.createImageData(n, ch), D = id.data;
    var LC = hx(lightCol), DC = hx(darkCol);
    for (i = 0; i < n; i++) {
      var v = clamp(t[i] * gain, -1, 1);
      if (Math.abs(v) < 0.015) continue;
      var dark = v > 0, a = dark ? v * kD : -v * kL, dd = dark ? dep[i] : depth, col = dark ? DC : LC;
      if (a <= 0 || dd <= 0) continue;
      var y0 = Math.min(r.y[i], r.y[i + 1]) - 1;
      var r0 = Math.max(0, Math.floor((y0 - top) / rs)), r1 = Math.min(ch - 1, Math.ceil((y0 + dd - top) / rs));
      for (var row = r0; row <= r1; row++) {
        var f = (top + row * rs - y0) / dd;
        if (f < 0) f = 0;
        if (f > 1) continue;
        // 1 at the skyline, about 0.4 halfway, 0 at the full depth
        var prof = f < 0.5 ? 1 - f * 1.2 : 0.8 * (1 - f);
        var q = (row * n + i) * 4;
        D[q] = col[0]; D[q + 1] = col[1]; D[q + 2] = col[2]; D[q + 3] = 255 * a * prof;
      }
    }
    g.putImageData(id, 0, 0);
    ctx.save();
    ridgePath(ctx, r);
    ctx.clip();
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = 'low';
    ctx.drawImage(c, 0, 0, n, ch, 0, top, r.w, ch * rs);
    ctx.restore();
  }

  // Shadowed flank to the right of each peak, bounded by a wandering spine.
  function facets(ctx, r, baseY, col, a, seed, minDrop, fromRight) {
    var n = r.n, y = r.y, dx = r.w / n, rr = rng(seed);
    ctx.save();
    ridgePath(ctx, r);
    ctx.clip();
    ctx.beginPath();
    var dir = fromRight ? -1 : 1;
    for (var i = 2; i < n - 1; i++) {
      if (!(y[i] <= y[i - 1] && y[i] < y[i + 1])) continue;
      if (fromRight) { if (!(y[i] < y[i - 1] && y[i] <= y[i + 1])) continue; }
      var j = i;
      if (!fromRight) { while (j < n && y[j + 1] >= y[j]) j++; } else { while (j > 0 && y[j - 1] >= y[j]) j--; }
      if (y[j] - y[i] < minDrop) continue;
      var span = baseY - y[i], drift = dir * (0.12 + rr() * 0.3) * span, vd = dir * (0.05 + rr() * 0.2) * span;
      ctx.moveTo(i * dx, y[i]);
      var k;
      if (!fromRight) for (k = i + 1; k <= j; k++) ctx.lineTo(k * dx, y[k]);
      else for (k = i - 1; k >= j; k--) ctx.lineTo(k * dx, y[k]);
      ctx.lineTo(j * dx + vd, baseY + 4);
      ctx.lineTo(i * dx + drift, baseY + 4);
      for (var q = 9; q > 0; q--) {
        var tq = q / 10;
        ctx.lineTo(lerp(i * dx, i * dx + drift, Math.pow(tq, 1.3)) + n1(tq * 4 + i * 0.37, seed) * span * 0.05, lerp(y[i], baseY, tq));
      }
      ctx.closePath();
    }
    // flanks fade out toward their foot so no hard-edged bands hang below the peaks
    ctx.fillStyle = vlin(ctx, r.top, baseY, [[0, col, a], [0.5, col, a * 0.8], [1, col, 0]]);
    ctx.fill();
    ctx.restore();
  }

  function stars(ctx, w, h, S, seed, count, y0, y1, col, alpha) {
    var r = rng(seed), u = S / 390;
    ctx.save();
    for (var i = 0; i < count; i++) {
      var x = r() * w, t = r(), y = (y0 + (y1 - y0) * t * t) * h;
      var big = r(), sz = (0.45 + Math.pow(big, 6) * 1.5) * u;
      var a = alpha * (0.25 + 0.75 * r()) * (1 - t * 0.75);
      ctx.fillStyle = css(col, a);
      ctx.beginPath();
      ctx.arc(x, y, sz, 0, Math.PI * 2);
      ctx.fill();
      if (big > 0.93) glow(ctx, x, y, sz * 7, col, a * 0.35);
    }
    ctx.restore();
  }

  // phase: 0 full disc, otherwise offset of the shadow disc in radii (crescent)
  function moon(ctx, x, y, r, col, shadowAlpha, phase, tiltDeg, glowCol, glowA) {
    glow(ctx, x, y, r * 9, glowCol || col, glowA == null ? 0.16 : glowA);
    glow(ctx, x, y, r * 2.6, glowCol || col, (glowA == null ? 0.16 : glowA) * 1.3);
    ctx.save();
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.clip();
    ctx.fillStyle = css(col, 1);
    ctx.fillRect(x - r, y - r, 2 * r, 2 * r);
    // soft mottling from fbm at about 8 percent contrast, with a gentle limb darkening
    var tex = field(2 * r, 2 * r, Math.max(16, Math.min(64, Math.round(r))), function (uu, vv, out) {
      var m = f2(uu * 3.2, vv * 3.2, 977, 4) + 0.5 * f2(uu * 1.3 + 4, vv * 1.3, 978, 2);
      var dd = Math.hypot(uu - 0.5, vv - 0.5) * 2;
      out[0] = 70; out[1] = 74; out[2] = 98;
      out[3] = 255 * (0.08 * sstep(-0.35, 0.7, m) + 0.07 * sstep(0.55, 1.0, dd));
    });
    blit(ctx, tex, x - r, y - r, 2 * r, 2 * r);
    if (phase) {
      var a = ((tiltDeg || 0) * Math.PI) / 180;
      ctx.globalCompositeOperation = 'destination-out';
      ctx.fillStyle = css('#000000', shadowAlpha == null ? 0.93 : shadowAlpha);
      ctx.beginPath();
      ctx.arc(x + Math.cos(a) * r * phase, y + Math.sin(a) * r * phase, r * 1.02, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();
  }

  function sun(ctx, x, y, r, core, halo, haloR, haloA) {
    glow(ctx, x, y, haloR, halo, haloA);
    glow(ctx, x, y, r * 2.2, core, 0.5);
    ctx.fillStyle = css(core, 1);
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  }

  // Gull-like silhouette: two crescent wings meeting at the body.
  function bird(ctx, x, y, s, flap, tilt) {
    var f = flap == null ? 1 : flap;
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(tilt || 0);
    ctx.beginPath();
    ctx.moveTo(-s, -0.32 * s * f);
    ctx.quadraticCurveTo(-0.45 * s, -0.52 * s * f, 0, 0.02 * s);
    ctx.quadraticCurveTo(0.45 * s, -0.52 * s * f, s, -0.3 * s * f);
    ctx.quadraticCurveTo(0.48 * s, -0.3 * s * f, 0, 0.16 * s);
    ctx.quadraticCurveTo(-0.48 * s, -0.3 * s * f, -s, -0.32 * s * f);
    ctx.fill();
    ctx.restore();
  }
  function flock(ctx, cx, cy, spread, s, count, seed) {
    var r = rng(seed);
    for (var i = 0; i < count; i++) {
      var k = i / Math.max(1, count - 1);
      bird(ctx, cx + (k - 0.5) * spread * 2 + (r() - 0.5) * spread * 0.6, cy + (r() - 0.5) * spread * 0.7 + Math.abs(k - 0.5) * spread * 0.4,
        s * (0.6 + 0.5 * r()), 0.6 + 0.7 * r(), (r() - 0.5) * 0.4);
    }
  }

  // Layered-tier conifer outline added to the current path.
  function sprucePath(ctx, x, yb, H, Wd, r) {
    var n = Math.round(clamp((H / Wd) * 2.1, 6, 22)), L = [], R = [], i;
    for (i = 0; i < n; i++) {
      var f = i / n, yo = yb - H * (0.07 + 0.86 * f), yi = yo - ((H * 0.86) / n) * 0.72, wo = Wd * Math.pow(1 - f, 0.85);
      L.push([x - wo * (0.8 + 0.35 * r()), yo + H * 0.012 * r()], [x - wo * 0.28, yi]);
      R.push([x + wo * (0.8 + 0.35 * r()), yo + H * 0.012 * r()], [x + wo * 0.28, yi]);
    }
    var tw = Math.max(Wd * 0.07, 0.6);
    ctx.moveTo(x - tw, yb);
    ctx.lineTo(x - tw, yb - H * 0.06);
    for (i = 0; i < L.length; i++) ctx.lineTo(L[i][0], L[i][1]);
    ctx.lineTo(x, yb - H);
    for (i = R.length - 1; i >= 0; i--) ctx.lineTo(R[i][0], R[i][1]);
    ctx.lineTo(x + tw, yb - H * 0.06);
    ctx.lineTo(x + tw, yb);
    ctx.closePath();
  }
  function treeline(ctx, r, x0n, x1n, minH, maxH, spacing, widthRatio, seed, sink) {
    var rr = rng(seed), x = x0n * r.w;
    while (x < x1n * r.w) {
      var H = lerp(minH, maxH, Math.pow(rr(), 1.4));
      var yb = ridgeAt(r, x / r.w) + (sink || H * 0.08);
      sprucePath(ctx, x, yb, H, H * widthRatio * (0.8 + 0.4 * rr()), rr);
      x += spacing * (0.35 + rr() * 0.9);
    }
  }

  // Strip of short horizontal glints around a vertical axis (sun or moon path).
  function glitter(ctx, cx, yTop, yBot, halfW, col, a, seed, u, rows, grow) {
    var r = rng(seed);
    ctx.save();
    for (var i = 0; i < rows; i++) {
      var t = (i + r()) / rows, tt = Math.pow(t, grow || 1.6), y = lerp(yTop, yBot, tt);
      var spread = halfW * (0.18 + 1.3 * tt), cnt = 1 + Math.floor(r() * 3);
      for (var j = 0; j < cnt; j++) {
        var g = (r() + r() + r() - 1.5) / 1.5, x = cx + g * spread;
        var len = (2 + tt * 18) * u * (0.4 + r()), th = (0.45 + tt * 1.5) * u;
        var aa = a * Math.pow(1 - Math.min(1, Math.abs(g)), 1.5) * (0.35 + 0.65 * r());
        ctx.fillStyle = css(col, aa);
        ctx.fillRect(x - len / 2, y, len, th);
      }
    }
    ctx.restore();
  }

  function fillAll(ctx, w, h, style) { ctx.fillStyle = style; ctx.fillRect(0, 0, w, h); }

  // ================================================================ scenes

  // ---------------------------------------------------------------- 1. Dune lagoons (aerial)
  // Height field on a grid; cf receives the gradient in height units per S, so the
  // lighting does not change with the grid resolution.
  function terrain(w, h, cols, hf, cf) {
    var S = Math.min(w, h), cw = Math.max(3, Math.round(cols)), ch = Math.max(3, Math.round(cols * h / w));
    var H = new Float32Array(cw * ch), x, y, ax = w / S, ay = h / S;
    for (y = 0; y < ch; y++) for (x = 0; x < cw; x++) H[y * cw + x] = hf((x / (cw - 1)) * ax, (y / (ch - 1)) * ay, x / (cw - 1), y / (ch - 1));
    var c = mk(cw, ch), g = c.getContext('2d'), id = g.createImageData(cw, ch), d = id.data, out = [0, 0, 0, 255];
    var sx = ax / (cw - 1), sy = ay / (ch - 1);
    for (y = 0; y < ch; y++) for (x = 0; x < cw; x++) {
      var x0 = x > 0 ? x - 1 : x, x1 = x < cw - 1 ? x + 1 : x, y0 = y > 0 ? y - 1 : y, y1 = y < ch - 1 ? y + 1 : y;
      var gx = (H[y * cw + x1] - H[y * cw + x0]) / ((x1 - x0) * sx), gy = (H[y1 * cw + x] - H[y0 * cw + x]) / ((y1 - y0) * sy);
      out[3] = 255;
      cf(H[y * cw + x], gx, gy, (x / (cw - 1)) * ax, (y / (ch - 1)) * ay, x / (cw - 1), y / (ch - 1), out);
      var k = (y * cw + x) * 4;
      d[k] = out[0]; d[k + 1] = out[1]; d[k + 2] = out[2]; d[k + 3] = out[3];
    }
    g.putImageData(id, 0, 0);
    return c;
  }
  // 1 across the chat area, easing to 0 above it and at the very bottom
  function bandMask(v) { return sstep(0.36, 0.5, v) * (1 - 0.5 * sstep(0.9, 1.0, v)); }

  function dunes(ctx, w, h, o) {
    var d = !!o.dark, S = Math.min(w, h);
    var P = d ? {
      crest: '#575C84', mid: '#3B3F5C', shade: '#262842', lagS: '#2A6478', lagD: '#123546', lagB: '#1C4E63', wet: '#2C3150', haze: '#3B4060'
    } : {
      crest: '#FFFDF8', mid: '#F1EADC', shade: '#DCCCB0', lagS: '#6FCFD0', lagD: '#2E9FB0', lagB: '#5FC6C9', wet: '#D8CDB6', haze: '#EEF0EA'
    };
    var A = 0.5, ca = Math.cos(A), sa = Math.sin(A), F = 3.4;
    // light from the upper left by day, from the upper right under the moon
    var L = d ? [0.55, -0.55, 0.62] : [-0.2, -0.85, 0.5], ll = Math.hypot(L[0], L[1], L[2]);
    L = [L[0] / ll, L[1] / ll, L[2] / ll];
    function wl(x, y) { return 0.02 + 0.2 * sstep(0.02, 0.5, f2(x * 2.3 + 3, y * 2.3, 611, 3)); }
    function H(x, y) {
      // the crest direction itself turns slowly across the field
      var A2 = A + 0.45 * f2(x * 0.45 + 9, y * 0.45, 600, 2), p = x * Math.cos(A2) + y * Math.sin(A2);
      var ph = p * F + 1.1 * f2(x * 0.8, y * 0.8, 601, 3) + 0.2 * f2(x * 3.2, y * 3.2, 602, 2);
      var k = Math.floor(ph), f = ph - k;
      // long windward slope, short steep slip face
      var pr = f < 0.85 ? Math.pow(sm(f / 0.85), 1.4) : 1 - sm((f - 0.85) / 0.15);
      var amp = 0.4 + 0.6 * (0.5 + 0.5 * f2(x * 1.6 + k * 0.7, y * 1.6, 603, 2));
      return pr * amp + 0.06 * f2(x * 3, y * 3, 605, 2) + 0.012 * f2(x * 10, y * 10, 604, 2);
    }
    var moonAt = null;
    if (d) {
      // the moon is reflected in the deepest pool near the top of the frame
      var best = -1;
      for (var yy = 0.14; yy <= 0.32; yy += 0.01) for (var xx = 0.2; xx <= 0.8; xx += 0.01) {
        var X = xx * w / S, Y = yy * h / S, dep = wl(X, Y) - H(X, Y);
        if (dep > best) { best = dep; moonAt = [X, Y]; }
      }
    }
    var cols = Math.min(460, Math.max(200, w / 1.3));
    var img = terrain(w, h, cols, H, function (hh, gx, gy, x, y, uu, vv, out) {
      var band = bandMask(vv), water = wl(x, y), dep = water - hh, c;
      var k = d ? 0.32 : 0.45, nx = -gx * k, ny = -gy * k, nl = Math.sqrt(nx * nx + ny * ny + 1);
      var lam = (nx * L[0] + ny * L[1] + L[2]) / nl;
      lam = lerp(lam, L[2], 0.45 * band);
      // troughs sit a little deeper in shade than the crests
      var s = sstep(0.05, 0.95, lam) * (0.72 + 0.28 * sstep(0.0, 0.7, hh));
      c = s < 0.5 ? mix(P.shade, P.mid, s * 2) : mix(P.mid, P.crest, s * 2 - 1);
      if (dep > -0.02) {
        // damp sand ring, then shallow turquoise deepening toward the middle of each pool
        var wet = sstep(-0.02, 0.0, dep);
        c = mix(c, P.wet, wet * 0.55);
        var wa = sstep(0.0, 0.007, dep);
        var lc = mix(P.lagS, P.lagD, sstep(0.004, 0.09, dep));
        lc = mix(lc, d ? '#3F7F8E' : '#B5E8E2', sstep(0.02, 0.0, dep) * 0.7);
        lc = mix(lc, P.lagB, 0.3);
        // sky sheen drifts across the open water
        lc = mix(lc, d ? '#3A7890' : '#C9F1EE', 0.22 * (0.5 + 0.5 * f2(x * 2.2, y * 5, 621, 2)));
        if (moonAt) {
          var mr = Math.sqrt((x - moonAt[0]) * (x - moonAt[0]) + (y - moonAt[1]) * (y - moonAt[1]));
          lc = mix(lc, '#7FA8C4', 0.6 * Math.exp(-mr * mr / 0.0035));
          lc = mix(lc, '#F6F2E2', sstep(0.03, 0.022, mr));
        }
        c = mix(c, lc, wa);
      }
      // calmer, paler middle where the conversation sits
      c = mix(c, P.haze, band * (d ? 0.22 : 0.28));
      out[0] = c[0]; out[1] = c[1]; out[2] = c[2];
    });
    blit(ctx, img, 0, 0, w, h);
    if (d) {
      // moonlight falls across the top of the field
      glow(ctx, 0.85 * w, -0.05 * h, 1.1 * S, '#8E9AD0', 0.16, 'screen');
    } else {
      glow(ctx, 0.1 * w, -0.05 * h, 1.2 * S, '#FFFDF6', 0.35);
    }
    grain(ctx, w, h, d ? 0.09 : 0.05, d);
  }

  
// ---------------------------------------------------------------- 2. Palm coast
  // Clean two-tone coconut palm: a tapered, gently curved trunk and fronds made of
  // filled, tapered leaflets hanging from each midrib. back/front are the two tones.
  function palmTree(ctx, bx, by, cx, cy, S, u, scale, seed, front, back, ring) {
    var r = rng(seed), i;
    var bend = (cx > bx ? -1 : 1) * Math.abs(cy - by) * 0.16;
    var mx = (bx + cx) / 2 + bend, my = (by + cy) / 2;
    var w0 = Math.max(6, 0.06 * S * scale), w1 = Math.max(3.5, 0.03 * S * scale), Ls = [], Rs = [], N = 40;
    for (i = 0; i <= N; i++) {
      var t = i / N, it = 1 - t;
      var px = it * it * bx + 2 * it * t * mx + t * t * cx, py = it * it * by + 2 * it * t * my + t * t * cy;
      var tx = 2 * it * (mx - bx) + 2 * t * (cx - mx), ty = 2 * it * (my - by) + 2 * t * (cy - my), tl = Math.hypot(tx, ty);
      var nx = -ty / tl, ny = tx / tl, wd = lerp(w0, w1, Math.pow(t, 0.6)) * 0.5 * (t < 0.06 ? 1 + (0.06 - t) * 4 : 1);
      Ls.push([px + nx * wd, py + ny * wd]);
      Rs.push([px - nx * wd, py - ny * wd]);
    }
    ctx.fillStyle = css(front);
    ctx.beginPath();
    ctx.moveTo(Ls[0][0], Ls[0][1]);
    for (i = 1; i <= N; i++) ctx.lineTo(Ls[i][0], Ls[i][1]);
    for (i = N; i >= 0; i--) ctx.lineTo(Rs[i][0], Rs[i][1]);
    ctx.closePath();
    ctx.fill();
    // faint leaf-scar rings, clipped to the trunk
    ctx.save();
    ctx.clip();
    ctx.strokeStyle = css(ring, 0.22);
    ctx.lineWidth = Math.max(0.8, 0.9 * u * scale);
    ctx.beginPath();
    for (i = 2; i < N; i += 2) { ctx.moveTo(Ls[i][0], Ls[i][1]); ctx.lineTo(Rs[i][0], Rs[i][1]); }
    ctx.stroke();
    ctx.restore();

    function frond(angDeg, lenK, col, droop) {
      var th = angDeg * Math.PI / 180, Lf = 0.3 * S * scale * lenK;
      var dx = Math.cos(th), dy = Math.sin(th), side = dx >= 0 ? 1 : -1;
      var c1x = cx + dx * Lf * 0.6, c1y = cy + dy * Lf * 0.6 - Lf * 0.12;
      // upright fronds arch over to their side instead of standing like spikes
      var ex = cx + dx * Lf * 0.85 + side * Lf * 0.3 * Math.max(0, -dy), ey = cy + dy * Lf * 0.85 + Lf * droop;
      ctx.fillStyle = css(col);
      ctx.strokeStyle = css(col);
      ctx.lineCap = 'round';
      ctx.lineWidth = Math.max(1.2, 2.2 * u * scale);
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.quadraticCurveTo(c1x, c1y, ex, ey);
      ctx.stroke();
      ctx.beginPath();
      var nl = 24;
      for (var k = 1; k <= nl; k++) {
        var t2 = 0.06 + 0.92 * (k / nl), i2 = 1 - t2;
        var px2 = i2 * i2 * cx + 2 * i2 * t2 * c1x + t2 * t2 * ex, py2 = i2 * i2 * cy + 2 * i2 * t2 * c1y + t2 * t2 * ey;
        var tx2 = 2 * i2 * (c1x - cx) + 2 * t2 * (ex - c1x), ty2 = 2 * i2 * (c1y - cy) + 2 * t2 * (ey - c1y), tl2 = Math.hypot(tx2, ty2);
        tx2 /= tl2; ty2 /= tl2;
        var len = Lf * 0.24 * Math.pow(Math.sin(Math.PI * (0.08 + 0.86 * t2)), 0.8) * (0.85 + 0.25 * r());
        var wb = Math.max(1.3, 0.011 * S * scale) * (1 - 0.5 * t2);
        for (var sd = -1; sd <= 1; sd += 2) {
          var ang = sd * (0.75 - 0.25 * t2);
          var lx = tx2 * Math.cos(ang) - ty2 * Math.sin(ang), ly = tx2 * Math.sin(ang) + ty2 * Math.cos(ang);
          ly += 0.9; // leaflets hang under their own weight
          var ll = Math.hypot(lx, ly);
          lx /= ll; ly /= ll;
          var qx = -ly, qy = lx;
          var tipx = px2 + lx * len, tipy = py2 + ly * len;
          ctx.moveTo(px2 + qx * wb * 0.5, py2 + qy * wb * 0.5);
          ctx.quadraticCurveTo(px2 + lx * len * 0.5 + qx * wb * 0.6, py2 + ly * len * 0.5 + qy * wb * 0.6 + len * 0.08, tipx, tipy);
          ctx.quadraticCurveTo(px2 + lx * len * 0.5 - qx * wb * 0.6, py2 + ly * len * 0.5 - qy * wb * 0.6 + len * 0.08, px2 - qx * wb * 0.5, py2 - qy * wb * 0.5);
          ctx.closePath();
        }
      }
      ctx.fill();
    }
    // back tone first, then the front fronds over it
    var backs = [[-138, 0.85, 0.38], [-44, 0.9, 0.36], [-104, 0.66, 0.3], [-160, 0.95, 0.55], [-18, 0.95, 0.52]];
    var fronts = [[-172, 1.0, 0.62], [-8, 1.0, 0.6], [160, 0.85, 0.75], [22, 0.82, 0.7], [-122, 0.72, 0.34], [-66, 0.75, 0.34], [120, 0.55, 0.6], [58, 0.55, 0.6]];
    for (i = 0; i < backs.length; i++) frond(backs[i][0] + (r() - 0.5) * 8, backs[i][1] * (0.9 + 0.2 * r()), back, backs[i][2]);
    for (i = 0; i < fronts.length; i++) frond(fronts[i][0] + (r() - 0.5) * 8, fronts[i][1] * (0.9 + 0.2 * r()), front, fronts[i][2]);
    ctx.fillStyle = css(front);
    for (i = 0; i < 4; i++) {
      ctx.beginPath();
      ctx.arc(cx + (i - 1.5) * 0.009 * S * scale, cy + 0.014 * S * scale + (i % 2) * 0.007 * S * scale, 0.008 * S * scale, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  function palms(ctx, w, h, o) {
    var d = !!o.dark, S = Math.min(w, h), u = S / 390, hz = 0.44, seaB = 0.88;
    var P = d ? {
      sky: [[0, '#141A36'], [0.45, '#1A2140'], [0.8, '#26315A'], [1, '#2D3A66']],
      sea0: '#2E4270', sea1: '#233457', sea2: '#1A2846', sand0: '#2A3152', sand1: '#323A5E', foam: '#AFC0EA',
      front: '#0A0F22', back: '#141C38', ring: '#3A4A78', glint: '#F2F1FF', streak: '#8EA2DA'
    } : {
      sky: [[0, '#6FAEDC'], [0.5, '#A3CFEC'], [0.9, '#D8ECF3'], [1, '#EAF4F2']],
      sea0: '#A9DCE0', sea1: '#88CBD0', sea2: '#A6D8D6', sand0: '#EFE3CD', sand1: '#F6EEDF', foam: '#FFFFFF',
      front: '#1F4A44', back: '#3A6E63', ring: '#6E9C8E', glint: '#FFFFFF', streak: '#FFFFFF'
    };
    fillAll(ctx, w, h, vlin(ctx, 0, hz * h, P.sky));
    var sx = 0.58 * w, sy = 0.2 * h;
    if (d) {
      stars(ctx, w, h, S, 21, Math.round(110 * (w * h) / (390 * 844)), 0, hz - 0.04, '#EEF0FF', 0.75);
      moon(ctx, sx, sy, 0.034 * S, '#F4F1E4', 0, 0, 0, '#AEBCEA', 0.18);
    } else {
      glow(ctx, sx, sy, 1.0 * S, '#FFFFFF', 0.45);
      sun(ctx, sx, sy, 0.032 * S, '#FFFEF6', '#FFF3D2', 0.28 * S, 0.65);
    }
    // long thin cloud streaks above the horizon
    var cr = rng(31);
    for (var i = 0; i < 6; i++) {
      eglow(ctx, cr() * w, (hz - 0.02 - cr() * 0.12) * h, (0.25 + cr() * 0.35) * S, (0.003 + cr() * 0.005) * h, d ? '#7F8DC0' : '#FFFFFF', d ? 0.25 : 0.6);
    }
    // sea, calm and pale toward the beach
    ctx.fillStyle = vlin(ctx, hz * h, seaB * h, [[0, P.sea0], [0.35, P.sea1], [1, P.sea2]]);
    ctx.fillRect(0, hz * h, w, (seaB - hz) * h + 2);
    ctx.fillStyle = css(d ? '#9FB0E0' : '#FFFFFF', d ? 0.3 : 0.6);
    ctx.fillRect(0, hz * h, w, Math.max(1, 0.8 * u));
    var sr = rng(41);
    for (i = 0; i < 30; i++) {
      var t = (i + 0.5) / 30, yy = lerp(hz, seaB, Math.pow(t, 1.5)) * h, xx = sr() * w;
      ctx.fillStyle = css(P.streak, (d ? 0.07 : 0.2) * (0.5 + sr()));
      ctx.fillRect(xx - (0.15 + sr() * 0.3) * w, yy, (0.2 + sr() * 0.45) * w, (0.5 + t * 1.1) * u);
    }
    // the sun or moon path, brightest near the horizon so the chat area stays quiet
    glitter(ctx, sx, hz * h + u, 0.7 * h, (d ? 0.06 : 0.05) * S, P.glint, d ? 0.7 : 0.75, 77, u, 70, 1.3);
    // shoreline
    var shore = ridge(w, h, S, function (xs) { return seaB + 0.006 * Math.sin(xs * 5.5 + 1) + 0.004 * f1(xs * 4, 81, 2); });
    fillRidge(ctx, shore, vlin(ctx, shore.top, h, [[0, P.sand0], [1, P.sand1]]));
    ctx.strokeStyle = css(P.foam, d ? 0.4 : 0.9);
    ctx.lineWidth = 1.4 * u;
    ctx.beginPath();
    for (i = 0; i <= shore.n; i++) { var x = (i / shore.n) * w, y = shore.y[i] + 0.6 * u; if (i) ctx.lineTo(x, y); else ctx.moveTo(x, y); }
    ctx.stroke();
    // palms frame the two edges; the middle stays open
    palmTree(ctx, -0.06 * w, 1.03 * h, 0.06 * w, 0.31 * h, S, u, 0.62, 13, mix(P.back, P.sky[2][1], 0.25), mix(P.back, P.sky[2][1], 0.4), P.ring);
    palmTree(ctx, 0.02 * w, 1.04 * h, 0.17 * w, 0.15 * h, S, u, 0.95, 7, P.front, P.back, P.ring);
    palmTree(ctx, 1.08 * w, 1.04 * h, 0.89 * w, 0.27 * h, S, u, 0.78, 19, P.front, P.back, P.ring);
    if (!d) {
      ctx.fillStyle = css('#4E6E7C', 0.6);
      flock(ctx, 0.44 * w, 0.29 * h, 0.035 * S, 0.011 * S, 3, 9);
    }
    grain(ctx, w, h, d ? 0.09 : 0.06, d);
  }

  
// ---------------------------------------------------------------- 3. River bends (aerial)
  function river(ctx, w, h, o) {
    var d = !!o.dark, S = Math.min(w, h), u = S / 390, ax = w / S, ay = h / S;
    var P = d ? {
      lo: '#04130F', mid: '#0E2C26', hi: '#1E4A3E', warm: '#2A4630', cool: '#0C2E3E',
      wEdge: '#1E3150', wMid: '#4E6796', wCen: '#9DB2DA', bank: '#1A2626', mist: '#8FA8C8', mistA: 0.22, veil: '#0A1A22', veilA: 0.12
    } : {
      lo: '#7FA779', mid: '#9DBF95', hi: '#C3DCB9', warm: '#BDD49E', cool: '#97C2A6',
      wEdge: '#9FC3CC', wMid: '#BFD9E0', wCen: '#CFE4E9', bank: '#7FA37C', sheen: '#E6F1F4', mist: '#FFFFFF', mistA: 0.32, veil: '#F1F6EE', veilA: 0.08
    };
    // meander centreline x(y) in pixels, and its slope
    function cxAt(y) { return (0.52 + 0.27 * Math.sin(y * 5.4 + 0.5) + 0.07 * Math.sin(y * 13.1 + 2.1) + 0.03 * f1(y * 3, 91, 2)) * w; }
    function slope(y) { return (cxAt(y + 0.002) - cxAt(y - 0.002)) / (0.004 * h); }
    var RW = 0.068 * S;
    // perpendicular distance to the centreline, in units of the river half-width
    function rdist(uu, vv) { var sl = slope(vv); return Math.abs(uu * w - cxAt(vv)) / Math.sqrt(1 + sl * sl) / (RW * 0.5); }

    var cols = d ? Math.min(300, Math.max(140, w / 3.5)) : Math.min(460, Math.max(260, w / 1.9));
    var canopy = relief(w, h, cols, function (uu, vv) {
      var x = uu * ax, y = vv * ay;
      var wx = x + 0.3 * f2(x * 1.4, y * 1.4, 11, 2), wy = y + 0.3 * f2(x * 1.4 + 4, y * 1.4, 12, 2);
      var land = f2(wx * 2.4, wy * 2.4, 17, 5, 0.52);
      // the floodplain sinks toward the river
      var rd = rdist(uu, vv);
      // individual tree crowns read as small rounded bumps in daylight
      if (d) return land * 0.9 - 0.35 * Math.exp(-rd * rd / 30) + f2(x * 22, y * 22, 19, 2) * 0.035;
      var crowns = 0;
      return land * 0.5 - 0.3 * Math.exp(-rd * rd / 30) + crowns;
    }, function (hgt, lt, uu, vv, out) {
      var x = uu * ax, y = vv * ay;
      var sh = clamp(0.52 + hgt * 0.5 + lt * (d ? 5.5 : 7), 0, 1);
      var hue = clamp(0.5 + f2(x * 1.1 + 9, y * 1.1, 23, 2) * 1.1, 0, 1);
      var c = mix(mix(P.lo, P.hi, sh), mix(P.cool, P.warm, hue), 0.22);
      if (!d) {
        // tree crowns as small lit spheres: bright on the sun side, dark gaps between
        var qx = x + 0.025 * n2(x * 9, y * 9, 81), qy = y + 0.025 * n2(x * 9 + 5, y * 9, 82);
        var cv = worleyV(qx * 24, qy * 24, 18), rr = cv[0] / (0.5 + 0.25 * cv[3]);
        var dome = Math.max(0, 1 - rr * rr), lit = clamp(0.55 - 0.9 * (cv[1] * 0.7 + cv[2] * 0.7), 0, 1);
        var tone = lerp(0.42, 1, Math.sqrt(dome)) * lerp(0.6, 1.12, lit * Math.sqrt(dome) + 0.5 * (1 - Math.sqrt(dome)));
        c = mix(c, mix(P.lo, P.hi, clamp(tone, 0, 1)), 0.32);
        c = mix(c, P.warm, 0.25 * sstep(0.75, 1, cv[3]) * dome);
      }
      // canopy edge shadow along the banks
      var rd = rdist(uu, vv), bs = Math.exp(-Math.pow(Math.max(0, rd - 1) / 0.9, 2));
      c = mix(c, P.lo, bs * (d ? 0.6 : 0.4));
      out[0] = c[0]; out[1] = c[1]; out[2] = c[2]; out[3] = 255;
    });
    blit(ctx, canopy, 0, 0, w, h);

    // river outline as a polygon so the water can be shaded across its width
    var N = 260, L = [], R = [], i;
    function outline(half) {
      L = []; R = [];
      for (i = 0; i <= N; i++) {
        var yy = -0.04 + (i / N) * 1.08, sl = slope(yy), nl = Math.sqrt(1 + sl * sl), x = cxAt(yy), y = yy * h;
        var hw = half * (1 + 0.12 * Math.sin(yy * 9.0 + 1.3));
        L.push([x - hw / nl, y + hw * sl / nl]);
        R.push([x + hw / nl, y - hw * sl / nl]);
      }
      ctx.beginPath();
      ctx.moveTo(L[0][0], L[0][1]);
      for (i = 1; i <= N; i++) ctx.lineTo(L[i][0], L[i][1]);
      for (i = N; i >= 0; i--) ctx.lineTo(R[i][0], R[i][1]);
      ctx.closePath();
    }
    outline(RW * 0.62);
    ctx.fillStyle = css(P.bank, d ? 0.9 : 1);
    ctx.fill();
    var water = field(w, h, 200, function (uu, vv, out) {
      var rd = rdist(uu, vv), t = clamp(1 - rd, 0, 1);
      // flat water with a thin shallow rim; the sky reflection varies slowly along the flow
      var fl = 0.5 + 0.5 * f2(uu * ax * 2.2, vv * ay * 3.5, 41, 3);
      var c = mix(P.wMid, P.wCen, sstep(0.2, 0.85, fl));
      c = mix(P.wEdge, c, sstep(0.0, 0.22, t));
      if (!d) {
        // pale sky sheen along the west bank
        var side = uu * w - cxAt(vv);
        if (side < 0) c = mix(c, P.sheen, 0.75 * sstep(0.25, 0.9, rd) * sstep(1.05, 0.95, rd));
      }
      out[0] = c[0]; out[1] = c[1]; out[2] = c[2]; out[3] = 255;
    });
    ctx.save();
    outline(RW * 0.5);
    ctx.clip();
    blit(ctx, water, 0, 0, w, h);
    ctx.restore();

    // morning mist rolling over the canopy
    var mist = field(w, h, 110, function (uu, vv, out) {
      var x = uu * ax, y = vv * ay;
      var m = f2(x * 1.6 + 0.3, y * 4.2, 61, 4);
      var a = sstep(-0.05, 0.45, m) * (0.55 + 0.45 * Math.sin(vv * 7 + 1) * Math.sin(vv * 7 + 1));
      if (!d) {
        // in daylight the mist lies only over one bend of the river
        var my = 0.27, mxp = cxAt(my) / w, ddm = Math.hypot((uu - mxp) * ax / 0.42, (vv - my) * ay / 0.2);
        a = sstep(1.0, 0.25, ddm + 0.25 * f2(x * 2.5, y * 2.5, 62, 3)) * 1.0;
      }
      var c = hx(P.mist);
      out[0] = c[0]; out[1] = c[1]; out[2] = c[2]; out[3] = 255 * clamp(a * P.mistA, 0, 1);
    });
    blit(ctx, mist, 0, 0, w, h);
    fillAll(ctx, w, h, css(P.veil, P.veilA));
    if (d) {
      glow(ctx, 0.72 * w, 0.2 * h, 0.9 * S, '#9FB8E8', 0.12, 'screen');
    } else {
      glow(ctx, 0.2 * w, 0.05 * h, 1.1 * S, '#FFF7E4', 0.5);
      ctx.fillStyle = css('#3F5E52', 0.55);
      flock(ctx, 0.66 * w, 0.84 * h, 0.035 * S, 0.012 * S, 3, 3);
    }
    ctx.fillStyle = rad(ctx, w / 2, h * 0.5, Math.max(w, h) * 0.75, [[0.55, P.lo, 0], [1, P.lo, d ? 0.45 : 0.2]]);
    ctx.fillRect(0, 0, w, h);
    grain(ctx, w, h, d ? 0.09 : 0.06, d);
  }

  // ---------------------------------------------------------------- 4. Highland mist (araucaria)
  function araucaria(ctx, x, yb, H, seed) {
    var r = rng(seed), tw = Math.max(0.8, H * 0.03), crownY = yb - H * 0.7;
    ctx.moveTo(x - tw, yb);
    ctx.lineTo(x - tw * 0.7, crownY);
    ctx.lineTo(x + tw * 0.7, crownY);
    ctx.lineTo(x + tw, yb);
    ctx.closePath();
    var span = H * (0.34 + 0.1 * r()), nb = 7;
    for (var b = 0; b < nb; b++) {
      var k = (b / (nb - 1)) * 2 - 1, ex = x + k * span * (0.9 + 0.15 * r()), ey = yb - H * (0.93 + 0.04 * Math.abs(k) + 0.03 * r());
      var sy = crownY + H * 0.04 * (1 - Math.abs(k)) - H * 0.02, bw = tw * 0.5;
      // branch arm curving up
      ctx.moveTo(x - bw, sy + bw);
      ctx.quadraticCurveTo(x + k * span * 0.7, sy + H * 0.02, ex - bw, ey + H * 0.02);
      ctx.lineTo(ex + bw, ey + H * 0.02);
      ctx.quadraticCurveTo(x + k * span * 0.7 + bw * 2, sy + H * 0.02 - bw, x + bw, sy);
      ctx.closePath();
      // tufted pad at the tip
      var pr = span * (0.24 + 0.06 * r()), pyr = H * 0.055;
      ctx.moveTo(ex + pr, ey);
      ctx.ellipse(ex, ey, pr, pyr, 0, 0, Math.PI * 2);
      ctx.moveTo(ex + pr * 0.7, ey - pyr * 0.6);
      ctx.ellipse(ex, ey - pyr * 0.6, pr * 0.7, pyr * 0.8, 0, 0, Math.PI * 2);
    }
  }
  function roundTree(ctx, x, yb, H, r) {
    var R = H * 0.42;
    ctx.moveTo(x + R, yb - H + R);
    ctx.arc(x, yb - H + R, R, 0, Math.PI * 2);
    ctx.moveTo(x + R * 0.8 + R * 0.5, yb - H + R * 1.5);
    ctx.arc(x + R * 0.5, yb - H + R * 1.5, R * 0.8, 0, Math.PI * 2);
    ctx.moveTo(x - R * 0.5 + R * 0.75, yb - H + R * 1.55);
    ctx.arc(x - R * 0.5, yb - H + R * 1.55, R * 0.75, 0, Math.PI * 2);
    ctx.rect(x - H * 0.03, yb - H + R * 1.5, H * 0.06, H - R * 1.5);
  }
  function highlands(ctx, w, h, o) {
    var d = !!o.dark, S = Math.min(w, h), u = S / 390;
    var P = d ? {
      sky: [[0, '#0A1430'], [0.4, '#16284A'], [0.8, '#2C4468'], [1, '#3C5578']],
      layers: ['#435678', '#36486A', '#2A3A57', '#1F2D45'], tree: '#141E33', mist: '#6F87A8', low: '#2A3A57'
    } : {
      sky: [[0, '#8EBCD8'], [0.45, '#BBD7E2'], [0.85, '#E4EDE6'], [1, '#EEF2EA']],
      layers: ['#DCE6D8', '#C9D7C3', '#B4C8AE', '#A2B99C'], tree: '#7F9C7C', mist: '#F7F9F3', low: '#CBD8C6'
    };
    fillAll(ctx, w, h, vlin(ctx, 0, 0.5 * h, P.sky));
    if (d) {
      stars(ctx, w, h, S, 41, Math.round(130 * (w * h) / (390 * 844)), 0, 0.34, '#E8F0FF', 0.75);
      moon(ctx, 0.7 * w, 0.13 * h, 0.036 * S, '#F3F1E6', 0, 0, 0, '#B9CCF0', 0.16);
    } else {
      glow(ctx, 0.68 * w, 0.17 * h, 1.0 * S, '#FFF8E6', 0.6);
      sun(ctx, 0.68 * w, 0.17 * h, 0.032 * S, '#FFFDF6', '#FFF3D6', 0.22 * S, 0.6);
    }
    // four receding ridges between 30 and 55 percent of the height
    var L = [{ y: 0.37, a: 0.1, f: 3.0 }, { y: 0.42, a: 0.09, f: 3.8 }, { y: 0.475, a: 0.08, f: 3.2 }, { y: 0.54, a: 0.07, f: 2.4 }];
    for (var k = 0; k < L.length; k++) {
      var l = L[k], c = P.layers[k], near = k === L.length - 1;
      var r = ridge(w, h, S, (function (l, k) {
        return function (xs) {
          var v = 0.5 + 0.5 * f1(xs * l.f + k * 3.1, 200 + k, 4, 0.42);
          return l.y - sm(clamp(v, 0, 1)) * l.a - Math.abs(n1(xs * 60, 300 + k)) * 0.002;
        };
      })(l, k));
      if (k >= 1) {
        // smaller trees ride the middle ridges in the layer colour
        ctx.fillStyle = css(near ? P.tree : mix(c, P.tree, 0.25));
        ctx.beginPath();
        var rr = rng(500 + k), scaleH = (near ? 0.085 : 0.018 + 0.012 * k) * h, x = rr() * 0.04 * w;
        while (x < w) {
          var xn = x / w, keep = near ? (rr() < 0.8 && (xn < 0.34 || xn > 0.6 || rr() < 0.25)) : rr() < 0.6;
          var H = scaleH * (0.55 + 0.6 * rr()), yb = ridgeAt(r, xn) + H * 0.06;
          if (keep) {
            if (near || rr() < 0.7) araucaria(ctx, x, yb, H, 900 + k * 100 + Math.floor(x));
            else roundTree(ctx, x, yb, H * 0.45, rr);
          }
          x += scaleH * (near ? 0.55 + rr() * 0.9 : 0.5 + rr() * 1.2);
        }
        ctx.fill();
      }
      fillRidge(ctx, r, near ? vlin(ctx, r.top, 0.72 * h, [[0, c], [0.35, mix(c, P.mist, 0.35)], [1, mix(c, P.mist, 0.8)]])
        : vlin(ctx, r.top, (l.y + 0.06) * h, [[0, c], [1, mix(c, P.mist, 0.45)]]));
      // valley mist pools at the foot of each ridge
      var my = l.y + 0.02;
      ctx.fillStyle = vlin(ctx, (my - 0.04) * h, (my + 0.02) * h, [[0, P.mist, 0], [0.8, P.mist, d ? 0.3 : 0.42], [1, P.mist, d ? 0.2 : 0.3]]);
      ctx.fillRect(0, (my - 0.04) * h, w, 0.06 * h);
    }
    // below the ridges: mist with two faint hill shapes, low enough in contrast for the chat
    var lows = [0.72, 0.9];
    for (var j = 0; j < lows.length; j++) {
      var y0 = lows[j];
      var rl = ridge(w, h, S, function (xs) { return y0 - 0.04 * (0.5 + 0.5 * f1(xs * 0.8 + j * 5, 260 + j, 3)); });
      fillRidge(ctx, rl, vlin(ctx, rl.top, (y0 + 0.08) * h, [[0, P.low, d ? 0.45 : 0.4], [1, P.low, 0]]));
    }
    var mist = field(w, h, 90, function (uu, vv, out) {
      var m = f2(uu * 1.4 * w / S, vv * 5 * h / S, 271, 4), a = sstep(0.5, 0.62, vv) * (0.45 + 0.4 * sstep(-0.3, 0.5, m));
      var cc = hx(P.mist);
      out[0] = cc[0]; out[1] = cc[1]; out[2] = cc[2]; out[3] = 255 * a * (d ? 0.55 : 0.8);
    });
    blit(ctx, mist, 0, 0, w, h);
    if (!d) {
      ctx.fillStyle = css('#5F7C74', 0.6);
      flock(ctx, 0.36 * w, 0.2 * h, 0.04 * S, 0.011 * S, 3, 21);
    }
    grain(ctx, w, h, d ? 0.09 : 0.06, d);
  }

  
// ---------------------------------------------------------------- 5. Glacier lake
  function lakeUpper(ctx, w, h, S, d, P) {
    fillAll(ctx, w, h, vlin(ctx, 0, 0.5 * h, P.sky));
    if (d) {
      stars(ctx, w, h, S, 51, Math.round(170 * (w * h) / (390 * 844)), 0, 0.4, '#EEF2FF', 0.9);
      moon(ctx, 0.24 * w, 0.1 * h, 0.022 * S, '#F4F0E2', 0.95, 0.58, -20, '#C8CCF0', 0.12);
    } else {
      glow(ctx, 0.85 * w, 0.02 * h, 0.9 * S, '#FFFFFF', 0.35);
    }
    // far range: ridged noise peaks with snow and shaded flanks
    var far = ridge(w, h, S, function (xs, xn) {
      // ridged multifractal: sharp crests at several scales
      var rd = 0, amp = 1, fr = 3.1, nrm = 0;
      for (var o = 0; o < 5; o++) {
        var q = 1 - Math.abs(n1(xs * fr + o * 7.3, 11 + o));
        rd += q * q * amp; nrm += amp; amp *= 0.5; fr *= 2.1;
      }
      rd /= nrm;
      var env = 0.42 + 0.58 * Math.exp(-Math.pow((xn - 0.64) / 0.34, 2)) + 0.3 * Math.exp(-Math.pow((xn - 0.06) / 0.2, 2));
      return 0.5 - 0.27 * env * (0.3 + 0.7 * rd) - 0.025;
    });
    fillRidge(ctx, far, vlin(ctx, far.top, 0.52 * h, [[0, P.rock], [1, mix(P.rock, P.haze, d ? 0.25 : 0.6)]]));
    ctx.save();
    ridgePath(ctx, far);
    ctx.clip();
    ctx.beginPath();
    ctx.moveTo(-2, -2);
    ctx.lineTo(w + 2, -2);
    var n = 200;
    for (var i = n; i >= 0; i--) {
      var xn = i / n, xs = (xn * w) / S;
      var ry = ridgeAt(far, xn) / h, band = 0.07 + 0.04 * f1(xs * 4, 78, 3) + 0.015 * f1(xs * 18, 77, 2);
      var sl = Math.min(ry + band, 0.4 + 0.012 * f1(xs * 9, 76, 2));
      ctx.lineTo(xn * w, sl * h);
    }
    ctx.closePath();
    ctx.fillStyle = vlin(ctx, far.top, 0.42 * h, [[0, P.snow], [1, mix(P.snow, P.rock, 0.25)]]);
    ctx.fill();
    ctx.restore();
    facets(ctx, far, 0.44 * h, P.shadow, d ? 0.5 : 0.32, 12, 0.025 * h, false);
    // atmospheric veil at the foot of the range
    ctx.fillStyle = vlin(ctx, 0.36 * h, 0.5 * h, [[0, P.haze, 0], [1, P.haze, d ? 0.45 : 0.7]]);
    ctx.fillRect(0, 0.36 * h, w, 0.14 * h);
    // forested foothills
    var mid = ridge(w, h, S, function (xs, xn) {
      return 0.515 - 0.05 * (0.5 + 0.5 * f1(xs * 1.8 + 7, 31, 3)) - 0.035 * Math.exp(-Math.pow((xn - 0.05) / 0.2, 2));
    });
    var mc = mix(P.forest, P.haze, d ? 0.3 : 0.38);
    ctx.fillStyle = css(mc);
    ctx.beginPath();
    treeline(ctx, mid, -0.02, 1.02, 0.008 * h, 0.016 * h, 0.01 * w, 0.28, 33);
    ctx.fill();
    fillRidge(ctx, mid, vlin(ctx, mid.top, 0.56 * h, [[0, mc], [1, mix(mc, P.haze, 0.3)]]));
    // shoreline spruce
    var shore = ridge(w, h, S, function (xs) { return 0.556 + 0.002 * n1(xs * 6, 41); });
    ctx.fillStyle = css(P.forest);
    ctx.beginPath();
    treeline(ctx, shore, -0.02, 1.02, 0.01 * h, 0.026 * h, 0.0117 * w, 0.26, 44);
    ctx.fill();
    fillRidge(ctx, shore, css(P.forest));
  }
  function lake(ctx, w, h, o) {
    var d = !!o.dark, S = Math.min(w, h), u = S / 390, wl = 0.556;
    var P = d ? {
      sky: [[0, '#080C24'], [0.45, '#161E48'], [0.8, '#33345F'], [1, '#504670']],
      rock: '#1C2242', snow: '#C3C5E4', shadow: '#0A0D24', haze: '#363762', forest: '#060A16',
      water0: '#1B4A57', water1: '#0B2A33', ripple: '#9FD2D6'
    } : {
      sky: [[0, '#6AA6D9'], [0.45, '#A4CCEA'], [0.85, '#DCECF4'], [1, '#EDF4F4']],
      rock: '#5C6F88', snow: '#FFFFFF', shadow: '#33476A', haze: '#DCE8EE', forest: '#1E3B3A',
      water0: '#8FDCD6', water1: '#4FB7BA', ripple: '#FFFFFF'
    };
    lakeUpper(ctx, w, h, S, d, P);
    // reflection: re-render the upper scene small, mirror it, let it blur by upscaling
    var sc = 0.3, rw = Math.max(8, Math.round(w * sc)), rh = Math.max(8, Math.round(wl * h * sc));
    var rc = mk(rw, rh), rg = rc.getContext('2d');
    rg.scale(rw / w, rh / (wl * h));
    lakeUpper(rg, w, h, S, d, P);
    ctx.save();
    ctx.beginPath();
    ctx.rect(0, wl * h, w, h - wl * h);
    ctx.clip();
    // water tint deepens toward the viewer
    ctx.fillStyle = vlin(ctx, wl * h, h, [[0, P.water0], [0.3, P.water0], [1, P.water1]]);
    ctx.fillRect(0, wl * h, w, h - wl * h);
    // a faint mirror image of the shore and the peaks
    ctx.translate(0, 2 * wl * h);
    ctx.scale(1, -1);
    blit(ctx, rc, 0, 0, w, wl * h, 0.15);
    ctx.restore();
    ctx.fillStyle = vlin(ctx, wl * h, (wl + 0.05) * h, [[0, P.forest, d ? 0.3 : 0.22], [1, P.forest, 0]]);
    ctx.fillRect(0, wl * h, w, 0.05 * h);
    // ripples
    var r = rng(61);
    for (var i = 0; i < 46; i++) {
      var t = (i + r()) / 46, y = lerp(wl + 0.004, 1, Math.pow(t, 1.7)) * h, x = r() * w, len = (0.08 + r() * 0.35) * w;
      ctx.fillStyle = css(P.ripple, (d ? 0.07 : 0.22) * (0.4 + r()));
      ctx.fillRect(x - len / 2, y, len, (0.5 + t * 1.3) * u);
    }
    ctx.fillStyle = css(P.ripple, d ? 0.18 : 0.5);
    ctx.fillRect(0, wl * h, w, Math.max(1, 0.7 * u));
    if (d) {
      // a few reflected stars
      stars(ctx, w, h, S, 52, Math.round(40 * (w * h) / (390 * 844)), 0.6, 0.95, '#DDE8FF', 0.35);
    }
    grain(ctx, w, h, d ? 0.09 : 0.06, d);
  }

  // ---------------------------------------------------------------- 7. Canola sky
  // Soft clouds from fbm. cum: cumulus { x, y: flat base, wid, hgt } in fractions of
  // w and h; str: stratus wisps { y, x0, x1, th, a }. P: { top, under, rimLow }.
  // rimLow lights the undersides (dusk) instead of the tops (day).
  function softClouds(ctx, w, h, S, cum, str, P, seed, alpha) {
    var ax = w / S, ay = h / S, top = hx(P.top), und = hx(P.under);
    var c = field(w, h, Math.min(220, Math.max(150, w / 2.2)), function (uu, vv, out) {
      var x = uu * ax, y = vv * ay, A = 0, shade = 0, wsum = 0;
      for (var i = 0; i < cum.length; i++) {
        var C = cum[i], dx = (uu - C.x) * ax / C.wid, dy = (C.y - vv) * ay / C.hgt;
        if (dy < -0.2 || dy > 1.6 || Math.abs(dx) > 1.4) continue;
        // a dome that bulges more on top, with a flat, slightly ragged base
        var e = 1 - dx * dx - Math.pow(Math.max(0, dy), 1.6) * 0.9;
        var n = f2(x * 7 + i * 3, y * 7, seed + i, 4);
        var dns = e * 1.1 + 0.75 * n - 0.15;
        dns *= sstep(-0.12, 0.08, dy + 0.06 * n);
        var a = sstep(0.0, 0.35, dns);
        if (a <= 0) continue;
        // brighter toward the top, with puffs self-shading on their lower sides
        var lift = clamp(dy * 0.6 + 0.35 + 0.8 * (n - f2(x * 7 + i * 3, y * 7 + 0.04, seed + i, 4)), 0, 1);
        if (P.rimLow) lift = 1 - clamp(dy * 0.8 - 0.1, 0, 1);
        shade += lift * a; wsum += a;
        A = Math.max(A, a);
      }
      for (var j = 0; j < str.length; j++) {
        var T = str[j], band = Math.exp(-Math.pow((vv - T.y) / T.th, 2));
        if (band < 0.01) continue;
        var span = sstep(T.x0 - 0.15, T.x0 + 0.1, uu) * sstep(T.x1 + 0.15, T.x1 - 0.1, uu);
        var m = sstep(-0.1, 0.6, f2(x * 1.6 + j * 7, vv * 40, seed + 50 + j, 4)) * band * span * T.a;
        if (m > A) { shade = shade * (A / m) + (P.rimLow ? 0.8 : 0.75) * (1 - A / m); wsum = 1; A = m; }
      }
      if (A <= 0) { out[3] = 0; return; }
      var t = wsum > 0 ? shade / wsum : 0.8, col = mix(und, top, t);
      out[0] = col[0]; out[1] = col[1]; out[2] = col[2]; out[3] = 255 * A * alpha;
    });
    blit(ctx, c, 0, 0, w, h);
  }

  function canola(ctx, w, h, o) {
    var d = !!o.dark, S = Math.min(w, h), u = S / 390, hz = 0.45;
    var P = d ? {
      sky: [[0, '#10153A'], [0.4, '#262C60'], [0.7, '#4E3F76'], [0.9, '#965A80'], [1, '#C98479']],
      cTop: '#3C3A6C', cUnder: '#F0A890', far: '#8E6272', field0: '#54405E', field1: '#1E1A36', row: '#0A0814', belt: '#24203A'
    } : {
      sky: [[0, '#7FB2E5'], [0.55, '#A9CCEE'], [1, '#CFE3F3']],
      cTop: '#FFFFFF', cUnder: '#C9D6E6', far: '#DCD8A6', field0: '#E9D57A', field1: '#D9C060', row: '#A08A30', belt: '#7C958E'
    };
    fillAll(ctx, w, h, vlin(ctx, 0, hz * h, P.sky));
    if (d) {
      stars(ctx, w, h, S, 101, Math.round(90 * (w * h) / (390 * 844)), 0, 0.3, '#F4F0FF', 0.75);
      moon(ctx, 0.24 * w, 0.1 * h, 0.026 * S, '#F6F0DC', 0.95, 0.6, -150, '#D0C0F0', 0.12);
      eglow(ctx, 0.62 * w, hz * h, 0.9 * w, 0.14 * h, '#FFB08A', 0.35);
    } else {
      glow(ctx, 0.78 * w, 0.06 * h, 1.0 * S, '#FFFFFF', 0.35);
    }
    softClouds(ctx, w, h, S, [
      { x: 0.26, y: 0.355, wid: 0.36, hgt: 0.16 },
      { x: 0.8, y: 0.3, wid: 0.24, hgt: 0.1 },
      { x: 0.62, y: 0.425, wid: 0.3, hgt: 0.07 }
    ], [
      { y: 0.13, x0: 0.05, x1: 0.75, th: 0.012, a: 0.75 },
      { y: 0.18, x0: 0.4, x1: 1.0, th: 0.009, a: 0.6 },
      { y: 0.235, x0: -0.1, x1: 0.45, th: 0.008, a: 0.5 }
    ], d ? { top: P.cTop, under: P.cUnder, rimLow: true } : { top: P.cTop, under: P.cUnder }, 131, d ? 0.9 : 0.95);
    // haze at the horizon
    var hzC = P.sky[P.sky.length - 1][1];
    ctx.fillStyle = vlin(ctx, (hz - 0.05) * h, hz * h, [[0, hzC, 0], [1, hzC, 0.8]]);
    ctx.fillRect(0, (hz - 0.05) * h, w, 0.05 * h);
    // shelterbelts and a far grain elevator on the horizon
    var belt = ridge(w, h, S, function (xs, xn) {
      var b = Math.max(0, f1(xs * 3, 111, 2)) * 0.01 * (xn > 0.55 && xn < 0.75 ? 1 : 0.45);
      return hz + 0.001 - b;
    });
    var beltC = mix(P.belt, hzC, d ? 0.15 : 0.4);
    fillRidge(ctx, belt, css(beltC));
    // a prairie grain elevator: tall shed with a cupola and a low annex
    var ex = 0.2 * w, ew = 0.022 * S, eh = 0.042 * h, gy = hz * h + 1;
    ctx.fillStyle = css(beltC);
    ctx.beginPath();
    ctx.moveTo(ex - ew / 2, gy);
    ctx.lineTo(ex - ew / 2, gy - eh * 0.82);
    ctx.lineTo(ex, gy - eh * 0.92);
    ctx.lineTo(ex + ew / 2, gy - eh * 0.82);
    ctx.lineTo(ex + ew / 2, gy);
    ctx.closePath();
    ctx.rect(ex - ew * 0.22, gy - eh, ew * 0.44, eh * 0.2);
    ctx.moveTo(ex - ew * 0.3, gy - eh * 1.06);
    ctx.lineTo(ex, gy - eh * 1.14);
    ctx.lineTo(ex + ew * 0.3, gy - eh * 1.06);
    ctx.closePath();
    ctx.rect(ex + ew / 2, gy - eh * 0.36, ew * 0.9, eh * 0.36);
    ctx.fill();
    // field
    var fy = hz + 0.002;
    ctx.fillStyle = vlin(ctx, fy * h, h, [[0, P.far], [0.1, P.field0], [1, P.field1]]);
    ctx.fillRect(0, fy * h, w, h - fy * h);
    // soft swells of brighter crop, compressed toward the horizon
    var sw = field(w, h, 90, function (uu, vv, out) {
      var depth = vv - fy;
      if (depth <= 0) { out[3] = 0; return; }
      var m = f2((uu - 0.55) / (depth + 0.06) * 0.5, Math.log(depth + 0.02) * 1.1, 141, 3);
      var cc = hx(d ? '#A87A70' : '#F7EAA6');
      out[0] = cc[0]; out[1] = cc[1]; out[2] = cc[2]; out[3] = 255 * sstep(0, 0.6, m) * (d ? 0.2 : 0.4);
    });
    blit(ctx, sw, 0, 0, w, h);
    // rows converging on a vanishing point, kept faint
    var vx = 0.55 * w;
    ctx.save();
    ctx.beginPath();
    ctx.rect(0, fy * h, w, h - fy * h);
    ctx.clip();
    ctx.strokeStyle = vlin(ctx, fy * h, h, [[0, P.row, 0], [0.3, P.row, d ? 0.07 : 0.07], [1, P.row, d ? 0.13 : 0.12]]);
    ctx.lineWidth = 1.1 * u;
    ctx.beginPath();
    for (var i = -60; i <= 60; i++) { ctx.moveTo(vx + i * 0.003 * w, fy * h); ctx.lineTo(vx + i * 0.08 * w, h * 1.02); }
    ctx.stroke();
    ctx.restore();
    if (!d) {
      ctx.fillStyle = css('#4A6688', 0.5);
      flock(ctx, 0.56 * w, 0.21 * h, 0.03 * S, 0.01 * S, 2, 4);
    }
    grain(ctx, w, h, d ? 0.09 : 0.06, d);
  }

  
// ---------------------------------------------------------------- 8. Harbour fog
  function fogCoast(ctx, w, h, o) {
    var d = !!o.dark, S = Math.min(w, h), u = S / 390, ax = w / S, ay = h / S, hz = 0.47;
    var P = d ? {
      sky: [[0, '#081022'], [0.5, '#102038'], [1, '#1D3550']],
      sea0: '#1A3048', sea1: '#0A1424', rock: '#0A1220', rockHi: '#2C4462', far: '#16283C', fog: '#3E5A7A', foam: '#8FB0CC',
      tower: '#14202F', towerSh: '#0C1520', band: '#14202F', lantern: '#1A2636', spruce: '#060C16'
    } : {
      sky: [[0, '#A3C7DC'], [0.5, '#CFE3EB'], [1, '#E8F1F2']],
      sea0: '#B3D6DD', sea1: '#86B2C0', rock: '#8A9896', rockHi: '#D3DBD7', far: '#AFC6CB', fog: '#F5F9F9', foam: '#FFFFFF',
      tower: '#FAF8F2', towerSh: '#D9DEDF', band: '#C9574A', lantern: '#B84A3F', spruce: '#3B5E5C'
    };
    fillAll(ctx, w, h, vlin(ctx, 0, hz * h, P.sky));
    if (d) stars(ctx, w, h, S, 131, Math.round(70 * (w * h) / (390 * 844)), 0, 0.32, '#E6EEFF', 0.6);
    else {
      glow(ctx, 0.28 * w, 0.14 * h, 0.75 * S, '#FFFDF4', 0.6);
      sun(ctx, 0.28 * w, 0.14 * h, 0.034 * S, '#FFFEF8', '#FFFFFF', 0.12 * S, 0.5);
    }
    // sea
    ctx.fillStyle = vlin(ctx, hz * h, h, [[0, P.sea0], [1, P.sea1]]);
    ctx.fillRect(0, hz * h, w, h - hz * h);
    var r = rng(141);
    for (var i = 0; i < 34; i++) {
      var t = (i + r()) / 34, y = lerp(hz + 0.003, 1, Math.pow(t, 1.6)) * h, x = r() * w, len = (0.06 + r() * 0.3) * w;
      ctx.fillStyle = css(P.foam, (d ? 0.06 : 0.22) * (0.4 + r()));
      ctx.fillRect(x - len / 2, y, len, (0.5 + t * 1.2) * u);
    }
    // a far wooded island in the haze
    var isl = ridge(w, h, S, function (xs, xn) { return hz + 0.001 - 0.016 * Math.max(0, Math.sin(Math.PI * clamp((xn - 0.02) / 0.3, 0, 1))) * (0.8 + 0.2 * n1(xs * 9, 142)); });
    ctx.fillStyle = css(P.far);
    ctx.beginPath();
    treeline(ctx, isl, 0.03, 0.31, 0.005 * h, 0.009 * h, 0.009 * w, 0.3, 143, 0.003 * h);
    ctx.fill();
    fillRidge(ctx, isl, css(P.far));
    // rocky point on the right: granite slabs rising to the lighthouse
    var head = ridge(w, h, S, function (xs, xn) {
      var e = sstep(0.52, 0.74, xn), top = 0.405 + 0.006 * f1(xs * 5, 144, 3);
      return lerp(hz + 0.004, top, e) + 0.01 * Math.abs(n1(xs * 14, 145)) * e * (1 - e) * 4 + 0.004 * Math.abs(n1(xs * 40, 146)) * e;
    });
    // spruce fringe behind the light, low so the tower stands clear
    ctx.fillStyle = css(d ? P.spruce : mix(P.spruce, P.sky[2][1], 0.35));
    ctx.beginPath();
    treeline(ctx, head, 0.9, 1.04, 0.02 * h, 0.05 * h, 0.012 * w, 0.27, 147);
    ctx.fill();
    fillRidge(ctx, head, vlin(ctx, 0.4 * h, hz * h + 0.03 * h, [[0, mix(P.rock, P.rockHi, d ? 0.25 : 0.3)], [0.5, P.rock], [1, mix(P.rock, P.sea1, 0.3)]]));
    // slab joints: a few sloping lit edges across the granite
    ctx.save();
    ridgePath(ctx, head);
    ctx.clip();
    var jr = rng(148);
    ctx.strokeStyle = css(d ? P.rockHi : '#6A7876', d ? 0.22 : 0.35);
    ctx.lineWidth = 1.0 * u;
    ctx.beginPath();
    for (i = 0; i < 6; i++) {
      var jx = lerp(0.56, 1.0, jr()) * w, jy = ridgeAt(head, jx / w) + (0.006 + jr() * 0.03) * h, jl = (0.04 + jr() * 0.08) * w;
      ctx.moveTo(jx, jy);
      ctx.quadraticCurveTo(jx + jl * 0.5, jy + 0.004 * h, jx + jl, jy + (0.006 + jr() * 0.01) * h);
    }
    ctx.stroke();
    ctx.restore();
    eglow(ctx, 0.6 * w, (hz + 0.004) * h, 0.12 * w, 0.004 * h, P.foam, d ? 0.3 : 0.8);

    // lighthouse: tapered tower, gallery, lantern room and dome
    var lx = 0.8 * w, lb = ridgeAt(head, 0.8) + 0.006 * h, lt = 0.262 * h, lw0 = 0.088 * w, lw1 = 0.062 * w;
    ctx.beginPath();
    ctx.moveTo(lx - lw0 / 2, lb);
    ctx.lineTo(lx - lw1 / 2, lt);
    ctx.lineTo(lx + lw1 / 2, lt);
    ctx.lineTo(lx + lw0 / 2, lb);
    ctx.closePath();
    ctx.fillStyle = lin(ctx, lx - lw0 / 2, 0, lx + lw0 / 2, 0, [[0, P.tower], [0.55, P.tower], [1, P.towerSh]]);
    ctx.fill();
    if (!d) {
      ctx.save();
      ctx.clip();
      ctx.fillStyle = css(P.band, 0.92);
      ctx.fillRect(lx - lw0, lt + (lb - lt) * 0.42, lw0 * 2, (lb - lt) * 0.14);
      ctx.restore();
      // two small windows
      ctx.fillStyle = css('#55646C', 0.7);
      ctx.fillRect(lx - 0.006 * w, lt + (lb - lt) * 0.2, 0.012 * w, 0.012 * h);
      ctx.fillRect(lx - 0.006 * w, lt + (lb - lt) * 0.68, 0.012 * w, 0.012 * h);
    }
    var gy = lt, gw = lw1 * 1.3, lrH = 0.03 * h, lrW = lw1 * 0.72;
    ctx.fillStyle = css(d ? P.tower : '#3E4A50');
    ctx.fillRect(lx - gw / 2, gy - 0.004 * h, gw, 0.005 * h);
    ctx.fillStyle = css(d ? P.lantern : '#E9EEF0');
    ctx.fillRect(lx - lrW / 2, gy - 0.004 * h - lrH, lrW, lrH);
    if (!d) {
      ctx.strokeStyle = css('#3E4A50', 0.8);
      ctx.lineWidth = Math.max(1, 0.8 * u);
      ctx.beginPath();
      ctx.moveTo(lx, gy - 0.004 * h - lrH); ctx.lineTo(lx, gy - 0.004 * h);
      ctx.moveTo(lx - lrW * 0.25, gy - 0.004 * h - lrH); ctx.lineTo(lx - lrW * 0.25, gy - 0.004 * h);
      ctx.moveTo(lx + lrW * 0.25, gy - 0.004 * h - lrH); ctx.lineTo(lx + lrW * 0.25, gy - 0.004 * h);
      ctx.stroke();
    }
    var domeY = gy - 0.004 * h - lrH;
    ctx.fillStyle = css(P.lantern);
    ctx.beginPath();
    ctx.moveTo(lx - lrW * 0.62, domeY);
    ctx.quadraticCurveTo(lx - lrW * 0.5, domeY - lrH * 0.7, lx, domeY - lrH * 0.8);
    ctx.quadraticCurveTo(lx + lrW * 0.5, domeY - lrH * 0.7, lx + lrW * 0.62, domeY);
    ctx.closePath();
    ctx.fill();
    ctx.fillRect(lx - 0.6 * u, domeY - lrH * 1.1, 1.2 * u, lrH * 0.35);
    // keeper's cottage beside the tower
    var kx = 0.88 * w, kb = ridgeAt(head, 0.9) + 0.004 * h, kw = 0.075 * w, kh = 0.028 * h;
    ctx.fillStyle = css(d ? P.towerSh : '#ECEAE3');
    ctx.fillRect(kx, kb - kh, kw, kh + 0.006 * h);
    ctx.fillStyle = css(d ? P.towerSh : '#7C4A44');
    ctx.beginPath();
    ctx.moveTo(kx - 0.006 * w, kb - kh);
    ctx.lineTo(kx + kw / 2, kb - kh - 0.022 * h);
    ctx.lineTo(kx + kw + 0.006 * w, kb - kh);
    ctx.closePath();
    ctx.fill();
    var lampY = domeY + lrH * 0.5;
    if (d) {
      ctx.fillStyle = css('#FFE9B8', 0.95);
      ctx.fillRect(lx - lrW / 2, domeY, lrW, lrH);
      // the beam sweeps left across the top third of the sky
      // the beam is drawn small and upscaled, which feathers its edges
      var bs = 14, bw = Math.ceil(w / bs) + 2, bh = Math.ceil(h / bs) + 2, bcv = mk(bw, bh), bg = bcv.getContext('2d');
      bg.scale(1 / bs, 1 / bs);
      bg.beginPath();
      bg.moveTo(lx, lampY);
      bg.lineTo(-0.15 * w, lampY - 0.2 * h);
      bg.lineTo(-0.15 * w, lampY + 0.04 * h);
      bg.closePath();
      bg.fillStyle = lin(bg, lx, 0, -0.15 * w, 0, [[0, '#FFF2C7', 0.3], [0.5, '#FFF2C7', 0.13], [1, '#FFF2C7', 0.03]]);
      bg.fill();
      ctx.save();
      ctx.globalCompositeOperation = 'screen';
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = 'high';
      ctx.drawImage(bcv, 0, 0, bw, bh, 0, 0, bw * bs, bh * bs);
      ctx.restore();
      glow(ctx, lx, lampY, 0.28 * S, '#FFDCA8', 0.35, 'screen');
      glow(ctx, lx, lampY, 0.05 * S, '#FFF4DE', 0.9, 'screen');
      // lit cottage window and the light on the water
      ctx.fillStyle = css('#FFD9A0', 0.85);
      ctx.fillRect(kx + kw * 0.38, kb - kh * 0.7, kw * 0.22, kh * 0.4);
      glitter(ctx, lx, hz * h + 0.01 * h, 0.7 * h, 0.04 * S, '#FFD9A0', 0.3, 149, u, 30, 1.3);
    }
    // foreground boulders bottom-left, wet tops catching the light, foam at the waterline
    var B = [[-0.03, 0.95, 0.12, 0.05], [0.09, 0.975, 0.13, 0.035], [0.22, 0.99, 0.08, 0.02]];
    var rockC = d ? '#03070E' : '#46676C', rockHi = d ? '#2F4A68' : '#A8C6C8';
    for (i = 0; i < B.length; i++) {
      var bx = B[i][0] * w, by = B[i][1] * h, brx = B[i][2] * S, bry = B[i][3] * h, br = rng(160 + i);
      ctx.beginPath();
      for (var q = 0; q <= 48; q++) {
        var an = (q / 48) * Math.PI * 2, rr2 = 1 + 0.1 * n1(q * 0.35, 161 + i) + 0.04 * (br() - 0.5);
        var px2 = bx + Math.cos(an) * brx * rr2, py2 = by + Math.sin(an) * bry * rr2 * (Math.sin(an) < 0 ? 1 : 1.6);
        if (q) ctx.lineTo(px2, py2); else ctx.moveTo(px2, py2);
      }
      ctx.closePath();
      ctx.fillStyle = vlin(ctx, by - bry, by + bry, [[0, mix(rockC, rockHi, 0.35)], [0.45, rockC], [1, rockC]]);
      ctx.fill();
      ctx.save();
      ctx.clip();
      eglow(ctx, bx - brx * 0.35, by - bry * 0.85, brx * 0.9, bry * 0.5, rockHi, d ? 0.35 : 0.55);
      ctx.restore();
      eglow(ctx, bx + brx * 0.1, by + bry * 0.55, brx * 1.25, bry * 0.22, P.foam, d ? 0.22 : 0.7);
    }
    // fog banks: horizontal streaks over the water where the chat sits
    var fog = field(w, h, 120, function (uu, vv, out) {
      var x = uu * ax, y = vv * ay;
      var m = f2(x * 1.1, y * 6.5, 151, 4);
      var band = Math.exp(-Math.pow((vv - 0.5) / 0.05, 2)) * 0.55 + Math.exp(-Math.pow((vv - 0.66) / 0.08, 2)) * 0.8 + Math.exp(-Math.pow((vv - 0.84) / 0.07, 2)) * 0.55;
      var a = clamp(band * (0.45 + 0.9 * m), 0, 1);
      var c = hx(P.fog);
      out[0] = c[0]; out[1] = c[1]; out[2] = c[2]; out[3] = 255 * a * (d ? 0.55 : 0.75);
    });
    blit(ctx, fog, 0, 0, w, h);
    grain(ctx, w, h, d ? 0.09 : 0.06, d);
  }

  
// ---------------------------------------------------------------- 9. Canyon glow
  function mesaRidge(w, h, S, base, buttes, seed) {
    return ridge(w, h, S, function (xs, xn) {
      var e = 0;
      for (var i = 0; i < buttes.length; i++) {
        var b = buttes[i], dd = Math.abs(xn - b[0]) * (w / S) - b[1];
        var H = b[2], hgt;
        if (dd < 0) hgt = H * (1 - 0.02 * Math.abs(n1(xs * 20, seed + i)));
        else if (dd < b[3]) hgt = H * (1 - 0.42 * Math.pow(dd / b[3], 1.4));
        else hgt = H * 0.58 * Math.pow(Math.max(0, 1 - (dd - b[3]) / b[4]), 1.6);
        if (hgt > e) e = hgt;
      }
      return base - e - 0.004 * (0.5 + 0.5 * f1(xs * 6, seed, 3));
    });
  }
  function canyon(ctx, w, h, o) {
    var d = !!o.dark, S = Math.min(w, h), u = S / 390;
    var P = d ? {
      sky: [[0, '#090D26'], [0.4, '#1A1A44'], [0.75, '#3A2650'], [1, '#5E3550']],
      haze: '#4A2C50', l0: '#3C2448', l1: '#2E1A36', l2: '#221226', l3: '#170B18', lit: '#E8A07A', shade: '#000000', strata: '#000000'
    } : {
      sky: [[0, '#F8E6CF'], [0.55, '#F7D3B2'], [1, '#F3B98F']],
      haze: '#F5D6BE', l0: '#E8B79C', l1: '#D9967A', l2: '#C9836A', l3: '#B9705A', lit: '#FFE0B8', shade: '#6A2C24', strata: '#7A3020'
    };
    var hz = 0.46;
    fillAll(ctx, w, h, vlin(ctx, 0, hz * h, P.sky));
    var sx = 0.26 * w, sy = 0.3 * h;
    if (d) {
      stars(ctx, w, h, S, 161, Math.round(180 * (w * h) / (390 * 844)), 0, 0.55, '#FFF2E8', 0.85);
      moon(ctx, 0.74 * w, 0.16 * h, 0.032 * S, '#F8EEDC', 0.95, 0.55, 200, '#E8C8E0', 0.12);
      eglow(ctx, 0.2 * w, hz * h, 0.8 * w, 0.12 * h, '#B0507A', 0.3);
    } else {
      glow(ctx, sx, sy, 1.0 * S, '#FFF1DC', 0.7);
      sun(ctx, sx, sy, 0.038 * S, '#FFFBF2', '#FFE0B4', 0.25 * S, 0.65);
    }
    var layers = [
      { base: 0.46, b: [[0.15, 0.08, 0.04, 0.03, 0.12], [0.62, 0.18, 0.055, 0.04, 0.15], [0.95, 0.05, 0.035, 0.02, 0.1]], c: P.l0, s: 1 },
      { base: 0.54, b: [[0.32, 0.06, 0.09, 0.04, 0.12], [0.86, 0.1, 0.1, 0.05, 0.16]], c: P.l1, s: 2 },
      { base: 0.7, b: [[0.06, 0.16, 0.12, 0.05, 0.18], [0.58, 0.035, 0.08, 0.025, 0.1]], c: P.l2, s: 3 },
      { base: 1.0, b: [[0.88, 0.2, 0.2, 0.05, 0.25], [0.04, 0.05, 0.1, 0.03, 0.2]], c: P.l3, s: 4 }
    ];
    for (var k = 0; k < layers.length; k++) {
      var L = layers[k], r = mesaRidge(w, h, S, L.base, L.b, 170 + k * 10);
      fillRidge(ctx, r, vlin(ctx, r.top, L.base * h, [[0, L.c], [1, mix(L.c, P.haze, d ? 0.25 : 0.4)]]));
      // strata in the cliff band just under the rim
      ctx.save();
      ridgePath(ctx, r);
      ctx.clip();
      var sr = rng(180 + k), band = (L.base * h - r.top) * 0.6;
      for (var q = 0; q < 6; q++) {
        var yy = r.top + band * (q + 0.3 + 0.4 * sr()) / 6;
        ctx.fillStyle = css(P.strata, (d ? 0.12 : 0.07) * (0.5 + sr()) * (1 - q / 7));
        ctx.fillRect(0, yy, w, (0.6 + sr() * 1.2) * u * (1 + k * 0.5));
      }
      ctx.restore();
      facets(ctx, r, L.base * h, P.shade, d ? 0.35 : 0.22, 190 + k, 0.015 * h, false);
      ctx.fillStyle = vlin(ctx, (L.base - 0.06) * h, L.base * h, [[0, P.haze, 0], [1, P.haze, d ? 0.35 : 0.55]]);
      ctx.fillRect(0, (L.base - 0.06) * h, w, 0.06 * h);
      // the haze carries on below the foot until the next layer covers it
      ctx.fillStyle = css(P.haze, d ? 0.35 : 0.55);
      ctx.fillRect(0, L.base * h, w, h);
    }
    // the near mesas recede into warm haze where the chat sits
    ctx.fillStyle = vlin(ctx, 0.5 * h, 0.62 * h, [[0, P.haze, 0], [1, P.haze, d ? 0.22 : 0.3]]);
    ctx.fillRect(0, 0.5 * h, w, 0.12 * h);
    ctx.fillStyle = css(P.haze, d ? 0.22 : 0.3);
    ctx.fillRect(0, 0.62 * h, w, 0.38 * h);
    if (!d) {
      ctx.fillStyle = css('#8A5A4A', 0.55);
      flock(ctx, 0.56 * w, 0.2 * h, 0.03 * S, 0.012 * S, 2, 8);
    }
    grain(ctx, w, h, d ? 0.09 : 0.06, d);
  }

  // ---------------------------------------------------------------- 10. Moon tide
  function moonTide(ctx, w, h, o) {
    var d = !!o.dark, S = Math.min(w, h), u = S / 390, hz = 0.7;
    var P = d ? {
      sky: [[0, '#050A1C'], [0.5, '#0C1636'], [0.85, '#1A2550'], [1, '#2A3260']],
      sea0: '#1C2550', sea1: '#070C22', glint: '#E8EEFF', streak: '#7E8CC8'
    } : {
      sky: [[0, '#7DB3DF'], [0.5, '#B9D7EC'], [0.85, '#E6EEF0'], [1, '#F5EFE6']],
      sea0: '#A7D0DA', sea1: '#5A9AB4', glint: '#FFFFFF', streak: '#FFFFFF'
    };
    fillAll(ctx, w, h, vlin(ctx, 0, hz * h, P.sky));
    var mx = 0.5 * w, my = 0.34 * h;
    if (d) {
      stars(ctx, w, h, S, 191, Math.round(160 * (w * h) / (390 * 844)), 0, 0.62, '#EEF2FF', 0.8);
      moon(ctx, mx, my, 0.075 * S, '#F4F1E6', 0, 0, 0, '#B8C6F0', 0.2);
    } else {
      glow(ctx, mx, my, 1.0 * S, '#FFFFFF', 0.45);
      sun(ctx, mx, my, 0.045 * S, '#FFFEF9', '#FFF4DA', 0.3 * S, 0.6);
    }
    // long stratus streaks crossing the disc
    var r = rng(201);
    for (var i = 0; i < 7; i++) {
      var y = (0.28 + r() * 0.36) * h;
      eglow(ctx, r() * w, y, (0.3 + r() * 0.4) * S, (0.003 + r() * 0.006) * h, P.streak, d ? 0.22 : 0.55);
    }
    ctx.fillStyle = vlin(ctx, hz * h, h, [[0, P.sea0], [1, P.sea1]]);
    ctx.fillRect(0, hz * h, w, h - hz * h);
    eglow(ctx, mx, hz * h, 0.7 * w, 0.025 * h, d ? '#8090D0' : '#FFFFFF', d ? 0.3 : 0.55);
    for (i = 0; i < 30; i++) {
      var t = (i + r()) / 30, yy = lerp(hz + 0.004, 1, Math.pow(t, 1.6)) * h, xx = r() * w, len = (0.1 + r() * 0.4) * w;
      ctx.fillStyle = css(P.streak, (d ? 0.05 : 0.18) * (0.4 + r()));
      ctx.fillRect(xx - len / 2, yy, len, (0.5 + t) * u);
    }
    glitter(ctx, mx, hz * h + u, h, (d ? 0.09 : 0.07) * S, P.glint, d ? 0.85 : 0.9, 211, u, 110, 1.5);
    // one distant sail
    var bx = 0.23 * w, by = hz * h - 0.5 * u, bs = 0.024 * S;
    ctx.fillStyle = css(d ? '#04081A' : '#41606E', d ? 1 : 0.85);
    ctx.beginPath();
    ctx.moveTo(bx, by - bs * 1.6);
    ctx.lineTo(bx, by - bs * 0.12);
    ctx.lineTo(bx - bs * 0.65, by - bs * 0.12);
    ctx.closePath();
    ctx.moveTo(bx + bs * 0.06, by - bs * 1.4);
    ctx.lineTo(bx + bs * 0.5, by - bs * 0.15);
    ctx.lineTo(bx + bs * 0.06, by - bs * 0.15);
    ctx.closePath();
    ctx.moveTo(bx - bs * 0.7, by - bs * 0.08);
    ctx.lineTo(bx + bs * 0.6, by - bs * 0.08);
    ctx.lineTo(bx + bs * 0.45, by);
    ctx.lineTo(bx - bs * 0.55, by);
    ctx.closePath();
    ctx.fill();
    grain(ctx, w, h, d ? 0.09 : 0.06, d);
  }

// ---------------------------------------------------------------- 11. Island peak (Noronha)
  function noronha(ctx, w, h, o) {
    var d = !!o.dark, S = Math.min(w, h), u = S / 390, hz = 0.45;
    var P = d ? {
      sky: [[0, '#0B1230'], [0.45, '#1D2656'], [0.8, '#4A3F72'], [0.95, '#A86A7E'], [1, '#D89A82']],
      sea0: '#3A3E6C', sea1: '#1E2A50', sea2: '#152040', rock: '#1E1C38', rockLit: '#4A3E66', veg: '#161A30', vegLit: '#2A3050',
      sand: '#3A3456', streak: '#B0A0D0', haze: '#6A5A88'
    } : {
      sky: [[0, '#5FA6DE'], [0.5, '#93C6EC'], [0.9, '#CFE6F2'], [1, '#E6F2F2']],
      sea0: '#3A97BE', sea1: '#71C0D1', sea2: '#A9DDDC', rock: '#7E776A', rockLit: '#B9AD98', veg: '#4F8250', vegLit: '#7FAA6C',
      sand: '#F3E8CF', streak: '#FFFFFF', haze: '#D6E9F0'
    };
    fillAll(ctx, w, h, vlin(ctx, 0, hz * h, P.sky));
    if (d) {
      stars(ctx, w, h, S, 301, Math.round(120 * (w * h) / (390 * 844)), 0, 0.32, '#F0F0FF', 0.75);
      eglow(ctx, 0.75 * w, hz * h, 0.8 * w, 0.12 * h, '#FFB48A', 0.3);
    } else {
      glow(ctx, 0.82 * w, 0.1 * h, 0.9 * S, '#FFFFFF', 0.45);
      sun(ctx, 0.82 * w, 0.1 * h, 0.03 * S, '#FFFEF6', '#FFF4D8', 0.25 * S, 0.6);
    }
    // a few long wisps behind the peak
    var cr = rng(311);
    for (var i = 0; i < 5; i++) eglow(ctx, cr() * w, (0.2 + cr() * 0.2) * h, (0.25 + cr() * 0.3) * S, (0.003 + cr() * 0.004) * h, d ? '#C090B0' : '#FFFFFF', d ? 0.25 : 0.55);

    // the island: low green hills with a thumb-shaped volcanic spire
    var xc = 0.36, hw = 0.11, top = 0.215, base = hz - 0.07;
    var isl = ridge(w, h, S, function (xs, xn) {
      // a broad ridge of hills that climbs toward the peak from both sides
      var hills = hz - 0.006 - 0.05 * Math.max(0, Math.sin(Math.PI * clamp((xn + 0.08) / 0.95, 0, 1))) * (0.6 + 0.4 * (0.5 + 0.5 * f1(xs * 3, 312, 3)));
      hills -= 0.05 * Math.exp(-Math.pow((xn - xc) / 0.22, 2));
      hills -= 0.016 * Math.exp(-Math.pow((xn - 0.72) / 0.06, 2));
      // the peak: a steep west face, a stepped east shoulder and a slightly hooked summit
      var dx = xn - xc, sp = 1;
      if (dx < 0) { var tl = -dx / (hw * 0.7); if (tl < 1) sp = top + (base - top) * Math.pow(tl, 1.5); }
      else {
        var tr2 = dx / hw;
        if (tr2 < 1) sp = top + 0.012 + (base - top) * (Math.pow(tr2, 1.25) * 0.75 + 0.25 * sstep(0.45, 0.6, tr2));
      }
      sp += 0.004 * n1(xs * 40, 313) + 0.006 * Math.abs(n1(xs * 12, 320));
      return Math.min(hills, sp);
    });
    fillRidge(ctx, isl, css(P.veg));
    ctx.save();
    ridgePath(ctx, isl);
    ctx.clip();
    // lit flanks on the sun side, a soft shade on the other
    var sideLit = d ? -1 : 1;
    ctx.fillStyle = lin(ctx, (xc - hw * 1.5) * w, 0, (xc + hw * 1.5) * w, 0, sideLit > 0 ? [[0, P.veg, 1], [0.5, P.veg, 1], [1, P.vegLit, 1]] : [[0, P.vegLit, 1], [0.5, P.veg, 1], [1, P.veg, 1]]);
    ctx.fillRect(0, 0, w, h);
    // bare rock on the upper spire, with a wavering vegetation line
    ctx.beginPath();
    ctx.moveTo((xc - hw * 1.2) * w, 0);
    var nV = 60;
    for (i = 0; i <= nV; i++) {
      var xn = xc - hw * 1.2 + (i / nV) * hw * 2.4;
      var vy = top + (base - top) * (0.62 + 0.07 * n1(xn * 28, 314)) + 0.012 * Math.abs(xn - xc) / hw;
      ctx.lineTo(xn * w, vy * h);
    }
    ctx.lineTo((xc + hw * 1.2) * w, 0);
    ctx.closePath();
    ctx.fillStyle = lin(ctx, (xc - hw) * w, 0, (xc + hw) * w, 0, sideLit > 0 ? [[0, P.rock], [0.55, P.rock], [1, P.rockLit]] : [[0, P.rockLit], [0.45, P.rock], [1, P.rock]]);
    ctx.fill();
    // vertical fluting in the rock
    var fr = rng(315);
    for (i = 0; i < 12; i++) {
      var fx = (xc + (fr() - 0.4) * hw * 1.1) * w;
      ctx.fillStyle = css(d ? '#000000' : '#4E473E', 0.12 + 0.1 * fr());
      ctx.fillRect(fx, top * h, (0.6 + fr() * 1.2) * u, (base - top) * h * (0.4 + 0.4 * fr()));
    }
    // canopy texture on the hills
    var tr = rng(316);
    for (i = 0; i < 160; i++) {
      var tx = tr() * w, ty = ridgeAt(isl, tx / w) + tr() * 0.05 * h;
      ctx.fillStyle = css(tr() < 0.5 ? P.vegLit : P.veg, 0.22);
      ctx.beginPath();
      ctx.arc(tx, ty, (1.2 + tr() * 2.4) * u, 0, Math.PI * 2);
      ctx.fill();
    }
    // haze at the waterline
    ctx.fillStyle = vlin(ctx, (hz - 0.04) * h, hz * h, [[0, P.haze, 0], [1, P.haze, d ? 0.35 : 0.5]]);
    ctx.fillRect(0, (hz - 0.04) * h, w, 0.04 * h);
    ctx.restore();
    // a pale beach under the hills on the right
    eglow(ctx, 0.68 * w, (hz - 0.002) * h, 0.12 * w, 0.004 * h, P.sand, d ? 0.5 : 0.95);
    if (d) {
      // village lights at the foot of the hills
      var lr = rng(317);
      for (i = 0; i < 14; i++) {
        var lxp = (0.55 + lr() * 0.25) * w, lyp = (hz - 0.008 - lr() * 0.012) * h;
        glow(ctx, lxp, lyp, 4 * u, '#FFCF8A', 0.6);
        ctx.fillStyle = css('#FFE2B0', 0.9);
        ctx.fillRect(lxp, lyp, 1.2 * u, 1.2 * u);
      }
    }

    // sea, deep offshore and turquoise toward the viewer
    ctx.fillStyle = vlin(ctx, hz * h, h, [[0, P.sea0], [0.35, P.sea1], [1, P.sea2]]);
    ctx.fillRect(0, hz * h, w, h - hz * h);
    ctx.fillStyle = css(d ? '#E0B0A0' : '#FFFFFF', d ? 0.3 : 0.5);
    ctx.fillRect(0, hz * h, w, Math.max(1, 0.7 * u));
    var sr = rng(318);
    for (i = 0; i < 36; i++) {
      var t = (i + sr()) / 36, yy = lerp(hz + 0.004, 1, Math.pow(t, 1.6)) * h, xx = sr() * w, len = (0.08 + sr() * 0.35) * w;
      ctx.fillStyle = css(P.streak, (d ? 0.05 : 0.16) * (0.4 + sr()));
      ctx.fillRect(xx - len / 2, yy, len, (0.5 + t * 1.2) * u);
    }
    // the sun or dusk glow on the water, near the horizon only
    glitter(ctx, d ? 0.75 * w : 0.82 * w, hz * h + u, 0.62 * h, 0.05 * S, d ? '#FFC0A0' : '#FFFFFF', d ? 0.45 : 0.7, 319, u, 40, 1.4);
    // a small fishing boat
    var bx = 0.18 * w, by = (hz + 0.05) * h, bs = 0.03 * S;
    ctx.fillStyle = css(d ? '#0A0C20' : '#F4F1EA');
    ctx.beginPath();
    ctx.moveTo(bx - bs, by - bs * 0.15);
    ctx.lineTo(bx + bs, by - bs * 0.15);
    ctx.lineTo(bx + bs * 0.75, by + bs * 0.12);
    ctx.lineTo(bx - bs * 0.8, by + bs * 0.12);
    ctx.closePath();
    ctx.fill();
    ctx.fillStyle = css(d ? '#0A0C20' : '#C65A48');
    ctx.fillRect(bx - bs * 0.25, by - bs * 0.55, bs * 0.5, bs * 0.4);
    if (!d) {
      ctx.fillStyle = css('#2D6A78', 0.6);
      ctx.fillRect(bx - bs * 0.85, by + bs * 0.12, bs * 1.6, Math.max(1, 0.6 * u));
      ctx.fillStyle = css('#3C5E70', 0.55);
      flock(ctx, 0.62 * w, 0.21 * h, 0.03 * S, 0.011 * S, 3, 17);
    }
    grain(ctx, w, h, d ? 0.09 : 0.06, d);
  }

  
// ---------------------------------------------------------------- 12. Falls mist (Iguaçu)
  function falls(ctx, w, h, o) {
    var d = !!o.dark, S = Math.min(w, h), u = S / 390, ax = w / S, ay = h / S;
    var P = d ? {
      sky: [[0, '#070D22'], [0.6, '#13204A'], [1, '#22346A']],
      canopy: '#09121E', canopyLit: '#1A2B40', cliff: '#121A2A', moss: '#1C2C3C', water: '#D2DEF4', waterSh: '#5E74A0',
      mist: '#8496C4', river0: '#3A4A78', river1: '#16223E', foam: '#AFC0E8'
    } : {
      sky: [[0, '#86BEE6'], [0.6, '#B6D9EF'], [1, '#D8EBF3']],
      canopy: '#2F5E3E', canopyLit: '#6E9C5E', cliff: '#4F5A4C', moss: '#6F8A5C', water: '#FFFFFF', waterSh: '#B4C7CE',
      mist: '#F4F8F7', river0: '#CFE2DC', river1: '#A6C9C1', foam: '#FFFFFF'
    };
    // a horseshoe gorge: its near arms sit higher in the frame than the far middle
    function bow(uu) { var k = 2 * uu - 1; return k * k; }
    function rimY(uu) { return 0.2 - 0.07 * bow(uu); }
    function lipY(uu) { return 0.3 - 0.05 * bow(uu); }
    function footY(uu) { return 0.42 - 0.03 * bow(uu); }
    // appends a sampled curve to the current path (lineTo throughout, so paths stay closed)
    function curve(fn, x0, x1, n) { for (var i = 0; i <= n; i++) { var t = x0 + (x1 - x0) * i / n; ctx.lineTo(t * w, fn(t) * h); } }
    fillAll(ctx, w, h, vlin(ctx, 0, 0.2 * h, P.sky));
    if (d) {
      stars(ctx, w, h, S, 401, Math.round(70 * (w * h) / (390 * 844)), 0, 0.16, '#E8EEFF', 0.7);
      moon(ctx, 0.78 * w, 0.065 * h, 0.026 * S, '#F4F1E6', 0, 0, 0, '#B8C6F0', 0.18);
    } else {
      glow(ctx, 0.78 * w, 0.02 * h, 0.8 * S, '#FFFFFF', 0.5);
    }
    // wet basalt between the rim and the foot, mottled with moss
    var cliffC = field(w, h, 140, function (uu, vv, out) {
      var X = uu * ax, Y = vv * ay, m = f2(X * 6, Y * 10, 403, 4), k = f2(X * 18, Y * 4, 404, 2);
      var c = mix(P.cliff, P.moss, sstep(0.0, 0.5, m) * 0.8);
      c = mix(c, P.canopy, sstep(0.25, 0.6, k) * 0.35);
      out[0] = c[0]; out[1] = c[1]; out[2] = c[2]; out[3] = 255;
    });
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(-0.01 * w, rimY(0) * h);
    curve(rimY, -0.01, 1.01, 60);
    ctx.lineTo(1.01 * w, h);
    ctx.lineTo(-0.01 * w, h);
    ctx.closePath();
    ctx.clip();
    blit(ctx, cliffC, 0, 0, w, h);
    ctx.restore();
    // curtains of falling water, finer toward the far middle of the bend
    var cur = [], r = rng(402), x = -0.03;
    while (x < 1.03) {
      var far = 1 - bow(clamp(x, 0, 1)), cw = (0.02 + r() * 0.07) * (1 - 0.4 * far);
      cur.push([x, x + cw, r() < 0.7, r() < 0.85]);
      x += cw + (0.03 + r() * 0.07) * (1 - 0.4 * far);
    }
    var fallsC = field(w, h, Math.min(400, Math.max(200, w / 1.4)), function (uu, vv, out) {
      out[3] = 0;
      var rim = rimY(uu), lip = lipY(uu), foot = footY(uu);
      if (vv < rim || vv > foot + 0.04) return;
      var X = uu * ax, Y = vv * ay, upper = vv < lip;
      for (var i = 0; i < cur.length; i++) {
        var c = cur[i];
        if (upper ? !c[2] : !c[3]) continue;
        var y0 = upper ? rim + 0.004 : lip + 0.002, y1 = upper ? lip + 0.006 : foot + 0.04, t = clamp((vv - y0) / (y1 - y0), 0, 1);
        // each curtain spreads a little as it falls and frays at the edges
        var spread = 0.006 + 0.012 * t, fray = 0.006 * n1(Y * 14 + i * 3, 410 + i);
        var e = Math.min(uu - c[0] + spread + fray, c[1] - uu + spread - fray);
        if (e < -0.008) continue;
        var a = sstep(-0.008, 0.004, e) * sstep(y0 - 0.003, y0 + 0.004, vv) * sstep(1, 0.75, t);
        var st = n2(X * 140 + i * 7, Y * 3, 420), st2 = n2(X * 50 + i, Y * 1.4, 421);
        var bright = clamp(0.62 + 0.32 * st + 0.18 * st2 + 0.25 * (1 - sstep(0, 0.12, t)), 0, 1);
        var col = mix(P.waterSh, P.water, bright);
        out[0] = col[0]; out[1] = col[1]; out[2] = col[2];
        out[3] = 255 * a * clamp(0.72 + 0.28 * st, 0, 1);
        return;
      }
    });
    blit(ctx, fallsC, 0, 0, w, h);
    // the vegetated ledge between the tiers
    ctx.beginPath();
    ctx.moveTo(-0.01 * w, lipY(0) * h);
    curve(function (t) {
      var b = Math.abs(n1(t * 40, 432)) * 0.007 + Math.abs(n1(t * 90, 433)) * 0.003;
      return lipY(t) - 0.008 - b - 0.006 * Math.max(0, f1(t * 9, 431, 2));
    }, -0.01, 1.01, 160);
    curve(function (t) { return lipY(t) + 0.006 + 0.004 * Math.abs(n1(t * 30, 434)); }, 1.01, -0.01, 60);
    ctx.closePath();
    ctx.fillStyle = vlin(ctx, (lipY(0) - 0.03) * h, (lipY(0.5) + 0.01) * h, [[0, P.canopyLit], [1, mix(P.canopy, P.canopyLit, 0.3)]]);
    ctx.fill();
    // jungle along the rim: rounded crowns over a dark band
    ctx.beginPath();
    ctx.moveTo(-0.01 * w, rimY(0) * h);
    curve(function (t) {
      var b = 0;
      for (var k = 0; k < 3; k++) b += Math.abs(n1(t * (24 + k * 15) + k * 5, 440 + k)) * (0.011 / (k + 1));
      return rimY(t) + 0.006 - b - 0.014 * (0.5 + 0.5 * f1(t * 3, 443, 2));
    }, -0.01, 1.01, 220);
    curve(function (t) { return rimY(t) + 0.01; }, 1.01, -0.01, 40);
    ctx.closePath();
    ctx.fillStyle = vlin(ctx, 0.09 * h, 0.22 * h, [[0, P.canopyLit], [0.5, P.canopy], [1, P.canopy]]);
    ctx.fill();

    // river below, calm and pale; its top is feathered so the falls dissolve into it
    ctx.fillStyle = vlin(ctx, 0.38 * h, h, [[0, P.river0, 0], [0.12, P.river0, 1], [1, P.river1, 1]]);
    ctx.fillRect(0, 0.38 * h, w, h);
    var fr = rng(450);
    for (var i = 0; i < 40; i++) {
      var t = (i + fr()) / 40, yy = lerp(0.55, 1, Math.pow(t, 1.4)) * h, xx = fr() * w, len = (0.05 + fr() * 0.25) * w;
      ctx.fillStyle = css(P.foam, (d ? 0.05 : 0.22) * (0.4 + fr()));
      ctx.fillRect(xx - len / 2, yy, len, (0.5 + t) * u);
    }
    // spray billows up from the plunge pools and swallows the foot of the falls
    var mist = field(w, h, 130, function (uu, vv, out) {
      var X = uu * ax, Y = vv * ay, foot = footY(uu);
      var m = f2(X * 2.4, Y * 2.8 + 0.3, 460, 5), m2 = f2(X * 6, Y * 6, 461, 3);
      var top = foot - 0.1 - 0.05 * m;
      var body = sstep(top, top + 0.09, vv) * (1 - (d ? 0.6 : 0.8) * sstep(foot + 0.04, foot + 0.24, vv));
      var veil = Math.exp(-Math.pow((vv - lipY(uu) - 0.01) / 0.022, 2)) * 0.35 * (0.5 + m2);
      var a = clamp(body * (1.0 + 0.3 * m) + veil, 0, 1);
      var cc = hx(P.mist);
      out[0] = cc[0]; out[1] = cc[1]; out[2] = cc[2]; out[3] = 255 * a * (d ? 0.8 : 0.92);
    });
    blit(ctx, mist, 0, 0, w, h);
    // a rainbow by day, a pale moonbow by night, standing in the spray
    var bw = Math.ceil(w / 4), bh = Math.ceil(h / 4), bc = mk(bw, bh), bg = bc.getContext('2d');
    var cx = 0.3 * bw, cy = 0.72 * bh, R = 0.34 * bh, th = 0.03 * bh;
    var spec = d ? [['#E6D8F2', 0], ['#DCE6FF', 0.5], ['#D6F0E8', 1]] : [['#9A62D0', 0], ['#4C80E0', 0.2], ['#3FB27A', 0.42], ['#F0D040', 0.62], ['#F29A3A', 0.8], ['#E2504A', 1]];
    var g = bg.createRadialGradient(cx, cy, R - th, cx, cy, R + th);
    g.addColorStop(0, css(spec[0][0], 0));
    for (var q = 0; q < spec.length; q++) g.addColorStop(0.12 + 0.76 * spec[q][1], css(spec[q][0], 1));
    g.addColorStop(1, css(spec[spec.length - 1][0], 0));
    bg.fillStyle = g;
    bg.fillRect(0, 0, bw, bh);
    // only where the spray is thick enough to hold it
    bg.globalCompositeOperation = 'destination-in';
    bg.fillStyle = vlin(bg, 0.3 * bh, 0.56 * bh, [[0, '#000000', 0], [0.35, '#000000', 1], [0.7, '#000000', 0.6], [1, '#000000', 0]]);
    bg.fillRect(0, 0, bw, bh);
    blit(ctx, bc, 0, 0, w, h, d ? 0.18 : 0.3);
    if (!d) {
      ctx.fillStyle = css('#2F4A40', 0.55);
      flock(ctx, 0.5 * w, 0.075 * h, 0.03 * S, 0.01 * S, 3, 41);
    }
    grain(ctx, w, h, d ? 0.09 : 0.05, d);
  }

  
// ---------------------------------------------------------------- 13. Polar ice
  // F1 and F2 cell distances; F2 - F1 is small along cell borders
  var W2 = [0, 0];
  function worley2(x, y, s) {
    var i = Math.floor(x), j = Math.floor(y), b1 = 9, b2 = 9;
    for (var dj = -1; dj <= 1; dj++) for (var di = -1; di <= 1; di++) {
      var ci = i + di, cj = j + dj;
      var px = ci + 0.1 + 0.8 * h2(ci, cj, s), py = cj + 0.1 + 0.8 * h2(ci, cj, s + 9);
      var dx = px - x, dy = py - y, d = dx * dx + dy * dy;
      if (d < b1) { b2 = b1; b1 = d; } else if (d < b2) b2 = d;
    }
    W2[0] = Math.sqrt(b1); W2[1] = Math.sqrt(b2);
    return W2;
  }
  function berg(ctx, x0, x1, base, topY, h, w, lit, shade, seed) {
    // tabular iceberg: a gently uneven top with rounded shoulders, a lit face and a shaded face
    var r = rng(seed), n = 48, hgt = base - topY;
    function top(t) {
      var sh = sstep(0, 0.12, t) * sstep(1, 0.86, t);
      return base - hgt * (0.25 + 0.75 * sh) * (1 + 0.06 * f1(t * 4, seed, 3)) + (t > 0.55 && t < 0.62 ? hgt * 0.18 * sm(1 - Math.abs(t - 0.585) / 0.035) : 0);
    }
    ctx.beginPath();
    ctx.moveTo(x0 * w, base * h);
    for (var i = 0; i <= n; i++) { var t = i / n; ctx.lineTo(lerp(x0, x1, t) * w, top(t) * h); }
    ctx.lineTo(x1 * w, base * h);
    ctx.closePath();
    ctx.fillStyle = css(lit);
    ctx.fill();
    ctx.save();
    ctx.clip();
    var split = lerp(x0, x1, 0.6 + 0.15 * r());
    ctx.fillStyle = lin(ctx, split * w, 0, x1 * w, 0, [[0, shade, 0.0], [0.2, shade, 0.8], [1, shade, 1]]);
    ctx.fillRect(split * w, 0, (x1 - split) * w + 2, h);
    ctx.fillStyle = vlin(ctx, topY * h, base * h, [[0, shade, 0], [1, shade, 0.35]]);
    ctx.fillRect(x0 * w, topY * h, (x1 - x0) * w, hgt * h);
    ctx.restore();
    // soft mirror image on the still water at its foot
    ctx.fillStyle = vlin(ctx, base * h, (base + hgt * 0.6) * h, [[0, lit, 0.35], [1, lit, 0]]);
    ctx.fillRect(x0 * w, base * h, (x1 - x0) * w, hgt * 0.6 * h);
  }
  function polar(ctx, w, h, o) {
    var d = !!o.dark, S = Math.min(w, h), u = S / 390, hz = 0.43;
    var P = d ? {
      sky: [[0, '#0E1434'], [0.45, '#232A5E'], [0.8, '#4E4A80'], [1, '#86699A']],
      bergLit: '#B6B4E0', bergSh: '#5E5E98', floe: '#555C92', floeHi: '#7D82B8', lead: '#1C2148', leadHi: '#3A3C70', haze: '#6E6094'
    } : {
      sky: [[0, '#9EBCE4'], [0.45, '#C9D2EC'], [0.8, '#EAD9E8'], [1, '#F6E5DE']],
      bergLit: '#FFFFFF', bergSh: '#B8C6E4', floe: '#DDE2F0', floeHi: '#EEF0F7', lead: '#93A6D0', leadHi: '#C8CCE8', haze: '#EEE2E8'
    };
    fillAll(ctx, w, h, vlin(ctx, 0, hz * h, P.sky));
    if (d) {
      stars(ctx, w, h, S, 501, Math.round(140 * (w * h) / (390 * 844)), 0, 0.3, '#EEF0FF', 0.8);
      moon(ctx, 0.3 * w, 0.14 * h, 0.03 * S, '#F6F2E4', 0.94, 0.58, -160, '#C8C0F0', 0.14);
      eglow(ctx, 0.6 * w, hz * h, 0.9 * w, 0.12 * h, '#E0A0B8', 0.25);
    } else {
      // a low pale sun with a soft halo and sun pillar
      glow(ctx, 0.7 * w, 0.31 * h, 1.0 * S, '#FFF4EA', 0.55);
      sun(ctx, 0.7 * w, 0.31 * h, 0.03 * S, '#FFFDF8', '#FFE9DC', 0.18 * S, 0.5);
      eglow(ctx, 0.7 * w, 0.3 * h, 0.02 * S, 0.12 * h, '#FFFFFF', 0.35);
    }
    // icebergs on the horizon
    berg(ctx, 0.06, 0.38, hz + 0.003, hz - 0.06, h, w, P.bergLit, P.bergSh, 511);
    berg(ctx, 0.5, 0.62, hz + 0.002, hz - 0.03, h, w, mix(P.bergLit, P.haze, 0.35), mix(P.bergSh, P.haze, 0.35), 512);
    berg(ctx, 0.82, 1.04, hz + 0.003, hz - 0.045, h, w, mix(P.bergLit, P.haze, 0.15), mix(P.bergSh, P.haze, 0.15), 513);
    ctx.fillStyle = vlin(ctx, (hz - 0.02) * h, hz * h, [[0, P.haze, 0], [1, P.haze, 0.45]]);
    ctx.fillRect(0, (hz - 0.02) * h, w, 0.02 * h);
    // pack ice in perspective: floes are cells, leads are their borders
    var ice = field(w, h, Math.min(360, Math.max(180, w / 1.5)), function (uu, vv, out) {
      var dep = vv - hz;
      if (dep < 0) { out[3] = 0; return; }
      var z = 0.02 + dep, X = (uu - 0.5) / z * 0.22, Y = 0.5 / z;
      var qx = X * 1.2 + 0.35 * f2(X * 1.5, Y * 1.5, 521, 2), qy = Y * 1.2 + 0.35 * f2(X * 1.5 + 4, Y * 1.5, 526, 2);
      var wv = worley2(qx, qy, 522), edge = wv[1] - wv[0];
      // open water varies from hairline cracks to wide leads
      var open = clamp(0.5 + 0.7 * f2(X * 0.5, Y * 0.5, 523, 2), 0, 1), fw = 0.05 + 0.5 * open * open;
      var wv2 = worley2(qx * 3.1 + 7, qy * 3.1, 527), e2 = wv2[1] - wv2[0];
      var broken = sstep(0.45, 0.8, open), fw2 = 0.06 * broken;
      var leadA = Math.max(sstep(fw, fw * 0.55, edge), broken > 0 ? sstep(fw2, fw2 * 0.5, e2) * broken : 0);
      var c = mix(P.floe, P.floeHi, 0.5 + 0.5 * n2(X * 4, Y * 4, 524));
      // floe rims sit a touch darker, and the leads mirror the sky
      c = mix(c, P.lead, sstep(fw * 1.8, fw, edge) * 0.15);
      var lc = mix(P.lead, P.leadHi, 0.5 + 0.5 * n2(X * 0.8, Y * 6, 525));
      c = mix(c, lc, leadA);
      // haze far away, calmer floes where the chat sits
      c = mix(c, P.haze, sstep(0.08, 0.0, dep) * 0.7);
      var band = bandMask(vv);
      c = mix(c, P.floe, band * (d ? 0.2 : 0.22));
      out[0] = c[0]; out[1] = c[1]; out[2] = c[2]; out[3] = 255;
    });
    blit(ctx, ice, 0, 0, w, h);
    if (!d) glitter(ctx, 0.7 * w, hz * h + u, 0.52 * h, 0.03 * S, '#FFFFFF', 0.4, 531, u, 18, 1.2);
    grain(ctx, w, h, d ? 0.09 : 0.05, d);
  }

  
// ================================================================ registry

  function reg(def) { def.section = 'nature'; def.kind = 'canvas'; WP.push(def); }

  reg({ id: 'nature.dunes', name: 'Dune Lagoons', blurb: 'White dune crests and rain lagoons seen from above; at night the moon shines in one pool.', bubble: { light: 'teal', dark: 'purple' }, render: dunes });
  reg({ id: 'nature.palms', name: 'Palm Coast', blurb: 'Coconut palms on an Atlantic beach, by morning sun and by moonlight.', bubble: { light: 'orange', dark: 'blue' }, render: palms });
  reg({ id: 'nature.river', name: 'River Bends', blurb: 'A rainforest river from above, in morning mist and by moonlight.', bubble: { light: 'teal', dark: 'teal' }, render: river });
  reg({ id: 'nature.highlands', name: 'Highland Mist', blurb: 'Araucaria ridges over a sea of mist, at sunrise and under the full moon.', bubble: { light: 'blue', dark: 'green' }, render: highlands });
  reg({ id: 'nature.lake', name: 'Glacier Lake', blurb: 'Snow peaks over a turquoise lake, and the same lake under the stars.', bubble: { light: 'teal', dark: 'teal' }, render: lake });
  reg({ id: 'nature.canola', name: 'Canola Sky', blurb: 'A prairie field under a huge summer sky, and the long dusk after.', bubble: { light: 'green', dark: 'orange' }, render: canola });
  reg({ id: 'nature.fog', name: 'Harbour Fog', blurb: 'A foggy cove with a lighthouse that lights up after dark.', bubble: { light: 'blue', dark: 'blue' }, render: fogCoast });
  reg({ id: 'nature.canyon', name: 'Canyon Glow', blurb: 'Layered mesas at golden hour and under a crescent moon.', bubble: { light: 'blue', dark: 'purple' }, render: canyon });
  reg({ id: 'nature.noronha', name: 'Island Peak', blurb: 'A volcanic spire over a turquoise bay, then the blue hour with village lights.', bubble: { light: 'yellow', dark: 'pink' }, render: noronha });
  reg({ id: 'nature.falls', name: 'Falls Mist', blurb: 'Jungle waterfalls in rising spray, with a rainbow by day and a moonbow by night.', bubble: { light: 'pink', dark: 'purple' }, render: falls });
  reg({ id: 'nature.polar', name: 'Polar Ice', blurb: 'Pack ice and tabular icebergs under a pastel Arctic sky, then the polar twilight.', bubble: { light: 'blue', dark: 'purple' }, render: polar });
  reg({ id: 'nature.tide', name: 'Moon Tide', blurb: 'Open sea with a sun path by day and a moon path by night.', bubble: { light: 'blue', dark: 'blue' }, render: moonTide });
})(typeof window !== 'undefined' ? window : globalThis);

