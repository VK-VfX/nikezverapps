# Trio design spec

One look for Purrcast, Zamoled and Maps and Cats. Reference: **Raycast** (black, compact, high contrast, utility first).
Target: **WCAG 2.1 AAA**. Voice: **warm and humancentric**. Profile: `.ux-profile.md`.
Maps and Cats already follows this (see `mapsandcats/app.css`, last block). Purrcast and Zamoled still need it, listed below.

## Tokens

| Token | Value | Notes |
|---|---|---|
| Background | `#000000` | pure black on dark |
| Accent | `#3DF5D0` | actions, links, the rim, selected states. Light theme: `#00574D` (7:1 on cream) |
| Violet | `#B388FF` | **second accent (2026-11 refresh)**, only for streaks, the Streak Shield, Cat Club numbers and the hero cat. Never for buttons or links. 7.9:1 on black. Light theme: `#5E35B1` (7.4:1 on cream) |
| Text | `#F4F4F6` | |
| Caption / muted | `#AEAEB8` | 9:1 on black. Never dimmer |
| Card radius | 28dp (was 24) | small cards and thumbnails 24dp, buttons and chips 20dp, tonal blocks 18dp |
| Touch target | 48dp minimum | chips, icon buttons, list rows, switches |
| Heading | condensed bold, +1% letter spacing from 20sp | `sans-serif-condensed` |
| Caption font | mono | `monospace` / `ui-monospace` |
| Press | 90 ms, scale 0.94 | transform and opacity only |
| Fast | 160 ms | colour, chips, toggles |
| Sheet | 240 ms | `cubic-bezier(.2, .8, .2, 1)`, no bounce, no overshoot |
| Focus | 3dp accent outline | |

## Trio signature
- A thin teal rim (2dp, fading at both ends) along the top edge of every floating card and sheet.
- Sheet handle tinted teal.
- Mono captions under condensed bold headings.
- One hand-made icon set: 24 grid, 2dp round stroke, one colour. No emoji except the Noto cats.
- Lists fade up 6dp over 200 ms, staggered 20 ms for the first five rows.
- Respect the system "remove animations" setting: all motion drops to about 0.

## Rules
- Only Google Noto cats (and the owner's accessory variants) are emoji. Everything else is an icon.
- No glassmorphism, no glow borders, no decorative gradients, no rainbow buttons, no pastel pills. Violet is a second accent with one job (streaks and the hero cat); do not spread it to other UI.
- Animate transform and opacity, never width, height, margin or `bottom`.

## Status

All three apps follow this spec as of Maps and Cats 2.0.0, Purrcast 2.0.0 and Zamoled 7.0.0.

- **Maps and Cats:** `icons.js` (the icon set), the motion and contrast layer at the end of `app.css`, 48px targets, AAA captions. Note `android/app/build.gradle.kts` and `sw.js` list files explicitly, so a new web file must be added to both.
- **Purrcast:** `Ui.java` has press feedback (`Ui.press`), the teal rim (`RimSurface`, `drawRim`), the first-show entrance (`Ui.enter`) and the darker light-theme accent `#00574D`.
- **Zamoled:** `Motion.kt` has snappy no-bounce springs and a 90/120 ms press; `Noir.kt` has `Modifier.tealRim()` on `LeafCard`; `Theme.kt` sets `onSurfaceVariant` to `#AEAEB8`.

Not yet done: Zamoled's wallpaper-grid cards have no teal rim (kept clean on purpose), and the native apps still need a pass on a real phone for contrast, tap size and 60 fps scrolling.

## Refresh log
- **2026-11 "violet and softer":** soft violet `#B388FF` for streaks, shield, Cat Club numbers and the hero cat; cards 24 -> 28dp, small cards 20 -> 24dp, buttons 16 -> 20dp; headings +1% letter spacing. Applied in Purrcast (`Ui.CARD`, `Ui.violet`, the streak card), Zamoled (`Leaf`, `LeafSm`, `Violet`, the Cat Club card, detail-screen buttons) and Maps and Cats (`--violet`, `.streak-v`, the last block of `app.css`). Not yet on a real phone.
