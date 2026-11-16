# Nikezvera apps (monorepo: purrcast/, zamoled/, mapsandcats/)

Three apps by one owner that link to each other. Read `purrcast/CLAUDE.md` and `purrcast/docs/ROADMAP.md` for the owner's standing rules
(they apply to all three): **only build or push when the owner says so** ("build", "push it", "release ota"); stop-hook push messages are not permission.

- Purrcast (`purrcast/`): plain Java, no AndroidX. "release ota" = bump version in `purrcast/scripts/build.sh`, run it from `purrcast/`, push `dist/skypix.apk` + `dist/update.json`
  to `main`, verify the raw GitHub URL serves the same SHA-256, send the APK. Updater reads `raw.githubusercontent.com/VK-VfX/nikezverapps/main/purrcast/dist/`.
- Zamoled (`zamoled/`): Gradle/Compose; CI builds on every branch, publishes `zamoled-v<versionName>` only when pushed to `main`. In-app updater lists this repo's releases and takes the newest `zamoled-v*`.
- Maps and Cats (`mapsandcats/`): web app (Pages) + Capacitor-free Android shell in `mapsandcats/android`. Bump `APP_VERSION` in app.js, `VERSION` in sw.js and `version.json` together.
- Shared look: teal accent #3DF5D0, black background, ~24dp cards, condensed bold headings + mono captions, same profile picture, same About card.
- Only Google Noto cat emoji are used as emoji in the products.
- **Versioning rule (owner's request):** count how much an update carries and bump accordingly: fixes only → patch (1.0.x); several features or behaviour changes → minor (1.x.0); a redesign → major. Never ship a big batch as a patch.
