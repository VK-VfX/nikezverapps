# DepthLock

Clock-behind-the-subject lock screen for Android (built for Pixel 9 Pro XL, works on any Android 12+ phone with Google Play services).

- **Live wallpaper**: photo, then clock, then the cut-out subject on top. Layers shift as you tilt the phone.
- **Screen saver** (while charging) and **Always-on screen**: dimmed copy that moves a few pixels each minute.
- Subject cut-out runs on the phone (ML Kit subject segmentation, model delivered by Google Play services).

Limits: Pixel draws its own lock-screen clock and its own always-on display; an app cannot remove or replace either.

Build: `cd depthlock && ./gradlew assembleDebug` (CI: `.github/workflows/depthlock-build.yml`).
