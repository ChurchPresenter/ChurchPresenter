package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.churchpresenter.app.churchpresenter.data.settings.MetronomePosition

/** Maps a free 3x3 grid position to a screen [Alignment]; NONE means the metronome is disabled. */
fun MetronomePosition.toAlignment(): Alignment? = when (this) {
    MetronomePosition.NONE -> null
    MetronomePosition.TOP_LEFT -> Alignment.TopStart
    MetronomePosition.TOP_CENTER -> Alignment.TopCenter
    MetronomePosition.TOP_RIGHT -> Alignment.TopEnd
    MetronomePosition.MIDDLE_LEFT -> Alignment.CenterStart
    MetronomePosition.CENTER -> Alignment.Center
    MetronomePosition.MIDDLE_RIGHT -> Alignment.CenterEnd
    MetronomePosition.BOTTOM_LEFT -> Alignment.BottomStart
    MetronomePosition.BOTTOM_CENTER -> Alignment.BottomCenter
    MetronomePosition.BOTTOM_RIGHT -> Alignment.BottomEnd
}

/**
 * A silent visual metronome — a dot that flashes [bpm] times per minute while [active] is true.
 * Each flash is a brief on-pulse followed by a dark gap, so the flash count matches the tempo
 * exactly rather than a symmetric on/off blink (which would only flash at half the BPM).
 */
@Composable
fun MetronomeDot(
    bpm: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = Color.White
) {
    var flashOn by remember { mutableStateOf(false) }
    LaunchedEffect(bpm, active) {
        if (!active || bpm <= 0) {
            flashOn = false
            return@LaunchedEffect
        }
        val periodMs = 60_000L / bpm
        val flashMs = periodMs.coerceAtMost(150L).coerceAtLeast(30L)
        while (isActive) {
            flashOn = true
            delay(flashMs)
            flashOn = false
            delay((periodMs - flashMs).coerceAtLeast(0L))
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer(alpha = if (flashOn) 1f else 0f)
            .background(color, CircleShape)
    )
}
