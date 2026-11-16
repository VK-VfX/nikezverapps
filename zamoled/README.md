# Zamoled

An Android wallpaper app that **generates** pitch-black AMOLED wallpapers on your phone. It's built with Kotlin, Jetpack Compose and Material 3. You don't download images: every wallpaper is drawn from a seed, so you get an endless supply, rendered sharp at any resolution up to 4K.

## Features

- **71 wallpapers across 10 worlds**, each with its own art direction
  | World     | Styles |
  |-----------|--------|
  | Doodle    | Sticker Bomb, Gouache, Chalk, Space Doodles and Cat Club (twelve breeds, from tabby and British Shorthair to Bengal, Siamese, calico, Sphynx, Maine Coon and Scottish Fold, each with its own markings and a different mood) |
  | Lab       | Science emoji as stickers, gouache or chalk: Physics, Maths, Chemistry, Biology, Psychology, Philosophy, History, Space, Spirals & Radiation, and Everything |
  | Words     | Your own words on black, typed in the editor: Bold, Quiet, Quote, Echo, Marker |
  | For Pixel | Material 3 Expressive on pure black, in your phone's own Material You colours: Bloom, Cookies, Blobs, Waves, Flow, Pills, Arches, Components (switches, sliders, chips, FAB and cards), Aura, Overlap |
  | Minimal   | Ink (Lone Tree, Koi Pond, Moon Fisher), Lines (Constellation, One Line, Contours, Orbits: a few hairlines on black) and Always-on (Glance: one small dim mark, over 99% of pixels off) |
  | Nature    | Sky and land (Northern Lights, Moonlit Peaks, Starry Pines, Moon Path), Fire (Campfire, Embers, Inferno, Volcano, Flame) and Storm (Lightning Strike, Storm Cell) |
  | City      | Rooftops, Skyline, Night Highway |
  | Glitch    | Data Rain, Wet Street (a rainy cyberpunk street), and eight code looks: Source Code, Diff Glitch, Kernel Panic, Hex Dump, Code Fall, Big Code, Corrupted, Build Log |
  | Pixel Bits | Pixel Castle, Pixel Quest, Pixel Sunset, Pixel Galaxy, Pixel Lighthouse |
  | Light     | Lanterns, Candlelight, Jellyfish, Fireflies |

  Older styles that were retired from browsing (for example Halo, Silk, Aurora, Pixel City) still open if you saved them as a favourite.
- **True black backgrounds.** Every wallpaper starts from `#000000`, and the editor shows a live **Pitch black %**: the share of pixels your OLED screen can switch off completely.
- **Material 3 / Material You.** It uses the dynamic colour theme (Android 12+) with surfaces forced to true black. There's also a **Material You palette** that tints wallpapers with your system colours.
- **16 palettes plus Material You** (Mono, Neon Cyan, Sunset, Aurora, Sakura, Lava, Ocean, Acid, Violet, Gold, Candy, Graffiti, Hue Shift, Fire, Storm, Cyberpunk), or Auto, which picks palettes that suit each style.
- **Sub-categories.** Each world is split into sub-categories, shown as a second row of chips once you pick one, so you narrow the feed instead of scrolling it.
- **Daily wallpaper.** Turn it on from the clock icon on Explore and a fresh wallpaper is set about once a day in the background, from everything, your favourites or one world.
- **Black level.** A slider that crushes dim glow and haze to pure `#000000` on any wallpaper, for a darker, more battery-friendly result.
- **Widget-safe and Purrcast quiet zones.** Lay the picture out below your launcher clock and weather widgets, or dim soft zones where Purrcast's widgets sit.
- **Clock preview.** See the wallpaper under a lock-screen clock or a home screen with icons before you apply it.
- **Matching pair.** One tap sets this wallpaper on home and a sibling in the same style and colours on lock.
- **Swipe to compare.** In the preview, swipe left for a new variation and right to go back.
- **Share.** A wallpaper is a short recipe, so a shared link (or the code pasted into search) draws the identical wallpaper on another phone.
- **Search, style pages and history.** Search styles and worlds, open a page of one style's variations with "More like this", and find what you opened lately under "Recently viewed".
- **Detail slider.** Turn the density of stars, doodles, lines, windows and falling code up or down on the styles that have it.
- **Arrangements (Doodle world).** Lay the doodles out as a Grid, Rings, Spiral, Wave, Mosaic, Burst, Diagonal, Frame, Shape or a loose Scatter. Auto picks one from the seed.
- **Save** as a lossless PNG at screen size, QHD+ (1440×3200) or 4K (2160×3840). Files go to `Pictures/Zamoled`, and no storage permission is needed.
- **Apply** to the home screen, lock screen or both.
- **In-app updates.** The app checks this repo's releases and downloads and installs a newer version from inside the app.
- **The trio.** Zamoled opens Purrcast and Maps and Cats, shows their Cat Club numbers, and every 10 days of your Purrcast streak earns a special Cat Club wallpaper (see `docs/TRIO.md` at the repo root).
- **Favorites**, **Shuffle** (new seed) and **Surprise me**. The feed is a finite batch of 24, with a Shuffle button at the end, so there is nothing to scroll forever.
- **Expressive Material 3 UI:** a bottom navigation bar with Explore, Categories and Favorites; a "Today's picks" carousel; category icons in their own morphing shapes; shared-element transitions from thumbnail to full screen; a floating editing toolbar; and spring motion throughout.

## Get the APK

Zamoled updates itself from inside the app. For a first install, or to build your own:

1. Open this repo's **Releases** and download the newest `zamoled-v<version>` release (`.github/workflows/zamoled-build.yml` builds it).
2. Install it on your phone. You may need to allow installs from your browser or file manager.

Every push that touches `zamoled/` runs the build as a check. A push to `main` also publishes the release `zamoled-v<versionName>` (bump `versionName` in `app/build.gradle.kts` for a new release), and the in-app updater picks up the newest `zamoled-v*` release.

### Signing (so updates install over the old app)

Android only accepts an update when it's signed with the same key as the installed app. CI signs every build with one permanent key, which it reads from two repository secrets (**Settings → Secrets and variables → Actions**):

| Secret | Value |
|---|---|
| `ZAMOLED_KEYSTORE_BASE64` | the keystore file (`.jks`), base64-encoded |
| `ZAMOLED_KEY_PASSWORD` | the keystore and key password (key alias `zamoled`) |

These secrets are optional. Without them, CI creates its own key on the first build and keeps it in the repository's private Actions cache, so builds still share one key and update over each other. Add the secrets if you want a key you control and back up yourself. Note that switching keys later means one more uninstall.

To create a key: `keytool -genkeypair -keystore zamoled.jks -storetype PKCS12 -alias zamoled -keyalg RSA -keysize 2048 -validity 36500`, then `base64 -w0 zamoled.jks`. Keep the `.jks` file and password safe: losing them means users have to uninstall before they can install your next build.

The debug build uses the app id `com.nikopick.zamoled.debug`, so it installs alongside the release build.

## Build locally

You need Android Studio (or the Android SDK with platform 35) and JDK 17.

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Requires Android 10 (API 29) or newer.

## How it works

### Shading

`gen/ShadeKit.kt` is what separates a drawing from a painting. One key light sits up and to
the left of every shape: `Shade.volume` grades a fill from highlight to core to shadow,
`occlude` darkens the edge the light cannot reach, `sheen` puts the specular back, `fur`
combs tapered hairs out along a silhouette with `PathMeasure`, `coat` lays guard hairs
across it, and `eye` builds an eye out of iris, pupil, catchlight and a wet glint. `Pen`
takes a `shaded` flag, so every filled shape it draws becomes a lit volume rather than a
flat colour.


`gen/Core.kt` holds the engine. Each style draws on a virtual canvas that is 1000 units wide, which is scaled to the target bitmap. Because of that, the same seed gives the same picture as a 360 px thumbnail and as a 4K export. Randomness comes from `kotlin.random.Random(seed)` plus a seeded Perlin noise. A wallpaper is fully described by `style | seed | palette | detail | arrangement`, and favourites are stored as that string.

To add a new style, subclass `Style`, draw with the `Scene` helpers (`stroke`, `glowPath`, `glowDot`, `grad`, `noise`…), and register it in `Styles.all`.

## Previewing the art on a computer

`tools/preview` renders the generators to PNGs without a phone. See `tools/preview/README.md`.

