# Trio Streaks and Streak Shield Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every app (Purrcast, Zamoled, Maps and Cats) keeps its own day streak with identical rules, plus a Streak Shield that works while all three apps are installed.

**Architecture:** One pure `step` function per language (Java, Kotlin, JS), all checked against one shared vectors file so the rules cannot drift. Each app stores its state locally, detects its two siblings with the existing package check, and shows the three streaks through the existing read-only Cat Club provider (the `label` text carries the shield state).

**Tech Stack:** Purrcast plain Java (no AndroidX, JVM test harness `purrcast/tools`), Zamoled Kotlin/Compose (Robolectric preview project `zamoled/tools/preview`), Maps and Cats web (`mapsandcats/`, `node` for tests) in an Android shell.

**Spec:** `docs/superpowers/specs/2026-11-17-trio-streaks-and-hero-cat-design.md` (sections 1, 2, 5). Follow-on plans: `2026-11-17-progress-save-code.md`, `2026-11-17-hero-cat.md`.

## Global Constraints
- A visit = the app coming to the front; day number = local calendar day: `floor((nowMillis + tzOffsetMillis) / 86400000)`.
- Shield: saves a gap of 1 to 7 missed days (day gap 2..8), once; recharges after 7 consecutive visit days; works only while all three apps are installed; a new install starts `ready = true`.
- State per app: `count, best, last, mask (64-bit, bit 0 = latest visit day), ready, charge (0..7)`.
- The Cat Club provider keeps one row with columns `value` (int) and `label` (short text). Never expose anything else.
- No network, no new permissions, no data leaves the phone. Warm wording, AAA contrast, 48 px targets, only Noto cats, no emoji outside them.
- Plain Java in Purrcast (no lambdas, `--release 8`, no AndroidX). Each commit is local; **do not push, build releases or bump versions** until the owner says so.

## Review Focus
- Phone clock moved backwards, time zone or DST change: a visit day earlier than `last` changes nothing.
- The same day opened many times (every `onResume`, process death and restart): no double count.
- A sibling uninstalled between visits: shield is off for that visit, streak resets normally, `ready` is kept.
- Sibling not installed or an older version whose provider label has no shield text: Cat Club line still reads correctly.
- Legacy Purrcast installs with `streak_mask` but no `streak_count` or shield fields: streak continues, shield starts ready.

## File Structure
- Create `docs/streak-vectors.json` (shared test cases).
- Purrcast: create `purrcast/app/src/main/java/app/skypix/core/Streak.java`, `purrcast/tools/app/skypix/tools/StreakTest.java`; modify `Store.java` (`touchStreak`), `Trio.java`, `CatClubProvider.java`, `MainActivity.java` (streak card, Cat Club row).
- Zamoled: create `zamoled/app/src/main/java/com/nikopick/zamoled/gen/StreakRules.kt` (pure, in `gen/` because the preview and desktop projects already compile that folder), `.../data/StreakStore.kt`, `zamoled/tools/preview/src/test/kotlin/StreakTest.kt`; modify `MainActivity.kt`, `data/Trio.kt`, `data/CatClubProvider.kt`, `ui/SettingsScreen.kt` ("The trio" card).
- Maps and Cats: create `mapsandcats/streak.js`, `mapsandcats/tools/streak.test.js`; modify `index.html`, `sw.js` (SHELL list), `android/app/build.gradle.kts` (the explicit asset `include(...)` list), `app.js`, `android/.../MainActivity.java` (bridge), `android/.../CatClubProvider.java`.
- Modify `docs/TRIO.md`.

## The rules (the only algorithm the signatures do not give)
```
step(s, day, shieldOn):
  if day <= s.last and s.last != 0: return s            # same day, or clock went back
  gap = (s.last == 0) ? 0 : day - s.last
  n = copy(s)
  if gap == 1:            n.count += 1; if !n.ready: n.charge += 1; if n.charge >= 7: n.ready = true; n.charge = 0
  elif 2 <= gap <= 8 and shieldOn and s.ready:  n.count += 1; n.ready = false; n.charge = 0
  else:                   n.count = 1; if !n.ready: n.charge = 0
  n.best = max(n.best, n.count)
  delta = (s.last == 0) ? 64 : gap
  n.mask = (delta >= 64) ? 1 : ((s.mask << delta) | 1)          # unsigned 64-bit
  n.last = day
shieldLabel(s, shieldOn) = "" if !shieldOn; "shield ready" if s.ready; else "shield recharging, " + (7 - s.charge) + " days to go"
```

### Task 1: Shared vectors
**Files:** Create `docs/streak-vectors.json`.
**Produces:** a file `{"cases":[{name, shieldOn, start, visits, expect}]}`; `start` and `expect` hold `count, best, last, mask (decimal string), ready, charge`; `expect` also holds `label`; `visits` is a list of day numbers or `{"from":a,"to":b}`. `start` is always `{count:0,best:0,last:0,mask:"0",ready:true,charge:0}`.

- [ ] **Step 1: Write the file with exactly these cases** (visits, shieldOn → count, best, last, mask, ready, charge, label):
  - first visit: `[100]`, on → 1,1,100,"1",true,0,"shield ready"
  - four in a row: `[100,101,102,103]`, on → 4,4,103,"15",true,0,"shield ready"
  - same day twice: `[100,100]`, on → 1,1,100,"1",true,0,"shield ready"
  - clock moved back: `[100,101,99]`, on → 2,2,101,"3",true,0,"shield ready"
  - miss 2 days, shield off: `[100,101,102,103,106]`, off → 1,4,106,"121",true,0,""
  - miss 2 days, shield on: same visits, on → 5,5,106,"121",false,0,"shield recharging, 7 days to go"
  - miss 7 days (gap 8) saved: `[100,101,109]`, on → 3,3,109,"769",false,0,"shield recharging, 7 days to go"
  - miss 8 days (gap 9) not saved: `[100,101,110]`, on → 1,2,110,"1537",true,0,"shield ready"
  - gap while recharging: `[100,101,104,108]`, on → 1,3,108,"401",false,0,"shield recharging, 7 days to go"
  - recharge after 7 in a row: `[100,101,104,105,106,107,108,109,110,111]`, on → 10,10,111,"3327",true,0,"shield ready"
  - long gap: `[100,200]`, on → 1,1,200,"1",true,0,"shield ready"
  - full history: `{"from":200,"to":263}`, on → 64,64,263,"18446744073709551615",true,0,"shield ready"
- [ ] **Step 2: Commit** `git add docs/streak-vectors.json && git commit -m "test: shared streak vectors"`

### Task 2: Purrcast streak core and wiring
**Files:** Create `.../app/skypix/core/Streak.java`, `purrcast/tools/app/skypix/tools/StreakTest.java`. Modify `Store.java:105-130`, `Trio.java`, `CatClubProvider.java`, `MainActivity.java` (call at `:154`, streak card, Cat Club row near `:956`).
**Interfaces:**
- Produces: `final class Streak { static final class State { int count, best, charge; long last, mask; boolean ready = true; } static State step(State s, long day, boolean shieldOn); static String label(State s, boolean shieldOn); static State fromLegacy(long mask); }` (`fromLegacy` seeds `count` and `best` with the run of consecutive set bits from bit 0 of an old install's `streak_mask` (max 64), `last = 0` is **not** used: the caller sets `last` from `streak_last`; `ready = true`, `charge = 0`.)
- Produces: `Store.State streakState(Context)` is **not** added; instead `static boolean Store.shieldOn(Context c)` (all three installed) and `static String Store.streakLabel(Context c)`.
- Produces: `static boolean Trio.allInstalled(Context c)` = `installed(c, ZAMOLED) && installed(c, MAPS)`.
- Consumes: pref keys already used: `streak_count`, `streak_best`, `streak_last`, `streak_mask`; new keys `shield_ready` (boolean, default true) and `shield_charge` (int).

- [ ] **Step 1: Write `StreakTest.java`** (a `main` that loads `docs/streak-vectors.json` with the existing `org.json`, runs every case through `Streak.step`, compares all fields and `label`, prints `OK <n> cases` or the first mismatch and exits 1). Also tests `mask` is treated as unsigned (`Long.toUnsignedString`), and `fromLegacy(0b0111L)` gives `count = best = 3, ready = true, charge = 0` and `fromLegacy(-1L)` gives `64`.
- [ ] **Step 2: Run, expect failure** (class missing): `cd purrcast && javac -nowarn -d build/tools -classpath build/deps/json.jar app/src/main/java/app/skypix/core/*.java tools/app/skypix/tools/StreakTest.java`
- [ ] **Step 3: Implement `Streak.java`** following "The rules" above (unsigned shifts).
- [ ] **Step 4: Run** `java -cp build/tools:build/deps/json.jar app.skypix.tools.StreakTest ../docs/streak-vectors.json`; expected `OK 12 cases`.
- [ ] **Step 5: Rewrite `Store.touchStreak(Context)`** to load a `Streak.State` from the keys above, call `Streak.step(state, dayNumber(), shieldOn(c))`, and store it. Legacy rule: when `streak_count` is missing, start from `Streak.fromLegacy(streak_mask)` with `last` taken from `streak_last`. Keep `streak(c)`, `bestStreak(c)`, `streakMask(c)` working. Keep the existing early return when `last == today`.
- [ ] **Step 6: Wire detection and label.** `Trio.allInstalled`; `Store.shieldOn(c)` calls it; `Store.streakLabel(c)` returns `"day streak"` plus `" · " + Streak.label(...)` when the label is non-empty; `CatClubProvider.query` returns that label (value stays `Store.streak(c)`).
- [ ] **Step 7: Show it.** In `MainActivity` the streak card (`Ui.StreakCard`) gets a second line "Shield ready" / "Shield recharging, N days to go" only when `shieldOn`; the Cat Club row lists own streak plus the two sibling labels (read through `Trio.stat`).
- [ ] **Step 8: Verify** `bash scripts/preview.sh` still renders; run the StreakTest again; with an older sibling whose provider label is plain (`"5 day streak"`) the Cat Club row still reads `5 day streak` (check by reading `Trio.stat` output in a short `main`, or on a device). Commit `feat(purrcast): streak shield`.

### Task 3: Zamoled streak core and wiring
**Files:** Create `.../gen/StreakRules.kt`, `.../data/StreakStore.kt`, `zamoled/tools/preview/src/test/kotlin/StreakTest.kt`. Modify `MainActivity.kt` (`onCreate`/`onStart`), `data/Trio.kt`, `data/CatClubProvider.kt`, `ui/SettingsScreen.kt:218-245`.
**Interfaces:**
- Produces: `data class StreakState(val count: Int = 0, val best: Int = 0, val last: Long = 0, val mask: ULong = 0u, val ready: Boolean = true, val charge: Int = 0)`; `fun streakStep(s: StreakState, day: Long, shieldOn: Boolean): StreakState`; `fun shieldLabel(s: StreakState, shieldOn: Boolean): String` (all in `StreakRules.kt`, no Android imports).
- Produces: `object StreakStore { fun touch(context: Context): StreakState; fun read(context: Context): StreakState; fun label(context: Context): String }`; `fun Trio.allInstalled(context: Context): Boolean` (`installedVersion(PURRCAST) != null && installedVersion(MAPS) != null`).
- Stores in prefs file `zamoled`: `streak_count, streak_best, streak_last, streak_mask` (string of the unsigned decimal), `shield_ready, shield_charge`.

- [ ] **Step 1: Write `StreakTest.kt`** (JUnit, no Robolectric needed): loads `../../../docs/streak-vectors.json`, runs every case, asserts all fields and the label; mask compared as `ULong`.
- [ ] **Step 2: Run, expect failure:** `cd zamoled/tools/preview && ANDROID_HOME=/home/user/android-sdk ./gradlew test --tests StreakTest --console=plain -q`
- [ ] **Step 3: Implement `StreakRules.kt`** per "The rules" (`ULong` shifts).
- [ ] **Step 4: Run again;** expected: test passes (12 cases).
- [ ] **Step 5: Implement `StreakStore`** (day number from `TimeZone.getDefault().getOffset(now)`), call `StreakStore.touch(this)` in `MainActivity.onStart`.
- [ ] **Step 6: Provider.** `CatClubProvider.query` returns `value = streak count`, `label = StreakStore.label(context)` (`"day streak"` plus shield text); "wallpapers kept" stays visible in Zamoled's own Cat Club card only.
- [ ] **Step 7: Show it.** The Settings "The trio" card (`SettingsScreen.kt`) lists: own streak with shield state, wallpapers kept, and both sibling labels. Update the explanatory text there (it currently says "Purrcast adds its day streak and Maps and Cats its cats caught").
- [ ] **Step 8: Verify** `./gradlew :app:assembleDebug` succeeds and the test passes. Commit `feat(zamoled): day streak and shield`.

### Task 4: Maps and Cats streak core and wiring
**Files:** Create `mapsandcats/streak.js`, `mapsandcats/tools/streak.test.js`. Modify `index.html` (script tag before `app.js`), `sw.js` (add `'streak.js'` to `SHELL`), `android/app/build.gradle.kts` (add `"streak.js"` to the asset `include(...)` list), `app.js`, `android/.../MainActivity.java` (bridge), `android/.../CatClubProvider.java`.
**Interfaces:**
- Produces (global in browser, `module.exports` in node): `streakStep(s, day, shieldOn)` where `s = {count, best, last, mask (decimal string), ready, charge}`; `streakLabel(s, shieldOn)`; `streakDay(nowMs, tzOffsetMs)`.
- Produces: bridge `@JavascriptInterface void setStreak(int days, String label)` (stores `streak_days`, `streak_label` in the `catclub` prefs); provider returns `value = streak_days`, `label = streak_label` (default `"day streak"`).
- Consumes: bridge `TRIO.installed(pkg)` for `app.skypix.weather` and `com.nikopick.zamoled`.

- [ ] **Step 1: Write `streak.test.js`** (node; loads the vectors, uses `BigInt` for masks, prints `OK 12 cases`).
- [ ] **Step 2: Run, expect failure:** `node mapsandcats/tools/streak.test.js`
- [ ] **Step 3: Implement `streak.js`** per "The rules" (`BigInt.asUintN(64, ...)`).
- [ ] **Step 4: Run; expected `OK 12 cases`.**
- [ ] **Step 5: Wire.** `S.streak` (default fresh state) is saved with the rest of `S`; `touchStreak()` runs at startup and on `document.visibilitychange` to visible, using `streakDay(Date.now(), -new Date().getTimezoneOffset() * 60000)`; then `TRIO.setStreak(S.streak.count, 'day streak' + shieldText)`. Extend `refreshTrio()` so the Cat Club line shows `own streak · cats caught · sibling labels`.
- [ ] **Step 6: Verify in a phone-size browser** (Playwright, `serviceWorkers:'block'`): no console errors; the Cat Club line shows the streak; open the page, set the clock a day later with `page.clock`, confirm the count goes to 2. Commit `feat(mapsandcats): day streak and shield`.

### Task 5: TRIO.md and help text
**Files:** Modify `docs/TRIO.md`; each app's "How it works" text (`MainActivity.java:994-1009` in Purrcast, `SettingsScreen.kt` in Zamoled, the Cat Club `<details>` in `mapsandcats/index.html`).
- [ ] **Step 1:** In TRIO.md replace the "Streaks and rewards" section with: every app has its own streak under the same rules, the shield rule, and the new provider rule (`label` may end with `· shield ready` or `· shield recharging, N days to go`).
- [ ] **Step 2:** Update the three help texts to say the same in plain words.
- [ ] **Step 3: Verify** by search that no help text still says only Purrcast has a streak: `grep -rn "Purrcast streak\|Purrcast adds its day streak" docs purrcast/app zamoled/app mapsandcats/index.html`. Commit `docs: trio streaks and shield`.
