package xyz.intiluha.apnea

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Port of apnea-trainer's test.js, plus session-engine checks. */
class LogicTest {
    @Test fun formatting() {
        assertEquals("0:00", fmt(0))
        assertEquals("0:05", fmt(5))
        assertEquals("1:05", fmt(65))
        assertEquals("2:05", fmt(125))
        assertEquals("0:00", fmt(-3))
    }

    @Test fun modeCycleWraps() {
        assertEquals(Mode.CO2, Mode.FREE.cycle(1))
        assertEquals(Mode.FREE, Mode.BOX.cycle(1))
        assertEquals(Mode.BOX, Mode.FREE.cycle(-1))
    }

    @Test fun co2Table() {
        val c = genCO2(180)
        assertEquals(8, c.rounds)
        assertEquals(90, c.hold)
        assertEquals(8, c.rests.size)
        assertEquals(120, c.rests[0])
        assertEquals(15, c.rests[7])
        assertTrue(c.rests.zipWithNext().all { (a, b) -> b < a })
        assertEquals(30, genCO2(20).hold)
    }

    @Test fun o2Table() {
        val o = genO2(180)
        assertEquals(120, o.rest)
        assertEquals(8, o.holds.size)
        assertTrue(o.holds.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(o.holds.last() <= 180)
        assertTrue(o.holds[0] < o.holds[7])
    }

    @Test fun freePhases() {
        val pf = buildPhases(Mode.FREE, breatheUp = true, maxHold = 90)
        assertEquals(listOf(Kind.BREATHE_UP, Kind.READY, Kind.HOLD), pf.map { it.kind })
        assertNull(pf[2].dur)
        val pf2 = buildPhases(Mode.FREE, breatheUp = false, maxHold = 90)
        assertEquals(2, pf2.size)
        assertEquals(Kind.READY, pf2[0].kind)
    }

    @Test fun tablePhases() {
        val pc = buildPhases(Mode.CO2, true, 180)
        assertEquals(16, pc.size)
        assertTrue(pc[0].kind == Kind.BREATHE_UP && pc[0].early)
        assertTrue(pc[1].kind == Kind.HOLD && pc[1].dur == 90)
        assertEquals(Kind.HOLD, pc.last().kind)
        assertTrue(pc[15].round == 8 && pc[15].rounds == 8)
    }

    @Test fun boxPhases() {
        val pb = buildPhases(Mode.BOX, true, 0)
        assertEquals(32, pb.size)
        assertEquals(listOf(Kind.INHALE, Kind.HOLD_FULL, Kind.EXHALE, Kind.HOLD_EMPTY), pb.take(4).map { it.kind })
    }

    @Test fun orbBounds() {
        fun near(a: Float, b: Float, eps: Float = 0.001f) = assertTrue("$a !~ $b", abs(a - b) < eps)
        near(orbScale(Kind.INHALE, 0.0, 4), ORB_LO)
        near(orbScale(Kind.INHALE, 4.0, 4), ORB_HI)
        near(orbScale(Kind.EXHALE, 0.0, 4), ORB_HI)
        near(orbScale(Kind.EXHALE, 4.0, 4), ORB_LO)
        assertEquals(ORB_LO, orbScale(Kind.HOLD_EMPTY, 2.0, 4))
        near(orbScale(Kind.HOLD, 1.0, null), ORB_HI, 0.02f)
        var e = 0.0
        while (e <= 16) { val s = orbScale(Kind.BREATHE_UP, e, null); assertTrue(s in 0.5f..1.4f); e += 0.25 }
        assertTrue(orbScale(Kind.READY, 0.0, 4) < orbScale(Kind.READY, 4.0, 4))
    }

    @Test fun freeHoldIsBanked() {
        val outcomes = mutableListOf<Outcome>()
        val cues = mutableListOf<Cue>()
        val s = Session(cues::add, outcomes::add)
        s.start(100.0, breatheUp = true, maxHold = 90)
        assertEquals("Breathe up", s.tick(100.5).label)
        s.skip(110.0) // "Hold now →"
        assertEquals("Final breath", s.tick(111.0).label)
        val f = s.tick(130.0) // ready lasted 4 s -> holding for 16 s
        assertEquals("Hold", f.label)
        assertEquals("0:16", f.timer)
        assertNull(f.ghost)
        s.primary(184.4) // hold started at 114 -> 70.4 s
        assertFalse(s.running)
        assertEquals(listOf<Outcome>(Outcome.FreeHold(70)), outcomes)
        assertEquals("1:10", s.tick(200.0).timer)
        assertEquals(Cue.DONE, cues.last())
    }

    @Test fun tableRunsToCompletionEvenWithMissedFrames() {
        val outcomes = mutableListOf<Outcome>()
        val s = Session({}, outcomes::add)
        s.select(Mode.BOX)
        s.start(0.0, true, 90)
        assertEquals("Cycle 1 / 8", s.tick(1.0).round)
        assertEquals("Exhale", s.tick(9.0).label)
        assertEquals("Cycle 3 / 8", s.tick(33.0).round)
        val done = s.tick(8 * 16.0 + 1)
        assertFalse(done.running)
        assertEquals("Session complete", done.label)
        assertEquals(listOf<Outcome>(Outcome.TableDone(Mode.BOX)), outcomes)
    }

    @Test fun lastThreeSecondsTick() {
        val cues = mutableListOf<Cue>()
        val s = Session(cues::add) {}
        s.select(Mode.CO2)
        s.start(0.0, true, 60)
        cues.clear()
        var t = 0.0
        while (t < 119.9) { s.tick(t); t += 1.0 / 60 }
        assertEquals(listOf(Cue.TICK, Cue.TICK, Cue.TICK), cues)
    }
}
