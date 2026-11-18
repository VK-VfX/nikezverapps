package app.skypix.android;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import app.skypix.core.Cards;
import app.skypix.core.Wmo;
import app.skypix.core.Weather;
import app.skypix.weather.R;
import app.skypix.core.Fmt;
import app.skypix.core.Gfx;
import app.skypix.core.Insights;
import app.skypix.core.Place;
import app.skypix.core.Variants;

import java.util.HashMap;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 1, REQ_BACKGROUND = 2, REQ_NOTIFY = 3;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ScrollView scroll;
    private LinearLayout content;
    private TextView statusText;
    private FrameLayout rootView;
    private Ui.SkyTint skyTint;
    private int titleTaps;
    private long lastTitleTap;
    private boolean refreshing;
    private int pendingScrollKind = -1;
    private final HashMap<Integer, View> cardByKind = new HashMap<Integer, View>();

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            rebuild();
            ui.postDelayed(this, 60000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        rootView = root;
        root.addView(new Ui.Backdrop(this), new FrameLayout.LayoutParams(-1, -1));
        skyTint = new Ui.SkyTint(this);
        root.addView(skyTint, new FrameLayout.LayoutParams(-1, Ui.dp(this, 300)));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        content.setPadding(pad, Ui.dp(this, 20), pad, Ui.dp(this, 32));
        scroll.addView(content, new ViewGroup.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        applySystemBars();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        pendingScrollKind = i != null && i.hasExtra("from_widget") ? i.getIntExtra("from_widget", -1) : -1;
        Uri d = i == null ? null : i.getData();
        // purrcast://weather?lat=..&lon=..&name=..  (sent by Maps and Cats): save the place and fetch its forecast
        if (d != null && "purrcast".equals(d.getScheme()) && "weather".equals(d.getHost())) {
            try {
                double lat = Double.parseDouble(d.getQueryParameter("lat")), lon = Double.parseDouble(d.getQueryParameter("lon"));
                String name = d.getQueryParameter("name");
                if (Math.abs(lat) <= 90 && Math.abs(lon) <= 180) {
                    boolean have = false;
                    for (Places.Saved s : Places.list(this)) {
                        double dy = (s.place.lat - lat) * 111.0, dx = (s.place.lon - lon) * 111.0 * Math.cos(Math.toRadians(lat));
                        if (dx * dx + dy * dy < 2.25) have = true; // within 1.5 km
                    }
                    if (!have) {
                        Place p = new Place();
                        p.lat = lat;
                        p.lon = lon;
                        p.precise = false;
                        p.provider = "link";
                        p.fixTimeMs = System.currentTimeMillis();
                        Places.add(this, name == null ? "" : name.length() > 40 ? name.substring(0, 40) : name, p);
                        android.widget.Toast.makeText(this, "Saved " + (name == null || name.length() == 0 ? "the place" : name) + " from Maps and Cats", android.widget.Toast.LENGTH_SHORT).show();
                    }
                    pendingRefresh = true;
                }
            } catch (Exception ignored) {
            }
        }
    }

    private boolean pendingRefresh;

    private void applySystemBars() {
        boolean dark = Ui.isDark(this);
        int bg = Ui.bg(this);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (!dark) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        applySystemBars();
        rebuild();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Store.visible = true;
        Notifier.ensureChannels(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Ui.animStartMs = android.os.SystemClock.uptimeMillis();
        Store.touchStreak(this);
        Store.applyHero(this);
        rebuild();
        Jobs.sync(this);
        startRefresh(pendingRefresh);
        pendingRefresh = false;
        ui.postDelayed(ticker, 60000);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    protected void onStop() {
        Store.visible = false;
        super.onStop();
    }

    // ---- refresh ----------------------------------------------------------------------------------------------------

    private void startRefresh(final boolean force) {
        if (refreshing || !Store.anyLocation(this)) return;
        refreshing = true;
        setStatus("UPDATING...");
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Updater.run(app, true, 12000, force ? 0 : 8 * 60 * 1000L);
                } catch (Throwable t) {
                    Ui.report("refresh", t);
                }
                ui.post(new Runnable() {
                    @Override public void run() {
                        refreshing = false;
                        rebuild();
                    }
                });
            }
        }, "skypix-refresh").start();
    }

    private void setStatus(String s) {
        if (statusText != null) statusText.setText(s);
    }

    // ---- screen -----------------------------------------------------------------------------------------------------

    private LinearLayout.LayoutParams lp(int w, int h, float topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = Ui.dp(this, topDp);
        return p;
    }

    private boolean entered;

    private void rebuild() {
        try {
            rebuildUnsafe();
            if (!entered && content.getChildCount() > 0) {
                entered = true;
                Ui.enter(content);
            }
        } catch (Throwable t) {
            Ui.report("rebuild", t);
            content.removeAllViews();
            TextView e = new TextView(this);
            e.setText("Purrcast couldn't draw this screen.\n\n" + Ui.lastError);
            e.setTextColor(0xFFFF8A8A);
            e.setTextIsSelectable(true);
            content.addView(e);
        }
    }

    private void rebuildUnsafe() {
        final int keepY = scroll.getScrollY();
        content.removeAllViews();
        cardByKind.clear();
        boolean dark = Ui.isDark(this);
        int ink = dark ? 0xFFFFFFFF : 0xFF0E1630;
        int ink2 = dark ? 0x99FFFFFF : 0x990E1630;
        final Cards.Data d = Store.data(this);
        if (skyTint != null) skyTint.set(Cards.skyTop(d), dark);

        // header: big Google-style title, quiet status line, round refresh button
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView logo = new android.widget.ImageView(this);
        logo.setImageResource(R.drawable.purrcast_mark);
        logo.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        logo.setContentDescription("Purrcast");
        logo.setClipToOutline(true);
        final int logoRadius = Ui.dp(this, 15);
        logo.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), logoRadius); }
        });
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52));
        logoLp.rightMargin = Ui.dp(this, 14);
        head.addView(logo, logoLp);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, "Purrcast", 34, 500, Ui.onSurface(this));
        title.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                long now = android.os.SystemClock.uptimeMillis();
                titleTaps = now - lastTitleTap < 900 ? titleTaps + 1 : 1;
                lastTitleTap = now;
                if (titleTaps >= 7) {
                    titleTaps = 0;
                    Toast.makeText(MainActivity.this, "Meow! \uD83D\uDE3A", Toast.LENGTH_SHORT).show();
                    if (rootView != null) rootView.addView(new Ui.CatRain(MainActivity.this), new FrameLayout.LayoutParams(-1, -1));
                }
            }
        });
        titles.addView(title);
        statusText = Ui.text(this, statusLine(d), 14, 400, Ui.onSurfaceVar(this));
        titles.addView(statusText, lp(-2, -2, 4));
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        Ui.RefreshButton refresh = new Ui.RefreshButton(this);
        refresh.spinning = refreshing;
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!Store.anyLocation(MainActivity.this)) requestLocation();
                else startRefresh(true);
            }
        });
        head.addView(refresh, new LinearLayout.LayoutParams(-2, -2));
        content.addView(head, lp(-1, -2, 0));

        Ui.StreakCard streakCard = new Ui.StreakCard(this, Store.streak(this), Store.bestStreak(this), Store.streakMask(this), Store.shieldLine(this));
        streakCard.setClickable(true);
        streakCard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showCatClubHelp(); }
        });
        content.addView(streakCard, lp(-1, -2, 16));
        final int ready = Store.rewardReady(this);
        if (ready > 0) {
            TextView note = Ui.text(this, "\ud83c\udf81 A special Zamoled wallpaper for your day " + ready + " streak is ready. Tap to claim it.", 14.5f, 500, Ui.primary(this));
            note.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
            note.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { claimStreakReward(); }
            });
            content.addView(note, lp(-1, -2, 10));
        }

        addBanners();

        if (d.ready()) {
            addCard(Cards.NOW_WIDE, 160, null, d, 20);
            List<Insights.Insight> notes = Insights.compute(d.weather, d.units, d.nowSec);
            int shown = 0;
            for (Insights.Insight in : notes) {
                if (shown >= 4) break;
                content.addView(new Ui.CardView(this, Cards.HEADSUP, 68, in, d), lp(-1, -2, 12));
                shown++;
            }
            if (shown == 0) addCard(Cards.HEADSUP, 68, null, d, 12);
            addCard(Cards.HOURLY, 150, null, d, 12);
            addCard(Cards.FORECAST, 236, null, d, 12);
            addCard(Cards.DETAILS, 196, null, d, 12);
            addCard(Cards.SUN, 150, null, d, 12);
            addCard(Cards.LOCATION, 150, null, d, 12);
        }

        addSettings(ink, ink2);
        addPlaces();
        addProgress();
        addUpdates();
        addWidgetPicker(ink, ink2);
        addPlacedWidgets();
        addAbout();

        TextView foot = new TextView(this);
        foot.setText("Weather & air quality: Open-Meteo.com (CC BY 4.0)\nPlace names: © OpenStreetMap contributors\nCat emoji: Google Noto Emoji (Apache 2.0)\nNo accounts, no analytics, no Google services. We collect nothing: your location is sent to Open-Meteo only to get your forecast.\nAlerts are forecast-based nudges, not official warnings. In an emergency follow your local authorities (for example MET Malaysia and NADMA).");
        foot.setTextSize(12);
        foot.setTypeface(AndroidGfx.typeface(400));
        foot.setTextColor(Ui.onSurfaceVar(this));
        foot.setLineSpacing(0, 1.2f);
        content.addView(foot, lp(-1, -2, 26));
        if (Ui.lastError != null) {
            TextView err = new TextView(this);
            err.setText("Last issue (please send this):\n" + Ui.lastError);
            err.setTextColor(0xFFFF8A8A);
            err.setTextSize(10.5f);
            err.setTextIsSelectable(true);
            content.addView(err, lp(-1, -2, 14));
        }

        if (pendingScrollKind < 0) scroll.post(new Runnable() {
            @Override public void run() { scroll.scrollTo(0, keepY); }
        });
        if (pendingScrollKind >= 0) {
            final View target = cardByKind.get(pendingScrollKind);
            pendingScrollKind = -1;
            if (target != null) scroll.post(new Runnable() {
                @Override public void run() { scroll.smoothScrollTo(0, Math.max(0, target.getTop() - Ui.dp(MainActivity.this, 8))); }
            });
        }
    }

    private void addCard(int kind, float h, Insights.Insight in, Cards.Data d, float topDp) {
        Ui.CardView v = new Ui.CardView(this, kind, h, in, d);
        content.addView(v, lp(-1, -2, topDp));
        if (!cardByKind.containsKey(kind)) cardByKind.put(kind, v);
    }

    private String statusLine(Cards.Data d) {
        if (!Store.anyLocation(this)) return "Location needed";
        if (refreshing) return "Updating\u2026";
        if (d.place == null) return "Finding you\u2026";
        long fetched = Store.fetchedAt(this);
        StringBuilder sb = new StringBuilder();
        if (fetched > 0) sb.append("Updated ").append(Fmt.ago((System.currentTimeMillis() - fetched) / 1000L));
        if (!Float.isNaN(d.place.accuracyM)) sb.append(sb.length() > 0 ? " \u00b7 " : "").append("\u00b1").append(Math.round(d.place.accuracyM)).append(" m");
        return sb.length() == 0 ? "Ready" : sb.toString();
    }

    // ---- banners ----------------------------------------------------------------------------------------------------

    private void banner(String title, String body, String button, View.OnClickListener action) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        box.setPadding(p, p, p, p);
        box.setBackground(Ui.surface(this, Ui.primaryContainer(this), Ui.CARD));
        box.addView(Ui.text(this, title, 20, 500, Ui.onPrimaryContainer(this)));
        TextView t = Ui.text(this, body, 14.5f, 400, Ui.onPrimaryContainer(this));
        t.setLineSpacing(0, 1.2f);
        box.addView(t, lp(-1, -2, 8));
        Ui.Btn b = new Ui.Btn(this, button, true);
        b.setOnClickListener(action);
        box.addView(b, lp(-2, -2, 14));
        content.addView(box, lp(-1, -2, 16));
    }

    private void addBanners() {
        if (!Store.anyLocation(this)) {
            banner("Where are you?", "Purrcast only needs your location. It is used to show weather for the exact spot you are standing, and it never leaves your phone except as coordinates sent to the weather service.",
                    "Allow location", new View.OnClickListener() {
                        @Override public void onClick(View v) { requestLocation(); }
                    });
            return;
        }
        if (!Store.fine(this)) {
            banner("Approximate only", "Android is only sharing a rough area. Switch Location to \"Precise\" for street-level accuracy.",
                    "Open settings", new View.OnClickListener() {
                        @Override public void onClick(View v) { openAppSettings(); }
                    });
        }
        if (!locationServicesOn()) {
            banner("Location is off", "Turn on location services so Purrcast can find where you are.",
                    "Open settings", new View.OnClickListener() {
                        @Override public void onClick(View v) { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
                    });
        }
        if (Build.VERSION.SDK_INT >= 33 && !Store.notificationsAllowed(this) && (Store.alerts(this) || Store.hourly(this))) {
            banner("Turn on alerts", "Allow notifications to get weather alerts and the hourly briefing.",
                    "Allow", new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
                        }
                    });
        }
    }

    private boolean locationServicesOn() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return true;
        if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void requestLocation() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
    }

    private void openAppSettings() {
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        try {
            Jobs.sync(this);
            if (code == REQ_LOCATION && Store.anyLocation(this)) {
                Jobs.kick(this);
                startRefresh(true);
            }
        } catch (Throwable t) {
            Ui.report("permission result", t);
        }
        ui.post(new Runnable() {
            @Override public void run() { rebuild(); }
        });
    }

    // ---- settings -------------------------------------------------------------------------------------------------

    // ---- progress: save a code, restore a code ----

    private static final int REQ_SAVE_TXT = 41, REQ_SAVE_JSON = 42, REQ_OPEN_BACKUP = 43;
    private String pendingBody;
    private boolean restoreWithSettings;

    private void addProgress() {
        LinearLayout box = section("Progress", 0);
        Ui.Btn save = new Ui.Btn(this, "Save", true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSaveSheet(); }
        });
        row(box, "Save my progress", "A short code for your streak. Keep it in notes, a screenshot or your calendar.", save);
        Ui.Btn restore = new Ui.Btn(this, "Restore", false);
        restore.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showRestoreDialog(); }
        });
        row(box, "Restore progress", "Paste a code or open a backup file. Nothing ever goes down.", restore);
    }

    private void showSaveSheet() {
        final String code = Store.progressCode(this);
        final java.util.List<String> names = new java.util.ArrayList<String>();
        names.add("Share to notes or chat");
        if (Build.VERSION.SDK_INT >= 29) names.add("Save a screenshot card");
        names.add("Add to calendar");
        names.add("Save as a text file");
        names.add("Full backup file (places and settings)");
        names.add("Copy");
        new android.app.AlertDialog.Builder(this).setTitle("Keep your streak safe")
                .setItems(names.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        String n = names.get(which);
                        if (n.startsWith("Share")) ProgressShare.shareText(MainActivity.this, "Purrcast progress code:\n" + code);
                        else if (n.startsWith("Save a screenshot")) ProgressShare.saveCard(MainActivity.this, code);
                        else if (n.startsWith("Add to")) ProgressShare.addCalendar(MainActivity.this, code);
                        else if (n.startsWith("Save as a text")) {
                            pendingBody = "Purrcast progress code\n" + code + "\n";
                            ProgressShare.pickSave(MainActivity.this, REQ_SAVE_TXT, "purrcast-progress-" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()) + ".txt", "text/plain");
                        } else if (n.startsWith("Full backup")) {
                            try {
                                pendingBody = Store.backupJson(MainActivity.this);
                                ProgressShare.pickSave(MainActivity.this, REQ_SAVE_JSON, "purrcast-backup-" + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()) + ".json", "application/json");
                            } catch (Throwable t) {
                                ProgressShare.toast(MainActivity.this, "Couldn't make the backup file.");
                            }
                        } else ProgressShare.copy(MainActivity.this, code);
                    }
                }).setNegativeButton("Close", null).show();
    }

    private void showRestoreDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        final android.widget.EditText field = new android.widget.EditText(this);
        field.setHint("PRC1-XXXX-XXXX-...");
        field.setMinLines(3);
        field.setGravity(Gravity.TOP);
        field.setTypeface(android.graphics.Typeface.MONOSPACE);
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (t != null && t.length() < 5000 && app.skypix.core.SaveCode.decode(t.toString(), app.skypix.core.SaveCode.PRC).kind == app.skypix.core.SaveCode.OK) field.setText(t);
            }
        } catch (Throwable ignored) {
        }
        box.addView(field, new LinearLayout.LayoutParams(-1, -2));
        final android.widget.CheckBox settings = new android.widget.CheckBox(this);
        settings.setText("Also restore my settings (backup files only)");
        box.addView(settings, new LinearLayout.LayoutParams(-1, -2));
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this).setTitle("Restore progress").setView(box)
                .setPositiveButton("Restore", null)
                .setNeutralButton("Open file", null)
                .setNegativeButton("Cancel", null).create();
        dlg.show();
        dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    String msg = ProgressShare.restoreFromText(MainActivity.this, field.getText().toString());
                    dlg.dismiss();
                    ProgressShare.toast(MainActivity.this, msg);
                    rebuild();
                } catch (IllegalArgumentException e) {
                    field.setError(e.getMessage());
                }
            }
        });
        dlg.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                restoreWithSettings = settings.isChecked();
                dlg.dismiss();
                ProgressShare.pickOpen(MainActivity.this, REQ_OPEN_BACKUP);
            }
        });
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == REQ_SAVE_TXT || request == REQ_SAVE_JSON) {
            if (pendingBody != null) ProgressShare.writeTo(this, data.getData(), pendingBody);
            pendingBody = null;
        } else if (request == REQ_OPEN_BACKUP) {
            try {
                String text = ProgressShare.readFrom(this, data.getData());
                String msg = text.trim().startsWith("{") ? Store.restoreBackupJson(this, text, restoreWithSettings) : ProgressShare.restoreFromText(this, text);
                ProgressShare.toast(this, msg);
                rebuild();
            } catch (IllegalArgumentException e) {
                ProgressShare.toast(this, e.getMessage());
            } catch (Throwable t) {
                ProgressShare.toast(this, "Couldn't open that file.");
            }
        }
    }

    private LinearLayout section(String title, int ink) {
        content.addView(Ui.text(this, title, 15, 500, Ui.primary(this)), lp(-2, -2, 32));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        box.setBackground(Ui.surface(this, Ui.container(this), Ui.CARD));
        content.addView(box, lp(-1, -2, 12));
        return box;
    }

    private LinearLayout row(LinearLayout parent, String title, String sub, View control) {
        if (parent.getChildCount() > 0) {
            View div = new View(this);
            div.setBackgroundColor(Ui.divider(this));
            parent.addView(div, new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(this, 1))));
        }
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(this, 64));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(0, Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12));
        text.addView(Ui.text(this, title, 16.5f, 400, Ui.onSurface(this)));
        if (sub != null) {
            TextView st = Ui.text(this, sub, 13.5f, 400, Ui.onSurfaceVar(this));
            st.setLineSpacing(0, 1.12f);
            text.addView(st, lp(-1, -2, 2));
        }
        r.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        if (control != null) r.addView(control, new LinearLayout.LayoutParams(-2, -2));
        parent.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    private CompoundButton toggle(boolean on, CompoundButton.OnCheckedChangeListener l) {
        Ui.Toggle s = new Ui.Toggle(this);
        s.setChecked(on);
        s.setOnCheckedChangeListener(l);
        return s;
    }

    private void addSettings(int ink, int ink2) {
        LinearLayout box = section("Settings", ink);

        // units
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        String cur = Store.tempPref(this);
        String[][] opts = {{"Auto", "auto"}, {"°C", "c"}, {"°F", "f"}};
        for (final String[] o : opts) {
            Ui.Btn b = new Ui.Btn(this, o[0], cur.equals(o[1]));
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Store.setTempPref(MainActivity.this, o[1]);
                    WidgetUpdater.updateAll(MainActivity.this);
                    rebuild();
                }
            });
            seg.addView(b, new LinearLayout.LayoutParams(-2, -2));
        }
        row(box, "Units", "Temperature, wind and pressure", seg);

        LinearLayout style = new LinearLayout(this);
        style.setOrientation(LinearLayout.HORIZONTAL);
        String[] bgNames = {"Solid", "Glass", "Clear"};
        for (int m = 0; m < 3; m++) {
            final int mode = m;
            Ui.Btn b = new Ui.Btn(this, bgNames[m], Store.bgMode(this) == m);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Store.setBgMode(MainActivity.this, mode);
                    WidgetUpdater.updateAll(MainActivity.this);
                    rebuild();
                }
            });
            style.addView(b, new LinearLayout.LayoutParams(-2, -2));
        }
        row(box, "Default widget background", "Solid, glass or clear for new widgets. Touch and hold any widget, then Widget settings, to change just that one", style);

        if (Build.VERSION.SDK_INT >= 31) {
            LinearLayout colors = new LinearLayout(this);
            colors.setOrientation(LinearLayout.HORIZONTAL);
            String[] names = {"Weather", "Material You"};
            for (int i = 0; i < 2; i++) {
                final boolean on = i == 1;
                Ui.Btn b = new Ui.Btn(this, names[i], Store.materialYou(this) == on);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        Store.setMaterialYou(MainActivity.this, on);
                        applySystemBars();
                        WidgetUpdater.updateAll(MainActivity.this);
                        rebuild();
                    }
                });
                colors.addView(b, new LinearLayout.LayoutParams(-2, -2));
            }
            row(box, "Colours", "Weather blue, or colours picked from your wallpaper", colors);
        }

        row(box, "Cat icons", "Weather shown as Google Noto cats, a different mood for every sky",
                toggle(Store.cats(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        Store.setCats(MainActivity.this, on);
                        app.skypix.core.Glyphs.cats = on;
                        WidgetUpdater.updateAll(MainActivity.this);
                        rebuild();
                    }
                }));

        if (Trio.allInstalled(this)) {
            row(box, "Show hero cat", "The masked hero cat on your widgets and streak card, in the mood of the weather",
                    toggle(Store.heroOn(this), new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                            Store.setHeroOn(MainActivity.this, on);
                            Store.applyHero(MainActivity.this);
                            WidgetUpdater.updateAll(MainActivity.this);
                            rebuild();
                        }
                    }));
        } else {
            row(box, "Hero cat", "Install Zamoled and Maps and Cats to meet the masked hero cat", null);
        }

        row(box, "Animate on unlock", "5 seconds of weather motion each time you open your phone",
                toggle(Store.animate(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        Store.setAnimate(MainActivity.this, on);
                        Jobs.syncAnimService(getApplicationContext());
                    }
                }));

        Ui.Btn playNow = new Ui.Btn(this, "Play now", false);
        playNow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (WidgetUpdater.count(MainActivity.this) == 0) {
                    Toast.makeText(MainActivity.this, "Add a widget first, then try again", Toast.LENGTH_LONG).show();
                    return;
                }
                Jobs.syncAnimService(getApplicationContext());
                Jobs.AnimService.play(MainActivity.this, "test");
                Toast.makeText(MainActivity.this, "Playing on your home screen widgets for 5 s", Toast.LENGTH_LONG).show();
                ui.postDelayed(new Runnable() {
                    @Override public void run() { rebuild(); }
                }, 6000);
            }
        });
        row(box, "Test the animation", animStatus(), playNow);

        row(box, "Emergency & weather alerts", "Floods, thunderstorms, bring-an-umbrella nudges, heat, UV and wind",
                toggle(Store.alerts(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        Store.setAlerts(MainActivity.this, on);
                        Jobs.sync(MainActivity.this);
                        if (on) askNotifications();
                    }
                }));

        row(box, "Cat weather news", "A bulletin from the cats every 2 hours, written from the live forecast",
                toggle(Store.hourly(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        Store.setHourly(MainActivity.this, on);
                        Jobs.sync(MainActivity.this);
                        if (on) askNotifications();
                    }
                }));

        Ui.Btn morningTime = Store.morning(this) ? new Ui.Btn(this, hourLabel(Store.morningHour(this)), false) : null;
        if (morningTime != null) morningTime.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickMorning(); }
        });
        LinearLayout morningControls = new LinearLayout(this);
        morningControls.setOrientation(LinearLayout.HORIZONTAL);
        morningControls.setGravity(Gravity.CENTER_VERTICAL);
        if (morningTime != null) morningControls.addView(morningTime, new LinearLayout.LayoutParams(-2, -2));
        morningControls.addView(toggle(Store.morning(this), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                Store.setMorning(MainActivity.this, on);
                Jobs.sync(MainActivity.this);
                if (on) askNotifications();
                rebuild();
            }
        }));
        row(box, "Morning briefing", "The day's weather and what to carry, once each morning", morningControls);

        row(box, "Quiet hours", "No cat news " + hourLabel(Store.quietStart(this)) + " – " + hourLabel(Store.quietEnd(this)),
                toggle(Store.quiet(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) {
                        Store.setQuiet(MainActivity.this, on);
                        rebuild();
                    }
                }));
        if (Store.quiet(this)) {
            LinearLayout times = new LinearLayout(this);
            times.setOrientation(LinearLayout.HORIZONTAL);
            Ui.Btn from = new Ui.Btn(this, "From " + hourLabel(Store.quietStart(this)), false);
            from.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { pickHour(true); }
            });
            Ui.Btn to = new Ui.Btn(this, "To " + hourLabel(Store.quietEnd(this)), false);
            to.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { pickHour(false); }
            });
            times.addView(from, new LinearLayout.LayoutParams(-2, -2));
            times.addView(to, new LinearLayout.LayoutParams(-2, -2));
            box.addView(times, lp(-2, -2, 4));
        }

        // location accuracy
        String locState = Store.fine(this) ? "Precise location is on" : Store.coarse(this) ? "Approximate only" : "Not allowed yet";
        Ui.Btn bg = null;
        String bgSub;
        if (Build.VERSION.SDK_INT < 29) bgSub = "Widgets refresh your position automatically";
        else if (Store.background(this)) bgSub = "Widgets follow you in the background";
        else {
            bgSub = "Widgets use your last known spot until you open Purrcast";
            if (Store.fine(this)) {
                bg = new Ui.Btn(this, "Always", false);
                bg.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (Build.VERSION.SDK_INT >= 30) {
                            Toast.makeText(MainActivity.this, "Choose \"Allow all the time\"", Toast.LENGTH_LONG).show();
                        }
                        requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, REQ_BACKGROUND);
                    }
                });
            }
        }
        row(box, locState, null, null);
        row(box, "Background location", bgSub, bg);
    }

    private String animStatus() {
        int widgets = WidgetUpdater.count(this);
        StringBuilder sb = new StringBuilder();
        if (widgets == 0) sb.append("No widgets on your home screen yet");
        else sb.append(Jobs.AnimService.running ? "Unlock listener is running" : "Unlock listener is not running");
        if (Jobs.AnimService.lastPlayMs > 0) {
            sb.append(" \u00b7 last played (").append(Jobs.AnimService.lastTrigger).append(") ").append(Fmt.time(Jobs.AnimService.lastPlayMs / 1000L, java.util.TimeZone.getDefault().getOffset(Jobs.AnimService.lastPlayMs) / 1000, DateFormat.is24HourFormat(this), true));
        }
        return sb.toString();
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Store.notificationsAllowed(this)) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFY);
        }
    }

    private String hourLabel(int h) {
        return DateFormat.is24HourFormat(this) ? (h < 10 ? "0" : "") + h + ":00" : (h % 12 == 0 ? 12 : h % 12) + (h < 12 ? " AM" : " PM");
    }

    private void pickMorning() {
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override public void onTimeSet(TimePicker view, int hour, int minute) {
                Store.setMorningHour(MainActivity.this, hour);
                Jobs.sync(MainActivity.this);
                rebuild();
            }
        }, Store.morningHour(this), 0, DateFormat.is24HourFormat(this)).show();
    }

    // ---- saved places ---------------------------------------------------------------------------------------------

    private void addPlaces() {
        LinearLayout box = section("Saved places", 0);
        final Context app = getApplicationContext();
        for (final Places.Saved sp : Places.list(this)) {
            Weather w = Places.loadWeather(this, sp.id);
            String sub = sp.place.title();
            if (w != null) sub = Store.units(this).t(w.now(System.currentTimeMillis() / 1000L).temp) + " \u00b7 " + Wmo.label(w.now(System.currentTimeMillis() / 1000L).code) + " \u00b7 " + sub;
            Ui.Btn remove = new Ui.Btn(this, "Remove", false);
            remove.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Places.remove(app, sp.id);
                    WidgetUpdater.updateAll(app);
                    rebuild();
                }
            });
            Ui.Btn maps = new Ui.Btn(this, "Maps", false);
            maps.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Trio.open(MainActivity.this, "mapsandcats://place?lat=" + sp.place.lat + "&lon=" + sp.place.lon + "&name=" + Uri.encode(sp.name),
                            Trio.REPO + "/releases");
                }
            });
            LinearLayout both = new LinearLayout(this);
            both.setOrientation(LinearLayout.HORIZONTAL);
            both.addView(maps, new LinearLayout.LayoutParams(-2, -2));
            both.addView(remove, new LinearLayout.LayoutParams(-2, -2));
            row(box, sp.name, sub, both);
        }
        Ui.Btn search = new Ui.Btn(this, "Search", true);
        search.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { searchPlaceDialog(); }
        });
        row(box, "Add a place", "Find a city or town. Widgets can show any saved place", search);
        Cards.Data cur = Store.data(this);
        if (cur.place != null) {
            Ui.Btn here = new Ui.Btn(this, "Save", false);
            here.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { saveHereDialog(); }
            });
            row(box, "Save where I am", "Name this spot, like Home or Work", here);
        }
    }

    private void saveHereDialog() {
        final Place here = Store.loadPlace(this);
        if (here == null) return;
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setText("Home");
        input.setSelectAllOnFocus(true);
        new android.app.AlertDialog.Builder(this).setTitle("Name this place").setView(input)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) {
                        Place copy = Place.fromJson(here.toJson().toString());
                        Places.add(getApplicationContext(), input.getText().toString(), copy);
                        refreshSavedAsync();
                        rebuild();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void refreshSavedAsync() {
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                Updater.refreshSaved(app, true);
                WidgetUpdater.updateAll(app);
                runOnUiThread(new Runnable() { @Override public void run() { rebuild(); } });
            }
        }, "skypix-places").start();
    }

    private void searchPlaceDialog() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setHint("City or town");
        new android.app.AlertDialog.Builder(this).setTitle("Add a place").setView(input)
                .setPositiveButton("Search", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int which) { runPlaceSearch(input.getText().toString().trim()); }
                }).setNegativeButton("Cancel", null).show();
    }

    private void runPlaceSearch(final String query) {
        if (query.length() < 2) return;
        Toast.makeText(this, "Searching\u2026", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=en&format=json&name=" + java.net.URLEncoder.encode(query, "UTF-8");
                    org.json.JSONObject o = new org.json.JSONObject(Net.get(url));
                    final org.json.JSONArray r = o.optJSONArray("results");
                    if (r == null || r.length() == 0) {
                        runOnUiThread(new Runnable() { @Override public void run() { Toast.makeText(MainActivity.this, "No places found for that name", Toast.LENGTH_LONG).show(); } });
                        return;
                    }
                    final String[] labels = new String[r.length()];
                    for (int i = 0; i < r.length(); i++) {
                        org.json.JSONObject x = r.getJSONObject(i);
                        String admin = x.optString("admin1", "");
                        labels[i] = x.optString("name") + (admin.length() > 0 ? ", " + admin : "") + ", " + x.optString("country", "");
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            new android.app.AlertDialog.Builder(MainActivity.this).setTitle("Pick a place")
                                    .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                                        @Override public void onClick(android.content.DialogInterface d, int which) {
                                            try {
                                                org.json.JSONObject x = r.getJSONObject(which);
                                                Place p = new Place();
                                                p.lat = x.getDouble("latitude");
                                                p.lon = x.getDouble("longitude");
                                                p.city = x.optString("name");
                                                p.region = x.optString("admin1", "");
                                                p.country = x.optString("country", "");
                                                p.provider = "search";
                                                Places.add(getApplicationContext(), p.city, p);
                                                refreshSavedAsync();
                                                rebuild();
                                            } catch (Exception e) {
                                                Ui.report("add place", e);
                                            }
                                        }
                                    }).show();
                        }
                    });
                } catch (Throwable t) {
                    Ui.report("place search", t);
                    runOnUiThread(new Runnable() { @Override public void run() { Toast.makeText(MainActivity.this, "Couldn't search right now. Are you online?", Toast.LENGTH_LONG).show(); } });
                }
            }
        }, "skypix-geocode").start();
    }

    // ---- app updates ----------------------------------------------------------------------------------------------

    private TextView updateSub;

    /**
     * "Your widgets": every resizable widget on the home screen with a Settings button. This works with any launcher,
     * including ones that don't show "Widget settings" when you touch and hold a widget.
     */
    private void addPlacedWidgets() {
        android.appwidget.AppWidgetManager m = android.appwidget.AppWidgetManager.getInstance(this);
        LinearLayout box = null;
        for (int v = 0; v < Widgets.ALL.length; v++) {
            if (Variants.LEGACY[v]) continue;
            int[] ids;
            try {
                ids = m.getAppWidgetIds(new android.content.ComponentName(this, Widgets.ALL[v]));
            } catch (Throwable t) {
                continue;
            }
            for (final int id : ids) {
                if (box == null) box = section("Your widgets", 0);
                int bg = Places.effectiveBg(this, id);
                String sub = new String[]{"Solid", "Glass", "Clear"}[Math.max(0, Math.min(2, bg))];
                Ui.Btn b = new Ui.Btn(this, "Settings", false);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View x) {
                        startActivity(new Intent(MainActivity.this, PlaceConfigActivity.class)
                                .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id));
                    }
                });
                row(box, Variants.TITLE[v], sub + " background", b);
            }
        }
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable t) {
            Ui.report("open link", t);
        }
    }

    /** "About Purrcast": the maker, the version and where to find them. */
    private void addAbout() {
        content.addView(Ui.text(this, "About Purrcast", 15, 500, Ui.primary(this)), lp(-2, -2, 32));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 26), Ui.dp(this, 20), Ui.dp(this, 22));
        card.setBackground(Ui.surface(this, Ui.container(this), Ui.CARD));
        content.addView(card, lp(-1, -2, 12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.profile);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setClipToOutline(true);
        icon.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
        });
        int is = Ui.dp(this, 96);
        card.addView(icon, new LinearLayout.LayoutParams(is, is));

        TextView name = Ui.text(this, "Nikezvera", 24, 500, Ui.onSurface(this));
        name.setGravity(Gravity.CENTER);
        card.addView(name, lp(-2, -2, 16));
        TextView role = Ui.text(this, "Developer of Purrcast", 15, 400, Ui.onSurfaceVar(this));
        role.setGravity(Gravity.CENTER);
        card.addView(role, lp(-2, -2, 2));
        TextView ver = Ui.text(this, "Version " + AppUpdate.installedName(this), 13.5f, 500, Ui.onTonal(this));
        ver.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        ver.setBackground(Ui.surface(this, Ui.tonal(this), 20));
        card.addView(ver, lp(-2, -2, 14));

        LinearLayout box = section("Find me", 0);
        View.OnClickListener tg = new View.OnClickListener() {
            @Override public void onClick(View v) { open("https://t.me/xnzvix"); }
        };
        Ui.Btn t = new Ui.Btn(this, "Open", true);
        t.setOnClickListener(tg);
        row(box, "Telegram", "@xnzvix", t);
        Ui.Btn lg = new Ui.Btn(this, "Share", false);
        lg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Diag.share(MainActivity.this); }
        });
        row(box, "Log for bug reports", "Stays on this phone until you share it. No places or searches.", lg);
        Ui.Btn lc = new Ui.Btn(this, "Clear", false);
        lc.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Diag.clear(MainActivity.this); Toast.makeText(MainActivity.this, "Log cleared", Toast.LENGTH_SHORT).show(); }
        });
        row(box, "Clear the log", null, lc);
        Ui.Btn nt = new Ui.Btn(this, "View", false);
        nt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new android.app.AlertDialog.Builder(MainActivity.this).setTitle("Open-source notices")
                        .setMessage("Cat emoji: Noto Emoji, © Google, Apache-2.0 (via svgmoji, MIT).\nWeather and air quality: Open-Meteo.com, CC BY 4.0.\nPlace names: © OpenStreetMap contributors, ODbL.\n\nFull list: github.com/VK-VfX/nikezverapps, NOTICE.md")
                        .setPositiveButton("OK", null).show();
            }
        });
        row(box, "Open-source notices", "What Purrcast is built on", nt);
        Ui.Btn g = new Ui.Btn(this, "Open", false);
        g.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open("https://github.com/VK-VfX/nikezverapps/tree/main/purrcast"); }
        });
        row(box, "Source and releases", "GitHub", g);

        LinearLayout trio = section("The trio", 0);
        Trio.refresh(new Trio.Done() {
            @Override public void run() { ui.post(new Runnable() { @Override public void run() { rebuild(); } }); }
        });
        String zStat = Trio.stat(this, "com.nikopick.zamoled.catclub"), mStat = Trio.stat(this, "app.mapsandcats.catclub");
        StringBuilder club = new StringBuilder(Store.streak(this) + " " + Store.streakLabel(this));
        if (zStat != null) club.append(" \u00b7 ").append(zStat);
        if (mStat != null) club.append(" \u00b7 ").append(mStat);
        Ui.Btn how = new Ui.Btn(this, "How it works", false);
        how.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showCatClubHelp(); }
        });
        row(trio, "Cat Club", club.toString(), how);
        final int rd = Store.rewardReady(this);
        Ui.Btn claim = new Ui.Btn(this, rd > 0 ? "Claim" : "Info", rd > 0);
        claim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (Store.rewardReady(MainActivity.this) > 0) claimStreakReward(); else showCatClubHelp(); }
        });
        int nextDay = Store.rewardNext(this), toGo = nextDay - Store.bestStreak(this);
        row(trio, "Streak wallpaper", rd > 0 ? "Day " + rd + " wallpaper is ready in Zamoled"
                : "A special Zamoled wallpaper every " + Store.REWARD_EVERY + " days. Next at day " + nextDay + " (" + Math.max(0, toGo) + " to go)", claim);
        trioRow(trio, "Zamoled", "AMOLED wallpapers that make room for your widgets", Trio.ZAMOLED, "zamoled",
                Trio.REPO + "/releases");
        trioRow(trio, "Maps and Cats", "Maps, navigation and cats to catch", Trio.MAPS, "mapsandcats",
                Trio.REPO + "/releases");
        if (Trio.installed(this, Trio.ZAMOLED)) {
            Ui.Btn wp = new Ui.Btn(this, "Make", false);
            wp.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Trio.open(MainActivity.this, "zamoled://weather?mood=" + currentMood(), Trio.REPO + "/releases");
                }
            });
            row(trio, "Wallpaper for this weather", "Opens Zamoled with a " + currentMood() + " wallpaper, ready to save", wp);
        }
    }

    /** Opens the special wallpaper in Zamoled and marks it claimed; without Zamoled it opens its download page and keeps it waiting. */
    private void claimStreakReward() {
        int day = Store.rewardReady(this);
        if (day <= 0) return;
        if (!Trio.installed(this, Trio.ZAMOLED)) {
            android.widget.Toast.makeText(this, "Install Zamoled to claim it. It will keep waiting.", android.widget.Toast.LENGTH_LONG).show();
            open(Trio.REPO + "/releases");
            return;
        }
        Store.rewardClaimed(this);
        Trio.open(this, "zamoled://reward?day=" + day, Trio.REPO + "/releases");
        rebuild();
    }

    /** What the streak and the Cat Club really do, in plain words. */
    private void showCatClubHelp() {
        String msg = "STREAK\n"
                + "Opening Purrcast counts as your visit for the day. Visit on consecutive days and the streak grows. "
                + "Miss a whole day and it starts again at 1; your best streak is kept.\n\n"
                + "THE CAT ON YOUR STREAK CARD\n"
                + "1\u20132 days: a cloud cat \u00b7 3 days: a partly sunny cat \u00b7 7 days: a sunny cat \u00b7 14+ days: a cat in sunglasses. "
                + "You also get a cheer at 3, 7, 14, 30 and 100 days.\n\n"
                + "THE CAT CLUB\n"
                + "All three apps count their own day streak the same way, and each shows the other two when they are installed. "
                + "Nothing is uploaded; the apps only read each other's counts on your phone.\n\n"
                + "THE STREAK SHIELD\n"
                + "Install Purrcast, Zamoled and Maps and Cats together and every streak gets a shield. If you miss up to 7 days it saves your streak once, "
                + "then it recharges after 7 days in a row. It works only while all three apps are installed.\n\n"
                + "REWARDS\n"
                + "Every 10 days of streak (10, 20, 30 ...) earns a special wallpaper in Zamoled. It appears here when it is ready; "
                + "tap Claim and Zamoled opens it, ready to save. Your best streak counts, so a missed day never takes a reward away. "
                + "Besides that there are the cat's moods and cheers here, and levels in Maps and Cats.";
        new android.app.AlertDialog.Builder(this).setTitle("How the Cat Club works").setMessage(msg)
                .setPositiveButton("Got it", null).show();
    }

    /** One word for the weather right now, which Zamoled turns into a matching wallpaper. */
    private String currentMood() {
        try {
            Cards.Data d = Store.data(this);
            if (d.weather == null) return "clear";
            Weather.Now n = d.weather.now(System.currentTimeMillis() / 1000L);
            if (Wmo.isStorm(n.code)) return "storm";
            if (Wmo.isSnow(n.code)) return "snow";
            if (Wmo.isWet(n.code)) return "rain";
            if (n.code == 45 || n.code == 48) return "fog";
            if (n.code == 3) return "cloudy";
            return n.isDay ? "clear" : "night";
        } catch (Throwable t) {
            return "clear";
        }
    }

    /** One sibling app: its Cat Club-style version line, and Open / Update / Get. */
    private void trioRow(LinearLayout box, String name, String sub, final String pkg, String key, final String url) {
        boolean has = Trio.installed(this, pkg);
        String have = has ? Trio.installedVersion(this, pkg) : null;
        String latest = Trio.latest.get(key);
        boolean outdated = have != null && latest != null && Trio.newer(latest, have);
        String line = sub;
        if (have != null) line += "\nv" + have + (outdated ? " \u00b7 v" + latest + " is out, open it to update" : " \u00b7 up to date");
        Ui.Btn b = new Ui.Btn(this, !has ? "Get" : outdated ? "Update" : "Open", !has || outdated);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                if (li != null) {
                    try { startActivity(li); return; } catch (Throwable ignored) { }
                }
                open(url);
            }
        });
        row(box, name, line, b);
    }

    private void addUpdates() {
        LinearLayout box = section("App updates", 0);
        AppUpdate.Info av = AppUpdate.available;
        String sub;
        if (AppUpdate.status.length() > 0) sub = AppUpdate.status;
        else if (av != null) sub = "Version " + av.name + " is ready" + (av.notes.length() > 0 ? ": " + av.notes : "");
        else sub = "Tap Check to look for a new version";
        Ui.Btn action = new Ui.Btn(this, av != null ? "Install" : "Check", av != null);
        action.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (AppUpdate.busy) return;
                if (AppUpdate.available != null) installUpdate(); else checkUpdate();
            }
        });
        LinearLayout r = row(box, "Purrcast " + AppUpdate.installedName(this), sub, action);
        View col = r.getChildAt(0);
        updateSub = col instanceof LinearLayout && ((LinearLayout) col).getChildCount() > 1 ? (TextView) ((LinearLayout) col).getChildAt(1) : null;
        row(box, "Check automatically", "Once a day. Only contacts GitHub, nothing about you is sent",
                toggle(Store.autoUpdate(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean on) { Store.setAutoUpdate(MainActivity.this, on); }
                }));
    }

    private void setUpdateStatus(final String s) {
        AppUpdate.status = s;
        runOnUiThread(new Runnable() {
            @Override public void run() { if (updateSub != null) updateSub.setText(s); }
        });
    }

    private void checkUpdate() {
        final Context app = getApplicationContext();
        AppUpdate.busy = true;
        setUpdateStatus("Checking\u2026");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    AppUpdate.Info i = AppUpdate.check(app);
                    AppUpdate.status = i == null ? "You're on the latest version" : "";
                } catch (Throwable t) {
                    Diag.error(app, "update-check", t);
                    AppUpdate.status = "Couldn't check right now. Are you online?";
                }
                AppUpdate.busy = false;
                runOnUiThread(new Runnable() { @Override public void run() { rebuild(); } });
            }
        }, "skypix-update-check").start();
    }

    private void installUpdate() {
        final Context app = getApplicationContext();
        final AppUpdate.Info info = AppUpdate.available;
        if (info == null) return;
        if (!AppUpdate.ensureInstallPermission(this)) {
            setUpdateStatus("Allow Purrcast to install apps, come back, then tap Install again");
            return;
        }
        AppUpdate.busy = true;
        setUpdateStatus("Downloading 0%");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    java.io.File apk = AppUpdate.download(app, info, new AppUpdate.Progress() {
                        @Override public void onProgress(String text) { setUpdateStatus(text); }
                    });
                    setUpdateStatus("Verified. Handing over to Android\u2026");
                    AppUpdate.install(app, apk);
                } catch (Throwable t) {
                    Diag.error(app, "update-install", t);
                    AppUpdate.busy = false;
                    setUpdateStatus("Update failed: " + t.getMessage());
                }
            }
        }, "skypix-update-install").start();
    }

    private void pickHour(final boolean start) {
        int h = start ? Store.quietStart(this) : Store.quietEnd(this);
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override public void onTimeSet(TimePicker view, int hour, int minute) {
                if (start) Store.setQuietHours(MainActivity.this, hour, Store.quietEnd(MainActivity.this));
                else Store.setQuietHours(MainActivity.this, Store.quietStart(MainActivity.this), hour);
                rebuild();
            }
        }, h, 0, DateFormat.is24HourFormat(this)).show();
    }

    // ---- widget picker --------------------------------------------------------------------------------------------

    private void addWidgetPicker(int ink, int ink2) {
        content.addView(Ui.text(this, "Add widgets", 15, 500, Ui.primary(this)), lp(-2, -2, 32));
        final AppWidgetManager awm = AppWidgetManager.getInstance(this);
        boolean canPin = Build.VERSION.SDK_INT >= 26 && awm.isRequestPinAppWidgetSupported();
        for (int v = 0; v < Variants.COUNT; v++) {
            if (Variants.LEGACY[v]) continue; // sized widgets from older versions stay on home screens but aren't offered any more
            final int variant = v;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 14));
            card.setBackground(Ui.surface(this, Ui.container(this), Ui.CARD));

            int res = getResources().getIdentifier("preview_" + Variants.KEY[v], "drawable", getPackageName());
            if (res != 0) {
                android.widget.ImageView img = new android.widget.ImageView(this);
                img.setImageResource(res);
                img.setAdjustViewBounds(true);
                img.setMaxHeight(Ui.dp(this, 150));
                img.setScaleType(android.widget.ImageView.ScaleType.FIT_START);
                img.setContentDescription(Variants.TITLE[v] + " widget preview");
                card.addView(img, lp(-2, -2, 0));
            }
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.addView(Ui.text(this, Variants.TITLE[v] + "  \u00b7  resizable", 16.5f, 500, Ui.onSurface(this)));
            TextView sub = Ui.text(this, Variants.BLURB[v], 13.5f, 400, Ui.onSurfaceVar(this));
            sub.setLineSpacing(0, 1.12f);
            text.addView(sub, lp(-1, -2, 2));
            line.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
            if (canPin) {
                Ui.Btn add = new Ui.Btn(this, "Add", true);
                add.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        awm.requestPinAppWidget(new ComponentName(MainActivity.this, Widgets.ALL[variant]), null, null);
                    }
                });
                line.addView(add, new LinearLayout.LayoutParams(-2, -2));
            }
            card.addView(line, lp(-1, -2, res != 0 ? 12 : 0));
            content.addView(card, lp(-1, -2, 12));
        }
        if (!canPin) {
            content.addView(Ui.text(this, "Long-press your home screen, tap Widgets, and look for Purrcast.", 13.5f, 400, Ui.onSurfaceVar(this)), lp(-1, -2, 12));
        }
    }
}
