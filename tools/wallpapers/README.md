# Chat wallpapers

`bake.mjs` turns the wallpapers picked in the Gua Wallpaper Lab, and the photo wallpapers in `photos.json`, into app assets for gua-android and gua-ios.

## What it writes

Into gua-android `libraries/matrixui/src/main/assets/wallpapers/` and gua-ios `ElementX/Resources/Wallpapers/`, with the same file names:

| File | What |
|---|---|
| `<id>-light.webp`, `<id>-dark.webp` | Every artwork in `artworks.json`, 1290x2796, WebP `-q 80 -m 6`, no grain. Live artworks get a still frame of their shader. |
| `<id>.msl` (iOS), `<id>.agsl` (Android) | Live artworks only, from `shaders/generate.mjs`, when that folder is present. |
| `noise.webp` | 128x128 grey grain that tiles, lossless, for the apps to draw over the art. |
| `<id>.webp` | Every photo in `photos.json`, 1290x2796, WebP `-q 82 -m 6`, one file for light and dark. See [Photo wallpapers](#photo-wallpapers). |
| `manifest.json` | Byte-identical on both platforms: per artwork `id`, `section`, `live`, `files`, `sha256`, `anchor`, `fallback_preset`, `bubble` and, for live ones, `still_time`; plus the noise tile and the generator version. Photo entries, right after Nature, also carry a `credit` (`author`, `licence`, `licence_url`, `source_url`, `modified`). Live artworks list both `files.msl` and `files.agsl`, but the `.msl` file ships only on iOS and the `.agsl` only on Android, so each platform checks only its own. |

It prints a size table and fails, writing nothing, if the art of either platform goes over 5 MB.

## Running it

From the gua-android checkout, with gua-ios next to it:

```sh
node tools/wallpapers/bake.mjs --android . --ios ../gua-ios \
  --playwright-core <node_modules>/playwright-core --cwebp /usr/local/bin/cwebp
```

- `--images-only` leaves the shader ports out of the run and the manifest, and warns about any `.msl` or `.agsl` files still in the asset folders: they must not be committed with that manifest. A full run writes them and lists them with their `sha256`.
- `--only <id,id> --png <dir>` renders a few artworks as PNG for review and writes nothing else.
- `--playwright-core` (or `PLAYWRIGHT_CORE`) points at any existing install with its cached Chromium; nothing is installed.
- The art bake keeps the photo entries of the manifest it replaces.

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

## Photo wallpapers

The photo wallpapers are not under the AGPL. Each keeps its own licence, CC0 1.0 or CC BY 4.0, as listed in `THIRD_PARTY_NOTICES.md`; the legal code of both is in `licenses/`. Smiling Maned Wolf is the exception: its author licensed it to Gua alone, all rights reserved, on the terms in `licenses/LicenseRef-Gabriel-Bonfa-Photo.txt`, which its `licence_url` points to. Originals never go in a repository.

```sh
node tools/wallpapers/bake.mjs --photos <originals> --android . --ios ../gua-ios --cwebp /usr/local/bin/cwebp
```

- No browser is needed. `dwebp` and `webpmux` must sit next to `cwebp`, all three at 1.6.0.
- `<originals>/<id>/` holds exactly one file, the original, and the bake stops if its sha256 differs from `photos.json`.
- Each photo is cropped upright, after applying the JPEG's EXIF orientation, and resized to 1290x2796. A photo with an ICC profile is converted to sRGB (relative colorimetric, matrix/TRC profiles only) and the file keeps nothing else from the original: no profile, no EXIF, no GPS. An XMP packet adds `dc:creator`, `dc:rights`, `xmpRights:WebStatement` (the licence URL) and, when there is one, `dc:source`.
- Only the photo entries of `manifest.json` change; every art entry stays byte for byte. The bake fails, writing nothing, over 8 MB of photos per platform.
- In dark mode the apps dim a photo as much as a user's own photo at its default.

`photos.json` lists the photos in gallery order. `names` are the gallery names in each app language. `author`, `licence`, `licence_url` and `source_url` (null for a photo with no public page) form the credit, and `licence_note` says why a licence applies when the source page does not show it. `original` is the sha256 and upright size of the original, `crop` a rectangle in its upright pixels. `anchor`, `bubble` and `fallback_preset` work as for the art; a new photo without `fallback_preset` gets one by the same hue rule, from the mean colour of its WebP.

To fetch the originals again (Wikimedia asks scripts for a descriptive User-Agent, so pass one with `-A`):

```sh
node -e 'for (const p of require("./tools/wallpapers/photos.json").photos) if (p.original_url) console.log(p.id, p.original_url)' |
  while read -r id url; do mkdir -p "originals/$id" && curl -fL -A '<tool name and contact>' -o "originals/$id/${url##*/}" "$url"; done
```

Photos with no `original_url` come from the photographer's own files and are not published. cwebp cannot read HEIC, so a HEIC original is converted to PNG first and `original` pins that PNG.
