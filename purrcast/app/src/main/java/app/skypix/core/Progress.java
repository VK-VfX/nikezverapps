package app.skypix.core;

import java.io.ByteArrayOutputStream;

/** What the Purrcast save code carries (counters only): the streak block and how many streak rewards were claimed. */
public final class Progress {
    public Streak.State streak = new Streak.State();
    public int rewardClaimed;

    // ---- the streak block, shared by all three apps' codes ----

    public static void writeVarint(ByteArrayOutputStream o, long v) {
        while (true) {
            int b = (int) (v & 0x7F);
            v >>>= 7;
            if (v != 0) o.write(b | 0x80); else { o.write(b); return; }
        }
    }

    /** Position-tracking reader over the payload. */
    public static final class In {
        final byte[] b;
        int p;

        public In(byte[] b) { this.b = b; }

        public long varint() {
            long v = 0;
            int shift = 0;
            while (p < b.length) {
                int x = b[p++] & 0xFF;
                v |= (long) (x & 0x7F) << shift;
                if ((x & 0x80) == 0) return v;
                shift += 7;
            }
            throw new IllegalArgumentException("short payload");
        }

        public int u8() {
            if (p >= b.length) throw new IllegalArgumentException("short payload");
            return b[p++] & 0xFF;
        }
    }

    public static void writeStreak(ByteArrayOutputStream o, Streak.State s) {
        writeVarint(o, s.count);
        writeVarint(o, s.best);
        writeVarint(o, s.last);
        for (int i = 7; i >= 0; i--) o.write((int) (s.mask >>> (i * 8)) & 0xFF);
        o.write((s.ready ? 1 : 0) | (s.charge << 1));
    }

    public static Streak.State readStreak(In in) {
        Streak.State s = new Streak.State();
        s.count = (int) in.varint();
        s.best = (int) in.varint();
        s.last = in.varint();
        long m = 0;
        for (int i = 0; i < 8; i++) m = (m << 8) | in.u8();
        s.mask = m;
        int sh = in.u8();
        s.ready = (sh & 1) != 0;
        s.charge = sh >> 1;
        return s;
    }

    /** Never lowers: keeps the newer streak block, takes the larger best. */
    public static Streak.State mergeStreak(Streak.State local, Streak.State remote) {
        Streak.State out = (local.last >= remote.last ? local : remote).copy();
        out.best = Math.max(Math.max(local.best, remote.best), out.count);
        return out;
    }

    // ---- Purrcast ----

    public byte[] toPayload() {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        writeStreak(o, streak);
        writeVarint(o, rewardClaimed);
        return o.toByteArray();
    }

    public static Progress fromPayload(byte[] payload) {
        In in = new In(payload);
        Progress p = new Progress();
        p.streak = readStreak(in);
        p.rewardClaimed = (int) in.varint();
        return p;
    }

    public static Progress merge(Progress local, Progress remote) {
        Progress out = new Progress();
        out.streak = mergeStreak(local.streak, remote.streak);
        out.rewardClaimed = Math.max(local.rewardClaimed, remote.rewardClaimed);
        return out;
    }
}
