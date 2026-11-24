package com.hamax.dj

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlin.math.cos
import kotlin.math.sin

class DjViewModel(application: Application) : AndroidViewModel(application) {

    val settings = AppSettings(application)
    val deckA = Deck(application, "A", settings)
    val deckB = Deck(application, "B", settings)
    val sampler = Sampler()

    /** 0 = only deck A, 1 = only deck B. */
    var crossfader by mutableFloatStateOf(0.5f)
        private set

    init {
        settings.onMixerChanged = { applyMix() }
        applyMix()
    }

    fun updateCrossfader(value: Float) {
        crossfader = value.coerceIn(0f, 1f)
        applyMix()
    }

    /** Re-reads the crossfader, its curve and the master volume, and hands the result to both decks. */
    private fun applyMix() {
        val x = if (settings.crossfaderReverse) 1f - crossfader else crossfader
        val gainA: Float
        val gainB: Float
        if (settings.crossfaderCurve == AppSettings.CURVE_SHARP) {
            // "cut" curve: both decks stay at full volume until close to the far end
            gainA = (2f * (1f - x)).coerceIn(0f, 1f)
            gainB = (2f * x).coerceIn(0f, 1f)
        } else {
            // constant power, so the loudness stays even while you sweep across
            val angle = x * (Math.PI / 2.0)
            gainA = cos(angle).toFloat()
            gainB = sin(angle).toFloat()
        }
        deckA.updateCrossfadeGain(gainA)
        deckB.updateCrossfadeGain(gainB)
        deckA.updateMasterGain(settings.masterVolume)
        deckB.updateMasterGain(settings.masterVolume)
        sampler.updateLevel(settings.masterVolume)
    }

    /** Opacity of deck B's picture on top of deck A's, following the crossfader. */
    val videoMix: Float
        get() = if (settings.crossfaderReverse) 1f - crossfader else crossfader

    fun pauseAll() {
        deckA.player.pause()
        deckB.player.pause()
    }

    override fun onCleared() {
        deckA.release()
        deckB.release()
        sampler.release()
    }
}
