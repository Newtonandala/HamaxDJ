package com.hamax.dj

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import com.hamax.dj.ui.HamaxColors

/** Every user setting. Saved on the phone and shared by the whole app. */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hamaxdj_settings", Context.MODE_PRIVATE)

    /** Set by the view model so the mixer re-reads the crossfader and master volume. */
    var onMixerChanged: (() -> Unit)? = null

    var crossfaderCurve by mutableIntStateOf(prefs.getInt("curve", CURVE_SMOOTH))
        private set
    var crossfaderReverse by mutableStateOf(prefs.getBoolean("reverse", false))
        private set
    var masterVolume by mutableFloatStateOf(prefs.getFloat("master", 0.9f))
        private set
    var defaultPitchRange by mutableIntStateOf(prefs.getInt("pitch_range", 8))
        private set
    var keyLockDefault by mutableStateOf(prefs.getBoolean("key_lock", true))
        private set
    var jogSensitivity by mutableFloatStateOf(prefs.getFloat("jog_sens", 1f))
        private set
    var autoAnalyze by mutableStateOf(prefs.getBoolean("auto_analyze", true))
        private set
    var waveformSeconds by mutableIntStateOf(prefs.getInt("wave_seconds", 8))
        private set
    var showBeatGrid by mutableStateOf(prefs.getBoolean("beat_grid", true))
        private set
    var accentPreset by mutableIntStateOf(prefs.getInt("accent", 0))
        private set
    var keepScreenOn by mutableStateOf(prefs.getBoolean("keep_on", true))
        private set

    init {
        applyAccent()
    }

    fun updateCrossfaderCurve(value: Int) {
        crossfaderCurve = value
        prefs.edit { putInt("curve", value) }
        onMixerChanged?.invoke()
    }

    fun updateCrossfaderReverse(value: Boolean) {
        crossfaderReverse = value
        prefs.edit { putBoolean("reverse", value) }
        onMixerChanged?.invoke()
    }

    fun updateMasterVolume(value: Float) {
        masterVolume = value.coerceIn(0f, 1f)
        prefs.edit { putFloat("master", masterVolume) }
        onMixerChanged?.invoke()
    }

    fun updateDefaultPitchRange(value: Int) {
        defaultPitchRange = value
        prefs.edit { putInt("pitch_range", value) }
    }

    fun updateKeyLockDefault(value: Boolean) {
        keyLockDefault = value
        prefs.edit { putBoolean("key_lock", value) }
    }

    fun updateJogSensitivity(value: Float) {
        jogSensitivity = value.coerceIn(0.5f, 2f)
        prefs.edit { putFloat("jog_sens", jogSensitivity) }
    }

    fun updateAutoAnalyze(value: Boolean) {
        autoAnalyze = value
        prefs.edit { putBoolean("auto_analyze", value) }
    }

    fun updateWaveformSeconds(value: Int) {
        waveformSeconds = value
        prefs.edit { putInt("wave_seconds", value) }
    }

    fun updateShowBeatGrid(value: Boolean) {
        showBeatGrid = value
        prefs.edit { putBoolean("beat_grid", value) }
    }

    fun updateAccentPreset(value: Int) {
        accentPreset = value.coerceIn(0, ACCENT_PRESETS.size - 1)
        prefs.edit { putInt("accent", accentPreset) }
        applyAccent()
    }

    fun updateKeepScreenOn(value: Boolean) {
        keepScreenOn = value
        prefs.edit { putBoolean("keep_on", value) }
    }

    fun resetToDefaults() {
        updateCrossfaderCurve(CURVE_SMOOTH)
        updateCrossfaderReverse(false)
        updateMasterVolume(0.9f)
        updateDefaultPitchRange(8)
        updateKeyLockDefault(true)
        updateJogSensitivity(1f)
        updateAutoAnalyze(true)
        updateWaveformSeconds(8)
        updateShowBeatGrid(true)
        updateAccentPreset(0)
        updateKeepScreenOn(true)
    }

    private fun applyAccent() {
        val pair = ACCENT_PRESETS[accentPreset.coerceIn(0, ACCENT_PRESETS.size - 1)]
        HamaxColors.updateAccents(pair.first, pair.second)
    }

    companion object {
        const val CURVE_SMOOTH = 0
        const val CURVE_SHARP = 1

        val ACCENT_NAMES = listOf("Cyan / Red", "Lime / Magenta", "Amber / Violet", "Teal / Orange")
        val ACCENT_PRESETS = listOf(
            Pair(Color(0xFF2CA4E8), Color(0xFFF0505A)),
            Pair(Color(0xFF7BD34A), Color(0xFFE056C8)),
            Pair(Color(0xFFFFB020), Color(0xFF9B7BFF)),
            Pair(Color(0xFF2DD4BF), Color(0xFFFF8A3D))
        )
        val WAVEFORM_SECONDS = listOf(4, 8, 16)
    }
}
