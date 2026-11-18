package app.skypix.tools;

import app.skypix.core.Progress;
import app.skypix.core.Streak;

/** Purrcast progress mapping and merge. Usage: ProgressTest */
public final class ProgressTest {
    static int fail;
    static void check(String n, Object got, Object want) { if (!String.valueOf(got).equals(String.valueOf(want))) { System.out.println("FAIL " + n + ": got " + got + " want " + want); fail++; } }
    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x & 0xFF)); return s.toString(); }

    static Progress p(int count, int best, long last, long mask, boolean ready, int charge, int reward) {
        Progress p = new Progress();
        p.streak.count = count; p.streak.best = best; p.streak.last = last; p.streak.mask = mask; p.streak.ready = ready; p.streak.charge = charge;
        p.rewardClaimed = reward;
        return p;
    }

    public static void main(String[] a) {
        Progress ref = p(14, 20, 20409, 16383, true, 0, 1);
        check("payload hex", hex(ref.toPayload()), "0e14b99f010000000000003fff0101");
        Progress back = Progress.fromPayload(ref.toPayload());
        check("count", back.streak.count, 14); check("best", back.streak.best, 20); check("last", back.streak.last, 20409);
        check("mask", back.streak.mask, 16383L); check("ready", back.streak.ready, true); check("reward", back.rewardClaimed, 1);
        // newer local keeps its block but takes a higher remote best
        Progress m = Progress.merge(p(5, 5, 100, 31, true, 0, 0), p(9, 30, 90, 7, false, 3, 2));
        check("local newer count", m.streak.count, 5); check("local newer best", m.streak.best, 30); check("rewards max", m.rewardClaimed, 2); check("local ready", m.streak.ready, true);
        // newer remote takes the remote block
        Progress r = Progress.merge(p(2, 8, 90, 3, true, 0, 1), p(6, 8, 100, 63, false, 4, 0));
        check("remote newer count", r.streak.count, 6); check("remote newer ready", r.streak.ready, false); check("remote newer charge", r.streak.charge, 4); check("reward keeps local", r.rewardClaimed, 1);
        // an all-zero code changes nothing
        Progress z = Progress.merge(p(14, 20, 20409, 16383, true, 0, 1), new Progress());
        check("zero count", z.streak.count, 14); check("zero best", z.streak.best, 20); check("zero reward", z.rewardClaimed, 1);
        if (fail > 0) { System.out.println(fail + " failures"); System.exit(1); }
        System.out.println("OK progress");
    }
}
