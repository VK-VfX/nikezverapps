package app.skypix.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.widget.Toast;

import app.skypix.core.Progress;
import app.skypix.core.SaveCode;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** The ways to keep or bring back the progress code. Only system intents and pickers: no permissions, no network. */
final class ProgressShare {
    private ProgressShare() {}

    static final String NAME = "Purrcast";

    static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_LONG).show(); }

    static void noApp(Context c) { toast(c, "No app found for that. Try Copy."); }

    static void shareText(Activity a, String text) {
        try {
            a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Keep your progress code"));
        } catch (Throwable t) {
            noApp(a);
        }
    }

    static void copy(Context c, String text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Purrcast progress code", text));
        toast(c, "Copied. Paste it somewhere safe.");
    }

    static void addCalendar(Activity a, String code) {
        try {
            long today = System.currentTimeMillis();
            a.startActivity(new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, NAME + " progress code")
                    .putExtra(CalendarContract.Events.DESCRIPTION, code)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, today)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, today)
                    .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true));
        } catch (Throwable t) {
            noApp(a);
        }
    }

    /** Opens the system "save as" screen; the caller writes the body in onActivityResult. */
    static boolean pickSave(Activity a, int request, String name, String mime) {
        try {
            a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name), request);
            return true;
        } catch (Throwable t) {
            noApp(a);
            return false;
        }
    }

    static boolean pickOpen(Activity a, int request) {
        try {
            a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), request);
            return true;
        } catch (Throwable t) {
            noApp(a);
            return false;
        }
    }

    static void writeTo(Context c, Uri uri, String body) {
        try {
            OutputStream os = c.getContentResolver().openOutputStream(uri);
            try { os.write(body.getBytes("UTF-8")); } finally { os.close(); }
            toast(c, "Saved.");
        } catch (Throwable t) {
            toast(c, "Couldn't save that file.");
        }
    }

    static String readFrom(Context c, Uri uri) throws java.io.IOException {
        java.io.InputStream in = c.getContentResolver().openInputStream(uri);
        try {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0 && total < 1_000_000) { o.write(buf, 0, n); total += n; }
            return o.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    /** A 1080 x 1350 black card: the app, the date, and the code in big mono type. */
    static Bitmap card(String code, String savedOn) {
        Bitmap b = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        cv.drawColor(0xFF000000);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF3DF5D0);
        p.setTextSize(64);
        p.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        cv.drawText(NAME + " progress", 80, 190, p);
        p.setColor(0xFFAEAEB8);
        p.setTextSize(36);
        p.setTypeface(Typeface.MONOSPACE);
        cv.drawText("Saved " + savedOn, 80, 250, p);
        p.setColor(0xFFF4F4F6);
        p.setTextSize(58);
        String[] groups = code.split("-");
        StringBuilder line = new StringBuilder();
        float y = 520;
        for (int i = 0; i < groups.length; i++) {
            String next = (line.length() == 0 ? "" : line + "-") + groups[i];
            if (p.measureText(next) > 920 && line.length() > 0) {
                cv.drawText(line.toString(), 80, y, p);
                y += 92;
                line.setLength(0);
                line.append(groups[i]);
            } else {
                line.setLength(0);
                line.append(next);
            }
        }
        if (line.length() > 0) cv.drawText(line.toString(), 80, y, p);
        p.setColor(0xFFAEAEB8);
        p.setTextSize(38);
        p.setTypeface(Typeface.DEFAULT);
        cv.drawText("Keep this safe. Paste it into", 80, 1120, p);
        cv.drawText("Restore progress to get your streak back.", 80, 1172, p);
        return b;
    }

    static void saveCard(Activity a, String code) {
        if (Build.VERSION.SDK_INT < 29) { noApp(a); return; }
        try {
            String day = new SimpleDateFormat("d MMM yyyy", Locale.US).format(new Date());
            Bitmap b = card(code, day);
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, "purrcast-progress-" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".png");
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Trio");
            Uri u = a.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            OutputStream os = a.getContentResolver().openOutputStream(u);
            try { b.compress(Bitmap.CompressFormat.PNG, 100, os); } finally { os.close(); }
            toast(a, "Saved to Pictures/Trio.");
        } catch (Throwable t) {
            toast(a, "Couldn't save the picture. Try Copy.");
        }
    }

    /** Reads a pasted code (or a whole note) and merges it. Never lowers anything. Returns the message to show. */
    static String restoreFromText(Context c, String pasted) {
        SaveCode.Result r = SaveCode.decode(pasted, SaveCode.PRC);
        if (r.kind == SaveCode.TYPO) throw new IllegalArgumentException("That code has a typo. Check the characters near the end.");
        if (r.kind == SaveCode.WRONG_APP) throw new IllegalArgumentException("That code belongs to " + appFor(r.otherPrefix) + ".");
        if (r.kind != SaveCode.OK) throw new IllegalArgumentException("I couldn't find a Purrcast code in that.");
        Progress merged;
        try {
            merged = Progress.merge(Store.loadProgress(c), Progress.fromPayload(r.payload));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("That code has a typo. Check the characters near the end.");
        }
        Store.applyProgress(c, merged);
        return "Restored: " + merged.streak.count + " day streak, best " + merged.streak.best + ".";
    }

    static String appFor(String prefix) { return SaveCode.ZAM.equals(prefix) ? "Zamoled" : SaveCode.MAC.equals(prefix) ? "Maps and Cats" : "another app"; }
}
