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

    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    // A body in front of the name: Malay and Indonesian, French, Spanish, Portuguese, Italian, English.
    private static final Pattern PREFIX = Pattern.compile(
            "^(majlis\\s+(perbandaran|bandaraya|daerah|bandaran|tempatan)|dewan\\s+bandaraya|pejabat\\s+daerah|pihak\\s+berkuasa\\s+tempatan"
                    + "|pemerintah\\s+(kota|kabupaten|daerah)|pemkot|pemkab"
                    + "|mairie\\s+(de|du|des|d['\u2019])|communaut[\u00e9e]\\s+(de\\s+communes|d['\u2019]agglom[\u00e9e]ration|urbaine)|m[\u00e9e]tropole\\s+(de|du|des|d['\u2019])"
                    + "|ayuntamiento\\s+(de|del)|c[\u00e2a]mara\\s+municipal\\s+(de|do|da|dos|das)|prefeitura(\\s+municipal)?\\s+(de|do|da|dos|das)"
                    + "|comune\\s+(di|del|della|dell['\u2019]|dei)|citt[\u00e0a]\\s+metropolitana\\s+di"
                    + "|shire\\s+of|regional\\s+(district|municipality|council)\\s+of"
                    + "|(municipality|municipal\\s+council|city\\s+council|district\\s+council|town\\s+council)\\s+of)\\b\\s*", F);
    // German and Dutch bodies are a word then the name ("Gemeinde Garching"); the space is required so "Stadtteil Mitte" and "Stadtpark" stay places.
    private static final Pattern WORD_PREFIX = Pattern.compile(
            "^(stadtverwaltung|gemeindeverwaltung|stadtgemeinde|marktgemeinde|verbandsgemeinde|ortsgemeinde|gemeinde|stadt|landratsamt|bezirksamt|gemeente)\\s+", F);
    // A body after the name: "Subang Jaya City Council", "\u00c7ankaya Belediyesi", "Kobenhavn kommune", Chinese, Japanese and Korean city offices.
    private static final Pattern SUFFIX = Pattern.compile(
            "\\s*(\\b((city|municipal|district|borough|county|town|metropolitan|regional|shire)\\s+council|city\\s+hall|belediyesi|kommune|kommun)"
                    + "|(\u4eba\u6c11\u653f\u5e9c|\u653f\u5e9c|\u5f79\u6240|\u5f79\u5834|\uc2dc\uccad|\uad6c\uccad|\uad70\uccad))$", F);

    /** True for a municipal or administrative body (a council, a city hall, a district office), not a place people live in. */
    public static boolean isBody(String name) {
        if (name == null) return false;
        String s = name.trim();
        return s.length() > 0 && (PREFIX.matcher(s).find() || WORD_PREFIX.matcher(s).find() || SUFFIX.matcher(s).find());
    }

    /** The place a body is named after ("Majlis Bandaraya Subang Jaya" gives "Subang Jaya"); a normal name stays; "" if nothing is left. */
    public static String stripBody(String name) {
        if (name == null) return "";
        String s = name.trim();
        if (!isBody(s)) return s;
        s = PREFIX.matcher(s).replaceFirst("");
        s = WORD_PREFIX.matcher(s).replaceFirst("");
        s = SUFFIX.matcher(s).replaceFirst("");
        return s.trim();
    }

    /** The names to show for a spot. [fromBody] is true when [city] was worked out from a municipal body (a nearby lookup may improve it). */
    public static final class Names {
        public String city = "";
        public String neighborhood = "";
        public boolean fromBody;

        /** True when there is no real city, town or village yet: a nearby lookup can find one. */
        public boolean needsNearby() {
            return fromBody || city.length() == 0;
        }
    }

    private static final String[] CITY_KEYS = {"city", "town", "village", "municipality", "county", "state_district"};
    private static final String[] AREA_KEYS = {"suburb", "quarter", "neighbourhood", "city_district", "borough", "district", "hamlet"};

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

    private static Cand nearestTown(double lat, double lon, List<Cand> cands, double maxM) {
        Cand best = null;
        double bestD = maxM;
        for (int i = 0; i < cands.size(); i++) {
            Cand c = cands.get(i);
            if (!("town".equals(c.kind) || "city".equals(c.kind) || "village".equals(c.kind))) continue;
            double d = meters(lat, lon, c.lat, c.lon);
            if (d <= bestD) {
                best = c;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * Points at the town, city or suburb you are really in. A name worked out from a municipal body gives way to the closest
     * town or suburb within 2.5 km; when there is no city at all, the closest town, city or village within 12 km is used (and if
     * there is none, the state is what gets shown). Real names are never replaced.
     */
    public static void refine(Names n, double lat, double lon, List<Cand> cands) {
        if (!n.needsNearby()) return;
        Cand c = nearest(lat, lon, cands, 2500);
        if (c == null && n.city.length() == 0) c = nearestTown(lat, lon, cands, 12000);
        if (c != null) {
            n.city = c.name;
            n.fromBody = false;
            if (n.neighborhood.equals(n.city)) n.neighborhood = "";
        }
    }
}
