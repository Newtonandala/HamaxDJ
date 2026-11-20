package com.hamax.dj

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs

/**
 * One DJ deck. Plays any audio or video file (audio and video are handled the same way:
 * every track has sound, and tracks that also have pictures feed the video mixer).
 */
class Deck(private val context: Context, val label: String) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

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
    var pitchRangePct by mutableIntStateOf(8)
        private set
    var keyLock by mutableStateOf(true)
        private set

    var channelVolume by mutableFloatStateOf(0.85f)
        private set
    private var crossfadeGain by mutableFloatStateOf(1f)

    /** Speed change in percent, for example +3.3 means 3.3 percent faster. */
    val speedPercent: Float
        get() = (pitchValue - 0.5f) * 2f * pitchRangePct

    private var previewing = false
    private var jogResume = false
    private var jogPendingMs = 0L
    private var jogLastSeek = 0L

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
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.playWhenReady = false
        cuePointMs = 0L
        positionMs = 0L
        durationMs = 0L
        isLoaded = true
        applyPlaybackParams()
        applyVolume()
    }

    private fun queryName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (e: Exception) {
            null
        }
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
        val target = (durationMs * fraction.coerceIn(0f, 1f)).toLong()
        player.seekTo(target)
        positionMs = target
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
        jogPendingMs += (deltaDegrees / 360f * MS_PER_TURN).toLong()
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

    private fun applyPlaybackParams() {
        val speed = 1f + speedPercent / 100f
        player.playbackParameters = PlaybackParameters(speed, if (keyLock) 1f else speed)
    }

    // ---- volume ------------------------------------------------------------------------

    fun setChannelVolume(value: Float) {
        channelVolume = value.coerceIn(0f, 1f)
        applyVolume()
    }

    fun setCrossfadeGain(gain: Float) {
        crossfadeGain = gain
        applyVolume()
    }

    private fun applyVolume() {
        player.volume = (channelVolume * crossfadeGain).coerceIn(0f, 1f)
    }

    // ---- housekeeping ------------------------------------------------------------------

    /** Called about 30 times a second from the UI to keep the clock readouts and jog wheel moving. */
    fun refresh() {
        if (!isLoaded) return
        positionMs = player.currentPosition.coerceAtLeast(0L)
        val d = player.duration
        if (d != C.TIME_UNSET && d > 0L) durationMs = d
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }

    companion object {
        /** A vinyl record at 33 1/3 rpm turns once every 1.8 seconds. */
        const val MS_PER_TURN = 1800f
        val PITCH_RANGES = listOf(6, 8, 10, 16, 50)
    }
}
