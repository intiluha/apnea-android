package dev.apnea.trainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Import/export of the history log as JSON (Store.kt). */
class HistoryJsonTest {
    private val a = LogEntry("Max hold · new best", "1:30", 3000)
    private val b = LogEntry("CO₂ table · complete", "done", 2000)
    private val c = LogEntry("Max hold", "1:10", 1000)

    @Test fun roundTrip() {
        val json = historyToJson(listOf(a, b, c))
        assertEquals(listOf(a, b, c), parseHistory(json))
    }

    @Test fun acceptsSavedBlobAndDropsUnlabelled() {
        val blob = """{"pb":90,"history":[{"label":"Max hold","value":"1:10","at":1000},{"value":"x"}]}"""
        assertEquals(listOf(c), parseHistory(blob))
        assertEquals(emptyList<LogEntry>(), parseHistory("[]"))
    }

    @Test fun rejectsGarbage() {
        assertNull(parseHistory("not json"))
        assertNull(parseHistory("""{"pb":90}"""))
        assertNull(parseHistory("[1,2,3]"))
    }

    @Test fun mergeDedupesSortsAndCaps() {
        assertEquals(listOf(a, b, c), mergeHistory(listOf(c, a), listOf(b, a)))
        val many = List(50) { LogEntry("Max hold", "0:30", it.toLong()) }
        val merged = mergeHistory(listOf(a), many)
        assertEquals(HISTORY_MAX, merged.size)
        assertEquals(a, merged.first())
    }
}
