package dev.apnea.trainer

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the stage shows for one frame. */
data class Frame(
    val label: String,
    val hint: String,
    val timer: String,
    val orbScale: Float,
    /** Ring fill, 0..1. */
    val ring: Float,
    val cool: Boolean,
    val round: String?,
    val ghost: String?,
    val running: Boolean = false,
)

/** Outcomes the host must persist. */
sealed interface Outcome {
    data class FreeHold(val seconds: Int) : Outcome
    data class TableDone(val mode: Mode) : Outcome
}

/**
 * Phase sequencer, driven by [tick] with a monotonic clock in seconds. Android-free so it is
 * unit-testable; mirrors the web app's enterPhase/loop/advance/stopSession.
 */
class Session(
    private val cue: (Cue) -> Unit,
    private val onOutcome: (Outcome) -> Unit,
) {
    var mode = Mode.FREE
        private set
    var running = false
        private set

    private var phases: List<Phase> = emptyList()
    private var idx = 0
    private var phaseStart = 0.0
    private var lastTick = -1
    private var idle: Frame = idleFrame()

    val phase get() = phases.getOrNull(idx)

    fun select(m: Mode) {
        if (running) stop()
        mode = m
        idle = idleFrame()
    }

    fun start(now: Double, breatheUp: Boolean, maxHold: Int) {
        phases = buildPhases(mode, breatheUp, maxHold)
        if (phases.isEmpty()) return
        running = true
        enter(0, now)
    }

    /** User abort ("End session"), or switching modes mid-session. */
    fun stop() {
        running = false
        idle = idleFrame()
    }

    /** Primary button: stop-and-bank during a max hold, otherwise abort. */
    fun primary(now: Double) {
        val ph = phase
        if (running && mode == Mode.FREE && ph?.kind == Kind.HOLD) finishFree(now - phaseStart)
        else stop()
    }

    /** Ghost button: skip / hold now / next. */
    fun skip(now: Double) {
        if (running) advance(now)
    }

    private fun enter(n: Int, now: Double) {
        idx = n
        phaseStart = now
        lastTick = -1
        val k = phases[n].kind
        cue(
            when {
                k == Kind.HOLD || k == Kind.HOLD_FULL -> Cue.HOLD
                k.cool -> Cue.REST
                else -> Cue.INHALE
            }
        )
    }

    private fun advance(now: Double) {
        val ph = phases[idx]
        if (mode == Mode.FREE && ph.kind == Kind.HOLD) {
            finishFree(now - phaseStart); return
        }
        if (idx + 1 >= phases.size) {
            running = false
            idle = Frame("Session complete", "well held — recover fully", "✓", 0.85f, 0f, false, null, null)
            onOutcome(Outcome.TableDone(mode))
            cue(Cue.DONE)
            return
        }
        enter(idx + 1, now)
    }

    private fun finishFree(sec: Double) {
        val v = sec.roundToInt()
        running = false
        idle = Frame("Hold complete", "recover with slow, easy breaths", fmt(v), 0.85f, 0f, false, null, null)
        onOutcome(Outcome.FreeHold(v))
        cue(Cue.DONE)
    }

    /** Called when the host knows the free hold was a new PB (it owns persisted state). */
    fun markNewBest() {
        idle = idle.copy(label = "New personal best")
    }

    /** Advance timers and produce the frame to draw. */
    fun tick(now: Double): Frame {
        if (!running) return idle
        var ph = phases[idx]
        var elapsed = now - phaseStart
        // Catch up if frames were missed (e.g. screen briefly off): finish every elapsed phase.
        while (ph.dur != null && elapsed >= ph.dur) {
            val endAt = phaseStart + ph.dur
            advance(endAt)
            if (!running) return idle
            ph = phases[idx]
            elapsed = now - phaseStart
        }

        val ring: Float
        val timer: String
        val dur = ph.dur
        if (dur != null) {
            ring = min(1.0, elapsed / dur).toFloat()
            val whole = ceil(max(0.0, dur - elapsed)).toInt()
            timer = fmt(whole)
            // tick on the last 3 seconds
            if (whole in 1..3 && whole != lastTick) { lastTick = whole; cue(Cue.TICK) }
        } else {
            // open-ended hold: count up, ring nearly full and gently breathing
            timer = fmt(floor(elapsed))
            ring = (1 - (0.12 + 0.02 * (0.5 + 0.5 * sin(elapsed * 1.4)))).toFloat()
            val mark = floor(elapsed).toInt()
            if (mark > 0 && mark % 30 == 0 && mark != lastTick) { lastTick = mark; cue(Cue.TICK) }
        }

        val hint = breathWord(ph.kind, elapsed).ifEmpty {
            when (ph.kind) {
                Kind.HOLD -> "let contractions come"
                Kind.READY -> "big, calm inhale"
                else -> ""
            }
        }
        val ghost = when {
            ph.early -> if (ph.kind == Kind.BREATHE_UP) "Hold now →" else "Skip →"
            ph.kind == Kind.REST -> "Skip →"
            ph.kind == Kind.HOLD && ph.dur != null -> "Next →"
            else -> null
        }
        val round = if (ph.rounds > 0) "${if (mode == Mode.BOX) "Cycle" else "Round"} ${ph.round} / ${ph.rounds}" else null
        return Frame(ph.label, hint, timer, orbScale(ph.kind, elapsed, ph.dur), ring, ph.kind.cool, round, ghost, running = true)
    }

    private fun idleFrame() = Frame("Ready when you are", mode.hint, "0:00", 0.85f, 0f, false, null, null)
}
