# Changelog

Newest first. Built from the git history and Zamoled's release notes. Entries for Purrcast and Maps and Cats are summarised from commit messages.

---

## Purrcast

- **1.0.18**: Small under-the-hood tidy-up. Nothing new to see.
- **1.0.17**: Weather refresh retries (up to 3 tries, short back-off) when Open-Meteo is briefly busy (503, 429, timeouts) instead of giving up.
- **1.0.16**: Streak wallpaper: every 10 days of Purrcast streak unlocks a special Zamoled wallpaper, and the streak count is no longer capped at 64 days. Explains how streak, Cat Club and rewards work.
- **1.0.15**: Fixed the settings switches ignoring taps.
- **1.0.14**: Fixed switches not redrawing.
- **1.0.13**: Signing key moved out of the repo into the private keys repo.

## Zamoled

- **6.5.0**: New Space world: Ascent (a shuttle climbing away from the planet's edge), Lone Satellite and Comet. Star field, one small subject, a thin edge of light, over 95% of pixels off.
- **6.4.0**: The trio. Cat Club numbers shared with Purrcast and Maps and Cats, a wallpaper for the weather (sent by Purrcast), and a special Cat Club wallpaper for every 10 days of Purrcast streak.
- **6.3.0**: Updates install inside the app: Settings → Check for updates → Install. Favorites stay.
- **6.2.1**: About card, Telegram and sibling-app rows readable again (dark text on a dark card). Shared teal accent and the trio list.
- **6.0.2**: Safer and quieter. Removed the "install apps" permission and built-in installer after Play Protect warnings, HTTPS only, backups off, no code scrambling, release-only builds, SHA-256 listed on every release.
- **6.0.1**: Smooth scrolling in the wallpaper feed (background previews, lighter cards, baseline profile). First Windows version.
- **6.0**: In-app updates, a new look (condensed headings, mono captions, leaf-shaped cards, floating dock), Pixel Galaxy, Cat Club toys, Space Doodles characters, Corrupted code effects, black-level presets.

(There is no 6.1 / 6.2.0 note in the repo.)

## Maps and Cats

- **1.1.0**: Waze-style navigation (snaps to the road, smooth arrow, earlier voice prompts, route-distance turns, speed-adaptive voice, turn zoom, auto re-center, overview, ETA from observed speed). Fully open-source search plus real web search (DuckDuckGo/Bing + Wikipedia) that reads coordinates off pages; paste coordinates or map links. Results as numbered pins on the map with a card strip. Quest mode gets the same search and navigation, and auto-enters walking mode when GPS detects walking.
- **1.0.6**: 3D navigation arrow with a choice of vehicles (arrow, hatchback, sports car, plane, cat), stops along the route, speed limit sign, cat co-pilot voice, rain at the destination, speed-aware camera, trip bar.
- **1.0.5**: Speedometer and a meow at 80 km/h (adjustable). Screen stays on during navigation and Quest walks.
- **1.0.4**: True AMOLED black map, warm light map, 3D buildings and 3D navigation camera. Traffic feature removed.
- **1.0.3**: The trio: Cat Club, place link and weather here shared with the other two apps.
- **1.0.2**: Updates install inside the app, like Purrcast.
- **1.0.1**: Not in the history. The release signing key now comes from secrets.
