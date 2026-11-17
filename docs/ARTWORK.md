# Artwork provenance

| Artwork | Where | How it was made |
|---|---|---|
| App icons (Purrcast, Maps and Cats, Zamoled launcher icons) and the profile picture | `docs/icons/`, each app's `res/` | Generated with Google Gemini by the owner (confirmed by the owner 2026-11-17 for the app icons; the profile picture is not confirmed, add it here when known). Zamoled's launcher icon is also written out as vector paths in `zamoled/app/src/main/res/drawable/ic_launcher_foreground.xml`. |
| Cat artwork inside the apps | `mapsandcats/assets/cats`, hero cats | Google Noto Emoji (Apache-2.0) via svgmoji (MIT). |
| Hero cat (mask, cape, collar, faces) | `trio-art/hero-cats/build.py` | Drawn in code by the project on top of the Noto cat head. |
| Night Photos | Zamoled `assets/photos` | Unsplash photographers, credited in `CREDITS.txt`. |

Notes for the owner (not legal advice):
- Gemini's terms let you use what you generate, including in apps. Whether purely AI-generated images carry copyright is unsettled in many countries (the US Copyright Office says no unless a person adds creative work), so you may not be able to stop others copying the icons. A hand-edited version or an icon drawn by you would be stronger.
- Do not use an icon that looks like a real brand's logo. Check each generated icon once against that.
- Keep the prompt and date of generation for each icon if you can; it helps if anyone asks where they came from.
