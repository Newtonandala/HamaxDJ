package com.hamax.dj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hamax.dj.AppSettings
import com.hamax.dj.Deck
import com.hamax.dj.TrackAnalyzer
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The master waveform: deck A on the top row, deck B underneath, both scrolling past a fixed
 * playhead in the middle. Drag a row sideways to move that deck's playhead.
 */
@Composable
fun WaveformStrip(
    deckA: Deck,
    deckB: Deck,
    settings: AppSettings,
    modifier: Modifier = Modifier,
    height: Dp = 60.dp
) {
    val windowMs = settings.waveformSeconds * 1000f
    val showGrid = settings.showBeatGrid
    val colorA = HamaxColors.DeckA
    val colorB = HamaxColors.DeckB

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(HamaxColors.Well)
            .pointerInput(deckA, deckB, windowMs) {
                var target: Deck? = null
                detectHorizontalDragGestures(
                    onDragStart = { start -> target = if (start.y < size.height / 2f) deckA else deckB },
                    onDragEnd = { target = null },
                    onDragCancel = { target = null },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        // dragging to the right pulls the track back, like moving a record
                        val deltaMs = (-dx / size.width * windowMs).toLong()
                        target?.scrubBy(deltaMs)
                    }
                )
            }
    ) {
        val rowH = size.height / 2f
        drawRow(deckA, colorA, 0f, rowH, windowMs, showGrid)
        drawRow(deckB, colorB, rowH, rowH, windowMs, showGrid)
        drawLine(HamaxColors.Line, Offset(0f, rowH), Offset(size.width, rowH), 1f)
        // playhead
        drawLine(Color.White, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 2f)
    }
}

private fun DrawScope.drawRow(
    deck: Deck,
    color: Color,
    top: Float,
    rowH: Float,
    windowMs: Float,
    showGrid: Boolean
) {
    val w = size.width
    val mid = top + rowH / 2f
    val pos = deck.positionMs.toFloat()
    val startMs = pos - windowMs / 2f
    val msPerPx = windowMs / w
    val data = deck.peaks

    if (!deck.isLoaded || data == null) {
        drawLine(color.copy(alpha = 0.25f), Offset(0f, mid), Offset(w, mid), 1f)
        return
    }

    val binsPerSecond = TrackAnalyzer.HOPS_PER_SECOND
    val msPerBin = 1000f / binsPerSecond
    val half = rowH / 2f - 2f
    val past = color.copy(alpha = 0.45f)

    var x = 0
    val columns = w.toInt()
    while (x < columns) {
        val t0 = startMs + x * msPerPx
        val t1 = t0 + msPerPx
        val b0 = floor(t0 / msPerBin).toInt()
        val b1 = max(b0, floor(t1 / msPerBin).toInt())
        var peak = 0f
        if (b1 >= 0 && b0 < data.size) {
            var b = max(b0, 0)
            val last = minOf(b1, data.size - 1)
            while (b <= last) {
                if (data[b] > peak) peak = data[b]
                b++
            }
        }
        if (peak > 0f) {
            val h = sqrt(peak) * half
            drawLine(
                color = if (x < columns / 2) past else color,
                start = Offset(x.toFloat(), mid - h),
                end = Offset(x.toFloat(), mid + h),
                strokeWidth = 1f
            )
        }
        x++
    }

    // beat lines
    if (showGrid && deck.bpm > 0f) {
        val beatMs = deck.beatLengthMs()
        var k = floor((startMs - deck.firstBeatMs) / beatMs).toInt()
        val endMs = startMs + windowMs
        while (true) {
            val t = deck.firstBeatMs + k * beatMs
            if (t > endMs) break
            if (t >= startMs) {
                val px = (t - startMs) / msPerPx
                val bar = ((k % 4) + 4) % 4 == 0
                drawLine(
                    color = Color.White.copy(alpha = if (bar) 0.45f else 0.18f),
                    start = Offset(px, top),
                    end = Offset(px, top + rowH),
                    strokeWidth = 1f
                )
            }
            k++
        }
    }

    // cues and loop
    for (i in 0 until Deck.HOT_CUE_COUNT) {
        val c = deck.hotCues[i]
        if (c >= 0L) {
            val px = (c - startMs) / msPerPx
            if (px in 0f..w) {
                drawLine(PAD_COLORS[i], Offset(px, top), Offset(px, top + rowH), 2f)
            }
        }
    }
    if (deck.loopActive) {
        val a = ((deck.loopStartMs - startMs) / msPerPx).coerceIn(0f, w)
        val b = ((deck.loopEndMs - startMs) / msPerPx).coerceIn(0f, w)
        if (b > a) {
            drawRect(
                color = Color(0x33FFB020),
                topLeft = Offset(a, top),
                size = androidx.compose.ui.geometry.Size(b - a, rowH)
            )
        }
    }
}

/** One colour per pad, used by hot cues, markers on the waveform and the overview strip. */
val PAD_COLORS = listOf(
    Color(0xFFFF5A5F), Color(0xFFFFA630), Color(0xFFFFE14D), Color(0xFF6BD968),
    Color(0xFF3DD6C6), Color(0xFF4DA3FF), Color(0xFF9A7BFF), Color(0xFFFF6FD0)
)
