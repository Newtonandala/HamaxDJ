package com.hamax.dj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamax.dj.Deck
import com.hamax.dj.Sampler

private val MODE_NAMES = listOf("HOT", "LOOP", "SMP", "FX")

/**
 * The eight performance pads of a deck, with four modes:
 * HOT = hot cues (tap to set or jump, hold to delete), LOOP = auto loops (1/4 to 32 beats),
 * SMP = sampler sounds, FX = effects that are on while you hold the pad.
 */
@Composable
fun PadPanel(deck: Deck, sampler: Sampler, accent: Color, modifier: Modifier = Modifier) {
    var mode by remember { mutableIntStateOf(0) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth().height(22.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MODE_NAMES.forEachIndexed { index, name ->
                val on = index == mode
                val shape = RoundedCornerShape(5.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(if (on) accent else HamaxColors.Control)
                        .border(1.dp, if (on) accent else HamaxColors.Line, shape)
                        .clickable(role = Role.Tab) { mode = index },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name,
                        color = if (on) HamaxColors.OnAccent else HamaxColors.Soft,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (row in 0 until 4) {
                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    for (col in 0 until 2) {
                        val index = row * 2 + col
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            when (mode) {
                                0 -> HotCuePad(deck, index)
                                1 -> LoopPad(deck, index)
                                2 -> SamplerPad(sampler, index)
                                else -> FxPad(deck, index)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HotCuePad(deck: Deck, index: Int) {
    val stored = deck.hotCues[index]
    val set = stored >= 0L
    PerfPad(
        label = (index + 1).toString(),
        subLabel = if (set) formatTime(stored).dropLast(2) else null,
        color = PAD_COLORS[index],
        lit = set,
        dim = !set,
        modifier = Modifier.fillMaxSize(),
        onTap = { deck.hotCuePress(index) },
        onLongPress = { deck.hotCueDelete(index) }
    )
}

@Composable
private fun LoopPad(deck: Deck, index: Int) {
    val on = deck.loopActive && deck.loopIndex == index
    PerfPad(
        label = Deck.LOOP_NAMES[index],
        color = HamaxColors.Cue,
        lit = on,
        modifier = Modifier.fillMaxSize(),
        onTap = { deck.toggleLoop(index) }
    )
}

@Composable
private fun SamplerPad(sampler: Sampler, index: Int) {
    PerfPad(
        label = Sampler.NAMES[index],
        color = PAD_COLORS[index],
        dim = true,
        modifier = Modifier.fillMaxSize(),
        onPress = { sampler.trigger(index) }
    )
}

@Composable
private fun FxPad(deck: Deck, index: Int) {
    PerfPad(
        label = Deck.FX_NAMES[index],
        color = PAD_COLORS[(index + 3) % PAD_COLORS.size],
        lit = deck.activeFx == index,
        dim = true,
        modifier = Modifier.fillMaxSize(),
        onPress = { deck.fxDown(index) },
        onRelease = { deck.fxUp(index) }
    )
}
