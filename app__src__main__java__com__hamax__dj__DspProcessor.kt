package com.hamax.dj

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The sound path of one deck, sitting inside its ExoPlayer:
 * gain, 3-band EQ (low shelf 320 Hz, mid 1 kHz, high shelf 3.2 kHz),
 * a colour filter (left = low-pass, right = high-pass), echo and a level meter.
 *
 * The controls are plain volatile fields: the screen writes them and the audio thread reads them.
 */
class DspProcessor : BaseAudioProcessor() {

    /** Input gain, 0..2 (1 = unchanged). */
    @Volatile
    var gain = 1f

    @Volatile
    var lowDb = 0f

    @Volatile
    var midDb = 0f

    @Volatile
    var highDb = 0f

    /** Colour filter, -1 (low-pass closed) through 0 (off) to +1 (high-pass closed). */
    @Volatile
    var filter = 0f

    @Volatile
    var lowKill = false

    @Volatile
    var highKill = false

    @Volatile
    var mute = false

    @Volatile
    var echoOn = false

    @Volatile
    var echoMs = 375f

    /** Loudest sample in the most recent block of audio, 0..1. */
    @Volatile
    var peak = 0f
        private set

    private class Biquad {
        var b0 = 1.0
        var b1 = 0.0
        var b2 = 0.0
        var a1 = 0.0
        var a2 = 0.0
        var active = false
    }

    private val stages = Array(STAGE_COUNT) { Biquad() }
    private var sampleRate = 44100
    private var channels = 2
    private var z1 = DoubleArray(0)
    private var z2 = DoubleArray(0)
    private var echoBuf = FloatArray(0)
    private var echoLen = 0
    private var echoPos = 0

    private var cLow = Float.NaN
    private var cMid = Float.NaN
    private var cHigh = Float.NaN
    private var cFilter = Float.NaN
    private var cRate = -1

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount < 1 ||
            inputAudioFormat.channelCount > 8
        ) {
            // other formats pass through untouched
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channels = inputAudioFormat.channelCount
        z1 = DoubleArray(STAGE_COUNT * channels)
        z2 = DoubleArray(STAGE_COUNT * channels)
        echoLen = (sampleRate * 3 / 2).coerceAtLeast(2)
        echoBuf = FloatArray(echoLen * channels)
        echoPos = 0
        cRate = -1
        return inputAudioFormat
    }

    override fun onFlush() {
        z1.fill(0.0)
        z2.fill(0.0)
        echoBuf.fill(0f)
        echoPos = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        refreshCoefficients()

        val ch = channels
        val frames = remaining / (2 * ch)
        val out = replaceOutputBuffer(frames * 2 * ch)
        val g = if (mute) 0.0 else gain.toDouble()
        val echoActive = echoLen > 1
        val delayFrames = if (echoActive) {
            (echoMs * sampleRate / 1000f).toInt().coerceIn(1, echoLen - 1)
        } else 1
        val recording = echoOn
        var blockPeak = 0f

        for (f in 0 until frames) {
            val readFrame = if (echoActive) (echoPos - delayFrames + echoLen) % echoLen else 0
            for (c in 0 until ch) {
                var x = inputBuffer.getShort() / 32768.0 * g
                for (s in 0 until STAGE_COUNT) {
                    val st = stages[s]
                    if (!st.active) continue
                    val i = s * ch + c
                    val y = st.b0 * x + z1[i]
                    z1[i] = st.b1 * x - st.a1 * y + z2[i]
                    z2[i] = st.b2 * x - st.a2 * y
                    x = y
                }
                var xf = x.toFloat()
                if (echoActive) {
                    val d = echoBuf[readFrame * ch + c]
                    echoBuf[echoPos * ch + c] = (if (recording) xf else 0f) + d * ECHO_FEEDBACK
                    xf += d * ECHO_WET
                }
                val a = abs(xf)
                if (a > blockPeak) blockPeak = a
                out.putShort((xf * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            }
            if (echoActive) {
                echoPos++
                if (echoPos >= echoLen) echoPos = 0
            }
        }

        inputBuffer.position(inputBuffer.limit())
        out.flip()
        peak = if (blockPeak > 1f) 1f else blockPeak
    }

    /** Recomputes the filter coefficients, but only when a control or the sample rate changed. */
    private fun refreshCoefficients() {
        val lo = if (lowKill) -45f else lowDb
        val hi = if (highKill) -45f else highDb
        val md = midDb
        val fl = filter
        if (lo == cLow && md == cMid && hi == cHigh && fl == cFilter && sampleRate == cRate) return
        cLow = lo
        cMid = md
        cHigh = hi
        cFilter = fl
        cRate = sampleRate

        designLowShelf(stages[0], 320.0, lo)
        designPeak(stages[1], 1000.0, md)
        designHighShelf(stages[2], 3200.0, hi)

        val limit = sampleRate * 0.45
        if (fl < -0.02f) {
            val t = ((-fl - 0.02f) / 0.98f).coerceIn(0f, 1f).toDouble()
            val cutoff = (18000.0 * (250.0 / 18000.0).pow(t)).coerceAtMost(limit)
            designLowPass(stages[3], cutoff)
            designLowPass(stages[4], cutoff)
        } else if (fl > 0.02f) {
            val t = ((fl - 0.02f) / 0.98f).coerceIn(0f, 1f).toDouble()
            val cutoff = (30.0 * (6000.0 / 30.0).pow(t)).coerceAtMost(limit)
            designHighPass(stages[3], cutoff)
            designHighPass(stages[4], cutoff)
        } else {
            stages[3].active = false
            stages[4].active = false
        }
    }

    // The formulas below are the standard "audio EQ cookbook" biquads.

    private fun designLowShelf(s: Biquad, f0: Double, dB: Float) {
        if (abs(dB) < 0.05f) {
            s.active = false
            return
        }
        val a = 10.0.pow(dB / 40.0)
        val w0 = 2.0 * PI * f0 / sampleRate
        val cosw = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val sqA = sqrt(a)
        val a0 = (a + 1) + (a - 1) * cosw + 2 * sqA * alpha
        s.b0 = a * ((a + 1) - (a - 1) * cosw + 2 * sqA * alpha) / a0
        s.b1 = 2 * a * ((a - 1) - (a + 1) * cosw) / a0
        s.b2 = a * ((a + 1) - (a - 1) * cosw - 2 * sqA * alpha) / a0
        s.a1 = -2 * ((a - 1) + (a + 1) * cosw) / a0
        s.a2 = ((a + 1) + (a - 1) * cosw - 2 * sqA * alpha) / a0
        s.active = true
    }

    private fun designHighShelf(s: Biquad, f0: Double, dB: Float) {
        if (abs(dB) < 0.05f) {
            s.active = false
            return
        }
        val a = 10.0.pow(dB / 40.0)
        val w0 = 2.0 * PI * f0 / sampleRate
        val cosw = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val sqA = sqrt(a)
        val a0 = (a + 1) - (a - 1) * cosw + 2 * sqA * alpha
        s.b0 = a * ((a + 1) + (a - 1) * cosw + 2 * sqA * alpha) / a0
        s.b1 = -2 * a * ((a - 1) + (a + 1) * cosw) / a0
        s.b2 = a * ((a + 1) + (a - 1) * cosw - 2 * sqA * alpha) / a0
        s.a1 = 2 * ((a - 1) - (a + 1) * cosw) / a0
        s.a2 = ((a + 1) - (a - 1) * cosw - 2 * sqA * alpha) / a0
        s.active = true
    }

    private fun designPeak(s: Biquad, f0: Double, dB: Float) {
        if (abs(dB) < 0.05f) {
            s.active = false
            return
        }
        val a = 10.0.pow(dB / 40.0)
        val w0 = 2.0 * PI * f0 / sampleRate
        val cosw = cos(w0)
        val alpha = sin(w0) / (2.0 * 0.9)
        val a0 = 1 + alpha / a
        s.b0 = (1 + alpha * a) / a0
        s.b1 = -2 * cosw / a0
        s.b2 = (1 - alpha * a) / a0
        s.a1 = -2 * cosw / a0
        s.a2 = (1 - alpha / a) / a0
        s.active = true
    }

    private fun designLowPass(s: Biquad, cutoff: Double) {
        val w0 = 2.0 * PI * cutoff / sampleRate
        val cosw = cos(w0)
        val alpha = sin(w0) / (2.0 * 0.7071)
        val a0 = 1 + alpha
        s.b0 = (1 - cosw) / 2 / a0
        s.b1 = (1 - cosw) / a0
        s.b2 = s.b0
        s.a1 = -2 * cosw / a0
        s.a2 = (1 - alpha) / a0
        s.active = true
    }

    private fun designHighPass(s: Biquad, cutoff: Double) {
        val w0 = 2.0 * PI * cutoff / sampleRate
        val cosw = cos(w0)
        val alpha = sin(w0) / (2.0 * 0.7071)
        val a0 = 1 + alpha
        s.b0 = (1 + cosw) / 2 / a0
        s.b1 = -(1 + cosw) / a0
        s.b2 = s.b0
        s.a1 = -2 * cosw / a0
        s.a2 = (1 - alpha) / a0
        s.active = true
    }

    companion object {
        private const val STAGE_COUNT = 5
        private const val ECHO_FEEDBACK = 0.5f
        private const val ECHO_WET = 0.55f
    }
}
