package com.nikopick.zamoled.data

import android.content.Context
import com.nikopick.zamoled.gen.SaveCode
import com.nikopick.zamoled.gen.SaveCodeResult
import com.nikopick.zamoled.gen.mergeStreak
import com.nikopick.zamoled.gen.zamFromPayload
import com.nikopick.zamoled.gen.zamPayload
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Zamoled's progress save code and optional backup file. Counters in the code; favourites and a few settings in the file. */
object ProgressStore {
    private const val FILE = "zamoled"
    private val SETTINGS = listOf("auto_on", "auto_source", "auto_target", "auto_update", "likes", "hero_on")

    fun code(context: Context): String = SaveCode.encode(SaveCode.ZAM, zamPayload(StreakStore.read(context)))

    /** Reads a pasted code (or a whole note) and merges it. Never lowers anything. Throws with a friendly message. */
    fun restoreFromText(context: Context, pasted: String): String {
        when (val r = SaveCode.decode(pasted, SaveCode.ZAM)) {
            SaveCodeResult.Typo -> throw IllegalArgumentException("That code has a typo. Check the characters near the end.")
            is SaveCodeResult.WrongApp -> throw IllegalArgumentException("That code belongs to ${appFor(r.other)}.")
            SaveCodeResult.NotFound -> throw IllegalArgumentException("I couldn't find a Zamoled code in that.")
            is SaveCodeResult.Ok -> {
                val remote = runCatching { zamFromPayload(r.payload) }.getOrNull()
                    ?: throw IllegalArgumentException("That code has a typo. Check the characters near the end.")
                val merged = mergeStreak(StreakStore.read(context), remote)
                StreakStore.write(context, merged)
                return "Restored: ${merged.count} day streak, best ${merged.best}."
            }
        }
    }

    private fun appFor(prefix: String) = when (prefix) { SaveCode.PRC -> "Purrcast"; SaveCode.MAC -> "Maps and Cats"; else -> "another app" }

    fun backupJson(context: Context): String {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val o = JSONObject()
        o.put("app", "zamoled").put("format", 1).put("saved", SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
        o.put("progress", code(context))
        o.put("favorites", JSONArray((p.getStringSet("favorites", emptySet()) ?: emptySet()).sorted()))
        val s = JSONObject()
        val all = p.all
        for (k in SETTINGS) {
            val v = all[k] ?: continue
            s.put(k, if (v is Set<*>) JSONArray(v.map { it.toString() }) else v)
        }
        o.put("settings", s)
        return o.toString(2)
    }

    fun restoreBackupJson(context: Context, json: String, withSettings: Boolean): String {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: throw IllegalArgumentException("That file could not be read.")
        if (o.optString("app") != "zamoled") throw IllegalArgumentException("That file is not a Zamoled backup.")
        val msg = restoreFromText(context, o.optString("progress"))
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val have = (p.getStringSet("favorites", emptySet()) ?: emptySet()).toMutableSet()
        val before = have.size
        o.optJSONArray("favorites")?.let { a -> for (i in 0 until a.length()) have.add(a.getString(i)) }
        val e = p.edit().putStringSet("favorites", have)
        if (withSettings) {
            val s = o.optJSONObject("settings")
            if (s != null) for (k in SETTINGS) {
                if (!s.has(k)) continue
                when (val v = s.get(k)) {
                    is Boolean -> e.putBoolean(k, v)
                    is Int -> e.putInt(k, v)
                    is String -> e.putString(k, v)
                    is JSONArray -> e.putStringSet(k, (0 until v.length()).map { v.getString(it) }.toSet())
                }
            }
        }
        e.apply()
        val added = have.size - before
        return msg + if (added > 0) " $added favorite${if (added == 1) "" else "s"} added." else ""
    }
}
