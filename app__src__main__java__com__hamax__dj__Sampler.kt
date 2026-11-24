package com.hamax.dj

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Eight DJ sounds made from maths, so the app needs no sound files.
 * Each one is built the first time it is played and kept ready after that.
 */
class Sampler {

    private val tracks = arrayOfNulls<AudioTrack>(NAMES.size)
    private var level = 0.9f

    fun updateLevel(value: Float) {
        level = value.coerceIn(0f, 1f)
    }

    fun trigger(index: Int) {
        if (index < 0 || index >= NAMES.size) return
        try {
            val track = tracks[index] ?: build(index).also { tracks[index] = it }
            track.stop()
            track.setPlaybackHeadPosition(0)
            track.setVolume(level)
            track.play()
        } catch (e: Exception) {
            // a sound that cannot play is skipped
        }
    }

    fun release() {
        for (i in tracks.indices) {
            try {
                tracks[i]?.release()
            } catch (e: Exception) {
                // already released
            }
            tracks[i] = null
        }
    }

    private fun build(index: Int): AudioTrack {
        val data = synth(index)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(data.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(data, 0, data.size)
        return track
    }

    private fun synth(index: Int): ShortArray {
        val f = when (index) {
            0 -> horn()
            1 -> siren()
            2 -> riser()
            3 -> kick()
            4 -> snare()
            5 -> clap()
            6 -> laser()
            else -> boom()
        }
        return ShortArray(f.size) { i -> (f[i].coerceIn(-1f, 1f) * 30000f).toInt().toShort() }
    }

    private fun horn(): FloatArray {
        val n = (0.9 * RATE).toInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            val attack = min(1.0, t / 0.01)
            val release = if (t > 0.75) (0.9 - t) / 0.15 else 1.0
            val vib = 1.0 + 0.004 * sin(2.0 * PI * 6.0 * t)
            var s = 0.0
            for (h in 1..8) {
                s += sin(2.0 * PI * 440.0 * vib * h * t) / h
                s += 0.6 * sin(2.0 * PI * 660.0 * vib * h * t) / h
            }
            (s * attack * release * 0.3).toFloat()
        }
    }

    private fun siren(): FloatArray {
        val n = (1.6 * RATE).toInt()
        var phase = 0.0
        return FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            val freq = 800.0 + 350.0 * sin(2.0 * PI * 1.25 * t)
            phase += 2.0 * PI * freq / RATE
            val env = min(1.0, t / 0.03) * min(1.0, (1.6 - t) / 0.1)
            (sin(phase) * env * 0.5).toFloat()
        }
    }

    private fun riser(): FloatArray {
        val dur = 2.0
        val n = (dur * RATE).toInt()
        val rnd = Random(3)
        var phase = 0.0
        return FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            val p = t / dur
            val freq = 150.0 * exp(p * 2.8)
            phase += 2.0 * PI * freq / RATE
            val noise = rnd.nextFloat() * 2f - 1f
            val env = p * min(1.0, (dur - t) / 0.05)
            ((sin(phase) * 0.45 + noise * 0.25 * p) * env).toFloat()
        }
    }

    private fun kick(): FloatArray {
        val n = (0.35 * RATE).toInt()
        var phase = 0.0
        return FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            val freq = 45.0 + 105.0 * exp(-t * 22.0)
            phase += 2.0 * PI * freq / RATE
            (sin(phase) * exp(-t * 9.0) * 0.95).toFloat()
        }
    }

    private fun snare(): FloatArray {
        val n = (0.3 * RATE).toInt()
        val rnd = Random(5)
        return FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            val noise = (rnd.nextFloat() * 2f - 1f).toDouble()
            (noise * exp(-t * 18.0) * 0.7 + sin(2.0 * PI * 190.0 * t) * exp(-t * 25.0) * 0.5).toFloat()
        }
    }

    private fun clap(): FloatArray {
        val n = (0.3 * RATE).toInt()
        val rnd = Random(7)
        val bursts = doubleArrayOf(0.0, 0.012, 0.026)
        return FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            val noise = (rnd.nextFloat() * 2f - 1f).toDouble()
            var env = exp(-t * 14.0) * 0.3
            for (b in bursts) {
                if (t >= b) env += exp(-(t - b) * 70.0) * 0.5
            }
            (noise * env).toFloat()
        }
    }

    private fun laser(): FloatArray {
        val n = (0.4 * RATE).toInt()
        var phase = 0.0
        return FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            val freq = 200.0 + 2400.0 * exp(-t * 7.0)
            phase += 2.0 * PI * freq / RATE
            (sin(phase) * exp(-t * 4.0) * 0.6).toFloat()
        }
    }

    private fun boom(): FloatArray {
        val n = (1.4 * RATE).toInt()
        val rnd = Random(11)
        var lp = 0.0
        return FloatArray(n) { i ->
            val t = i.toDouble() / RATE
            val noise = (rnd.nextFloat() * 2f - 1f).toDouble()
            lp += 0.08 * (noise - lp)
            ((lp * 4.0) * exp(-t * 3.0) * 0.6 + sin(2.0 * PI * 55.0 * t) * exp(-t * 2.5) * 0.6).toFloat()
        }
    }

    companion object {
        const val RATE = 44100
        val NAMES = listOf("HORN", "SIREN", "RISER", "KICK", "SNARE", "CLAP", "LASER", "BOOM")
    }
}
