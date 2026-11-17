import com.nikopick.zamoled.gen.StreakState
import com.nikopick.zamoled.gen.shieldLabel
import com.nikopick.zamoled.gen.streakFromLegacy
import com.nikopick.zamoled.gen.streakStep
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Runs every case in docs/streak-vectors.json through streakStep (the same cases Purrcast and Maps and Cats use). */
@RunWith(RobolectricTestRunner::class)
class StreakTest {
    @Test
    fun sharedVectors() {
        val cases = JSONObject(File("../../../docs/streak-vectors.json").readText()).getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val name = c.getString("name")
            val on = c.getBoolean("shieldOn")
            var s = StreakState()
            val v = c.get("visits")
            val days = if (v is JSONArray) (0 until v.length()).map { v.getLong(it) } else (v as JSONObject).let { (it.getLong("from")..it.getLong("to")).toList() }
            for (d in days) s = streakStep(s, d, on)
            val e = c.getJSONObject("expect")
            assertEquals("$name count", e.getInt("count"), s.count)
            assertEquals("$name best", e.getInt("best"), s.best)
            assertEquals("$name last", e.getLong("last"), s.last)
            assertEquals("$name mask", e.getString("mask"), s.mask.toString())
            assertEquals("$name ready", e.getBoolean("ready"), s.ready)
            assertEquals("$name charge", e.getInt("charge"), s.charge)
            assertEquals("$name label", e.getString("label"), shieldLabel(s, on))
        }
    }

    @Test
    fun legacyHistory() {
        assertEquals(3, streakFromLegacy(7uL).count)
        assertEquals(3, streakFromLegacy(7uL).best)
        assertEquals(64, streakFromLegacy(ULong.MAX_VALUE).count)
        assertEquals(0, streakFromLegacy(0uL).count)
    }
}
