import com.nikopick.zamoled.gen.StreakState
import com.nikopick.zamoled.gen.mergeStreak
import com.nikopick.zamoled.gen.zamFromPayload
import com.nikopick.zamoled.gen.zamPayload
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Zamoled progress mapping and merge (the reference payload is the one in docs/save-code-vectors.json). */
@RunWith(RobolectricTestRunner::class)
class ProgressTest {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun referencePayloadRoundTrips() {
        val s = StreakState(3, 3, 20409, 7uL, false, 2)
        assertEquals("0303b99f01000000000000000704", hex(zamPayload(s)))
        assertEquals(s, zamFromPayload(zamPayload(s)))
    }

    @Test
    fun mergeNeverLowers() {
        val newerLocal = mergeStreak(StreakState(5, 5, 100, 31uL, true, 0), StreakState(9, 30, 90, 7uL, false, 3))
        assertEquals(5, newerLocal.count); assertEquals(30, newerLocal.best); assertEquals(true, newerLocal.ready)
        val newerRemote = mergeStreak(StreakState(2, 8, 90, 3uL, true, 0), StreakState(6, 8, 100, 63uL, false, 4))
        assertEquals(6, newerRemote.count); assertEquals(false, newerRemote.ready); assertEquals(4, newerRemote.charge)
        val zero = mergeStreak(StreakState(14, 20, 20409, 16383uL, true, 0), StreakState())
        assertEquals(14, zero.count); assertEquals(20, zero.best)
    }
}
