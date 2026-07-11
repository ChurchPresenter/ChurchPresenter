package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import org.churchpresenter.app.churchpresenter.models.AnimationType
import org.churchpresenter.app.churchpresenter.utils.Constants
import presentation.engine.model.Direction
import presentation.engine.model.LayerState
import presentation.engine.model.TransitionType

/**
 * Output renderer for presentations. Draws the animated [frame] (per-layer bitmaps with live
 * alpha/transform/clip from the timeline evaluator) when playback is active; otherwise falls
 * back to the static bitmap path ([SlidePresenter] — cached JPEG frame with the app's
 * configured slide transition), which also covers static decks, freeze, and the moments before
 * a slide's layers finish rasterizing.
 */
@Composable
fun PresentationPresenter(
    frame: PresentationFrame?,
    slide: ImageBitmap?,
    previousSlide: ImageBitmap? = null,
    transitionAlpha: Float = 1f,
    slideOffset: Float = 1f,
    animationType: AnimationType = AnimationType.FADE,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    frozen: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (frame == null || frozen) {
        SlidePresenter(
            modifier = modifier,
            slide = if (frozen) null else slide,
            previousSlide = if (frozen) null else previousSlide,
            transitionAlpha = transitionAlpha,
            slideOffset = slideOffset,
            animationType = animationType,
            outputRole = outputRole
        )
        return
    }

    if (outputRole == Constants.OUTPUT_ROLE_KEY) {
        // Key output: full-white key while a slide is live — identical to the static path.
        Box(modifier = modifier.fillMaxSize().background(Color.White))
        return
    }

    Canvas(modifier = modifier.fillMaxSize().background(Color.Black)) {
        drawAnimatedFrame(frame)
    }
}

private fun DrawScope.drawAnimatedFrame(frame: PresentationFrame) {
    // Letterbox the slide frame into the window.
    val windowW = size.width
    val windowH = size.height
    if (windowW <= 0f || windowH <= 0f) return
    val fit = minOf(windowW / frame.frameWidthPx, windowH / frame.frameHeightPx)
    val slideW = frame.frameWidthPx * fit
    val slideH = frame.frameHeightPx * fit
    val originX = (windowW - slideW) / 2f
    val originY = (windowH - slideH) / 2f

    clipRect(originX, originY, originX + slideW, originY + slideH) {
        translate(originX, originY) {
            scale(fit, fit, pivot = Offset.Zero) {
                val transition = frame.transition
                if (transition == null) {
                    drawLayerGroup(frame, frame.layers)
                } else {
                    drawTransition(frame, transition)
                }
            }
        }
    }
}

/** Composites the deck-defined slide transition: outgoing layers vs incoming layers. */
private fun DrawScope.drawTransition(frame: PresentationFrame, transition: TransitionOverlay) {
    val w = frame.frameWidthPx.toFloat()
    val h = frame.frameHeightPx.toFloat()
    val p = transition.progress
    // Unit vector of the incoming slide's movement.
    val (dx, dy) = when (transition.direction) {
        Direction.LEFT -> -1f to 0f
        Direction.RIGHT -> 1f to 0f
        Direction.UP -> 0f to -1f
        Direction.DOWN -> 0f to 1f
        else -> -1f to 0f
    }
    when (transition.type) {
        TransitionType.FADE -> {
            drawLayerGroup(frame, transition.fromLayers)
            drawLayerGroup(frame, frame.layers, alphaMultiplier = p)
        }
        TransitionType.PUSH -> {
            translate(dx * p * w, dy * p * h) { drawLayerGroup(frame, transition.fromLayers) }
            translate(dx * (p - 1f) * w, dy * (p - 1f) * h) { drawLayerGroup(frame, frame.layers) }
        }
        TransitionType.COVER -> {
            drawLayerGroup(frame, transition.fromLayers)
            translate(dx * (p - 1f) * w, dy * (p - 1f) * h) { drawLayerGroup(frame, frame.layers) }
        }
        TransitionType.WIPE -> {
            drawLayerGroup(frame, transition.fromLayers)
            val reveal = when (transition.direction) {
                Direction.RIGHT -> floatArrayOf(0f, 0f, w * p, h)
                Direction.LEFT -> floatArrayOf(w * (1f - p), 0f, w, h)
                Direction.UP -> floatArrayOf(0f, h * (1f - p), w, h)
                else -> floatArrayOf(0f, 0f, w, h * p) // DOWN and default
            }
            clipRect(reveal[0], reveal[1], reveal[2], reveal[3]) { drawLayerGroup(frame, frame.layers) }
        }
        TransitionType.SPLIT -> {
            drawLayerGroup(frame, transition.fromLayers)
            val horizontalBands = transition.direction != Direction.LEFT && transition.direction != Direction.RIGHT
            if (horizontalBands) {
                clipRect(0f, h * (0.5f - p / 2f), w, h * (0.5f + p / 2f)) { drawLayerGroup(frame, frame.layers) }
            } else {
                clipRect(w * (0.5f - p / 2f), 0f, w * (0.5f + p / 2f), h) { drawLayerGroup(frame, frame.layers) }
            }
        }
        TransitionType.NONE -> drawLayerGroup(frame, frame.layers)
    }
}

private fun DrawScope.drawLayerGroup(
    frame: PresentationFrame,
    layers: List<PlacedLayer>,
    alphaMultiplier: Float = 1f
) {
    for (layer in layers) {
        drawLayer(frame, layer, alphaMultiplier)
    }
}

private fun DrawScope.drawLayer(frame: PresentationFrame, layer: PlacedLayer, alphaMultiplier: Float = 1f) {
    val state: LayerState = layer.state
    val alpha = (state.alpha.toFloat() * alphaMultiplier).coerceIn(0f, 1f)
    if (alpha <= 0f) return

    val bitmapW = layer.bitmap.width.toFloat()
    val bitmapH = layer.bitmap.height.toFloat()
    val baseX = layer.offsetXPx + state.translateXPt.toFloat() * frame.scalePxPerPt
    val baseY = layer.offsetYPx + state.translateYPt.toFloat() * frame.scalePxPerPt

    translate(baseX, baseY) {
        // Transforms compose about the layer's center (in its translated position).
        rotate(state.rotationDeg.toFloat(), pivot = Offset(bitmapW / 2f, bitmapH / 2f)) {
            scale(
                state.scaleX.toFloat(), state.scaleY.toFloat(),
                pivot = Offset(bitmapW / 2f, bitmapH / 2f)
            ) {
                val clip = state.clip
                if (clip != null) {
                    clipRect(
                        left = (clip.left * bitmapW).toFloat(),
                        top = (clip.top * bitmapH).toFloat(),
                        right = (clip.right * bitmapW).toFloat(),
                        bottom = (clip.bottom * bitmapH).toFloat()
                    ) {
                        drawImage(image = layer.bitmap, alpha = alpha)
                    }
                } else {
                    drawImage(image = layer.bitmap, alpha = alpha)
                }
            }
        }
    }
}
