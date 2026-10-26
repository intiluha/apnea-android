package xyz.intiluha.apnea

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

enum class Cue { HOLD, REST, TICK, DONE, INHALE }

/** Soft synthesized tones (same pitches as the web app) plus haptics, so you can train eyes-closed. */
class Cues(context: Context) {
    var sound = true
    var vibrate = true

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)

    private val audio = Executors.newSingleThreadExecutor()
    private val rate = 44100

    fun play(cue: Cue) {
        if (sound) audio.execute { tones(cue) }
        if (vibrate) buzz(cue)
    }

    private fun tones(cue: Cue) = when (cue) {
        Cue.HOLD -> tone(196.0, 0.5, 0.16)
        Cue.REST -> tone(392.0, 0.4, 0.12)
        Cue.TICK -> tone(660.0, 0.08, 0.08)
        Cue.DONE -> { tone(523.0, 0.18, 0.14); tone(784.0, 0.4, 0.14) }
        Cue.INHALE -> tone(440.0, 0.25, 0.09, triangle = true)
    }

    private fun buzz(cue: Cue) {
        val v = vibrator ?: return
        val effect = when (cue) {
            Cue.HOLD -> VibrationEffect.createOneShot(350, 200)
            Cue.REST -> VibrationEffect.createWaveform(longArrayOf(0, 90, 110, 90), intArrayOf(0, 160, 0, 160), -1)
            Cue.TICK -> VibrationEffect.createOneShot(25, 90)
            Cue.DONE -> VibrationEffect.createWaveform(longArrayOf(0, 80, 90, 80, 90, 200), intArrayOf(0, 180, 0, 180, 0, 220), -1)
            Cue.INHALE -> VibrationEffect.createOneShot(60, 120)
        }
        v.vibrate(effect)
    }

    /** Blocking: renders and plays one enveloped tone (20 ms attack, exponential decay). */
    private fun tone(freq: Double, dur: Double, gain: Double, triangle: Boolean = false) {
        val n = ((dur + 0.02) * rate).toInt()
        val pcm = ShortArray(n)
        val attack = 0.02 * rate
        val decayK = kotlin.math.ln(gain / 0.0001) / ((dur - 0.02) * rate)
        for (i in 0 until n) {
            val env = if (i < attack) gain * i / attack else gain * exp(-decayK * (i - attack))
            val ph = freq * i / rate
            val w = if (triangle) 1 - 4 * abs(ph - kotlin.math.floor(ph + 0.5)) else sin(2 * PI * ph)
            pcm[i] = (min(1.0, env) * w * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(n * 2)
            .build()
        try {
            track.write(pcm, 0, n)
            track.play()
            Thread.sleep((dur * 1000).toLong() + 30)
        } catch (_: Exception) {
        } finally {
            track.release()
        }
    }

    fun close() = audio.shutdownNow()
}
