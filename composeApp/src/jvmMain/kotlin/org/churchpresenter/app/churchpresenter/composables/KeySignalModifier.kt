package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Modifier for key output mode (no-op).
 * Text presenters force white colors directly when outputRole is KEY.
 * Images render normally on black background — the video mixer handles keying.
 * DeckLink key uses pixel-level conversion at capture time.
 */
fun Modifier.keySignal(): Modifier = this

/**
 * ColorFilter that converts image content to a key signal.
 * Forces all RGB to 255 (pure white) while preserving the original alpha channel.
 * Works correctly on Image composables where alpha comes from content (Lottie, PNG)
 * rather than the backing surface.
 */
val keyColorFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(floatArrayOf(
        0f, 0f, 0f, 0f, 255f,  // R = 255
        0f, 0f, 0f, 0f, 255f,  // G = 255
        0f, 0f, 0f, 0f, 255f,  // B = 255
        0f, 0f, 0f, 1f, 0f     // A = unchanged (preserves transparency)
    ))
)
