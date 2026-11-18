import com.nikopick.zamoled.gen.heroAnimated
import com.nikopick.zamoled.gen.heroEmotion
import com.nikopick.zamoled.gen.heroJoy
import com.nikopick.zamoled.gen.heroPhase
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Runs docs/hero-cat-vectors.json (shared with Purrcast and Maps and Cats) through the supercat rules. */
@RunWith(RobolectricTestRunner::class)
class HeroCatTest {
    @Test
    fun sharedVectors() {
        val j = JSONObject(File("../../../docs/hero-cat-vectors.json").readText())
        val p = j.getJSONArray("phases")
        for (i in 0 until p.length()) {
            val c = p.getJSONObject(i)
            val n = c.getInt("count")
            val tag = "${n},${c.getBoolean("ready")},${c.getInt("charge")}"
            assertEquals("phase $tag", c.getString("phase"), heroPhase(n, c.getBoolean("ready"), c.getInt("charge")).name)
            assertEquals("animated $tag", c.getBoolean("animated"), heroAnimated(n))
            assertEquals("joy $tag", c.getInt("joy"), heroJoy(n))
        }
        val e = j.getJSONArray("emotions")
        for (i in 0 until e.length()) {
            val c = e.getJSONObject(i)
            assertEquals(c.toString(), c.getString("emotion"), heroEmotion(c.getString("weather"), c.getInt("count")))
        }
    }
}
