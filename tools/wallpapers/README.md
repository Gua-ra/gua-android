# Chat wallpapers

`bake.mjs` turns the wallpapers picked in the Gua Wallpaper Lab into app assets for gua-android and gua-ios.

## What it writes

Into gua-android `libraries/matrixui/src/main/assets/wallpapers/` and gua-ios `ElementX/Resources/Wallpapers/`, with the same file names:

| File | What |
|---|---|
| `<id>-light.webp`, `<id>-dark.webp` | Every artwork in `artworks.json`, 1290x2796, WebP `-q 80 -m 6`, no grain. Live artworks get a still frame of their shader. |
| `<id>.msl` (iOS), `<id>.agsl` (Android) | Live artworks only, from `shaders/generate.mjs`, when that folder is present. |
| `noise.webp` | 128x128 grey grain that tiles, lossless, for the apps to draw over the art. |
| `manifest.json` | Byte-identical on both platforms: per artwork `id`, `section`, `live`, `files`, `sha256`, `anchor`, `fallback_preset`, `bubble` and, for live ones, `still_time`; plus the noise tile and the generator version. Live artworks list both `files.msl` and `files.agsl`, but the `.msl` file ships only on iOS and the `.agsl` only on Android, so each platform checks only its own. |

It prints a size table and fails, writing nothing, if either platform goes over 5 MB.

## Running it

From the gua-android checkout, with gua-ios next to it:

```sh
node tools/wallpapers/bake.mjs --android . --ios ../gua-ios \
  --playwright-core <node_modules>/playwright-core --cwebp /usr/local/bin/cwebp
```

- `--images-only` leaves the shader ports out of the run and the manifest, and warns about any `.msl` or `.agsl` files still in the asset folders: they must not be committed with that manifest. A full run writes them and lists them with their `sha256`.
- `--only <id,id> --png <dir>` renders a few artworks as PNG for review and writes nothing else.
- `--playwright-core` (or `PLAYWRIGHT_CORE`) points at any existing install with its cached Chromium; nothing is installed.

Pinned tools, so a re-bake is byte-identical: Node 22, playwright-core 1.54.2 with Chromium 139.0.7258.5 (WebGL on SwiftShader), cwebp 1.6.0. Another cwebp stops the bake; another Chromium only warns.

## Sources

- `lab/`: the Lab page's wallpaper modules, copied verbatim. To update, copy the whole script again.
- `artworks.json`: the picked artworks in gallery order. `id` is the wire id, `lab` the Lab id, `option` the picked colour option, `anchor` the point of the image that stays on screen when it is cropped, `still_time` the shader time of a live still (the live view starts its clock there, see `shaders/README.md`).
- `fallback_preset` is pinned once shipped. A new artwork without one gets the original-ten preset whose light fill is nearest in OKLab hue to the light image's mean colour, or graphite when that colour is too grey to have a hue (chroma under 0.005). Full OKLab distance would always pick the palest preset, since the presets share one lightness and most artworks are much paler. The bake prints a note if a pinned one is no longer the nearest.

## Where the bake differs from the Lab

`bake.mjs` patches the Lab modules as they load. With its flags off, a render at the Lab's preview size (794x1720) matches the Lab page's own preview pixel for pixel.

- Grain: the Lab's film grain is left out, since lossy WebP smears it and the apps draw `noise.webp` at runtime. Nature scenes keep the slight lift their grain gives dark skies. Paper keeps its tooth (`lab_texture`), which is part of the paper and survives q80.
- Stars: Nature counts stars by the canvas's pixel area, so the bake counts them as the Lab's preview does.
- Crescent moons: the Lab cuts the shadow out of the whole canvas, which leaves a hole. The bake paints the sky through the shadow instead.
- Pantanal (`lab_size`): its fireflies are sized in device pixels, so it is drawn at the Lab's preview size and scaled up.
