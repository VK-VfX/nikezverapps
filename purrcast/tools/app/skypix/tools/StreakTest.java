package app.skypix.tools;

import app.skypix.core.Streak;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;

/** Runs every case in docs/streak-vectors.json through Streak.step. Usage: StreakTest path/to/streak-vectors.json */
public final class StreakTest {
    private static int fail;

    private static void check(String name, String what, Object got, Object want) {
        if (!String.valueOf(got).equals(String.valueOf(want))) {
            System.out.println("FAIL " + name + ": " + what + " got " + got + " want " + want);
            fail++;
        }
    }

    public static void main(String[] args) throws Exception {
        String text = new String(Files.readAllBytes(new File(args[0]).toPath()), StandardCharsets.UTF_8);
        JSONArray cases = new JSONObject(text).getJSONArray("cases");
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String name = c.getString("name");
            boolean on = c.getBoolean("shieldOn");
            Streak.State s = new Streak.State();
            Object v = c.get("visits");
            if (v instanceof JSONArray) {
                JSONArray a = (JSONArray) v;
                for (int k = 0; k < a.length(); k++) s = Streak.step(s, a.getLong(k), on);
            } else {
                JSONObject r = (JSONObject) v;
                for (long d = r.getLong("from"); d <= r.getLong("to"); d++) s = Streak.step(s, d, on);
            }
            JSONObject e = c.getJSONObject("expect");
            check(name, "count", s.count, e.getInt("count"));
            check(name, "best", s.best, e.getInt("best"));
            check(name, "last", s.last, e.getLong("last"));
            check(name, "mask", Long.toUnsignedString(s.mask), e.getString("mask"));
            check(name, "ready", s.ready, e.getBoolean("ready"));
            check(name, "charge", s.charge, e.getInt("charge"));
            check(name, "label", Streak.label(s, on), e.getString("label"));
        }
        JSONArray ad = new JSONObject(text).getJSONArray("adopt");
        for (int i = 0; i < ad.length(); i++) {
            JSONObject c = ad.getJSONObject(i), lo = c.getJSONObject("local"), sb = c.getJSONObject("sibling"), e = c.getJSONObject("expect");
            Streak.State s = new Streak.State();
            s.count = lo.getInt("count"); s.best = lo.getInt("best"); s.last = lo.getLong("last");
            Streak.State n = Streak.adopt(s, c.getLong("today"), sb.getInt("count"), sb.getInt("best"), sb.getLong("last"));
            check(c.getString("name"), "count", n.count, e.getInt("count"));
            check(c.getString("name"), "best", n.best, e.getInt("best"));
        }
        Streak.State l = Streak.fromLegacy(7L);
        check("legacy 0b111", "count", l.count, 3);
        check("legacy 0b111", "best", l.best, 3);
        check("legacy 0b111", "ready", l.ready, true);
        check("legacy all ones", "count", Streak.fromLegacy(-1L).count, 64);
        check("legacy empty", "count", Streak.fromLegacy(0L).count, 0);
        if (fail > 0) { System.out.println(fail + " failures"); System.exit(1); }
        System.out.println("OK " + cases.length() + " cases + adopt");
    }
}
