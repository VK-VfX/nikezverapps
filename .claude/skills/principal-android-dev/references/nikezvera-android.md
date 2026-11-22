# How the three apps are built, signed and updated

Facts below were read from the repo (check the file before relying on a number; versions move).

| | Purrcast | Zamoled | Maps and Cats |
|---|---|---|---|
| Package | `app.skypix.weather` | `com.nikopick.zamoled` | `app.mapsandcats` |
| Language / UI | Plain Java, no AndroidX, no Google services | Kotlin, Gradle, Jetpack Compose | Web app (HTML/JS/CSS, Pages) in a Capacitor-free Android shell |
| Source | `purrcast/app/src/main/java` | `zamoled/app/src/main` | `mapsandcats/*.js` + `mapsandcats/android/app` |
| minSdk / targetSdk | 26 / 34 (`purrcast/scripts/build.sh`, aapt flags) | 29 / 35 (`zamoled/app/build.gradle.kts`) | 24 / 34 (`mapsandcats/android/app/build.gradle.kts`) |
| Build | `purrcast/scripts/build.sh`: aapt, javac `--release 8`, dx, zipalign, apksigner (v1+v2+v3). No lambdas. | `./gradlew assembleRelease` | Gradle for the shell; web part is static files |
| Version lives in | `VERSION_CODE`/`VERSION_NAME` in `build.sh` | `versionCode`/`versionName` in `app/build.gradle.kts` | `APP_VERSION` in `app.js`, `VERSION` in `sw.js`, `version.json`, and the Gradle `versionName`/`versionCode` (use `mapsandcats/bump-version.sh <ver> "<notes>"`, it keeps them in step) |
| Release path | Manual "release ota": push `purrcast/dist/skypix.apk` + `dist/update.json` to `main` | CI (`zamoled-build.yml`) builds on every branch; publishes release `zamoled-v<versionName>` only from `main` | CI (`mapsandcats-apk.yml`) builds; publishes a GitHub Release from `main` |
| In-app updater | Reads `raw.githubusercontent.com/VK-VfX/nikezverapps/main/purrcast/dist/` (`update.json` with `versionCode`, `versionName`, `sha256`); `ApkUpdater.java` | Lists this repo's releases, takes the newest `zamoled-v*` | Web: `version.json` + service worker; shell updater per its Android code |
| Signing | Private `nikezverapps-keys` repo (`purrcast/purrcast.jks`, `.pass`), path via `NIKEZ_KEYS` or sibling folder | CI secrets `ZAMOLED_KEYSTORE_BASE64`/`ZAMOLED_KEY_PASSWORD`, else a CI key kept in the Actions cache (same key reused every run) | CI secrets `MAPSANDCATS_KEYSTORE_BASE64`/`MAPSANDCATS_KEY_PASSWORD` |
| Tests | `purrcast/tools/` (Java, e.g. `StreakTest`), `scripts/preview.sh` modes | `zamoled/tools/preview/` (Kotlin, `StreakTest`, draw tests) | `mapsandcats/tools/*.test.js` |
| Notes | `scripts/gen_widgets.py` is the single source of widgets; run it, then regenerate previews | `zamoled/release-notes/<ver>.md`, `default.md` | Pages deploy |

Zamoled for Windows is separate: `zamoled/tools/desktop/VERSION`, tag `zamoled-windows-v<version>`, workflow `zamoled-windows.yml`. Android releases never need a Windows zip.

## Purrcast specifics
- Declared permissions include fine/coarse/background location, internet, network state, notifications, boot completed, install packages, foreground service (special use). Location is the only runtime permission asked up front.
- Network (Open-Meteo) is blocked in the build sandbox: parsers cannot be tested live, so write them defensively and rely on fixtures in `purrcast/tools/fixtures`.
- Owner is in Malaysia (Shah Alam): never require weather that does not happen there for a core feature.
- Only Google Noto cat emoji as emoji.

## The ecosystem contract (`docs/TRIO.md` is the source of truth)
- Cat Club providers, read-only: `content://app.skypix.weather.catclub/stats`, `content://com.nikopick.zamoled.catclub/stats`, `content://app.mapsandcats.catclub/stats`; columns `value` (int), `label`, and for Purrcast `best`, `last`, `mood`.
- Deep links: `purrcast://weather?lat=&lon=&name=`, `zamoled://w?k=<recipe>`, `zamoled://reward?day=10`, `mapsandcats://place?lat=&lon=&name=`.
- Every link: package in `<queries>`, `resolveActivity`/`getLaunchIntentForPackage` before opening, quiet fallback if the sibling is missing, only the data the other app needs, written in `docs/TRIO.md` first.
- Shared rules checked by shared vectors: `docs/streak-vectors.json`, `docs/save-code-vectors.json`, `docs/place-names-vectors.json`, `docs/hero-cat-vectors.json`. Changing a rule means changing all three apps and the vectors together.
- Save codes: prefixes `PRC1`, `ZAM1`, `MAC1`, Crockford base32 with CRC-16, per app. Restoring never lowers a number.
- Log catcher in all three apps (About, "Log for bug reports", about 48 KB, phone only).

## Installed-base invariants
1. `versionCode` only rises. 2. Signing key never changes. 3. Saved favourites, recipes and codes keep decoding. 4. A seeded generator draws the same picture for the same seed. 5. A missing or older sibling never breaks an app. 6. No accounts, no background syncing, no analytics.

## Common commands
- Purrcast build (only when told): `cd purrcast && scripts/build.sh`. Previews: `MODE=previews scripts/preview.sh /tmp/out`.
- Zamoled build (only when told): `cd zamoled && ./gradlew assembleRelease`.
- Maps and Cats version bump: `cd mapsandcats && ./bump-version.sh 2.6.1 "notes"`.
- Verify a signature: `apksigner verify --verbose --print-certs <apk>`; checksum: `sha256sum <apk>`.
