import android.graphics.Bitmap
import com.nikopick.zamoled.gen.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Hero Club: stays hidden until unlocked; renders on pure black; keeps the corners exactly #000000. */
@RunWith(RobolectricTestRunner::class)
class HeroTest {
    @Test
    fun heroClubIsPureBlackAndGated() {
        CodeSource.init(File("../../app/src/main/java/com/nikopick/zamoled/gen"))
        HeroPack.init(File("../../app/src/main/assets/hero"))
        HeroPack.unlocked = false
        assertFalse("hidden while locked", Styles.all.any { it.id == "hero_club" })
        HeroPack.unlocked = true
        HeroPack.lead = "sad"
        assertTrue("shown when unlocked", Styles.all.any { it.id == "hero_club" })
        val bmp = Renderer.render(WallpaperSpec("hero_club", 12345L, "auto", 1f, "auto", 0f, "", false, false, 1, 1), 540, 1170)
        for ((x, y) in listOf(0 to 0, 539 to 0, 0 to 1169, 539 to 1169)) assertEquals("corner $x,$y", 0xFF000000.toInt(), bmp.getPixel(x, y))
        val ratio = Renderer.blackRatio(bmp)
        assertTrue("black ratio $ratio", ratio >= 0.5f)
        val out = File(System.getProperty("hero.out") ?: "build/hero").apply { mkdirs() }
        File(out, "hero_club.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        HeroPack.unlocked = false
    }
}
