package com.nikopick.zamoled.gen

/**
 * The progress save code (the same format as Purrcast's Java and Maps and Cats' JS; all three are checked against
 * docs/save-code-vectors.json). Pure Kotlin, no Android. Lives in gen/ because the preview and desktop projects compile that folder.
 */
sealed class SaveCodeResult {
    class Ok(val payload: ByteArray) : SaveCodeResult()
    class WrongApp(val other: String) : SaveCodeResult()
    object Typo : SaveCodeResult()
    object NotFound : SaveCodeResult()
}

object SaveCode {
    const val PRC = "PRC1"
    const val ZAM = "ZAM1"
    const val MAC = "MAC1"
    private val PREFIXES = listOf(PRC, ZAM, MAC)
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    private fun extras(prefix: String) = when (prefix) { PRC -> 1; MAC -> 2; else -> 0 }

    fun crc16(b: ByteArray, len: Int = b.size): Int {
        var c = 0xFFFF
        for (i in 0 until len) {
            c = c xor ((b[i].toInt() and 0xFF) shl 8)
            repeat(8) { c = if (c and 0x8000 != 0) ((c shl 1) xor 0x1021) and 0xFFFF else (c shl 1) and 0xFFFF }
        }
        return c
    }

    fun encode(prefix: String, payload: ByteArray): String {
        val crc = crc16(payload)
        val full = payload + byteArrayOf((crc shr 8).toByte(), crc.toByte())
        val chars = StringBuilder()
        var acc = 0
        var bits = 0
        for (x in full) {
            acc = (acc shl 8) or (x.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                chars.append(ALPHABET[(acc shr (bits - 5)) and 31])
                bits -= 5
            }
            acc = acc and ((1 shl bits) - 1)
        }
        if (bits > 0) chars.append(ALPHABET[(acc shl (5 - bits)) and 31])
        return prefix + chars.chunked(4).joinToString("") { "-$it" }
    }

    private fun norm(c: Char): Char = when (val u = c.uppercaseChar()) { 'O' -> '0'; 'I', 'L' -> '1'; else -> u }

    private fun wordChar(c: Char) = c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z'

    private fun find(s: String, prefix: String): Int {
        for (i in 0..s.length - prefix.length) {
            if (i > 0 && wordChar(s[i - 1])) continue
            var ok = true
            for (k in prefix.indices) if (norm(s[i + k]) != norm(prefix[k])) { ok = false; break }
            if (ok) return i
        }
        return -1
    }

    private class Reader(val s: String, var pos: Int) {
        var acc = 0
        var bits = 0

        fun nextByte(): Int {
            while (bits < 8) {
                var v = -1
                while (pos < s.length) {
                    val c = s[pos++]
                    if (c == '-' || c == ' ' || c == '\n' || c == '\r' || c == '\t') continue
                    v = ALPHABET.indexOf(norm(c))
                    break
                }
                if (v < 0) return -1
                acc = (acc shl 5) or v
                bits += 5
            }
            val b = (acc shr (bits - 8)) and 0xFF
            bits -= 8
            acc = acc and ((1 shl bits) - 1)
            return b
        }
    }

    fun decode(pasted: String, prefix: String): SaveCodeResult {
        if (pasted.isEmpty()) return SaveCodeResult.NotFound
        val at = find(pasted, prefix)
        if (at < 0) {
            for (p in PREFIXES) if (p != prefix && find(pasted, p) >= 0) return SaveCodeResult.WrongApp(p)
            return SaveCodeResult.NotFound
        }
        val r = Reader(pasted, at + prefix.length)
        val out = java.io.ByteArrayOutputStream()
        val varints = 3 + extras(prefix)
        for (v in 0 until varints) {
            if (v == 3) repeat(9) { val b = r.nextByte(); if (b < 0) return SaveCodeResult.Typo; out.write(b) }
            var i = 0
            while (true) {
                val b = r.nextByte()
                if (b < 0 || i > 9) return SaveCodeResult.Typo
                out.write(b)
                if (b and 0x80 == 0) break
                i++
            }
        }
        if (varints == 3) repeat(9) { val b = r.nextByte(); if (b < 0) return SaveCodeResult.Typo; out.write(b) }
        val hi = r.nextByte()
        val lo = r.nextByte()
        if (hi < 0 || lo < 0) return SaveCodeResult.Typo
        val payload = out.toByteArray()
        return if (crc16(payload) == ((hi shl 8) or lo)) SaveCodeResult.Ok(payload) else SaveCodeResult.Typo
    }
}
