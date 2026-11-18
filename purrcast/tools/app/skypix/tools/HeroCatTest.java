package app.skypix.tools;

import app.skypix.core.HeroCat;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;

/** Runs docs/hero-cat-vectors.json through HeroCat. Usage: HeroCatTest path/to/hero-cat-vectors.json */
public final class HeroCatTest {
    public static void main(String[] args) throws Exception {
        JSONObject j = new JSONObject(new String(Files.readAllBytes(new File(args[0]).toPath()), StandardCharsets.UTF_8));
        int fail = 0, n = 0;
        JSONArray p = j.getJSONArray("phases");
        for (int i = 0; i < p.length(); i++, n++) {
            JSONObject c = p.getJSONObject(i);
            int count = c.getInt("count"); boolean ready = c.getBoolean("ready"); int charge = c.getInt("charge");
            String got = HeroCat.phase(count, ready, charge).name() + "/" + HeroCat.animated(count) + "/" + HeroCat.joy(count);
            String want = c.getString("phase") + "/" + c.getBoolean("animated") + "/" + c.getInt("joy");
            if (!got.equals(want)) { System.out.println("FAIL phase " + count + "," + ready + "," + charge + " got " + got + " want " + want); fail++; }
        }
        JSONArray e = j.getJSONArray("emotions");
        for (int i = 0; i < e.length(); i++, n++) {
            JSONObject c = e.getJSONObject(i);
            String got = HeroCat.emotion(c.getString("weather"), c.getInt("count"));
            if (!got.equals(c.getString("emotion"))) { System.out.println("FAIL emotion " + c + " got " + got); fail++; }
        }
        System.out.println(fail == 0 ? "OK " + n + " cases" : fail + " FAILED of " + n);
        System.exit(fail == 0 ? 0 : 1);
    }
}
