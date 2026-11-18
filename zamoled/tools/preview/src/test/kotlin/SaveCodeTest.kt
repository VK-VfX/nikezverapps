import com.nikopick.zamoled.gen.SaveCode
import com.nikopick.zamoled.gen.SaveCodeResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Checks SaveCode against docs/save-code-vectors.json (the same file Purrcast and Maps and Cats use). */
@RunWith(RobolectricTestRunner::class)
class SaveCodeTest {
    private fun unhex(h: String) = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun vectors() {
        val root = JSONObject(File("../../../docs/save-code-vectors.json").readText())
        val cases = root.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val h = c.getString("payloadHex").dropLast(4)
            assertEquals(c.getString("app") + " encode", c.getString("code"), SaveCode.encode(c.getString("prefix"), unhex(h)))
            val r = SaveCode.decode(c.getString("code"), c.getString("prefix"))
            assertTrue(c.getString("app") + " decode", r is SaveCodeResult.Ok)
            assertEquals(h, hex((r as SaveCodeResult.Ok).payload))
        }
        val messy = root.getJSONArray("messy")
        for (i in 0 until messy.length()) {
            val m = messy.getJSONObject(i)
            val r = SaveCode.decode(m.getString("text"), m.getString("prefix"))
            val kind = when (r) { is SaveCodeResult.Ok -> "OK"; is SaveCodeResult.WrongApp -> "WRONG_APP"; SaveCodeResult.Typo -> "TYPO"; SaveCodeResult.NotFound -> "NOT_FOUND" }
            assertEquals(m.getString("name"), m.getString("expectKind"), kind)
            if (r is SaveCodeResult.Ok && m.has("expectPayloadHex")) assertEquals(m.getString("expectPayloadHex"), hex(r.payload))
            if (r is SaveCodeResult.WrongApp && m.has("expectOther")) assertEquals(m.getString("expectOther"), r.other)
        }
        val big = buildString { val rnd = java.util.Random(1); repeat(5_000_000) { append('A' + rnd.nextInt(26)) } }
        val t0 = System.currentTimeMillis()
        assertTrue(SaveCode.decode(big, "PRC1") is SaveCodeResult.NotFound)
        assertTrue("5 MB paste must be fast", System.currentTimeMillis() - t0 < 2000)
    }
}
