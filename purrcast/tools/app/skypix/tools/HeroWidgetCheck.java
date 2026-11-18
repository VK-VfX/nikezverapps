package app.skypix.tools;

import app.skypix.core.Cards;
import app.skypix.core.Glyphs;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Draws a few widgets with the hero cat on, for a look. Usage: HeroWidgetCheck outdir */
public final class HeroWidgetCheck {
    public static void main(String[] a) throws Exception {
        String[] sc = {"clear", "rain", "storm"};
        int[] kinds = {Cards.NOW, Cards.CLOCK_BIG, Cards.HEADSUP, Cards.NOW_WIDE};
        int[][] sz = {{110, 110}, {160, 150}, {180, 110}, {250, 110}};
        float u = 2f;
        BufferedImage img = new BufferedImage(Math.round(740 * u), Math.round(150 * u) * sc.length, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        Glyphs.hero = true;
        for (int r = 0; r < sc.length; r++) {
            Cards.Data d = Samples.data(sc[r], false, false);
            int x = 10;
            for (int c = 0; c < kinds.length; c++) {
                g.drawImage(Preview.render(kinds[c], sz[c][0], sz[c][1], u, true, d), Math.round(x * u), Math.round((6 + r * 150) * u), null);
                x += sz[c][0] + 12;
            }
        }
        g.dispose();
        new File(a[0]).mkdirs();
        ImageIO.write(img, "png", new File(a[0], "hero-widgets.png"));
    }
}
