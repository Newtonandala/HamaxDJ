package com.hamax.dj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamax.dj.AppSettings
import com.hamax.dj.Deck

private const val APP_VERSION = "0.2.0"

/**
 * The app's Settings page. It covers the whole screen, saves every change at once on the phone,
 * and closes with DONE or the back button.
 */
@Composable
fun SettingsScreen(settings: AppSettings, onClose: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(HamaxColors.Bg)
            // swallow touches so nothing underneath reacts while Settings is open
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Settings", color = HamaxColors.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                text = "   saved automatically",
                color = HamaxColors.Muted,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            PadButton(
                label = "DONE",
                onClick = onClose,
                modifier = Modifier.size(width = 84.dp, height = 34.dp),
                background = HamaxColors.DeckA,
                foreground = HamaxColors.OnAccent,
                borderColor = HamaxColors.DeckA,
                fontSize = 12.sp
            )
        }

        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // left column
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card("MIXER") {
                    Choice("Crossfader curve", "Smooth keeps the loudness even; Cut keeps both decks full until the far ends.") {
                        Segmented(
                            options = listOf("SMOOTH", "CUT"),
                            selected = settings.crossfaderCurve,
                            onSelect = { settings.updateCrossfaderCurve(it) },
                            accent = HamaxColors.DeckA
                        )
                    }
                    Toggle(
                        title = "Reverse crossfader",
                        note = "Deck A on the right, deck B on the left.",
                        checked = settings.crossfaderReverse,
                        onChange = { settings.updateCrossfaderReverse(it) }
                    )
                    SliderRow(
                        title = "Master volume",
                        valueText = (settings.masterVolume * 100).toInt().toString() + "%",
                        value = settings.masterVolume,
                        range = 0f..1f,
                        onChange = { settings.updateMasterVolume(it) }
                    )
                }

                Card("DECKS") {
                    Choice("Default pitch range", "Used when the app starts. Each deck can still change it with the +/- button.") {
                        Segmented(
                            options = Deck.PITCH_RANGES.map { "±$it%" },
                            selected = Deck.PITCH_RANGES.indexOf(settings.defaultPitchRange).coerceAtLeast(0),
                            onSelect = { settings.updateDefaultPitchRange(Deck.PITCH_RANGES[it]) },
                            accent = HamaxColors.DeckA
                        )
                    }
                    Toggle(
                        title = "Key lock on by default",
                        note = "Keeps the song's pitch the same when you change the tempo.",
                        checked = settings.keyLockDefault,
                        onChange = { settings.updateKeyLockDefault(it) }
                    )
                    SliderRow(
                        title = "Jog wheel sensitivity",
                        valueText = "%.1fx".format(settings.jogSensitivity),
                        value = settings.jogSensitivity,
                        range = 0.5f..2f,
                        onChange = { settings.updateJogSensitivity(it) }
                    )
                }
            }

            // right column
            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card("TRACKS AND WAVEFORM") {
                    Toggle(
                        title = "Analyse tracks when loaded",
                        note = "Finds the tempo and draws the waveform. Long tracks take a few seconds.",
                        checked = settings.autoAnalyze,
                        onChange = { settings.updateAutoAnalyze(it) }
                    )
                    Choice("Waveform zoom", "How many seconds of music the master waveform shows.") {
                        Segmented(
                            options = AppSettings.WAVEFORM_SECONDS.map { "$it s" },
                            selected = AppSettings.WAVEFORM_SECONDS.indexOf(settings.waveformSeconds).coerceAtLeast(0),
                            onSelect = { settings.updateWaveformSeconds(AppSettings.WAVEFORM_SECONDS[it]) },
                            accent = HamaxColors.DeckA
                        )
                    }
                    Toggle(
                        title = "Show beat grid",
                        note = "Beat lines on the waveform once a tempo is known.",
                        checked = settings.showBeatGrid,
                        onChange = { settings.updateShowBeatGrid(it) }
                    )
                }

                Card("LOOK AND SCREEN") {
                    Text("Deck colours", color = HamaxColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        AppSettings.ACCENT_PRESETS.forEachIndexed { index, pair ->
                            val on = index == settings.accentPreset
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(HamaxColors.Control)
                                    .border(
                                        if (on) 2.dp else 1.dp,
                                        if (on) HamaxColors.Text else HamaxColors.Line,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { settings.updateAccentPreset(index) }
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(pair.first))
                                    Box(Modifier.size(16.dp).clip(CircleShape).background(pair.second))
                                }
                                Text(
                                    AppSettings.ACCENT_NAMES[index],
                                    color = HamaxColors.Soft,
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Toggle(
                        title = "Keep screen on",
                        note = "Stops the phone from dimming or locking while you mix.",
                        checked = settings.keepScreenOn,
                        onChange = { settings.updateKeepScreenOn(it) }
                    )
                }

                Card("ABOUT") {
                    Text("HamaxDJ  v$APP_VERSION", color = HamaxColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Two decks for audio and video files, 3-band EQ, colour filter, hot cues, loops, " +
                            "effects, sampler and tempo detection. The headphone cue (PFL) needs a second " +
                            "audio output, which a phone does not have, so it is not included.",
                        color = HamaxColors.Muted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                    if (confirmReset) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            PadButton(
                                label = "YES, RESET",
                                onClick = {
                                    settings.resetToDefaults()
                                    confirmReset = false
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                foreground = HamaxColors.DeckB,
                                borderColor = HamaxColors.DeckB,
                                fontSize = 10.sp
                            )
                            PadButton(
                                label = "CANCEL",
                                onClick = { confirmReset = false },
                                modifier = Modifier.weight(1f).height(32.dp),
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        PadButton(
                            label = "RESET ALL SETTINGS",
                            onClick = { confirmReset = true },
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HamaxColors.Panel)
            .border(1.dp, HamaxColors.PanelBorder, shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SmallLabel(title, color = HamaxColors.DeckA)
        content()
    }
}

@Composable
private fun Choice(title: String, note: String, control: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = HamaxColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(note, color = HamaxColors.Muted, fontSize = 10.sp, lineHeight = 13.sp)
        control()
    }
}

@Composable
private fun Toggle(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = HamaxColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(note, color = HamaxColors.Muted, fontSize = 10.sp, lineHeight = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = HamaxColors.OnAccent,
                checkedTrackColor = HamaxColors.DeckA,
                uncheckedThumbColor = HamaxColors.Soft,
                uncheckedTrackColor = HamaxColors.Control,
                uncheckedBorderColor = HamaxColors.Line
            )
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                title,
                color = HamaxColors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(valueText, color = HamaxColors.DeckA, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = HamaxColors.Text,
                activeTrackColor = HamaxColors.DeckA,
                inactiveTrackColor = HamaxColors.Line
            )
        )
    }
}
