package com.hamax.dj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * A rotary knob. Drag up or down to turn it; double-tap to put it back to its centre.
 * The coloured arc grows from the centre mark towards the current value.
 */
@Composable
fun Knob(
    value: Float,
    onValueChange: (Float) -> Unit,
    accent: Color,
    label: String,
    modifier: Modifier = Modifier,
    knobSize: androidx.compose.ui.unit.Dp = 34.dp,
    resetValue: Float = 0.5f
) {
    val currentChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(knobSize)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        // a full sweep takes about 140 dp of finger travel
                        val next = currentValue - dragAmount / (140.dp.toPx())
                        currentChange(next.coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { currentChange(resetValue) })
                }
        ) {
            val r = size.minDimension / 2f
            val stroke = r * 0.16f
            val arcR = r - stroke / 2f
            val topLeft = Offset(center.x - arcR, center.y - arcR)
            val arcSize = Size(arcR * 2f, arcR * 2f)

            drawCircle(HamaxColors.Control, radius = r * 0.78f, center = center)
            drawCircle(HamaxColors.Line, radius = r * 0.78f, center = center, style = Stroke(width = 1f))
            drawArc(
                color = HamaxColors.Line,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val v = value.coerceIn(0f, 1f)
            val from = 270f // top centre
            val to = 135f + 270f * v
            if (kotlin.math.abs(v - resetValue) > 0.004f) {
                drawArc(
                    color = accent,
                    startAngle = minOf(from, to),
                    sweepAngle = kotlin.math.abs(to - from),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            val rad = Math.toRadians(to.toDouble())
            val inner = r * 0.26f
            val outer = r * 0.70f
            drawLine(
                color = HamaxColors.Text,
                start = Offset(center.x + inner * cos(rad).toFloat(), center.y + inner * sin(rad).toFloat()),
                end = Offset(center.x + outer * cos(rad).toFloat(), center.y + outer * sin(rad).toFloat()),
                strokeWidth = r * 0.14f,
                cap = StrokeCap.Round
            )
        }
        Text(
            text = label,
            color = HamaxColors.Muted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            maxLines = 1
        )
    }
}

/** A vertical LED level meter. [level] is 0..1 of full scale and is drawn on a decibel scale. */
@Composable
fun LedMeter(level: Float, modifier: Modifier = Modifier, segments: Int = 14) {
    Canvas(modifier) {
        val db = if (level <= 0.0001f) -80f else 20f * log10(level)
        val lit = (((db + 42f) / 42f) * segments).toInt().coerceIn(0, segments)
        val gap = 1.5.dp.toPx()
        val segH = (size.height - gap * (segments - 1)) / segments
        for (i in 0 until segments) {
            val y = size.height - (i + 1) * segH - i * gap
            val on = i < lit
            val base = when {
                i >= segments - 2 -> Color(0xFFFF4A4A)
                i >= segments - 5 -> Color(0xFFFFC03A)
                else -> Color(0xFF39D98A)
            }
            drawRoundRect(
                color = if (on) base else base.copy(alpha = 0.14f),
                topLeft = Offset(0f, y),
                size = Size(size.width, segH),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
            )
        }
    }
}

/** A row of mutually exclusive choices, such as the crossfader curve. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 30.dp
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { index, text ->
            val on = index == selected
            val shape = RoundedCornerShape(6.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(shape)
                    .background(if (on) accent else HamaxColors.Control)
                    .border(1.dp, if (on) accent else HamaxColors.Line, shape)
                    .clickable(role = Role.RadioButton) { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    color = if (on) HamaxColors.OnAccent else HamaxColors.Soft,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/** A small momentary or toggle pad used all over the deck: press feedback, optional lit state. */
@Composable
fun PerfPad(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = HamaxColors.DeckA,
    lit: Boolean = false,
    dim: Boolean = false,
    subLabel: String? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onPress: (() -> Unit)? = null,
    onRelease: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(6.dp)
    val tap by rememberUpdatedState(onTap)
    val long by rememberUpdatedState(onLongPress)
    val down by rememberUpdatedState(onPress)
    val up by rememberUpdatedState(onRelease)
    var pressed by remember { mutableStateOf(false) }
    val filled = lit || pressed
    Box(
        modifier
            .clip(shape)
            .background(if (filled) color else HamaxColors.Control)
            .border(1.dp, if (filled || !dim) color.copy(alpha = if (filled) 1f else 0.55f) else HamaxColors.Line, shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        down?.invoke()
                        tryAwaitRelease()
                        up?.invoke()
                        pressed = false
                    },
                    onLongPress = { long?.invoke() },
                    onTap = { tap?.invoke() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = if (filled) HamaxColors.OnAccent else if (dim) HamaxColors.Muted else HamaxColors.Text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = if (filled) HamaxColors.OnAccent else HamaxColors.Muted,
                    fontSize = 8.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SmallLabel(text: String, modifier: Modifier = Modifier, color: Color = HamaxColors.Muted) {
    Text(
        text = text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier
    )
}

/** Empty spacer that keeps a column the same height as its neighbours. */
@Composable
fun FillHeight(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight())
}
