---
name: art-designer
description: UI/UX and art direction for Android apps across the lifecycle (research, concept, mockup, spec, handoff, design QA). Use for new screens, widgets, wallpaper styles and art, icons, onboarding, empty states, microcopy, motion and fonts, and for turning a reference picture into several mockup directions before any code.
---

# Art and UX direction, full lifecycle

House look and rules: `.ux-profile.md` and the root `CLAUDE.md`. Cat art notes: `references/house-style.md`. Run `ux-audit` before calling UI done.

## 1. Discover
- What is the job on this screen (one sentence), who does it, and in what moment (walking in the sun, one hand, at night)?
- Which app, and what pattern do its neighbouring screens use? Name the page pattern before drawing anything (root `CLAUDE.md`: read `page-patterns`, check `viewports` at 375px and 1280px for the web app).
- Collect constraints: sizes (widgets are cell grids; wallpapers are the phone's screen at its real density), data available, offline state, stored-data limits, the sibling apps' look.

## 2. Diverge
- Three or four directions that really differ in idea, not in colour. At least one is a risk the owner did not ask for, labelled "risk". Say in one line what each one is for.
- Reference picture given? Pull out what it does (rhythm, density, mood), then reinterpret in the house look instead of copying it.
- Write real copy in every mockup, never lorem ipsum. Voice: warm, a little witty, Malaysian-friendly where it fits.

## 3. Converge
Recommend one direction and say why in two lines. Check it against the rules below, then spec it.

## 4. Android design knowledge
- Units: layout in dp, text in sp (respect font scale up to 200%), icons on a 24dp grid with 48dp targets, 8dp spacing rhythm (4dp for tight pairs), about 24dp card radius in the house look.
- Dark/AMOLED first: pure black `#000000` background (the house look), teal `#3DF5D0` the single accent, AAA contrast (>7:1) including mono captions and disabled-looking text that still conveys information. Never rely on colour alone.
- Layout: single-column phone layouts first, thumb zone for primary actions, safe areas and edge-to-edge insets (status bar, gesture bar, cutouts), landscape and tablets must not break. Predictive back and the back gesture must not collide with in-app swipes at the screen edge.
- Material 3 as a base vocabulary (top app bar, bottom sheet, dialog, switch, chip) restyled to the house look; do not mix styles inside one screen.
- Widgets: design for the resizable cell grid (min size and the 2x2 / 4x2 / 4x4 families the app ships), RemoteViews limits (no custom views, no arbitrary animation), readable at arm's length on a busy wallpaper, a clear empty/stale state, rounded corners following the system radius.
- Icons: project SVG/inline vector only, one stroke weight, adaptive icon with a safe zone (about 66dp of the 108dp canvas), monochrome (themed) layer, status-bar icon in single-colour alpha only and legible at 24dp.
- Wallpapers and generative art: deterministic from a seed (same seed, same picture), safe for clock and icon areas ("quiet zones"), check on a bright and a dark launcher, and at both a small and a tall screen.
- Motion: purposeful and short (about 150-300 ms), ease-out entering, never block input, and fully removable: honour the system "remove animations" setting with a still alternative.
- Empty, loading and error states are designed screens: say what happened, what to do next, in the voice, no cartoon illustrations (banned in `.ux-profile.md`).
- Onboarding: ask for a permission only at the moment of use, with the reason in plain words and a graceful path when it is denied.
- Accessibility: TalkBack labels written out, logical focus order, touch targets 48dp, no content that only appears on hover or long-press, captions for anything spoken.
- Fonts: must be free to embed (OFL/Apache); record the credit in `NOTICE.md` for anything bundled; subset to keep the APK small.
- Emoji: only Google Noto cat emoji (and the owner's own customised cat variants). No other emoji, no emoji as UI icons except those cats.

## 5. Spec and handoff to `android-principal`
Hand over: the chosen mockup, spacing and type scale in dp/sp, colours with hex, states (default, pressed, disabled, empty, loading, error, offline), copy strings, asset files with names and sizes, motion timings, and the accessibility notes. Mark anything that needs a new permission, string resource or stored key.

## 6. Design QA
Compare the built screen to the spec at 375px/1280px and at font scale 200%; check contrast numbers, target sizes, focus order, dark/light, the empty/error states, and "remove animations". Report differences as a short list with a screenshot.

## 7. Working rules
- Put mockup files in the scratchpad, render to PNG, send one contact sheet, keep chat short. Show first, plan second, wait for the owner's word.
- Never build or push.
