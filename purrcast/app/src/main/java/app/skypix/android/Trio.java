package app.skypix.android;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/** How Purrcast, Zamoled and Maps and Cats know about each other. Everything here is safe when the other app is missing. */
final class Trio {
    private Trio() {}

    static final String ZAMOLED = "com.nikopick.zamoled";
    static final String MAPS = "app.mapsandcats";
    static final String REPO = "https://github.com/VK-VfX/nikezverapps";

    private static final String RELEASES = "https://api.github.com/repos/VK-VfX/nikezverapps/releases?per_page=30";
    private static final String UPDATE = "https://raw.githubusercontent.com/VK-VfX/nikezverapps/main/purrcast/dist/update.json";

    /** Newest published versions, keyed "purrcast", "zamoled", "mapsandcats". Filled in the background. */
    static volatile Map<String, String> latest = new HashMap<String, String>();
    private static volatile long fetchedAt;
    private static volatile boolean fetching;

    /** True when Zamoled and Maps and Cats are both installed next to Purrcast. */
    static boolean allInstalled(Context c) { return installed(c, ZAMOLED) && installed(c, MAPS); }

    static boolean installed(Context c, String pkg) {
        return c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }

    static String installedVersion(Context c, String pkg) {
        try {
            return c.getPackageManager().getPackageInfo(pkg, 0).versionName;
        } catch (Exception e) {
            return null;
        }
    }

    /** "12 cats caught" from another app's public Cat Club numbers, or null when it is not installed. */
    static String stat(Context c, String authority) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Uri.parse("content://" + authority + "/stats"), null, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                return cur.getInt(cur.getColumnIndexOrThrow("value")) + " " + cur.getString(cur.getColumnIndexOrThrow("label"));
            }
        } catch (Throwable ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return null;
    }

    /** Another app's day streak as {count, best, last visit day}, or null when it is not installed or is an older version without these numbers. */
    static long[] streakOf(Context c, String authority) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Uri.parse("content://" + authority + "/stats"), null, null, null, null);
            if (cur != null && cur.moveToFirst()) {
                int b = cur.getColumnIndex("best"), l = cur.getColumnIndex("last");
                if (b < 0 || l < 0) return null;
                return new long[]{cur.getInt(cur.getColumnIndexOrThrow("value")), cur.getInt(b), cur.getLong(l)};
            }
        } catch (Throwable ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return null;
    }

    static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? parse(x[i]) : 0, q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    interface Done { void run(); }

    /** Fetches the latest versions at most every 30 minutes; calls back on a background thread when new data is in. */
    static void refresh(final Done done) {
        if (fetching || System.currentTimeMillis() - fetchedAt < 30L * 60 * 1000) return;
        fetching = true;
        new Thread(new Runnable() {
            @Override public void run() {
                Map<String, String> out = new HashMap<String, String>();
                try {
                    JSONArray all = new JSONArray(Net.get(RELEASES));
                    for (int i = 0; i < all.length(); i++) {
                        JSONObject r = all.getJSONObject(i);
                        if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue;
                        String tag = r.optString("tag_name");
                        for (String app : new String[]{"zamoled", "mapsandcats"}) {
                            if (tag.startsWith(app + "-v") && !out.containsKey(app)) out.put(app, tag.substring(app.length() + 2));
                        }
                    }
                } catch (Throwable ignored) {
                }
                try {
                    out.put("purrcast", new JSONObject(Net.get(UPDATE)).getString("versionName"));
                } catch (Throwable ignored) {
                }
                if (!out.isEmpty()) {
                    latest = out;
                    fetchedAt = System.currentTimeMillis();
                }
                fetching = false;
                if (done != null) done.run();
            }
        }, "trio-latest").start();
    }

    /** Opens a sibling's link when it is installed, otherwise the fallback page. */
    static void open(Context c, String uri, String fallback) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(c.getPackageManager()) != null) {
                c.startActivity(i);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallback)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Throwable ignored) {
        }
    }
}
