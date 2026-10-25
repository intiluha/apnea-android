package dev.apnea.trainer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LogEntry(val label: String, val value: String, val at: Long)

/** Newest-first history is capped at this many entries, both when logging and when importing. */
const val HISTORY_MAX = 40

private fun LogEntry.toJson() = JSONObject().put("label", label).put("value", value).put("at", at)

private fun JSONArray.toHistory() = List(length()) { i ->
    val e = getJSONObject(i)
    LogEntry(e.optString("label"), e.optString("value"), e.optLong("at", 0))
}

/** The export format: a plain JSON array of `{label, value, at}` objects, newest first. */
fun historyToJson(history: List<LogEntry>): String {
    val h = JSONArray()
    history.forEach { h.put(it.toJson()) }
    return h.toString(2)
}

/**
 * Reads an exported history: a bare array, or an object with a "history" array (the saved
 * "apnea.v1" blob also qualifies). Entries without a label are dropped. Returns null if the
 * text isn't one of those shapes.
 */
fun parseHistory(text: String): List<LogEntry>? = try {
    val t = text.trim()
    val arr = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).getJSONArray("history")
    arr.toHistory().filter { it.label.isNotEmpty() }
} catch (e: Exception) {
    null
}

/** Union of both lists without duplicates, newest first, capped at [HISTORY_MAX]. */
fun mergeHistory(current: List<LogEntry>, imported: List<LogEntry>): List<LogEntry> =
    (current + imported).distinct().sortedByDescending { it.at }.take(HISTORY_MAX)

data class Saved(
    val pb: Int = 0,
    val last: Int = 0,
    val count: Int = 0,
    /** Starting estimate used to size tables until a personal best exists. */
    val maxHold: Int = 90,
    val breatheUp: Boolean = true,
    val sound: Boolean = true,
    val vibrate: Boolean = true,
    val history: List<LogEntry> = emptyList(),
) {
    /** Single source of truth for table sizing: personal best, else the estimate. */
    val tableBasis get() = if (pb > 0) pb else maxHold
}

/** Persists [Saved] as one JSON blob, mirroring the web app's localStorage "apnea.v1". */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("apnea", Context.MODE_PRIVATE)

    fun load(): Saved = try {
        val s = JSONObject(prefs.getString(KEY, null) ?: "{}")
        val h = s.optJSONArray("history") ?: JSONArray()
        Saved(
            pb = s.optInt("pb", 0),
            last = s.optInt("last", 0),
            count = s.optInt("count", 0),
            maxHold = s.optInt("maxHold", 90).takeIf { it > 0 } ?: 90,
            breatheUp = s.optBoolean("breatheUp", true),
            sound = s.optBoolean("sound", true),
            vibrate = s.optBoolean("vibrate", true),
            history = h.toHistory(),
        )
    } catch (e: Exception) {
        Saved()
    }

    fun save(s: Saved) {
        val h = JSONArray()
        s.history.forEach { h.put(it.toJson()) }
        val o = JSONObject()
            .put("pb", s.pb).put("last", s.last).put("count", s.count).put("maxHold", s.maxHold)
            .put("breatheUp", s.breatheUp).put("sound", s.sound).put("vibrate", s.vibrate)
            .put("history", h)
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private companion object {
        const val KEY = "apnea.v1"
    }
}
