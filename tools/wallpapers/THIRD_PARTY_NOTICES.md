# Third-party notices

## Hash without Sine

`hash12` and `hash22` in the live wallpaper shaders are adapted from "Hash without Sine" by David Hoskins (https://www.shadertoy.com/view/4djSRW). They appear in the Lab modules in `lab/` and in the shader files generated from them by `shaders/generate.mjs`: `ElementX/Resources/Wallpapers/*.msl` in gua-ios and `libraries/matrixui/src/main/assets/wallpapers/*.agsl` in gua-android. Each generated file carries the notice below.

```
Copyright (c) 2014 David Hoskins.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Photo wallpapers

The photo wallpapers, `libraries/matrixui/src/main/assets/wallpapers/photo-*.webp` in gua-android and `ElementX/Resources/Wallpapers/photo-*.webp` in gua-ios, are not under the AGPL. Each keeps its own licence, listed below. The legal code is in `licenses/CC0-1.0.txt` and `licenses/CC-BY-4.0.txt`.

Every photo was cropped to the 1290:2796 phone shape, resized to 1290x2796 where needed and re-encoded as WebP. Its colours were converted to sRGB and its metadata removed, and an XMP packet names the author, licence and source. `bake.mjs` bakes them from `photos.json`, which records each original's URL, sha256 and crop.

| File | Name | Author | Licence | Source | Changes |
|---|---|---|---|---|---|
| `photo-lencois-lagoon.webp` | Lençóis Lagoon | Gerda Arendt | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Lagoon_in_curved_sanddunes%2C_Len%C3%A7%C3%B3is_Maranhenses.jpg) | Cropped to 1384x3000 at 700,0 and resized |
| `photo-lencois-dunes.webp` | Lençóis Dusk | Gerda Arendt | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Sand_dunes_before_sunset%2C_Len%C3%A7%C3%B3is_Maranhenses.jpg) | Cropped to 1384x3000 at 0,0 and resized |
| `photo-gruta-azul.webp` | Blue Grotto | Wilfredo Rodriguez (Wilfredor) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Rock_walls_and_entrance_of_Gruta_Azul%2C_Arraial_do_Cabo%2C_Brazil.jpg) | Cropped to 2503x5425 at 1300,0 and resized |
| `photo-ipe.webp` | Ipê at Sunset | Thyrome | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Handroanthus_ochraceus.jpg) | Cropped to 2392x5184 at 600,0 and resized |
| `photo-araucaria.webp` | Araucária Crown | Themium | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Arauc%C3%A1ria_jordanense_1.jpg) | Cropped to 1290x2796 at 1116,0 |
| `photo-maned-wolf.webp` | Sleeping Maned Wolf | Wilfredo Rodriguez (Wilfredor) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Chrysocyon_brachyurus_in_S%C3%A3o_Paulo_Zoo.jpg) | Cropped to 1314x2848 at 1468,0 and resized |
| `photo-maned-wolf-standing.webp` | Maned Wolf | Gil Myers, Smithsonian's National Zoo | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Smithsonian's National Zoo](https://nationalzoo.si.edu/object/nzp_NZP-20160729-011GM) | Cropped to 1384x3000 at 100,0 and resized |
| `photo-yukon-trail.webp` | Yukon Trail | Sarah Lacerda | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | Author's own photo | Cropped to 3720x8064 at 1164,0 and resized |
| `photo-yukon-sun.webp` | Yukon Sun | Sarah Lacerda | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | Author's own photo | Cropped to 3720x8064 at 1164,0 and resized |
| `photo-yukon-forest.webp` | Boreal Trail | Sarah Lacerda | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | Author's own photo | Cropped to 3720x8064 at 1000,0 and resized |
| `photo-yukon-sled.webp` | Yukon Sled | Sarah Lacerda | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | Author's own photo | Cropped to 3720x8064 at 912,0 and resized |
| `photo-yukon-sled-2.webp` | Sled Dogs | Sarah Lacerda | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | Author's own photo | Cropped to 3720x8064 at 1164,0 and resized |
| `photo-peyto-lake.webp` | Peyto Lake | Ezra Jeffrey-Comeau | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:The_Highest_Highs_(Unsplash).jpg) | Cropped to 1978x4288 at 435,0 and resized |
| `photo-lone-canoe.webp` | Lone Canoe | Braxton Stuntz | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:The_Lone_Canoe_(Unsplash).jpg) | Cropped to 2555x5538 at 563,0 and resized |
| `photo-jasper-aurora.webp` | Jasper Aurora | Priscilla Du Preez | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Northern_lights_over_a_lake_(Unsplash).jpg) | Cropped to 1609x3488 at 2395,0 and resized |
| `photo-nunavik-aurora.webp` | Nunavik Aurora | Nicolas M. Perrault | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Aurora_Borealis_in_Kangiqsualujjaq.tif) | Cropped to 1643x3562 at 1400,0 and resized |
| `photo-lac-delage.webp` | Moonlit Lake | Wilfredo Rodriguez (Wilfredor) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Lac_Delage_at_night,_Quebec,_Canada.jpg) | Cropped to 2539x5504 at 2095,0 and resized |
| `photo-quebec-ice.webp` | Cedar Ice | Wilfredo Rodriguez (Wilfredor) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Ice_on_a_branch_in_Quebec_City.jpg) | Cropped to 2823x6118 at 542,0 and resized |
| `photo-quebec-leaves.webp` | Maple Leaves | Wilfredo Rodriguez (Wilfredor) | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Autumn_leaves_on_the_ground_in_Quebec_City.jpg) | Cropped to 1845x4000 at 2077,0 and resized |

Unsplash released photos under CC0 until 4 June 2017 and has used its own licence since 5 June 2017. Peyto Lake, Lone Canoe and Jasper Aurora were published on Unsplash before that change, and Wikimedia Commons hosts them under CC0.
