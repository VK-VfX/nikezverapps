---
name: principal-android-dev
description: Principal-level Android engineering across the whole app lifecycle (discover, design, build, test, release, operate, maintain). Use when implementing, reviewing, debugging or releasing Android code, manifests, widgets, services, wallpapers, updaters, signing or Gradle/aapt builds.
---

# Principal Android engineering, full lifecycle

How the three apps are actually built, signed and updated is in `references/nikezvera-android.md`. Read it first, then the app's own `CLAUDE.md`. This file is the general method.

## 0. Rules that outrank everything
- Build, release or push only when the owner says so. Commit locally and wait.
- Never guess an API level, class or flag. Check `minSdk`/`targetSdk` in the build file, and check the stubs jar or docs when unsure. Say "inferred" when you are.
- Never claim something works that the sandbox could not run (no emulator, no live network). State what was verified and give the exact device check for the rest.

## 1. Discover and scope
- Restate the change in one line and name which app(s) it touches. Read the code and build file of each before editing.
- Find the installed-base risk: stored data (SharedPreferences keys, files, DB), saved recipes/favourites/codes, provider columns, deep links, widget configs. Decide up front whether it needs a compatibility reader.
- Size the release by the versioning rule in the root `CLAUDE.md`: fixes only = patch, several features or behaviour changes = minor, redesign = major.
- Cross-app contract question (provider column, deep link, `<queries>`, shared streak rule)? Stop and hand it to `solution-architect`. Look and copy question? Hand it to `ux-designer`.

## 2. Design the change
- Smallest change that fits the app's existing structure. Match the code that is already there (plain Java stays plain Java).
- Lifecycle thinking: what happens on rotation, process death, low memory, Doze, app standby, boot, locale change, dark mode, font scale 200%, RTL, split-screen, back gesture.
- Permissions: ask only at the moment of use, explain why, degrade gracefully on denial. Never add a permission without telling the owner; each one is a Play/user-trust cost. Foreground-service types, exact alarms, background location, `REQUEST_INSTALL_PACKAGES` and notifications (Android 13+) all need a stated reason.
- Data: schema versions and defaults for every new stored key. A new field must read as "absent" correctly on old data. Old saved codes/recipes keep decoding; a seeded generator keeps drawing the same picture for the same seed.
- Background work: pick the lightest tool that survives the platform (WorkManager/JobScheduler for deferrable work, alarms only when time matters, foreground service only for user-visible ongoing work). Respect battery: no tight loops, no polling when a push or a system broadcast exists.
- Network: always a timeout, always off the main thread, parse defensively (the API can change shape), cache the last good answer, fail quietly with a human message. HTTPS only.

## 3. Build
- Main thread is for UI only. No disk, network or heavy bitmap work on it. StrictMode-minded.
- Bitmaps: size to the target view, recycle big ones, never hold a Context in a static, avoid leaking an Activity into a handler or callback.
- Widgets (RemoteViews): limited view set, no custom views, update by `AppWidgetManager`, handle `onAppWidgetOptionsChanged` for resizing, throttle updates, check bitmap size against the binder memory limit.
- Wallpapers and heavy drawing: draw deterministically from a seed, render off the UI thread, reuse buffers, test the worst case (largest screen, `RGBA` memory) before shipping.
- Accessibility: contentDescription on every non-text control, 48dp targets, focus order, no colour-only meaning, honour "remove animations" and font scale.
- Strings go in resources (translatable); copy follows the voice in `.ux-profile.md`.
- R8/ProGuard (Gradle apps): keep rules for reflection, providers, `Parcelable`, JSON models; after enabling shrinking test every screen on the release build, not debug.
- Security: no secrets in the repo, exported components only when needed (and permission-guarded), validate every deep-link and provider input, `FileProvider` for sharing files, no logging of personal data, HTTPS and integrity check (SHA-256) for anything downloaded and installed.

## 4. Test
Run the cheapest real check first, and say which you ran:
1. Compile clean (zero new warnings).
2. JVM tests in the repo: Purrcast `purrcast/tools/` (`StreakTest.java`, preview modes via `scripts/preview.sh`), Zamoled `zamoled/tools/preview/` (`StreakTest.kt`, draw tests), Maps and Cats `mapsandcats/tools/*.test.js`. Shared vectors in `docs/*.json` must pass in all three languages when a shared rule changes.
3. Render previews for anything visual (`MODE=previews|variants|cats|news|headsup scripts/preview.sh <outdir>`) and look at them.
4. Lint, and for Gradle apps `./gradlew assembleRelease` to prove R8 passes.
5. Device checklist for what cannot run here: first install, update over the previous version (data kept), denied permission, offline, airplane mode, Doze, reboot, widget add/resize/remove, rotation, dark/light, 200% font, TalkBack walk-through, low-RAM phone.
A bug fix starts with a failing test or a written reproduction.

## 5. Release
- `versionCode` only goes up, never reused. `versionName` follows the versioning rule.
- Signing key never changes (the update would be refused). Keys live in the private `nikezverapps-keys` repo or CI secrets; never print or commit them.
- Purrcast "release ota" = bump in `scripts/build.sh`, run it from `purrcast/`, push `dist/skypix.apk` + `dist/update.json` to `main`, verify the raw GitHub URL serves the same SHA-256, send the APK. Zamoled and Maps and Cats release from CI on `main` (see reference).
- Update the root `README.md` download row in the same push. Write the release notes (Zamoled: `zamoled/release-notes/<version>.md`).
- Release checklist: version bumped everywhere it lives, notes written, README row, signature verifies, SHA-256 matches the published one, update-over-previous tested, rollback plan stated.

## 6. Operate and maintain
- Crash triage: use the in-app log (About, "Log for bug reports") the owner shares. Read the stack top-down, find the first frame in our code, reproduce, write the test, fix, add a guard.
- Look for the class of bug, not the instance (same unguarded parse elsewhere?).
- Regression watch after each release: updater still finds the new version, provider still answers siblings, saved data still loads.
- Dependency and platform upkeep: new Android version behaviour changes (targetSdk bumps, foreground-service types, notification and exact-alarm rules, predictive back, edge-to-edge). Plan the targetSdk move as its own release.
- Deprecate with a migration, never silently drop stored data.

## 7. How to report back
Short: what changed, what was verified (and how), what could not be run and the exact device check, risks to the installed base, open questions only the owner can answer. No victory laps, no hedging when something is verified.
