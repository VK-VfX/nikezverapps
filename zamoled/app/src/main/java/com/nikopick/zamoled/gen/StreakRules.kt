package com.nikopick.zamoled.gen

/**
 * The day streak and the Streak Shield: pure rules, no Android. The very same rules run in Purrcast (Java) and Maps and Cats (JS);
 * all three are checked against docs/streak-vectors.json so they cannot drift apart. (This file lives in gen/ only because the
 * preview and desktop projects already compile that folder.)
 *
 * A visit is the app coming to the front; the day is the phone's local calendar day. The shield saves a gap of 1 to 7 missed
 * days, once, and recharges after 7 visit days in a row. It only works while all three apps are installed.
 */
const val STREAK_RECHARGE_DAYS = 7
const val STREAK_MAX_SAVED_GAP = 8

data class StreakState(
    val count: Int = 0,
    val best: Int = 0,
    /** Day number of the last visit; 0 means never. */
    val last: Long = 0,
    /** 64-day history, bit 0 = the last visit day. */
    val mask: ULong = 0uL,
    val ready: Boolean = true,
    val charge: Int = 0,
)

/** The streak after a visit on [day]; the same state when nothing changes (same day, or the clock went backwards). */
fun streakStep(s: StreakState, day: Long, shieldOn: Boolean): StreakState {
    if (s.last != 0L && day <= s.last) return s
    val gap = if (s.last == 0L) 0L else day - s.last
    var count = s.count
    var ready = s.ready
    var charge = s.charge
    when {
        gap == 1L -> {
            count++
            if (!ready) {
                charge++
                if (charge >= STREAK_RECHARGE_DAYS) { ready = true; charge = 0 }
            }
        }
        gap in 2..STREAK_MAX_SAVED_GAP && shieldOn && s.ready -> { count++; ready = false; charge = 0 }
        else -> { count = 1; if (!ready) charge = 0 }
    }
    val delta = if (s.last == 0L) 64L else gap
    val mask = if (delta >= 64) 1uL else ((s.mask shl delta.toInt()) or 1uL)
    return StreakState(count, maxOf(s.best, count), day, mask, ready, charge)
}

/** "" when the shield is off, else "shield ready" or "shield recharging, N days to go". */
fun shieldLabel(s: StreakState, shieldOn: Boolean): String = when {
    !shieldOn -> ""
    s.ready -> "shield ready"
    else -> "shield recharging, ${STREAK_RECHARGE_DAYS - s.charge} days to go"
}

/** An old history with only the 64-day mask: its streak is the run of set bits from bit 0. */
fun streakFromLegacy(mask: ULong): StreakState {
    var run = 0
    while (run < 64 && ((mask shr run) and 1uL) != 0uL) run++
    return StreakState(count = run, best = run, mask = mask)
}
