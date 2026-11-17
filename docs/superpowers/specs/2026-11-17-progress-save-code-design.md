# Progress save code (Purrcast, Zamoled, Maps and Cats)

Status: **draft for the owner's approval.** Nothing is built. The hero-cat unlock is a separate project (see the end).

## Intent and the why
Streaks, rewards, levels and favourites live only on the phone. Purrcast and Zamoled have backups switched off, so a reinstall or a
new phone loses them. The owner's rule is no sign-up, no accounts, no servers and no data collection, so the answer has to be a
thing the **user holds**: a short code (and an optional file) they can keep in their notes, a screenshot, their calendar or a file.

## Goals
- Each app can **Save my progress** and **Restore progress**, with no account, no network and no new permissions.
- The save code is **small, readable and forgiving**: easy to type, safe to paste, typo-proof.
- Several ways to keep it: Notes (share sheet), screenshot card, calendar event, a file, copy.
- An **optional backup file** carries the bigger things (favourites, saved places, settings).
- Restoring can never lower progress, and never needs the other apps.

## Non-goals
- No cloud, no sync, no server, no QR scanning (it would need the camera permission), no automatic backup in this project
  (that is phase 2, see the end).
- No cross-app restore: one app never reads or writes another app's full state (TRIO.md rule 3 stays as it is).

## Anti-goals (this fails even if it works)
- Anything that needs an account, sends data off the phone or adds a permission prompt.
- A restore that wipes or lowers what the user already has.
- A code so long that nobody would ever type it.

## Constraints
- `docs/TRIO.md`: data moves only when the user taps something; sibling apps share read-only counts only. Add a rule: save codes are per app.
- Purrcast is plain Java with no AndroidX (so no AndroidX FileProvider). Zamoled is Kotlin/Compose. Maps and Cats is a web app inside an
  Android shell, reached through the existing JS bridge.
- Trio look and voice (`.ux-profile.md`): warm wording, AAA contrast, 48 px targets, the soft green/red/teal buttons, inline icons only.

## Approaches considered
1. **One code per app, one shared format** (chosen). Simple, independent, fits the sharing rules.
2. One combined "trio passport": rejected. It needs one app to read the other apps' full state or write into them, which breaks the
   read-only rule.
3. Sibling mirror (an app asks an installed sibling what it last saw): kept as a possible later bonus; it only works while the sibling stays installed.
4. Android's own device backup: wanted too (owner said "both"), built after the code, as phase 2.

## The code
- **Format:** `PRC1-7K2M-9QXA-4DTE-...` Crockford base32 (no I, L, O, U), groups of four, upper or lower case, dashes and spaces ignored,
  `O` read as `0` and `I`/`L` as `1`. Prefix per app: `PRC1`, `ZAM1`, `MAC1` (the digit is the format version).
- **Safety:** a 16-bit checksum at the end. A bad code says "that code has a typo" and shows where it stops matching, never a crash.
  A code with the wrong prefix says which app it belongs to.
- **Finding it:** Restore searches whatever was pasted (a whole note with three codes in it) and takes the one with this app's prefix.
- **Contents (counters only, decided by the owner).** Every app now has its own day streak (see the trio streaks design), so all
  three codes share one **streak block**: streak count, best streak, last visit day, the 64-day history, and the Streak Shield state.
  - Purrcast: the streak block plus rewards claimed. About 20 bytes, 36 characters.
  - Zamoled: the streak block only (rewards are tracked in Purrcast). Its favourites are **not** in the code (they go in the optional file).
  - Maps and Cats: the streak block plus XP and cats caught (level is worked out from XP).
- **Restoring never lowers anything:** each number takes the larger of "what is here" and "what the code says"; favourites are merged, not replaced.
- **Streak rules still apply after restore (my call):** the best streak and rewards come back in full; today's streak follows the normal
  rule (a stale code means the streak restarts at 1 on the next open). No cheating, no special cases.
- **Not secret, not signed:** a person can edit their own numbers. That is fine, there is no leaderboard and rewards are decorative.

## The optional backup file
- Plain text (JSON), named like `purrcast-backup-2026-11-17.json`. Holds the counters plus the bigger things:
  Purrcast saved places and settings; Zamoled favourites and daily-wallpaper settings; Maps and Cats saved places (home, work, favourites) and settings.
  Never location history, never anything the app does not already keep.
- Saved with the system file picker (no permission), restored with the system "open file" picker. Merges the same way as the code.

## Ways to keep it (one "Save my progress" button opens a small sheet)
| Choice | How | Notes |
|---|---|---|
| Notes / chat | system share sheet (text) | reaches Keep, Samsung Notes, a chat with yourself |
| Screenshot card | the app draws a card (app name, date, the code in large groups) and saves it to Pictures/Trio | no permission on Android 10+; older phones use the share sheet instead |
| Calendar | opens the calendar app with a prefilled event: title "Purrcast progress code", the code in the description | the user taps save; no calendar permission |
| File | `.txt` for the code, `.json` for the full backup, via the file picker | |
| Copy | clipboard | |
Restore: paste box that picks up a code from the clipboard, plus "Open file".

## Where it lives
Settings, a **Progress** section with two buttons (Save my progress, Restore progress). The existing "The trio" list gets a one-line pointer.
Warm wording ("Keep your streak safe"), the soft button style, icons from each app's own set.

## What is left to the builder
Exact bit packing and the checksum polynomial; the card layout; the share/file plumbing in Purrcast without AndroidX (a tiny own
ContentProvider is the likely route); the Android-shell bridge methods for Maps and Cats (share text, save file, save image); copy text.
Round-trip tests on a normal JVM (encode, decode, typos, wrong prefix, older and newer format versions, merge never lowers).

## Release shape
A new feature in each app: Purrcast 2.1.0, Zamoled 8.1.0, Maps and Cats 2.2.0 (minor bumps by the versioning rule). Update `docs/TRIO.md`
and each app's help text.

## Separate, later (and now designed in its own document)
- **Trio streaks, Streak Shield and the hero cat:** see `2026-11-17-trio-streaks-and-hero-cat-design.md`. The save code depends on the streak block defined there.
- **Phase 2:** Android's own backup, limited to just the progress numbers (turned on deliberately; Purrcast and Zamoled have it off today).
