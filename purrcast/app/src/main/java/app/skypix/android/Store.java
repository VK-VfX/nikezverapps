package app.skypix.android;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.format.DateFormat;

import app.skypix.core.Cards;
import app.skypix.core.Insights;
import app.skypix.core.Place;
import app.skypix.core.Units;
import app.skypix.core.Weather;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Preferences plus the last-known weather/place cache. */
final class Store {
    private Store() {}

    /** True while the activity is on screen; background location is only usable then (unless granted). */
    static volatile boolean visible;

    static SharedPreferences prefs(Context c) { return sp(c); }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("skypix", Context.MODE_PRIVATE);
    }

    // ---- settings ----
    static String tempPref(Context c) { return sp(c).getString("temp", "auto"); }
    static void setTempPref(Context c, String v) { sp(c).edit().putString("temp", v).apply(); }

    static boolean alerts(Context c) { return sp(c).getBoolean("alerts", true); }
    static void setAlerts(Context c, boolean v) { sp(c).edit().putBoolean("alerts", v).apply(); }

    static boolean hourly(Context c) { return sp(c).getBoolean("hourly", true); }
    static void setHourly(Context c, boolean v) { sp(c).edit().putBoolean("hourly", v).apply(); }

    /** Morning briefing. */
    static boolean morning(Context c) { return sp(c).getBoolean("morning", true); }
    static void setMorning(Context c, boolean v) { sp(c).edit().putBoolean("morning", v).apply(); }
    static int morningHour(Context c) { return sp(c).getInt("morning_hour", 7); }
    static void setMorningHour(Context c, int h) { sp(c).edit().putInt("morning_hour", h).apply(); }

    /** OTA updates. */
    static boolean autoUpdate(Context c) { return sp(c).getBoolean("auto_update", true); }
    static void setAutoUpdate(Context c, boolean v) { sp(c).edit().putBoolean("auto_update", v).apply(); }
    static long lastUpdateCheck(Context c) { return sp(c).getLong("update_check", 0L); }
    static void setLastUpdateCheck(Context c, long t) { sp(c).edit().putLong("update_check", t).apply(); }
    static long notifiedVersion(Context c) { return sp(c).getLong("update_notified", 0L); }
    static void setNotifiedVersion(Context c, long v) { sp(c).edit().putLong("update_notified", v).apply(); }

    static boolean quiet(Context c) { return sp(c).getBoolean("quiet", true); }
    static void setQuiet(Context c, boolean v) { sp(c).edit().putBoolean("quiet", v).apply(); }

    static int quietStart(Context c) { return sp(c).getInt("qs", 22); }
    static int quietEnd(Context c) { return sp(c).getInt("qe", 7); }
    static void setQuietHours(Context c, int start, int end) { sp(c).edit().putInt("qs", start).putInt("qe", end).apply(); }

    // ---- Material You colours (Android 12+) ----
    static boolean materialYou(Context c) { return sp(c).getBoolean("you", false); }
    static void setMaterialYou(Context c, boolean v) { sp(c).edit().putBoolean("you", v).apply(); }

    /** Fills the dynamic-colour fields of the card data from the system palette (no-op unless enabled on Android 12+). */
    static void applyColors(Context c, Cards.Data d) {
        d.youTop = 0;
        if (Build.VERSION.SDK_INT < 31 || !materialYou(c)) return;
        try {
            d.youTop = c.getColor(android.R.color.system_accent1_600);
            d.youBottom = c.getColor(android.R.color.system_accent1_700);
            d.youP0 = c.getColor(android.R.color.system_accent1_500);
            d.youP1 = c.getColor(android.R.color.system_accent1_700);
        } catch (Throwable t) {
            d.youTop = 0;
        }
    }

    /** The MET Malaysia warnings feature was removed; forget what it stored. Safe to call on every start. */
    static void dropOfficial(Context c) {
        SharedPreferences p = sp(c);
        if (!p.contains("official") && !p.contains("official_json") && !p.contains("official_status")) return;
        p.edit().remove("official").remove("official_marine").remove("official_status").remove("official_json").remove("official_at").apply();
    }

    // ---- streak: consecutive days the app was opened ----
    static long dayNumber() {
        long now = System.currentTimeMillis();
        return (now + java.util.TimeZone.getDefault().getOffset(now)) / 86400000L;
    }

    /** Call when the app comes to the front. The rules live in {@link app.skypix.core.Streak} (shared with the other two apps). */
    static synchronized void touchStreak(Context c) {
        SharedPreferences p = sp(c);
        app.skypix.core.Streak.State s = streakState(p);
        long today = dayNumber();
        app.skypix.core.Streak.State n = app.skypix.core.Streak.step(s, today, shieldOn(c));
        // The trio shares one streak: take the highest from the other two apps (see docs/TRIO.md).
        for (String authority : new String[]{"com.nikopick.zamoled.catclub", "app.mapsandcats.catclub"}) {
            long[] sib = Trio.streakOf(c, authority);
            if (sib != null) n = app.skypix.core.Streak.adopt(n, today, (int) sib[0], (int) sib[1], sib[2]);
        }
        if (n == s) return;
        p.edit().putLong("streak_last", n.last).putLong("streak_mask", n.mask).putInt("streak_count", n.count)
                .putInt("streak_best", n.best).putBoolean("shield_ready", n.ready).putInt("shield_charge", n.charge).apply();
    }

    private static app.skypix.core.Streak.State streakState(SharedPreferences p) {
        long mask = p.getLong("streak_mask", 0);
        int count = p.getInt("streak_count", -1);
        app.skypix.core.Streak.State s = count < 0 ? app.skypix.core.Streak.fromLegacy(mask) : new app.skypix.core.Streak.State();
        if (count >= 0) { s.count = count; s.mask = mask; }
        s.best = Math.max(p.getInt("streak_best", 0), s.best);
        s.last = p.getLong("streak_last", 0);
        s.ready = p.getBoolean("shield_ready", true);
        s.charge = p.getInt("shield_charge", 0);
        return s;
    }

    static long streakLast(Context c) { return sp(c).getLong("streak_last", 0); }

    // ---- progress save code and backup file (see docs/superpowers/specs/2026-11-17-progress-save-code-design.md) ----

    static app.skypix.core.Progress loadProgress(Context c) {
        app.skypix.core.Progress p = new app.skypix.core.Progress();
        p.streak = streakState(sp(c));
        p.rewardClaimed = sp(c).getInt("reward_claimed", 0);
        return p;
    }

    static void applyProgress(Context c, app.skypix.core.Progress p) {
        sp(c).edit().putLong("streak_last", p.streak.last).putLong("streak_mask", p.streak.mask).putInt("streak_count", p.streak.count)
                .putInt("streak_best", p.streak.best).putBoolean("shield_ready", p.streak.ready).putInt("shield_charge", p.streak.charge)
                .putInt("reward_claimed", p.rewardClaimed).apply();
    }

    static String progressCode(Context c) {
        return app.skypix.core.SaveCode.encode(app.skypix.core.SaveCode.PRC, loadProgress(c).toPayload());
    }

    private static final String[] BACKUP_SETTINGS = {"alerts", "animate", "auto_update", "cats", "clear_dark", "hourly", "morning", "morning_hour", "qe", "qs", "quiet", "temp", "you", "hero_on"};

    /** The optional full backup: the code, saved places and a few settings. No location history, nothing else. */
    static String backupJson(Context c) throws org.json.JSONException {
        org.json.JSONObject o = new org.json.JSONObject();
        o.put("app", "purrcast").put("format", 1).put("saved", new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date()));
        o.put("progress", progressCode(c));
        org.json.JSONArray places = new org.json.JSONArray();
        for (Places.Saved s : Places.list(c)) places.put(new org.json.JSONObject().put("name", s.name).put("lat", s.place.lat).put("lon", s.place.lon));
        o.put("places", places);
        org.json.JSONObject set = new org.json.JSONObject();
        java.util.Map<String, ?> all = sp(c).getAll();
        for (String k : BACKUP_SETTINGS) if (all.containsKey(k)) set.put(k, all.get(k));
        o.put("settings", set);
        return o.toString(2);
    }

    /** Merges a backup file into this phone. Throws IllegalArgumentException with a friendly message. */
    static String restoreBackupJson(Context c, String json, boolean withSettings) {
        try {
            org.json.JSONObject o = new org.json.JSONObject(json);
            if (!"purrcast".equals(o.optString("app"))) throw new IllegalArgumentException("That file is not a Purrcast backup.");
            String msg = ProgressShare.restoreFromText(c, o.optString("progress"));
            int added = 0;
            org.json.JSONArray arr = o.optJSONArray("places");
            java.util.List<Places.Saved> have = Places.list(c);
            for (int i = 0; arr != null && i < arr.length(); i++) {
                org.json.JSONObject p = arr.getJSONObject(i);
                double lat = p.getDouble("lat"), lon = p.getDouble("lon");
                boolean dup = false;
                for (Places.Saved s : have) if (Math.round(s.place.lat * 1e4) == Math.round(lat * 1e4) && Math.round(s.place.lon * 1e4) == Math.round(lon * 1e4)) dup = true;
                if (dup) continue;
                Place pl = new Place();
                pl.lat = lat;
                pl.lon = lon;
                Places.add(c, p.optString("name"), pl);
                added++;
            }
            if (withSettings) {
                org.json.JSONObject set = o.optJSONObject("settings");
                SharedPreferences.Editor e = sp(c).edit();
                for (String k : BACKUP_SETTINGS) {
                    if (set == null || !set.has(k)) continue;
                    Object v = set.get(k);
                    if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
                    else if (v instanceof Integer) e.putInt(k, (Integer) v);
                    else if (v instanceof Long) e.putLong(k, (Long) v);
                    else if (v instanceof String) e.putString(k, (String) v);
                }
                e.apply();
            }
            return msg + (added > 0 ? " " + added + " saved place" + (added == 1 ? "" : "s") + " added." : "");
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException("That file could not be read.");
        }
    }

    /** The hero-cat mood for the latest weather (see HeroMood); other apps read it from the Cat Club provider. */
    static String mood(Context c) { return sp(c).getString("hero_mood", "smile"); }
    static void setMood(Context c, String m) { sp(c).edit().putString("hero_mood", m).apply(); }

    /** The "Show hero cat" switch (default on). The hero only shows while all three apps are installed. */
    static boolean heroOn(Context c) { return sp(c).getBoolean("hero_on", true); }
    static void setHeroOn(Context c, boolean v) { sp(c).edit().putBoolean("hero_on", v).apply(); }
    static boolean heroActive(Context c) { return heroOn(c) && Trio.allInstalled(c); }
    /** Makes the widgets and cards follow the switch and the installed apps. Call before drawing. */
    static void applyHero(Context c) { app.skypix.core.Glyphs.hero = heroActive(c); }

    /** True while all three apps are installed on this phone: the Streak Shield and the hero cat work. */
    static boolean shieldOn(Context c) { return Trio.allInstalled(c); }

    /** What the Cat Club shows next to the streak: "day streak" plus the shield state when it is on. */
    static String streakLabel(Context c) {
        boolean on = shieldOn(c);
        String shield = app.skypix.core.Streak.label(streakState(sp(c)), on);
        return shield.isEmpty() ? "day streak" : "day streak \u00b7 " + shield;
    }

    /** "Shield ready" / "Shield recharging, N days to go", or "" when it is off. For the streak card. */
    static String shieldLine(Context c) {
        String s = app.skypix.core.Streak.label(streakState(sp(c)), shieldOn(c));
        return s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static int streak(Context c) {
        SharedPreferences p = sp(c);
        int n = p.getInt("streak_count", -1);
        if (n >= 0) return n;
        long mask = p.getLong("streak_mask", 0);
        int run = 0;
        while (run < 64 && ((mask >>> run) & 1L) != 0) run++;
        return run;
    }

    // ---- streak rewards: a special Zamoled wallpaper for every 10 days of best streak ----
    static final int REWARD_EVERY = 10;
    /** Day number of the next wallpaper that is ready to claim, or 0 when none is waiting. */
    static int rewardReady(Context c) {
        int earned = bestStreak(c) / REWARD_EVERY, claimed = sp(c).getInt("reward_claimed", 0);
        return claimed < earned ? (claimed + 1) * REWARD_EVERY : 0;
    }
    /** The streak day that earns the next wallpaper after the ones already claimed. */
    static int rewardNext(Context c) { return (sp(c).getInt("reward_claimed", 0) + 1) * REWARD_EVERY; }
    static void rewardClaimed(Context c) { sp(c).edit().putInt("reward_claimed", sp(c).getInt("reward_claimed", 0) + 1).apply(); }

    static int bestStreak(Context c) { return sp(c).getInt("streak_best", 0); }
    static long streakMask(Context c) { return sp(c).getLong("streak_mask", 0); }

    static boolean cats(Context c) { return sp(c).getBoolean("cats", true); }
    static void setCats(Context c, boolean v) { sp(c).edit().putBoolean("cats", v).apply(); }

    static boolean animate(Context c) { return sp(c).getBoolean("animate", true); }
    static void setAnimate(Context c, boolean v) { sp(c).edit().putBoolean("animate", v).apply(); }

    /** Widget background: 0 = solid, 1 = frosted glass, 2 = clear (dark mode fully see-through). */
    static int bgMode(Context c) {
        SharedPreferences p = sp(c);
        if (p.contains("bg_mode")) return p.getInt("bg_mode", 0);
        return p.getBoolean("clear_dark", false) ? 2 : 0;
    }

    static void setBgMode(Context c, int mode) { sp(c).edit().putInt("bg_mode", mode).apply(); }

    static void applyBackground(Context c, Cards.Data d) {
        int mode = bgMode(c);
        d.transparentDark = mode == 2;
        d.bgAlpha = mode == 1 ? 0.6f : 1f;
    }

    static Units units(Context c) {
        return Units.resolve(tempPref(c), Locale.getDefault(), DateFormat.is24HourFormat(c));
    }

    // ---- cache ----
    static synchronized void saveWeather(Context c, String forecast, String air, long fetchedAtMs) {
        sp(c).edit().putString("fc", forecast).putString("air", air == null ? "" : air).putLong("fetched", fetchedAtMs).apply();
    }

    static synchronized Weather loadWeather(Context c) {
        SharedPreferences s = sp(c);
        String fc = s.getString("fc", null);
        if (fc == null) return null;
        try {
            Weather w = Weather.parse(fc, s.getString("air", ""), s.getLong("fetched", 0));
            return w.valid() ? w : null;
        } catch (Exception e) {
            return null;
        }
    }

    static long fetchedAt(Context c) { return sp(c).getLong("fetched", 0); }

    static String lastAir(Context c) { return sp(c).getString("air", ""); }

    static synchronized void savePlace(Context c, Place p) {
        sp(c).edit().putString("place", p.toJson().toString()).apply();
    }

    static synchronized Place loadPlace(Context c) {
        String j = sp(c).getString("place", null);
        return j == null ? null : Place.fromJson(j);
    }

    static Cards.Data data(Context c) {
        Cards.Data d = new Cards.Data();
        d.weather = loadWeather(c);
        d.place = loadPlace(c);
        d.units = units(c);
        d.nowSec = System.currentTimeMillis() / 1000L;
        d.tzOffset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000;
        applyColors(c, d);
        return d;
    }

    // ---- notification de-duplication ----
    static synchronized boolean markSeen(Context c, String key) {
        SharedPreferences s = sp(c);
        Set<String> seen = new HashSet<String>(s.getStringSet("seen", new HashSet<String>()));
        if (seen.contains(key)) return false;
        if (seen.size() > 60) seen.clear();
        seen.add(key);
        s.edit().putStringSet("seen", seen).apply();
        return true;
    }

    // ---- permissions ----
    static boolean fine(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean coarse(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean anyLocation(Context c) {
        return fine(c) || coarse(c);
    }

    static boolean background(Context c) {
        return Build.VERSION.SDK_INT < 29
                || c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean notificationsAllowed(Context c) {
        return Build.VERSION.SDK_INT < 33
                || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
}
