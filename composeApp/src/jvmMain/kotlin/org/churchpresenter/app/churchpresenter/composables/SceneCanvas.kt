package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.models.PathPoint
import org.churchpresenter.app.churchpresenter.models.Scene
import org.churchpresenter.app.churchpresenter.models.SceneSource
import org.churchpresenter.app.churchpresenter.models.SourceTransform
import androidx.compose.foundation.shape.CircleShape
import java.awt.Cursor
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class SnapLine(
    val orientation: SnapOrientation,
    val position: Float // normalized 0-1
)

enum class SnapOrientation { HORIZONTAL, VERTICAL }

private const val SNAP_THRESHOLD_PX = 6f

@Composable
fun SceneCanvas(
    modifier: Modifier = Modifier,
    scene: Scene,
    selectedSourceId: String?,
    onSourceSelected: (String?) -> Unit,
    onTransformChanged: (sourceId: String, SourceTransform) -> Unit,
    isInteractive: Boolean = true,
    isPresenter: Boolean = false,
    activeTool: String = "select",
    drawingStrokeColor: String = "#FFFFFF",
    drawingFillColor: String = "#00000000",
    drawingStrokeWidth: Float = 3f,
    onShapeDrawn: ((SceneSource.ShapeSource) -> Unit)? = null
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeSnapLines by remember { mutableStateOf<List<SnapLine>>(emptyList()) }

    // Drawing state
    var drawingInProgress by remember { mutableStateOf(false) }
    var drawStartNorm by remember { mutableStateOf(Offset.Zero) }
    var drawCurrentNorm by remember { mutableStateOf(Offset.Zero) }
    var freehandPoints by remember { mutableStateOf<List<PathPoint>>(emptyList()) }

    Box(
        modifier = modifier
            .aspectRatio(scene.canvasWidth.toFloat() / scene.canvasHeight.toFloat())
            .background(Color.Black)
            .onSizeChanged { canvasSize = it }
            .then(
                if (isInteractive && activeTool == "select") {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { onSourceSelected(null) }
                    }
                } else if (isInteractive && activeTool != "select") {
                    // Drawing mode pointer input
                    Modifier.pointerInput(activeTool, drawingStrokeColor, drawingFillColor, drawingStrokeWidth) {
                        val cw = canvasSize.width.toFloat()
                        val ch = canvasSize.height.toFloat()
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (cw > 0 && ch > 0) {
                                    val density = this@pointerInput.density
                                    val normX = offset.x * density / cw
                                    val normY = offset.y * density / ch
                                    drawStartNorm = Offset(normX, normY)
                                    drawCurrentNorm = Offset(normX, normY)
                                    drawingInProgress = true
                                    if (activeTool == "freehand") {
                                        freehandPoints = listOf(PathPoint(normX, normY))
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (cw > 0 && ch > 0) {
                                    val density = this@pointerInput.density
                                    val normX = change.position.x * density / cw
                                    val normY = change.position.y * density / ch
                                    drawCurrentNorm = Offset(normX, normY)
                                    if (activeTool == "freehand") {
                                        freehandPoints = freehandPoints + PathPoint(normX, normY)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (drawingInProgress) {
                                    drawingInProgress = false
                                    val x = minOf(drawStartNorm.x, drawCurrentNorm.x)
                                    val y = minOf(drawStartNorm.y, drawCurrentNorm.y)
                                    val w = abs(drawCurrentNorm.x - drawStartNorm.x).coerceAtLeast(0.01f)
                                    val h = abs(drawCurrentNorm.y - drawStartNorm.y).coerceAtLeast(0.01f)

                                    val shape = SceneSource.ShapeSource(
                                        id = UUID.randomUUID().toString(),
                                        name = activeTool.replaceFirstChar { it.uppercase() },
                                        transform = when (activeTool) {
                                            "line", "arrow" -> SourceTransform(
                                                x = drawStartNorm.x, y = drawStartNorm.y,
                                                width = drawCurrentNorm.x - drawStartNorm.x,
                                                height = drawCurrentNorm.y - drawStartNorm.y
                                            )
                                            "freehand" -> {
                                                val minX = freehandPoints.minOf { it.x }
                                                val minY = freehandPoints.minOf { it.y }
                                                val maxX = freehandPoints.maxOf { it.x }
                                                val maxY = freehandPoints.maxOf { it.y }
                                                SourceTransform(
                                                    x = minX, y = minY,
                                                    width = (maxX - minX).coerceAtLeast(0.01f),
                                                    height = (maxY - minY).coerceAtLeast(0.01f)
                                                )
                                            }
                                            else -> SourceTransform(x = x, y = y, width = w, height = h)
                                        },
                                        shapeType = activeTool,
                                        strokeColor = drawingStrokeColor,
                                        fillColor = drawingFillColor,
                                        strokeWidth = drawingStrokeWidth,
                                        points = if (activeTool == "freehand") {
                                            // Normalize points relative to bounding box
                                            val minX = freehandPoints.minOf { it.x }
                                            val minY = freehandPoints.minOf { it.y }
                                            val rangeX = (freehandPoints.maxOf { it.x } - minX).coerceAtLeast(0.01f)
                                            val rangeY = (freehandPoints.maxOf { it.y } - minY).coerceAtLeast(0.01f)
                                            freehandPoints.map { p ->
                                                PathPoint((p.x - minX) / rangeX, (p.y - minY) / rangeY)
                                            }
                                        } else emptyList()
                                    )
                                    onShapeDrawn?.invoke(shape)
                                    freehandPoints = emptyList()
                                }
                            }
                        )
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
                        if (isInteractive && activeTool == "select" && isSelected && !source.locked) {
                            // Selected + unlocked: drag to move with snapping
                            Modifier.pointerInput(source.id, isSelected) {
                                detectDragGestures(
                                    onDragEnd = { activeSnapLines = emptyList() },
                                    onDragCancel = { activeSnapLines = emptyList() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (cw > 0 && ch > 0) {
                                            val ct = currentTransform
                                            var newX = ct.x + dragAmount.x * scale / cw
                                            var newY = ct.y + dragAmount.y * scale / ch

                                            // Snap logic
                                            val snapResult = computeSnap(
                                                newX, newY, ct.width, ct.height,
                                                scene.sources, source.id, cw, ch
                                            )
                                            newX = snapResult.x
                                            newY = snapResult.y
                                            activeSnapLines = snapResult.snapLines

                                            onTransformChanged(
                                                source.id,
                                                ct.copy(x = newX, y = newY)
                                            )
                                        }
                                    }
                                )
                            }
                        } else if (isInteractive && activeTool == "select") {
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
            if (isSelected && !source.locked && cw > 0 && ch > 0 && activeTool == "select") {
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

        // Draw snap guide lines
        if (activeSnapLines.isNotEmpty() && cw > 0 && ch > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                activeSnapLines.forEach { snap ->
                    when (snap.orientation) {
                        SnapOrientation.VERTICAL -> {
                            val px = snap.position * size.width
                            drawLine(
                                color = Color.Magenta,
                                start = Offset(px, 0f),
                                end = Offset(px, size.height),
                                strokeWidth = 1f,
                                pathEffect = dashEffect
                            )
                        }
                        SnapOrientation.HORIZONTAL -> {
                            val py = snap.position * size.height
                            drawLine(
                                color = Color.Magenta,
                                start = Offset(0f, py),
                                end = Offset(size.width, py),
                                strokeWidth = 1f,
                                pathEffect = dashEffect
                            )
                        }
                    }
                }
            }
        }

        // Drawing preview overlay
        if (drawingInProgress && cw > 0 && ch > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val previewColor = Color.Cyan.copy(alpha = 0.7f)
                val previewStroke = Stroke(width = 2f)

                when (activeTool) {
                    "rectangle" -> {
                        val left = minOf(drawStartNorm.x, drawCurrentNorm.x) * size.width
                        val top = minOf(drawStartNorm.y, drawCurrentNorm.y) * size.height
                        val right = maxOf(drawStartNorm.x, drawCurrentNorm.x) * size.width
                        val bottom = maxOf(drawStartNorm.y, drawCurrentNorm.y) * size.height
                        drawRect(
                            color = previewColor,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = previewStroke
                        )
                    }
                    "ellipse" -> {
                        val left = minOf(drawStartNorm.x, drawCurrentNorm.x) * size.width
                        val top = minOf(drawStartNorm.y, drawCurrentNorm.y) * size.height
                        val right = maxOf(drawStartNorm.x, drawCurrentNorm.x) * size.width
                        val bottom = maxOf(drawStartNorm.y, drawCurrentNorm.y) * size.height
                        drawOval(
                            color = previewColor,
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = previewStroke
                        )
                    }
                    "line", "arrow" -> {
                        drawLine(
                            color = previewColor,
                            start = Offset(drawStartNorm.x * size.width, drawStartNorm.y * size.height),
                            end = Offset(drawCurrentNorm.x * size.width, drawCurrentNorm.y * size.height),
                            strokeWidth = 2f
                        )
                    }
                    "freehand" -> {
                        if (freehandPoints.size >= 2) {
                            val path = Path().apply {
                                moveTo(freehandPoints[0].x * size.width, freehandPoints[0].y * size.height)
                                for (i in 1 until freehandPoints.size) {
                                    lineTo(freehandPoints[i].x * size.width, freehandPoints[i].y * size.height)
                                }
                            }
                            drawPath(path, color = previewColor, style = previewStroke)
                        }
                    }
                }
            }
        }
    }
}

// --- Snap computation ---

private data class SnapResult(val x: Float, val y: Float, val snapLines: List<SnapLine>)

private fun computeSnap(
    x: Float, y: Float, w: Float, h: Float,
    sources: List<SceneSource>, excludeId: String,
    canvasWidth: Float, canvasHeight: Float
): SnapResult {
    val threshold = SNAP_THRESHOLD_PX / canvasWidth // normalize threshold

    // Collect snap targets
    val vTargets = mutableListOf(0f, 0.5f, 1f) // canvas left, center, right
    val hTargets = mutableListOf(0f, 0.5f, 1f) // canvas top, center, bottom

    sources.forEach { s ->
        if (s.id == excludeId || !s.visible) return@forEach
        val st = s.transform
        vTargets.addAll(listOf(st.x, st.x + st.width / 2f, st.x + st.width))
        hTargets.addAll(listOf(st.y, st.y + st.height / 2f, st.y + st.height))
    }

    var snappedX = x
    var snappedY = y
    val lines = mutableListOf<SnapLine>()

    // Source edges/center for snapping
    val sourceLeft = x
    val sourceCenterX = x + w / 2f
    val sourceRight = x + w
    val sourceTop = y
    val sourceCenterY = y + h / 2f
    val sourceBottom = y + h

    // Vertical snap (X axis)
    var bestVDist = threshold
    for (target in vTargets) {
        for (edge in listOf(sourceLeft, sourceCenterX, sourceRight)) {
            val dist = abs(edge - target)
            if (dist < bestVDist) {
                bestVDist = dist
                snappedX = x + (target - edge)
                lines.removeAll { it.orientation == SnapOrientation.VERTICAL }
                lines.add(SnapLine(SnapOrientation.VERTICAL, target))
            }
        }
    }

    // Horizontal snap (Y axis)
    val hThreshold = SNAP_THRESHOLD_PX / canvasHeight
    var bestHDist = hThreshold
    for (target in hTargets) {
        for (edge in listOf(sourceTop, sourceCenterY, sourceBottom)) {
            val dist = abs(edge - target)
            if (dist < bestHDist) {
                bestHDist = dist
                snappedY = y + (target - edge)
                lines.removeAll { it.orientation == SnapOrientation.HORIZONTAL }
                lines.add(SnapLine(SnapOrientation.HORIZONTAL, target))
            }
        }
    }

    return SnapResult(snappedX, snappedY, lines)
}

// --- Resize Handles ---

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
        val anchorX: Float,
        val anchorY: Float,
        val cursor: Int,
        val onDrag: (SourceTransform, Offset) -> SourceTransform
    )

    val handles = listOf(
        HandleDef(0f, 0f, Cursor.NW_RESIZE_CURSOR) { t, d ->
            val dx = d.x / canvasWidth; val dy = d.y / canvasHeight
            t.copy(x = t.x + dx, y = t.y + dy, width = t.width - dx, height = t.height - dy)
        },
        HandleDef(0.5f, 0f, Cursor.N_RESIZE_CURSOR) { t, d ->
            val dy = d.y / canvasHeight
            t.copy(y = t.y + dy, height = t.height - dy)
        },
        HandleDef(1f, 0f, Cursor.NE_RESIZE_CURSOR) { t, d ->
            val dx = d.x / canvasWidth; val dy = d.y / canvasHeight
            t.copy(y = t.y + dy, width = t.width + dx, height = t.height - dy)
        },
        HandleDef(0f, 0.5f, Cursor.W_RESIZE_CURSOR) { t, d ->
            val dx = d.x / canvasWidth
            t.copy(x = t.x + dx, width = t.width - dx)
        },
        HandleDef(1f, 0.5f, Cursor.E_RESIZE_CURSOR) { t, d ->
            val dx = d.x / canvasWidth
            t.copy(width = t.width + dx)
        },
        HandleDef(0f, 1f, Cursor.SW_RESIZE_CURSOR) { t, d ->
            val dx = d.x / canvasWidth; val dy = d.y / canvasHeight
            t.copy(x = t.x + dx, width = t.width - dx, height = t.height + dy)
        },
        HandleDef(0.5f, 1f, Cursor.S_RESIZE_CURSOR) { t, d ->
            val dy = d.y / canvasHeight
            t.copy(height = t.height + dy)
        },
        HandleDef(1f, 1f, Cursor.SE_RESIZE_CURSOR) { t, d ->
            val dx = d.x / canvasWidth; val dy = d.y / canvasHeight
            t.copy(width = t.width + dx, height = t.height + dy)
        }
    )

    val centerPx = Offset(
        (transform.x + transform.width / 2f) * canvasWidth,
        (transform.y + transform.height / 2f) * canvasHeight
    )
    val angleRad = Math.toRadians(transform.rotation.toDouble())
    val cosA = cos(angleRad).toFloat()
    val sinA = sin(angleRad).toFloat()

    handles.forEachIndexed { index, handle ->
        val rawX = (transform.x + transform.width * handle.anchorX) * canvasWidth
        val rawY = (transform.y + transform.height * handle.anchorY) * canvasHeight
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
    val handleOffset = 25.dp
    val density = LocalDensity.current
    val currentTransform by rememberUpdatedState(transform)

    val centerPx = Offset(
        (transform.x + transform.width / 2f) * canvasWidth,
        (transform.y + transform.height / 2f) * canvasHeight
    )
    val angleRad = Math.toRadians(transform.rotation.toDouble())
    val cosA = cos(angleRad).toFloat()
    val sinA = sin(angleRad).toFloat()

    val handleOffsetPx = with(density) { handleOffset.toPx() }
    val rawX = (transform.x + transform.width / 2f) * canvasWidth
    val rawY = transform.y * canvasHeight - handleOffsetPx
    val dx = rawX - centerPx.x
    val dy = rawY - centerPx.y
    val rotX = centerPx.x + dx * cosA - dy * sinA
    val rotY = centerPx.y + dx * sinA + dy * cosA

    val hxDp = with(density) { rotX.toInt().toDp() } - handleSize / 2
    val hyDp = with(density) { rotY.toInt().toDp() } - handleSize / 2

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
