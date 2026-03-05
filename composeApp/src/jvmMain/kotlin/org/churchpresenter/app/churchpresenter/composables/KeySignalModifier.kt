package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Renders content as a key/alpha matte signal for video mixers.
 * All visible content pixels become white, preserving their alpha.
 * On a black background this produces a standard key signal.
 */
fun Modifier.keySignal(): Modifier = this
    .graphicsLayer(alpha = 0.99f) // force offscreen compositing layer
    .drawWithContent {
        drawContent()
        drawRect(
            color = Color.White,
            blendMode = BlendMode.SrcIn
        )
    }
