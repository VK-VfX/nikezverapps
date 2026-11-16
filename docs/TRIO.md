# How Purrcast, Zamoled and Maps and Cats work together

Each app is complete on its own. Every link below checks that the other app is installed (`<queries>` in each manifest)
and quietly falls back to the project's GitHub page, or hides itself, when it is not. No accounts, no background syncing:
data moves only when you tap something, or as tiny read-only counts.

| App | Package | Cat Club provider (read-only) | Deep link it receives |
|---|---|---|---|
| Purrcast | `app.skypix.weather` | `content://app.skypix.weather.catclub/stats` → `value`, `label` (day streak) | `purrcast://weather?lat=&lon=&name=` saves that place and fetches its forecast |
| Zamoled | `com.nikopick.zamoled` | `content://com.nikopick.zamoled.catclub/stats` (wallpapers kept) | `zamoled://w?k=<recipe>` a shared wallpaper; `zamoled://weather?mood=rain\|storm\|cloudy\|clear\|night\|snow\|fog` a wallpaper for the weather, with Purrcast's quiet zones |
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
