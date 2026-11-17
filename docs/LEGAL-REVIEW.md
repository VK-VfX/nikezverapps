# Legal review: Purrcast, Zamoled, Maps and Cats

Date: 2026-11-17. **This is an engineering review of what the code and files do, not legal advice.** Nothing here is a verdict from a lawyer;
the "get a lawyer" items are marked. Everything below is read from the repo as it is today.

## Summary
| # | Finding | App | Risk | Fix |
|---|---|---|---|---|
| 1 | No `LICENSE` file in the repo (public repo, all rights reserved by default) | all | Medium | Pick a licence and add it, see 1 |
| 2 | Maps and Cats web search scrapes DuckDuckGo HTML and Bing result pages | Maps | **High** | Remove both, keep Wikipedia, see 2 |
| 3 | "No data collection" wording is not exact: your location and typed searches do go to third-party services | all | Medium | Reword and add a short privacy note, see 3 |
| 4 | Weather warnings are now our own forecast maths, with no disclaimer | Purrcast | Medium | One disclaimer line in About and Settings, see 4 |
| 5 | Navigation and ETA have no safety or accuracy disclaimer | Maps | Medium | One line in About and before the first route, see 5 |
| 6 | Public routing and search servers (OSRM demo, Nominatim, Photon) have usage policies | Maps | Medium | Rate limits, identify the app, keep "your own server" setting, see 6 |
| 7 | Third-party licence texts are shipped for some parts, not all | all | Low | Add an in-app "Open-source notices" page, see 7 |
| 8 | Who owns the three app icons and the cat art? | all | Check | Write down where each came from, see 8 |
| 9 | Open-Meteo's free tier is for non-commercial use | Purrcast | Low today | Keep the app free and ad-free, or buy a plan, see 9 |
| 10 | Sibling apps can read your streak (exported provider, no signature check) | all | Low | Say so in the privacy note, or add a signature permission, see 10 |
| 11 | Zamoled Windows zip and emoji fonts | Zamoled | Check | Confirm which emoji font the desktop build renders with, see 11 |

## 1. Repo licence
No `LICENSE` or `NOTICE` at the repo root. Without one, nobody may legally copy, modify or redistribute the code, even though it is public.
Decide what you want: keep it all-rights-reserved (add a one-line `LICENSE` saying so), or open it (MIT or Apache-2.0 are the usual picks for apps like these).
Either way add a `NOTICE` that lists the third-party parts in section 7. Art you made yourself can carry a different licence than the code.

## 2. Maps and Cats web search (highest risk)
`mapsandcats/app.js` reads `html.duckduckgo.com/html/` and `bing.com/search` result pages and then fetches the pages they list to look for coordinates.
- Bing's terms forbid automated scraping and DuckDuckGo's HTML page is not a public API. They can block you, and it is a terms-of-service breach even if nobody sues.
- Fetching and parsing arbitrary third-party pages may copy their content; coordinates and place names are facts, but the pages are not yours.
- **Fix:** keep Wikipedia (it has a real API) and the map search (Photon / Nominatim); drop DDG and Bing, or turn the web step into "open this search in your browser".

## 3. Privacy wording
The apps store nothing about you on a server and have no accounts or analytics. That is true. But "no data collection" read literally is not, because:
- Purrcast sends your coordinates to Open-Meteo (forecast, air quality, place search) and your IP address necessarily goes with every request.
- Maps and Cats sends your typed searches to Photon / Nominatim, your route to OSRM, and loads tiles from OpenFreeMap.
- All three call GitHub to check for updates.
**Fix:** say "We collect nothing. To work, the app talks to these services: ..." in each app's About, and keep a short `docs/PRIVACY.md` (what is sent, to whom, why, what stays on the phone). Malaysia's PDPA 2010 covers commercial transactions; a free hobby app is probably outside it, but the note costs nothing and is the first thing a reviewer or user asks for. If you ever charge for an app or put it on a store, ask a lawyer.

## 4. Weather warnings (Purrcast)
Since the MET Malaysia feed was removed, "Flood risk", "Strong wind" and so on are computed from Open-Meteo forecasts. They are not official warnings, and the one-liners rightly say nothing about being official. Add **one disclaimer line** in About and in the Alerts setting:
"These are forecast-based nudges, not official warnings. In an emergency follow your local authorities (for example MET Malaysia and NADMA)."
Do not put it in the one-liners. Never write "official", "emergency service" or the name of an authority on an alert you generated yourself.

## 5. Navigation (Maps and Cats)
No "do not use while driving" or accuracy line exists. Add to About and show once before the first route: "Routes and times are estimates (no live traffic). Obey road signs and the law. Do not operate the phone while driving." The app already says the time is a range, which helps.

## 6. Public servers
- **Nominatim** (OpenStreetMap): max 1 request per second, identify the app (User-Agent or Referer), no search-as-you-type, cache results. Check the debounce in the search box.
- **router.project-osrm.org** and **routing.openstreetmap.de**: demo servers, light use only, no SLA. For wider release host your own or accept they may rate-limit. The "your own server" settings fields already cover this.
- **photon.komoot.io**: fair use, no heavy load.
- **OpenFreeMap**: free; keep its attribution visible (the compact attribution control does this).
- **Map attribution** "© OpenStreetMap contributors" is shown (footer and the control). Keep it; it is a licence condition (ODbL).

## 7. Third-party notices
Present: Noto Emoji (Apache-2.0) and svgmoji (MIT) texts in `mapsandcats/assets/cats`, MapLibre licence in `mapsandcats/vendor/maplibre`, Unsplash credits in `zamoled/.../photos/CREDITS.txt`, Open-Meteo / OSM / Noto lines in Purrcast's About.
Missing: a user-visible "Open-source notices" screen in each app (Apache-2.0 asks that the licence text travels with the app), and notices for AndroidX / Jetpack Compose / WorkManager / Kotlin (all Apache-2.0) in Zamoled.
**Fix:** one `NOTICE` file per app, shown from About, listing each component, its licence and a link.

## 8. Artwork provenance
The app icons (cat on a cloud, cat on a globe, the sparkle) and the hero cat art are the owner's work built on Noto cats (hero cats: Noto head, own mask/cape drawn in `trio-art/hero-cats/build.py`, notice shipped). Write in `docs/` how each icon was made. If any image was produced with an AI image tool, copyright in it may be weak or unavailable, which matters if you want to stop others copying it. Generic masks and capes are not tied to any character, so there is no trademark issue with the hero cat.

## 9. Open-Meteo
Free API access is for **non-commercial** use and is rate-limited; attribution (CC BY 4.0) is shown in Purrcast. If Purrcast ever carries ads, payment or heavy traffic, move to a paid plan or self-host.

## 10. Exported Cat Club providers
Each app exposes day streak, best, last visit day (and Purrcast's mood) to any app on the phone. It is not sensitive, and it is how the trio works, but it is readable by other apps. State it in the privacy note, or protect it with a permission only your three apps hold (the apps are signed with different keys today, so that would need a shared key or a check by package and certificate).

## 11. Zamoled desktop and emoji
The Android app draws emoji with the phone's own font (Noto Color Emoji). The Windows build must not ship a copy of Microsoft's Segoe UI Emoji or other proprietary fonts inside the zip; confirm which font it uses and ship Noto (Apache-2.0) instead if in doubt.
Unsplash photos (Night Photos): the Unsplash licence allows free use and modification without permission; keep `CREDITS.txt`, and do not build a competing photo library from them. Credits are already given.

## Not a problem
- Google Noto cat emoji: licence allows this use; notice is shipped. Only Noto cats are used.
- Hero cat: generic mask and cape, no character or logo.
- Updater: only talks to GitHub, checks a SHA-256, and Android refuses an APK signed with another key.
- Permissions: location and notifications are asked for and used for what they say; no contacts, no storage, no camera.
- Keys: signing keys are in a private repo, not in this one.
