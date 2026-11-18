# How Purrcast, Zamoled and Maps and Cats work together

Each app is complete on its own. Every link below checks that the other app is installed (`<queries>` in each manifest)
and quietly falls back to the project's GitHub page, or hides itself, when it is not. No accounts, no background syncing:
data moves only when you tap something, or as tiny read-only counts.

| App | Package | Cat Club provider (read-only: day streak and shield text) | Deep link it receives |
|---|---|---|---|
| Purrcast | `app.skypix.weather` | `content://app.skypix.weather.catclub/stats` → `value`, `label`, `best`, `last`, `mood` | `purrcast://weather?lat=&lon=&name=` saves that place and fetches its forecast |
| Zamoled | `com.nikopick.zamoled` | `content://com.nikopick.zamoled.catclub/stats` (day streak, shield) | `zamoled://w?k=<recipe>` a shared wallpaper; `zamoled://reward?day=10` the streak wallpaper; `zamoled://weather?mood=rain\|storm\|cloudy\|clear\|night\|snow\|fog` a wallpaper for the weather, with Purrcast's quiet zones |
| Maps and Cats | `app.mapsandcats` | `content://app.mapsandcats.catclub/stats` (day streak, shield) | `mapsandcats://place?lat=&lon=&name=` shows that place and offers a route |

## What you see
- **Cat Club line** in each app's "The trio" list: its own day streak (with the shield state) plus the siblings' streaks.
- **Purrcast, Saved places → Maps**: opens that place in Maps and Cats.
- **Maps and Cats, a destination → "Weather here"**: opens Purrcast and saves the place.
- **Purrcast, The trio → "Wallpaper for this weather"**: opens Zamoled with a matching wallpaper (mood from the current forecast).
- **Version line** per sibling: installed version, and "v1.2.3 is out, open to update" when GitHub has a newer one.
  (Each app updates itself in-app; the line is a reminder, and Open takes you there.)

## Rules for new links
1. Add the target package to `<queries>` and use `resolveActivity` / `getLaunchIntentForPackage` before opening.
2. Pass only what the other app needs (a place, a mood); never an identifier or anything personal.
3. Counts only through the Cat Club provider, one row, columns `value` (int) and `label` (short text).
4. Write the link in this file first.

## Streaks and the Streak Shield (what is real today)
- **One shared streak, highest wins (owner's change, replaces "every app has its own").** Every app keeps its own day streak with identical rules, and each time it opens it reads the other two apps' streak (`value`, `best`, `last`) and takes the highest one that is still alive (visited today or yesterday). The apps converge on the same number without any app being the leader, and nothing leaves the phone. The rule is `adopt` in `docs/streak-vectors.json`. Opening the app counts as that day's visit (the phone's local calendar day).
  The next day adds 1; a longer gap resets it to 1; the best is kept. State per app: count, best, last visit day, a 64-day history, shield state.
- **Streak Shield**: while **all three apps are installed**, a gap of 1 to 7 missed days does not reset the streak (missed days add nothing, today adds 1).
  It saves once, then recharges after 7 visit days in a row. Each app has its own shield; uninstalling an app switches it off but never removes earned days.
- **One set of test cases** (`docs/streak-vectors.json`) is checked in Purrcast (`purrcast/tools/.../StreakTest.java`), Zamoled
  (`zamoled/tools/preview/.../StreakTest.kt`) and Maps and Cats (`mapsandcats/tools/streak.test.js`), so the rules cannot drift apart. Change a rule in all three and the vectors together.
- **Cat Club provider**: `value` is the day streak; `label` is `day streak`, optionally followed by ` · shield ready` or ` · shield recharging, N days to go`.
  Older versions show a plain label; that still reads correctly.
- **Streak card cat** (Purrcast): cloud cat at 1-2 days, partly sunny at 3, sunny at 7, sunglasses at 14+. Cheers at 3, 7, 14, 30, 100 days.
- **Maps and Cats** also keeps cats caught (Quest). A catch is 10-90 XP by rarity, walking is 1 XP per 25 m, level = 1 + floor(sqrt(XP / 60)). **Zamoled** also keeps wallpapers kept (hearted favourites).
- **Rewards**: every **10 days of Purrcast best streak** earns a special Zamoled wallpaper (counted from the *best* streak, so a missed day never takes a reward back; Purrcast tracks which were claimed).
  Link: `zamoled://reward?day=10` (day must be a multiple of 10). It opens the Cat Club ("gatti") style, one fixed picture per day number,
  with a new colour set each step and Purrcast's quiet zones. Anything new must be added here and in each app's help text.

## Log catcher (all three apps)
About, right under the Telegram row: **Log for bug reports** with Share and Clear. Each app keeps a small log on the phone only (about 48 KB, oldest half dropped when full): starts, crashes (with stack trace), why the app last stopped (Android's exit reasons), update check/download/checksum/installer status codes and messages, network failures by host and status, and in Maps and Cats page errors, map errors and location error codes. It never records places, searches, typed text or coordinates (decimals that look like coordinates and URL query strings are stripped as they are written). Nothing is sent anywhere: Share opens Android's share sheet and the person chooses where it goes.

## Hero cat on the streak cards (first step)
When Purrcast, Zamoled and Maps and Cats are all installed, the streak card (Purrcast), the Cat Club card (Zamoled) and the Cat Club block (Maps and Cats) show the masked hero cat in the mood Purrcast chose from the weather (see `HeroMood`; shared as the `mood` column of the Cat Club provider). Until then they show the locked silhouette and a line saying to install all three. The widgets, the Hero Club wallpapers and the walking parade come later. The violet ring next to it counts days to the next 10-day reward.

## Hero cat everywhere (complete)
- **Unlock rule:** Purrcast, Zamoled and Maps and Cats all installed, and the app's own "Show hero cat" switch on (each app keeps its own switch, default on). Nothing is sent anywhere; each app checks which siblings are installed.
- **Purrcast:** every widget cat becomes the hero cat in the emotion of that weather (`HeroMood.fromIcon`); notifications and text keep the plain cat emoji. The streak card shows the hero in Purrcast's weather mood. If a sibling is uninstalled, the next refresh falls back to the normal cats.
- **Zamoled:** a "Hero Club" wallpaper style in Doodle (pure black, corners exactly #000000): the big cats show Purrcast's mood, the rest come from the calm set. Hidden while locked; saved favourites of it still open.
- **Maps and Cats:** a walking parade of five hero cats across the Cat Club block in the drawer (the weather mood leads). A still row when the phone's "remove animations" setting is on.
- **Angry and sad** only appear when the weather is bad (they come only from Purrcast's mood); everything chosen for decoration is from the calm set.
- **Locked:** a silhouette and "Install all three apps to meet the hero cat".

## Save codes (complete)
- Settings, **Progress**: *Save my progress* and *Restore progress* in all three apps. Codes are **per app**: one app never reads or writes another app's code.
- Prefixes: `PRC1` (streak + rewards claimed), `ZAM1` (streak), `MAC1` (streak + xp + cats caught). Crockford base32 in groups of four with a CRC-16; shared vectors in `docs/save-code-vectors.json` are checked by Java, Kotlin and JS tests.
- Ways to keep it: share to notes or chat, a screenshot card (Pictures/Trio, Android 10+), a calendar event, a text file, copy. An optional full backup `.json` carries saved places (Purrcast, Maps and Cats), favourites (Zamoled) and a few settings.
- Restoring never lowers anything: bests, xp, cats caught and rewards take the larger number; the newer streak block wins. A pasted note with several codes works; a wrong-app code, a typo and an empty paste each get a friendly message.
- No new permissions, no network, no camera.

## Hero cat art (v2)
Five mask looks, each with its own cape and collar colour: red domino, blue wing, purple visor, green goggles and rose cat-eye. Every emotion exists in every look (`hero-<emotion>-<0..4>`), plus one locked silhouette; made by `trio-art/hero-cats/build.py` with soft shading (mask sheen, cape gradient, forehead light, chin shade) and shipped at 512 px (Maps and Cats uses the vector SVG). A different look is picked each time: every widget refresh in Purrcast, each cat in Zamoled's Hero Club and in the Maps and Cats parade, and every time a card is shown.
Widgets show the whole place name: it wraps and shrinks before anything is cut; on the wide card the country is dropped first.
