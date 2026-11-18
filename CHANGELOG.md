# Changelog

Newest first. Built from the git history and Zamoled's release notes. Entries for Purrcast and Maps and Cats are summarised from commit messages.

---

## Purrcast

- **2.2.0**: One shared streak (the highest across the three apps), a log for bug reports in About, open-source notices, clearer privacy and alert wording.
- **2.1.0**: A day streak of your own plus the Streak Shield (saves up to 7 missed days when all three apps are installed). MET Malaysia alerts removed; warnings use plain wording from Open-Meteo only. Hero cat art and the weather mood are shared with the other two apps.
- **2.0.0**: New look: snappier taps, smoother cards, a teal edge on every card, clearer text in light mode.
- **1.0.18**: Small under-the-hood tidy-up. Nothing new to see.
- **1.0.17**: Weather refresh retries (up to 3 tries, short back-off) when Open-Meteo is briefly busy (503, 429, timeouts) instead of giving up.
- **1.0.16**: Streak wallpaper: every 10 days of Purrcast streak unlocks a special Zamoled wallpaper, and the streak count is no longer capped at 64 days. Explains how streak, Cat Club and rewards work.
- **1.0.15**: Fixed the settings switches ignoring taps.
- **1.0.14**: Fixed switches not redrawing.
- **1.0.13**: Signing key moved out of the repo into the private keys repo.

## Zamoled

- **8.2.1**: The Windows zip now carries the licences of the fonts and libraries it ships (THIRD-PARTY.txt). Nothing else changes.
- **8.2.0**: One shared streak (the highest across the three apps), a log for bug reports in About (shows why an update install failed), open-source notices.
- **8.1.0**: Your own day streak and the Streak Shield, shown in the Cat Club card. Hero cat art is bundled for what comes next.
- **8.0.0**: Night Photos world, short editor tabs, calmer buttons, denser or airier doodles. "For Pixel" removed.
- **6.5.0**: New Space world: Ascent (a shuttle climbing away from the planet's edge), Lone Satellite and Comet. Star field, one small subject, a thin edge of light, over 95% of pixels off.
- **6.4.0**: The trio. Cat Club numbers shared with Purrcast and Maps and Cats, a wallpaper for the weather (sent by Purrcast), and a special Cat Club wallpaper for every 10 days of Purrcast streak.
- **6.3.0**: Updates install inside the app: Settings → Check for updates → Install. Favorites stay.
- **6.2.1**: About card, Telegram and sibling-app rows readable again (dark text on a dark card). Shared teal accent and the trio list.
- **6.0.2**: Safer and quieter. Removed the "install apps" permission and built-in installer after Play Protect warnings, HTTPS only, backups off, no code scrambling, release-only builds, SHA-256 listed on every release.
- **6.0.1**: Smooth scrolling in the wallpaper feed (background previews, lighter cards, baseline profile). First Windows version.
- **6.0**: In-app updates, a new look (condensed headings, mono captions, leaf-shaped cards, floating dock), Pixel Galaxy, Cat Club toys, Space Doodles characters, Corrupted code effects, black-level presets.

(There is no 6.1 / 6.2.0 note in the repo.)

## Zamoled for Windows

- **1.0.0**: Now released on its own (tag `zamoled-windows-v<version>`), separately from the Android app, so a phone update no longer means a Windows update. Same generators and the same look as Zamoled 8.2.1.

## Maps and Cats

- **2.3.0**: One shared streak, a log for bug reports in About, open-source notices, navigation and privacy wording. Web search now uses Wikipedia only; typing searches Photon, Enter also asks Nominatim.
- **2.2.0**: Your own day streak and the Streak Shield, shown in the Cat Club line. Hero cat art is bundled for what comes next.
- **2.1.0**: Green Start and red End buttons, honest time ranges (no live traffic), 3D switch moved to Settings, clearer map buttons.
- **1.1.0**: Waze-style navigation (snaps to the road, smooth arrow, earlier voice prompts, route-distance turns, speed-adaptive voice, turn zoom, auto re-center, overview, ETA from observed speed). Fully open-source search plus real web search (DuckDuckGo/Bing + Wikipedia) that reads coordinates off pages; paste coordinates or map links. Results as numbered pins on the map with a card strip. Quest mode gets the same search and navigation, and auto-enters walking mode when GPS detects walking.
- **1.0.6**: 3D navigation arrow with a choice of vehicles (arrow, hatchback, sports car, plane, cat), stops along the route, speed limit sign, cat co-pilot voice, rain at the destination, speed-aware camera, trip bar.
- **1.0.5**: Speedometer and a meow at 80 km/h (adjustable). Screen stays on during navigation and Quest walks.
- **1.0.4**: True AMOLED black map, warm light map, 3D buildings and 3D navigation camera. Traffic feature removed.
- **1.0.3**: The trio: Cat Club, place link and weather here shared with the other two apps.
- **1.0.2**: Updates install inside the app, like Purrcast.
- **1.0.1**: Not in the history. The release signing key now comes from secrets.
