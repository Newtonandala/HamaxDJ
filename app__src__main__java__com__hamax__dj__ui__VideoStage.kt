package com.hamax.dj.ui

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.hamax.dj.Deck

/**
 * The output screen. Deck A's picture sits underneath and deck B's picture is laid on top
 * with its opacity set by the crossfader, so sliding across dissolves from one video to the other.
 * Audio-only tracks have no picture, so they simply don't show here.
 */
@Composable
fun VideoStage(deckA: Deck, deckB: Deck, crossfader: Float, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        DeckSurface(deckA, alpha = 1f)
        DeckSurface(deckB, alpha = crossfader)

        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StageChip(deckA, HamaxColors.DeckA)
            StageChip(deckB, HamaxColors.DeckB)
        }
    }
}

@Composable
private fun DeckSurface(deck: Deck, alpha: Float) {
    AndroidView(
        factory = { context ->
            TextureView(context).also { deck.player.setVideoTextureView(it) }
        },
        modifier = Modifier.aspectRatio(deck.videoAspect),
        update = { view -> view.alpha = alpha },
        onRelease = { view -> deck.player.clearVideoTextureView(view) }
    )
}

@Composable
private fun StageChip(deck: Deck, accent: Color) {
    val kind = when {
        !deck.isLoaded -> "-"
        deck.hasVideo -> "VIDEO"
        else -> "AUDIO"
    }
    Text(
        text = deck.label + " " + kind,
        color = accent,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .background(Color(0xB3000000))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}
