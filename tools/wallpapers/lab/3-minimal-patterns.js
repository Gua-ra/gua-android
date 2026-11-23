/*
 * Copyright 2026 Gua
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 *
 * Copied verbatim from the Gua Wallpaper Lab page, script 3 of 4 (the Minimal and Patterns sections).
 * Do not edit here: change the Lab, then copy the whole script again.
 */

/*
 * Gua chat wallpapers: Minimal and Patterns sections.
 * Copyright Gua. Original work for Gua, apart from two adapted pieces: the "Hash without Sine"
 * function in the Currents Live shader (David Hoskins, MIT licence) and mulberry32 (Tommy Ettinger, CC0).
 *
 * kind 'canvas': render(ctx, w, h, { dark, t, pulse, scale, option }) draws the whole wallpaper.
 *   Output is deterministic: noise comes from integer hashes of pixel coordinates and seeded PRNGs.
 *   opts.scale is device pixels per design point; when absent it is w / 390.
 * kind 'webgl': frag is a GLSL ES 1.00 shader using the shared uniform contract.
 *
 * Optional colour options: a wallpaper may carry options: [{ id, name, swatch: { light, dark },
 * bubble: { light, dark } }]. render() reads opts.option (an option id) and falls back to the first
 * option, so a host that ignores options gets the default look. The top-level bubble always equals
 * the first option's bubble.
 *
 * Per-pixel wallpapers are evaluated in linear light, then a triangular dither removes 8-bit banding.
 */
(function () {
  'use strict';

  // ---------- shared helpers ----------

  function hash2(x, y, seed) {
    var h = Math.imul(x | 0, 374761393) ^ Math.imul(y | 0, 668265263) ^ Math.imul(seed | 0, 2246822519);
    h = Math.imul(h ^ (h >>> 13), 1274126177);
    h ^= h >>> 16;
    return (h >>> 0) / 4294967296;
  }

  function mulberry32(seed) {
    var a = seed >>> 0;
    return function () {
      a = (a + 0x6D2B79F5) >>> 0;
      var t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function srgbToLin(c) { return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }
  function linToSrgb(c) { return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1 / 2.4) - 0.055; }

  function hex(h) {
    var n = parseInt(h.replace('#', ''), 16);
    return [srgbToLin(((n >> 16) & 255) / 255), srgbToLin(((n >> 8) & 255) / 255), srgbToLin((n & 255) / 255)];
  }
  function rgba(h, a) {
    var n = parseInt(h.replace('#', ''), 16);
    return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + a + ')';
  }

  function clamp01(v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
  function smooth(a, b, v) { var t = clamp01((v - a) / (b - a)); return t * t * (3 - 2 * t); }
  function mixInto(o, c, k) { o[0] += (c[0] - o[0]) * k; o[1] += (c[1] - o[1]) * k; o[2] += (c[2] - o[2]) * k; }
  function setTo(o, c) { o[0] = c[0]; o[1] = c[1]; o[2] = c[2]; }
  function scaleInto(o, f) { o[0] *= f; o[1] *= f; o[2] *= f; }
  function lerp3(a, b, t) { return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t]; }

  // Linear [0,1] -> sRGB 8-bit, via a LUT big enough that the dither is not quantised away.
  var LUT_N = 8192;
  var LUT = new Float32Array(LUT_N + 1);
  for (var li = 0; li <= LUT_N; li++) LUT[li] = linToSrgb(li / LUT_N) * 255;

  // Value noise on an integer lattice, smoothstep-interpolated. Range 0..1.
  function vnoise(x, y, seed) {
    var xi = Math.floor(x), yi = Math.floor(y);
    var xf = x - xi, yf = y - yi;
    var u = xf * xf * (3 - 2 * xf), v = yf * yf * (3 - 2 * yf);
    var a = hash2(xi, yi, seed), b = hash2(xi + 1, yi, seed);
    var c = hash2(xi, yi + 1, seed), d = hash2(xi + 1, yi + 1, seed);
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
  }
  function fbm3(x, y, seed) {
    return vnoise(x, y, seed) * 0.57 + vnoise(x * 2.03, y * 2.03, seed + 1) * 0.29 + vnoise(x * 4.1, y * 4.1, seed + 2) * 0.14;
  }

  /*
   * Runs fn(px, py, out) for every pixel. px, py are in "width units": px in 0..1 across, py in
   * 0..h/w down, so circles stay round on any aspect ratio. fn writes linear RGB into out.
   * grain(x, y) optionally returns a luminance offset in 8-bit steps; it is called right after fn
   * for the same pixel, so fn may leave per-pixel state for it in a closure.
   */
  function shade(ctx, w, h, fn, grain) {
    var img = ctx.createImageData(w, h);
    var d = img.data;
    var out = [0, 0, 0];
    var inv = 1 / w;
    var i = 0;
    for (var y = 0; y < h; y++) {
      var py = (y + 0.5) * inv;
      for (var x = 0; x < w; x++) {
        fn((x + 0.5) * inv, py, out);
        // Triangular dither, +-1 LSB, shared by the three channels so it reads as grain, not colour noise.
        var n = hash2(x, y, 7) + hash2(x, y, 11) - 1;
        if (grain) n += grain(x, y);
        for (var k = 0; k < 3; k++) {
          var v = out[k];
          v = v < 0 ? 0 : v > 1 ? 1 : v;
          var s = LUT[(v * LUT_N + 0.5) | 0] + n;
          d[i + k] = s < 0 ? 0 : s > 255 ? 255 : s + 0.5;
        }
        d[i + 3] = 255;
        i += 4;
      }
    }
    ctx.putImageData(img, 0, 0);
  }

  // Film grain: clumps of about 1.1 pt plus a little per-pixel sparkle. Roughly -1..1.
  function filmGrain(px, py, k, seed) {
    return (vnoise(px / (1.1 * k), py / (1.1 * k), seed) - 0.5) * 1.7 + (hash2(px, py, seed + 9) - 0.5) * 0.7;
  }

  function unitScale(w, opts) { return (opts && opts.scale) || w / 390; }

  // Picks the colour option named by opts.option, else the first.
  function pick(options, o) {
    var id = o && o.option;
    for (var i = 0; i < options.length; i++) if (options[i].id === id) return options[i];
    return options[0];
  }
  // Public option descriptors (no palette internals), with the bubble pairing of each.
  function describe(options) {
    return options.map(function (op) {
      return { id: op.id, name: op.name, swatch: op.swatch, bubble: op.bubble };
    });
  }

  // Paper fibres: short curved strands, half a shade lighter and half darker than the sheet.
  function fibres(ctx, w, h, k, seed, density, light, dark) {
    var rnd = mulberry32(seed);
    var n = Math.round((w / k) * (h / k) * density);
    var paths = [new Path2D(), new Path2D()];
    for (var i = 0; i < n; i++) {
      var x = rnd() * w, y = rnd() * h, len = (3 + rnd() * rnd() * 16) * k, a = rnd() * Math.PI * 2;
      var bend = (rnd() - 0.5) * len * 0.7, ca = Math.cos(a), sa = Math.sin(a);
      var p = paths[rnd() < 0.55 ? 0 : 1];
      p.moveTo(x, y);
      p.quadraticCurveTo(x + ca * len * 0.5 - sa * bend, y + sa * len * 0.5 + ca * bend, x + ca * len, y + sa * len);
    }
    ctx.save();
    ctx.lineCap = 'round';
    ctx.lineWidth = 0.42 * k;
    ctx.strokeStyle = light; ctx.stroke(paths[0]);
    ctx.strokeStyle = dark; ctx.stroke(paths[1]);
    ctx.restore();
  }

  var REG = (window.GUA_WALLPAPERS = window.GUA_WALLPAPERS || []);

  // ================================================================ MINIMAL

  // 1. Ember: one warm glow pooled in the bottom-right corner, like the last light on a wall.
  (function () {
    var OPTS = [
      { id: 'copper', name: 'Copper', swatch: { light: '#F0A77A', dark: '#A9441E' }, bubble: { light: 'orange', dark: 'orange' },
        dark: { top: hex('#0B0B0D'), bot: hex('#100C0A'), glow: hex('#A9441E'), core: hex('#E58A4A'), a: 0.62, b: 0.30 },
        light: { top: hex('#F7F5F2'), bot: hex('#F8F1EA'), glow: hex('#F4C8A6'), core: hex('#F0A77A'), a: 0.85, b: 0.35 } },
      { id: 'violet', name: 'Violet', swatch: { light: '#C3A9F2', dark: '#6C52E6' }, bubble: { light: 'purple', dark: 'purple' },
        dark: { top: hex('#09080F'), bot: hex('#0C0A13'), glow: hex('#5B2E86'), core: hex('#8A6BF0'), a: 0.6, b: 0.30 },
        light: { top: hex('#F8F7FB'), bot: hex('#F6F2FA'), glow: hex('#DCCBF5'), core: hex('#BFA2F0'), a: 0.85, b: 0.35 } },
      { id: 'teal', name: 'Teal', swatch: { light: '#8FD3C4', dark: '#16756E' }, bubble: { light: 'teal', dark: 'teal' },
        dark: { top: hex('#070A0C'), bot: hex('#08100F'), glow: hex('#0F6560'), core: hex('#3BA796'), a: 0.6, b: 0.30 },
        light: { top: hex('#F5F7F7'), bot: hex('#EFF5F3'), glow: hex('#BDE3DA'), core: hex('#8FD3C4'), a: 0.85, b: 0.35 } }
    ];
    REG.push({
      id: 'ember', name: 'Ember', section: 'minimal', kind: 'canvas',
      blurb: 'A single warm glow pooled in one corner, with a fine film grain.',
      bubble: OPTS[0].bubble, options: describe(OPTS),
      render: function (ctx, w, h, o) {
        var p = pick(OPTS, o)[o && o.dark ? 'dark' : 'light'], H = h / w, k = unitScale(w, o), dark = !!(o && o.dark);
        var g = 0;
        shade(ctx, w, h, function (x, y, c) {
          setTo(c, p.top); mixInto(c, p.bot, smooth(0, H, y));
          var dx = x - 1.02, dy = y - (H + 0.04), d2 = dx * dx + dy * dy;
          var a = Math.exp(-d2 / 0.42), b = Math.exp(-d2 / 0.09);
          mixInto(c, p.glow, p.a * a);
          mixInto(c, p.core, p.b * b);
          g = a;
        }, function (px, py) {
          // Grain lives in the light, about 2.5 % of full scale at the brightest point.
          return filmGrain(px, py, k, 41) * (dark ? 1.2 + 5.5 * g : 1.0 + 4.0 * g);
        });
      }
    });
  })();

  // 2. Horizon: a hairline of light high on the screen, a pale sky above and still water below.
  (function () {
    var P = {
      light: { sky0: hex('#F3F6F9'), sky1: hex('#F9EFE6'), glow: hex('#FBE3CC'), line: hex('#FFFFFF'), sea0: hex('#E6ECF1'), sea1: hex('#EDF1F4'),
        ripple: hex('#DCE4EB'), refl: hex('#F7E6D6'), ga: 0.4, la: 1.0, sky1At: 0.75, sun: hex('#FAD9B8'), sunA: 0.85 },
      dark: { sky0: hex('#070A12'), sky1: hex('#0E1626'), glow: hex('#2B4A68'), line: hex('#7FA7C9'), sea0: hex('#0D1522'), sea1: hex('#0A101A'),
        ripple: hex('#132034'), refl: hex('#24405C'), ga: 0.55, la: 1.0, sky1At: 0.6, sun: hex('#9FB8CF'), sunA: 0.0 }
    };
    REG.push({
      id: 'horizon', name: 'Horizon', section: 'minimal', kind: 'canvas',
      blurb: 'A hairline of light where a pale sky meets still water.',
      bubble: { light: 'orange', dark: 'teal' },
      render: function (ctx, w, h, o) {
        var p = o && o.dark ? P.dark : P.light, H = h / w, hy = H * 0.32, k = unitScale(w, o), SUN_X = 0.64, SUN_R = 0.075;
        var lw = 0.5 * k / w; // the line is 1 pt thick
        var gw = 30 * k / w; // glow reach
        shade(ctx, w, h, function (x, y, c) {
          var cx = x - 0.5, fall = 0.55 + 0.45 * Math.exp(-cx * cx / 0.18);
          if (y < hy) {
            setTo(c, p.sky0); mixInto(c, p.sky1, Math.pow(smooth(0, hy, y), 1.6) * p.sky1At + smooth(hy * 0.7, hy, y) * (1 - p.sky1At));
            mixInto(c, p.glow, p.ga * Math.exp(-(hy - y) / gw) * fall);
            if (p.sunA > 0) {
              // A low sun, half set, sitting on the line.
              var sx = x - SUN_X, sy = y - hy, sd = Math.sqrt(sx * sx + sy * sy) - SUN_R;
              mixInto(c, p.glow, 0.5 * p.sunA * Math.exp(-Math.max(0, sd) / 0.07));
              mixInto(c, p.sun, p.sunA * clamp01(0.5 - sd * w / (1.2 * k)));
            }
          } else {
            var dw = y - hy;
            setTo(c, p.sea0); mixInto(c, p.sea1, smooth(hy, H, y));
            mixInto(c, p.glow, p.ga * 0.7 * Math.exp(-dw / (gw * 0.6)) * fall);
            // Long, flat ripples that thin out with distance from the horizon.
            var r = vnoise(x * 2.2, dw * 70 / (1 + dw * 6), 5) * 0.65 + vnoise(x * 6.0, dw * 160 / (1 + dw * 6), 6) * 0.35;
            mixInto(c, p.ripple, smooth(0.55, 0.85, r) * 0.55 * Math.exp(-dw / 0.35));
            // Faint column of reflected light under the centre of the line.
            var col = Math.exp(-cx * cx / 0.004) * Math.exp(-dw / 0.28) * (0.6 + 0.4 * vnoise(x * 30, dw * 90, 8));
            mixInto(c, p.refl, 0.5 * col);
            if (p.sunA > 0) {
              // The sun's road on the water, broken into flat ripples.
              var rx = (x - SUN_X) / (SUN_R * (1 + dw * 0.6));
              var road = Math.exp(-rx * rx * 1.6) * Math.exp(-dw / 0.32);
              var br = vnoise(x * 14, dw * 260 / (1 + dw * 5), 9);
              mixInto(c, p.sun, p.sunA * 0.7 * road * smooth(0.35, 0.7, br));
            }
          }
          var dy = (y - hy) / lw;
          mixInto(c, p.line, p.la * Math.exp(-dy * dy) * fall);
        });
      }
    });
  })();

  // 3. Paper: cotton paper with visible fibres and tooth, lit by window light from the left.
  (function () {
    var OPTS = [
      { id: 'cream', name: 'Cream', swatch: { light: '#F4EFE6', dark: '#1C1A17' }, bubble: { light: 'green', dark: 'green' },
        light: { base: hex('#F4EFE6'), lit: hex('#FAF7F1'), shade: hex('#E6DED1'), fl: '#FFFDF8', fd: '#D9CFBE', tooth: 6.5 },
        dark: { base: hex('#1C1A17'), lit: hex('#24211D'), shade: hex('#141210'), fl: '#3A352E', fd: '#0F0E0C', tooth: 3.6 } },
      { id: 'sage', name: 'Sage', swatch: { light: '#E8EDE3', dark: '#171B16' }, bubble: { light: 'pink', dark: 'pink' },
        light: { base: hex('#E8EDE3'), lit: hex('#F1F4ED'), shade: hex('#DAE1D4'), fl: '#FAFCF7', fd: '#C7D0C0', tooth: 6.5 },
        dark: { base: hex('#171B16'), lit: hex('#1E231D'), shade: hex('#111410'), fl: '#30372E', fd: '#0C0E0B', tooth: 3.6 } },
      { id: 'blush', name: 'Blush', swatch: { light: '#F3E7E3', dark: '#1D1819' }, bubble: { light: 'green', dark: 'green' },
        light: { base: hex('#F3E7E3'), lit: hex('#F9F1EE'), shade: hex('#E7D7D2'), fl: '#FFFAF8', fd: '#DCC6BF', tooth: 6.5 },
        dark: { base: hex('#1D1819'), lit: hex('#252021'), shade: hex('#151112'), fl: '#3A3234', fd: '#100C0D', tooth: 3.6 } }
    ];
    REG.push({
      id: 'paper', name: 'Paper', section: 'minimal', kind: 'canvas',
      blurb: 'Soft cotton paper with real fibres, lit from one side.',
      bubble: OPTS[0].bubble, options: describe(OPTS),
      render: function (ctx, w, h, o) {
        var dark = !!(o && o.dark), p = pick(OPTS, o)[dark ? 'dark' : 'light'], H = h / w, k = unitScale(w, o);
        shade(ctx, w, h, function (x, y, c) {
          setTo(c, p.base);
          // Window light falling in from the left, with a soft deckled edge where it ends.
          var edge = 0.55 + 0.05 * (vnoise(y * 9, 0.5, 3) - 0.5) + y * 0.12;
          var light = 1 - smooth(edge - 0.32, edge + 0.08, x);
          mixInto(c, p.lit, light * 0.85);
          // Soft vignette.
          var vx = (x - 0.5) * 1.6, vy = (y / H - 0.5) * 1.3, v = vx * vx + vy * vy;
          mixInto(c, p.shade, smooth(0.25, 1.2, v) * 0.75);
          // Cloudy mottling, the way cotton fibre settles unevenly.
          var m = fbm3(x * 4, y * 4, 21);
          mixInto(c, m > 0.5 ? p.lit : p.shade, Math.min(1, Math.abs(m - 0.5) * 0.55));
        }, function (px, py) {
          var tooth = vnoise(px / (1.4 * k), py / (1.4 * k), 31) - 0.5;
          var fine = hash2(px, py, 34) - 0.5;
          return p.tooth * (tooth * 1.5 + fine * 0.5);
        });
        fibres(ctx, w, h, k, 9001, 0.022, rgba(p.fl, dark ? 0.35 : 0.55), rgba(p.fd, dark ? 0.45 : 0.32));
      }
    });
  })();

  // 4. Grid: a dot grid, crisp above the chat and thinning out behind it. Square or isometric.
  (function () {
    var OPTS = [
      { id: 'square', name: 'Square', swatch: { light: '#C3C9D2', dark: '#2E3440' }, bubble: { light: 'blue', dark: 'blue' }, iso: false },
      { id: 'iso', name: 'Isometric', swatch: { light: '#C3C9D2', dark: '#2E3440' }, bubble: { light: 'blue', dark: 'blue' }, iso: true }
    ];
    var P = {
      light: { top: hex('#F6F7F8'), bot: hex('#F1F3F5'), dot: hex('#C3C9D2') },
      dark: { top: hex('#0E1114'), bot: hex('#0B0D10'), dot: hex('#2E3440') }
    };
    REG.push({
      id: 'grid', name: 'Grid', section: 'minimal', kind: 'canvas',
      blurb: 'A fine dot grid, crisp at the top and thinning out behind your messages.',
      bubble: OPTS[0].bubble, options: describe(OPTS),
      render: function (ctx, w, h, o) {
        var op = pick(OPTS, o), p = o && o.dark ? P.dark : P.light, H = h / w, k = unitScale(w, o);
        var pitch = 18 * k, rowH = op.iso ? pitch * Math.sqrt(3) / 2 : pitch;
        var rad = 0.9 * k; // dot radius, device pixels
        var ox = (w * 0.5) % pitch;
        shade(ctx, w, h, function (x, y, c) {
          setTo(c, p.top); mixInto(c, p.bot, y / H);
          var gx = x * w, gy = y * w;
          var row = Math.floor(gy / rowH + 0.5);
          var shift = op.iso && (row & 1) ? pitch * 0.5 : 0;
          var fx = ((gx - ox - shift) % pitch + pitch * 1.5) % pitch - pitch * 0.5;
          var fy = gy - row * rowH;
          var d = Math.sqrt(fx * fx + fy * fy);
          var cov = clamp01(rad + 0.5 - d);
          if (cov > 0) {
            var t = y / H;
            var vis = 1 - 0.75 * smooth(0.4, 0.65, t);
            mixInto(c, p.dot, cov * vis);
          }
        });
      }
    });
  })();

  // 5. Eclipse: the lit rim of a dark disc rising over the top of the screen.
  (function () {
    var P = {
      dark: { base: hex('#08080B'), disc: hex('#040406'), warm: hex('#E2A57E'), cool: hex('#7E8FD0'), halo: 0.32, rim: 0.55 },
      light: { base: hex('#F7F4F0'), disc: hex('#ECE6DF'), warm: hex('#E3B98E'), cool: hex('#F2D7B8'), halo: 0.55, rim: 0.7 }
    };
    REG.push({
      id: 'eclipse', name: 'Eclipse', section: 'minimal', kind: 'canvas',
      blurb: 'The glowing edge of a dark disc at the top of the sky.',
      bubble: { light: 'orange', dark: 'orange' },
      render: function (ctx, w, h, o) {
        var p = o && o.dark ? P.dark : P.light, k = unitScale(w, o);
        var cx = 0.62, cy = -0.62, R = 0.98, rimW = 0.9 * k / w;
        shade(ctx, w, h, function (x, y, c) {
          var dx = x - cx, dy = y - cy, d = Math.sqrt(dx * dx + dy * dy), e = d - R;
          var ang = Math.atan2(dy, dx); // 0 = right, pi/2 = straight down
          var warmth = clamp01(0.5 + Math.cos(ang - 1.25) * 1.4);
          var col = lerp3(p.cool, p.warm, warmth);
          // Strongest under the disc, fading round both sides of the arc.
          var bright = 0.25 + 0.75 * Math.pow(clamp01(Math.sin(ang)), 3);
          if (e < 0) {
            setTo(c, p.disc);
            mixInto(c, p.base, Math.exp(e / 0.05));
            mixInto(c, col, p.halo * 0.35 * Math.exp(e / 0.012) * bright);
          } else {
            setTo(c, p.base);
            mixInto(c, col, p.halo * bright * (0.65 * Math.exp(-e / 0.05) + 0.35 * Math.exp(-e / 0.22)));
          }
          var r = e / rimW;
          mixInto(c, col, p.rim * bright * Math.exp(-r * r));
        });
      }
    });
  })();

  // 6. Paper Cut: stacked arcs of cut card fanning from the top-right corner, each casting a soft
  //    shadow on the sheet behind it.
  (function () {
    // Layers front to back: centre (x, y) and radius in width units. Front layers are smaller.
    var L = [[0.80, -0.30, 0.50], [0.76, -0.33, 0.73], [0.82, -0.36, 0.95], [0.74, -0.40, 1.15], [0.79, -0.42, 1.33]];
    var OPTS = [
      { id: 'apricot', name: 'Apricot', swatch: { light: '#E9A27C', dark: '#6A3322' }, bubble: { light: 'teal', dark: 'orange' },
        light: { layers: ['#E39A74', '#ECB394', '#F2C8AF', '#F6DAC8', '#F9E7DB'].map(hex), base: hex('#FBF4EE'), sh: hex('#8A4B2E'), edge: hex('#FFF6EE') },
        dark: { layers: ['#7A3C26', '#5E2F1F', '#472419', '#341B14', '#251410'].map(hex), base: hex('#140D0B'), sh: hex('#000000'), edge: hex('#C77954') } },
      { id: 'tide', name: 'Tide', swatch: { light: '#7FAFC2', dark: '#1F4A5E' }, bubble: { light: 'orange', dark: 'teal' },
        light: { layers: ['#7FAFC2', '#9DC3D1', '#BAD5DF', '#D3E5EB', '#E6F0F3'].map(hex), base: hex('#F3F7F8'), sh: hex('#1F4A5E'), edge: hex('#F4FBFD') },
        dark: { layers: ['#1F4C61', '#183C4E', '#132F3E', '#0F2430', '#0B1A23'].map(hex), base: hex('#071118'), sh: hex('#000000'), edge: hex('#5FA2BC') } },
      { id: 'plum', name: 'Plum', swatch: { light: '#B48FB8', dark: '#4A2850' }, bubble: { light: 'yellow', dark: 'purple' },
        light: { layers: ['#B48FB8', '#C8A9CB', '#D9C2DB', '#E7D7E8', '#F1E8F1'].map(hex), base: hex('#F8F4F8'), sh: hex('#4A2850'), edge: hex('#FCF6FC') },
        dark: { layers: ['#55305C', '#43264A', '#341E3A', '#27172C', '#1C1120'].map(hex), base: hex('#110B13'), sh: hex('#000000'), edge: hex('#B07DB8') } }
    ];
    REG.push({
      id: 'papercut', name: 'Paper Cut', section: 'minimal', kind: 'canvas',
      blurb: 'Layers of cut card fanning from one corner, with soft real shadows.',
      bubble: OPTS[0].bubble, options: describe(OPTS),
      render: function (ctx, w, h, o) {
        var dark = !!(o && o.dark), p = pick(OPTS, o)[dark ? 'dark' : 'light'], k = unitScale(w, o);
        var px1 = k / w, sig = 7 * px1, sigWide = 26 * px1, off = 2.5 * px1;
        shade(ctx, w, h, function (x, y, c) {
          // Signed distance outside each disc (positive outside). The pixel belongs to the first
          // layer that contains it; the layer in front of that one casts the shadow.
          var j = L.length, dPrev = 0;
          for (var i = 0; i < L.length; i++) {
            var dx = x - L[i][0], dy = y - L[i][1];
            var e = Math.sqrt(dx * dx + dy * dy) - L[i][2];
            if (e < 0) { j = i; break; }
            dPrev = e;
          }
          setTo(c, j < L.length ? p.layers[j] : p.base);
          if (j > 0) {
            var s = dPrev - off;
            var sh = s < 0 ? 1 : 0.65 * Math.exp(-s / sig) + 0.35 * Math.exp(-s / sigWide);
            mixInto(c, p.sh, (dark ? 0.55 : 0.22) * sh);
            // The cut edge of the front card catches the light for about a point.
            var ed = dPrev / (0.9 * px1);
            mixInto(c, p.edge, (dark ? 0.35 : 0.6) * Math.exp(-ed * ed));
          }
        }, function (px, py) {
          return filmGrain(px, py, k, 77) * (dark ? 1.6 : 2.6);
        });
      }
    });
  })();

  // 7. Frost: coloured forms behind a pane of frosted glass. The nearer a form is to the glass, the
  //    sharper its edge; the frost scatters every edge into a fine sparkle.
  (function () {
    // Forms: type, geometry (width units), blur, colour index. 'c' circle(x, y, r); 'p' capsule(x0, y0, x1, y1, r).
    var F = [
      ['p', -0.10, 0.74, 0.30, 0.36, 0.12, 0.09, 1],
      ['c', 0.86, 0.17, 0.34, 0.006, 0],
      ['c', 0.52, 0.52, 0.07, 0.04, 2],
      ['c', 0.10, 2.44, 0.36, 0.11, 3]
    ];
    var OPTS = [
      { id: 'citrus', name: 'Citrus', swatch: { light: '#F2A65A', dark: '#B5622A' }, bubble: { light: 'yellow', dark: 'orange' },
        light: { base: hex('#F1F2F3'), base2: hex('#F4F1EE'), cols: ['#F2A65A', '#F5D27A', '#E8735A', '#F3BFA3'].map(hex), mix: 0.15 },
        dark: { base: hex('#0C0D10'), base2: hex('#100E0D'), cols: ['#B5622A', '#9E7A22', '#A23C2C', '#7A3D28'].map(hex), mix: 0.2 } },
      { id: 'lagoon', name: 'Lagoon', swatch: { light: '#5DB8B0', dark: '#1F6E78' }, bubble: { light: 'blue', dark: 'teal' },
        light: { base: hex('#F0F3F4'), base2: hex('#EEF2F4'), cols: ['#5DB8B0', '#8EC8EE', '#3E7FC8', '#A7DCCB'].map(hex), mix: 0.15 },
        dark: { base: hex('#0A0E11'), base2: hex('#0B0F14'), cols: ['#1F7A78', '#28588C', '#173F7A', '#1C5A4E'].map(hex), mix: 0.2 } },
      { id: 'orchid', name: 'Orchid', swatch: { light: '#B494E8', dark: '#5E3A9C' }, bubble: { light: 'pink', dark: 'purple' },
        light: { base: hex('#F3F1F5'), base2: hex('#F4F0F2'), cols: ['#B494E8', '#F2A9C6', '#7C6BD8', '#F4C7B6'].map(hex), mix: 0.15 },
        dark: { base: hex('#0D0B12'), base2: hex('#100B10'), cols: ['#5E3A9C', '#963A6A', '#3F348F', '#7A3F55'].map(hex), mix: 0.2 } }
    ];
    REG.push({
      id: 'frost', name: 'Frost', section: 'minimal', kind: 'canvas',
      blurb: 'Bright forms seen through frosted glass, sharp where they touch the pane.',
      bubble: OPTS[0].bubble, options: describe(OPTS),
      render: function (ctx, w, h, o) {
        var dark = !!(o && o.dark), p = pick(OPTS, o)[dark ? 'dark' : 'light'], H = h / w, k = unitScale(w, o);
        var jit = 1.6 * k / w, ix = 0, iy = 0;
        var cols = p.cols.map(function (cc) { return lerp3(cc, p.base, p.mix); });
        // Darker and lighter versions give each form a gentle roundness.
        var shadeCols = cols.map(function (cc) { return [cc[0] * (dark ? 0.55 : 0.78), cc[1] * (dark ? 0.55 : 0.78), cc[2] * (dark ? 0.55 : 0.78)]; });
        var hiCols = cols.map(function (cc) { return lerp3(cc, [1, 1, 1], dark ? 0.12 : 0.3); });
        shade(ctx, w, h, function (x, y, c) {
          setTo(c, p.base); mixInto(c, p.base2, y / H);
          // Frost: each pixel sees the scene from a slightly scattered point.
          var sx = x + (hash2(ix, iy, 3) - 0.5) * jit;
          var sy = y + (hash2(ix, iy, 5) - 0.5) * jit;
          for (var i = 0; i < F.length; i++) {
            var f = F[i], d, ux, uy, R, b, ci;
            if (f[0] === 'c') {
              ux = sx - f[1]; uy = sy - f[2]; R = f[3]; b = f[4]; ci = f[5];
              d = Math.sqrt(ux * ux + uy * uy) - R;
            } else {
              var ax = f[3] - f[1], ay = f[4] - f[2], qx = sx - f[1], qy = sy - f[2];
              var tt = clamp01((qx * ax + qy * ay) / (ax * ax + ay * ay));
              ux = qx - ax * tt; uy = qy - ay * tt; R = f[5]; b = f[6]; ci = f[7];
              d = Math.sqrt(ux * ux + uy * uy) - R;
            }
            var cov = smooth(b, -b, d);
            if (cov <= 0) continue;
            // Light from the top left: lighter on that side, deeper on the far side.
            var nl = clamp01(0.5 - (ux * 0.6 + uy * 0.8) / (R + b) * 0.5);
            var col = lerp3(shadeCols[ci], cols[ci], smooth(0.0, 0.6, nl));
            col = lerp3(col, hiCols[ci], smooth(0.6, 1.0, nl));
            mixInto(c, col, cov);
          }
          ix++; if (ix >= w) { ix = 0; iy++; }
        }, function (px, py) {
          // The frosted surface itself: a fine, even tooth over everything.
          return ((hash2(px, py, 12) - 0.5) * 1.2 + vnoise(px / (0.8 * k), py / (0.8 * k), 13) - 0.5) * (dark ? 2.2 : 3.2);
        });
      }
    });
  })();

  // 8. Riso: a two-colour print. Ink sky with a paper sun, the edges dissolving into grain.
  (function () {
    var OPTS = [
      { id: 'terracotta', name: 'Terracotta', swatch: { light: '#D06A4C', dark: '#7A3524' }, bubble: { light: 'orange', dark: 'orange' },
        light: { paper: hex('#F4EFE7'), ink: hex('#D06A4C') }, dark: { paper: hex('#121214'), ink: hex('#6E3021'), sun: hex('#C7744F') } },
      { id: 'cobalt', name: 'Cobalt', swatch: { light: '#4565AE', dark: '#22386E' }, bubble: { light: 'blue', dark: 'blue' },
        light: { paper: hex('#F2F0EA'), ink: hex('#4565AE') }, dark: { paper: hex('#0F1115'), ink: hex('#203566'), sun: hex('#7E9AD6') } },
      { id: 'forest', name: 'Forest', swatch: { light: '#3E7A62', dark: '#1E4638' }, bubble: { light: 'green', dark: 'green' },
        light: { paper: hex('#F1F1EA'), ink: hex('#3E7A62') }, dark: { paper: hex('#0F1311'), ink: hex('#1C4234'), sun: hex('#7FB89A') } }
    ];
    REG.push({
      id: 'riso', name: 'Riso', section: 'minimal', kind: 'canvas',
      blurb: 'A two-colour print: an ink sky and a paper sun, dissolving into grain.',
      bubble: OPTS[0].bubble, options: describe(OPTS),
      render: function (ctx, w, h, o) {
        var dark = !!(o && o.dark), p = pick(OPTS, o)[dark ? 'dark' : 'light'], k = unitScale(w, o);
        var ix = 0, iy = 0, gs = 1 / (1.0 * k);
        var inkSoft = lerp3(p.ink, p.paper, 0.18);
        shade(ctx, w, h, function (x, y, c) {
          // Ink coverage: solid at the top, a wavy lower edge, a round sun knocked out of it.
          var yb = 0.52 + 0.07 * Math.sin(x * 3.3 + 0.4) + 0.03 * Math.sin(x * 8.1 + 2.2) + 0.05 * (fbm3(x * 2.5, y * 2.5, 9) - 0.5);
          var g = (yb - y) / 0.26 + 0.5;
          var sx = x - 0.68, sy = y - 0.27, sd = Math.sqrt(sx * sx + sy * sy) - 0.15;
          var sunG = 0.5 - sd / 0.035;
          if (!p.sun) g = Math.min(g, 1 - sunG);
          // Stochastic screen: clumped noise threshold, the look of a stencil print.
          var n = vnoise(ix * gs, iy * gs, 51) * 0.7 + hash2(ix, iy, 52) * 0.3;
          var cov = smooth(n - 0.12, n + 0.12, g);
          // Ink is laid unevenly: slightly lighter where the drum ran thin.
          var even = vnoise(x * 5, y * 5, 53) * 0.7 + vnoise(x * 11, y * 11, 54) * 0.3;
          setTo(c, p.paper);
          mixInto(c, lerp3(p.ink, inkSoft, smooth(0.4, 0.85, even) * 0.25), cov);
          // On dark paper the sun is printed in a second, lighter ink.
          if (p.sun) mixInto(c, p.sun, smooth(n - 0.12, n + 0.12, sunG));
          ix++; if (ix >= w) { ix = 0; iy++; }
        }, function (px, py) {
          return filmGrain(px, py, k, 55) * (dark ? 1.4 : 2.4);
        });
      }
    });
  })();

  // 9. One Line: a single unbroken pen line drawing hills, a peak and the sun.
  (function () {
    // Catmull-Rom control points in design points on a 390-wide screen.
    var PTS = [
      [-30, 340], [22, 322], [58, 298], [86, 304], [106, 262], [132, 214], [158, 258], [176, 282],
      [204, 288], [232, 266], [240, 238], [226, 212], [202, 204], [176, 218], [166, 246], [180, 274],
      [206, 290], [236, 284], [252, 242], [276, 198], [302, 244], [320, 272], [348, 266], [374, 290], [424, 308]
    ];
    var OPTS = [
      { id: 'terracotta', name: 'Terracotta', swatch: { light: '#B65A3C', dark: '#D9B07A' }, bubble: { light: 'blue', dark: 'orange' },
        light: { paper: hex('#F6F2EB'), paper2: hex('#F2ECE3'), ink: '#B65A3C' }, dark: { paper: hex('#101319'), paper2: hex('#0C0E13'), ink: '#D9B07A' } },
      { id: 'ink', name: 'Ink', swatch: { light: '#2F3A4A', dark: '#C9D4E3' }, bubble: { light: 'yellow', dark: 'blue' },
        light: { paper: hex('#F5F4F0'), paper2: hex('#EFEEE9'), ink: '#2F3A4A' }, dark: { paper: hex('#0E1013'), paper2: hex('#0B0C0F'), ink: '#AEBBCC' } }
    ];
    function samplePath(sx, sy) {
      // Catmull-Rom (centripetal enough for this smooth set) sampled densely.
      var out = [];
      for (var i = 0; i < PTS.length - 1; i++) {
        var p0 = PTS[Math.max(0, i - 1)], p1 = PTS[i], p2 = PTS[i + 1], p3 = PTS[Math.min(PTS.length - 1, i + 2)];
        for (var s = 0; s < 24; s++) {
          var t = s / 24, t2 = t * t, t3 = t2 * t;
          var x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
          var y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
          out.push([x * sx, y * sy]);
        }
      }
      out.push([PTS[PTS.length - 1][0] * sx, PTS[PTS.length - 1][1] * sy]);
      return out;
    }
    REG.push({
      id: 'line', name: 'One Line', section: 'minimal', kind: 'canvas',
      blurb: 'One unbroken pen line: hills, a peak and the sun.',
      bubble: OPTS[0].bubble, options: describe(OPTS),
      render: function (ctx, w, h, o) {
        var dark = !!(o && o.dark), p = pick(OPTS, o)[dark ? 'dark' : 'light'], H = h / w, k = unitScale(w, o);
        shade(ctx, w, h, function (x, y, c) { setTo(c, p.paper); mixInto(c, p.paper2, smooth(0, H, y)); },
          function (px, py) { return filmGrain(px, py, k, 61) * (dark ? 1.0 : 1.8); });
        var sx = w / 390, sy = sx;
        var pts = samplePath(sx, sy);
        ctx.save();
        ctx.lineCap = 'round'; ctx.lineJoin = 'round';
        ctx.strokeStyle = p.ink; ctx.globalAlpha = dark ? 0.85 : 0.9;
        // Pressure: the pen swells on long strokes and thins on turns and at both ends.
        // Opaque short segments with round caps, composited as one layer so overlaps do not darken.
        var off = document.createElement('canvas'); off.width = w; off.height = h;
        var oc = off.getContext('2d');
        oc.lineCap = 'round'; oc.lineJoin = 'round'; oc.strokeStyle = p.ink;
        var n = pts.length;
        for (var i = 1; i < n; i++) {
          var u = i / n;
          var taper = smooth(0, 0.06, u) * (1 - smooth(0.94, 1, u));
          var swell = 0.8 + 0.35 * Math.sin(u * 17.0 + 0.6) * Math.sin(u * 5.3);
          oc.lineWidth = Math.max(0.5 * k, 1.9 * k * swell * (0.35 + 0.65 * taper));
          oc.beginPath(); oc.moveTo(pts[i - 1][0], pts[i - 1][1]); oc.lineTo(pts[i][0], pts[i][1]); oc.stroke();
        }
        ctx.drawImage(off, 0, 0);
        ctx.restore();
      }
    });
  })();

  // 10. Bauhaus: flat colour blocks on a four-column grid, kept to the top third.
  (function () {
    // Shapes in width units on a 0.25 grid. Colour keys: o ochre, r rust, s slate, g sage, b blush, k ink, p paper.
    var SH = [
      ['rect', 0, 0, 0.5, 0.25, 'o'],
      ['quarter', 0, 0.25, 0.25, 'r', 3],      // centre, radius, colour, quadrant (0 = down-right ... 3 = up-right)
      ['rect', 0.5, 0, 0.25, 0.25, 's'],
      ['circle', 0.625, 0.125, 0.075, 'p'],
      ['rect', 0.75, 0, 0.25, 0.5, 'g'],
      ['half', 0.75, 0.25, 0.25, 'o', 'right'],
      ['rect', 0.25, 0.25, 0.25, 0.25, 'b'],
      ['half', 0.375, 0.5, 0.125, 'k', 'up'],
      ['half', 0.125, 0.25, 0.125, 's', 'down'],
      ['half', 0.625, 0.25, 0.125, 'r', 'down'],
      ['half', 0.875, 0.5, 0.125, 'r', 'down'],
      ['stripes', 0.5, 0.25, 0.25, 0.25, 'k']
    ];
    var P = {
      light: { paper: hex('#F3EEE5'), o: hex('#E2B24E'), r: hex('#C95B3E'), s: hex('#3D5A7A'), g: hex('#A4B8A1'), b: hex('#EBC2B0'), k: hex('#2A2E35'), p: hex('#F3EEE5') },
      dark: { paper: hex('#101216'), o: hex('#A57B2C'), r: hex('#8E3F2B'), s: hex('#2C4766'), g: hex('#4F6552'), b: hex('#7B5246'), k: hex('#D9CFC0'), p: hex('#101216') }
    };
    function inside(s, x, y) {
      var dx, dy;
      switch (s[0]) {
        case 'rect': return x >= s[1] && x < s[1] + s[3] && y >= s[2] && y < s[2] + s[4] ? 1 : 0;
        case 'circle': dx = x - s[1]; dy = y - s[2]; return dx * dx + dy * dy < s[3] * s[3] ? 1 : 0;
        case 'quarter':
          dx = x - s[1]; dy = y - s[2];
          if (dx * dx + dy * dy >= s[3] * s[3]) return 0;
          return (s[5] === 3 && dx >= 0 && dy <= 0) ? 1 : 0;
        case 'half':
          dx = x - s[1]; dy = y - s[2];
          if (dx * dx + dy * dy >= s[3] * s[3]) return 0;
          if (s[5] === 'up') return dy <= 0 ? 1 : 0;
          if (s[5] === 'down') return dy >= 0 ? 1 : 0;
          if (s[5] === 'right') return dx >= 0 && x < 1 ? 1 : 0;
          return dx <= 0 ? 1 : 0;
        case 'stripes':
          if (!(x >= s[1] && x < s[1] + s[3] && y >= s[2] && y < s[2] + s[4])) return 0;
          return ((y - s[2]) / s[4] * 5) % 1 < 0.42 ? 1 : 0;
      }
      return 0;
    }
    REG.push({
      id: 'bauhaus', name: 'Bauhaus', section: 'minimal', kind: 'canvas',
      blurb: 'Flat blocks of colour on a strict grid, all in the top third.',
      bubble: { light: 'blue', dark: 'blue' },
      render: function (ctx, w, h, o) {
        var dark = !!(o && o.dark), p = dark ? P.dark : P.light, k = unitScale(w, o);
        var ss = 3, inv = 1 / (w * ss); // 3 x 3 supersampling keeps the arcs clean
        var acc = [0, 0, 0];
        shade(ctx, w, h, function (x, y, c) {
          if (y > 0.62) { setTo(c, p.paper); return; }
          var px0 = x - 0.5 / w, py0 = y - 0.5 / w;
          acc[0] = acc[1] = acc[2] = 0;
          for (var sy = 0; sy < ss; sy++) {
            for (var sx = 0; sx < ss; sx++) {
              var X = px0 + (sx + 0.5) * inv, Y = py0 + (sy + 0.5) * inv;
              var col = p.paper;
              for (var i = 0; i < SH.length; i++) if (inside(SH[i], X, Y)) col = p[SH[i][SH[i][0] === 'rect' || SH[i][0] === 'stripes' ? 5 : (SH[i][0] === 'circle' ? 4 : 4)]];
              acc[0] += col[0]; acc[1] += col[1]; acc[2] += col[2];
            }
          }
          c[0] = acc[0] / (ss * ss); c[1] = acc[1] / (ss * ss); c[2] = acc[2] / (ss * ss);
          // Ink is never perfectly flat on uncoated paper.
          var m = vnoise(x * 18, y * 18, 71);
          scaleInto(c, 0.985 + 0.03 * m);
        }, function (px, py) {
          return filmGrain(px, py, k, 73) * (dark ? 1.4 : 2.4);
        });
      }
    });
  })();

  // 11. Tints: one quiet colour per bubble colour, deeper than the bubble so it stands out, with grain.
  (function () {
    var T = [
      ['green', 'Green', '#C9E3CE', '#0B1A12'], ['teal', 'Teal', '#C3E1DB', '#081A19'], ['blue', 'Blue', '#CBD9EF', '#0B1626'],
      ['purple', 'Purple', '#D9CEEE', '#151022'], ['pink', 'Pink', '#EECDD9', '#1F0D17'], ['orange', 'Orange', '#F1D3BA', '#1E1209'],
      ['yellow', 'Yellow', '#EBE0B2', '#191508'], ['graphite', 'Graphite', '#D0D5DC', '#131519']
    ];
    var OPTS = T.map(function (r) {
      return { id: r[0], name: r[1], swatch: { light: r[2], dark: r[3] }, bubble: { light: r[0], dark: r[0] }, light: hex(r[2]), dark: hex(r[3]) };
    });
    REG.push({
      id: 'tints', name: 'Tints', section: 'minimal', kind: 'canvas',
      blurb: 'One calm colour, a shade deeper than your bubbles, with a fine grain.',
      bubble: OPTS[0].bubble, options: describe(OPTS),
      render: function (ctx, w, h, o) {
        var dark = !!(o && o.dark), base = pick(OPTS, o)[dark ? 'dark' : 'light'], H = h / w, k = unitScale(w, o);
        var hi = dark ? [base[0] * 1.7, base[1] * 1.7, base[2] * 1.7] : lerp3(base, [1, 1, 1], 0.25), lo = [base[0] * 0.88, base[1] * 0.88, base[2] * 0.88];
        shade(ctx, w, h, function (x, y, c) {
          setTo(c, base);
          // Daylight from the top: a touch lighter there, a touch deeper at the bottom corners.
          mixInto(c, hi, (1 - smooth(0, H * 0.6, y + x * 0.2)) * 0.6);
          mixInto(c, lo, smooth(H * 0.55, H * 1.1, y + Math.abs(x - 0.5) * 0.5) * 0.7);
        }, function (px, py) {
          return filmGrain(px, py, k, 81) * (dark ? 1.3 : 2.6);
        });
      }
    });
  })();

  // ================================================================ PATTERNS
  // Doodle icons live in a 32 x 32 box centred on the origin and are stroked with round caps and joins.

  function circle(c, x, y, r) { c.moveTo(x + r, y); c.arc(x, y, r, 0, Math.PI * 2); }
  function poly(c, pts, close) {
    c.moveTo(pts[0], pts[1]);
    for (var i = 2; i < pts.length; i += 2) c.lineTo(pts[i], pts[i + 1]);
    if (close) c.closePath();
  }
  function steam(c, x, y, hgt) {
    c.moveTo(x, y); c.bezierCurveTo(x - 2.2, y - hgt * 0.3, x + 2.2, y - hgt * 0.6, x, y - hgt);
  }
  function dot(c, x, y) { circle(c, x, y, 0.55); }
  function star5(c, x, y, R, r) {
    for (var i = 0; i < 10; i++) {
      var a = -Math.PI / 2 + i * Math.PI / 5, rr = i % 2 ? r : R;
      var px = x + Math.cos(a) * rr, py = y + Math.sin(a) * rr;
      if (i) c.lineTo(px, py); else c.moveTo(px, py);
    }
    c.closePath();
  }
  function sparkle(c, x, y, R) {
    c.moveTo(x, y - R);
    c.quadraticCurveTo(x, y, x + R, y); c.quadraticCurveTo(x, y, x, y + R);
    c.quadraticCurveTo(x, y, x - R, y); c.quadraticCurveTo(x, y, x, y - R); c.closePath();
  }
  function wave(c, x0, x1, y, amp, n) {
    var steps = n * 8, dx = (x1 - x0) / steps;
    c.moveTo(x0, y);
    for (var i = 1; i <= steps; i++) c.lineTo(x0 + i * dx, y - Math.sin(i / steps * n * Math.PI * 2) * amp);
  }

  // --- Brazil ---
  function frond(c, x0, y0, cx, cy, x1, y1, n, len) {
    c.moveTo(x0, y0); c.quadraticCurveTo(cx, cy, x1, y1);
    for (var i = 1; i <= n; i++) {
      var t = 0.12 + 0.8 * i / n, it = 1 - t;
      var px = it * it * x0 + 2 * it * t * cx + t * t * x1, py = it * it * y0 + 2 * it * t * cy + t * t * y1;
      var tx = 2 * it * (cx - x0) + 2 * t * (x1 - cx), ty = 2 * it * (cy - y0) + 2 * t * (y1 - cy);
      var tl = Math.sqrt(tx * tx + ty * ty); tx /= tl; ty /= tl;
      var L = len * (0.45 + 0.55 * Math.sin(Math.PI * (t * 0.9 + 0.05)));
      for (var s = -1; s <= 1; s += 2) {
        var nx = -ty * s, ny = tx * s;
        var ex = px + (nx * 0.8 + tx * 0.6) * L, ey = py + (ny * 0.8 + ty * 0.6) * L;
        c.moveTo(px, py); c.quadraticCurveTo(px + nx * L * 0.55 + tx * L * 0.05, py + ny * L * 0.55 + ty * L * 0.05, ex, ey);
      }
    }
  }
  // One ipê trumpet: a flared, frilled bell opening away from the twig at angle a.
  function trumpet(c, x, y, a, s) {
    var ca = Math.cos(a), sa = Math.sin(a);
    function P(u, v) { return [x + (u * ca - v * sa) * s, y + (u * sa + v * ca) * s]; }
    var q = [P(0, -1), P(5, -1.6), P(8.5, -4.2), P(9.6, -2.2), P(10.6, 0), P(9.6, 2.2), P(8.5, 4.2), P(5, 1.6), P(0, 1)];
    c.moveTo(q[0][0], q[0][1]);
    c.quadraticCurveTo(q[1][0], q[1][1], q[2][0], q[2][1]);
    c.quadraticCurveTo(q[3][0], q[3][1], q[4][0], q[4][1]);
    c.quadraticCurveTo(q[5][0], q[5][1], q[6][0], q[6][1]);
    c.quadraticCurveTo(q[7][0], q[7][1], q[8][0], q[8][1]);
    var m = P(6, 0), e = P(9.4, 0); c.moveTo(m[0], m[1]); c.lineTo(e[0], e[1]);
  }
  var BRAZIL = [
    function palm(c) { frond(c, -12, 13, -6, -6, 13, -11, 7, 8.5); },
    function toucan(c) {
      // Perched, facing right, with the big banana beak.
      c.moveTo(1.5, -8); c.bezierCurveTo(-3.5, -12, -9, -9, -8.5, -3); c.bezierCurveTo(-8, 3, -6.5, 7, -8.5, 13.5);
      c.moveTo(-8.5, 13.5); c.lineTo(-4.5, 10); c.bezierCurveTo(-1, 8, 2, 4, 2, -1);
      c.moveTo(1.5, -8); c.bezierCurveTo(7, -10.5, 12, -8, 14, -3); c.bezierCurveTo(10, -4.5, 6, -4.2, 2, -2.5);
      c.moveTo(2.2, -5.3); c.bezierCurveTo(6, -6.6, 10, -6, 13.2, -4.3);
      circle(c, -2.5, -5.5, 1.3);
      c.moveTo(-9, 9); c.lineTo(3, 9);
      c.moveTo(-5.5, -1); c.bezierCurveTo(-5.5, 3, -4, 6, -2, 7.5);
    },
    function caju(c) {
      // Cashew apple (bell) with the kidney-shaped nut hanging below it.
      c.moveTo(-4, -10.5); c.bezierCurveTo(-6.5, -6.5, -9.5, -2.5, -8.5, 1.5); c.quadraticCurveTo(-8, 3.5, -5.5, 3.5); c.lineTo(5.5, 3.5);
      c.quadraticCurveTo(8, 3.5, 8.5, 1.5); c.bezierCurveTo(9.5, -2.5, 6.5, -6.5, 4, -10.5); c.quadraticCurveTo(0, -12.5, -4, -10.5); c.closePath();
      c.moveTo(-2.5, 3.8); c.bezierCurveTo(-7.5, 5.5, -7.5, 13.5, -1.5, 13.5); c.bezierCurveTo(4, 13.5, 8, 10, 6.5, 6.5);
      c.bezierCurveTo(5.5, 4.5, 3, 5, 2, 7); c.bezierCurveTo(1.2, 8.5, -0.5, 8, -0.8, 6.5); c.lineTo(-0.8, 3.8);
      c.moveTo(0, -11.5); c.quadraticCurveTo(0.8, -13.8, 3.2, -14.4);
      c.moveTo(-4.5, -5); c.quadraticCurveTo(-5.4, -2, -4.6, 0.5);
    },
    function ipe(c) {
      // A twig with a cluster of three trumpet flowers, the way ipê blooms before its leaves.
      c.moveTo(-13, 13); c.quadraticCurveTo(-6, 6, -3, 1);
      c.moveTo(-6.5, 6.5); c.quadraticCurveTo(-10, 3, -11, -1);
      trumpet(c, -3, 1, -1.15, 1.05); trumpet(c, -3, 1, -0.2, 1.0); trumpet(c, -3, 1, 0.75, 0.9);
      dot(c, -11.5, -2.5);
    },
    function jangada(c) {
      // Jangada raft: low deck, tall mast and the big triangular sail curving in the wind.
      c.moveTo(-12, 8); c.lineTo(12, 8); c.lineTo(9.5, 11); c.lineTo(-10, 11); c.closePath();
      c.moveTo(-3, 8); c.lineTo(-3, -14);
      c.moveTo(-3, -13); c.quadraticCurveTo(9, -4, 10, 6); c.lineTo(-2, 6);
      c.moveTo(-3, -6); c.lineTo(5.5, -2.5);
      c.moveTo(-14, 14); c.quadraticCurveTo(-10.5, 12.5, -7, 14); c.moveTo(4, 14); c.quadraticCurveTo(7.5, 12.5, 11, 14);
    },
    function calcadao(c) {
      // A square of Copacabana pavement: two bold wave bands.
      c.moveTo(-12, -12); c.lineTo(12, -12); c.lineTo(12, 12); c.lineTo(-12, 12); c.closePath();
      for (var b = 0; b < 2; b++) {
        var y0 = -5 + b * 10;
        c.moveTo(-12, y0);
        c.bezierCurveTo(-7, y0 - 7, -5, y0 + 4, 0, y0 - 1);
        c.bezierCurveTo(5, y0 - 6, 7, y0 + 4, 12, y0);
      }
    },
    function sugarloaf(c) {
      // Pão de Açúcar and its neighbour, with the cable car line between them.
      c.moveTo(-14, 11); c.bezierCurveTo(-12, 0, -9, -4, -6, -4); c.bezierCurveTo(-3, -4, -2, 2, 0, 11);
      c.moveTo(-1, 11); c.bezierCurveTo(1, -6, 4, -12, 7.5, -12); c.bezierCurveTo(11, -12, 12.5, -4, 14, 11);
      c.moveTo(-14, 11); c.lineTo(14, 11);
      c.moveTo(-6, -4); c.quadraticCurveTo(0, -5, 7.5, -12);
      c.moveTo(1.2, -8.2); c.lineTo(1.2, -6.4); c.moveTo(-0.2, -6.4); c.lineTo(2.6, -6.4); c.lineTo(2.6, -4.4); c.lineTo(-0.2, -4.4); c.closePath();
    },
    function hammock(c) {
      // Striped rede hanging from fans of cords gathered at two hooks.
      c.moveTo(-9, -4); c.quadraticCurveTo(0, 3, 9, -4);
      c.moveTo(-8.5, -2.5); c.quadraticCurveTo(0, 7.5, 8.5, -2.5);
      c.moveTo(-8, -1); c.quadraticCurveTo(0, 12, 8, -1);
      c.moveTo(-9, -4); c.lineTo(-8, -1); c.moveTo(9, -4); c.lineTo(8, -1);
      for (var s = -1; s <= 1; s += 2) {
        circle(c, s * 14, -10, 1.1);
        c.moveTo(s * 13.4, -9); c.lineTo(s * 9, -4);
        c.moveTo(s * 13.4, -9); c.lineTo(s * 8.5, -2.5);
        c.moveTo(s * 13.4, -9); c.lineTo(s * 8, -1);
      }
    },
    function pandeiro(c) {
      circle(c, 0, 0, 12); circle(c, 0, 0, 8.5);
      for (var i = 0; i < 5; i++) {
        var a = -Math.PI / 2 + i * Math.PI * 2 / 5;
        c.moveTo(Math.cos(a) * 10.25 + 1.6, Math.sin(a) * 10.25); c.arc(Math.cos(a) * 10.25, Math.sin(a) * 10.25, 1.6, 0, Math.PI * 2);
      }
    },
    function coconut(c) {
      c.moveTo(-9, -2); c.bezierCurveTo(-11, 7, -5, 12, 0, 12); c.bezierCurveTo(5, 12, 11, 7, 9, -2);
      c.moveTo(-9, -2); c.bezierCurveTo(-4, -4.5, 4, -4.5, 9, -2); c.bezierCurveTo(4, 0.5, -4, 0.5, -9, -2);
      c.moveTo(1, -2.5); c.lineTo(5, -13); c.lineTo(9, -13);
    }
  ];
  var FILL_BRAZIL = [
    function (c) { circle(c, 0, 0, 2.2); },
    function (c) { star5(c, 0, 0, 4.4, 1.9); },
    function (c) { wave(c, -4.5, 4.5, 0, 1.3, 1); },
    function (c) { dot(c, -2.5, 0); dot(c, 2.5, 0); }
  ];

  // --- Canada ---
  var MAPLE_R = [0, -14, 2.4, -9.6, 5, -11, 4.4, -4.6, 9, -8.4, 9.8, -5.6, 13.4, -6.4, 11.8, -2.2, 13.4, -1, 7.2, 4.4, 8.2, 6.8, 1, 5.6, 1, 13];
  var CANADA = [
    function maple(c) {
      var n = MAPLE_R.length;
      c.moveTo(MAPLE_R[0], MAPLE_R[1]);
      for (var i = 2; i < n - 2; i += 2) c.lineTo(MAPLE_R[i], MAPLE_R[i + 1]);
      c.moveTo(MAPLE_R[0], MAPLE_R[1]);
      for (var j = 2; j < n - 2; j += 2) c.lineTo(-MAPLE_R[j], MAPLE_R[j + 1]);
      c.lineTo(-1, 5.6); c.lineTo(-1, 13); c.moveTo(1, 5.6); c.lineTo(1, 13);
      c.moveTo(-1, 13); c.lineTo(1, 13);
    },
    function canoe(c) {
      c.moveTo(-14, 2); c.quadraticCurveTo(0, 16, 14, 2); c.quadraticCurveTo(0, 7.5, -14, 2);
      c.moveTo(-14, 2); c.lineTo(-12.6, -0.2); c.moveTo(14, 2); c.lineTo(12.6, -0.2);
      c.moveTo(-11, -9); c.lineTo(3, -4.2);
      c.moveTo(3, -4.2); c.bezierCurveTo(5.5, -6.6, 10.5, -6.2, 12, -3.6); c.bezierCurveTo(10, -1.2, 5, -1.6, 3, -4.2);
    },
    function pine(c) {
      poly(c, [0, -14, -5.5, -5.5, -2.8, -5.5, -8.5, 2, -5, 2, -11, 9.5, 11, 9.5, 5, 2, 8.5, 2, 2.8, -5.5, 5.5, -5.5], true);
      c.moveTo(0, 9.5); c.lineTo(0, 13.5);
    },
    function loon(c) {
      c.moveTo(-13, 1.5); c.bezierCurveTo(-11, 7.5, 4, 8.5, 9.5, 4); c.bezierCurveTo(11.5, 2, 11.5, -0.5, 10.6, -2);
      c.moveTo(-13, 1.5); c.bezierCurveTo(-9, -1.5, -1, -2.5, 4, -1.5); c.bezierCurveTo(5, -4, 5, -7, 8, -7.6);
      c.bezierCurveTo(9.6, -7.9, 10.8, -7.2, 11.4, -6.2); c.lineTo(15, -5.2); c.lineTo(11.2, -4.4); c.lineTo(10.6, -2);
      c.moveTo(-6, 2.6); c.lineTo(-2, 2.6); c.moveTo(-3.5, 4.6); c.lineTo(0.5, 4.6);
      dot(c, 8.4, -5.6);
      c.moveTo(-14, 11); c.quadraticCurveTo(-11, 9.8, -8, 11); c.moveTo(3, 11); c.quadraticCurveTo(6, 9.8, 9, 11);
    },
    function snowflake(c) {
      for (var i = 0; i < 6; i++) {
        var a = i * Math.PI / 3 - Math.PI / 2, ca = Math.cos(a), sa = Math.sin(a);
        c.moveTo(0, 0); c.lineTo(ca * 12.5, sa * 12.5);
        var bx = ca * 7.5, by = sa * 7.5;
        for (var s = -1; s <= 1; s += 2) {
          var b = a + s * 0.75;
          c.moveTo(bx, by); c.lineTo(bx + Math.cos(b) * 3.8, by + Math.sin(b) * 3.8);
        }
      }
    },
    function mountains(c) {
      poly(c, [-14, 10, -4, -7, 3, 3.5]); poly(c, [0, -1, 5, -6, 14, 10]); c.moveTo(-14, 10); c.lineTo(14, 10);
      poly(c, [-7.5, -1.2, -5.5, 0.6, -3.6, -1.2, -1.3, 0.5]);
    },
    function mug(c) {
      c.moveTo(-9, -4); c.lineTo(-9, 9); c.quadraticCurveTo(-9, 12, -6, 12); c.lineTo(4, 12); c.quadraticCurveTo(7, 12, 7, 9); c.lineTo(7, -4); c.closePath();
      c.moveTo(7, -1); c.lineTo(10, -1); c.quadraticCurveTo(13, -1, 13, 2); c.lineTo(13, 4); c.quadraticCurveTo(13, 7.5, 7, 7.5);
      steam(c, -4.5, -7, 6); steam(c, 1.5, -7, 7);
    },
    function moon(c) {
      c.moveTo(4, -11.5); c.arc(-1, 0, 12.5, -1.16, 1.16, true); c.arc(5, 0, 9, 1.5, -1.5, false);
      sparkle(c, 9, -4, 2.4);
    },
    function inukshuk(c) {
      // Stacked-stone figure: legs, a long lintel for arms, a body block and a head stone.
      poly(c, [-7, 13, -7, 5, -3, 5, -3, 13], true); poly(c, [3, 13, 3, 5, 7, 5, 7, 13], true);
      poly(c, [-8, 5, 8, 5, 8, 1, -8, 1], true);
      poly(c, [-14, 1, 14, 1, 13, -3, -13, -3], true);
      poly(c, [-4, -3, 4, -3, 3.5, -8, -3.5, -8], true);
      poly(c, [-2.5, -8, 2.5, -8, 2, -12.5, -2, -12.5], true);
    }
  ];
  var FILL_CANADA = [
    function (c) { circle(c, 0, 0, 2.2); },
    function (c) { for (var i = 0; i < 3; i++) { var a = i * Math.PI / 3; c.moveTo(Math.cos(a) * 4, Math.sin(a) * 4); c.lineTo(-Math.cos(a) * 4, -Math.sin(a) * 4); } },
    function (c) { dot(c, -2.5, 0); dot(c, 2.5, 0); },
    function (c) { sparkle(c, 0, 0, 4.2); }
  ];

  // Irregular but even scatter: dart throwing with toroidal distance, so the tile repeats seamlessly.
  function scatter(seed, T, nBig, dBig, dSmall, nIcons, nFill) {
    var rnd = mulberry32(seed), pts = [];
    function far(x, y, minBig, minSmall) {
      for (var i = 0; i < pts.length; i++) {
        var dx = Math.abs(pts[i].x - x), dy = Math.abs(pts[i].y - y);
        if (dx > T / 2) dx = T - dx; if (dy > T / 2) dy = T - dy;
        var m = pts[i].big ? minBig : minSmall;
        if (dx * dx + dy * dy < m * m) return false;
      }
      return true;
    }
    var bag = [], tries = 0;
    function nextIcon() {
      if (!bag.length) { for (var i = 0; i < nIcons; i++) bag.push(i); for (var j = bag.length - 1; j > 0; j--) { var r = (rnd() * (j + 1)) | 0, tmp = bag[j]; bag[j] = bag[r]; bag[r] = tmp; } }
      return bag.pop();
    }
    while (pts.length < nBig && tries < 30000) {
      tries++;
      var x = rnd() * T, y = rnd() * T;
      if (far(x, y, dBig, dBig)) pts.push({ x: x, y: y, big: true, icon: nextIcon(), rot: (rnd() - 0.5) * 0.9, s: 0.9 + rnd() * 0.16 });
    }
    tries = 0;
    while (tries < 6000) {
      tries++;
      var fx = rnd() * T, fy = rnd() * T;
      if (far(fx, fy, dBig * 0.6, dSmall)) pts.push({ x: fx, y: fy, big: false, icon: (rnd() * nFill) | 0, rot: rnd() * 6.283, s: 0.85 + rnd() * 0.3 });
    }
    return pts;
  }

  var scatterCache = {};

  function patternWallpaper(def) {
    REG.push({
      id: def.id, name: def.name, section: 'patterns', kind: 'canvas', blurb: def.blurb, bubble: def.bubble,
      render: function (ctx, w, h, o) {
        var p = o && o.dark ? def.dark : def.light, H = h / w, k = unitScale(w, o);
        shade(ctx, w, h, function (x, y, c) { setTo(c, p.top); mixInto(c, p.bot, y / H); });
        var T = def.tile;
        var pts = scatterCache[def.id] || (scatterCache[def.id] = scatter(def.seed, T, def.nBig, def.dBig, def.dSmall, def.icons.length, def.fills.length));
        ctx.save();
        ctx.lineCap = 'round'; ctx.lineJoin = 'round';
        ctx.strokeStyle = p.ink;
        var tilePx = T * k, ox = def.offsetX * k;
        var nx = Math.ceil(w / tilePx) + 1, ny = Math.ceil(h / tilePx) + 1;
        for (var ty = -1; ty < ny; ty++) {
          for (var tx = -1; tx < nx; tx++) {
            var bx = ox + tx * tilePx, by = ty * tilePx;
            for (var i = 0; i < pts.length; i++) {
              var q = pts[i], X = bx + q.x * k, Y = by + q.y * k, sc = q.s * k * (q.big ? def.iconScale : def.fillScale);
              if (X < -40 * k || X > w + 40 * k || Y < -40 * k || Y > h + 40 * k) continue;
              ctx.setTransform(Math.cos(q.rot) * sc, Math.sin(q.rot) * sc, -Math.sin(q.rot) * sc, Math.cos(q.rot) * sc, X, Y);
              ctx.lineWidth = def.stroke * k / sc;
              ctx.beginPath();
              (q.big ? def.icons : def.fills)[q.icon](ctx);
              ctx.stroke();
            }
          }
        }
        ctx.restore();
      }
    });
  }

  patternWallpaper({
    id: 'doodle-brasil', name: 'Brasil', blurb: 'Ipê blossom, caju, jangadas, Sugarloaf and the waves of the calçadão.',
    bubble: { light: 'green', dark: 'green' },
    icons: BRAZIL, fills: FILL_BRAZIL, seed: 5521, tile: 360, nBig: 64, dBig: 50, dSmall: 22, offsetX: 17,
    iconScale: 1.1, fillScale: 0.95, stroke: 1.5,
    light: { top: hex('#EEF3E6'), bot: hex('#EAF0E1'), ink: '#C9D6BC' },
    dark: { top: hex('#0F1A14'), bot: hex('#0D1712'), ink: '#2B4033' }
  });

  patternWallpaper({
    id: 'doodle-canada', name: 'Canada', blurb: 'Maple leaves, canoes, loons, inukshuks and northern pines.',
    bubble: { light: 'blue', dark: 'blue' },
    icons: CANADA, fills: FILL_CANADA, seed: 3137, tile: 360, nBig: 64, dBig: 50, dSmall: 22, offsetX: 31,
    iconScale: 1.1, fillScale: 0.95, stroke: 1.5,
    light: { top: hex('#F5F2EC'), bot: hex('#F2EEE7'), ink: '#D3C6B8' },
    dark: { top: hex('#10141F'), bot: hex('#0E121C'), ink: '#2A3346' }
  });

  // Currents: quarter-arc truchet tiles that join into calm, endless curves.
  (function () {
    var P = {
      light: { top: hex('#F2EFF5'), bot: hex('#ECE8F1'), ink: 'rgba(95, 75, 140, 0.12)' },
      dark: { top: hex('#131118'), bot: hex('#121017'), ink: '#282536' }
    };
    REG.push({
      id: 'pattern-currents', name: 'Currents', section: 'patterns', kind: 'canvas',
      blurb: 'Endless arcs that meet and part like water.',
      bubble: { light: 'purple', dark: 'purple' },
      render: function (ctx, w, h, o) {
        var p = o && o.dark ? P.dark : P.light, H = h / w, k = unitScale(w, o);
        shade(ctx, w, h, function (x, y, c) { setTo(c, p.top); mixInto(c, p.bot, y / H); });
        // Each cell holds two quarter-arc ribbons (three parallel strands) in one of two orientations.
        // Orientation repeats every N cells, so the pattern tiles.
        var cell = 44 * k, N = 16;
        var cols = Math.ceil(w / cell) + 1, rows = Math.ceil(h / cell) + 1;
        var half = cell / 2, radii = [half - 0.2 * cell, half, half + 0.2 * cell];
        ctx.save();
        ctx.lineCap = 'butt'; ctx.strokeStyle = p.ink; ctx.lineWidth = 1.1 * k;
        ctx.beginPath();
        for (var j = 0; j < rows; j++) {
          for (var i = 0; i < cols; i++) {
            var x = i * cell, y = j * cell;
            var flip = hash2(i % N, j % N, 77) < 0.5;
            for (var ri = 0; ri < 3; ri++) {
              var r = radii[ri];
              if (flip) {
                ctx.moveTo(x + r, y); ctx.arc(x, y, r, 0, Math.PI / 2);
                ctx.moveTo(x + cell - r, y + cell); ctx.arc(x + cell, y + cell, r, Math.PI, Math.PI * 1.5);
              } else {
                ctx.moveTo(x + cell, y + r); ctx.arc(x + cell, y, r, Math.PI / 2, Math.PI);
                ctx.moveTo(x, y + cell - r); ctx.arc(x, y + cell, r, -Math.PI / 2, 0);
              }
            }
          }
        }
        ctx.stroke();
        ctx.restore();
      }
    });
  })();

  // Azulejo: an original blue-and-white tile with eightfold symmetry, glazed by hand, at whisper contrast.
  (function () {
    var P = {
      light: { base: hex('#F3F5F9'), glazeHi: hex('#F9FAFC'), glazeLo: hex('#EDF0F5'), grout: '#E4E9F0', ink: '#D2DCEC', wash: '#E6EBF3' },
      dark: { base: hex('#0D1322'), glazeHi: hex('#101829'), glazeLo: hex('#0B101D'), grout: '#080C16', ink: '#21304D', wash: '#141E33' }
    };
    var TILE = 100; // points
    // Motif in one octant of a tile centred on the origin, drawn under the 8 symmetries of the square.
    function octant(c, ink, wash) {
      c.fillStyle = ink; c.strokeStyle = ink; c.lineCap = 'round';
      // Centre: a small boss and eight petals, long on the axes and short on the diagonals.
      c.beginPath(); c.arc(0, 0, 2.6, 0, Math.PI * 2); c.fill();
      c.beginPath(); c.moveTo(5, 0); c.bezierCurveTo(9, -4.6, 15, -4.2, 19.5, 0); c.closePath(); c.fill();
      c.save(); c.rotate(Math.PI / 4);
      c.beginPath(); c.moveTo(5.5, 0); c.bezierCurveTo(8, -2.8, 11.5, -2.6, 14, 0); c.closePath(); c.fill();
      // Diagonal leaf reaching for the corner, with a curled tendril beside it.
      c.beginPath(); c.moveTo(27, 0); c.bezierCurveTo(30, -5.2, 35.5, -4.6, 39.5, 0); c.closePath(); c.fill();
      c.lineWidth = 1.1;
      c.beginPath(); c.moveTo(28, -3.2); c.bezierCurveTo(26.5, -9, 31, -12.5, 34.5, -10); c.stroke();
      c.beginPath(); c.arc(34.6, -8.8, 1.2, 0, Math.PI * 2); c.fill();
      c.restore();
      // Medallion ring.
      c.lineWidth = 1.2;
      c.beginPath(); c.arc(0, 0, 23, -0.01, Math.PI / 4 + 0.01); c.stroke();
      // Between ring and edge on the axis: a washed lens with a small leaf, meeting a bead on the tile edge.
      c.fillStyle = wash;
      c.beginPath(); c.moveTo(27, 0); c.bezierCurveTo(32, -8, 41, -8, 46, -6); c.lineTo(46, 0); c.closePath(); c.fill();
      c.fillStyle = ink;
      c.beginPath(); c.moveTo(29, 0); c.bezierCurveTo(32, -3, 36.5, -3, 40, 0); c.closePath(); c.fill();
      c.beginPath(); c.arc(46, 0, 3.4, Math.PI / 2, Math.PI * 1.5); c.fill();
      // Corner: a quarter of a ringed rosette; four tiles complete it.
      c.fillStyle = wash;
      c.beginPath(); c.moveTo(46, -46); c.arc(46, -46, 15, Math.PI / 2, Math.PI); c.closePath(); c.fill();
      c.fillStyle = ink;
      c.beginPath(); c.moveTo(46, -46); c.arc(46, -46, 7.5, Math.PI / 2, Math.PI); c.closePath(); c.fill();
      c.lineWidth = 1.1;
      c.beginPath(); c.arc(46, -46, 18.5, Math.PI / 2, Math.PI); c.stroke();
    }
    var cache = {};
    function tileCanvas(dark, k) {
      var key = (dark ? 'd' : 'l') + k;
      if (cache[key]) return cache[key];
      var p = dark ? P.dark : P.light;
      var s = Math.round(TILE * k);
      var cv = document.createElement('canvas'); cv.width = s; cv.height = s;
      var c = cv.getContext('2d');
      c.setTransform(s / TILE, 0, 0, s / TILE, s / 2, s / 2);
      for (var r = 0; r < 4; r++) {
        for (var m = 0; m < 2; m++) {
          c.save();
          c.rotate(r * Math.PI / 2);
          if (m) c.scale(1, -1);
          octant(c, p.ink, p.wash);
          c.restore();
        }
      }
      // Inner frame line and the grout.
      c.setTransform(s / TILE, 0, 0, s / TILE, 0, 0);
      c.strokeStyle = p.grout; c.lineWidth = 1.2;
      c.strokeRect(0.6, 0.6, TILE - 1.2, TILE - 1.2);
      return (cache[key] = cv);
    }
    REG.push({
      id: 'pattern-azulejo', name: 'Azulejo', section: 'patterns', kind: 'canvas',
      blurb: 'Hand-glazed blue and white tiles, drawn for Gua, at a whisper.',
      bubble: { light: 'blue', dark: 'blue' },
      render: function (ctx, w, h, o) {
        var dark = !!(o && o.dark), p = dark ? P.dark : P.light, k = unitScale(w, o);
        var tp = TILE * k, ox = ((w - tp * Math.floor(w / tp)) / 2) % tp - tp;
        var nx = Math.ceil(w / tp) + 2, ny = Math.ceil(h / tp) + 1;
        // Glaze: every tile catches the light a little differently.
        shade(ctx, w, h, function (x, y, c) {
          var X = x * w - ox, Y = y * w;
          var ti = Math.floor(X / tp), tj = Math.floor(Y / tp);
          var u = X / tp - ti, v = Y / tp - tj;
          var tone = hash2(ti, tj, 91);
          setTo(c, p.base);
          mixInto(c, tone > 0.5 ? p.glazeHi : p.glazeLo, Math.abs(tone - 0.5) * 0.9);
          // A soft sheen across each tile, as glaze pools thicker on one side.
          var sheen = (1 - u) * (1 - v) * 0.5 + vnoise(u * 3 + ti * 7, v * 3 + tj * 5, 92) * 0.5;
          mixInto(c, p.glazeHi, smooth(0.45, 0.9, sheen) * 0.6);
        }, function (px, py) { return (hash2(px, py, 93) - 0.5) * (dark ? 1.2 : 2.0); });
        var tc = tileCanvas(dark, k);
        ctx.save();
        ctx.globalCompositeOperation = dark ? 'lighten' : 'darken';
        for (var j = 0; j < ny; j++) for (var i = 0; i < nx; i++) ctx.drawImage(tc, Math.round(ox + i * tp), Math.round(j * tp), Math.round(tp), Math.round(tp));
        ctx.restore();
      }
    });
  })();

  // Calçadão: wave bands laid in small stones, the way Rio paves its beachfront, as a quiet repeat.
  (function () {
    var P = {
      light: { a: hex('#EFECE6'), b: hex('#E2DED6'), grout: hex('#E5E1DA'), j: 0.03 },
      dark: { a: hex('#1A1C21'), b: hex('#121418'), grout: hex('#0D0E11'), j: 0.06 }
    };
    REG.push({
      id: 'pattern-calcadao', name: 'Calçadão', section: 'patterns', kind: 'canvas',
      blurb: 'Bands of wave laid in small stones, like the Rio beachfront.',
      bubble: { light: 'teal', dark: 'teal' },
      render: function (ctx, w, h, o) {
        var dark = !!(o && o.dark), p = dark ? P.dark : P.light, k = unitScale(w, o);
        var cs = 5.2 * k / w; // stone size in width units
        var lambda = 0.5, period = 0.105, amp = 0.045;
        function band(x, y) {
          var th = x / lambda * Math.PI * 2;
          // Rounded crests, narrow troughs: the classic wave silhouette.
          var s = y + amp * (Math.sin(th) + 0.3 * Math.sin(2 * th - 1.2));
          return ((Math.floor(s / period) % 2) + 2) % 2;
        }
        shade(ctx, w, h, function (x, y, c) {
          var gx = x / cs, gy = y / cs, xi = Math.floor(gx), yi = Math.floor(gy);
          var d1 = 9, d2 = 9, fx = 0, fy = 0, fi = 0, fj = 0;
          for (var j = -1; j <= 1; j++) {
            for (var i = -1; i <= 1; i++) {
              var cx = xi + i, cy = yi + j;
              var px = cx + 0.15 + 0.7 * hash2(cx, cy, 1), py = cy + 0.15 + 0.7 * hash2(cx, cy, 2);
              var dx = gx - px, dy = gy - py, d = dx * dx + dy * dy;
              if (d < d1) { d2 = d1; d1 = d; fx = px; fy = py; fi = cx; fj = cy; } else if (d < d2) d2 = d;
            }
          }
          // Each stone takes the colour of the band its centre falls in, so edges are stepped like real mosaic.
          setTo(c, band(fx * cs, fy * cs) ? p.b : p.a);
          var jit = (hash2(fi, fj, 3) - 0.5) * 2 * p.j;
          scaleInto(c, 1 + jit);
          var edge = Math.sqrt(d2) - Math.sqrt(d1);
          mixInto(c, p.grout, 1 - smooth(0.04, 0.16, edge));
        }, function (px, py) { return (hash2(px, py, 97) - 0.5) * (dark ? 1.2 : 2.0); });
      }
    });
  })();

  // Terrazzo: chips of marble and glass set in pale cement, polished flat.
  (function () {
    var P = {
      light: { base: hex('#EFEBE5'), base2: hex('#ECE7E0'), chips: ['#E8CFC3', '#D5DDD0', '#D2D8E1', '#ECDFC2', '#DDD8D1', '#F7F5F1'], fleck: '#D9D3CB' },
      dark: { base: hex('#16171A'), base2: hex('#141518'), chips: ['#2A221F', '#1F2622', '#20252D', '#29251C', '#1D1E21', '#25262A'], fleck: '#1E1F23' }
    };
    function chip(c, rnd, x, y, r) {
      // An irregular, slightly concave-free shard: 5 to 8 vertices around an ellipse.
      var n = 5 + ((rnd() * 4) | 0), a0 = rnd() * 6.283, ex = 0.7 + rnd() * 0.6;
      for (var i = 0; i < n; i++) {
        var a = a0 + i / n * 6.283 + (rnd() - 0.5) * 0.6, rr = r * (0.7 + rnd() * 0.45);
        var px = x + Math.cos(a) * rr * ex, py = y + Math.sin(a) * rr / ex;
        if (i) c.lineTo(px, py); else c.moveTo(px, py);
      }
      c.closePath();
    }
    REG.push({
      id: 'pattern-terrazzo', name: 'Terrazzo', section: 'patterns', kind: 'canvas',
      blurb: 'Polished terrazzo: soft chips of stone and glass in pale cement.',
      bubble: { light: 'blue', dark: 'blue' },
      render: function (ctx, w, h, o) {
        var dark = !!(o && o.dark), p = dark ? P.dark : P.light, H = h / w, k = unitScale(w, o);
        shade(ctx, w, h, function (x, y, c) {
          setTo(c, p.base); mixInto(c, p.base2, y / H);
          mixInto(c, p.base2, smooth(0.4, 0.8, fbm3(x * 6, y * 6, 101)) * 0.8);
        }, function (px, py) { return (hash2(px, py, 103) - 0.5) * (dark ? 1.6 : 3.0); });
        var rnd = mulberry32(4242);
        var area = (w / k) * (h / k);
        var groups = [[area / 4200, 7, 12], [area / 700, 2.6, 5], [area / 110, 0.7, 1.5]];
        ctx.save();
        for (var g = 0; g < groups.length; g++) {
          var n = Math.round(groups[g][0]);
          var paths = p.chips.map(function () { return new Path2D(); });
          for (var i = 0; i < n; i++) {
            var ci = (rnd() * p.chips.length) | 0;
            chip(paths[ci], rnd, rnd() * w, rnd() * h, (groups[g][1] + rnd() * (groups[g][2] - groups[g][1])) * k);
          }
          for (var q = 0; q < paths.length; q++) { ctx.fillStyle = p.chips[q]; ctx.fill(paths[q]); }
        }
        ctx.restore();
      }
    });
  })();

  // Knit: a cabin sweater. Plain stockinette, with a band of maple leaves and peerie diamonds up top.
  (function () {
    // Chart rows, top to bottom. '.' base, 'a' main contrast, 'b' second contrast. Repeats across.
    var BAND = [
      { rep: 6, rows: ['bbbbbb'] },
      { rep: 6, rows: ['......'] },
      { rep: 6, rows: ['..b...', '.b.b..', 'b...b.', '.b.b..', '..b...'] },
      { rep: 6, rows: ['......', '......'] },
      { rep: 13, rows: [
        '......a......',
        '.....aaa.....',
        '...a.aaa.a...',
        '...aaaaaaa...',
        '.a..aaaaa..a.',
        '.aa.aaaaa.aa.',
        '..aaaaaaaaa..',
        '...aaaaaaa...',
        '....aaaaa....',
        '...aaa.aaa...',
        '......a......',
        '......a......'] },
      { rep: 6, rows: ['......', '......'] },
      { rep: 6, rows: ['..b...', '.b.b..', 'b...b.', '.b.b..', '..b...'] },
      { rep: 6, rows: ['......'] },
      { rep: 6, rows: ['bbbbbb'] }
    ];
    var CHART = [];
    BAND.forEach(function (b) { b.rows.forEach(function (r) { CHART.push(r); }); });
    var START_ROW = 8; // rows of plain knit above the band
    var P = {
      light: { base: hex('#EFE7DA'), a: hex('#D2A296'), b: hex('#A9B8A6'), dk: 0.8, hi: 1.05 },
      dark: { base: hex('#171A21'), a: hex('#4A2328'), b: hex('#22352F'), dk: 0.55, hi: 1.25 }
    };
    REG.push({
      id: 'pattern-knit', name: 'Knit', section: 'patterns', kind: 'canvas',
      blurb: 'A cabin sweater: soft stockinette with a band of maple leaves.',
      bubble: { light: 'blue', dark: 'blue' },
      render: function (ctx, w, h, o) {
        var dark = !!(o && o.dark), p = dark ? P.dark : P.light, k = unitScale(w, o);
        var cols = Math.max(20, Math.round(w / (10 * k)));
        var cw = 1 / cols, ch = cw * 0.86; // stitch cell in width units
        var colOff = Math.floor((cols - 13) / 2) % 13; // centre one maple leaf
        var cA = [0, 0, 0];
        shade(ctx, w, h, function (x, y, c) {
          var row = Math.floor(y / ch), col = Math.floor(x / cw);
          var u = x / cw - col - 0.5, v = y / ch - row; // u -0.5..0.5, v 0..1
          // Each stitch is a V of two legs. Pick the nearer leg.
          var side = u < 0 ? -1 : 1;
          var lx = u - side * 0.24, ly = v - 0.5;
          var ang = side * 0.55, ca = Math.cos(ang), sa = Math.sin(ang);
          var ax = lx * ca - ly * sa, ay = lx * sa + ly * ca;
          var e = Math.sqrt((ax / 0.27) * (ax / 0.27) + (ay / 0.62) * (ay / 0.62));
          // Colour from the chart.
          var key = '.';
          var cr = row - START_ROW;
          if (cr >= 0 && cr < CHART.length) {
            var line = CHART[cr], rep = line.length;
            key = line.charAt((((col - colOff) % rep) + rep) % rep);
          }
          var base = key === 'a' ? p.a : key === 'b' ? p.b : p.base;
          setTo(c, base);
          // Yarn roundness: bright along the leg's spine, shadowed in the gaps between legs.
          var round = clamp01(1 - e);
          var f = p.dk + (p.hi - p.dk) * Math.sqrt(round);
          // Twist of the plies along the leg.
          f *= 1 + 0.05 * Math.sin(ay * 40 + ax * 12) * round;
          scaleInto(c, f);
          if (e > 1) mixInto(c, [base[0] * p.dk * 0.85, base[1] * p.dk * 0.85, base[2] * p.dk * 0.85], smooth(1, 1.3, e));
        }, function (px, py) { return (hash2(px, py, 111) - 0.5) * (dark ? 1.4 : 2.6); });
      }
    });
  })();


  // Currents Live: the Currents arcs as a slow living surface. Light glints travel along the strands;
  // a send sets off a ripple that runs through the arcs from the sent bubble and lifts the light there.
  // Works in top-left coordinates like the canvas version. Send origin: the optional u_origin
  // extension shared with the Live section (GL pixels, (0,0) when the host does not set it); the
  // fallback is where the newest outgoing bubble sits, 83 % across and 11 % up from the bottom.
  REG.push({
    id: 'pattern-currents-live', name: 'Currents Live', section: 'patterns', kind: 'webgl',
    blurb: 'The Currents arcs, alive: glints drift along the strands and your message sends a ripple through them.',
    bubble: { light: 'purple', dark: 'purple' },
    frag: [
      'precision highp float;',
      'uniform vec2 u_res;',
      'uniform float u_time;',
      'uniform float u_dark;',
      'uniform float u_pulse;',
      'uniform vec2 u_tilt;',
      'uniform vec2 u_origin;',
      // Hash without Sine, David Hoskins, MIT licence.
      'float hash12(vec2 p){vec3 p3=fract(vec3(p.xyx)*0.1031);p3+=dot(p3,p3.yzx+33.33);return fract((p3.x+p3.y)*p3.z);}',
      'float wrap16(float x){return x-16.0*floor(x/16.0);}',
      'float strands(float d,float hc,float s){return min(min(abs(d-hc+s),abs(d-hc)),abs(d-hc-s));}',
      'void main(){',
      '  float k=u_res.x/390.0;',
      '  vec2 frag=vec2(gl_FragCoord.x,u_res.y-gl_FragCoord.y);',
      '  vec2 og=(u_origin.x+u_origin.y>0.5)?u_origin:vec2(u_res.x*0.83,u_res.y*0.113);',
      '  vec2 o=vec2(og.x,u_res.y-og.y);',
      '  vec2 dv=frag-o;',
      '  float dist=length(dv);',
      '  float R=0.45*u_res.x;',
      '  float near=exp(-(dist*dist)/(R*R));',
      // Ripple: rings running outward while the pulse is up, bending the strands radially.
      '  float ring=sin(dist/(11.0*k)-u_time*7.0);',
      '  float env=u_pulse*exp(-dist/R);',
      '  vec2 p=frag+(dv/max(dist,1.0))*ring*env*6.0*k+u_tilt*6.0*k+vec2(u_time*0.8*k,u_time*0.35*k);',
      '  float cell=44.0*k;',
      '  vec2 id=floor(p/cell);',
      '  vec2 f=p-id*cell;',
      '  float h=hash12(vec2(wrap16(id.x),wrap16(id.y))+0.5);',
      '  float fl=step(h,0.5);',
      '  vec2 c1=mix(vec2(cell,0.0),vec2(0.0),fl);',
      '  vec2 c2=mix(vec2(0.0,cell),vec2(cell),fl);',
      '  float hc=0.5*cell, s=0.2*cell;',
      '  float e1=strands(length(f-c1),hc,s);',
      '  float e2=strands(length(f-c2),hc,s);',
      '  float e=min(e1,e2);',
      '  float cov=clamp(0.55*k+0.5-e,0.0,1.0);',
      // Glints: a slow travelling highlight on each arc, phase-shifted per cell so nothing marches in step.
      '  vec2 v=mix(f-c2,f-c1,step(e1,e2));',
      '  float ang=atan(v.y,v.x);',
      '  float g=0.5+0.5*sin(ang*4.0+h*40.0+u_time*0.32+(id.x+id.y)*1.7);',
      '  float glint=smoothstep(0.72,1.0,g);',
      '  vec3 top,bot,line,hi,glow;',
      '  if(u_dark>0.5){',
      '    top=vec3(0.0745,0.0667,0.0941); bot=vec3(0.0706,0.0627,0.0902);',
      '    line=vec3(0.1569,0.1451,0.2118); hi=vec3(0.2588,0.2314,0.4000); glow=vec3(0.4510,0.3725,0.7451);',
      '  } else {',
      '    top=vec3(0.9490,0.9373,0.9608); bot=vec3(0.9255,0.9098,0.9451);',
      '    line=vec3(0.8706,0.8471,0.9098); hi=vec3(0.7765,0.7333,0.8824); glow=vec3(0.8196,0.7451,0.9608);',
      '  }',
      '  vec3 base=mix(top,bot,frag.y/u_res.y);',
      '  vec3 lc=mix(line,hi,clamp(glint*0.6+env*1.2,0.0,1.0));',
      '  vec3 col=mix(base,lc,clamp(cov*(1.0+env*1.5),0.0,1.0));',
      // The send lifts the light around the bubble by about 12 %, fading with the pulse.
      '  col=mix(col,glow,mix(0.28,0.16,u_dark)*u_pulse*near);',
      '  float n=hash12(gl_FragCoord.xy)+hash12(gl_FragCoord.yx+17.17)-1.0;',
      '  gl_FragColor=vec4(col+n*(1.5/255.0),1.0);',
      '}'
    ].join('\n')
  });

  // Exposed for the icon review page only; the app never reads it.
  window.GUA_PATTERN_ICONS = { brasil: BRAZIL, canada: CANADA, fillBrasil: FILL_BRAZIL, fillCanada: FILL_CANADA };
})();

