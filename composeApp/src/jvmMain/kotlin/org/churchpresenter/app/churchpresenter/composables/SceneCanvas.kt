package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.models.Scene
import org.churchpresenter.app.churchpresenter.models.SceneSource
import org.churchpresenter.app.churchpresenter.models.SourceTransform
import androidx.compose.foundation.shape.CircleShape
import java.awt.Cursor
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SceneCanvas(
    modifier: Modifier = Modifier,
    scene: Scene,
    selectedSourceId: String?,
    onSourceSelected: (String?) -> Unit,
    onTransformChanged: (sourceId: String, SourceTransform) -> Unit,
    isInteractive: Boolean = true,
    isPresenter: Boolean = false
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .aspectRatio(scene.canvasWidth.toFloat() / scene.canvasHeight.toFloat())
            .background(Color.Black)
            .onSizeChanged { canvasSize = it }
            .then(
                if (isInteractive) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { onSourceSelected(null) }
                    }
                } else Modifier
            )
    ) {
        val density = LocalDensity.current
        val scale = density.density
        val cw = canvasSize.width.toFloat()
        val ch = canvasSize.height.toFloat()

        // Render sources in order (first = back, last = front)
        scene.sources.forEach { source ->
            if (!source.visible) return@forEach

            val t = source.transform
            val currentTransform by rememberUpdatedState(t)
            val sxDp = with(density) { (t.x * cw).toInt().toDp() }
            val syDp = with(density) { (t.y * ch).toInt().toDp() }
            val swDp = with(density) { (t.width * cw).toInt().toDp() }
            val shDp = with(density) { (t.height * ch).toInt().toDp() }
            val isSelected = isInteractive && source.id == selectedSourceId

            Box(
                modifier = Modifier
                    .offset(sxDp, syDp)
                    .size(width = swDp, height = shDp)
                    // Pointer input BEFORE graphicsLayer so drag is in parent (unrotated) space
                    .then(
                        if (isInteractive && isSelected && !source.locked) {
                            // Selected + unlocked: drag to move
                            Modifier.pointerInput(source.id, isSelected) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (cw > 0 && ch > 0) {
                                            val ct = currentTransform
                                            onTransformChanged(
                                                source.id,
                                                ct.copy(
                                                    x = ct.x + dragAmount.x * scale / cw,
                                                    y = ct.y + dragAmount.y * scale / ch
                                                )
                                            )
                                        }
                                    }
                                )
                            }
                        } else if (isInteractive) {
                            // Not selected or locked: tap to select only
                            Modifier.pointerInput(source.id, isSelected) {
                                detectTapGestures { onSourceSelected(source.id) }
                            }
                        } else Modifier
                    )
                    .graphicsLayer {
                        rotationZ = t.rotation
                        alpha = t.opacity
                    }
                    .then(
                        if (isSelected) Modifier.border(2.dp, Color.Cyan) else Modifier
                    )
            ) {
                SceneSourceRenderer(
                    source = source,
                    isPresenter = isPresenter
                )
            }

            // Resize + rotate handles for selected source
            if (isSelected && !source.locked && cw > 0 && ch > 0) {
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
    }
}

@Composable
private fun ResizeHandles(
    transform: SourceTransform,
    canvasWidth: Float,
    canvasHeight: Float,
    onTransformChanged: (SourceTransform) -> Unit
) {
    val handleSize = 8.dp
    val density = LocalDensity.current
    val scale = density.density
    val currentTransform by rememberUpdatedState(transform)

    data class HandleDef(
        val anchorX: Float, // 0=left, 0.5=center, 1=right
        val anchorY: Float, // 0=top, 0.5=center, 1=bottom
        val cursor: Int,
        val onDrag: (SourceTransform, Offset) -> SourceTransform
    )

    val handles = listOf(
        // Top-left
        HandleDef(0f, 0f, Cursor.NW_RESIZE_CURSOR) { t, d ->
            val dx = d.x / canvasWidth; val dy = d.y / canvasHeight
            t.copy(x = t.x + dx, y = t.y + dy, width = t.width - dx, height = t.height - dy)
        },
        // Top-center
        HandleDef(0.5f, 0f, Cursor.N_RESIZE_CURSOR) { t, d ->
            val dy = d.y / canvasHeight
            t.copy(y = t.y + dy, height = t.height - dy)
        },
        // Top-right
        HandleDef(1f, 0f, Cursor.NE_RESIZE_CURSOR) { t, d ->
            val dx = d.x / canvasWidth; val dy = d.y / canvasHeight
            t.copy(y = t.y + dy, width = t.width + dx, height = t.height - dy)
        },
        // Center-left
        HandleDef(0f, 0.5f, Cursor.W_RESIZE_CURSOR) { t, d ->
            val dx = d.x / canvasWidth
            t.copy(x = t.x + dx, width = t.width - dx)
        },
        // Center-right
        HandleDef(1f, 0.5f, Cursor.E_RESIZE_CURSOR) { t, d ->
            val dx = d.x / canvasWidth
            t.copy(width = t.width + dx)
        },
        // Bottom-left
        HandleDef(0f, 1f, Cursor.SW_RESIZE_CURSOR) { t, d ->
            val dx = d.x / canvasWidth; val dy = d.y / canvasHeight
            t.copy(x = t.x + dx, width = t.width - dx, height = t.height + dy)
        },
        // Bottom-center
        HandleDef(0.5f, 1f, Cursor.S_RESIZE_CURSOR) { t, d ->
            val dy = d.y / canvasHeight
            t.copy(height = t.height + dy)
        },
        // Bottom-right
        HandleDef(1f, 1f, Cursor.SE_RESIZE_CURSOR) { t, d ->
            val dx = d.x / canvasWidth; val dy = d.y / canvasHeight
            t.copy(width = t.width + dx, height = t.height + dy)
        }
    )

    // Rotate handle positions around the source's center
    val centerPx = Offset(
        (transform.x + transform.width / 2f) * canvasWidth,
        (transform.y + transform.height / 2f) * canvasHeight
    )
    val angleRad = Math.toRadians(transform.rotation.toDouble())
    val cosA = cos(angleRad).toFloat()
    val sinA = sin(angleRad).toFloat()

    handles.forEachIndexed { index, handle ->
        // Unrotated handle position in pixels
        val rawX = (transform.x + transform.width * handle.anchorX) * canvasWidth
        val rawY = (transform.y + transform.height * handle.anchorY) * canvasHeight
        // Rotate around center
        val dx = rawX - centerPx.x
        val dy = rawY - centerPx.y
        val rotX = centerPx.x + dx * cosA - dy * sinA
        val rotY = centerPx.y + dx * sinA + dy * cosA

        val hxDp = with(density) { rotX.toInt().toDp() } - handleSize / 2
        val hyDp = with(density) { rotY.toInt().toDp() } - handleSize / 2

        Box(
            modifier = Modifier
                .offset(hxDp, hyDp)
                .size(handleSize)
                .background(Color.White)
                .border(1.dp, Color.Cyan)
                .pointerHoverIcon(PointerIcon(Cursor(handle.cursor)))
                .pointerInput(index) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val scaledDrag = Offset(dragAmount.x * scale, dragAmount.y * scale)
                        val newTransform = handle.onDrag(currentTransform, scaledDrag)
                        val minSize = 0.01f
                        onTransformChanged(
                            newTransform.copy(
                                width = newTransform.width.coerceAtLeast(minSize),
                                height = newTransform.height.coerceAtLeast(minSize)
                            )
                        )
                    }
                }
        )
    }
}

@Composable
private fun RotateHandle(
    transform: SourceTransform,
    canvasWidth: Float,
    canvasHeight: Float,
    onTransformChanged: (SourceTransform) -> Unit
) {
    val handleSize = 10.dp
    val handleOffset = 25.dp // distance above the top-center
    val density = LocalDensity.current
    val currentTransform by rememberUpdatedState(transform)

    // Center of the source in pixels
    val centerPx = Offset(
        (transform.x + transform.width / 2f) * canvasWidth,
        (transform.y + transform.height / 2f) * canvasHeight
    )
    val angleRad = Math.toRadians(transform.rotation.toDouble())
    val cosA = cos(angleRad).toFloat()
    val sinA = sin(angleRad).toFloat()

    // Top-center, offset upward by handleOffset pixels
    val handleOffsetPx = with(density) { handleOffset.toPx() }
    val rawX = (transform.x + transform.width / 2f) * canvasWidth
    val rawY = transform.y * canvasHeight - handleOffsetPx
    // Rotate around center
    val dx = rawX - centerPx.x
    val dy = rawY - centerPx.y
    val rotX = centerPx.x + dx * cosA - dy * sinA
    val rotY = centerPx.y + dx * sinA + dy * cosA

    val hxDp = with(density) { rotX.toInt().toDp() } - handleSize / 2
    val hyDp = with(density) { rotY.toInt().toDp() } - handleSize / 2

    // Track the angle at drag start so rotation is relative, not absolute
    var startAngle by remember { mutableStateOf(0f) }
    var startRotation by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .offset(hxDp, hyDp)
            .size(handleSize)
            .background(Color.Cyan, CircleShape)
            .border(1.dp, Color.White, CircleShape)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { startOffset ->
                        val ct = currentTransform
                        val cx = (ct.x + ct.width / 2f) * canvasWidth
                        val cy = (ct.y + ct.height / 2f) * canvasHeight
                        // Handle center in canvas pixels
                        val aRad = Math.toRadians(ct.rotation.toDouble())
                        val cA = cos(aRad).toFloat()
                        val sA = sin(aRad).toFloat()
                        val rX = (ct.x + ct.width / 2f) * canvasWidth
                        val rY = ct.y * canvasHeight - handleOffsetPx
                        val dxx = rX - cx
                        val dyy = rY - cy
                        val handleCenterX = cx + dxx * cA - dyy * sA
                        val handleCenterY = cy + dxx * sA + dyy * cA
                        startAngle = Math.toDegrees(
                            atan2((handleCenterY - cy).toDouble(), (handleCenterX - cx).toDouble())
                        ).toFloat()
                        startRotation = ct.rotation
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val ct = currentTransform
                        val cx = (ct.x + ct.width / 2f) * canvasWidth
                        val cy = (ct.y + ct.height / 2f) * canvasHeight
                        // Pointer position in canvas pixels
                        val handleSizePx = with(density) { handleSize.toPx() }
                        val hxPx = with(density) { hxDp.toPx() }
                        val hyPx = with(density) { hyDp.toPx() }
                        val pointerX = hxPx + change.position.x + handleSizePx / 2f
                        val pointerY = hyPx + change.position.y + handleSizePx / 2f
                        val currentAngle = Math.toDegrees(
                            atan2((pointerY - cy).toDouble(), (pointerX - cx).toDouble())
                        ).toFloat()
                        val delta = currentAngle - startAngle
                        onTransformChanged(ct.copy(rotation = startRotation + delta))
                    }
                )
            }
    )
}
