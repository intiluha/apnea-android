package xyz.intiluha.apnea

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// Pure training logic, ported from apnea-trainer's index.html. Covered by LogicTest.

enum class Mode(val title: String, val tag: String, val hint: String) {
    FREE("Max Hold", "FREE", "Sit or lie down · relax your shoulders"),
    CO2("CO₂ Table", "TOLERANCE", "Builds tolerance to the urge to breathe"),
    O2("O₂ Table", "CAPACITY", "Builds your max breath-hold capacity"),
    BOX("Box", "CALM", "4-4-4-4 · calm your nervous system");

    /** The mode [step] tabs away, wrapping around (swipe navigation). */
    fun cycle(step: Int) = entries[(ordinal + step).mod(entries.size)]
}

enum class Kind { BREATHE_UP, REST, READY, HOLD, INHALE, HOLD_FULL, EXHALE, HOLD_EMPTY;
    /** Seafoam orb/ring: recovery and exhale-side phases. */
    val cool get() = this == REST || this == BREATHE_UP || this == EXHALE || this == HOLD_EMPTY
}

data class Phase(
    val kind: Kind,
    /** Seconds; null = open-ended count-up hold. */
    val dur: Int?,
    val label: String,
    val round: Int = 0,
    val rounds: Int = 0,
    /** Phase may be ended early ("Hold now →"). */
    val early: Boolean = false,
)

data class Co2Table(val rounds: Int, val hold: Int, val rests: List<Int>)
data class O2Table(val rounds: Int, val rest: Int, val holds: List<Int>)

// JS Math.round semantics (half rounds up) so tables match the web app exactly.
private fun jsRound(x: Double): Int = kotlin.math.floor(x + 0.5).toInt()

fun fmt(sec: Double): String {
    val s = max(0, jsRound(sec))
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

fun fmt(sec: Int) = fmt(sec.toDouble())

fun genCO2(maxSec: Int): Co2Table {
    val rounds = 8
    val hold = max(30, jsRound(maxSec * 0.5 / 5) * 5)
    val rests = List(rounds) { max(15, 120 - it * 15) }
    return Co2Table(rounds, hold, rests)
}

fun genO2(maxSec: Int): O2Table {
    val rounds = 8
    val start = max(20, jsRound(maxSec * 0.4))
    val end = max(start + 20, jsRound(maxSec * 0.85))
    val holds = List(rounds) { jsRound(start + (end - start) * it / (rounds - 1.0)) }
    return O2Table(rounds, 120, holds)
}

fun buildPhases(mode: Mode, breatheUp: Boolean, maxHold: Int): List<Phase> {
    val p = mutableListOf<Phase>()
    when (mode) {
        Mode.FREE -> {
            if (breatheUp) p += Phase(Kind.BREATHE_UP, 40, "Breathe up", early = true)
            p += Phase(Kind.READY, 4, "Final breath")
            p += Phase(Kind.HOLD, null, "Hold")
        }
        Mode.CO2 -> {
            val t = genCO2(maxHold)
            for (i in 0 until t.rounds) {
                p += restPhase(i, t.rests[i], t.rounds)
                p += Phase(Kind.HOLD, t.hold, "Hold", i + 1, t.rounds)
            }
        }
        Mode.O2 -> {
            val t = genO2(maxHold)
            for (i in 0 until t.rounds) {
                p += restPhase(i, t.rest, t.rounds)
                p += Phase(Kind.HOLD, t.holds[i], "Hold", i + 1, t.rounds)
            }
        }
        Mode.BOX -> {
            val cycles = 8
            for (i in 1..cycles) {
                p += Phase(Kind.INHALE, 4, "Inhale", i, cycles)
                p += Phase(Kind.HOLD_FULL, 4, "Hold", i, cycles)
                p += Phase(Kind.EXHALE, 4, "Exhale", i, cycles)
                p += Phase(Kind.HOLD_EMPTY, 4, "Hold", i, cycles)
            }
        }
    }
    return p
}

private fun restPhase(i: Int, dur: Int, rounds: Int) =
    if (i == 0) Phase(Kind.BREATHE_UP, dur, "Breathe up", 1, rounds, early = true)
    else Phase(Kind.REST, dur, "Recover", i + 1, rounds)

/** Planned (rest, hold) seconds per round for the table modes. */
fun schedule(mode: Mode, maxHold: Int): List<Pair<Int, Int>> = when (mode) {
    Mode.CO2 -> genCO2(maxHold).let { t -> t.rests.map { it to t.hold } }
    Mode.O2 -> genO2(maxHold).let { t -> t.holds.map { t.rest to it } }
    else -> emptyList()
}

const val ORB_LO = 0.62f
const val ORB_HI = 1.32f

fun easeInOut(x: Double) = if (x < 0.5) 2 * x * x else 1 - (-2 * x + 2).pow(2) / 2

/** Orb scale for a phase, [elapsed] seconds into it. */
fun orbScale(kind: Kind, elapsed: Double, dur: Int?): Float {
    val prog = if (dur != null && dur > 0) min(1.0, elapsed / dur) else 0.0
    return when (kind) {
        // calm 4s-in / 4s-out cycle
        Kind.BREATHE_UP, Kind.REST -> (0.97 - 0.35 * cos(2 * PI * elapsed / 8)).toFloat()
        Kind.READY, Kind.INHALE -> (ORB_LO + (ORB_HI - ORB_LO) * easeInOut(prog)).toFloat()
        Kind.EXHALE -> (ORB_HI - (ORB_HI - ORB_LO) * easeInOut(prog)).toFloat()
        Kind.HOLD, Kind.HOLD_FULL -> (ORB_HI + 0.012 * sin(elapsed * 2.2)).toFloat()
        Kind.HOLD_EMPTY -> ORB_LO
    }
}

fun breathWord(kind: Kind, elapsed: Double): String =
    if (kind == Kind.BREATHE_UP || kind == Kind.REST) {
        if (elapsed % 8 < 4) "breathe in" else "breathe out"
    } else ""
