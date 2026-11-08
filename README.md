# Textura

Turn any text into a designed page. The text always fills the whole shape you pick
(portrait, landscape, square, story or block), you flick through looks, edit the one you like,
and export a high-resolution image.

## Build the APK

Requirements: Android Studio (Ladybug or newer) or JDK 17 plus the Android SDK (API 35).

**Android Studio:** open the `Textura` folder, let Gradle sync, then *Build > Build Bundle(s) / APK(s) > Build APK(s)*.

**Command line:**

```
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk (signed with the debug key, fine for sideloading)
```

The first sync downloads Gradle 8.9 and the dependencies, so it needs internet once.
Minimum Android version: 10 (API 29).

## What is in it

- **Create:** paste or type text, pick a shape, tap Create. Shares from other apps ("Share > Textura") fill the text box too.
- **Browser:** full-page preview. Flick left or right to change look, tap a category pill (Paper, Typed, Handwritten, Art, 3D),
  double-tap (or the grid icon) for a grid of every look. Edit, Favorite, Save, Share along the bottom.
- **Editor** with live preview, undo/redo, reset to template, and tabs:
  - **Text:** colour, gradient fill, uppercase, bold
  - **Font:** 40 bundled fonts in groups, letter spacing, handwritten movement and ink realism
  - **Background:** one colour, gradient (linear, radial, sweep, up to 5 colours, angle, presets), or your own photo with blur, dim and colour overlay
  - **Depth:** flat (outline, shadow, glow), emboss, engrave, or true 3D extrusion (length, direction, side colour, shading, edge highlight)
  - **Layout:** shape, alignment incl. justify, margins, line and letter spacing, spread lines to fill the height
  - **Paper:** lined, graph, dotted, parchment, old letter, sticky note, receipt, chalkboard, newsprint, kraft; paper colour, texture, float on desk, tilt
- **48 built-in looks**, plus "Save as my template".
- **Export:** HD 1080, 2K 2048 or 4K 3840 (longest side), PNG or JPG, transparent text-only PNG. Saves to Pictures/Textura or opens the share sheet.
- **Library:** favorites, my templates, history of what you exported.
- **Settings:** theme, default shape, size and format, JPG quality, haptics.

## How the layout works

All layout is computed in a fixed 1000-unit design space, then scaled to the output size, so the preview and the 4K export
break lines in exactly the same places. `render/TextEngine.kt` binary-searches the largest font size that fits the shape
(including the extra room a 3D extrusion needs), wraps and justifies the lines, and spreads them to fill the height.
`render/Renderer.kt` draws background, paper, then the text passes (shadow, outline, emboss, 3D layers, face).

## Project map

```
app/src/main/java/com/textura/app/
  MainActivity.kt          navigation
  model/                   Look, Background, shapes, fonts, the 48-look catalogue
  render/                  TextEngine, Renderer, PaperPainter, BackgroundPainter
  data/                    Store (settings, favorites, templates, history), Exporter (gallery + share)
  ui/                      screens, editor tabs, controls, theme
app/src/main/assets/fonts/ bundled .ttf files
```

To add a look, add a `Look(...)` entry to `model/Looks.kt`. To add a font, drop the .ttf into `assets/fonts/`
and add an `AppFont` line in `model/Fonts.kt`.

## Fonts

Bundled from Google Fonts under the SIL Open Font License or Apache License 2.0
(Caveat, Shadows Into Light, Indie Flower, Patrick Hand, Reenie Beanie, Homemade Apple, Gloria Hallelujah, Kalam,
Covered By Your Grace, Permanent Marker, Rock Salt, Dancing Script, Great Vibes, Sacramento, Satisfy, Pacifico, Yellowtail,
Lobster, Special Elite, Courier Prime, Space Mono, VT323, Cutive Mono, Playfair Display, Lora, EB Garamond, Crimson Text,
Cinzel, Old Standard TT, UnifrakturCook, Montserrat, Poppins, Oswald, Abril Fatface, Bebas Neue, Anton, Archivo Black,
Bungee, Righteous, Monoton).
