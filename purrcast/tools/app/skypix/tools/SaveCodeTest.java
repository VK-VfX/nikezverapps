package app.skypix.tools;

import app.skypix.core.SaveCode;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;

/** Checks SaveCode against docs/save-code-vectors.json. Usage: SaveCodeTest path/to/save-code-vectors.json */
public final class SaveCodeTest {
    static int fail;

    static void check(String name, Object got, Object want) {
        if (!String.valueOf(got).equals(String.valueOf(want))) { System.out.println("FAIL " + name + ": got " + got + " want " + want); fail++; }
    }

    static byte[] unhex(String h) {
        byte[] b = new byte[h.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        return b;
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < b.length; i++) s.append(String.format("%02x", b[i] & 0xFF));
        return s.toString();
    }

    static String kind(int k) { return k == SaveCode.OK ? "OK" : k == SaveCode.WRONG_APP ? "WRONG_APP" : k == SaveCode.TYPO ? "TYPO" : "NOT_FOUND"; }

    public static void main(String[] a) throws Exception {
        JSONObject root = new JSONObject(new String(Files.readAllBytes(new File(a[0]).toPath()), StandardCharsets.UTF_8));
        JSONArray cases = root.getJSONArray("cases");
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String h = c.getString("payloadHex");
            byte[] payload = unhex(h.substring(0, h.length() - 4));
            check(c.getString("app") + " encode", SaveCode.encode(c.getString("prefix"), payload), c.getString("code"));
            SaveCode.Result r = SaveCode.decode(c.getString("code"), c.getString("prefix"));
            check(c.getString("app") + " decode kind", kind(r.kind), "OK");
            if (r.payload != null) check(c.getString("app") + " decode payload", hex(r.payload), h.substring(0, h.length() - 4));
        }
        JSONArray messy = root.getJSONArray("messy");
        for (int i = 0; i < messy.length(); i++) {
            JSONObject m = messy.getJSONObject(i);
            SaveCode.Result r = SaveCode.decode(m.getString("text"), m.getString("prefix"));
            check(m.getString("name") + " kind", kind(r.kind), m.getString("expectKind"));
            if (m.has("expectPayloadHex") && r.payload != null) check(m.getString("name") + " payload", hex(r.payload), m.getString("expectPayloadHex"));
            if (m.has("expectOther")) check(m.getString("name") + " other", r.otherPrefix, m.getString("expectOther"));
        }
        StringBuilder big = new StringBuilder();
        java.util.Random rnd = new java.util.Random(1);
        while (big.length() < 5_000_000) big.append((char) ('A' + rnd.nextInt(26)));
        long t0 = System.currentTimeMillis();
        SaveCode.Result r = SaveCode.decode(big.toString(), SaveCode.PRC);
        check("5 MB paste is fast", System.currentTimeMillis() - t0 < 2000, true);
        check("5 MB paste kind", kind(r.kind), "NOT_FOUND");
        if (fail > 0) { System.out.println(fail + " failures"); System.exit(1); }
        System.out.println("OK " + cases.length() + " vectors, " + messy.length() + " messy");
    }
}
