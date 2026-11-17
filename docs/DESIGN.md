# Trio design spec

One look for Purrcast, Zamoled and Maps and Cats. Reference: **Raycast** (black, compact, high contrast, utility first).
Target: **WCAG 2.1 AAA**. Voice: **warm and humancentric**. Profile: `.ux-profile.md`.
Maps and Cats already follows this (see `mapsandcats/app.css`, last block). Purrcast and Zamoled still need it, listed below.

## Tokens

| Token | Value | Notes |
|---|---|---|
| Background | `#000000` | pure black on dark |
| Accent | `#3DF5D0` | the only accent. Light theme: `#00574D` (7:1 on cream) |
| Text | `#F4F4F6` | |
| Caption / muted | `#AEAEB8` | 9:1 on black. Never dimmer |
| Card radius | 24dp | |
| Touch target | 48dp minimum | chips, icon buttons, list rows, switches |
| Heading | condensed bold | `sans-serif-condensed` |
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
- No glassmorphism, no glow borders, no decorative gradients, no rainbow buttons, no pastel pills.
- Animate transform and opacity, never width, height, margin or `bottom`.

## Status

**Maps and Cats: done in code, checked in a phone-size browser.** New `icons.js`, motion and contrast layer in `app.css`, 48dp targets, AAA muted colours. Note `android/app/build.gradle.kts` and `sw.js` list files explicitly, so both now include `icons.js`.

**Purrcast (plain Java, no AndroidX): not changed, could not be run here.** To do:
1. `Ui.java`: add `TRIO_MUTED = 0xFFAEAEB8`, use it for every caption. Check the light accent: `0xFF00796B` is about 5.3:1, use `0xFF00574D`.
2. Touch targets: raise every button, switch row and list row to 48dp (`dp(48)` minimum height).
3. Press feedback: on touch down animate `scaleX/scaleY` to 0.94 over 90 ms, back over 120 ms (`ViewPropertyAnimator`, no layout work).
4. Cards: 24dp radius, plus the teal rim as a 2dp gradient `View` at the top of each card.
5. Screen transitions: 240 ms with a `DecelerateInterpolator`; skip when `Settings.Global.ANIMATOR_DURATION_SCALE` is 0.
6. Replace any non-cat emoji in strings with drawn icons.

**Zamoled (Compose): not changed, could not be run here.** To do:
1. `Theme.kt`: set `onSurfaceVariant` to `#AEAEB8`, keep the primary `#3DF5D0`.
2. A shared `Modifier.pressable()` in `Components.kt`: `graphicsLayer` scale to 0.94 on press, `tween(90)`; use it on every card, chip and dock item.
3. Cards: 24.dp shape plus a 2.dp teal gradient rim drawn with `drawBehind` at the top edge.
4. `minimumInteractiveComponentSize()` of 48.dp on all clickables.
5. Feed rows: `animateItem()` with `tween(200)`, and `LocalReduceMotion`-style check (animator scale 0 means snap).
6. Replace non-cat emoji in labels with icons.

Both native apps then need an emulator run to confirm: contrast, tap size, 60 fps scrolling.
