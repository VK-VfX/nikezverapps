package app.skypix.android;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import app.skypix.core.Cards;
import app.skypix.core.Gfx;
import app.skypix.core.Insights;

/** Small custom views so the app screen shares the widgets' pixel look. */
final class Ui {
    private Ui() {}

    /** Last non-fatal error, shown in the app footer so it can be reported instead of crashing. */
    static volatile String lastError;

    static void report(String where, Throwable t) {
        lastError = where + ": " + t;
        Diag.log("error", where + ": " + t);
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < Math.min(4, st.length); i++) lastError += "\n  at " + st[i];
    }

    /** Uptime (ms) when the in-app 5 s weather animation started; 0 = never. */
    static volatile long animStartMs;

    static float animT() {
        if (animStartMs == 0) return -1;
        float t = (android.os.SystemClock.uptimeMillis() - animStartMs) / 1000f;
        return t < 5f ? t : -1;
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static boolean isDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** One widget design drawn at full width. */
    static final class CardView extends View {
        private final int kind;
        private final float heightDp;
        private final Insights.Insight insight;
        private Cards.Data data;

        CardView(Context c, int kind, float heightDp, Insights.Insight insight, Cards.Data data) {
            super(c);
            this.kind = kind;
            this.heightDp = heightDp;
            this.insight = insight;
            this.data = data;
            setContentDescription(insight != null ? insight.title + ". " + insight.detail : Cards.describe(kind, data));
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension(MeasureSpec.getSize(wSpec), dp(getContext(), heightDp));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float u = getResources().getDisplayMetrics().density;
            AndroidGfx g = new AndroidGfx(canvas);
            float w = getWidth() / u, h = getHeight() / u;
            float t = animT();
            try {
                if (data != null) data.anim = t;
                if (insight != null) Cards.renderInsight(g, insight, w, h, u, isDark(getContext()), data);
                else Cards.render(g, kind, w, h, u, isDark(getContext()), data);
            } catch (Throwable e) {
                report("draw card " + kind, e);
                return;
            }
            if (t >= 0) postInvalidateDelayed(33);
        }
    }

    // ---- Material 3 palette (Pixel look; dark is AMOLED) ------------------------------------------------------------

    /** Material You: take the palette from the wallpaper (Android 12+, opt-in in Settings). */
    static boolean you(Context c) {
        return android.os.Build.VERSION.SDK_INT >= 31 && Store.materialYou(c);
    }

    private static int sys(Context c, int id, int fallback) {
        try {
            return c.getColor(id);
        } catch (Throwable t) {
            return fallback;
        }
    }

    static int bg(Context c) {
        if (you(c)) return isDark(c) ? 0xFF000000 : sys(c, android.R.color.system_neutral1_50, 0xFFF0F4F9);
        return isDark(c) ? 0xFF000000 : 0xFFF0F4F9;
    }
    static int container(Context c) {
        if (you(c)) return isDark(c) ? mixColor(sys(c, android.R.color.system_neutral1_900, 0xFF1B1B1F), 0xFF000000, 0.35f) : sys(c, android.R.color.system_neutral1_0, 0xFFFFFFFF);
        return isDark(c) ? 0xFF0D0D10 : 0xFFFFFFFF;
    }
    static int onSurface(Context c) {
        if (you(c)) return sys(c, isDark(c) ? android.R.color.system_neutral1_100 : android.R.color.system_neutral1_900, 0xFF1F1F1F);
        return isDark(c) ? 0xFFE3E3E3 : 0xFF1F1F1F;
    }
    static int onSurfaceVar(Context c) {
        if (you(c)) return sys(c, isDark(c) ? android.R.color.system_neutral2_200 : android.R.color.system_neutral2_700, 0xFF444746);
        return isDark(c) ? 0xFFC4C7C5 : 0xFF444746;
    }
    /** Card corner radius in dp (trio refresh: 28). */
    static final int CARD = 28;

    /** The trio's soft violet: streaks, the shield and the hero cat. Teal stays for actions. */
    static int violet(Context c) {
        return isDark(c) ? 0xFFB388FF : 0xFF5E35B1;
    }

    static int primary(Context c) {
        if (you(c)) return sys(c, isDark(c) ? android.R.color.system_accent1_200 : android.R.color.system_accent1_600, 0xFF0B57D0);
        return isDark(c) ? 0xFF3DF5D0 : 0xFF00574D;
    }
    static int onPrimary(Context c) {
        if (you(c)) return sys(c, isDark(c) ? android.R.color.system_accent1_800 : android.R.color.system_accent1_0, 0xFFFFFFFF);
        return isDark(c) ? 0xFF00110D : 0xFFFFFFFF;
    }
    static int primaryContainer(Context c) {
        if (you(c)) return sys(c, isDark(c) ? android.R.color.system_accent1_900 : android.R.color.system_accent1_100, 0xFFD3E3FD);
        return isDark(c) ? 0xFF0A4A3F : 0xFFB2F0E3;
    }
    static int onPrimaryContainer(Context c) {
        if (you(c)) return sys(c, isDark(c) ? android.R.color.system_accent1_100 : android.R.color.system_accent1_900, 0xFF041E49);
        return isDark(c) ? 0xFFBFFFF0 : 0xFF00201B;
    }
    static int tonal(Context c) {
        if (you(c)) return sys(c, isDark(c) ? android.R.color.system_accent2_800 : android.R.color.system_accent2_100, 0xFFD3E3FD);
        return isDark(c) ? 0xFF16312C : 0xFFB2F0E3;
    }
    static int onTonal(Context c) {
        if (you(c)) return sys(c, isDark(c) ? android.R.color.system_accent2_100 : android.R.color.system_accent2_900, 0xFF041E49);
        return isDark(c) ? 0xFFBFFFF0 : 0xFF00201B;
    }
    static int divider(Context c) {
        if (you(c)) return sys(c, isDark(c) ? android.R.color.system_neutral1_800 : android.R.color.system_neutral2_100, 0xFFE3E7EC);
        return isDark(c) ? 0xFF2A2B2E : 0xFFE3E7EC;
    }
    static int outline(Context c) {
        if (you(c)) return sys(c, android.R.color.system_neutral2_500, 0xFF747775);
        return isDark(c) ? 0xFF8E918F : 0xFF747775;
    }

    /** A TextView in Google Sans (on Pixel) at the given weight. */
    static android.widget.TextView text(Context c, String s, float sp, int weight, int color) {
        android.widget.TextView t = new android.widget.TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        // the family type: condensed bold for headings, the system face for reading
        t.setTypeface(weight >= 500 && sp >= 15 ? android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD) : AndroidGfx.typeface(weight));
        if (weight >= 500 && sp >= 20) t.setLetterSpacing(0.01f);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Rounded surface used for cards and groups. */
    static android.graphics.drawable.GradientDrawable surface(Context c, int color, float radiusDp) {
        // trio signature: cards (24dp and up) get a thin teal rim along the top edge
        android.graphics.drawable.GradientDrawable d = radiusDp >= 24 ? new RimSurface(c) : new android.graphics.drawable.GradientDrawable();
        d.setCornerRadius(dp(c, radiusDp));
        d.setColor(color);
        return d;
    }

    /** A rounded surface with the trio's teal rim: 2dp, fading out at both ends, along the top edge. */
    static final class RimSurface extends android.graphics.drawable.GradientDrawable {
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float u;
        private final int accent;

        RimSurface(Context c) {
            u = c.getResources().getDisplayMetrics().density;
            accent = primary(c);
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            android.graphics.Rect b = getBounds();
            float inset = 28 * u;
            if (b.width() <= inset * 2) return;
            float l = b.left + inset, r = b.right - inset;
            int solid = (accent & 0x00FFFFFF) | 0xCC000000, clear = accent & 0x00FFFFFF;
            rim.setShader(new android.graphics.LinearGradient(l, 0, r, 0, new int[] {clear, solid, clear}, null, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRoundRect(l, b.top, r, b.top + 2 * u, u, u, rim);
        }
    }

    /** The rim for custom-drawn cards. */
    static void drawRim(Canvas canvas, float w, float u, int accent) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float l = (CARD + 4) * u, r = w - (CARD + 4) * u;
        if (r <= l) return;
        int solid = (accent & 0x00FFFFFF) | 0xCC000000, clear = accent & 0x00FFFFFF;
        p.setShader(new android.graphics.LinearGradient(l, 0, r, 0, new int[] {clear, solid, clear}, null, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRoundRect(l, 0, r, 2 * u, u, u, p);
    }

    /** Quick press feedback: shrink to 94% in 90 ms, spring back in 120 ms. Transform only, so it never re-lays-out. */
    static void press(View v, boolean pressed) {
        v.animate().cancel();
        v.animate().scaleX(pressed ? 0.94f : 1f).scaleY(pressed ? 0.94f : 1f).setDuration(pressed ? 90 : 120)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    /** First show: the cards fade up 6dp, one after another (20 ms apart, first eight). Does nothing if animations are off. */
    static void enter(android.view.ViewGroup col) {
        float shift = dp(col.getContext(), 6);
        int n = Math.min(col.getChildCount(), 8);
        for (int i = 0; i < n; i++) {
            View v = col.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(shift);
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 20L).setDuration(200)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }

    /** Material pill button: filled (primary) or tonal. */
    static final class Btn extends View {
        private String label;
        private boolean filled;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Btn(Context c, String label, boolean filled) {
            super(c);
            this.label = label;
            this.filled = filled;
            setClickable(true);
            setFocusable(true);
            setContentDescription(label);
        }

        void setLabel(String l) {
            label = l;
            setContentDescription(l);
            requestLayout();
            invalidate();
        }

        void setFilled(boolean f) {
            filled = f;
            invalidate();
        }

        private float textPx() {
            return 14f * getResources().getDisplayMetrics().scaledDensity;
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = Math.round(new AndroidGfx(new Canvas()).textWidth(label, textPx(), Gfx.FONT_MEDIUM)) + dp(getContext(), 40);
            int mode = MeasureSpec.getMode(wSpec);
            int size = MeasureSpec.getSize(wSpec);
            int mw = mode == MeasureSpec.EXACTLY ? size : (mode == MeasureSpec.AT_MOST ? Math.min(w, size) : w);
            setMeasuredDimension(Math.max(mw, dp(getContext(), 64)), dp(getContext(), 48));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Context c = getContext();
            AndroidGfx g = new AndroidGfx(canvas);
            float u = getResources().getDisplayMetrics().density;
            float top = (getHeight() - 40 * u) / 2f, bottom = top + 40 * u;
            int fill = filled ? primary(c) : tonal(c);
            int ink = filled ? onPrimary(c) : onTonal(c);
            if (isPressed()) fill = mixColor(fill, ink, 0.14f);
            g.fillRoundRect(0, top, getWidth(), bottom, 20 * u, fill);
            float tp = textPx();
            g.text(label, getWidth() / 2f, top + 20 * u + tp * 0.36f, tp, Gfx.FONT_MEDIUM, Gfx.CENTER, ink);
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            press(this, pressed);
            invalidate();
        }
    }

    static int mixColor(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f);
        int bl = Math.round((a & 255) * (1 - f) + (b & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Round tonal refresh button; the arrow spins while {@link #spinning}. */
    static final class RefreshButton extends View {
        boolean spinning;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rf = new android.graphics.RectF();

        RefreshButton(Context c) {
            super(c);
            setClickable(true);
            setFocusable(true);
            setContentDescription("Refresh");
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(dp(getContext(), 48), dp(getContext(), 48));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Context c = getContext();
            float u = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(isPressed() ? mixColor(tonal(c), onTonal(c), 0.14f) : tonal(c));
            canvas.drawCircle(cx, cy, 20 * u, p);
            float rot = spinning ? (android.os.SystemClock.uptimeMillis() % 900) / 900f * 360f : 0;
            canvas.save();
            canvas.rotate(rot, cx, cy);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.2f * u);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(onTonal(c));
            float r = 7.5f * u;
            rf.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawArc(rf, -40, 290, false, p);
            // arrow head at the arc's open end
            double a = Math.toRadians(-40);
            float ax = cx + (float) Math.cos(a) * r, ay = cy + (float) Math.sin(a) * r;
            p.setStyle(Paint.Style.FILL);
            android.graphics.Path head = new android.graphics.Path();
            head.moveTo(ax + 4.2f * u, ay - 4.6f * u);
            head.lineTo(ax + 4.2f * u, ay + 3.2f * u);
            head.lineTo(ax - 3.6f * u, ay + 0.6f * u);
            head.close();
            canvas.drawPath(head, p);
            canvas.restore();
            if (spinning) postInvalidateOnAnimation();
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            press(this, pressed);
            invalidate();
        }
    }

    /** Material 3 switch (52 x 32). */
    static final class Toggle extends android.widget.CompoundButton {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Toggle(Context c) {
            super(c);
            setButtonDrawable(null);
            setBackground(null);
            // A bare CompoundButton has no style, so it is not clickable and ignores touches; a CheckBox gets this from its style.
            setClickable(true);
            setFocusable(true);
        }

        /** There is no drawable to react to the checked state, so redraw ourselves or the switch looks stuck. */
        @Override
        public void setChecked(boolean checked) {
            super.setChecked(checked);
            invalidate();
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            press(this, pressed);
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(dp(getContext(), 52), dp(getContext(), 48));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Context c = getContext();
            float u = getResources().getDisplayMetrics().density;
            float top = (getHeight() - 32 * u) / 2f;
            boolean on = isChecked();
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? primary(c) : container(c) == 0xFFFFFFFF ? 0xFFE1E3E1 : 0xFF2B2D30);
            canvas.drawRoundRect(0, top, getWidth(), top + 32 * u, 16 * u, 16 * u, p);
            if (!on) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2 * u);
                p.setColor(outline(c));
                canvas.drawRoundRect(u, top + u, getWidth() - u, top + 31 * u, 15 * u, 15 * u, p);
                p.setStyle(Paint.Style.FILL);
            }
            float tr = (on ? 12 : 8) * u;
            float tx = on ? getWidth() - 16 * u : 16 * u;
            p.setColor(on ? onPrimary(c) : outline(c));
            canvas.drawCircle(tx, top + 16 * u, tr, p);
        }
    }

    /** The streak counter: a cat that gets cooler as the streak grows, the day count, and the last 7 days. */
    static final class StreakCard extends View {
        private final int streak, best;
        private final long mask;
        private final String shield;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Bitmap hero;
        /** Uptime when the power-up animation began; 0 = not playing. It plays once for each 10-day step, and not with animations off. */
        private long burstStart;

        StreakCard(Context c, int streak, int best, long mask, String shield) {
            super(c);
            // all three apps installed: the hero cat, in the mood of the weather (see core/HeroMood)
            app.skypix.core.HeroCat.Phase ph = Store.heroPhase(c);
            this.hero = !Store.heroActive(c) ? null
                    : ph == app.skypix.core.HeroCat.Phase.HERO
                    ? AndroidGfx.emojiBitmap("hero_" + app.skypix.core.HeroCat.emotion(Store.mood(c), streak) + "_" + new java.util.Random().nextInt(app.skypix.core.Glyphs.HERO_LOOKS))
                    : AndroidGfx.emojiBitmap(ph == app.skypix.core.HeroCat.Phase.SAD ? "cry" : "smirk");   // plain Noto cat: sad, then confident from day 3
            if (hero != null && ph == app.skypix.core.HeroCat.Phase.HERO && Store.animate(c) && Store.burstStep(c) != streak / 10) {
                Store.setBurstStep(c, streak / 10);
                burstStart = android.os.SystemClock.uptimeMillis();
            }
            this.streak = streak;
            this.best = best;
            this.mask = mask;
            this.shield = shield == null ? "" : shield;
            setContentDescription(streak + " day streak. Best " + best + " days. " + (10 - streak % 10) + " days to the next reward.");
        }

        static String cheer(int s) {
            if (s >= 100) return "Legendary cat!";
            if (s >= 30) return "A whole month of purring!";
            if (s >= 14) return "Two weeks strong!";
            if (s >= 7) return "A whole week, pawsome!";
            if (s >= 3) return "Purr-fect, keep going!";
            return s <= 1 ? "Come back tomorrow to start a streak" : "Nice start!";
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(MeasureSpec.getSize(w), dp(getContext(), shield.isEmpty() ? 92 : 112));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Context c = getContext();
            float u = getResources().getDisplayMetrics().density;
            AndroidGfx g = new AndroidGfx(canvas);
            float w = getWidth(), h = getHeight();
            g.fillRoundRect(0, 0, w, h, CARD * u, container(c));
            drawRim(canvas, w, u, primary(c));
            app.skypix.core.Icon cat = streak >= 14 ? app.skypix.core.Icon.SHADES : streak >= 7 ? app.skypix.core.Icon.SUN
                    : streak >= 3 ? app.skypix.core.Icon.PARTLY_DAY : app.skypix.core.Icon.CLOUD;
            if (hero != null) {
                p.setFilterBitmap(true);
                float k = burstStart == 0 ? 1f : Math.min(1f, (android.os.SystemClock.uptimeMillis() - burstStart) / 1300f);
                float hx = 44 * u, hy = h / 2f;
                if (k < 1f) {
                    // power-up: rings of light spread from the cat while it springs in
                    float e = 1f - (1f - k) * (1f - k) * (1f - k);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(4 * u * (1f - k) + u);
                    p.setColor(violet(c)); p.setAlpha((int) (255 * (1f - k)));
                    canvas.drawCircle(hx, hy, 28 * u + e * 150 * u, p);
                    p.setColor(primary(c)); p.setAlpha((int) (230 * (1f - k)));
                    canvas.drawCircle(hx, hy, 28 * u + e * 80 * u, p);
                    p.setStyle(Paint.Style.FILL); p.setAlpha(255);
                }
                float sc = burstStart == 0 ? 1f : k < 0.75f ? 0.2f + 0.95f * (k / 0.75f) * (k / 0.75f) : 1.15f - 0.15f * ((k - 0.75f) / 0.25f);
                canvas.save();
                canvas.scale(sc, sc, hx, hy);
                canvas.drawBitmap(hero, null, new android.graphics.RectF(16 * u, (h - 56 * u) / 2f, 72 * u, (h + 56 * u) / 2f), p);
                canvas.restore();
                if (k < 1f) postInvalidateOnAnimation();
            } else {
                app.skypix.core.Glyphs.draw(g, cat, 16 * u, (h - 56 * u) / 2f, 56 * u, -1);
            }
            // violet ring toward the next 10-day reward
            float rcx = w - 16 * u - 26 * u, rcy = h / 2f, rr = 21 * u;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5 * u);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(divider(c));
            canvas.drawCircle(rcx, rcy, rr, p);
            float frac = streak > 0 && streak % 10 == 0 ? 1f : (streak % 10) / 10f;
            if (frac > 0) {
                p.setColor(violet(c));
                canvas.drawArc(rcx - rr, rcy - rr, rcx + rr, rcy + rr, -90, 360 * frac, false, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setStrokeCap(Paint.Cap.BUTT);
            g.text(String.valueOf(streak), rcx, rcy + 6 * u, 16 * u, Gfx.FONT_MEDIUM, Gfx.CENTER, onSurface(c));
            g.text(streak + (streak == 1 ? " day streak" : " day streak"), 86 * u, 38 * u, 20 * u, Gfx.FONT_MEDIUM, Gfx.LEFT, violet(c));
            g.text(cheer(streak) + (best > streak ? "  \u00b7  best " + best : ""), 86 * u, 60 * u, 13 * u, Gfx.FONT_REGULAR, Gfx.LEFT, onSurfaceVar(c));
            if (!shield.isEmpty()) g.text(shield, 86 * u, 100 * u, 12.5f * u, Gfx.FONT_MEDIUM, Gfx.LEFT, violet(c));
            // last 7 days, oldest to today
            float dot = 7 * u, gap = 7 * u;
            float x0 = 86 * u, y = 76 * u;
            for (int i = 6; i >= 0; i--) {
                boolean on = ((mask >>> i) & 1L) != 0;
                float cx = x0 + (6 - i) * (dot * 2 + gap) + dot;
                g.fillCircle(cx, y, dot * (on ? 0.85f : 0.55f), on ? violet(c) : divider(c));
            }
        }
    }

    /** Easter egg: a few seconds of Noto cats raining down the screen. */
    static final class CatRain extends View {
        private static final String[] NAMES = {"plain", "grin", "smile", "joy", "love", "smirk", "kiss", "weary", "cry", "pout", "walk", "black"};
        private final long start = android.os.SystemClock.uptimeMillis();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.RectF rf = new android.graphics.RectF();

        CatRain(Context c) {
            super(c);
            setClickable(false);
        }

        private static float rnd(int i, int salt) {
            double v = Math.sin(i * 12.9898 + salt * 78.233) * 43758.5453;
            return (float) (v - Math.floor(v));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float u = getResources().getDisplayMetrics().density;
            float t = (android.os.SystemClock.uptimeMillis() - start) / 1000f;
            if (t > 4.5f) {
                post(new Runnable() {
                    @Override public void run() {
                        if (getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) getParent()).removeView(CatRain.this);
                    }
                });
                return;
            }
            float h = getHeight(), w = getWidth();
            for (int i = 0; i < 30; i++) {
                android.graphics.Bitmap b = AndroidGfx.emojiBitmap(NAMES[(int) (rnd(i, 1) * NAMES.length) % NAMES.length]);
                if (b == null) continue;
                float size = (26 + rnd(i, 2) * 30) * u;
                float x = rnd(i, 3) * (w - size);
                float speed = (230 + rnd(i, 4) * 260) * u;
                float y = -size + t * speed - rnd(i, 5) * h * 0.5f;
                if (y > h || y < -size) continue;
                canvas.save();
                canvas.rotate((rnd(i, 6) - 0.5f) * 50f + t * (rnd(i, 7) - 0.5f) * 90f, x + size / 2, y + size / 2);
                rf.set(x, y, x + size, y + size);
                canvas.drawBitmap(b, null, rf, p);
                canvas.restore();
            }
            postInvalidateOnAnimation();
        }
    }

    /** Flat page background: Google surface in light, pure black in dark. */
    /** A soft wash of the current sky colour at the top of the screen, fading to nothing (the black stays black below). */
    static final class SkyTint extends View {
        private int sky;
        private boolean dark = true;
        private final Paint p = new Paint();

        SkyTint(Context c) {
            super(c);
            setWillNotDraw(false);
        }

        void set(int skyColor, boolean isDark) {
            sky = skyColor;
            dark = isDark;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (sky == 0) return;
            int top = ((dark ? 0x4D : 0x26) << 24) | (sky & 0xFFFFFF);
            p.setShader(new android.graphics.LinearGradient(0, 0, 0, getHeight(), top, sky & 0xFFFFFF, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), p);
        }
    }

    static final class Backdrop extends View {
        Backdrop(Context c) {
            super(c);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(bg(getContext()));
        }
    }
}
