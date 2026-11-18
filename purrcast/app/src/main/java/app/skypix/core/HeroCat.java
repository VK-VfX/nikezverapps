package app.skypix.core;

/**
 * The supercat's life cycle (docs/HERO-CAT-RULES.md). Pure rules shared with Zamoled (Kotlin) and Maps and Cats (JS),
 * checked against docs/hero-cat-vectors.json.
 */
public final class HeroCat {
    private HeroCat() {}

    public enum Phase { HERO, SAD, CONFIDENT }

    /** The supercat shows from the 10th streak day while the shield is ready; otherwise the plain Noto cat: sad for 2 days, confident from the 3rd. */
    public static Phase phase(int count, boolean shieldReady, int charge) {
        if (shieldReady && count >= 10) return Phase.HERO;
        int day = shieldReady ? count : charge + 1;
        return day >= 3 ? Phase.CONFIDENT : Phase.SAD;
    }

    /** How happy the supercat is: 0 on day 10, up to 4 from day 30. */
    public static int joy(int count) {
        return count <= 10 ? 0 : count <= 13 ? 1 : count <= 20 ? 2 : count <= 29 ? 3 : 4;
    }

    /** Calm weather moods grow happier with the streak; bad-weather and night moods are never changed. */
    public static String emotion(String weatherMood, int count) {
        int j = joy(count);
        boolean calm = weatherMood.equals("smile") || weatherMood.equals("smirk") || weatherMood.equals("confident");
        if (j >= 2 && calm) return "grin";
        if (j == 1 && (weatherMood.equals("smirk") || weatherMood.equals("confident"))) return "smile";
        return weatherMood;
    }

    /** Past 20 days the special wallpaper and the card get living effects. */
    public static boolean animated(int count) { return count > 20; }
}
