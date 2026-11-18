import android.graphics.Bitmap
import com.nikopick.zamoled.gen.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The Supercat reward wallpaper: renders at every tier, corners stay exactly #000000, mostly black. */
@RunWith(RobolectricTestRunner::class)
class SuperCatTest {
    @Test
    fun rewardWallpapers() {
        CodeSource.init(File("../../app/src/main/java/com/nikopick/zamoled/gen"))
        HeroPack.init(File("../../app/src/main/assets/hero"))
        val out = File(System.getProperty("hero.out") ?: "build/hero").apply { mkdirs() }
        for ((day, mood, pal) in listOf(Triple(10, "smile", "gold"), Triple(20, "smirk", "sakura"), Triple(30, "grin", "aurora"), Triple(60, "scared", "cyber"))) {
            HeroPack.lead = mood
            val spec = WallpaperSpec("supercat", day * 7919L, pal, black = 0.4f, text = day.toString())
            val bmp = Renderer.render(spec, 540, 1170)
            for ((x, y) in listOf(0 to 0, 539 to 0, 0 to 1169, 539 to 1169)) assertEquals("day $day corner $x,$y", 0xFF000000.toInt(), bmp.getPixel(x, y))
            assertTrue("day $day black ${Renderer.blackRatio(bmp)}", Renderer.blackRatio(bmp) >= 0.35f)
            File(out, "supercat_$day.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
