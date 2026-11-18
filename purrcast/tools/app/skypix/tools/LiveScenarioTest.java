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

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
