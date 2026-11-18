# Nikezvera apps

Three Android apps in one public repo (icons and latest downloads below). They open each other ("The trio" list in each app's settings) and share the Cat Club (see `docs/TRIO.md`).

## Download

| | App | Latest | Download |
|---|---|---|---|
| <img src="docs/icons/purrcast.png" width="64" alt="Purrcast icon"> | **Purrcast**: cat-themed weather app and widgets (`app.skypix.weather`) | 2.5.0 | [**Download APK**](https://raw.githubusercontent.com/VK-VfX/nikezverapps/main/purrcast/dist/skypix.apk) |
| <img src="docs/icons/zamoled.png" width="64" alt="Zamoled icon"> | **Zamoled**: pitch-black AMOLED wallpapers (`com.nikopick.zamoled`) | 8.5.0 | [**Download APK**](https://github.com/VK-VfX/nikezverapps/releases/download/zamoled-v8.5.0/zamoled-release.apk) · [Windows app 1.0.0](https://github.com/VK-VfX/nikezverapps/releases/download/zamoled-windows-v1.0.0/Zamoled-Windows-x64.zip) |
| <img src="docs/icons/mapsandcats.png" width="64" alt="Maps and Cats icon"> | **Maps and Cats**: keyless map and navigation, with Quest mode (`app.mapsandcats`) | 2.6.0 | [**Download APK**](https://github.com/VK-VfX/nikezverapps/releases/download/mapsandcats-v2.6.0/MapsAndCats-v2.6.0.apk) |

All three update themselves in-app after the first install. No sign-up, no accounts, no data collection. Older versions: [Releases](https://github.com/VK-VfX/nikezverapps/releases).

## What is where

| Folder | App | What |
|---|---|---|
| `purrcast/` | Purrcast | Plain Java, built with `purrcast/scripts/build.sh`. OTA: `purrcast/dist/update.json`. |
| `zamoled/` | Zamoled | Kotlin + Compose + Gradle. Releases tagged `zamoled-v<version>`. |
| `mapsandcats/` | Maps and Cats | Web app in an Android shell. Releases tagged `mapsandcats-v<version>`. |

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

- **README rule:** every release also updates the Download table above (version and link) in the same push.
