package app.skypix.core;

/**
 * Which hero-cat emotion to show. One rule for the whole trio: the mood follows the weather and the one-liner
 * Purrcast is showing. Angry and sad belong to bad weather only; the other apps never pick them at random.
 */
public final class HeroMood {
    private HeroMood() {}

    public static final String[] ALL = {"smile", "grin", "smirk", "confident", "angry", "shocked", "sad", "scared", "loaf"};
    /** What an app may pick at random when it has no weather mood to follow (no angry, no sad). */
    public static final String[] CALM = {"smile", "grin", "smirk", "confident", "shocked", "loaf"};

    /** The mood for the current weather icon and the top one-liner (may be null). The one-liner wins when it is a notice or a warning. */
    public static String pick(Icon weather, Insights.Insight top) {
        if (top != null && top.sev >= Insights.NOTICE) {
            String k = top.key;
            if (k.startsWith("storm") || (k.startsWith("flood") && !k.startsWith("flood-watch")) || (k.startsWith("wind") && top.sev >= Insights.WARNING)) return "angry";
            if (k.startsWith("flood-watch") || k.startsWith("wet")) return "sad";
            if (k.startsWith("rain-soon")) return "shocked";
            if (k.startsWith("heat") || k.startsWith("freeze") || k.startsWith("aqi") && top.sev >= Insights.WARNING || k.startsWith("uv") && top.sev >= Insights.WARNING) return "scared";
            if (k.startsWith("hot") || k.startsWith("uv")) return "confident";
            if (k.startsWith("wind")) return "shocked";
            if (k.startsWith("aqi") || k.startsWith("fog")) return "smirk";
        }
        return fromIcon(weather);
    }

    public static String fromIcon(Icon ic) {
        if (ic == null) return "smile";
        switch (ic) {
            case SHADES: return "confident";
            case PARTLY_DAY: case CLOUD: case FOG: return "smirk";
            case DRIZZLE: case RAIN: case UMBRELLA: return "sad";
            case STORM: return "angry";
            case WIND: return "shocked";
            case SNOW: case SHIVER: case MASK: return "scared";
            case SWEAT: return "grin";
            case MOON: case PARTLY_NIGHT: case SLEEPY: return "loaf";
            default: return "smile";
        }
    }

    /** True for the two moods that only bad weather may show. */
    public static boolean badWeatherOnly(String mood) {
        return "angry".equals(mood) || "sad".equals(mood);
    }

    /** What another app shows: Purrcast's mood when it has one, otherwise a calm pick from the seed. Unknown names fall back to calm. */
    public static String forApp(String purrcastMood, int seed) {
        if (purrcastMood != null) for (int i = 0; i < ALL.length; i++) if (ALL[i].equals(purrcastMood)) return purrcastMood;
        return CALM[Math.abs(seed % CALM.length)];
    }

    /** A random-looking pick for decoration (parade, club wallpaper): never angry or sad. */
    public static String decor(int seed) {
        return CALM[Math.abs(seed % CALM.length)];
    }
}
