# Trio streaks, the Streak Shield and the hero cat

Status: **draft for the owner's approval.** Nothing is built. Works together with `2026-11-17-progress-save-code-design.md`.

## Intent and the why
The three apps are a trinity: none of them leads. So every app counts its own day streak with the same rules, and when all three
are installed on one phone they unlock a **superpower**: a masked superhero Noto cat, smiling cutely, who shows up in all three
apps in a different way, and who gives each streak a **shield**. No accounts, no network, no data collection: the unlock is worked
out from which apps are installed on this phone.

## Goals
- Each app has a day streak (Zamoled and Maps and Cats get one for the first time), counted with identical rules.
- When all three apps are installed, the hero cat and the Streak Shield switch on in every app.
- The hero cat looks identical everywhere and has several designs.
- Before it is unlocked, a silhouette teaser shows there is something to unlock.

## Non-goals
- No leaderboard, no sharing of streaks off the phone, no network, no cross-app writes.
- No shop, no timers that nag, no push notifications about streaks.

## Anti-goals (a failure even if it works)
- One app quietly becoming the "main" one, or a streak that only exists in Purrcast.
- A shield so generous that streaks and the 10-day rewards stop meaning anything.
- Three slightly different hero cats because each app drew its own.
- Animations that ignore the phone's "remove animations" setting.

## Constraints
- `docs/TRIO.md`: sibling apps share only read-only counts through the Cat Club provider (one row, `value` and `label`).
  A new rule is needed: the provider `label` may carry a short streak and shield text ("day streak, shield ready").
- Installed-app detection already exists in each manifest (`<queries>`) and works locally.
- Purrcast is plain Java (no AndroidX); Zamoled is Kotlin/Compose; Maps and Cats is web in an Android shell.
- Trio look: pitch black, teal, AAA contrast, 48 px targets, warm voice, and **only Noto cats** (the hero cat is the owner's own
  Noto variant, like the sunglasses and umbrella cats).

## Approaches for the hero cat art
1. **Draw once, ship the same image files in all three apps (chosen by the owner).** Same face everywhere, simple to use in widgets,
   wallpapers and web. Costs about 300 KB per app.
2. Draw the mask and cape in code in each app: smaller, but three versions that can drift apart. Rejected.

## Decisions

### 1. A streak in every app (same rules)
- A visit is the app coming to the front. Day = the phone's local calendar day.
- State per app: count, best, last visit day, a 64-day history, shield state. Fields and rules are exactly Purrcast's today
  (`Store.touchStreak`); Zamoled stores them in its `zamoled` preferences, Maps and Cats in its saved state.
- Rules: a visit on the day after the last visit adds 1; a longer gap resets the count to 1 (unless the shield saves it, below);
  best never goes down.
- The Cat Club line in every app lists all three streaks.
- **One set of test vectors** (a list of "visit on these days, expect this count, best and shield") is checked in all three apps,
  so the rules cannot drift apart.

### 2. The Streak Shield (my call, to be confirmed)
- Works only while **all three apps are installed** at the moment of the visit.
- If you come back after missing **1 to 7 days**, the streak is kept: missed days add nothing, today adds 1. After 8 or more missed days it resets as usual.
- It saves you **once**, then recharges after **7 visit days in a row**. A new unlock starts "ready".
- Each app has its own shield state. The state shows on the Cat Club line: "shield ready" or "recharging, 3 days to go".
- Uninstalling an app turns the shield off but never removes streak days already earned.

### 3. The hero cat art
- About **seven designs**, drawn as SVG in the Noto style from the `svgmoji` cat faces already in the repo (licence notice kept):
  classic (smiling, red mask), **sun** (sunglasses), **rain** (umbrella), **night** (the black cat, gold mask), **storm** (cape and bolt),
  **hot**, **sleepy** (mask pushed up). Plus one **silhouette** for the locked teaser.
- One source folder for the SVGs; exported to SVG (Maps and Cats) and PNG at 512 px (Purrcast and Zamoled) by one script, so the files match.

### 4. Where the hero cat shows
| App | What changes when unlocked |
|---|---|
| Purrcast | Every widget uses the hero cat costume that matches the weather; the streak card shows the hero cat with the shield state. A "Show hero cat" switch in Settings (on by default). |
| Zamoled | A new Cat Club look: hero-cat wallpapers (the owner's "wallpaper in Zamoled"), plus the badge on its Cat Club line. |
| Maps and Cats | On the Settings page, a small parade of hero cats strolling across (transform-only animation, still when "remove animations" is on). |
Locked: a silhouette teaser with a short line ("Install all three to meet the hero cat") on the same spots.

### 5. Detection
Each app checks the other two packages when it opens (and when widgets refresh), caches the answer for a few minutes, and never
sends anything anywhere.

## What is left to the builder
The actual art; the parade choreography; exact widget artwork sizes and bitmap caching (so widgets stay light); the wording;
the shield bit packing in the save code; and the Zamoled hero-cat wallpaper composition (keep it pitch black).

## Release shape
A feature in each app: Purrcast 2.1.0, Zamoled 8.1.0, Maps and Cats 2.2.0, together with the save code. Update `docs/TRIO.md`
(streaks, the shield, the provider label) and each app's help text.

## Later, optional (not in this project)
- A ring of 10-day rewards so each app's streak unlocks something in the next app (Purrcast already unlocks a Zamoled wallpaper).
- Android's own automatic backup (phase 2 of the save code).


## Update: emotions instead of costumes (owner's request)
The hero cat has nine emotions: smile, grin, smirk, confident, angry, shocked, sad, scared, loaf (plus a locked silhouette). Same mask, cape and star collar on every one. Purrcast picks the emotion from the weather icon; Zamoled's Hero Club mixes them; the Maps and Cats parade walks a mix. Weather mapping is the builder's proposal, see the hero-cat plan.
