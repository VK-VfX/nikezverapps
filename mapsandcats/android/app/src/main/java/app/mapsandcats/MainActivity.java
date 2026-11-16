package app.mapsandcats;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
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
                    report("Update failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                }
            }).start();
        }
        private void report(String text) {
            runOnUiThread(() -> web.evaluateJavascript("window.__updProgress && window.__updProgress(" + JSONObject.quote(text) + ")", null));
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
        @JavascriptInterface public void setCats(int n) {
            getSharedPreferences(CatClubProvider.PREFS, MODE_PRIVATE).edit().putInt("cats", n).apply();
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
                            if (c != null && c.moveToFirst()) o.put("stat", c.getInt(c.getColumnIndexOrThrow("value")) + " " + c.getString(c.getColumnIndexOrThrow("label")));
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
