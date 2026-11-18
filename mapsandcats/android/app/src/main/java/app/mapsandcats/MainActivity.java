package app.mapsandcats;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.Locale;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

/** Thin shell: serves the bundled Maps and Cats web app and forwards the location permission. */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final int REQ_LOC = 7;
    private WebView web;
    private String pendingOrigin;
    private GeolocationPermissions.Callback pendingCb;

    // --- native text-to-speech (WebView has no reliable speechSynthesis) ---
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private Locale defaultLocale = Locale.getDefault();
    private final ArrayDeque<String[]> ttsQueue = new ArrayDeque<>(); // {text, languageTag or ""}
    private int ttsId = 0;

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            ttsReady = true;
            try { if (tts.getVoice() != null) defaultLocale = tts.getVoice().getLocale(); } catch (Exception ignored) {}
            tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)   // ducks music like a real nav app
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { runOnUiThread(() -> speakNext()); }
                @Override public void onError(String id) { runOnUiThread(() -> speakNext()); }
            });
        });
    }
    /** Segments are spoken one after another so each can switch language (a Chinese place name gets a Chinese voice). */
    private void speakNext() {
        if (!ttsReady) return;
        String[] s = ttsQueue.poll();
        if (s == null) return;
        Locale want = s[1].isEmpty() ? defaultLocale : Locale.forLanguageTag(s[1]);
        int avail = tts.isLanguageAvailable(want);
        tts.setLanguage(avail >= TextToSpeech.LANG_AVAILABLE ? want : defaultLocale);
        tts.speak(s[0], TextToSpeech.QUEUE_FLUSH, null, "mc" + (ttsId++));
    }
    /** Exposed to the page as window.AndroidUpdater: in-app APK updates from this app's GitHub release. */
    private class UpdateBridge {
        @JavascriptInterface public boolean canInstall() { return ApkUpdater.canInstall(MainActivity.this); }
        @JavascriptInterface public void openPermission() { runOnUiThread(() -> ApkUpdater.openInstallPermission(MainActivity.this)); }
        @JavascriptInterface public void install(String url, String sha256) {
            final Context app = getApplicationContext();
            new Thread(() -> {
                try {
                    ApkUpdater.downloadAndInstall(app, url, sha256, text -> report(text));
                } catch (Exception e) {
                    Diag.error(app, "update-install", e);
                    report("Update failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                }
            }).start();
        }
        private void report(String text) {
            runOnUiThread(() -> web.evaluateJavascript("window.__updProgress && window.__updProgress(" + JSONObject.quote(text) + ")", null));
        }
    }
    /** Exposed to the page as window.AndroidWeb: fetches a public web page for the place search (the page itself cannot, CORS). */
    private class WebBridge {
        @JavascriptInterface public void get(final String url, final String id) {
            new Thread(() -> {
                int status = 0; String finalUrl = url, body = "";
                try {
                    Uri u = Uri.parse(url);
                    String h = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
                    if (!"https".equals(u.getScheme()) || h.isEmpty() || h.equals("localhost") || h.endsWith(".local") || h.matches("[\\d.]+") || h.contains(":"))
                        throw new Exception("blocked");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                    c.setConnectTimeout(7000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(true);
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
                    c.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag() + ",en;q=0.8");
                    c.setRequestProperty("Accept", "text/html,application/json;q=0.9,*/*;q=0.5");
                    status = c.getResponseCode(); finalUrl = c.getURL().toString();
                    java.io.InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
                    if (in != null) {
                        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[16384]; int n, total = 0;
                        while ((n = in.read(buf)) > 0 && total < 700000) { out.write(buf, 0, n); total += n; }
                        in.close(); body = out.toString("UTF-8");
                    }
                } catch (Exception e) { status = 0; }
                final int st = status; final String fu = finalUrl, bd = body;
                runOnUiThread(() -> web.evaluateJavascript("window.__webGot && window.__webGot(" + JSONObject.quote(id) + "," + st + "," + JSONObject.quote(fu) + "," + JSONObject.quote(bd) + ")", null));
            }).start();
        }
    }
    /** Exposed to the page as window.AndroidApps: opens a sibling app (Purrcast, Zamoled), or its page when it isn't installed. */
    private class AppsBridge {
        @JavascriptInterface public boolean installed(String pkg) {
            return getPackageManager().getLaunchIntentForPackage(pkg) != null;
        }
        @JavascriptInterface public void open(String pkg, String url) {
            runOnUiThread(() -> {
                try {
                    Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (li != null) { startActivity(li); return; }
                    if (url != null && (url.startsWith("https://github.com/VK-VfX/") || url.startsWith("https://t.me/"))) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception ignored) {}
            });
        }
    }
    private static final String[][] SIBLINGS = {
        {"purrcast", "app.skypix.weather", "app.skypix.weather.catclub"},
        {"zamoled", "com.nikopick.zamoled", "com.nikopick.zamoled.catclub"},
    };
    private String pendingPlace = "";
    private String pendingBody;
    private void toast(String s) { runOnUiThread(() -> android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show()); }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == 51 && pendingBody != null) {
            try (java.io.OutputStream os = getContentResolver().openOutputStream(data.getData())) { os.write(pendingBody.getBytes("UTF-8")); toast("Saved."); } catch (Exception e) { toast("Couldn't save that file."); }
            pendingBody = null;
        } else if (request == 52) {
            try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
                java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n, total = 0;
                while ((n = in.read(buf)) > 0 && total < 1000000) { o.write(buf, 0, n); total += n; }
                final String text = o.toString("UTF-8");
                runOnUiThread(() -> web.evaluateJavascript("window.__macFilePicked && window.__macFilePicked(" + JSONObject.quote(text) + ")", null));
            } catch (Exception e) { toast("Couldn't open that file."); }
        }
    }

    /** mapsandcats://place?lat=..&lon=..&name=..  (sent by Purrcast): remembered until the page asks for it. */
    private void captureLink(Intent i) {
        Uri d = i == null ? null : i.getData();
        if (d == null || !"mapsandcats".equals(d.getScheme()) || !"place".equals(d.getHost())) return;
        try {
            double lat = Double.parseDouble(d.getQueryParameter("lat")), lon = Double.parseDouble(d.getQueryParameter("lon"));
            if (Math.abs(lat) > 90 || Math.abs(lon) > 180) return;
            String name = d.getQueryParameter("name");
            pendingPlace = new JSONObject().put("lat", lat).put("lon", lon).put("name", name == null ? "" : name).toString();
        } catch (Exception ignored) {}
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureLink(intent);
        if (web != null) web.evaluateJavascript("window.__macCheckPending && window.__macCheckPending()", null);
    }

    /** Exposed to the page as window.AndroidTrio: the shared Cat Club numbers and links to Purrcast and Zamoled. */
    private class TrioBridge {
        /** The day streak and its shield text, shown by the other two apps in their Cat Club line. */
        @JavascriptInterface public void setStreak(int days, int best, double last, String label) {
            getSharedPreferences(CatClubProvider.PREFS, MODE_PRIVATE).edit().putInt("streak_days", days).putInt("streak_best", best).putLong("streak_last", (long) last).putString("streak_label", label == null ? "day streak" : label).apply();
        }
        @JavascriptInterface public void shareText(String t) {
            runOnUiThread(() -> { try { startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "Keep your progress code")); } catch (Exception e) { toast("No app found for that. Try Copy."); } });
        }
        @JavascriptInterface public void saveFile(String name, String mime, String body) {
            pendingBody = body;
            runOnUiThread(() -> { try { startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name), 51); } catch (Exception e) { toast("No app found for that. Try Copy."); } });
        }
        @JavascriptInterface public void saveImage(String name, String dataUrl) {
            if (Build.VERSION.SDK_INT < 29) { toast("Saving a picture needs Android 10. Try Copy."); return; }
            try {
                byte[] png = android.util.Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), android.util.Base64.DEFAULT);
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name); v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png"); v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Trio");
                Uri u = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                try (java.io.OutputStream os = getContentResolver().openOutputStream(u)) { os.write(png); }
                toast("Saved to Pictures/Trio.");
            } catch (Exception e) { toast("Couldn't save the picture. Try Copy."); }
        }
        @JavascriptInterface public void addCalendar(String title, String desc) {
            runOnUiThread(() -> { try { long now = System.currentTimeMillis(); startActivity(new Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI).putExtra(android.provider.CalendarContract.Events.TITLE, title).putExtra(android.provider.CalendarContract.Events.DESCRIPTION, desc).putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, now).putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, now).putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, true)); } catch (Exception e) { toast("No calendar app found. Try Copy."); } });
        }
        @JavascriptInterface public void pickFile() {
            runOnUiThread(() -> { try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 52); } catch (Exception e) { toast("No app found for that."); } });
        }
        @JavascriptInterface public String clipboardText() {
            try { android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE); CharSequence t = cm.hasPrimaryClip() ? cm.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this) : null; return t == null ? "" : t.toString(); } catch (Exception e) { return ""; }
        }
        /** The page's own log lines (tag + message) go into the same diagnostic log as the shell's. */
        @JavascriptInterface public void diagLog(String tag, String msg) { Diag.log(MainActivity.this, tag == null ? "page" : tag, msg); }
        @JavascriptInterface public void shareLog(String appVersion) { runOnUiThread(() -> Diag.share(MainActivity.this, appVersion)); }
        @JavascriptInterface public void clearLog() { Diag.clear(MainActivity.this); }
        @JavascriptInterface public void setCats(int n) {
            getSharedPreferences(CatClubProvider.PREFS, MODE_PRIVATE).edit().putInt("cats", n).apply();
        }
        /** Keeps the screen on while navigating or on a Quest walk. */
        @JavascriptInterface public void keepAwake(boolean on) {
            runOnUiThread(() -> {
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }
        @JavascriptInterface public String takePlace() { String p = pendingPlace; pendingPlace = ""; return p; }
        /** {"purrcast":{"installed":true,"version":"1.0.15","stat":"5 day streak"}, ...} */
        @JavascriptInterface public String siblings() {
            JSONObject out = new JSONObject();
            for (String[] s : SIBLINGS) {
                try {
                    JSONObject o = new JSONObject();
                    boolean has = getPackageManager().getLaunchIntentForPackage(s[1]) != null;
                    o.put("installed", has);
                    if (has) {
                        try { o.put("version", getPackageManager().getPackageInfo(s[1], 0).versionName); } catch (Exception ignored) {}
                        android.database.Cursor c = null;
                        try {
                            c = getContentResolver().query(Uri.parse("content://" + s[2] + "/stats"), null, null, null, null);
                            if (c != null && c.moveToFirst()) {
                                o.put("stat", c.getInt(c.getColumnIndexOrThrow("value")) + " " + c.getString(c.getColumnIndexOrThrow("label")));
                                int bi = c.getColumnIndex("best"), li = c.getColumnIndex("last"), mi = c.getColumnIndex("mood");
                                if (mi >= 0) o.put("mood", c.getString(mi));
                                if (bi >= 0 && li >= 0) o.put("streak", new JSONObject().put("count", c.getInt(c.getColumnIndexOrThrow("value"))).put("best", c.getInt(bi)).put("last", c.getLong(li)));
                            }
                        } catch (Exception ignored) {} finally { if (c != null) c.close(); }
                    }
                    out.put(s[0], o);
                } catch (Exception ignored) {}
            }
            return out.toString();
        }
        /** Opens purrcast://... or zamoled://... when installed, otherwise the project page. */
        @JavascriptInterface public void openUri(String uri, String fallback) {
            runOnUiThread(() -> {
                try {
                    if (uri != null && (uri.startsWith("purrcast://") || uri.startsWith("zamoled://"))) {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                        if (i.resolveActivity(getPackageManager()) != null) { startActivity(i); return; }
                    }
                    if (fallback != null && fallback.startsWith("https://github.com/VK-VfX/")) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(fallback)));
                } catch (Exception ignored) {}
            });
        }
    }
    /** Exposed to the page as window.AndroidTTS */
    private class TtsBridge {
        @JavascriptInterface public boolean ready() { return ttsReady; }
        @JavascriptInterface public void stop() { runOnUiThread(() -> { ttsQueue.clear(); if (ttsReady) tts.stop(); }); }
        @JavascriptInterface public void speak(String json) {
            runOnUiThread(() -> {
                if (!ttsReady) return;
                try {
                    JSONArray a = new JSONArray(json);
                    ttsQueue.clear(); tts.stop();
                    for (int i = 0; i < a.length(); i++) {
                        JSONObject o = a.getJSONObject(i);
                        ttsQueue.add(new String[]{o.getString("text"), o.optString("lang", "")});
                    }
                    speakNext();
                } catch (Exception ignored) {}
            });
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Diag.init(this);
        Diag.log(this, "start", "shell " + Build.VERSION.RELEASE + " " + Build.MODEL);
        web = new WebView(this);
        web.setBackgroundColor(0xFF000000);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .setDomain(HOST)
            .addPathHandler("/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();

        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (HOST.equals(r.getUrl().getHost())) return false;
                startActivity(new Intent(Intent.ACTION_VIEW, r.getUrl())); // external links leave the app
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    cb.invoke(origin, true, false);
                } else {
                    pendingOrigin = origin; pendingCb = cb;
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOC);
                }
            }
        });
        initTts();
        web.addJavascriptInterface(new TtsBridge(), "AndroidTTS");
        web.addJavascriptInterface(new AppsBridge(), "AndroidApps");
        web.addJavascriptInterface(new UpdateBridge(), "AndroidUpdater");
        web.addJavascriptInterface(new TrioBridge(), "AndroidTrio");
        web.addJavascriptInterface(new WebBridge(), "AndroidWeb");
        captureLink(getIntent());
        web.loadUrl("https://" + HOST + "/index.html");
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_LOC && pendingCb != null) {
            boolean ok = false;
            for (int r : res) if (r == PackageManager.PERMISSION_GRANTED) ok = true;
            pendingCb.invoke(pendingOrigin, ok, false);
            pendingCb = null;
        }
    }

    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        // Let the web app close its own drawer/route first.
        web.evaluateJavascript("window.__macBack ? window.__macBack() : false", v -> {
            if (!"true".equals(v)) finish();
        });
    }
}
