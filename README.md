# Nikezvera apps

Three Android apps in one repo. They open each other ("The trio" list in each app's settings).

| Folder | App | Package | What |
|---|---|---|---|
| `purrcast/` | Purrcast | `app.skypix.weather` | Cat-themed weather app and widgets. Plain Java, built with `purrcast/scripts/build.sh`. OTA: `purrcast/dist/update.json`. |
| `zamoled/` | Zamoled | `com.nikopick.zamoled` | Generative pitch-black AMOLED wallpapers. Kotlin + Compose + Gradle. Releases tagged `zamoled-v<version>`. |
| `mapsandcats/` | Maps and Cats | `app.mapsandcats` | Keyless map and navigation PWA with an Android shell, plus Quest mode. Web app deployed to GitHub Pages; APKs tagged `mapsandcats-v<version>`. |

CI lives in `.github/workflows/` (one set per app, each runs only when its own folder changes).
Purrcast's signing key is in `purrcast/keystore/`, so keep this repo private.
