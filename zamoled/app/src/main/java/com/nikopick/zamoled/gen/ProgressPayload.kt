package com.nikopick.zamoled.gen

import java.io.ByteArrayOutputStream

/**
 * What the Zamoled save code carries: the streak block only (favourites go in the optional backup file).
 * Shared layout with Purrcast's and Maps and Cats' codes: varint count, best, last; 8 bytes of history; 1 shield byte.
 */
fun writeVarint(o: ByteArrayOutputStream, value: Long) {
    var v = value
    while (true) {
        val b = (v and 0x7F).toInt()
        v = v ushr 7
        if (v != 0L) o.write(b or 0x80) else { o.write(b); return }
    }
}

class PayloadReader(private val b: ByteArray) {
    private var p = 0
    fun u8(): Int { require(p < b.size) { "short payload" }; return b[p++].toInt() and 0xFF }
    fun varint(): Long {
        var v = 0L
        var shift = 0
        while (true) {
            val x = u8()
            v = v or ((x and 0x7F).toLong() shl shift)
            if (x and 0x80 == 0) return v
            shift += 7
        }
    }
}

fun zamPayload(s: StreakState): ByteArray {
    val o = ByteArrayOutputStream()
    writeVarint(o, s.count.toLong()); writeVarint(o, s.best.toLong()); writeVarint(o, s.last)
    for (i in 7 downTo 0) o.write(((s.mask shr (i * 8)) and 0xFFuL).toInt())
    o.write((if (s.ready) 1 else 0) or (s.charge shl 1))
    return o.toByteArray()
}

fun zamFromPayload(b: ByteArray): StreakState {
    val r = PayloadReader(b)
    val count = r.varint().toInt()
    val best = r.varint().toInt()
    val last = r.varint()
    var mask = 0uL
    repeat(8) { mask = (mask shl 8) or r.u8().toULong() }
    val sh = r.u8()
    return StreakState(count, best, last, mask, sh and 1 != 0, sh shr 1)
}
