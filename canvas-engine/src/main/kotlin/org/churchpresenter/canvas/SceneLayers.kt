package org.churchpresenter.canvas

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform

/**
 * One layer, drawn in its place in the stack. A layer selects itself on a tap; the selected one,
 * unless locked, is moved and resized through [SelectedSourceGrip] instead, drawn above every layer.
 */
@Composable
internal fun SceneSourceLayer(
    source: SceneSource,
    canvasPx: Size,
    fontScale: Float,
    isSelected: Boolean,
    isInteractive: Boolean,
    activeTool: String,
    onSourceSelected: (String?) -> Unit,
) {
    val t = source.transform
    // A layer the grip moves needs no tap of its own; a locked one keeps it.
    val gripMoves = isSelected && !source.locked
    val tapSelects = isInteractive && activeTool == "select" && !gripMoves
    Box(
        modifier = Modifier
            .layerBox(t, canvasPx)
            .then(
                if (tapSelects) {
                    Modifier.pointerInput(source.id) {
                        detectTapGestures { onSourceSelected(source.id) }
                    }
                } else Modifier
            )
            .graphicsLayer {
                rotationZ = t.rotation
                alpha = t.opacity
            }
    ) {
        SceneSourceRenderer(
            source = source,
            fontScale = fontScale,
            showDiagnostics = isInteractive
        )
    }
}

/** Places a box at [t]'s position and size on a canvas [canvasPx] pixels across. */
@Composable
private fun Modifier.layerBox(t: SourceTransform, canvasPx: Size): Modifier {
    val density = LocalDensity.current
    val sxDp = with(density) { (t.x * canvasPx.width).toInt().toDp() }
    val syDp = with(density) { (t.y * canvasPx.height).toInt().toDp() }
    val swDp = with(density) { (t.width * canvasPx.width).toInt().toDp() }
    val shDp = with(density) { (t.height * canvasPx.height).toInt().toDp() }
    return offset(sxDp, syDp).size(width = swDp, height = shDp)
}

/**
 * The selected layer's outline, drag area and handles, drawn above every layer so a layer that
 * another one covers can still be moved and resized once it is selected (#937) -- in the stack, the
 * layer on top took the press. A locked layer gets only its outline.
 */
@Composable
internal fun SelectedSourceGrip(
    sources: List<SceneSource>,
    selectedSourceId: String?,
    canvasPx: Size,
    activeTool: String,
    onTransformChanged: (sourceId: String, SourceTransform) -> Unit,
    onSnapLines: (List<SnapLine>) -> Unit,
) {
    val source = sources.firstOrNull { it.visible && it.id == selectedSourceId } ?: return
    SourceGrip(source, sources, canvasPx, activeTool, onTransformChanged, onSnapLines)
}

@Composable
private fun SourceGrip(
    source: SceneSource,
    sources: List<SceneSource>,
    canvasPx: Size,
    activeTool: String,
    onTransformChanged: (sourceId: String, SourceTransform) -> Unit,
    onSnapLines: (List<SnapLine>) -> Unit,
) {
    val cw = canvasPx.width
    val ch = canvasPx.height
    val t = source.transform
    val currentTransform by rememberUpdatedState(t)
    val editable = !source.locked && activeTool == "select"
    // Option (Alt) held as the press began: a picture set to Fill pans inside its box instead.
    var panning by remember { mutableStateOf(false) }
    val pans = source is SceneSource.ImageSource && source.contentScale == "FILL"
    Box(
        modifier = Modifier
            .layerBox(t, canvasPx)
            .testTag(SELECTED_SOURCE_GRIP_TAG)
            .then(if (editable && pans) Modifier.notePanPress { panning = it } else Modifier)
            // Pointer input BEFORE graphicsLayer so drag is in parent (unrotated) space
            .then(
                if (editable) {
                    Modifier.pointerInput(source.id) {
                        var rawX = 0f
                        var rawY = 0f
                        detectDragGestures(
                            onDragStart = {
                                rawX = currentTransform.x
                                rawY = currentTransform.y
                            },
                            onDragEnd = { onSnapLines(emptyList()) },
                            onDragCancel = { onSnapLines(emptyList()) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (panning && cw > 0 && ch > 0) {
                                    val ct = currentTransform
                                    onTransformChanged(source.id, ct.pannedBy(dragAmount.x, dragAmount.y, cw, ch))
                                } else if (cw > 0 && ch > 0) {
                                    val ct = currentTransform
                                    // Track raw (un-snapped) position so snap doesn't
                                    // cause drift between cursor and element
                                    rawX += dragAmount.x / cw
                                    rawY += dragAmount.y / ch

                                    val snapResult = computeSnap(
                                        SnapBox(rawX, rawY, ct.width, ct.height),
                                        SnapTargets(sources, source.id, cw, ch),
                                    )
                                    onSnapLines(snapResult.snapLines)

                                    onTransformChanged(
                                        source.id,
                                        ct.copy(x = snapResult.x, y = snapResult.y)
                                    )
                                }
                            }
                        )
                    }
                } else Modifier
            )
            .graphicsLayer { rotationZ = t.rotation }
            .border(2.dp, Color.Cyan)
    )

    if (editable && cw > 0 && ch > 0) {
        ResizeHandles(
            transform = t,
            canvasWidth = cw,
            canvasHeight = ch,
            onTransformChanged = { newTransform ->
                onTransformChanged(source.id, newTransform)
            }
        )
        RotateHandle(
            transform = t,
            canvasWidth = cw,
            canvasHeight = ch,
            onTransformChanged = { newTransform ->
                onTransformChanged(source.id, newTransform)
            }
        )
    }
}

/** Test handle for the selected layer's grip -- its drag area, above every layer. */
internal const val SELECTED_SOURCE_GRIP_TAG = "scene_selected_source_grip"

/** Tells [onPress] whether Option (Alt) was held as each press began, before the drag reads it. */
private fun Modifier.notePanPress(onPress: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press) onPress(event.keyboardModifiers.isAltPressed)
        }
    }
}

/**
 * This box with its picture's crop moved as if the picture were dragged [dx], [dy] pixels on a
 * [canvasWidth]×[canvasHeight] canvas: dragging right shows more of the left, so the crop moves left.
 * A drag across the whole box moves the crop all the way.
 */
internal fun SourceTransform.pannedBy(dx: Float, dy: Float, canvasWidth: Float, canvasHeight: Float): SourceTransform {
    val boxWidth = width * canvasWidth
    val boxHeight = height * canvasHeight
    return copy(
        cropX = if (boxWidth > 0f) (cropX - dx / boxWidth).coerceIn(0f, 1f) else cropX,
        cropY = if (boxHeight > 0f) (cropY - dy / boxHeight).coerceIn(0f, 1f) else cropY,
    )
}
