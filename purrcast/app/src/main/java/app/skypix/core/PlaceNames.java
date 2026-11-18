package app.skypix.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Holistic place names. OpenStreetMap often answers with the municipal body that owns a spot ("Majlis Perbandaran Petaling Jaya
 * Selatan", "Subang Jaya City Council") instead of the place people call it. A body is never shown as the place: the name is the
 * real town or village if the address has one, otherwise the place the body is named after, and when a real town or suburb is
 * close by, the closest one wins (standing in Subang Jaya means Subang Jaya, whichever council the map says owns the spot).
 */
public final class PlaceNames {
    private PlaceNames() {}

    private static final Pattern PREFIX = Pattern.compile(
            "^(majlis\\s+(perbandaran|bandaraya|daerah|bandaran|tempatan)|dewan\\s+bandaraya|pejabat\\s+daerah|pihak\\s+berkuasa\\s+tempatan"
                    + "|(municipality|municipal\\s+council|city\\s+council|district\\s+council|town\\s+council)\\s+of)\\b\\s*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUFFIX = Pattern.compile(
            "\\s*\\b((city|municipal|district|borough|county|town|metropolitan|regional)\\s+council|city\\s+hall)$", Pattern.CASE_INSENSITIVE);

    /** True for a municipal or administrative body (a council, a city hall, a district office), not a place people live in. */
    public static boolean isBody(String name) {
        if (name == null) return false;
        String s = name.trim();
        return s.length() > 0 && (PREFIX.matcher(s).find() || SUFFIX.matcher(s).find());
    }

    /** The place a body is named after ("Majlis Bandaraya Subang Jaya" gives "Subang Jaya"); a normal name stays; "" if nothing is left. */
    public static String stripBody(String name) {
        if (name == null) return "";
        String s = name.trim();
        if (!isBody(s)) return s;
        s = PREFIX.matcher(s).replaceFirst("");
        s = SUFFIX.matcher(s).replaceFirst("");
        return s.trim();
    }

    /** The names to show for a spot. [fromBody] is true when [city] was worked out from a municipal body (a nearby lookup may improve it). */
    public static final class Names {
        public String city = "";
        public String neighborhood = "";
        public boolean fromBody;
    }

    private static final String[] CITY_KEYS = {"city", "town", "village", "municipality", "county", "state_district"};
    private static final String[] AREA_KEYS = {"suburb", "quarter", "neighbourhood", "city_district", "hamlet"};

    /** Picks the city and the neighbourhood from a Nominatim address (field name to value). */
    public static Names resolve(Map<String, String> address) {
        Names n = new Names();
        for (int i = 0; i < CITY_KEYS.length && n.city.length() == 0; i++) {
            String v = value(address, CITY_KEYS[i]);
            if (v.length() > 0 && !isBody(v)) n.city = v;
        }
        for (int i = 0; i < CITY_KEYS.length && n.city.length() == 0; i++) {
            String v = value(address, CITY_KEYS[i]);
            String s = isBody(v) ? stripBody(v) : "";
            if (s.length() > 0) {
                n.city = s;
                n.fromBody = true;
            }
        }
        for (int i = 0; i < AREA_KEYS.length && n.neighborhood.length() == 0; i++) {
            String v = value(address, AREA_KEYS[i]);
            if (v.length() > 0 && !isBody(v)) n.neighborhood = v;
        }
        if (n.neighborhood.equals(n.city)) n.neighborhood = "";
        return n;
    }

    private static String value(Map<String, String> m, String key) {
        String v = m == null ? null : m.get(key);
        return v == null ? "" : v.trim();
    }

    // ---- the closest named place nearby ----

    public static final class Cand {
        public final String name, kind;
        public final double lat, lon;

        Cand(String name, String kind, double lat, double lon) {
            this.name = name;
            this.kind = kind;
            this.lat = lat;
            this.lon = lon;
        }
    }

    /** Reads an Overpass answer into named places (suburb, town, city, village, quarter, neighbourhood). Never throws. */
    public static List<Cand> parseOverpass(String json) {
        List<Cand> out = new ArrayList<Cand>();
        try {
            JSONArray el = new JSONObject(json).optJSONArray("elements");
            for (int i = 0; el != null && i < el.length(); i++) {
                JSONObject o = el.getJSONObject(i);
                JSONObject t = o.optJSONObject("tags");
                if (t == null || !o.has("lat") || !o.has("lon")) continue;
                String name = t.optString("name", "").trim(), kind = t.optString("place", "");
                if (name.length() == 0 || kind.length() == 0) continue;
                out.add(new Cand(name, kind, o.getDouble("lat"), o.getDouble("lon")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static boolean holistic(String kind) {
        return "town".equals(kind) || "city".equals(kind) || "suburb".equals(kind) || "village".equals(kind);
    }

    private static double meters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * 6371000 * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** The closest town, city, suburb or village within [maxM] metres, or null. */
    public static Cand nearest(double lat, double lon, List<Cand> cands, double maxM) {
        Cand best = null;
        double bestD = maxM;
        for (int i = 0; i < cands.size(); i++) {
            Cand c = cands.get(i);
            if (!holistic(c.kind)) continue;
            double d = meters(lat, lon, c.lat, c.lon);
            if (d <= bestD) {
                best = c;
                bestD = d;
            }
        }
        return best;
    }

    /** A name worked out from a municipal body gives way to the town or suburb you are actually in (within 2.5 km). Real names are never replaced. */
    public static void refine(Names n, double lat, double lon, List<Cand> cands) {
        if (!n.fromBody) return;
        Cand c = nearest(lat, lon, cands, 2500);
        if (c != null) {
            n.city = c.name;
            n.fromBody = false;
            if (n.neighborhood.equals(n.city)) n.neighborhood = "";
        }
    }
}
