package com.hamax.dj.ui

fun formatTime(ms: Long): String {
    val t = ms.coerceAtLeast(0L)
    val minutes = t / 60000L
    val seconds = (t / 1000L) % 60L
    val tenths = (t % 1000L) / 100L
    return "%d:%02d.%d".format(minutes, seconds, tenths)
}

fun formatRemain(positionMs: Long, durationMs: Long): String {
    return if (durationMs <= 0L) "-:--.-" else "-" + formatTime(durationMs - positionMs)
}

fun formatPitch(percent: Float): String {
    val sign = if (percent >= 0f) "+" else ""
    return sign + "%.1f%%".format(percent)
}
