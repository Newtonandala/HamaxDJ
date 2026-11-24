package com.hamax.dj.ui

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.hamax.dj.Deck
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val SilverRim = listOf(
    Color(0xFF8E939B), Color(0xFFE3E6EA), Color(0xFF6F747C), Color(0xFFCDD0D5),
    Color(0xFF82878F), Color(0xFFE6E9ED), Color(0xFF767B83), Color(0xFF8E939B)
)

private const val WORD = "HamaxDJ"

/**
 * The jog wheel: silver rim, grooved platter that spins with the music (33 1/3 rpm),
 * a glowing ring in the deck colour, and a progress arc around the edge.
 * Drag around it to scrub: the record is held while you touch it, then resumes.
 */
@Composable
fun JogWheel(deck: Deck, accent: Color, modifier: Modifier = Modifier) {
    val angle = (deck.positionMs / Deck.MS_PER_TURN % 1f) * 360f
    val progress = if (deck.durationMs > 0L) {
        (deck.positionMs.toFloat() / deck.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val textPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
        }
    }

    Canvas(
        modifier.pointerInput(deck) {
            var last = 0f
            var onRim = false
            fun angleAt(p: Offset): Float {
                val dy = (p.y - size.height / 2f).toDouble()
                val dx = (p.x - size.width / 2f).toDouble()
                return Math.toDegrees(atan2(dy, dx)).toFloat()
            }
            fun endTouch() {
                if (onRim) deck.nudgeEnd() else deck.jogEnd()
                onRim = false
            }
            detectDragGestures(
                onDragStart = { start ->
                    last = angleAt(start)
                    val dx = start.x - size.width / 2f
                    val dy = start.y - size.height / 2f
                    val radius = sqrt(dx * dx + dy * dy)
                    // the outer silver ring bends the pitch; the platter scratches
                    onRim = radius > minOf(size.width, size.height) / 2f * 0.80f
                    if (onRim) deck.nudgeStart() else deck.jogStart()
                },
                onDragEnd = { endTouch() },
                onDragCancel = { endTouch() },
                onDrag = { change, _ ->
                    change.consume()
                    val a = angleAt(change.position)
                    var delta = a - last
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    last = a
                    if (onRim) deck.nudgeMove(delta) else deck.jogMove(delta)
                }
            )
        }
    ) {
        val r = size.minDimension / 2f
        val c = center
        val ringW = r * 0.06f

        // outer track and progress arc
        drawCircle(HamaxColors.Line, radius = r, center = c)
        if (progress > 0f) {
            val arcR = r - ringW / 2f
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(c.x - arcR, c.y - arcR),
                size = Size(arcR * 2f, arcR * 2f),
                style = Stroke(width = ringW)
            )
        }
        drawCircle(HamaxColors.Bg, radius = r * 0.93f, center = c)

        // silver rim
        drawCircle(brush = Brush.sweepGradient(SilverRim, center = c), radius = r * 0.90f, center = c)

        // platter
        drawCircle(Color(0xFF15181D), radius = r * 0.82f, center = c)

        // spinning parts: grooves, lettering, position marker
        rotate(degrees = angle, pivot = c) {
            for (i in 0 until 90) {
                rotate(degrees = i * 4f, pivot = c) {
                    drawLine(
                        color = Color(0x1AFFFFFF),
                        start = Offset(c.x, c.y - r * 0.40f),
                        end = Offset(c.x, c.y - r * 0.80f),
                        strokeWidth = 1f
                    )
                }
            }

            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                textPaint.textSize = r * 0.16f
                for (arc in 0 until 2) {
                    val base = if (arc == 0) 0f else 180f
                    for (i in WORD.indices) {
                        textPaint.color = (if (i >= 5) accent else HamaxColors.Text).toArgb()
                        nc.save()
                        nc.rotate(base + (i - 3) * 14f, c.x, c.y)
                        nc.drawText(WORD[i].toString(), c.x, c.y - r * 0.60f, textPaint)
                        nc.restore()
                    }
                }
            }

            drawLine(
                color = accent,
                start = Offset(c.x, c.y - r * 0.81f),
                end = Offset(c.x, c.y - r * 0.73f),
                strokeWidth = r * 0.05f,
                cap = StrokeCap.Round
            )
        }

        // glowing centre ring
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.40f), Color.Transparent),
                center = c,
                radius = r * 0.56f
            ),
            radius = r * 0.56f,
            center = c
        )
        drawCircle(HamaxColors.Bg, radius = r * 0.36f, center = c)
        drawCircle(accent, radius = r * 0.36f, center = c, style = Stroke(width = r * 0.07f))

        // hub with hexagon
        drawCircle(Color(0xFF2A2F37), radius = r * 0.20f, center = c)
        val hexR = r * 0.11f
        val hex = Path()
        for (i in 0 until 6) {
            val a = Math.toRadians(60.0 * i - 90.0)
            val x = c.x + hexR * cos(a).toFloat()
            val y = c.y + hexR * sin(a).toFloat()
            if (i == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
        }
        hex.close()
        drawPath(hex, HamaxColors.Soft, style = Stroke(width = r * 0.02f))
    }
}
