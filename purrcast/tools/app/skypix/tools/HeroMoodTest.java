package app.skypix.tools;

import app.skypix.core.HeroMood;
import app.skypix.core.Icon;

public final class HeroMoodTest {
    static void eq(String a, String b) { if (!a.equals(b)) throw new AssertionError(a + " != " + b); }
    public static void main(String[] args) {
        eq(HeroMood.pick(Icon.SUN, null), "smile");
        eq(HeroMood.pick(Icon.RAIN, null), "sad");
        eq(HeroMood.pick(Icon.STORM, null), "angry");
        eq(HeroMood.pick(Icon.MOON, null), "loaf");
        for (Icon ic : Icon.values()) if (!java.util.Arrays.asList(HeroMood.ALL).contains(HeroMood.fromIcon(ic))) throw new AssertionError(ic.name());
        for (int i = 0; i < 50; i++) if (HeroMood.badWeatherOnly(HeroMood.decor(i)) || HeroMood.badWeatherOnly(HeroMood.forApp(null, i))) throw new AssertionError("decor " + i);
        eq(HeroMood.forApp("angry", 3), "angry");
        eq(HeroMood.forApp("bogus", 0), HeroMood.CALM[0]);
        System.out.println("OK mood");
    }
}
