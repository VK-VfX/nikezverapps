package app.skypix.tools;

import app.skypix.core.Cards;
import app.skypix.core.Fmt;
import app.skypix.core.Glyphs;
import app.skypix.core.Place;
import app.skypix.core.PlaceNames;
import app.skypix.core.Units;
import app.skypix.core.Weather;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.ImageIO;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Florence, Colorado, live: the place comes from a real Nominatim answer and the weather from live National Weather Service data
 * (the forecast hours and the latest observation), fed through Purrcast's own Weather model and widget renderer.
 * Usage: LiveFlorence fixturesDir outDir
 */
public final class LiveFlorence {
    static String read(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); }

    static int code(String text) {
        String s = text.toLowerCase();
        if (s.contains("thunder")) return 95;
        if (s.contains("snow") || s.contains("flurr")) return 71;
        if (s.contains("rain") || s.contains("shower") || s.contains("drizzle")) return s.contains("shower") ? 80 : 61;
        if (s.contains("fog")) return 45;
        if (s.contains("mostly cloudy") || s.equals("cloudy") || s.contains("overcast")) return 3;
        if (s.contains("partly")) return 2;
        if (s.contains("mostly sunny") || s.contains("mostly clear")) return 1;
        return 0;
    }

    static double mph(String s) {
        double best = 0;
        for (String part : s.replace("mph", "").split("to")) { try { best = Math.max(best, Double.parseDouble(part.trim())); } catch (Exception ignored) { } }
        return best * 1.609;
    }

    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);
        // ---- the place, from a live Nominatim answer ----
        JSONObject addr = new JSONObject(read(new File(dir, "nominatim-florence-co.json"))).getJSONObject("address");
        Map<String, String> fields = new HashMap<String, String>();
        for (Iterator<String> it = addr.keys(); it.hasNext();) { String k = it.next(); fields.put(k, addr.optString(k, "")); }
        PlaceNames.Names n = PlaceNames.resolve(fields);
        Place p = new Place();
        p.lat = 38.3920; p.lon = -105.1186; p.precise = true; p.provider = "gps";
        p.city = n.city; p.neighborhood = n.neighborhood; p.region = addr.optString("state"); p.country = addr.optString("country");

        // ---- the weather, from live NWS data ----
        JSONArray per = new JSONObject(read(new File(dir, "nws-florence-hourly.json"))).getJSONObject("properties").getJSONArray("periods");
        int off = -7 * 3600;
        int cnt = per.length();
        Weather w = new Weather();
        w.utcOffset = off; w.lat = p.lat; w.lon = p.lon; w.elevation = 1600;
        w.hTime = new long[cnt]; w.hTemp = new double[cnt]; w.hFeels = new double[cnt]; w.hHum = new double[cnt]; w.hPop = new double[cnt];
        w.hPrecip = new double[cnt]; w.hWind = new double[cnt]; w.hUv = new double[cnt]; w.hVis = new double[cnt]; w.hCode = new int[cnt]; w.hDay = new boolean[cnt];
        for (int i = 0; i < cnt; i++) {
            JSONObject o = per.getJSONObject(i);
            w.hTime[i] = OffsetDateTime.parse(o.getString("startTime")).toEpochSecond();
            double c = (o.getDouble("temperature") - 32) * 5 / 9;
            w.hTemp[i] = c; w.hFeels[i] = c;
            JSONObject rh = o.optJSONObject("relativeHumidity");
            w.hHum[i] = rh == null || rh.isNull("value") ? 40 : rh.getDouble("value");
            JSONObject pp = o.optJSONObject("probabilityOfPrecipitation");
            w.hPop[i] = pp == null || pp.isNull("value") ? 0 : pp.getDouble("value");
            w.hCode[i] = code(o.getString("shortForecast"));
            w.hPrecip[i] = w.hPop[i] >= 50 ? 0.5 : 0;
            w.hWind[i] = mph(o.optString("windSpeed", "0 mph"));
            w.hDay[i] = o.optBoolean("isDaytime", true);
            w.hVis[i] = 24000; w.hUv[i] = w.hDay[i] ? 3 : 0;
        }
        JSONObject ob = new JSONObject(read(new File(dir, "nws-florence-observation.json"))).getJSONObject("properties");
        long now = OffsetDateTime.parse(ob.getString("timestamp")).toEpochSecond();
        int hi = w.hourIndex(now);
        w.curTime = now;
        double tc = ob.getJSONObject("temperature").getDouble("value");
        w.temp = tc; w.feels = tc; w.humidity = ob.getJSONObject("relativeHumidity").optDouble("value", 40);
        w.windKmh = w.hWind[hi]; w.windDir = 270; w.gustKmh = w.hWind[hi] * 1.4; w.pressure = 1015; w.cloud = 10;
        w.code = code(ob.optString("textDescription", "Clear")); w.isDay = w.hDay[hi]; w.precipNow = 0; w.aqi = 0;
        // days: the high and low of each local day, sunrise and sunset from where the day starts and ends
        int days = Math.min(7, cnt / 24);
        w.dTime = new long[days]; w.dSunrise = new long[days]; w.dSunset = new long[days]; w.dMax = new double[days]; w.dMin = new double[days];
        w.dPop = new double[days]; w.dUv = new double[days]; w.dPrecip = new double[days]; w.dCode = new int[days];
        long day0 = ((w.hTime[0] + off) / 86400) * 86400 - off;
        for (int d = 0; d < days; d++) {
            w.dTime[d] = day0 + d * 86400L;
            double mx = -99, mn = 99; long sr = 0, ss = 0; double pop = 0; int cd = 0;
            for (int i = 0; i < cnt; i++) {
                if (w.hTime[i] < w.dTime[d] || w.hTime[i] >= w.dTime[d] + 86400) continue;
                mx = Math.max(mx, w.hTemp[i]); mn = Math.min(mn, w.hTemp[i]); pop = Math.max(pop, w.hPop[i]);
                if (w.hDay[i] && sr == 0) sr = w.hTime[i];
                if (w.hDay[i]) ss = w.hTime[i] + 3600;
                if (w.hDay[i] && cd == 0) cd = w.hCode[i];
            }
            w.dMax[d] = mx; w.dMin[d] = mn; w.dPop[d] = pop; w.dCode[d] = cd; w.dSunrise[d] = sr; w.dSunset[d] = ss; w.dUv[d] = 4;
        }
        w.mTime = new long[0]; w.mPrecip = new double[0];

        Cards.Data d = new Cards.Data();
        d.weather = w; d.place = p; d.units = new Units(true, true, false); d.nowSec = now; d.tzOffset = off;

        System.out.println("PLACE   " + p.title() + "   (short name: " + p.shortName() + ")");
        System.out.println("NOW     " + Math.round(w.temp * 9 / 5 + 32) + " F, " + w.humidity + "% humidity, wind " + Math.round(w.windKmh / 1.609) + " mph, code " + w.code);
        for (int i = 0; i < Math.min(3, days); i++) System.out.println("DAY " + i + "   high " + Math.round(w.dMax[i] * 9 / 5 + 32) + " F, low " + Math.round(w.dMin[i] * 9 / 5 + 32) + " F, rain chance " + Math.round(w.dPop[i]) + "%");

        // ---- Purrcast's own widgets, with the hero cat on ----
        Glyphs.hero = true;
        Glyphs.heroVariant = 1;
        int[] kinds = {Cards.NOW, Cards.CLOCK_BIG, Cards.HEADSUP, Cards.NOW_WIDE, Cards.HOURLY, Cards.FORECAST};
        int[][] sz = {{110, 110}, {160, 150}, {180, 110}, {250, 110}, {250, 110}, {180, 150}};
        float u = 2f;
        BufferedImage img = new BufferedImage(Math.round(1000 * u), Math.round(330 * u), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        int x = 10, y = 8, rowH = 0;
        for (int c = 0; c < kinds.length; c++) {
            if (x + sz[c][0] > 990) { x = 10; y += rowH + 12; rowH = 0; }
            g.drawImage(Preview.render(kinds[c], sz[c][0], sz[c][1], u, true, d), Math.round(x * u), Math.round(y * u), null);
            x += sz[c][0] + 12; rowH = Math.max(rowH, sz[c][1]);
        }
        g.dispose();
        new File(a[1]).mkdirs();
        ImageIO.write(img, "png", new File(a[1], "florence.png"));
    }
}
