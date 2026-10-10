package org.churchpresenter.sharedui.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * Draws [content] as a 1920x1080-family output would, scaled to the room it actually has.
 *
 * For presenters whose sizes are stored as plain points -- the Q&A question, the dictionary card,
 * the captions, the announcement -- rather than multiplied by [presenterScale] as the song and Bible
 * presenters do. [content] is laid out on a canvas of this box's size divided by [referenceScale],
 * and the result is scaled back into the box. A full-HD output (or a 1080x1920 portrait one), where
 * the scale is 1, is drawn exactly as [content] draws itself; a dev window, a 4K screen or a preview
 * tile shows the same picture at its own size, rather than the same points on more or less room.
 */
@Composable
fun ReferenceScaledBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = referenceScale(maxWidth, maxHeight)
        if (scale == 1f) {
            content()
            return@BoxWithConstraints
        }
        Box(
            Modifier.layout { measurable, constraints ->
                val width = constraints.maxWidth
                val height = constraints.maxHeight
                val placeable = measurable.measure(
                    Constraints.fixed((width / scale).roundToInt(), (height / scale).roundToInt()),
                )
                layout(width, height) {
                    placeable.placeWithLayer(0, 0) {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            },
        ) { content() }
    }
}

/**
 * The scale [ReferenceScaledBox] draws at: [presenterScale] against 1920x1080 for a landscape output,
 * and against 1080x1920 for a portrait one -- so a portrait screen lays its text out as a portrait
 * output does, wrapped to its own width, instead of as a 1920-wide line shrunk to fit.
 */
fun referenceScale(width: Dp, height: Dp): Float =
    if (height > width) {
        minOf(width.value / REFERENCE_HEIGHT, height.value / BACKGROUND_REFERENCE_WIDTH)
            .coerceIn(MIN_PRESENTER_SCALE, MAX_PRESENTER_SCALE)
    } else {
        presenterScale(width, height)
    }
