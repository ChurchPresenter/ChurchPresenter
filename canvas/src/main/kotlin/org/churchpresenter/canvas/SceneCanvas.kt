package org.churchpresenter.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import org.churchpresenter.core.models.scene.PathPoint
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.core.models.scene.forArea
import androidx.compose.foundation.shape.CircleShape
import java.awt.Cursor
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val HANDLE_MIDPOINT = 0.5f

data class SnapLine(
    val orientation: SnapOrientation,
    val position: Float // normalized 0-1
)

enum class SnapOrientation { HORIZONTAL, VERTICAL }

private const val SNAP_THRESHOLD_PX = 6f

/** The smallest a drawn shape is made, as a fraction of the canvas, so a click still leaves one. */
private const val MIN_SHAPE_EXTENT = 0.01f

/** Test handle for the canvas itself -- the scene-shaped surface, not the area it is centred in. */
internal const val SCENE_CANVAS_TAG = "scene_canvas"

/**
 * Draws [scene] centred in the area [modifier] gives it, editable when [isInteractive].
 *
 * @param autoLayout whether to draw whichever of the scene's layouts suits the area's shape (see
 *   [forArea]) — so an output picks its layout from its own shape. The editor turns it off and
 *   passes each layout itself.
 */
@Composable
fun SceneCanvas(
    modifier: Modifier = Modifier,
    scene: Scene,
    selectedSourceId: String?,
    onSourceSelected: (String?) -> Unit,
    onTransformChanged: (sourceId: String, SourceTransform) -> Unit,
    isInteractive: Boolean = true,
    activeTool: String = "select",
    drawingStrokeColor: String = "#FFFFFF",
    drawingFillColor: String = "#00000000",
    drawingStrokeWidth: Float = 3f,
    onShapeDrawn: ((SceneSource.ShapeSource) -> Unit)? = null,
    autoLayout: Boolean = true,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeSnapLines by remember { mutableStateOf<List<SnapLine>>(emptyList()) }

    // Drawing state
    var drawingInProgress by remember { mutableStateOf(false) }
    var drawStartNorm by remember { mutableStateOf(Offset.Zero) }
    var drawCurrentNorm by remember { mutableStateOf(Offset.Zero) }
    var freehandPoints by remember { mutableStateOf<List<PathPoint>>(emptyList()) }

    // The caller's modifier sizes the area and this centres the canvas in it. Applied to the canvas
    // itself, a caller's `fillMaxSize()` pinned the minimum size to the whole area, so `aspectRatio`
    // could not shrink to fit and sized from the width instead -- a portrait scene came out taller
    // than its area, spilling over the rows around it and cropped to a band on a landscape output.
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // A scene with a landscape and a portrait layout draws the one this area's shape calls for.
        val drawn = if (autoLayout) scene.forArea(maxWidth.value, maxHeight.value) else scene
        Box(
            modifier = Modifier
                .aspectRatio(drawn.canvasWidth.toFloat() / drawn.canvasHeight.toFloat())
                .testTag(SCENE_CANVAS_TAG)
                .clipToBounds()
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
                                        val shape = drawnShape(
                                            tool = activeTool,
                                            stroke = ShapeStroke(
                                                drawingStrokeColor, drawingFillColor, drawingStrokeWidth,
                                            ),
                                            start = drawStartNorm,
                                            end = drawCurrentNorm,
                                            freehand = freehandPoints,
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
            val cw = canvasSize.width.toFloat()
            val ch = canvasSize.height.toFloat()
            val fontScale =
                if (drawn.canvasWidth > 0 && cw > 0) (cw / density.density) / drawn.canvasWidth.toFloat() else 1f

            // Render sources in order (first = back, last = front)
            drawn.sources.forEach { source ->
                if (!source.visible) return@forEach
                SceneSourceLayer(
                    source = source,
                    sources = drawn.sources,
                    canvasPx = Size(cw, ch),
                    fontScale = fontScale,
                    isSelected = isInteractive && source.id == selectedSourceId,
                    isInteractive = isInteractive,
                    activeTool = activeTool,
                    onSourceSelected = onSourceSelected,
                    onTransformChanged = onTransformChanged,
                    onSnapLines = { activeSnapLines = it },
                )
            }

            // Draw snap guide lines
            if (activeSnapLines.isNotEmpty() && cw > 0 && ch > 0) SnapGuides(activeSnapLines)

            // Drawing preview overlay
            if (drawingInProgress && cw > 0 && ch > 0) {
                DrawingPreview(activeTool, drawStartNorm, drawCurrentNorm, freehandPoints)
            }
        }
    }
}

/** How a drawn shape is outlined and filled -- the toolbar's current colours and width. */
internal data class ShapeStroke(val color: String, val fill: String, val width: Float)

/**
 * The shape the [tool] drew from [start] to [end] (or along [freehand]), as fractions of the canvas:
 * a line or arrow keeps its two ends and a freehand stroke its points, each relative to the box.
 */
internal fun drawnShape(
    tool: String,
    stroke: ShapeStroke,
    start: Offset,
    end: Offset,
    freehand: List<PathPoint>,
): SceneSource.ShapeSource {
    val x = minOf(start.x, end.x)
    val y = minOf(start.y, end.y)
    val w = abs(end.x - start.x).coerceAtLeast(MIN_SHAPE_EXTENT)
    val h = abs(end.y - start.y).coerceAtLeast(MIN_SHAPE_EXTENT)

    return SceneSource.ShapeSource(
        id = UUID.randomUUID().toString(),
        name = tool.replaceFirstChar { it.uppercase() },
        transform = when (tool) {
            "line", "arrow" -> {
                val minX = minOf(start.x, end.x)
                val minY = minOf(start.y, end.y)
                val maxX = maxOf(start.x, end.x)
                val maxY = maxOf(start.y, end.y)
                SourceTransform(
                    x = minX, y = minY,
                    width = (maxX - minX).coerceAtLeast(MIN_SHAPE_EXTENT),
                    height = (maxY - minY).coerceAtLeast(MIN_SHAPE_EXTENT)
                )
            }
            "freehand" -> {
                val minX = freehand.minOf { it.x }
                val minY = freehand.minOf { it.y }
                val maxX = freehand.maxOf { it.x }
                val maxY = freehand.maxOf { it.y }
                SourceTransform(
                    x = minX, y = minY,
                    width = (maxX - minX).coerceAtLeast(MIN_SHAPE_EXTENT),
                    height = (maxY - minY).coerceAtLeast(MIN_SHAPE_EXTENT)
                )
            }
            else -> SourceTransform(x = x, y = y, width = w, height = h)
        },
        shapeType = tool,
        strokeColor = stroke.color,
        fillColor = stroke.fill,
        strokeWidth = stroke.width,
        points = if (tool == "line" || tool == "arrow") {
            // Store start/end as normalized points within bounding box
            val minX = minOf(start.x, end.x)
            val minY = minOf(start.y, end.y)
            val rangeX = (maxOf(start.x, end.x) - minX)
                .coerceAtLeast(MIN_SHAPE_EXTENT)
            val rangeY = (maxOf(start.y, end.y) - minY)
                .coerceAtLeast(MIN_SHAPE_EXTENT)
            listOf(
                PathPoint(
                    (start.x - minX) / rangeX,
                    (start.y - minY) / rangeY
                ),
                PathPoint(
                    (end.x - minX) / rangeX,
                    (end.y - minY) / rangeY
                )
            )
        } else if (tool == "freehand") {
            // Normalize points relative to bounding box
            val minX = freehand.minOf { it.x }
            val minY = freehand.minOf { it.y }
            val rangeX = (freehand.maxOf { it.x } - minX).coerceAtLeast(MIN_SHAPE_EXTENT)
            val rangeY = (freehand.maxOf { it.y } - minY).coerceAtLeast(MIN_SHAPE_EXTENT)
            freehand.map { p ->
                PathPoint((p.x - minX) / rangeX, (p.y - minY) / rangeY)
            }
        } else emptyList()
    )
}

@Composable
private fun SnapGuides(snapLines: List<SnapLine>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
        snapLines.forEach { snap ->
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

@Composable
private fun DrawingPreview(tool: String, start: Offset, end: Offset, freehand: List<PathPoint>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val previewColor = Color.Cyan.copy(alpha = 0.7f)
        val previewStroke = Stroke(width = 2f)

        when (tool) {
            "rectangle" -> {
                val left = minOf(start.x, end.x) * size.width
                val top = minOf(start.y, end.y) * size.height
                val right = maxOf(start.x, end.x) * size.width
                val bottom = maxOf(start.y, end.y) * size.height
                drawRect(
                    color = previewColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = previewStroke
                )
            }
            "ellipse" -> {
                val left = minOf(start.x, end.x) * size.width
                val top = minOf(start.y, end.y) * size.height
                val right = maxOf(start.x, end.x) * size.width
                val bottom = maxOf(start.y, end.y) * size.height
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
                    start = Offset(start.x * size.width, start.y * size.height),
                    end = Offset(end.x * size.width, end.y * size.height),
                    strokeWidth = 2f
                )
            }
            "freehand" -> {
                if (freehand.size >= 2) {
                    val path = Path().apply {
                        moveTo(freehand[0].x * size.width, freehand[0].y * size.height)
                        for (i in 1 until freehand.size) {
                            lineTo(freehand[i].x * size.width, freehand[i].y * size.height)
                        }
                    }
                    drawPath(path, color = previewColor, style = previewStroke)
                }
            }
        }
    }
}

/** One visible source on the canvas: drawn in place, and -- when selected -- dragged, resized and rotated. */
@Composable
private fun SceneSourceLayer(
    source: SceneSource,
    sources: List<SceneSource>,
    canvasPx: Size,
    fontScale: Float,
    isSelected: Boolean,
    isInteractive: Boolean,
    activeTool: String,
    onSourceSelected: (String?) -> Unit,
    onTransformChanged: (sourceId: String, SourceTransform) -> Unit,
    onSnapLines: (List<SnapLine>) -> Unit,
) {
    val density = LocalDensity.current
    val cw = canvasPx.width
    val ch = canvasPx.height
    val t = source.transform
    val currentTransform by rememberUpdatedState(t)
    val sxDp = with(density) { (t.x * cw).toInt().toDp() }
    val syDp = with(density) { (t.y * ch).toInt().toDp() }
    val swDp = with(density) { (t.width * cw).toInt().toDp() }
    val shDp = with(density) { (t.height * ch).toInt().toDp() }

    val editableForDrag = isSelected && !source.locked && activeTool == "select"
    Box(
        modifier = Modifier
            .offset(sxDp, syDp)
            .size(width = swDp, height = shDp)
            // Pointer input BEFORE graphicsLayer so drag is in parent (unrotated) space
            .then(
                if (isInteractive && editableForDrag) {
                    // Selected + unlocked: drag to move with snapping
                    Modifier.pointerInput(source.id, isSelected) {
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
                                if (cw > 0 && ch > 0) {
                                    val ct = currentTransform
                                    // Track raw (un-snapped) position so snap doesn't
                                    // cause drift between cursor and element
                                    rawX += dragAmount.x / cw
                                    rawY += dragAmount.y / ch

                                    // Snap logic
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
            fontScale = fontScale,
            showDiagnostics = isInteractive
        )
    }

    // Resize + rotate handles for selected source
    val resizable = isSelected && !source.locked
    val hasArea = cw > 0 && ch > 0
    if (resizable && hasArea && activeTool == "select") {
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

// --- Snap computation ---

internal data class SnapResult(val x: Float, val y: Float, val snapLines: List<SnapLine>)

/** The box being dragged, as fractions of the canvas. */
internal data class SnapBox(val x: Float, val y: Float, val w: Float, val h: Float)

/** What a dragged box can snap to: every other visible source, on a canvas this many pixels wide and high. */
internal data class SnapTargets(
    val sources: List<SceneSource>,
    val excludeId: String,
    val canvasWidth: Float,
    val canvasHeight: Float,
)

// internal (not private) so the snap geometry can be unit-tested directly; no behaviour change.
internal fun computeSnap(box: SnapBox, targets: SnapTargets): SnapResult {
    val x = box.x
    val y = box.y
    val w = box.w
    val h = box.h
    val sources = targets.sources
    val excludeId = targets.excludeId
    val canvasWidth = targets.canvasWidth
    val canvasHeight = targets.canvasHeight
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

/**
 * One of the eight resize grips around a selected source: where it sits on the source's box, the
 * cursor it shows, and what a drag of it does to the transform.
 */
internal data class ResizeHandleDef(
    /** 0, 0.5 or 1 along the source's width — 0 is its left edge. */
    val anchorX: Float,
    /** 0, 0.5 or 1 along the source's height — 0 is its top edge. */
    val anchorY: Float,
    val cursor: Int,
    val onDrag: (SourceTransform, Offset) -> SourceTransform
)

/**
 * The eight resize handles, in the order they are drawn: NW, N, NE, W, E, SW, S, SE.
 *
 * Each `onDrag` takes a pixel drag and returns the new transform in normalised 0..1 coordinates,
 * which is why it needs the canvas size. Which edges move and which stay anchored differs per
 * handle: dragging the west edge moves `x` *and* shrinks `width`, while dragging the east edge only
 * grows `width`. Getting that backwards makes a source jump across the screen as it is resized.
 *
 * Declared here rather than inside the composable so the arithmetic can be exercised directly —
 * driving it through the grips themselves would mean a test reproducing the handle-placement maths
 * to find out where each 8dp box landed, which asserts the test's own arithmetic rather than this.
 */
internal fun resizeHandles(canvasWidth: Float, canvasHeight: Float): List<ResizeHandleDef> = listOf(
    ResizeHandleDef(0f, 0f, Cursor.NW_RESIZE_CURSOR) { t, d ->
        val dx = d.x / canvasWidth; val dy = d.y / canvasHeight
        t.copy(x = t.x + dx, y = t.y + dy, width = t.width - dx, height = t.height - dy)
    },
    ResizeHandleDef(HANDLE_MIDPOINT, 0f, Cursor.N_RESIZE_CURSOR) { t, d ->
        val dy = d.y / canvasHeight
        t.copy(y = t.y + dy, height = t.height - dy)
    },
    ResizeHandleDef(1f, 0f, Cursor.NE_RESIZE_CURSOR) { t, d ->
        val dx = d.x / canvasWidth; val dy = d.y / canvasHeight
        t.copy(y = t.y + dy, width = t.width + dx, height = t.height - dy)
    },
    ResizeHandleDef(0f, HANDLE_MIDPOINT, Cursor.W_RESIZE_CURSOR) { t, d ->
        val dx = d.x / canvasWidth
        t.copy(x = t.x + dx, width = t.width - dx)
    },
    ResizeHandleDef(1f, HANDLE_MIDPOINT, Cursor.E_RESIZE_CURSOR) { t, d ->
        val dx = d.x / canvasWidth
        t.copy(width = t.width + dx)
    },
    ResizeHandleDef(0f, 1f, Cursor.SW_RESIZE_CURSOR) { t, d ->
        val dx = d.x / canvasWidth; val dy = d.y / canvasHeight
        t.copy(x = t.x + dx, width = t.width - dx, height = t.height + dy)
    },
    ResizeHandleDef(HANDLE_MIDPOINT, 1f, Cursor.S_RESIZE_CURSOR) { t, d ->
        val dy = d.y / canvasHeight
        t.copy(height = t.height + dy)
    },
    ResizeHandleDef(1f, 1f, Cursor.SE_RESIZE_CURSOR) { t, d ->
        val dx = d.x / canvasWidth; val dy = d.y / canvasHeight
        t.copy(width = t.width + dx, height = t.height + dy)
    }
)

@Composable
private fun ResizeHandles(
    transform: SourceTransform,
    canvasWidth: Float,
    canvasHeight: Float,
    onTransformChanged: (SourceTransform) -> Unit
) {
    val handleSize = 8.dp
    val density = LocalDensity.current
    val currentTransform by rememberUpdatedState(transform)

    val handles = resizeHandles(canvasWidth, canvasHeight)

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
                        val scaledDrag = Offset(dragAmount.x, dragAmount.y)
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
