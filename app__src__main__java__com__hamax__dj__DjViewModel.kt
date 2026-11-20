package com.hamax.dj

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlin.math.cos
import kotlin.math.sin

class DjViewModel(application: Application) : AndroidViewModel(application) {

    val deckA = Deck(application, "A")
    val deckB = Deck(application, "B")

    /** 0 = only deck A, 1 = only deck B. */
    var crossfader by mutableFloatStateOf(0.5f)
        private set

    init {
        applyCrossfader()
    }

    fun updateCrossfader(value: Float) {
        crossfader = value.coerceIn(0f, 1f)
        applyCrossfader()
    }

    /** Constant-power curve so the loudness stays even while you sweep across. */
    private fun applyCrossfader() {
        val angle = crossfader * (Math.PI / 2.0)
        deckA.updateCrossfadeGain(cos(angle).toFloat())
        deckB.updateCrossfadeGain(sin(angle).toFloat())
    }

    fun pauseAll() {
        deckA.player.pause()
        deckB.player.pause()
    }

    override fun onCleared() {
        deckA.release()
        deckB.release()
    }
}
