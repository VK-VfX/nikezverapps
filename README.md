# Nikezvera apps

Three Android apps in one public repo. They open each other ("The trio" list in each app's settings) and share the Cat Club (see `docs/TRIO.md`).

| Folder | App | Package | Version | What |
|---|---|---|---|---|
| `purrcast/` | Purrcast | `app.skypix.weather` | 1.0.16 | Cat-themed weather app and widgets. Plain Java, built with `purrcast/scripts/build.sh`. OTA: `purrcast/dist/update.json`. |
| `zamoled/` | Zamoled | `com.nikopick.zamoled` | 6.4.0 | Generative pitch-black AMOLED wallpapers. Kotlin + Compose + Gradle. Releases tagged `zamoled-v<version>`. |
| `mapsandcats/` | Maps and Cats | `app.mapsandcats` | 1.1.0 | Keyless map and navigation app (web app in an Android shell), plus Quest mode. APKs tagged `mapsandcats-v<version>`. |

## Maps and Cats highlights
- Pitch-black AMOLED map (warm light mode), 3D buildings and a tilted, heading-up navigation camera.
- Waze/Google-style navigation: snaps to the road, smooth arrow (arrow, hatchback, plane and more), speed-aware voice prompts, "then" preview, trip bar, speed limit and speedometer, cat co-pilot voice.
- Open-source search (Photon + Nominatim), paste coordinates or map links, and a web search (DuckDuckGo/Bing/Wikipedia) that reads coordinates off the pages it finds. Results show as pins on the map.
- Quest mode: walking only, cats to catch, and an automatic walking mode when the GPS sees you moving.

## Building and releasing
- Each app updates itself in-app from this repo (Purrcast reads `purrcast/dist/update.json` on `main`; Zamoled and Maps and Cats read their GitHub releases).
- CI lives in `.github/workflows/`: Zamoled and Maps and Cats each build when their own folder changes and publish a release when pushed to `main`. Purrcast is built locally with `build.sh`.
- Signing keys are **not** in this repo; they are kept privately and supplied to CI as secrets.
- Versioning: fixes bump the patch (1.0.x), several features bump the minor (1.x.0), a redesign bumps the major.
