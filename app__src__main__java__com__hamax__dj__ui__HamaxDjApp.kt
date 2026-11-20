package com.hamax.dj.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamax.dj.Deck
import com.hamax.dj.DjViewModel
import kotlinx.coroutines.delay

@Composable
fun HamaxDjApp(viewModel: DjViewModel) {
    val context = LocalContext.current
    var pendingDeck by remember { mutableStateOf<Deck?>(null) }

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

    // keeps clocks and jog wheels moving
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.deckA.refresh()
            viewModel.deckB.refresh()
            delay(33L)
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(HamaxColors.Bg)
            .safeDrawingPadding()
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DeckPanel(
            deck = viewModel.deckA,
            accent = HamaxColors.DeckA,
            mirrored = false,
            onLoad = { openFor(viewModel.deckA) },
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        MixerPanel(viewModel, Modifier.width(210.dp).fillMaxHeight())
        DeckPanel(
            deck = viewModel.deckB,
            accent = HamaxColors.DeckB,
            mirrored = true,
            onLoad = { openFor(viewModel.deckB) },
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun MixerPanel(viewModel: DjViewModel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .clip(shape)
            .background(HamaxColors.Panel)
            .border(1.dp, HamaxColors.PanelBorder, shape)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "HamaxDJ",
            color = HamaxColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )

        VideoStage(
            deckA = viewModel.deckA,
            deckB = viewModel.deckB,
            crossfader = viewModel.crossfader,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
        )

        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ChannelFader(viewModel.deckA, HamaxColors.DeckA, Modifier.fillMaxHeight())
            ChannelFader(viewModel.deckB, HamaxColors.DeckB, Modifier.fillMaxHeight())
        }

        Row(
            Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("A", color = HamaxColors.DeckA, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            Fader(
                value = viewModel.crossfader,
                onValueChange = { viewModel.setCrossfader(it) },
                vertical = false,
                accent = Color.White,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                snapToCenter = true,
                showCenterTick = true,
                onDoubleTap = { viewModel.setCrossfader(0.5f) }
            )
            Text("B", color = HamaxColors.DeckB, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ChannelFader(deck: Deck, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Fader(
            value = deck.channelVolume,
            onValueChange = { deck.setChannelVolume(it) },
            vertical = true,
            accent = accent,
            modifier = Modifier.weight(1f).width(44.dp),
            fill = true
        )
        Text(
            text = "VOL " + deck.label,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}
