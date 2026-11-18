package app.skypix.core;

import java.io.ByteArrayOutputStream;

/**
 * The progress save code: a short, typo-proof text a person can keep in Notes, a screenshot, a calendar or a file.
 * Format and vectors: docs/save-code-vectors.json and docs/superpowers/specs/2026-11-17-progress-save-code-design.md.
 * Pure Java, no Android; the same format is ported to Zamoled (Kotlin) and Maps and Cats (JS).
 */
public final class SaveCode {
    private SaveCode() {}

    public static final int OK = 0, WRONG_APP = 1, TYPO = 2, NOT_FOUND = 3;
    public static final String PRC = "PRC1", ZAM = "ZAM1", MAC = "MAC1";
    private static final String[] PREFIXES = {PRC, ZAM, MAC};
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    public static final class Result {
        public final int kind;
        /** The payload without its CRC (only for OK). */
        public final byte[] payload;
        /** The prefix of the app the code belongs to (only for WRONG_APP). */
        public final String otherPrefix;

        Result(int kind, byte[] payload, String otherPrefix) {
            this.kind = kind;
            this.payload = payload;
            this.otherPrefix = otherPrefix;
        }
    }

    /** How many varints follow the streak block for each app (Purrcast: rewards; Zamoled: none; Maps and Cats: xp, caught). */
    private static int extras(String prefix) {
        return PRC.equals(prefix) ? 1 : MAC.equals(prefix) ? 2 : 0;
    }

    public static int crc16(byte[] b, int len) {
        int c = 0xFFFF;
        for (int i = 0; i < len; i++) {
            c ^= (b[i] & 0xFF) << 8;
            for (int k = 0; k < 8; k++) c = (c & 0x8000) != 0 ? ((c << 1) ^ 0x1021) & 0xFFFF : (c << 1) & 0xFFFF;
        }
        return c;
    }

    public static String encode(String prefix, byte[] payload) {
        int crc = crc16(payload, payload.length);
        byte[] full = new byte[payload.length + 2];
        System.arraycopy(payload, 0, full, 0, payload.length);
        full[payload.length] = (byte) (crc >> 8);
        full[payload.length + 1] = (byte) crc;
        StringBuilder chars = new StringBuilder();
        int acc = 0, bits = 0;
        for (int i = 0; i < full.length; i++) {
            acc = (acc << 8) | (full[i] & 0xFF);
            bits += 8;
            while (bits >= 5) {
                chars.append(ALPHABET.charAt((acc >> (bits - 5)) & 31));
                bits -= 5;
            }
            acc &= (1 << bits) - 1;
        }
        if (bits > 0) chars.append(ALPHABET.charAt((acc << (5 - bits)) & 31));
        StringBuilder out = new StringBuilder(prefix);
        for (int i = 0; i < chars.length(); i += 4) {
            out.append('-').append(chars, i, Math.min(chars.length(), i + 4));
        }
        return out.toString();
    }

    private static char norm(char c) {
        c = Character.toUpperCase(c);
        if (c == 'O') return '0';
        if (c == 'I' || c == 'L') return '1';
        return c;
    }

    private static boolean wordChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** The index where [prefix] starts in [s] (case-insensitive, O/I/L read as 0/1/1), or -1. One pass, no backtracking. */
    private static int find(String s, String prefix) {
        int n = s.length();
        for (int i = 0; i + prefix.length() <= n; i++) {
            if (i > 0 && wordChar(s.charAt(i - 1))) continue;
            boolean ok = true;
            for (int k = 0; k < prefix.length(); k++) {
                if (norm(s.charAt(i + k)) != norm(prefix.charAt(k))) { ok = false; break; }
            }
            if (ok) return i;
        }
        return -1;
    }

    /** Reads code characters after the prefix, five bits each, skipping dashes and white space. */
    private static final class Reader {
        final String s;
        int pos;
        int acc, bits;
        boolean bad;

        Reader(String s, int pos) {
            this.s = s;
            this.pos = pos;
        }

        /** The next byte, or -1 when the text ends or holds a character that cannot be in a code. */
        int nextByte() {
            while (bits < 8) {
                int v = -1;
                while (pos < s.length()) {
                    char c = s.charAt(pos++);
                    if (c == '-' || c == ' ' || c == '\n' || c == '\r' || c == '\t') continue;
                    v = ALPHABET.indexOf(norm(c));
                    if (v < 0) bad = true;
                    break;
                }
                if (v < 0) return -1;
                acc = (acc << 5) | v;
                bits += 5;
            }
            int b = (acc >> (bits - 8)) & 0xFF;
            bits -= 8;
            acc &= (1 << bits) - 1;
            return b;
        }
    }

    public static Result decode(String pasted, String prefix) {
        if (pasted == null || pasted.length() == 0) return new Result(NOT_FOUND, null, null);
        int at = find(pasted, prefix);
        if (at < 0) {
            for (int i = 0; i < PREFIXES.length; i++) {
                if (!PREFIXES[i].equals(prefix) && find(pasted, PREFIXES[i]) >= 0) return new Result(WRONG_APP, null, PREFIXES[i]);
            }
            return new Result(NOT_FOUND, null, null);
        }
        Reader r = new Reader(pasted, at + prefix.length());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int varints = 3 + extras(prefix);
        for (int v = 0; v < varints; v++) {
            if (v == 3) {
                // the 8-byte history and the shield byte sit between the streak varints and the extras
                for (int i = 0; i < 9; i++) {
                    int b = r.nextByte();
                    if (b < 0) return new Result(TYPO, null, null);
                    out.write(b);
                }
            }
            for (int i = 0; ; i++) {
                int b = r.nextByte();
                if (b < 0 || i > 9) return new Result(TYPO, null, null);
                out.write(b);
                if ((b & 0x80) == 0) break;
            }
        }
        if (varints == 3) {
            for (int i = 0; i < 9; i++) {
                int b = r.nextByte();
                if (b < 0) return new Result(TYPO, null, null);
                out.write(b);
            }
        }
        int hi = r.nextByte(), lo = r.nextByte();
        if (hi < 0 || lo < 0) return new Result(TYPO, null, null);
        byte[] payload = out.toByteArray();
        if (crc16(payload, payload.length) != ((hi << 8) | lo)) return new Result(TYPO, null, null);
        return new Result(OK, payload, null);
    }
}
