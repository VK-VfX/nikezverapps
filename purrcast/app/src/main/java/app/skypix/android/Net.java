package app.skypix.android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class Net {
    private Net() {}

    static final String UA = "Purrcast/1.0 (Android weather app; offline-first, no tracking)";

    /** GET with a couple of gentle retries: Open-Meteo answers 503/429 now and then, and a second try a moment later almost always works. */
    static String get(String url) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                try { Thread.sleep(attempt == 1 ? 1500 : 4000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw last; }
            }
            try {
                return getOnce(url);
            } catch (HttpError e) {
                last = e;
                if (e.code < 500 && e.code != 429) throw e; // a real client error will not get better
            } catch (IOException e) {
                last = e; // timeouts and dropped connections
            }
        }
        throw last;
    }

    private static final class HttpError extends IOException {
        final int code;
        HttpError(int code, String url) { super("HTTP " + code + " for " + url); this.code = code; }
    }

    private static String getOnce(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) throw new HttpError(code, url);
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > 3 * 1024 * 1024) throw new IOException("response too large");
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
