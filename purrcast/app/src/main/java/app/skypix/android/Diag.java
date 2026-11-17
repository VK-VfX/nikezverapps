package app.skypix.android;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The log catcher: a small diagnostic log kept on the phone and shared only when the owner taps Share in About.
 * It records starts, crashes, update checks/downloads/installs and network failures. Never places, coordinates, searches or
 * anything typed: {@link #clean} strips decimals that look like coordinates and URL query strings before anything is written.
 */
final class Diag {
    private Diag() {}

    private static final int MAX = 48000;
    private static final Object LOCK = new Object();
    private static File file;
    private static Context app;

    /** For code with no Context at hand (needs {@link #init} to have run). */
    static void log(String tag, String msg) { if (app != null) log(app, tag, msg); }

    static void init(Context c) {
        synchronized (LOCK) {
            if (file == null) { app = c.getApplicationContext(); file = new File(app.getFilesDir(), "diag.log"); }
        }
    }

    static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("-?\\d{1,3}\\.\\d{4,}", "#").replaceAll("\\?[^\\s\"']+", "?...");
    }

    static void log(Context c, String tag, String msg) {
        try {
            init(c);
            String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date()) + " " + tag + " " + clean(msg) + "\n";
            synchronized (LOCK) {
                if (file.length() > MAX) trim();
                FileOutputStream os = new FileOutputStream(file, true);
                try { os.write(line.getBytes(Charset.forName("UTF-8"))); } finally { os.close(); }
            }
        } catch (Throwable ignored) {
        }
    }

    static void error(Context c, String tag, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        log(c, tag, s.length() > 2500 ? s.substring(0, 2500) : s);
    }

    private static void trim() {
        try {
            byte[] all = java.nio.file.Files.readAllBytes(file.toPath());
            int from = all.length / 2;
            while (from < all.length && all[from] != '\n') from++;
            FileOutputStream os = new FileOutputStream(file, false);
            try { os.write(all, Math.min(from + 1, all.length), Math.max(0, all.length - from - 1)); } finally { os.close(); }
        } catch (Throwable ignored) {
        }
    }

    /** The text that gets shared: header, why the app last stopped, then the log. */
    static String report(Context c) {
        init(c);
        StringBuilder b = new StringBuilder();
        b.append("Purrcast ").append(AppUpdate.installedName(c)).append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(") · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        b.append("Zamoled ").append(String.valueOf(Trio.installedVersion(c, Trio.ZAMOLED))).append(" · Maps and Cats ")
                .append(String.valueOf(Trio.installedVersion(c, Trio.MAPS))).append('\n');
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
                List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(null, 0, 5);
                for (ApplicationExitInfo x : exits) {
                    b.append("exit ").append(new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date(x.getTimestamp())))
                            .append(" reason=").append(x.getReason()).append(" status=").append(x.getStatus())
                            .append(" ").append(clean(x.getDescription())).append('\n');
                }
            } catch (Throwable ignored) {
            }
        }
        b.append("---\n");
        try {
            synchronized (LOCK) {
                if (file.exists()) b.append(new String(java.nio.file.Files.readAllBytes(file.toPath()), Charset.forName("UTF-8")));
            }
        } catch (Throwable ignored) {
        }
        return b.toString();
    }

    static void share(Context c) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Purrcast log").putExtra(Intent.EXTRA_TEXT, report(c));
        c.startActivity(Intent.createChooser(send, "Share Purrcast log").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static void clear(Context c) {
        init(c);
        synchronized (LOCK) { file.delete(); }
    }
}
