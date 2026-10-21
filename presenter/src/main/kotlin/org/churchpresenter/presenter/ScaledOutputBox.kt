package org.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.churchpresenter.sharedui.utils.OutputSize

/**
 * [content] laid out at [output]'s own pixel size, one pixel to the dp, then scaled down to fit
 * the space it is given — so a presenter in a preview wraps, fits and spaces exactly as it does on
 * the screen it stands for, only smaller.
 */
@Composable
fun ScaledOutputBox(output: OutputSize, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val scale = minOf(
                    constraints.maxWidth.toFloat() / output.width,
                    constraints.maxHeight.toFloat() / output.height,
                )
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = output.width,
                        maxWidth = output.width,
                        minHeight = output.height,
                        maxHeight = output.height,
                    ),
                )
                layout((output.width * scale).toInt(), (output.height * scale).toInt()) {
                    placeable.placeWithLayer(0, 0) {
                        this.scaleX = scale
                        this.scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            content()
        }
    }
}
