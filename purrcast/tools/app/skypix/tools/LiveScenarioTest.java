package app.skypix.tools;

import app.skypix.core.Place;
import app.skypix.core.PlaceNames;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONObject;

/**
 * A live scenario: Florence, Colorado. The fixture is a real Nominatim answer captured from the live service for 38.3920, -105.1186
 * (tools/fixtures/nominatim-florence-co.json). Usage: LiveScenarioTest tools/fixtures/nominatim-florence-co.json
 */
public final class LiveScenarioTest {
    static int pass, fail;

    static void check(String name, Object got, Object want) {
        boolean ok = String.valueOf(got).equals(String.valueOf(want));
        System.out.println((ok ? "PASS  " : "FAIL  ") + name + (ok ? "" : "   got [" + got + "] want [" + want + "]"));
        if (ok) pass++; else fail++;
    }

    static PlaceNames.Names names(File f) throws Exception {
        JSONObject a = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getJSONObject("address");
        Map<String, String> fields = new HashMap<String, String>();
        for (Iterator<String> it = a.keys(); it.hasNext();) { String k = it.next(); fields.put(k, a.optString(k, "")); }
        return PlaceNames.resolve(fields);
    }

    static Place place(File f) throws Exception {
        JSONObject a = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).getJSONObject("address");
        PlaceNames.Names n = names(f);
        Place p = new Place();
        p.city = n.city; p.neighborhood = n.neighborhood; p.region = a.optString("state"); p.country = a.optString("country");
        return p;
    }

    public static void main(String[] args) throws Exception {
        JSONObject a = new JSONObject(new String(Files.readAllBytes(new File(args[0]).toPath()), StandardCharsets.UTF_8)).getJSONObject("address");
        Map<String, String> fields = new HashMap<String, String>();
        for (Iterator<String> it = a.keys(); it.hasNext();) {
            String k = it.next();
            fields.put(k, a.optString(k, ""));
        }
        PlaceNames.Names n = PlaceNames.resolve(fields);
        check("the village is the place", n.city, "Florence");
        check("the county is not the place", n.city.contains("County"), false);
        check("no nearby lookup is needed for a real village", n.needsNearby(), false);

        Place p = new Place();
        p.lat = 38.3920;
        p.lon = -105.1186;
        p.city = n.city;
        p.neighborhood = n.neighborhood;
        p.region = a.optString("state");
        p.country = a.optString("country");
        check("short name", p.shortName(), "Florence");
        check("title names the state, so it is not Florence in Italy", p.title(), "Florence, Colorado");

        // ---- 60.918691, -149.642056: Hope, Alaska (live Nominatim answers captured at the street and at region level) ----
        File dir = new File(args[0]).getParentFile();
        Place hope = place(new File(dir, "nominatim-hope-ak.json"));
        check("Hope: the village is the place, not the borough", hope.shortName(), "Hope");
        check("Hope: the title names the state", hope.title(), "Hope, Alaska");
        Place wild = place(new File(dir, "nominatim-hope-ak-wilderness.json"));
        check("wilderness: a borough is not a town, so the state is shown", wild.shortName(), "Alaska");
        check("wilderness: the title is the state and the country", wild.title(), "Alaska, United States");
        PlaceNames.Names open = names(new File(dir, "nominatim-hope-ak-wilderness.json"));
        check("wilderness: a nearby lookup is wanted", open.needsNearby(), true);
        java.util.List<PlaceNames.Cand> near = PlaceNames.parseOverpass("{\"elements\":[{\"lat\":60.9391,\"lon\":-149.6411,\"tags\":{\"name\":\"Hope\",\"place\":\"village\"}}]}");
        PlaceNames.refine(open, 60.918691, -149.642056, near);
        check("wilderness: the village 2 km away is found", open.city, "Hope");

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
