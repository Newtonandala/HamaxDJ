package com.hamax.dj.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamax.dj.AppSettings
import com.hamax.dj.Deck
import com.hamax.dj.DjViewModel
import kotlinx.coroutines.delay
import kotlin.math.sqrt

@Composable
fun HamaxDjApp(viewModel: DjViewModel) {
    val context = LocalContext.current
    val settings = viewModel.settings
    var pendingDeck by remember { mutableStateOf<Deck?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showFull by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val target = pendingDeck
        if (uri != null && target != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Not every provider allows a persistent grant; playing right now still works.
            }
            target.load(uri)
        }
        pendingDeck = null
    }

    fun openFor(deck: Deck) {
        pendingDeck = deck
        picker.launch(arrayOf("audio/*", "video/*"))
    }

    // the "keep screen on" setting
    val view = LocalView.current
    val keepOn = settings.keepScreenOn
    SideEffect { view.keepScreenOn = keepOn }

    BackHandler(enabled = showSettings || showFull) {
        if (showSettings) showSettings = false else showFull = false
    }

    // keeps clocks, meters and jog wheels moving
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.deckA.refresh()
            viewModel.deckB.refresh()
            delay(33L)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(HamaxColors.Bg)
            .safeDrawingPadding()
    ) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            TopRow(
                viewModel = viewModel,
                onSettings = { showSettings = true },
                onFull = { showFull = true }
            )
            Row(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                DeckPanel(
                    deck = viewModel.deckA,
                    other = viewModel.deckB,
                    sampler = viewModel.sampler,
                    accent = HamaxColors.DeckA,
                    mirrored = false,
                    onLoad = { openFor(viewModel.deckA) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                MixerPanel(
                    viewModel = viewModel,
                    showFull = showFull,
                    onFull = { showFull = true },
                    onSettings = { showSettings = true },
                    modifier = Modifier.width(232.dp).fillMaxHeight()
                )
                DeckPanel(
                    deck = viewModel.deckB,
                    other = viewModel.deckA,
                    sampler = viewModel.sampler,
                    accent = HamaxColors.DeckB,
                    mirrored = true,
                    onLoad = { openFor(viewModel.deckB) },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        if (showFull) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showFull = false }
            ) {
                VideoStage(
                    deckA = viewModel.deckA,
                    deckB = viewModel.deckB,
                    crossfader = viewModel.videoMix,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    text = "Tap anywhere to close",
                    color = HamaxColors.Muted,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(6.dp)
                )
            }
        }

        if (showSettings) {
            SettingsScreen(settings = settings, onClose = { showSettings = false })
        }
    }
}

/** Title, the master waveform, and the two buttons that open the full-screen video and Settings. */
@Composable
private fun TopRow(viewModel: DjViewModel, onSettings: () -> Unit, onFull: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Hamax", color = HamaxColors.Text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Text("DJ", color = HamaxColors.DeckA, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
        WaveformStrip(
            deckA = viewModel.deckA,
            deckB = viewModel.deckB,
            settings = viewModel.settings,
            modifier = Modifier.weight(1f),
            height = 54.dp
        )
        Column(Modifier.width(78.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PadButton(
                label = "VIDEO",
                onClick = onFull,
                modifier = Modifier.fillMaxWidth().height(25.dp),
                fontSize = 9.sp
            )
            PadButton(
                label = "SETTINGS",
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth().height(25.dp),
                fontSize = 9.sp
            )
        }
    }
}

private val MIXER_TABS = listOf("AUDIO", "VIDEO", "MASTER")

@Composable
private fun MixerPanel(
    viewModel: DjViewModel,
    showFull: Boolean,
    onFull: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    var tab by remember { mutableIntStateOf(0) }
    Column(
        modifier
            .clip(shape)
            .background(HamaxColors.Panel)
            .border(1.dp, HamaxColors.PanelBorder, shape)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(24.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            MIXER_TABS.forEachIndexed { index, name ->
                val on = index == tab
                val tabShape = RoundedCornerShape(5.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(tabShape)
                        .background(if (on) HamaxColors.Text else HamaxColors.Control)
                        .border(1.dp, if (on) HamaxColors.Text else HamaxColors.Line, tabShape)
                        .clickable(role = Role.Tab) { tab = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name,
                        color = if (on) HamaxColors.OnAccent else HamaxColors.Soft,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> AudioTab(viewModel)
                1 -> VideoTab(viewModel, showFull, onFull)
                else -> MasterTab(viewModel, onSettings)
            }
        }

        // crossfader
        Row(
            Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("A", color = HamaxColors.DeckA, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Fader(
                value = viewModel.crossfader,
                onValueChange = { viewModel.updateCrossfader(it) },
                vertical = false,
                accent = Color.White,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                snapToCenter = true,
                showCenterTick = true,
                onDoubleTap = { viewModel.updateCrossfader(0.5f) }
            )
            Text("B", color = HamaxColors.DeckB, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun AudioTab(viewModel: DjViewModel) {
    Row(
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        KnobColumn(viewModel.deckA, HamaxColors.DeckA, Modifier.width(40.dp).fillMaxHeight())
        Row(
            Modifier.weight(1f).fillMaxHeight(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ChannelStrip(viewModel.deckA, HamaxColors.DeckA, Modifier.fillMaxHeight())
            ChannelStrip(viewModel.deckB, HamaxColors.DeckB, Modifier.fillMaxHeight())
        }
        KnobColumn(viewModel.deckB, HamaxColors.DeckB, Modifier.width(40.dp).fillMaxHeight())
    }
}

/** GAIN, HIGH, MID, LOW and the colour FILTER of one deck. Double-tap a knob to centre it. */
@Composable
private fun KnobColumn(deck: Deck, accent: Color, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val knob = (maxHeight / 5 - 12.dp).coerceIn(20.dp, 34.dp)
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Knob(deck.gainKnob, { deck.updateGainKnob(it) }, accent, "GAIN", knobSize = knob)
            Knob(deck.highKnob, { deck.updateHighKnob(it) }, accent, "HIGH", knobSize = knob)
            Knob(deck.midKnob, { deck.updateMidKnob(it) }, accent, "MID", knobSize = knob)
            Knob(deck.lowKnob, { deck.updateLowKnob(it) }, accent, "LOW", knobSize = knob)
            Knob(deck.filterKnob, { deck.updateFilterKnob(it) }, accent, "FILTER", knobSize = knob)
        }
    }
}

/** Level meter and volume fader of one channel. */
@Composable
private fun ChannelStrip(deck: Deck, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LedMeter(deck.outputLevel, Modifier.width(9.dp).fillMaxHeight())
            Fader(
                value = deck.channelVolume,
                onValueChange = { deck.updateChannelVolume(it) },
                vertical = true,
                accent = accent,
                modifier = Modifier.width(32.dp).fillMaxHeight(),
                fill = true
            )
        }
        Text(
            text = deck.label,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun VideoTab(viewModel: DjViewModel, showFull: Boolean, onFull: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black)
        ) {
            // only one picture view can be attached to a player at a time
            if (!showFull) {
                VideoStage(
                    deckA = viewModel.deckA,
                    deckB = viewModel.deckB,
                    crossfader = viewModel.videoMix,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = "The crossfader dissolves between the two pictures. Audio-only tracks show nothing here.",
            color = HamaxColors.Muted,
            fontSize = 9.sp,
            lineHeight = 12.sp
        )
        PadButton(
            label = "FULL SCREEN",
            onClick = onFull,
            modifier = Modifier.fillMaxWidth().height(30.dp),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MasterTab(viewModel: DjViewModel, onSettings: () -> Unit) {
    val settings = viewModel.settings
    val a = viewModel.deckA.outputLevel
    val b = viewModel.deckB.outputLevel
    val master = (sqrt(a * a + b * b) * settings.masterVolume).coerceIn(0f, 1f)

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LedMeter(master, Modifier.width(10.dp).fillMaxHeight())
                Fader(
                    value = settings.masterVolume,
                    onValueChange = { settings.updateMasterVolume(it) },
                    vertical = true,
                    accent = HamaxColors.Text,
                    modifier = Modifier.width(34.dp).fillMaxHeight(),
                    fill = true
                )
            }
            Text("MASTER", color = HamaxColors.Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            SmallLabel("CROSSFADER CURVE")
            Segmented(
                options = listOf("SMOOTH", "CUT"),
                selected = settings.crossfaderCurve,
                onSelect = { settings.updateCrossfaderCurve(it) },
                accent = HamaxColors.Text,
                height = 28.dp
            )
            PadButton(
                label = if (settings.crossfaderReverse) "REVERSED: ON" else "REVERSED: OFF",
                onClick = { settings.updateCrossfaderReverse(!settings.crossfaderReverse) },
                modifier = Modifier.fillMaxWidth().height(28.dp),
                foreground = if (settings.crossfaderReverse) HamaxColors.Cue else HamaxColors.Soft,
                fontSize = 9.sp
            )
            Text(
                text = if (settings.crossfaderCurve == AppSettings.CURVE_SHARP)
                    "Cut: both decks stay full until the far ends."
                else "Smooth: even loudness across the sweep.",
                color = HamaxColors.Muted,
                fontSize = 9.sp,
                lineHeight = 12.sp
            )
            Box(Modifier.weight(1f))
            PadButton(
                label = "ALL SETTINGS",
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth().height(30.dp),
                fontSize = 10.sp
            )
        }
    }
}
