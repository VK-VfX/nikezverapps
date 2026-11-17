package com.nikopick.zamoled.data

import android.content.Context
import com.nikopick.zamoled.gen.StreakState
import com.nikopick.zamoled.gen.shieldLabel
import com.nikopick.zamoled.gen.streakStep
import java.util.TimeZone

/** Zamoled's own day streak, kept in the app's preferences. Nothing leaves the phone. */
object StreakStore {
    private const val FILE = "zamoled"

    fun dayNumber(now: Long = System.currentTimeMillis()): Long = (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L

    fun read(context: Context): StreakState {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return StreakState(
            count = p.getInt("streak_count", 0),
            best = p.getInt("streak_best", 0),
            last = p.getLong("streak_last", 0),
            mask = p.getString("streak_mask", "0")?.toULongOrNull() ?: 0uL,
            ready = p.getBoolean("shield_ready", true),
            charge = p.getInt("shield_charge", 0),
        )
    }

    fun write(context: Context, s: StreakState) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt("streak_count", s.count).putInt("streak_best", s.best).putLong("streak_last", s.last)
            .putString("streak_mask", s.mask.toString()).putBoolean("shield_ready", s.ready).putInt("shield_charge", s.charge).apply()
    }

    /** Call when the app comes to the front. */
    @Synchronized
    fun touch(context: Context): StreakState {
        val before = read(context)
        val after = streakStep(before, dayNumber(), Trio.allInstalled(context))
        if (after != before) write(context, after)
        return after
    }

    /** What the Cat Club shows next to the streak: "day streak" plus the shield state when it is on. */
    fun label(context: Context): String {
        val shield = shieldLabel(read(context), Trio.allInstalled(context))
        return if (shield.isEmpty()) "day streak" else "day streak · $shield"
    }

    /** "Shield ready" / "Shield recharging, N days to go", or "" when it is off. */
    fun shieldLine(context: Context): String =
        shieldLabel(read(context), Trio.allInstalled(context)).replaceFirstChar { it.uppercase() }
}
