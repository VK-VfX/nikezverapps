# Progress Save Code Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In each app, "Save my progress" makes a short text code (and an optional backup file) the user keeps in Notes, a screenshot, a calendar event or a file, and "Restore progress" brings it back without ever lowering progress.

**Architecture:** One code format with three small codec ports (Java, Kotlin, JS) tested against one shared vectors file. Each app maps its own state to a payload and merges a restored payload with "never lower". The UI uses only system pickers and intents, so there are no new permissions and nothing leaves the phone.

**Tech Stack:** Purrcast plain Java (no AndroidX), Zamoled Kotlin/Compose, Maps and Cats web in an Android shell with new JS bridge methods.

**Spec:** `docs/superpowers/specs/2026-11-17-progress-save-code-design.md`. **Depends on:** `2026-11-17-trio-streaks-shield.md` (all three apps must already have the streak state).

## Global Constraints
- Prefixes `PRC1`, `ZAM1`, `MAC1`; code text `PRC1-XXXX-XXXX-...`; Crockford base32 alphabet `0123456789ABCDEFGHJKMNPQRSTVWXYZ`; input is case-insensitive, ignores dashes, spaces and newlines, reads `O` as `0` and `I`, `L` as `1`.
- Counters only in the code; favourites, saved places and settings only in the optional `.json` file.
- Restoring never lowers a number; no new permissions; no network; no camera/QR.
- Screenshot card only on Android 10+ (`Build.VERSION.SDK_INT >= 29`); older phones show only the other choices.
- Plain Java in Purrcast (no lambdas, no AndroidX). Local commits only; **do not push, build releases or bump versions** until the owner says so.
- Warm wording, soft button style, AAA contrast, 48 px targets, inline icons only.

## Review Focus
- A whole note pasted that holds all three codes plus other text: the right one is found, others ignored.
- A code with one wrong or missing character: "typo" message, never a crash or a half-restore.
- A code from the other app, an empty paste, a very long paste (megabytes): friendly message, no hang.
- Restoring an older code over newer progress: nothing goes down.
- Phone with no calendar app or no notes app: the choice shows a message instead of crashing (`ActivityNotFoundException`).

## The format (exact)
- **Payload bytes:** streak block, then app extras, then CRC.
  - Streak block: `varint count`, `varint best`, `varint last` (day number), 8 bytes `mask` (big-endian), 1 byte `shield` = `(ready ? 1 : 0) | (charge << 1)`.
  - Extras: Purrcast `varint rewardClaimed`; Zamoled none; Maps and Cats `varint xp`, `varint caught`.
  - `varint` = unsigned LEB128. CRC = CRC-16/CCITT-FALSE (poly `0x1021`, init `0xFFFF`) of everything before it, stored as 2 bytes big-endian.
- **Text:** payload bytes (with CRC) as a bit stream, 5 bits per character (zero-padded), then `PREFIX-` plus groups of 4 characters joined by `-`.
- **Decode:** find in the pasted text the first run that, after cleaning, starts with this app's prefix; a run that starts with another app's prefix gives `WrongApp(name)`; characters outside the alphabet or a CRC mismatch give `Typo`; nothing found gives `NotFound`.
- **Merge** (`never lower`): `best`, `xp`, `caught`, `rewardClaimed` = max. For the current streak block (`count, last, mask, ready, charge`) keep local when `local.last >= code.last`, otherwise take the code's block; always `best = max(...)`.

## Reference vectors (use these exact values)
| App | streak block | extras | code |
|---|---|---|---|
| Purrcast | count 14, best 20, last 20409, mask 16383, ready, charge 0 | rewardClaimed 1 | `PRC1-1RAB-K7R1-0000-0000-00ZZ-Y081-0VAG` (payload hex `0e14b99f010000000000003fff010106d5`) |
| Zamoled | count 3, best 3, last 20409, mask 7, not ready, charge 2 | none | `ZAM1-0C1V-K7R1-0000-0000-0000-E171-28` (payload hex `0303b99f01000000000000000704e112`) |
| Maps and Cats | count 5, best 9, last 20409, mask 31, ready, charge 0 | xp 1200, caught 37 | `MAC1-0M4V-K7R1-0000-0000-0001-Y0DG-14JM-P8G` (payload hex `0509b99f01000000000000001f01b009254b22`) |

### Task 1: Vectors file and Purrcast codec
**Files:** Create `docs/save-code-vectors.json`, `purrcast/app/src/main/java/app/skypix/core/SaveCode.java`, `purrcast/tools/app/skypix/tools/SaveCodeTest.java`.
**Produces:** `final class SaveCode { static String encode(String prefix, byte[] payloadWithoutCrc); static Result decode(String pasted, String prefix); static final class Result { final int kind; /* OK, WRONG_APP, TYPO, NOT_FOUND */ final byte[] payload; final String otherPrefix; } }` where `payload` excludes the CRC.
- [ ] **Step 1: Write the vectors file** `{"cases":[{app, prefix, payloadHex (with CRC), code}], "messy":[{text, prefix, expectKind, expectPayloadHex}]}` containing the three rows above plus: the Purrcast code lower-cased with spaces instead of dashes (OK); the same code with `0` replaced by `O` and `1` by `l` (OK); the Maps code pasted to the Purrcast decoder (`WRONG_APP`, `otherPrefix=MAC1`); the Purrcast code with its 9th character changed (`TYPO`); `""` and `"hello"` (`NOT_FOUND`); a note `"My codes:\nzam1 0c1v-k7r1-0000-0000-0000-e171-28\nthanks\nPRC1-1RAB-K7R1-0000-0000-00ZZ-Y081-0VAG"` decoded for `PRC1` (OK).
- [ ] **Step 2: Write `SaveCodeTest.java`** (loads the file, checks `encode` equals `code`, `decode` matches every `messy` entry, and that a 5 MB random string decodes to `NOT_FOUND` in under 2 seconds).
- [ ] **Step 3: Run, expect failure:** `cd purrcast && javac -nowarn -d build/tools -classpath build/deps/json.jar app/src/main/java/app/skypix/core/*.java tools/app/skypix/tools/SaveCodeTest.java`
- [ ] **Step 4: Implement `SaveCode.java`** per "The format"; scan the pasted text once (no regex backtracking over the whole input).
- [ ] **Step 5: Run** `java -cp build/tools:build/deps/json.jar app.skypix.tools.SaveCodeTest ../docs/save-code-vectors.json`; expected `OK`. Commit `feat: save code codec (purrcast) and vectors`.

### Task 2: Zamoled and Maps and Cats codecs
**Files:** Create `zamoled/app/src/main/java/com/nikopick/zamoled/gen/SaveCode.kt`, `zamoled/tools/preview/src/test/kotlin/SaveCodeTest.kt`, `mapsandcats/savecode.js`, `mapsandcats/tools/savecode.test.js`. Modify `mapsandcats/index.html`, `sw.js` (SHELL), `android/app/build.gradle.kts` (asset include list).
**Produces:** Kotlin `object SaveCode { fun encode(prefix: String, payload: ByteArray): String; fun decode(pasted: String, prefix: String): SaveCodeResult }` with `sealed class SaveCodeResult { class Ok(val payload: ByteArray); class WrongApp(val other: String); object Typo; object NotFound }`; JS `saveCodeEncode(prefix, bytes /*Uint8Array*/)` and `saveCodeDecode(pasted, prefix)` returning `{kind:'ok'|'wrongApp'|'typo'|'notFound', payload, other}`.
- [ ] **Step 1: Write both tests** reading `docs/save-code-vectors.json`, same assertions as Task 1.
- [ ] **Step 2: Run, expect failure:** `cd zamoled/tools/preview && ANDROID_HOME=/home/user/android-sdk ./gradlew test --tests SaveCodeTest --console=plain -q` and `node mapsandcats/tools/savecode.test.js`.
- [ ] **Step 3: Implement both ports** of Task 1's algorithm.
- [ ] **Step 4: Run both; expect all vectors to pass.** Commit `feat: save code codec (zamoled, mapsandcats)`.

### Task 3: Purrcast progress mapping, merge and backup file
**Files:** Create `purrcast/app/src/main/java/app/skypix/core/Progress.java`, `purrcast/tools/app/skypix/tools/ProgressTest.java`. Modify `Store.java`.
**Produces:** `final class Progress { Streak.State streak; int rewardClaimed; byte[] toPayload(); static Progress fromPayload(byte[] p); static Progress merge(Progress local, Progress remote); }` (uses `Streak.State` from the streak plan); `static Progress Store.loadProgress(Context c)`; `static void Store.applyProgress(Context c, Progress p)`; `static String Store.backupJson(Context c)`; `static String Store.restoreBackupJson(Context c, String json)` (returns a one-line summary or throws `IllegalArgumentException` with a friendly message).
- [ ] **Step 1: Write `ProgressTest.java`:** the Purrcast reference payload round-trips (`toPayload` hex equals the vector minus CRC; `fromPayload` restores every field); merge cases: local newer keeps local count but takes a higher remote best; remote newer takes the remote block; rewards take the max; applying an all-zero remote changes nothing.
- [ ] **Step 2: Run, expect failure; implement `Progress`; run, expect pass.**
- [ ] **Step 3: Implement the four `Store` methods.** Backup JSON shape: `{"app":"purrcast","format":1,"saved":"YYYY-MM-DD","progress":"<code>","places":[{"name":..,"lat":..,"lon":..}],"settings":{...}}`; restore merges places by rounded coordinates (4 decimals) and applies `settings` only when the caller passes `withSettings = true` (add that boolean parameter to `restoreBackupJson`).
- [ ] **Step 4: Verify** the test passes. Commit `feat(purrcast): progress mapping and backup file`.

### Task 4: Purrcast Save and Restore screens
**Files:** Modify `MainActivity.java` (Settings: new "Progress" section with two buttons; two dialogs; `onActivityResult`), create `purrcast/app/src/main/java/app/skypix/android/ProgressShare.java`.
**Produces:** `final class ProgressShare { static void shareText(Activity a, String text); static void saveCard(Activity a, String code); /* MediaStore, API 29+ */ static void addCalendar(Activity a, String code); static void saveFile(Activity a, String name, String mime, String body); /* ACTION_CREATE_DOCUMENT */ static Bitmap card(Context c, String code, String savedOn); }`.
- [ ] **Step 1: Card.** `card(...)` draws a 1080x1350 black bitmap: app name, "Saved <d MMM yyyy>", the code in two lines of large mono text, one warm line ("Keep this safe. Paste it into Restore."). 
- [ ] **Step 2: Choices.** "Save my progress" opens a list: Share to notes or chat, Save a screenshot card (hidden below API 29), Add to calendar (`ACTION_INSERT` on `CalendarContract.Events.CONTENT_URI`, title `Purrcast progress code`, description = the code, all-day today), Save as a file (`.txt`), Full backup file (`.json`), Copy. Every `startActivity` is wrapped to catch `ActivityNotFoundException` and show "No app found for that. Try Copy."
- [ ] **Step 3: Restore.** "Restore progress" opens a dialog with a text field (pre-filled when the clipboard holds a decodable code), "Restore" and "Open file". Messages: `Typo` "That code has a typo. Check the characters near the end."; `WrongApp(x)` "That code belongs to <app name>."; `NotFound` "I couldn't find a Purrcast code in that."; success "Restored: <n> day streak, best <b>." The file path calls `Store.restoreBackupJson` and shows its summary; a checkbox "Also restore my settings" (off).
- [ ] **Step 4: Verify** on the JVM-free parts by compiling (`bash scripts/build.sh` into a temp copy is **not** to be run; instead `javac` the sources as `scripts/build.sh` step 3 does), and describe the manual device checks in the commit body. Commit `feat(purrcast): save and restore progress`.

### Task 5: Zamoled mapping, backup file and screens
**Files:** Create `zamoled/.../data/ProgressStore.kt`, `zamoled/.../ui/ProgressSheet.kt`, `zamoled/tools/preview/src/test/kotlin/ProgressTest.kt`. Modify `ui/SettingsScreen.kt` (new "Progress" block under "The trio").
**Produces:** `object ProgressStore { fun payload(context: Context): ByteArray; fun applyPayload(context: Context, payload: ByteArray): String; fun backupJson(context: Context): String; fun restoreBackupJson(context: Context, json: String, withSettings: Boolean): String }` and a pure `fun mergeStreak(local: StreakState, remote: StreakState): StreakState` in `gen/StreakRules.kt`.
- [ ] **Step 1: Write `ProgressTest.kt`:** the Zamoled reference payload round-trips; `mergeStreak` cases as in Task 3; an all-zero remote changes nothing. Run, expect failure; implement; run, expect pass.
- [ ] **Step 2: Backup JSON** `{"app":"zamoled","format":1,"saved":..,"progress":"<code>","favorites":[<recipe keys>],"settings":{...daily wallpaper options}}`; restore unions favourites, applies settings only when `withSettings`.
- [ ] **Step 3: UI.** A Compose sheet with the same six choices and the same restore dialog and messages as Task 4, using `ActivityResultContracts.CreateDocument` / `OpenDocument`, `Intent.ACTION_SEND`, `ACTION_INSERT` (calendar) wrapped in `runCatching`, the card drawn with a Compose `Canvas` into a `Bitmap` and saved through `MediaStore.Images` on API 29+ (Zamoled `minSdk` is 29, so always available), the soft button style.
- [ ] **Step 4: Verify** `./gradlew :app:assembleDebug` and the test pass. Commit `feat(zamoled): save and restore progress`.

### Task 6: Maps and Cats mapping, bridge and screens
**Files:** Create `mapsandcats/progress.js`, `mapsandcats/tools/progress.test.js`. Modify `index.html` (drawer "Progress" group), `app.js`, `app.css`, `sw.js`, `android/app/build.gradle.kts` (add `progress.js` to the asset include list), `android/.../MainActivity.java`.
**Produces (JS):** `progressPayload(S)` returns `Uint8Array`; `progressApply(S, payload)` merges and returns a summary string; `progressBackup(S)` / `progressRestoreBackup(S, json, withSettings)` as in Task 3. **Bridge:** `@JavascriptInterface void shareText(String text)`, `void saveFile(String name, String mime, String body)`, `void saveImage(String name, String dataUrl)`, `void addCalendar(String title, String description)`, `void pickFile()` (result delivered by calling the page's `window.__macFilePicked(text)`), `String clipboardText()`.
- [ ] **Step 1: Write `progress.test.js`** (node): the Maps reference payload round-trips; merge never lowers `xp`/`caught`/`best`; all-zero remote changes nothing. Run, expect failure; implement; run, expect pass.
- [ ] **Step 2: Bridge methods** in `MainActivity.java`: `shareText` (`ACTION_SEND` chooser), `saveFile` (`ACTION_CREATE_DOCUMENT`, writes in `onActivityResult`), `saveImage` (decode the data URL, `MediaStore` insert on API 29+, a toast otherwise), `addCalendar`, `pickFile` (`ACTION_OPEN_DOCUMENT`, then `evaluateJavascript("window.__macFilePicked(" + JSONObject.quote(text) + ")")`), `clipboardText`. Each wrapped for `ActivityNotFoundException`.
- [ ] **Step 3: Drawer UI** (`Progress` group with "Save my progress" and "Restore progress", same choices and messages as Task 4). In a plain browser (no bridge) fall back to `navigator.share`, a Blob download and the clipboard.
- [ ] **Step 4: Verify** in a phone-size browser (Playwright): saving then restoring round-trips; a pasted note with three codes finds the `MAC1` one; no console errors. Commit `feat(mapsandcats): save and restore progress`.

### Task 7: TRIO.md and help text
- [ ] **Step 1:** Add to `docs/TRIO.md`: "Save codes are per app; one app never reads or writes another app's code", with the prefix table.
- [ ] **Step 2:** Add one help line per app about Save and Restore. Commit `docs: save codes`.
