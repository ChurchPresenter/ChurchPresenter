package org.churchpresenter.liveoutput

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer

/** What [HeldOutputFrame] remembers between draws: whether its layer already holds the held frame. */
private class HeldFrameState {
    var recorded = false
}

/**
 * Draws [content], or holds it back as [hold] says.
 *
 * - `null`: [content] drawn as usual, with nothing recorded.
 * - [ScreenHold.HOLD]: the frame drawn when the hold began is replayed from a graphics layer on every
 *   draw, so what goes live underneath is composed but never shown. The layer is replayed, not read
 *   back as a bitmap -- `GraphicsLayer.toImageBitmap()` is the call `DeckLinkComposeOutput` avoids --
 *   and nothing here blocks the event thread. A native view in the content (a browser page) is not
 *   part of a layer and keeps drawing.
 * - [ScreenHold.BLANK]: black.
 *
 * Drawing is all this does, so the content keeps its state and transitions, and a release shows what
 * is live at once.
 */
@Composable
fun HeldOutputFrame(
    hold: ScreenHold?,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val state = remember { HeldFrameState() }
    Box(
        modifier = modifier.drawWithContent {
            when (hold) {
                null -> {
                    state.recorded = false
                    drawContent()
                }
                ScreenHold.HOLD -> drawHeld(layer, state)
                ScreenHold.BLANK -> {
                    state.recorded = false
                    drawRect(Color.Black)
                }
            }
        },
    ) {
        content()
    }
}

/** Records the first held frame, then only ever replays it. */
private fun ContentDrawScope.drawHeld(layer: GraphicsLayer, state: HeldFrameState) {
    if (!state.recorded) {
        val content = this
        layer.record { content.drawContent() }
        state.recorded = true
    }
    drawLayer(layer)
}
