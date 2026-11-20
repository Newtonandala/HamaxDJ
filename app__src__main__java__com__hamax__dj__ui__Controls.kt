package com.hamax.dj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun PadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = HamaxColors.Control,
    foreground: Color = HamaxColors.Text,
    borderColor: Color = HamaxColors.Line,
    fontSize: TextUnit = 12.sp
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = foreground,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            maxLines = 1
        )
    }
}

@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        if (playing) {
            val barWidth = size.width * 0.28f
            drawRoundRect(
                color = tint,
                topLeft = Offset(size.width * 0.14f, size.height * 0.1f),
                size = Size(barWidth, size.height * 0.8f),
                cornerRadius = CornerRadius(2f, 2f)
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(size.width * 0.58f, size.height * 0.1f),
                size = Size(barWidth, size.height * 0.8f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        } else {
            val triangle = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.08f)
                lineTo(size.width * 0.92f, size.height * 0.5f)
                lineTo(size.width * 0.2f, size.height * 0.92f)
                close()
            }
            drawPath(triangle, tint)
        }
    }
}

private fun faderValue(
    point: Offset,
    size: IntSize,
    vertical: Boolean,
    thumbPx: Float,
    snapToCenter: Boolean
): Float {
    val travel = ((if (vertical) size.height else size.width) - thumbPx).coerceAtLeast(1f)
    val pos = if (vertical) point.y else point.x
    var v = ((pos - thumbPx / 2f) / travel).coerceIn(0f, 1f)
    if (vertical) v = 1f - v // top of a vertical fader is 1
    if (snapToCenter && abs(v - 0.5f) < 0.02f) v = 0.5f
    return v
}

/**
 * A DJ-style fader. Vertical faders read 0 at the bottom and 1 at the top;
 * horizontal faders read 0 on the left and 1 on the right.
 */
@Composable
fun Fader(
    value: Float,
    onValueChange: (Float) -> Unit,
    vertical: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
    snapToCenter: Boolean = false,
    showCenterTick: Boolean = false,
    onDoubleTap: (() -> Unit)? = null
) {
    val currentChange by rememberUpdatedState(onValueChange)
    val currentDoubleTap by rememberUpdatedState(onDoubleTap)

    Canvas(
        modifier
            .pointerInput(vertical, snapToCenter) {
                val thumbPx = 22.dp.toPx()
                detectDragGestures(
                    onDragStart = { start ->
                        currentChange(faderValue(start, size, vertical, thumbPx, snapToCenter))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        currentChange(faderValue(change.position, size, vertical, thumbPx, snapToCenter))
                    }
                )
            }
            .pointerInput(vertical, snapToCenter) {
                val thumbPx = 22.dp.toPx()
                detectTapGestures(
                    onDoubleTap = { currentDoubleTap?.invoke() },
                    onTap = { point ->
                        currentChange(faderValue(point, size, vertical, thumbPx, snapToCenter))
                    }
                )
            }
    ) {
        val thumbLen = 22.dp.toPx()
        val trackW = 4.dp.toPx()
        val cross = if (vertical) size.width else size.height
        val thumbCross = minOf(cross, 40.dp.toPx())
        val corner = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        val thumbCorner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        val thumbColor = HamaxColors.Text
        val notchColor = HamaxColors.Panel

        if (vertical) {
            val cx = size.width / 2f
            val travel = size.height - thumbLen
            val ty = (1f - value) * travel
            drawRoundRect(HamaxColors.Line, Offset(cx - trackW / 2f, 0f), Size(trackW, size.height), corner)
            if (fill) {
                drawRoundRect(
                    accent,
                    Offset(cx - trackW / 2f, ty + thumbLen / 2f),
                    Size(trackW, size.height - ty - thumbLen / 2f),
                    corner
                )
            }
            if (showCenterTick) {
                val cy = thumbLen / 2f + travel / 2f
                drawLine(HamaxColors.Muted, Offset(cx - 14.dp.toPx(), cy), Offset(cx + 14.dp.toPx(), cy), 2.dp.toPx())
            }
            drawRoundRect(thumbColor, Offset(cx - thumbCross / 2f, ty), Size(thumbCross, thumbLen), thumbCorner)
            drawLine(
                notchColor,
                Offset(cx - thumbCross / 2f + 5.dp.toPx(), ty + thumbLen / 2f),
                Offset(cx + thumbCross / 2f - 5.dp.toPx(), ty + thumbLen / 2f),
                2.dp.toPx()
            )
        } else {
            val cy = size.height / 2f
            val travel = size.width - thumbLen
            val tx = value * travel
            drawRoundRect(HamaxColors.Line, Offset(0f, cy - trackW / 2f), Size(size.width, trackW), corner)
            if (fill) {
                drawRoundRect(accent, Offset(0f, cy - trackW / 2f), Size(tx + thumbLen / 2f, trackW), corner)
            }
            if (showCenterTick) {
                val cx = thumbLen / 2f + travel / 2f
                drawLine(HamaxColors.Muted, Offset(cx, cy - 14.dp.toPx()), Offset(cx, cy + 14.dp.toPx()), 2.dp.toPx())
            }
            drawRoundRect(thumbColor, Offset(tx, cy - thumbCross / 2f), Size(thumbLen, thumbCross), thumbCorner)
            drawLine(
                notchColor,
                Offset(tx + thumbLen / 2f, cy - thumbCross / 2f + 5.dp.toPx()),
                Offset(tx + thumbLen / 2f, cy + thumbCross / 2f - 5.dp.toPx()),
                2.dp.toPx()
            )
        }
    }
}
