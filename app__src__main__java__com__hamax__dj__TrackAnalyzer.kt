package com.hamax.dj

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** What we learn about a track: its waveform, its tempo and where a beat falls. */
class TrackAnalysis(
    /** The loudest sample in every 10 ms of the track, 0..1. */
    val peaks: FloatArray,
    /** Beats per minute, or 0 when no steady tempo was found. */
    val bpm: Float,
    /** Time in milliseconds of a beat near the start of the track. */
    val firstBeatMs: Float
)

private class FloatList {
    var data = FloatArray(8192)
    var size = 0

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size] = v
        size++
    }

    fun toArray(): FloatArray = data.copyOf(size)
}

private class Tempo(val bpm: Float, val firstBeatMs: Float)

/**
 * Decodes a track in the background and works out its waveform and tempo.
 * Call analyze() from a worker thread, never from the main thread.
 */
object TrackAnalyzer {

    /** The waveform and tempo curves are built from 10 ms slices. */
    const val HOPS_PER_SECOND = 100
    private const val MAX_SECONDS = 720

    fun analyze(context: Context, uri: Uri): TrackAnalysis? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)

            var trackIndex = -1
            var inFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val m = f.getString(MediaFormat.KEY_MIME)
                if (m != null && m.startsWith("audio/")) {
                    trackIndex = i
                    inFormat = f
                    break
                }
            }
            if (trackIndex < 0 || inFormat == null) return null
            val mime = inFormat.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(trackIndex)

            val dec = MediaCodec.createDecoderByType(mime)
            codec = dec
            dec.configure(inFormat, null, null, 0)
            dec.start()

            val info = MediaCodec.BufferInfo()
            val peaks = FloatList()
            val full = FloatList()
            val low = FloatList()

            var sampleRate = if (inFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            var channels = if (inFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2
            if (channels < 1) channels = 1
            var floatPcm = false
            var lowAlpha = 1f - exp(-2.0 * PI * 200.0 / sampleRate).toFloat()

            // Hops are 10 ms on average even when the sample rate is not a multiple of 100.
            var nextHopEnd = sampleRate / HOPS_PER_SECOND.toDouble()
            var frameCount = 0L
            var hopCount = 0
            var hopPeak = 0f
            var sumFull = 0f
            var sumLow = 0f
            var lowState = 0f

            var sawInputEnd = false
            var done = false
            var idleRounds = 0

            while (!done) {
                if (!sawInputEnd) {
                    val inIdx = dec.dequeueInputBuffer(5000)
                    if (inIdx >= 0) {
                        val inBuf = dec.getInputBuffer(inIdx)
                        val size = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                        if (size < 0) {
                            dec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEnd = true
                        } else {
                            dec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = dec.dequeueOutputBuffer(info, 5000)
                if (outIdx >= 0) {
                    idleRounds = 0
                    val outBuf = dec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.order(ByteOrder.nativeOrder())

                        val mono: FloatArray
                        if (floatPcm) {
                            val fb = outBuf.asFloatBuffer()
                            val frames = fb.remaining() / channels
                            mono = FloatArray(frames)
                            for (fr in 0 until frames) {
                                var sum = 0f
                                for (c in 0 until channels) sum += fb.get()
                                mono[fr] = sum / channels
                            }
                        } else {
                            val sb = outBuf.asShortBuffer()
                            val frames = sb.remaining() / channels
                            mono = FloatArray(frames)
                            for (fr in 0 until frames) {
                                var sum = 0f
                                for (c in 0 until channels) sum += sb.get() / 32768f
                                mono[fr] = sum / channels
                            }
                        }

                        for (v in mono) {
                            lowState += lowAlpha * (v - lowState)
                            val a = abs(v)
                            if (a > hopPeak) hopPeak = a
                            sumFull += v * v
                            sumLow += lowState * lowState
                            hopCount++
                            frameCount++
                            if (frameCount >= nextHopEnd) {
                                peaks.add(min(1f, hopPeak))
                                full.add(sqrt(sumFull / hopCount))
                                low.add(sqrt(sumLow / hopCount))
                                hopCount = 0
                                hopPeak = 0f
                                sumFull = 0f
                                sumLow = 0f
                                nextHopEnd += sampleRate / HOPS_PER_SECOND.toDouble()
                            }
                        }
                    }
                    dec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) done = true
                    if (frameCount > MAX_SECONDS.toLong() * sampleRate) done = true
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = dec.outputFormat
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (channels < 1) channels = 1
                    floatPcm = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    nextHopEnd = frameCount + sampleRate / HOPS_PER_SECOND.toDouble()
                    lowAlpha = 1f - exp(-2.0 * PI * 200.0 / sampleRate).toFloat()
                } else {
                    idleRounds++
                    if (idleRounds > 2000) done = true
                }
            }

            if (peaks.size < 500) return null // shorter than five seconds

            val onset = onsetCurve(low.toArray(), full.toArray())
            val tempo = estimateTempo(onset)
            return TrackAnalysis(peaks.toArray(), tempo.bpm, tempo.firstBeatMs)
        } catch (e: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
                // the decoder may already be stopped
            }
            try {
                codec?.release()
            } catch (e: Exception) {
                // nothing more to free
            }
            extractor.release()
        }
    }

    /** How strongly each 10 ms slice starts a new sound, with the steady background removed. */
    private fun onsetCurve(low: FloatArray, full: FloatArray): FloatArray {
        val n = low.size
        val raw = FloatArray(n)
        for (i in 1 until n) {
            val dl = ln(1.0 + 1000.0 * low[i]) - ln(1.0 + 1000.0 * low[i - 1])
            val df = ln(1.0 + 1000.0 * full[i]) - ln(1.0 + 1000.0 * full[i - 1])
            raw[i] = (max(0.0, dl) + 0.5 * max(0.0, df)).toFloat()
        }

        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + raw[i]

        val out = FloatArray(n)
        val w = 25
        for (i in 0 until n) {
            val a = max(0, i - w)
            val b = min(n, i + w + 1)
            val mean = (prefix[b] - prefix[a]) / (b - a)
            out[i] = max(0f, raw[i] - mean.toFloat() * 1.2f)
        }
        return out
    }

    /**
     * Finds the tempo whose beats line up best with the sound starts.
     * First a coarse sweep from 70 to 180 BPM, then a fine sweep that compares beats
     * up to 32 apart, which pins the tempo down to about a tenth of a BPM.
     */
    private fun estimateTempo(rawOnset: FloatArray): Tempo {
        val n = rawOnset.size
        if (n < 800) return Tempo(0f, 0f)

        // soften the sharp spikes so fractional positions can be read between slices
        val onset = FloatArray(n)
        for (i in 0 until n) {
            val prev = if (i > 0) rawOnset[i - 1] else rawOnset[i]
            val next = if (i < n - 1) rawOnset[i + 1] else rawOnset[i]
            onset[i] = 0.25f * prev + 0.5f * rawOnset[i] + 0.25f * next
        }

        val start = min(1000, n / 6)
        val end = min(n, start + 9000)

        val coarseSpans = intArrayOf(1, 2)
        val coarseWeights = doubleArrayOf(1.0, 0.5)
        var bestBpm = 0.0
        var bestScore = 0.0
        var bpm = 70.0
        while (bpm <= 180.0) {
            val z = ln(bpm / 125.0) / 0.5
            val prior = exp(-0.5 * z * z)
            val score = comb(onset, start, end, 6000.0 / bpm, coarseSpans, coarseWeights) * prior
            if (score > bestScore) {
                bestScore = score
                bestBpm = bpm
            }
            bpm += 0.1
        }
        if (bestBpm <= 0.0) return Tempo(0f, 0f)

        val fineSpans = intArrayOf(4, 8, 16, 32)
        val fineWeights = doubleArrayOf(1.0, 1.0, 1.0, 1.0)
        var fineBest = 0.0
        var fineBpm = bestBpm
        var b = bestBpm - 0.6
        while (b <= bestBpm + 0.6) {
            val score = comb(onset, start, end, 6000.0 / b, fineSpans, fineWeights)
            if (score > fineBest) {
                fineBest = score
                fineBpm = b
            }
            b += 0.02
        }

        var tempo = fineBpm.toFloat()
        val rounded = tempo.roundToInt()
        if (abs(tempo - rounded) < 0.1f) tempo = rounded.toFloat()

        // where, inside one beat, the sound starts fall
        val period = 6000.0 / tempo
        var bestPhase = 0
        var bestPhaseScore = -1.0
        val steps = period.toInt() + 1
        for (p in 0 until steps) {
            var s = 0.0
            var pos = start + p.toDouble()
            while (pos < end - 1) {
                s += sampleAt(onset, pos, end)
                pos += period
            }
            if (s > bestPhaseScore) {
                bestPhaseScore = s
                bestPhase = p
            }
        }
        val firstBeatHop = (start + bestPhase) % period
        return Tempo(tempo, (firstBeatHop * 10.0).toFloat())
    }

    /** How well sound starts repeat at the given beat spacing (in 10 ms slices). */
    private fun comb(
        onset: FloatArray,
        start: Int,
        end: Int,
        lag: Double,
        spans: IntArray,
        weights: DoubleArray
    ): Double {
        var total = 0.0
        for (j in spans.indices) {
            val l = spans[j] * lag
            var s = 0.0
            for (i in start until end) {
                val a = onset[i]
                if (a > 0f) s += a * sampleAt(onset, i + l, end)
            }
            total += weights[j] * s
        }
        return total
    }

    private fun sampleAt(a: FloatArray, pos: Double, end: Int): Double {
        val i0 = floor(pos).toInt()
        if (i0 < 0 || i0 + 1 >= end) return 0.0
        val fr = pos - i0
        return a[i0] * (1.0 - fr) + a[i0 + 1] * fr
    }
}
