package app.skypix.tools;

import app.skypix.core.PlaceNames;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Holistic place names: municipal bodies are never shown as the place. Usage: PlaceNamesTest */
public final class PlaceNamesTest {
    static int pass, fail;
    static final List<String> failures = new ArrayList<String>();

    static void check(String name, Object got, Object want) {
        boolean ok = String.valueOf(got).equals(String.valueOf(want));
        System.out.println((ok ? "PASS  " : "FAIL  ") + name + (ok ? "" : "   got [" + got + "] want [" + want + "]"));
        if (ok) pass++; else { fail++; failures.add(name); }
    }

    static Map<String, String> addr(String... kv) {
        Map<String, String> m = new HashMap<String, String>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    public static void main(String[] args) {
        // ---- what counts as a municipal body (and what does not) ----
        check("body: Majlis Perbandaran Petaling Jaya Selatan", PlaceNames.isBody("Majlis Perbandaran Petaling Jaya Selatan"), true);
        check("body: Majlis Bandaraya Subang Jaya", PlaceNames.isBody("Majlis Bandaraya Subang Jaya"), true);
        check("body: Majlis Daerah Hulu Langat", PlaceNames.isBody("Majlis Daerah Hulu Langat"), true);
        check("body: Dewan Bandaraya Kuala Lumpur", PlaceNames.isBody("Dewan Bandaraya Kuala Lumpur"), true);
        check("body: Subang Jaya City Council", PlaceNames.isBody("Subang Jaya City Council"), true);
        check("body: Selangor Municipal Council", PlaceNames.isBody("Selangor Municipal Council"), true);
        check("not a body: Petaling Jaya", PlaceNames.isBody("Petaling Jaya"), false);
        check("not a body: Subang Jaya", PlaceNames.isBody("Subang Jaya"), false);
        check("not a body: City of London", PlaceNames.isBody("City of London"), false);
        check("not a body: Majlis Hotel", PlaceNames.isBody("Majlis Hotel Apartments"), false);
        check("not a body: empty", PlaceNames.isBody(""), false);

        // ---- turning a body into the place it governs ----
        check("strip: Majlis Perbandaran Petaling Jaya Selatan", PlaceNames.stripBody("Majlis Perbandaran Petaling Jaya Selatan"), "Petaling Jaya Selatan");
        check("strip: Majlis Bandaraya Subang Jaya", PlaceNames.stripBody("Majlis Bandaraya Subang Jaya"), "Subang Jaya");
        check("strip: Subang Jaya City Council", PlaceNames.stripBody("Subang Jaya City Council"), "Subang Jaya");
        check("strip: Majlis Daerah Hulu Langat", PlaceNames.stripBody("Majlis Daerah Hulu Langat"), "Hulu Langat");
        check("strip: Dewan Bandaraya Kuala Lumpur", PlaceNames.stripBody("Dewan Bandaraya Kuala Lumpur"), "Kuala Lumpur");
        check("strip: a normal name stays", PlaceNames.stripBody("Petaling Jaya"), "Petaling Jaya");
        check("strip: nothing left gives empty", PlaceNames.stripBody("Majlis Perbandaran"), "");

        // ---- resolving a Nominatim address ----
        PlaceNames.Names a = PlaceNames.resolve(addr("city", "Majlis Perbandaran Petaling Jaya Selatan", "suburb", "Seksyen 14", "state", "Selangor"));
        check("address: city from the body", a.city, "Petaling Jaya Selatan");
        check("address: suburb kept as the neighbourhood", a.neighborhood, "Seksyen 14");
        check("address: flagged as derived from a body", a.fromBody, true);

        PlaceNames.Names b = PlaceNames.resolve(addr("city", "Petaling Jaya", "suburb", "SS 2"));
        check("address: a real city is kept", b.city, "Petaling Jaya");
        check("address: a real city is not flagged", b.fromBody, false);

        PlaceNames.Names c = PlaceNames.resolve(addr("municipality", "Majlis Bandaraya Subang Jaya", "town", "Subang Jaya"));
        check("address: a real town beats a body in another field", c.city, "Subang Jaya");

        PlaceNames.Names d = PlaceNames.resolve(addr("county", "Majlis Daerah Hulu Langat", "village", "Kampung Batu 14"));
        check("address: a real village beats a body", d.city, "Kampung Batu 14");

        PlaceNames.Names e = PlaceNames.resolve(addr("suburb", "Majlis Perbandaran Petaling Jaya", "city", "Petaling Jaya"));
        check("address: a body as the suburb is dropped", e.neighborhood, "");

        PlaceNames.Names f = PlaceNames.resolve(addr("suburb", "Bangsar", "city", "Kuala Lumpur"));
        check("address: city and neighbourhood stay as they were", f.city + " / " + f.neighborhood, "Kuala Lumpur / Bangsar");

        PlaceNames.Names g = PlaceNames.resolve(addr("city", "Kuala Lumpur", "suburb", "Kuala Lumpur"));
        check("address: neighbourhood equal to the city is cleared", g.neighborhood, "");

        PlaceNames.Names h = PlaceNames.resolve(addr("state", "Selangor"));
        check("address: nothing known gives empty city", h.city, "");

        // ---- the closest place name nearby (proximity beats the council that happens to own the spot) ----
        String overpass = "{\"elements\":[" +
                "{\"type\":\"node\",\"lat\":3.0738,\"lon\":101.5183,\"tags\":{\"name\":\"Subang Jaya\",\"place\":\"town\"}}," +
                "{\"type\":\"node\",\"lat\":3.0890,\"lon\":101.5900,\"tags\":{\"name\":\"Petaling Jaya\",\"place\":\"city\"}}," +
                "{\"type\":\"node\",\"lat\":3.0700,\"lon\":101.5300,\"tags\":{\"name\":\"USJ 9\",\"name:en\":\"USJ 9\",\"place\":\"neighbourhood\"}}," +
                "{\"type\":\"node\",\"lat\":3.0,\"lon\":101.0,\"tags\":{\"highway\":\"bus_stop\"}}]}";
        List<PlaceNames.Cand> cands = PlaceNames.parseOverpass(overpass);
        check("overpass: only named places are read", cands.size(), 3);
        check("overpass: a place without a name is skipped", PlaceNames.parseOverpass("{\"elements\":[{\"lat\":1,\"lon\":2,\"tags\":{\"place\":\"town\"}}]}").size(), 0);
        check("overpass: garbage gives an empty list", PlaceNames.parseOverpass("not json").size(), 0);
        PlaceNames.Cand near = PlaceNames.nearest(3.0731, 101.5190, cands, 2500);
        check("nearest: the closest town or suburb wins", near == null ? "null" : near.name, "Subang Jaya");
        check("nearest: nothing within the radius gives null", PlaceNames.nearest(3.2, 101.9, cands, 2500) == null ? "null" : "found", "null");
        check("nearest: a neighbourhood is not holistic enough to rename the city", near == null ? "" : near.kind, "town");

        PlaceNames.Names body = PlaceNames.resolve(addr("city", "Majlis Perbandaran Petaling Jaya", "suburb", "Seksyen 9"));
        PlaceNames.refine(body, 3.0731, 101.5190, cands);
        check("refine: a derived name gives way to the town you are standing in", body.city, "Subang Jaya");
        PlaceNames.Names real = PlaceNames.resolve(addr("city", "Petaling Jaya"));
        PlaceNames.refine(real, 3.0731, 101.5190, cands);
        check("refine: a real city name is never replaced", real.city, "Petaling Jaya");
        PlaceNames.Names far = PlaceNames.resolve(addr("city", "Majlis Perbandaran Petaling Jaya Selatan"));
        PlaceNames.refine(far, 3.2, 101.9, cands);
        check("refine: with nothing close the derived name stays", far.city, "Petaling Jaya Selatan");

        // ---- places saved before this change still show the place, not the council ----
        app.skypix.core.Place old = new app.skypix.core.Place();
        old.city = "Majlis Perbandaran Petaling Jaya Selatan";
        old.country = "Malaysia";
        check("saved place: short name is the place", old.shortName(), "Petaling Jaya Selatan");
        check("saved place: title is the place and the country", old.title(), "Petaling Jaya Selatan, Malaysia");
        app.skypix.core.Place fine = new app.skypix.core.Place();
        fine.city = "Subang Jaya";
        fine.neighborhood = "USJ 9";
        check("saved place: a normal city is untouched", fine.shortName(), "Subang Jaya");
        app.skypix.core.Place onlyBody = new app.skypix.core.Place();
        onlyBody.city = "Majlis Perbandaran";
        onlyBody.neighborhood = "Seksyen 14";
        check("saved place: a body with nothing left falls back to the neighbourhood", onlyBody.shortName(), "Seksyen 14");

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
