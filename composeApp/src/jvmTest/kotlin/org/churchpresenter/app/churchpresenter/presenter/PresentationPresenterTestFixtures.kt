package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PixelMap
import presentation.engine.model.LayerSpec
import presentation.engine.model.LayerState
import presentation.engine.model.RectPt
import kotlin.math.abs
import kotlin.test.assertTrue

internal fun solidColorBitmap(width: Int, height: Int, color: Color): ImageBitmap =
    ImageBitmap(width, height).also { bitmap ->
        Canvas(bitmap).drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint().apply { this.color = color })
    }

internal fun placedLayer(
    color: Color,
    width: Int = 100,
    height: Int = 100,
    offsetXPx: Int = 0,
    offsetYPx: Int = 0,
    state: LayerState = LayerState.VISIBLE,
    id: String = "layer",
): PlacedLayer = PlacedLayer(
    spec = LayerSpec.StaticComposite(
        id = id,
        zIndex = 0,
        boundsPt = RectPt(0.0, 0.0, width.toDouble(), height.toDouble()),
    ),
    bitmap = solidColorBitmap(width, height, color),
    offsetXPx = offsetXPx,
    offsetYPx = offsetYPx,
    state = state,
)

internal fun assertColorAt(pixelMap: PixelMap, x: Int, y: Int, expected: Color, tolerance: Float = 0.02f) {
    val actual = pixelMap[x, y]
    assertTrue(
        abs(actual.red - expected.red) < tolerance &&
            abs(actual.green - expected.green) < tolerance &&
            abs(actual.blue - expected.blue) < tolerance,
        "expected $expected at ($x, $y) but was $actual",
    )
}

internal fun presentationFrame(
    layers: List<PlacedLayer>,
    frameWidthPx: Int = 100,
    frameHeightPx: Int = 100,
    scalePxPerPt: Float = 1f,
    transition: TransitionOverlay? = null,
): PresentationFrame = PresentationFrame(
    slideIndex = 0,
    frameWidthPx = frameWidthPx,
    frameHeightPx = frameHeightPx,
    scalePxPerPt = scalePxPerPt,
    layers = layers,
    completedSteps = 1,
    stepCount = 1,
    transition = transition,
)
