package com.hamax.dj.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

object HamaxColors {
    val Bg = Color(0xFF0B0D11)
    val Panel = Color(0xFF14171D)
    val PanelBorder = Color(0xFF232831)
    val Well = Color(0xFF0F1217)
    val Control = Color(0xFF1C2028)
    val Line = Color(0xFF2B313C)
    val Text = Color(0xFFE6E8EC)
    val Soft = Color(0xFFB4BAC6)
    val Muted = Color(0xFF8B93A1)
    private var accentA by mutableStateOf(Color(0xFF2CA4E8))
    private var accentB by mutableStateOf(Color(0xFFF0505A))

    /** Deck colours follow the "Deck colours" setting, so they are read through state. */
    val DeckA: Color get() = accentA
    val DeckB: Color get() = accentB

    fun updateAccents(a: Color, b: Color) {
        accentA = a
        accentB = b
    }
    val Cue = Color(0xFFFFB020)
    val OnAccent = Color(0xFF0B0D11)
}

@Composable
fun HamaxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = HamaxColors.DeckA,
            background = HamaxColors.Bg,
            surface = HamaxColors.Panel,
            onBackground = HamaxColors.Text,
            onSurface = HamaxColors.Text
        ),
        content = content
    )
}
