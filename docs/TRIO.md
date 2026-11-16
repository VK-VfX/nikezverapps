# How Purrcast, Zamoled and Maps and Cats work together

Each app is complete on its own. Every link below checks that the other app is installed (`<queries>` in each manifest)
and quietly falls back to the project's GitHub page, or hides itself, when it is not. No accounts, no background syncing:
data moves only when you tap something, or as tiny read-only counts.

| App | Package | Cat Club provider (read-only) | Deep link it receives |
|---|---|---|---|
| Purrcast | `app.skypix.weather` | `content://app.skypix.weather.catclub/stats` → `value`, `label` (day streak) | `purrcast://weather?lat=&lon=&name=` saves that place and fetches its forecast |
| Zamoled | `com.nikopick.zamoled` | `content://com.nikopick.zamoled.catclub/stats` (wallpapers kept) | `zamoled://w?k=<recipe>` a shared wallpaper; `zamoled://reward?day=10` the streak wallpaper; `zamoled://weather?mood=rain\|storm\|cloudy\|clear\|night\|snow\|fog` a wallpaper for the weather, with Purrcast's quiet zones |
| Maps and Cats | `app.mapsandcats` | `content://app.mapsandcats.catclub/stats` (cats caught) | `mapsandcats://place?lat=&lon=&name=` shows that place and offers a route |

## What you see
- **Cat Club line** in each app's "The trio" list: its own number plus the siblings' (day streak, wallpapers kept, cats caught).
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

## Streaks and rewards (what is real today)
- **Purrcast streak**: opening the app counts as that day's visit. Consecutive days grow it, a missed day resets it to 1, the best is kept (64-day history).
  The streak-card cat is a cloud cat at 1-2 days, partly sunny at 3, sunny at 7, sunglasses at 14+. Cheers at 3, 7, 14, 30, 100 days.
- **Maps and Cats**: cats caught (Quest). A catch is 10-90 XP by rarity, walking is 1 XP per 25 m, level = 1 + floor(sqrt(XP / 60)).
- **Zamoled**: wallpapers kept (hearted favourites).
- **Rewards**: cat moods, cheers, levels, and one real unlock: **every 10 days of Purrcast streak earns a special Zamoled wallpaper**.
  Counted from the *best* streak (a missed day never takes a reward back); Purrcast tracks which ones were claimed.
  Link: `zamoled://reward?day=10` (day must be a multiple of 10). It opens the Cat Club ("gatti") style, one fixed picture per day number,
  with a new colour set each step and Purrcast's quiet zones. Anything new must be added here and in each app's help text.
