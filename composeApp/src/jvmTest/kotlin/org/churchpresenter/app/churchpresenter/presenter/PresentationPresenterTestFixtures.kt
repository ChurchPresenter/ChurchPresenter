package org.churchpresenter.app.churchpresenter.presenter


import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import org.churchpresenter.presentationengine.model.LayerSpec
import org.churchpresenter.presentationengine.model.LayerState
import org.churchpresenter.presentationengine.model.RectPt

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
