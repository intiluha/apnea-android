package dev.apnea.trainer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class LogEntry(val label: String, val value: String, val at: Long)

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
            history = List(h.length()) { i ->
                val e = h.getJSONObject(i)
                LogEntry(e.optString("label"), e.optString("value"), e.optLong("at", 0))
            },
        )
    } catch (e: Exception) {
        Saved()
    }

    fun save(s: Saved) {
        val h = JSONArray()
        s.history.forEach { h.put(JSONObject().put("label", it.label).put("value", it.value).put("at", it.at)) }
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
