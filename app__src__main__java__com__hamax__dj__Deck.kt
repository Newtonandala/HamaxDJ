package com.hamax.dj

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlin.math.abs
import kotlin.math.roundToLong

/** Builds the player's audio output so that this deck's sound processor sits in front of it. */
private class DspRenderersFactory(context: Context, private val dsp: DspProcessor) :
    DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink? {
        return DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf<AudioProcessor>(dsp))
            .build()
    }
}

/**
 * One DJ deck. Plays any audio or video file (every track has sound; tracks that also have
 * pictures feed the video mixer). It also holds this deck's EQ, filter, cues, loops and effects.
 */
class Deck(
    private val context: Context,
    val label: String,
    private val settings: AppSettings
) {

    val dsp = DspProcessor()
    val player: ExoPlayer = ExoPlayer.Builder(context, DspRenderersFactory(context, dsp)).build()

    private val handler = Handler(Looper.getMainLooper())

    var title by mutableStateOf("Empty - tap LOAD")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isLoaded by mutableStateOf(false)
        private set
    var hasVideo by mutableStateOf(false)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var cuePointMs by mutableLongStateOf(0L)
        private set
    var videoAspect by mutableFloatStateOf(16f / 9f)
        private set

    /** 0..1 from bottom to top, 0.5 = original speed. */
    var pitchValue by mutableFloatStateOf(0.5f)
        private set
    var pitchRangePct by mutableIntStateOf(settings.defaultPitchRange)
        private set
    var keyLock by mutableStateOf(settings.keyLockDefault)
        private set

    var channelVolume by mutableFloatStateOf(0.85f)
        private set
    private var crossfadeGain by mutableFloatStateOf(1f)
    private var masterGain by mutableFloatStateOf(1f)

    // ---- mixer knobs (0..1, 0.5 = centre) ----------------------------------------------
    var gainKnob by mutableFloatStateOf(0.5f)
        private set
    var highKnob by mutableFloatStateOf(0.5f)
        private set
    var midKnob by mutableFloatStateOf(0.5f)
        private set
    var lowKnob by mutableFloatStateOf(0.5f)
        private set
    var filterKnob by mutableFloatStateOf(0.5f)
        private set

    /** Smoothed loudness of this deck's sound, 0..1, for the level meter. */
    var vuLevel by mutableFloatStateOf(0f)
        private set

    // ---- analysis ----------------------------------------------------------------------
    var analysisState by mutableIntStateOf(ANALYSIS_IDLE)
        private set

    /** Tempo of the track at normal speed; 0 means unknown. */
    var bpm by mutableFloatStateOf(0f)
        private set
    var firstBeatMs by mutableFloatStateOf(0f)
        private set
    var peaks by mutableStateOf<FloatArray?>(null)
        private set

    // ---- performance state -------------------------------------------------------------
    val hotCues = mutableStateListOf<Long>().apply { repeat(HOT_CUE_COUNT) { add(-1L) } }
    var loopActive by mutableStateOf(false)
        private set
    var loopStartMs by mutableLongStateOf(0L)
        private set
    var loopEndMs by mutableLongStateOf(0L)
        private set
    var loopIndex by mutableIntStateOf(-1)
        private set
    var activeFx by mutableIntStateOf(-1)
        private set

    /** Speed change in percent, for example +3.3 means 3.3 percent faster. */
    val speedPercent: Float
        get() = (pitchValue - 0.5f) * 2f * pitchRangePct

    /** Tempo being heard right now (track tempo with the pitch fader applied). */
    val currentBpm: Float
        get() = if (bpm > 0f) bpm * (1f + speedPercent / 100f) else 0f

    private var previewing = false
    private var jogResume = false
    private var jogPendingMs = 0L
    private var jogLastSeek = 0L

    private var nudgeFactor = 0f
    private var nudgeHeld = false
    private var nudgeLastMove = 0L
    private var brakeHeld = false
    private var brakeFactor = 1f
    private var lastSpeed = -1f
    private var lastPitch = -1f

    private var loopIsRoll = false
    private var rollOrigin = 0L
    private var rollStartUptime = 0L

    private var loadToken = 0
    private var currentUri: Uri? = null
    private val taps = ArrayList<Long>()

    private val loopWatcher = object : Runnable {
        override fun run() {
            if (!loopActive) return
            if (player.isPlaying) {
                val pos = player.currentPosition
                if (pos >= loopEndMs) {
                    if (pos < loopEndMs + 600L) {
                        player.seekTo(loopStartMs)
                    } else {
                        loopActive = false // the playhead was moved well past the loop: let it go
                        loopIndex = -1
                        loopIsRoll = false
                        return
                    }
                }
            }
            handler.postDelayed(this, 5L)
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@Deck.isPlaying = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                val d = player.duration
                if (d != C.TIME_UNSET && d > 0L) durationMs = d
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            hasVideo = tracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            errorMessage = "Can't play this file"
        }
    }

    init {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            false // two decks play at once, so neither may steal audio focus from the other
        )
        player.setSeekParameters(SeekParameters.EXACT)
        player.addListener(listener)
        applyVolume()
        applyPlaybackParams()
    }

    // ---- loading -----------------------------------------------------------------------

    fun load(uri: Uri) {
        errorMessage = null
        title = queryName(uri) ?: "Untitled"
        hasVideo = false
        videoAspect = 16f / 9f
        currentUri = uri
        clearPerformance()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = false
        cuePointMs = 0L
        positionMs = 0L
        durationMs = 0L
        isLoaded = true
        bpm = 0f
        firstBeatMs = 0f
        peaks = null
        pitchValue = 0.5f
        applyPlaybackParams()
        applyVolume()
        if (settings.autoAnalyze) analyze() else analysisState = ANALYSIS_IDLE
    }

    /** Works out the waveform and tempo of the loaded track in the background. */
    fun analyze() {
        val uri = currentUri ?: return
        loadToken++
        val token = loadToken
        analysisState = ANALYSIS_WORKING
        val appContext = context.applicationContext
        val worker = Thread {
            val result = try {
                TrackAnalyzer.analyze(appContext, uri)
            } catch (e: Throwable) {
                null
            }
            handler.post { if (token == loadToken) applyAnalysis(result) }
        }
        worker.isDaemon = true
        worker.priority = Thread.MIN_PRIORITY
        worker.name = "HamaxDJ-analyze-$label"
        worker.start()
    }

    private fun applyAnalysis(result: TrackAnalysis?) {
        if (result == null) {
            analysisState = ANALYSIS_FAILED
            return
        }
        peaks = result.peaks
        bpm = result.bpm
        firstBeatMs = result.firstBeatMs
        analysisState = ANALYSIS_DONE
    }

    private fun queryName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (e: Exception) {
            null
        }
    }

    private fun clearPerformance() {
        exitLoop()
        for (i in 0 until HOT_CUE_COUNT) hotCues[i] = -1L
        endAllFx()
        nudgeFactor = 0f
        taps.clear()
    }

    // ---- transport ---------------------------------------------------------------------

    fun togglePlay() {
        if (!isLoaded) return
        if (player.isPlaying || player.playWhenReady) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(cuePointMs)
            player.play()
        }
    }

    /** CDJ-style cue: while playing it returns to the cue point; while paused on the cue point
     *  it previews until released; while paused elsewhere it sets a new cue point. */
    fun cueDown() {
        if (!isLoaded) return
        if (player.isPlaying) {
            player.pause()
            player.seekTo(cuePointMs)
            positionMs = cuePointMs
        } else if (abs(player.currentPosition - cuePointMs) < 80L) {
            previewing = true
            player.play()
        } else {
            cuePointMs = player.currentPosition
        }
    }

    fun cueUp() {
        if (previewing) {
            previewing = false
            player.pause()
            player.seekTo(cuePointMs)
            positionMs = cuePointMs
        }
    }

    fun seekToFraction(fraction: Float) {
        if (!isLoaded || durationMs <= 0L) return
        seekToMs((durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun seekToMs(target: Long) {
        if (!isLoaded) return
        val upper = if (durationMs > 0L) durationMs else Long.MAX_VALUE
        val t = target.coerceIn(0L, upper)
        player.seekTo(t)
        positionMs = t
    }

    /** Moves the playhead by a number of milliseconds (used when dragging the waveform). */
    fun scrubBy(deltaMs: Long) {
        if (!isLoaded) return
        seekToMs(player.currentPosition + deltaMs)
    }

    // ---- jog wheel ---------------------------------------------------------------------

    fun jogStart() {
        if (!isLoaded) return
        jogResume = player.isPlaying
        if (jogResume) player.pause()
        jogPendingMs = 0L
    }

    fun jogMove(deltaDegrees: Float) {
        if (!isLoaded) return
        jogPendingMs += (deltaDegrees * settings.jogSensitivity / 360f * MS_PER_TURN).toLong()
        val now = SystemClock.uptimeMillis()
        if (now - jogLastSeek >= 45L) {
            flushJog()
            jogLastSeek = now
        }
    }

    fun jogEnd() {
        if (!isLoaded) return
        flushJog()
        if (jogResume) player.play()
        jogResume = false
    }

    private fun flushJog() {
        if (jogPendingMs == 0L) return
        val upper = if (durationMs > 0L) durationMs else Long.MAX_VALUE
        val target = (player.currentPosition + jogPendingMs).coerceIn(0L, upper)
        jogPendingMs = 0L
        player.seekTo(target)
        positionMs = target
    }

    /** The rim of the jog wheel: turning it briefly speeds the track up or slows it down. */
    fun nudgeStart() {
        nudgeHeld = true
        nudgeLastMove = SystemClock.uptimeMillis()
    }

    fun nudgeMove(deltaDegrees: Float) {
        val now = SystemClock.uptimeMillis()
        val dt = (now - nudgeLastMove).coerceAtLeast(8L)
        nudgeLastMove = now
        val target = (deltaDegrees / dt * 0.8f * settings.jogSensitivity).coerceIn(-0.3f, 0.3f)
        nudgeFactor = nudgeFactor * 0.5f + target * 0.5f
        applyPlaybackParams()
    }

    fun nudgeEnd() {
        nudgeHeld = false
    }

    // ---- pitch -------------------------------------------------------------------------

    fun setPitch(value: Float) {
        pitchValue = value.coerceIn(0f, 1f)
        applyPlaybackParams()
    }

    fun resetPitch() = setPitch(0.5f)

    fun cyclePitchRange() {
        val index = PITCH_RANGES.indexOf(pitchRangePct)
        pitchRangePct = PITCH_RANGES[(index + 1) % PITCH_RANGES.size]
        applyPlaybackParams()
    }

    fun toggleKeyLock() {
        keyLock = !keyLock
        applyPlaybackParams()
    }

    private fun totalSpeed(): Float {
        val base = 1f + speedPercent / 100f
        return (base * (1f + nudgeFactor) * brakeFactor).coerceIn(0.05f, 4f)
    }

    private fun applyPlaybackParams() {
        val speed = totalSpeed()
        val pitch = if (keyLock && brakeFactor > 0.99f) 1f else speed
        if (abs(speed - lastSpeed) < 0.0005f && abs(pitch - lastPitch) < 0.0005f) return
        lastSpeed = speed
        lastPitch = pitch
        player.playbackParameters = PlaybackParameters(speed, pitch)
    }

    // ---- tempo tools -------------------------------------------------------------------

    fun beatLengthMs(): Float = if (bpm > 0f) 60000f / bpm else 500f

    /** Where we are inside the current beat, 0..1 (for the beat lights). */
    fun beatPhase(): Float {
        if (bpm <= 0f) return 0f
        val beat = beatLengthMs()
        val x = (positionMs - firstBeatMs) / beat
        return x - kotlin.math.floor(x)
    }

    /** Tap in time with the music; the average of the last few taps becomes this deck's tempo. */
    fun tapTempo() {
        if (!isLoaded) return
        val now = SystemClock.uptimeMillis()
        if (taps.isNotEmpty() && now - taps.last() > 2000L) taps.clear()
        taps.add(now)
        while (taps.size > 7) taps.removeAt(0)
        if (taps.size >= 3) {
            val interval = (taps.last() - taps.first()).toFloat() / (taps.size - 1)
            val heard = 60000f / interval
            if (heard in 40f..300f) {
                bpm = heard / (1f + speedPercent / 100f)
                val beat = beatLengthMs()
                val x = player.currentPosition / beat
                firstBeatMs = (x - kotlin.math.floor(x)) * beat
                analysisState = ANALYSIS_DONE
            }
        }
    }

    /** Fixes a half-time or double-time mistake: a fast reading is halved, a slow one doubled. */
    fun flipHalfDouble() {
        if (bpm <= 0f) return
        if (bpm >= 100f) {
            if (bpm / 2f >= 40f) bpm /= 2f
        } else if (bpm * 2f <= 300f) {
            bpm *= 2f
        }
    }

    /** Matches this deck's tempo to the other deck by moving the pitch fader. */
    fun syncTo(other: Deck): Boolean {
        val target = other.currentBpm
        if (bpm <= 0f || target <= 0f) return false
        var ratio = target / bpm
        while (ratio > 1.5f) ratio /= 2f
        while (ratio < 0.75f) ratio *= 2f
        val pct = (ratio - 1f) * 100f
        val range = PITCH_RANGES.firstOrNull { it >= abs(pct) + 0.01f } ?: PITCH_RANGES.last()
        pitchRangePct = range
        pitchValue = (0.5f + pct / (2f * range)).coerceIn(0f, 1f)
        applyPlaybackParams()
        return true
    }

    // ---- hot cues ----------------------------------------------------------------------

    /** Empty pad: stores the playhead. Pad with a cue: jumps there. */
    fun hotCuePress(index: Int) {
        if (!isLoaded || index !in 0 until HOT_CUE_COUNT) return
        val stored = hotCues[index]
        if (stored < 0L) {
            hotCues[index] = player.currentPosition.coerceAtLeast(0L)
        } else {
            exitLoop()
            seekToMs(stored)
        }
    }

    fun hotCueDelete(index: Int) {
        if (index in 0 until HOT_CUE_COUNT) hotCues[index] = -1L
    }

    // ---- loops -------------------------------------------------------------------------

    fun toggleLoop(index: Int) {
        if (!isLoaded || index !in LOOP_BEATS.indices) return
        if (loopActive && loopIndex == index && !loopIsRoll) {
            exitLoop()
        } else {
            startLoop(LOOP_BEATS[index], index, snap = true, roll = false)
        }
    }

    fun exitLoop() {
        loopActive = false
        loopIndex = -1
        loopIsRoll = false
        handler.removeCallbacks(loopWatcher)
    }

    private fun startLoop(beats: Float, index: Int, snap: Boolean, roll: Boolean) {
        val beatMs = beatLengthMs()
        val pos = player.currentPosition.coerceAtLeast(0L)
        var start = pos
        if (snap && bpm > 0f) {
            val n = Math.round((pos - firstBeatMs) / beatMs)
            start = (firstBeatMs + n * beatMs).roundToLong().coerceAtLeast(0L)
        }
        var end = start + (beats * beatMs).roundToLong()
        if (durationMs > 0L && end > durationMs) end = durationMs
        if (end - start < 20L) return
        handler.removeCallbacks(loopWatcher)
        loopStartMs = start
        loopEndMs = end
        loopIndex = index
        loopIsRoll = roll
        loopActive = true
        handler.post(loopWatcher)
    }

    // ---- effects -----------------------------------------------------------------------

    fun fxDown(index: Int) {
        if (!isLoaded || index !in FX_NAMES.indices) return
        if (activeFx >= 0 && activeFx != index) fxEnd(activeFx)
        activeFx = index
        val realBeatMs = if (currentBpm > 0f) 60000f / currentBpm else 500f
        val speed = 1f + speedPercent / 100f
        when (index) {
            0 -> {
                dsp.echoMs = (realBeatMs * 0.75f * speed).coerceIn(60f, 1400f)
                dsp.echoOn = true
            }
            1 -> {
                dsp.echoMs = (realBeatMs * 0.5f * speed).coerceIn(60f, 1400f)
                dsp.echoOn = true
            }
            2 -> dsp.mute = true
            3 -> brakeHeld = true
            4 -> startRoll(0.25f)
            5 -> startRoll(0.125f)
            6 -> dsp.lowKill = true
            7 -> dsp.highKill = true
        }
    }

    fun fxUp(index: Int) {
        if (activeFx == index) fxEnd(index)
    }

    private fun fxEnd(index: Int) {
        when (index) {
            0, 1 -> dsp.echoOn = false
            2 -> dsp.mute = false
            3 -> brakeHeld = false
            4, 5 -> endRoll()
            6 -> dsp.lowKill = false
            7 -> dsp.highKill = false
        }
        if (activeFx == index) activeFx = -1
    }

    private fun endAllFx() {
        for (i in FX_NAMES.indices) fxEnd(i)
        activeFx = -1
    }

    private fun startRoll(beats: Float) {
        rollOrigin = player.currentPosition.coerceAtLeast(0L)
        rollStartUptime = SystemClock.uptimeMillis()
        startLoop(beats, -1, snap = false, roll = true)
    }

    /** A roll loops a tiny slice while held, then carries on from where the track would have been. */
    private fun endRoll() {
        if (loopActive && loopIsRoll) {
            val elapsed = SystemClock.uptimeMillis() - rollStartUptime
            val target = rollOrigin + (elapsed * totalSpeed()).roundToLong()
            exitLoop()
            seekToMs(target)
        }
    }

    // ---- mixer knobs -------------------------------------------------------------------

    fun updateGainKnob(value: Float) {
        gainKnob = value.coerceIn(0f, 1f)
        dsp.gain = gainKnob * 2f
    }

    fun updateHighKnob(value: Float) {
        highKnob = value.coerceIn(0f, 1f)
        dsp.highDb = knobToDb(highKnob)
    }

    fun updateMidKnob(value: Float) {
        midKnob = value.coerceIn(0f, 1f)
        dsp.midDb = knobToDb(midKnob)
    }

    fun updateLowKnob(value: Float) {
        lowKnob = value.coerceIn(0f, 1f)
        dsp.lowDb = knobToDb(lowKnob)
    }

    fun updateFilterKnob(value: Float) {
        filterKnob = value.coerceIn(0f, 1f)
        val f = (filterKnob - 0.5f) * 2f
        dsp.filter = if (abs(f) < 0.04f) 0f else f
    }

    /** Down to -30 dB (a full kill at the very bottom), up to +6 dB, centre = unchanged. */
    private fun knobToDb(v: Float): Float {
        if (v <= 0.01f) return -45f
        return if (v < 0.5f) (v - 0.5f) * 2f * 30f else (v - 0.5f) * 2f * 6f
    }

    // ---- volume ------------------------------------------------------------------------

    fun updateChannelVolume(value: Float) {
        channelVolume = value.coerceIn(0f, 1f)
        applyVolume()
    }

    fun updateCrossfadeGain(gain: Float) {
        crossfadeGain = gain
        applyVolume()
    }

    fun updateMasterGain(gain: Float) {
        masterGain = gain
        applyVolume()
    }

    /** What the level meter should show: the sound level after the channel fader and crossfader. */
    val outputLevel: Float
        get() = (vuLevel * channelVolume * crossfadeGain).coerceIn(0f, 1f)

    private fun applyVolume() {
        player.volume = (channelVolume * crossfadeGain * masterGain).coerceIn(0f, 1f)
    }

    // ---- housekeeping ------------------------------------------------------------------

    /** Called about 30 times a second from the UI: clocks, meters, nudge and brake. */
    fun refresh() {
        if (!isLoaded) {
            vuLevel = 0f
            return
        }
        positionMs = player.currentPosition.coerceAtLeast(0L)
        val d = player.duration
        if (d != C.TIME_UNSET && d > 0L) durationMs = d

        val raw = if (player.isPlaying) dsp.peak else 0f
        vuLevel = if (raw > vuLevel) raw else vuLevel * 0.82f

        var changed = false
        if (nudgeFactor != 0f && (!nudgeHeld || SystemClock.uptimeMillis() - nudgeLastMove > 70L)) {
            nudgeFactor *= 0.7f
            if (abs(nudgeFactor) < 0.01f) nudgeFactor = 0f
            changed = true
        }
        if (brakeHeld && brakeFactor > 0.05f) {
            brakeFactor = (brakeFactor - 0.07f).coerceAtLeast(0.05f)
            changed = true
        } else if (!brakeHeld && brakeFactor < 1f) {
            brakeFactor = (brakeFactor + 0.12f).coerceAtMost(1f)
            changed = true
        }
        if (changed) applyPlaybackParams()
    }

    fun release() {
        loadToken++
        handler.removeCallbacksAndMessages(null)
        player.removeListener(listener)
        player.release()
    }

    companion object {
        /** A vinyl record at 33 1/3 rpm turns once every 1.8 seconds. */
        const val MS_PER_TURN = 1800f
        val PITCH_RANGES = listOf(6, 8, 10, 16, 50)

        const val HOT_CUE_COUNT = 8
        val LOOP_BEATS = listOf(0.25f, 0.5f, 1f, 2f, 4f, 8f, 16f, 32f)
        val LOOP_NAMES = listOf("1/4", "1/2", "1", "2", "4", "8", "16", "32")
        val FX_NAMES = listOf(
            "ECHO", "ECHO 1/2", "CUT", "BRAKE", "ROLL 1/4", "ROLL 1/8", "LOW KILL", "HIGH KILL"
        )

        const val ANALYSIS_IDLE = 0
        const val ANALYSIS_WORKING = 1
        const val ANALYSIS_DONE = 2
        const val ANALYSIS_FAILED = 3
    }
}
