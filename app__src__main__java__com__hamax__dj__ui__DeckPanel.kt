package com.hamax.dj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamax.dj.Deck
import com.hamax.dj.Sampler
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

@Composable
fun DeckPanel(
    deck: Deck,
    other: Deck,
    sampler: Sampler,
    accent: Color,
    mirrored: Boolean,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .clip(shape)
            .background(HamaxColors.Panel)
            .border(1.dp, HamaxColors.PanelBorder, shape)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        TitleRow(deck, accent, mirrored, onLoad)
        InfoRow(deck, accent, mirrored)
        OverviewStrip(deck, accent)

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val padsWidth = 112.dp
            val pitchWidth = 44.dp
            val gap = 6.dp
            val jogSize = minOf(maxHeight, maxWidth - padsWidth - pitchWidth - gap * 2).coerceAtLeast(64.dp)
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mirrored) {
                    PitchColumn(deck, accent, Modifier.width(pitchWidth).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        JogWheel(deck, accent, Modifier.size(jogSize))
                    }
                    PadPanel(deck, sampler, accent, Modifier.width(padsWidth).fillMaxHeight())
                } else {
                    PadPanel(deck, sampler, accent, Modifier.width(padsWidth).fillMaxHeight())
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        JogWheel(deck, accent, Modifier.size(jogSize))
                    }
                    PitchColumn(deck, accent, Modifier.width(pitchWidth).fillMaxHeight())
                }
            }
        }

        TransportRow(deck, other, accent)
    }
}

@Composable
private fun TitleRow(deck: Deck, accent: Color, mirrored: Boolean, onLoad: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (mirrored) LoadButton(onLoad) else DeckBadge(deck.label, accent)

        Column(Modifier.weight(1f)) {
            Text(
                text = deck.title,
                color = HamaxColors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (mirrored) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            val note = deck.errorMessage
            val status = when {
                note != null -> note
                !deck.isLoaded -> "Audio or video file"
                deck.analysisState == Deck.ANALYSIS_WORKING -> (if (deck.hasVideo) "Video" else "Audio") + " - analysing..."
                deck.analysisState == Deck.ANALYSIS_FAILED -> (if (deck.hasVideo) "Video" else "Audio") + " - no tempo found"
                deck.hasVideo -> "Video"
                else -> "Audio"
            }
            Text(
                text = status,
                color = if (note != null) HamaxColors.DeckB else HamaxColors.Muted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (mirrored) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (mirrored) DeckBadge(deck.label, accent) else LoadButton(onLoad)
    }
}

@Composable
private fun DeckBadge(label: String, accent: Color) {
    Box(
        Modifier.size(width = 24.dp, height = 32.dp).clip(RoundedCornerShape(6.dp)).background(accent),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = HamaxColors.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun LoadButton(onLoad: () -> Unit) {
    PadButton(
        label = "LOAD",
        onClick = onLoad,
        modifier = Modifier.size(width = 54.dp, height = 32.dp),
        fontSize = 10.sp
    )
}

@Composable
private fun InfoRow(deck: Deck, accent: Color, mirrored: Boolean) {
    val bpmText = when {
        deck.analysisState == Deck.ANALYSIS_WORKING -> "..."
        deck.currentBpm > 0f -> "%.1f".format(deck.currentBpm)
        else -> "---"
    }
    val items = listOf(
        Readout("ELAPSED", formatTime(deck.positionMs), HamaxColors.Text),
        Readout("REMAIN", formatRemain(deck.positionMs, deck.durationMs), HamaxColors.Text),
        Readout("BPM  TAP", bpmText, HamaxColors.Cue, tappable = true),
        Readout("PITCH", formatPitch(deck.speedPercent), accent)
    )
    val ordered = if (mirrored) items.reversed() else items
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(HamaxColors.Well)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ordered.forEach { item ->
            val m = if (item.tappable) {
                Modifier.pointerInput(deck) {
                    detectTapGestures(
                        onTap = { deck.tapTempo() },
                        onLongPress = { deck.flipHalfDouble() }
                    )
                }
            } else Modifier
            Column(m.padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = item.value,
                    color = item.color,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = item.name,
                    color = HamaxColors.Muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    maxLines = 1
                )
            }
        }
    }
}

private class Readout(
    val name: String,
    val value: String,
    val color: Color,
    val tappable: Boolean = false
)

/** The whole track as one strip: tap or drag to jump anywhere. Shows the cue, hot cues and loop. */
@Composable
private fun OverviewStrip(deck: Deck, accent: Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(HamaxColors.Well)
            .pointerInput(deck) {
                detectTapGestures { point -> deck.seekToFraction(point.x / size.width) }
            }
            .pointerInput(deck) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    deck.seekToFraction(change.position.x / size.width)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val duration = deck.durationMs
        val progress = if (duration > 0L) (deck.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val data = deck.peaks

        if (data != null && data.isNotEmpty()) {
            val columns = w.toInt().coerceAtLeast(1)
            val perColumn = data.size.toFloat() / columns
            val mid = h / 2f
            for (x in 0 until columns) {
                val from = (x * perColumn).toInt()
                val to = max(from + 1, ((x + 1) * perColumn).toInt()).coerceAtMost(data.size)
                var peak = 0f
                for (i in from until to) if (data[i] > peak) peak = data[i]
                val half = sqrt(peak) * (h / 2f - 1f)
                val played = x.toFloat() / columns < progress
                drawLine(
                    color = if (played) accent.copy(alpha = 0.45f) else accent,
                    start = Offset(x.toFloat(), mid - half),
                    end = Offset(x.toFloat(), mid + half),
                    strokeWidth = 1f
                )
            }
        } else if (progress > 0f) {
            drawRect(accent.copy(alpha = 0.5f), Offset(0f, h / 2f - 3f), Size(w * progress, 6f))
        }

        if (duration > 0L) {
            for (i in 0 until Deck.HOT_CUE_COUNT) {
                val c = deck.hotCues[i]
                if (c >= 0L) {
                    val x = w * c.toFloat() / duration
                    drawRect(PAD_COLORS[i], Offset(x - 1f, 0f), Size(2f, h))
                }
            }
            if (deck.loopActive) {
                val a = w * deck.loopStartMs.toFloat() / duration
                val b = w * deck.loopEndMs.toFloat() / duration
                drawRect(Color(0x66FFB020), Offset(a, 0f), Size(max(2f, b - a), h))
            }
            val cueX = w * deck.cuePointMs.toFloat() / duration
            drawRect(HamaxColors.Cue, Offset(cueX - 1f, 0f), Size(2f, h))
            drawRect(Color.White, Offset(w * progress - 1f, 0f), Size(2f, h))
        }
    }
}

@Composable
private fun PitchColumn(deck: Deck, accent: Color, modifier: Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Fader(
            value = deck.pitchValue,
            onValueChange = { deck.setPitch(it) },
            vertical = true,
            accent = accent,
            modifier = Modifier.weight(1f).width(40.dp),
            snapToCenter = true,
            showCenterTick = true,
            onDoubleTap = { deck.resetPitch() }
        )
        PadButton(
            label = "±" + deck.pitchRangePct + "%",
            onClick = { deck.cyclePitchRange() },
            modifier = Modifier.fillMaxWidth().height(30.dp),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TransportRow(deck: Deck, other: Deck, accent: Color) {
    val shape = RoundedCornerShape(8.dp)
    val synced = deck.currentBpm > 0f && other.currentBpm > 0f &&
        abs(deck.currentBpm - other.currentBpm) < 0.15f
    Row(
        Modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        CueButton(deck, Modifier.weight(1f).fillMaxHeight())

        Box(
            Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .clip(shape)
                .background(accent)
                .clickable(
                    onClickLabel = "Play or pause deck " + deck.label,
                    role = Role.Button
                ) { deck.togglePlay() },
            contentAlignment = Alignment.Center
        ) {
            PlayPauseIcon(playing = deck.isPlaying, tint = HamaxColors.OnAccent)
        }

        PadButton(
            label = "SYNC",
            onClick = { deck.syncTo(other) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            background = if (synced) accent else HamaxColors.Control,
            foreground = if (synced) HamaxColors.OnAccent else HamaxColors.Text,
            borderColor = if (synced) accent else HamaxColors.Line,
            fontSize = 10.sp
        )

        PadButton(
            label = "KEY",
            onClick = { deck.toggleKeyLock() },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            background = if (deck.keyLock) HamaxColors.Control else HamaxColors.Well,
            foreground = if (deck.keyLock) accent else HamaxColors.Muted,
            borderColor = if (deck.keyLock) accent else HamaxColors.Line,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun CueButton(deck: Deck, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (pressed) HamaxColors.Cue else HamaxColors.Control)
            .border(1.dp, if (pressed) HamaxColors.Cue else HamaxColors.Line, shape)
            .pointerInput(deck) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        deck.cueDown()
                        tryAwaitRelease()
                        deck.cueUp()
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "CUE",
            color = if (pressed) HamaxColors.OnAccent else HamaxColors.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
