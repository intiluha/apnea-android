package xyz.intiluha.apnea

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

    @Test fun holdSecondsParsesOnlyTimes() {
        assertEquals(90, a.holdSeconds())
        assertEquals(70, c.holdSeconds())
        assertNull(b.holdSeconds())
        assertNull(LogEntry("Max hold", "1:5", 1).holdSeconds())
    }

    @Test fun importRecomputesStatsFromHistory() {
        val fresh = Saved().withImported(listOf(c, a, b))
        assertEquals(90, fresh.pb)
        assertEquals(90, fresh.maxHold)
        assertEquals(90, fresh.last)
        assertEquals(3, fresh.count)
        assertEquals(listOf(a, b, c), fresh.history)
    }

    @Test fun importKeepsBetterSavedStatsAndCountsOnlyNewEntries() {
        val newer = LogEntry("Max hold", "1:20", 4000)
        val s = Saved(pb = 120, last = 80, count = 7, maxHold = 120, history = listOf(newer, a))
        val next = s.withImported(listOf(a, b, c))
        assertEquals(120, next.pb)
        assertEquals(120, next.maxHold)
        assertEquals(80, next.last)
        assertEquals(9, next.count)
        assertEquals(listOf(newer, a, b, c), next.history)
        // Importing the same file again changes nothing.
        assertEquals(next, next.withImported(listOf(a, b, c)))
    }

    @Test fun importCountsEntriesTheCapDrops() {
        val many = List(HISTORY_MAX) { LogEntry("Max hold", "0:30", 1000L + HISTORY_MAX - it) }
        val s = Saved(count = HISTORY_MAX, history = many)
        val next = s.withImported(listOf(LogEntry("Max hold", "0:20", 5)))
        assertEquals(HISTORY_MAX + 1, next.count)
        assertEquals(many, next.history)
    }
}
