package app.skypix.core;

/**
 * The day streak and the Streak Shield. Pure rules, no Android: the same rules are ported to Zamoled (Kotlin) and Maps and
 * Cats (JS) and all three are checked against docs/streak-vectors.json, so they cannot drift apart.
 *
 * A visit is the app coming to the front; the day is the phone's local calendar day. The shield saves a gap of 1 to 7
 * missed days, once, and recharges after 7 visit days in a row. It only works while all three apps are installed.
 */
public final class Streak {
    private Streak() {}

    /** Visit days needed to recharge a used shield. */
    public static final int RECHARGE_DAYS = 7;
    /** Largest day gap the shield can bridge: 1 to 7 missed days. */
    public static final int MAX_SAVED_GAP = 8;

    public static final class State {
        public int count;
        public int best;
        /** Day number of the last visit; 0 means never. */
        public long last;
        /** 64-day history, bit 0 = the last visit day. Treat as unsigned. */
        public long mask;
        public boolean ready = true;
        public int charge;

        public State copy() {
            State s = new State();
            s.count = count; s.best = best; s.last = last; s.mask = mask; s.ready = ready; s.charge = charge;
            return s;
        }
    }

    /** The streak after a visit on [day]. Returns [s] itself when nothing changes (same day, or the clock went backwards). */
    public static State step(State s, long day, boolean shieldOn) {
        if (s.last != 0 && day <= s.last) return s;
        long gap = s.last == 0 ? 0 : day - s.last;
        State n = s.copy();
        if (gap == 1) {
            n.count++;
            if (!n.ready) {
                n.charge++;
                if (n.charge >= RECHARGE_DAYS) { n.ready = true; n.charge = 0; }
            }
        } else if (gap >= 2 && gap <= MAX_SAVED_GAP && shieldOn && s.ready) {
            n.count++;
            n.ready = false;
            n.charge = 0;
        } else {
            n.count = 1;
            if (!n.ready) n.charge = 0;
        }
        n.best = Math.max(n.best, n.count);
        long delta = s.last == 0 ? 64 : gap;
        n.mask = delta >= 64 ? 1L : ((s.mask << delta) | 1L);
        n.last = day;
        return n;
    }

    /** "" when the shield is off, else "shield ready" or "shield recharging, N days to go". */
    public static String label(State s, boolean shieldOn) {
        if (!shieldOn) return "";
        return s.ready ? "shield ready" : "shield recharging, " + (RECHARGE_DAYS - s.charge) + " days to go";
    }

    /** An old install had only the 64-day history: its streak is the run of set bits from bit 0. The caller sets {@code last}. */
    public static State fromLegacy(long mask) {
        int run = 0;
        while (run < 64 && ((mask >>> run) & 1L) != 0) run++;
        State s = new State();
        s.count = run;
        s.best = run;
        s.mask = mask;
        return s;
    }
}
