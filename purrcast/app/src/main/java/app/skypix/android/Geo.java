package app.skypix.android;

import app.skypix.core.Place;
import app.skypix.core.PlaceNames;

import org.json.JSONObject;

import java.util.Locale;

/** Reverse geocoding via OpenStreetMap Nominatim (no Google services involved). */
final class Geo {
    private Geo() {}

    /** Fills the name fields of {@code p}. Returns false if the lookup failed. */
    static boolean reverse(Place p) {
        try {
            String url = String.format(Locale.US,
                    "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1&accept-language=%s",
                    p.lat, p.lon, Locale.getDefault().toLanguageTag());
            JSONObject a = new JSONObject(Net.get(url)).optJSONObject("address");
            if (a == null) return false;
            java.util.Map<String, String> fields = new java.util.HashMap<String, String>();
            for (java.util.Iterator<String> it = a.keys(); it.hasNext();) {
                String k = it.next();
                fields.put(k, a.optString(k, ""));
            }
            // A council is not a place: use the real town, else the place the council is named after, else the closest town or suburb.
            PlaceNames.Names n = PlaceNames.resolve(fields);
            if (n.fromBody) {
                try {
                    String q = String.format(Locale.US, "[out:json][timeout:8];node(around:3000,%.5f,%.5f)[place~\"^(suburb|town|city|village|neighbourhood|quarter)$\"];out 20;", p.lat, p.lon);
                    PlaceNames.refine(n, p.lat, p.lon, PlaceNames.parseOverpass(Net.get("https://overpass-api.de/api/interpreter?data=" + java.net.URLEncoder.encode(q, "UTF-8"))));
                } catch (Exception ignored) {
                    // offline or busy: the name worked out from the council is still a place
                }
            }
            p.neighborhood = n.neighborhood;
            p.city = n.city;
            p.region = first(a, "state", "region", "province");
            p.country = a.optString("country", "");
            if (p.neighborhood.equals(p.city)) p.neighborhood = "";
            return p.city.length() > 0 || p.neighborhood.length() > 0 || p.region.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    static double distanceM(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
