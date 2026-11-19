# Live wallpaper shaders

The live artworks in `../artworks.json` are GLSL ES 1.00 shaders in the Lab modules (`../lab/`). This folder ports them to the apps and checks the ports.

| File | Does |
|---|---|
| `generate.mjs` | Writes `<id>.msl` into gua-ios `ElementX/Resources/Wallpapers/` and `<id>.agsl` into gua-android `libraries/matrixui/src/main/assets/wallpapers/`. |
| `render-goldens.mjs` | Renders the Lab GLSL in headless Chromium WebGL into `goldens/`. |
| `validate-msl.swift` | Compiles each `.msl` at runtime with Metal, renders the golden cases and compares. |
| `check-agsl.mjs` | Lints each `.agsl` against the AGSL rules, then renders it as GLSL in Chromium and compares. |
| `check-agsl-device.mjs`, `AgslDeviceCheck.java` | Runs each `.agsl` through Android's own `RuntimeShader` on a connected Android 13+ device or emulator, sets every uniform and, on Android 14+, draws the golden cases on the GPU and compares. |
| `harness.mjs` | Golden cases, the Chromium renderer, PNG files, the comparison and its limits, shared by the scripts above. |

## Commands

From the gua-android checkout, with gua-ios checked out next to it:

```sh
node tools/wallpapers/shaders/generate.mjs --ios ../gua-ios --android .
node tools/wallpapers/shaders/render-goldens.mjs --playwright <node_modules holding playwright-core>
swift tools/wallpapers/shaders/validate-msl.swift ../gua-ios/ElementX/Resources/Wallpapers
node tools/wallpapers/shaders/check-agsl.mjs --playwright <node_modules holding playwright-core>
node tools/wallpapers/shaders/check-agsl-device.mjs [--serial <adb serial>]
```

`--lab` takes the Lab page or a folder of module files; the default is `../lab/`. Nothing is installed for these scripts: Playwright comes from any existing install (`--playwright` or `GUA_PLAYWRIGHT`) with its cached Chromium. `validate-msl.swift` needs a Mac with Metal, not the Metal toolchain. `check-agsl-device.mjs` needs a JDK (17 or later) and the Android SDK (`--sdk`, `ANDROID_HOME` or the default SDK folder); it compiles `AgslDeviceCheck.java` with `javac` and `d8`, runs it with `app_process` from `/data/local/tmp` without installing an APK, and removes it afterwards. Start the emulator first, for example `emulator -avd <API 34+ AVD> -no-window -read-only -no-snapshot-save`, and stop it when done.

## Uniforms

The Lab contract (spec 6.1). The shaders work in GL coordinates: origin bottom-left, y up.

| Name | Type | Meaning |
|---|---|---|
| `u_res` | float2 | Render target size in pixels. |
| `u_time` | float | `(still_time + seconds since the wallpaper appeared) mod 1800`, with the artwork's `still_time` from `manifest.json`. Starting at the still's time rather than 0 refines the shared spec (6.1) so the first live frame is the still it replaces. Previews with frozen time use `still_time`. |
| `u_dark` | float | 1 for the dark variant, 0 for light. |
| `u_pulse` | float | Send envelope, 0 when idle. For `dt` seconds after the pulse starts: `a = min(1, dt/0.25)`, `pulse = a*a*(3-2a) * exp(-max(0, dt-0.25)/0.9)`, and 0 from `dt > 4.2`. A send starts it at `now - 0.25*(0.5 - sin(asin(1 - 2v)/3))`, `v` the current value, so a send while it plays never makes it jump (spec 6.5). |
| `u_tilt` | float2 | Device tilt in the interface's frame, each component in -1..1: (1,0) is the viewer to the right of the screen, (0,1) above it (spec 6.6). (0,0) wherever tilt does not run. |
| `u_origin` | float2 | Centre of the sent bubble in render-target pixels: the same space as `u_res` and `gl_FragCoord`/`fragCoord` (on iOS the half-size drawable, not points or view pixels), with y measured from the bottom, so `y = height - y_top_left`. (0,0) means each shader's default spot near the newest outgoing bubble. |

## iOS (MSL)

- Compile `<id>.msl` with `MTLDevice.makeLibrary(source:options:)` where `options.mathMode = .safe` and `options.mathFloatingPointFunctions = .precise`. Fast math changes the result by up to about 13 levels (see Goldens).
- Vertex function `gua_wallpaper_vertex`: a full-screen triangle from `vertex_id`. Draw `.triangle` with 3 vertices and no vertex buffer.
- Fragment function `gua_wallpaper_fragment`: returns the colour, alpha 1.
- Colour attachment: an 8-bit non-sRGB format (`.bgra8Unorm` or `.rgba8Unorm`), so values are stored as computed, as in WebGL. The shaders dither for 8 bits.
- Fragment buffer index 0 holds `GuaWallpaperUniforms`, 40 bytes:

| Offset | Field | Type |
|---|---|---|
| 0 | `u_res` | float2 |
| 8 | `u_tilt` | float2 |
| 16 | `u_origin` | float2 |
| 24 | `u_time` | float |
| 28 | `u_dark` | float |
| 32 | `u_pulse` | float |
| 36 | padding | float |

```swift
struct GuaWallpaperUniforms {
    var res: SIMD2<Float>
    var tilt: SIMD2<Float>
    var origin: SIMD2<Float>
    var time: Float
    var dark: Float
    var pulse: Float
    var pad: Float = 0
}
// encoder.setFragmentBytes(&uniforms, length: MemoryLayout<GuaWallpaperUniforms>.stride, index: 0)
```

How the port works: every Lab function becomes a member of one struct, together with the uniforms, `gl_FragCoord` and `gl_FragColor`, so the GLSL bodies compile unchanged. `vec2`/`vec3`/`vec4`/`mat2` are typedefs, and members shadow the built-ins whose Metal meaning differs: `mod` (floored), two-argument `atan`, and `smoothstep` (the Lab uses reversed edges). The fragment entry sets `gl_FragCoord.y = u_res.y - position.y`.

## Android (AGSL)

- `RuntimeShader(source)` on API 33 and up, source read from `assets/wallpapers/<id>.agsl`.
- Uniforms, set with `setFloatUniform`: `u_res` (float2), `u_time` (float), `u_dark` (float), `u_pulse` (float), `u_tilt` (float2), `u_origin` (float2). No child shaders.
- `half4 main(float2 fragCoord)`: `fragCoord` must be in the same pixel space as `u_res`, origin top-left, so set `u_res` to the size of the area the shader brush is drawn over. `main` flips y itself.

How the port works: type names become `float2`/`float3`/`float4`/`float2x2`, `smoothstep` becomes `gua_smoothstep` (explicit formula, so reversed edges behave the same on every GPU), helpers that read `gl_FragCoord` take it as their first parameter, and the Lab `main` body is inlined into `main(fragCoord)`.

## Goldens

`<id>-<light|dark>-t<0|30>-p<0|60>.png`: each case is rendered at 180x390 with `u_time` 0 or 30, `u_pulse` 0 or 0.6, tilt and origin (0,0), and stored after a 2x box downsample (90x195, each channel rounded half up). A check renders at 180x390, downsamples its own image the same way and compares: mean absolute difference at most 1.5 and maximum at most 10 (8-bit levels, RGB).

They are rendered with Chromium's SwiftShader backend: IEEE float on the CPU, identical on every machine, and what Metal with fast math off reproduces. GPUs that fuse multiply-adds (ANGLE on Metal in Chrome on a Mac, Metal with fast math, the Mac's GPU under the Android emulator) differ by up to 13 levels in small areas.

Two shaders have an allowance, the same in `harness.mjs` and `validate-msl.swift`: maximum 16 but mean at most 0.1. Both differences are thin features that move by under a pixel with float rounding; a broken port moves the mean far more.

- Molten Pearl: 4 or 5 single pixels on the thin specular crests, whose position comes from a finite-difference gradient. It reaches 10 even with fast math off.
- Ipê: the rim of one bokeh disc, whose radius comes from `hash22` of a constant. The hash's last bits depend on how the GPU rounds, so on GPUs that fuse multiply-adds the rim moves and reaches 13.

A value that switches a whole feature on or off, picks one of several discrete choices or places a feature must not come from `hash12` or `hash22`. Their output is the `fract` of a large product, so the one-ulp difference of a fused multiply-add can flip a threshold or wrap the value between 0 and 1, and the live view then pops against the still. Metal fuses even with fast math off, and AGSL cannot turn fusion off.

Sea Sparks and Boreal take every such value from `ih`, `ih1` and `iv`, in which every value is a whole number below 2^20: in Sea Sparks the night sparks, the send flare, the daytime glints and the noise that breaks the swell; in Boreal the stars, snowflakes, spruces, the snow on their tiers, the aurora's ray noise and the mask that dims parts of the band. fp32 holds those numbers exactly, so fused and unfused multiply-adds agree and both shaders get the normal limits on every GPU.

- `floor((x+0.5)*(1.0/1019.0))` is exact: the product is within 1e-4 of a quotient that is at least 1/2038 from a whole number.
- Decisions read from `h = n/1019` (on or off, the stars' twinkle rate and size) sit at least 2e-4 from their boundary, and positions are `fract(n*K)` with each `K` keeping the product at least 9e-4 from a whole number, so neither can flip or wrap.
- Boreal's ray noise reduces its lattice index with a half-cell margin, so a division a few ulps off cannot pick the wrong cell.
- A contour that depends on position, such as Sea Sparks' daytime glint threshold or the hard edge between two of a spruce's tiers, can still move by under a pixel.

## Checks run

- MSL on this Mac's GPU (fast math off): all cases within the limits; Molten Pearl max 10, every other shader max 2 or less, means 0.014 or less.
- AGSL through Android's `RuntimeShader` on the API 35 emulator (`check-agsl-device.mjs`): every file compiles, all six uniforms are accepted (including `u_tilt` in Rain on Glass, which never reads it) and every case draws. With the emulator's SwiftShader GPU the drawings equal the goldens (max 0). With the Mac's GPU under the emulator: Ipê max 13 (allowance), Molten Pearl 10, Rain on Glass 7, the rest 2 or less, means 0.052 or less.

## What is not verified

- No physical Android GPU (Mali, Adreno and others) has drawn the AGSL files. Expect differences like the Mac GPU's above, in the same small areas.
- Metal results come from this Mac's GPU. iPhone GPUs may round differently; the golden comparison on the simulator or a device will show it.
