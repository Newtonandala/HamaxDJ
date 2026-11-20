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
import androidx.compose.ui.geometry.CornerRadius
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

@Composable
fun DeckPanel(
    deck: Deck,
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
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TitleRow(deck, accent, mirrored, onLoad)
        InfoRow(deck, accent, mirrored)
        SeekStrip(deck, accent)

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pitchWidth = 52.dp
            val gap = 8.dp
            val jogSize = minOf(maxHeight, maxWidth - pitchWidth - gap).coerceAtLeast(64.dp)
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (mirrored) PitchColumn(deck, accent, Modifier.width(pitchWidth).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    JogWheel(deck, accent, Modifier.size(jogSize))
                }
                if (!mirrored) PitchColumn(deck, accent, Modifier.width(pitchWidth).fillMaxHeight())
            }
        }

        TransportRow(deck, accent)
    }
}

@Composable
private fun TitleRow(deck: Deck, accent: Color, mirrored: Boolean, onLoad: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (mirrored) LoadButton(onLoad) else DeckBadge(deck.label, accent)

        Column(Modifier.weight(1f)) {
            Text(
                text = deck.title,
                color = HamaxColors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (mirrored) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            val note = deck.errorMessage
            Text(
                text = note ?: if (!deck.isLoaded) "Audio or video file" else if (deck.hasVideo) "Video" else "Audio",
                color = if (note != null) HamaxColors.DeckB else HamaxColors.Muted,
                fontSize = 10.sp,
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
        Modifier.size(width = 26.dp, height = 36.dp).clip(RoundedCornerShape(6.dp)).background(accent),
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
        modifier = Modifier.size(width = 60.dp, height = 36.dp),
        fontSize = 11.sp
    )
}

@Composable
private fun InfoRow(deck: Deck, accent: Color, mirrored: Boolean) {
    val readouts = listOf(
        Triple("ELAPSED", formatTime(deck.positionMs), HamaxColors.Text),
        Triple("REMAIN", formatRemain(deck.positionMs, deck.durationMs), HamaxColors.Text),
        Triple("PITCH", formatPitch(deck.speedPercent), accent)
    )
    val ordered = if (mirrored) readouts.reversed() else readouts
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(HamaxColors.Well)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ordered.forEach { (name, value, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    color = color,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = name,
                    color = HamaxColors.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun SeekStrip(deck: Deck, accent: Color) {
    val progress = if (deck.durationMs > 0L) {
        (deck.positionMs.toFloat() / deck.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
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
        val barH = 6.dp.toPx()
        val y = (size.height - barH) / 2f
        val corner = CornerRadius(barH / 2f, barH / 2f)
        drawRoundRect(HamaxColors.Line, Offset(0f, y), Size(size.width, barH), corner)
        if (progress > 0f) {
            drawRoundRect(accent, Offset(0f, y), Size(size.width * progress, barH), corner)
        }
        if (deck.durationMs > 0L) {
            val cueX = size.width * (deck.cuePointMs.toFloat() / deck.durationMs.toFloat())
            drawRect(HamaxColors.Cue, Offset(cueX - 1.dp.toPx(), 2.dp.toPx()), Size(2.dp.toPx(), size.height - 4.dp.toPx()))
        }
    }
}

@Composable
private fun PitchColumn(deck: Deck, accent: Color, modifier: Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Fader(
            value = deck.pitchValue,
            onValueChange = { deck.setPitch(it) },
            vertical = true,
            accent = accent,
            modifier = Modifier.weight(1f).width(44.dp),
            snapToCenter = true,
            showCenterTick = true,
            onDoubleTap = { deck.resetPitch() }
        )
        PadButton(
            label = "±" + deck.pitchRangePct + "%",
            onClick = { deck.cyclePitchRange() },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun TransportRow(deck: Deck, accent: Color) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CueButton(deck, Modifier.weight(1f).fillMaxHeight())

        Box(
            Modifier
                .weight(1f)
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
            label = "KEY LOCK",
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
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
