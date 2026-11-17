package app.mapsandcats;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * In-app updates: downloads the new APK from this app's GitHub release, checks its SHA-256, and hands it to Android's
 * PackageInstaller, which asks the person to confirm. Android itself refuses an APK signed with a different key.
 */
final class ApkUpdater {
    private ApkUpdater() {}

    interface Progress { void onText(String text); }

    static boolean canInstall(Context c) {
        return Build.VERSION.SDK_INT < 26 || c.getPackageManager().canRequestPackageInstalls();
    }

    static void openInstallPermission(Context c) {
        c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName()))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    /** Never call on the main thread. Only this project's GitHub release downloads are accepted. */
    static void downloadAndInstall(Context c, String url, String sha256, Progress p) throws Exception {
        if (url == null || !url.startsWith("https://github.com/VK-VfX/")) throw new IOException("Unexpected download address");
        File dir = new File(c.getCacheDir(), "updates");
        dir.mkdirs();
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) f.delete();
        File out = new File(dir, "mapsandcats-update.apk");
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            Diag.log(c, "update", "download HTTP " + conn.getResponseCode());
            if (conn.getResponseCode() != 200) throw new IOException("HTTP " + conn.getResponseCode());
            long total = conn.getContentLengthLong();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = conn.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[16384];
                long done = 0;
                int n, last = -1;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                    md.update(buf, 0, n);
                    done += n;
                    if (done > 120L * 1024 * 1024) throw new IOException("File too large");
                    if (total > 0) {
                        int pct = (int) (done * 100 / total);
                        if (pct != last) { last = pct; p.onText("Downloading " + pct + "%"); }
                    }
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) hex.append(String.format("%02x", b));
            if (sha256 != null && sha256.length() > 0 && !hex.toString().equalsIgnoreCase(sha256)) {
                out.delete();
                Diag.log(c, "update", "checksum mismatch: got " + hex.substring(0, 12));
                throw new IOException("The download didn't match its checksum. Try again in a few minutes.");
            }
            Diag.log(c, "update", "downloaded " + out.length() + " bytes, sha256 " + hex.substring(0, 12) + " ok");
        } finally {
            conn.disconnect();
        }
        p.onText("Installing…");
        install(c, out);
        p.onText("Waiting for you to confirm the install");
    }

    private static void install(Context c, File apk) throws Exception {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(c.getPackageName());
        int id = pi.createSession(params);
        PackageInstaller.Session s = pi.openSession(id);
        try {
            try (OutputStream os = s.openWrite("update.apk", 0, apk.length()); InputStream in = new FileInputStream(apk)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                s.fsync(os);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            Intent cb = new Intent(c, InstallReceiver.class).setPackage(c.getPackageName());
            Diag.log(c, "update", "install session " + id + " committed");
            s.commit(PendingIntent.getBroadcast(c, id, cb, flags).getIntentSender());
        } catch (Exception e) {
            s.abandon();
            throw e;
        } finally {
            s.close();
        }
    }

    /** Receives the installer's progress; shows Android's confirmation screen when it asks for it. */
    public static final class InstallReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent intent) {
            int st = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            Diag.log(c, "installer", "status " + st + " " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (st != PackageInstaller.STATUS_SUCCESS) {
                String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Toast.makeText(c, "Install didn't finish" + (msg != null ? ": " + msg : ""), Toast.LENGTH_LONG).show();
            }
        }
    }
}
