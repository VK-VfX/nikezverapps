package app.skypix.tools;

import app.skypix.core.Cards;
import app.skypix.core.Glyphs;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Draws the Clock widget with long place names so they can be looked at. Usage: PlaceNameCheck outdir */
public final class PlaceNameCheck {
    public static void main(String[] a) throws Exception {
        String[] names = {"Subang Jaya City Council", "Kuala Lumpur", "Majlis Perbandaran Petaling Jaya Selatan", "New York"};
        int[] kinds = {Cards.NOW, Cards.NOW, Cards.CLOCK_BIG, Cards.NOW_WIDE};
        int[][] sz = {{110, 110}, {110, 150}, {160, 150}, {250, 110}};
        float u = 2f;
        BufferedImage img = new BufferedImage(Math.round(700 * u), Math.round(190 * u) * names.length, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.BLACK);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int r = 0; r < names.length; r++) {
            Cards.Data d = Samples.data("rain", false, false);
            Glyphs.hero = true; Glyphs.heroVariant = r;
            d.place.city = names[r];
            int x = 14;
            for (int c = 0; c < sz.length; c++) {
                g.drawImage(Preview.render(kinds[c], sz[c][0], sz[c][1], u, true, d), Math.round(x * u), Math.round((14 + r * 190) * u), null);
                x += sz[c][0] + 20;
            }
        }
        g.dispose();
        new File(a[0]).mkdirs();
        ImageIO.write(img, "png", new File(a[0], "place.png"));
    }
}
