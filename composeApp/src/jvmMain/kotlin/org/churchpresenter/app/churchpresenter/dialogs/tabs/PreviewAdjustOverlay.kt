package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_adjust_band
import org.churchpresenter.strings.generated.resources.profile_adjust_size
import org.churchpresenter.strings.generated.resources.profile_adjust_width
import kotlin.math.roundToInt
import org.churchpresenter.app.churchpresenter.presenter.BoxMargins
import org.churchpresenter.app.churchpresenter.presenter.LocalPresentedBlocks
import org.churchpresenter.app.churchpresenter.presenter.PresentedBlock
import org.churchpresenter.app.churchpresenter.presenter.textBoxArea
import org.churchpresenter.sharedui.utils.OutputSize
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.semantic
import org.jetbrains.compose.resources.stringResource

private val MARGIN_BAR_LONG = 32.dp
private val MARGIN_BAR_SHORT = 10.dp
private val WIDTH_DOT = 12.dp
private val WIDTH_LABEL_ROOM = 52.dp
private val SIZE_CORNER = 12.dp
private val BAND_BAR = 4.dp
private const val WIDTH_DOT_HEIGHT = 0.72f
private const val SIZE_PER_OUTPUT_PX = 0.2f
private val TEXT_SIZE_RANGE = 8..200
internal const val FULL_PERCENT = 100f

/**
 * The Adjust handles, drawn over a preview [stageWidth] wide of an [output]-sized screen.
 *
 * Every drag is turned into output pixels at the preview's own scale -- a pixel of pointer is
 * `output width ÷ preview width` pixels of screen -- so the large preview, being wider, moves each
 * value in finer steps. A drag writes as it goes, from the value it started at plus how far it has
 * come, so the picture under it follows the pointer.
 */
@Composable
internal fun PreviewAdjustOverlay(model: AdjustModel, stageWidth: Dp, output: OutputSize) {
    val stageHeight = stageWidth / output.aspectRatio
    // Preview dp per output pixel.
    val scale = stageWidth.value / output.width
    val m = model.margins.value
    val bandTop = model.band?.let { stageHeight * (1 - it.value / FULL_PERCENT) } ?: 0.dp
    val frame = AdjustFrame(
        left = (m.left * scale).dp,
        top = bandTop + (m.top * scale).dp,
        right = stageWidth - (m.right * scale).dp,
        bottom = stageHeight - (m.bottom * scale).dp,
    )
    // Where the overlay sits in the window: the presenter reports its blocks in window pixels.
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current.density
    val reported = LocalPresentedBlocks.current.orEmpty()
    val targets = model.blocks
    val blockFrames = targets?.keys?.map { reported.frameOf(
        PresentedBlock(targets.kind, it),
        origin,
        density,
    ) }.orEmpty()
    val referenceKey = targets?.keys?.getOrNull(targets.selected ?: 0)
    val referenceFrame = referenceKey?.let { reported.frameOf(
        PresentedBlock(PresentedBlock.Kind.REFERENCE, it),
        origin,
        density,
    ) }
    // What a dragged reference must stay inside: its translation's cell, which clips anything moved
    // out of it -- or the stage, where the layout has no cells.
    val referenceBounds = referenceKey
        ?.let { reported.frameOf(PresentedBlock(PresentedBlock.Kind.CELL, it), origin, density) }
        ?: AdjustFrame(0.dp, 0.dp, stageWidth, stageHeight)
    Box(
        Modifier
            .size(stageWidth, stageHeight)
            .onGloballyPositioned { origin = it.boundsInWindow().topLeft }
            .testTag(ADJUST_OVERLAY_TAG),
    ) {
        if (model.boxesOnly) {
            model.boxes?.let { BoxHandles(it, Rect(0f, 0f, stageWidth.value, stageHeight.value), scale) }
            return@Box
        }
        Box(
            Modifier
                .offset(frame.left, frame.top)
                .size(frame.width, frame.height)
                .dashedBorder(MaterialTheme.semantic.adjustHandle, 2.dp),
        )
        // Under every handle: a block is large, and the bars at its edges must still be caught.
        if (targets != null) BlockOutlines(targets, blockFrames, scale)
        model.boxes?.let { boxes ->
            val bandRect = model.band?.let { Rect(0f, bandTop.value, stageWidth.value, stageHeight.value) }
            val area = textBoxArea(
                outputWidth = stageWidth.value,
                outputHeight = stageHeight.value,
                options = boxes.options,
                margins = BoxMargins(m.left * scale, m.top * scale, m.right * scale, m.bottom * scale),
                band = bandRect,
            )
            BoxHandles(boxes, area, scale)
        }
        MarginBars(model, frame, scale, MarginRoom.of(output.width, output.height, model.band?.value))
        model.band?.let { BandBar(it, bandTop, stageWidth, stageHeight) }
        val inner = innerBox(frame, model.region?.value)
        model.region?.let { WidthDots(it, frame, inner) }
        MoveHandle(model, frame, scale)
        if (targets != null) BlockGrips(targets, referenceFrame, referenceBounds, scale)
        // The size corner sits on what it sizes -- the reference, or the picked block -- drawn last,
        // since that corner is often where the reference sits too.
        val sized = if (targets?.reference?.picked == true) referenceFrame
            else targets?.selected?.let { blockFrames.getOrNull(it) }
        SizeCorner(model, sized ?: inner, scale)
    }
}

/**
 * A draggable handle: [onDrag] is handed how far it has come since the drag began, in output pixels,
 * and [onStart] runs first, for the caller to note what it started from.
 */
@Composable
internal fun Modifier.adjustDrag(
    scale: Float,
    onStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onEnd: (Offset) -> Unit = {},
): Modifier {
    val density = LocalDensity.current.density
    val start by rememberUpdatedState(onStart)
    val drag by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onEnd)
    return pointerInput(Unit) {
        var total = Offset.Zero
        detectDragGestures(
            onDragStart = {
                total = Offset.Zero
                start()
            },
            onDrag = { change, amount ->
                change.consume()
                total += amount
                drag(total / (density * scale))
            },
            onDragEnd = { end(total / (density * scale)) },
        )
    }
}

/** One margin, the way its bar moves it: which edge, and which way along the drag it grows. */
private enum class MarginEdge(val horizontal: Boolean, val sign: Int, val side: MarginSide) {
    TOP(true, 1, MarginSide.TOP),
    BOTTOM(true, -1, MarginSide.BOTTOM),
    LEFT(false, 1, MarginSide.LEFT),
    RIGHT(false, -1, MarginSide.RIGHT);

    fun of(m: Margins): Int = when (this) {
        TOP -> m.top
        BOTTOM -> m.bottom
        LEFT -> m.left
        RIGHT -> m.right
    }

    fun set(m: Margins, value: Int): Margins = when (this) {
        TOP -> m.copy(top = value)
        BOTTOM -> m.copy(bottom = value)
        LEFT -> m.copy(left = value)
        RIGHT -> m.copy(right = value)
    }
}

/** The four blue bars, one centred on each edge, with the margin each one sets. */
@Composable
private fun MarginBars(model: AdjustModel, frame: AdjustFrame, scale: Float, room: MarginRoom) {
    var from by remember { mutableStateOf(model.margins.value) }
    val midX = frame.left + frame.width / 2
    val midY = frame.top + frame.height / 2
    MarginEdge.entries.forEach { edge ->
        val x = when (edge) {
            MarginEdge.LEFT -> frame.left - MARGIN_BAR_SHORT / 2
            MarginEdge.RIGHT -> frame.right - MARGIN_BAR_SHORT / 2
            else -> midX - MARGIN_BAR_LONG / 2
        }
        val y = when (edge) {
            MarginEdge.TOP -> frame.top - MARGIN_BAR_SHORT / 2
            MarginEdge.BOTTOM -> frame.bottom - MARGIN_BAR_SHORT / 2
            else -> midY - MARGIN_BAR_LONG / 2
        }
        MarginBar(x, y, edge, edge.of(model.margins.value), scale, { from = model.margins.value }) { total ->
            val along = if (edge.horizontal) total.y else total.x
            val value = (edge.of(from) + along * edge.sign).roundToInt().coerceIn(0, room.maxFor(edge.side, from))
            model.margins.onChange(edge.set(from, value))
        }
    }
}

@Composable
private fun MarginBar(
    x: Dp,
    y: Dp,
    edge: MarginEdge,
    value: Int,
    scale: Float,
    onStart: () -> Unit,
    onDrag: (Offset) -> Unit,
) {
    val w = if (edge.horizontal) MARGIN_BAR_LONG else MARGIN_BAR_SHORT
    val h = if (edge.horizontal) MARGIN_BAR_SHORT else MARGIN_BAR_LONG
    Box(
        Modifier
            .offset(x, y)
            .size(w, h)
            .background(MaterialTheme.semantic.adjustHandle, AppShape(3.dp))
            .adjustDrag(scale, onStart, onDrag)
            .testTag(adjustMarginTag(edge.name.lowercase())),
    )
    ValueChip(
        text = value.toString(),
        x = if (edge.horizontal) x + w + 4.dp else x - 4.dp,
        y = if (edge.horizontal) y - 3.dp else y + h + 2.dp,
        accent = false,
    )
}

/** A small label beside a handle: blue for the blue handles, orange for the orange ones. */
@Composable
private fun ValueChip(text: String, x: Dp, y: Dp, accent: Boolean) {
    val semantic = MaterialTheme.semantic
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (accent) semantic.onAdjustAccent else semantic.onAdjustHandle,
        modifier = Modifier
            .offset(x, y)
            .background(if (accent) semantic.adjustAccent else semantic.adjustHandle, AppShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** The white dots on either side of the content box, which set how wide it may be. */
@Composable
private fun WidthDots(adjustable: Adjustable<ContentRegion>, frame: AdjustFrame, inner: AdjustFrame) {
    val region = adjustable.value
    var from by remember { mutableStateOf(region.widthPercent) }
    val y = frame.top + frame.height * WIDTH_DOT_HEIGHT - WIDTH_DOT / 2
    val regionWidth = frame.width.value
    listOf(false to inner.left, true to inner.right).forEach { (right, x) ->
        Box(
            Modifier
                .offset(x - WIDTH_DOT / 2, y)
                .size(WIDTH_DOT)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(2.dp, MaterialTheme.semantic.adjustHandle, CircleShape)
                // Measured in preview dp: the width is a share of the frame, not a length on screen.
                .adjustDrag(
                    scale = 1f,
                    onStart = { from = region.widthPercent },
                    onDrag = { total ->
                        val delta = total.x / regionWidth * FULL_PERCENT
                        adjustable.onChange(region.copy(widthPercent = draggedWidth(region, from, delta, right)))
                    },
                )
                .testTag(adjustWidthTag(right)),
        )
    }
    ValueChip(
        stringResource(Res.string.profile_adjust_width, region.widthPercent),
        // Inside the box, under its dot: the right edge of the frame is often the preview's own.
        inner.right - WIDTH_LABEL_ROOM,
        y + WIDTH_DOT + 2.dp,
        accent = false,
    )
}

/** The orange corner at the content box's bottom right, which sizes the text the rows are pointed at. */
@Composable
private fun SizeCorner(model: AdjustModel, inner: AdjustFrame, scale: Float) {
    var from by remember { mutableStateOf(model.textSize.value) }
    Box(
        Modifier
            .offset(inner.right - SIZE_CORNER, inner.bottom - SIZE_CORNER)
            .size(SIZE_CORNER)
            .background(MaterialTheme.semantic.adjustAccent)
            .adjustDrag(scale, onStart = { from = model.textSize.value }, onDrag = { total ->
                val change = (total.x + total.y) / 2 * SIZE_PER_OUTPUT_PX
                model.textSize.onChange((from + change).roundToInt().coerceIn(TEXT_SIZE_RANGE))
            })
            .testTag(ADJUST_SIZE_TAG),
    )
    ValueChip(
        stringResource(Res.string.profile_adjust_size, model.textSize.value),
        inner.right - 44.dp,
        inner.bottom - SIZE_CORNER - 16.dp,
        accent = true,
    )
}

/** The orange bar along the band's top edge, which sets its height. */
@Composable
private fun BandBar(band: Adjustable<Int>, top: Dp, width: Dp, height: Dp) {
    val percent = band.value
    var from by remember { mutableStateOf(percent) }
    // One output pixel per preview dp here: the height is a share of the screen, not a length.
    Box(
        Modifier
            .offset(0.dp, top - BAND_BAR / 2)
            .fillMaxWidth()
            .height(BAND_BAR)
            .background(MaterialTheme.semantic.adjustAccent)
            .adjustDrag(1f, onStart = { from = percent }, onDrag = { total ->
                val change = -total.y / height.value * FULL_PERCENT
                band.onChange((from + change).roundToInt().coerceIn(BAND_RANGE))
            })
            .testTag(ADJUST_BAND_TAG),
    )
    ValueChip(stringResource(Res.string.profile_adjust_band, percent), width - 64.dp, top - 18.dp, accent = true)
}

/** Test handles for the Adjust handles. */
internal const val ADJUST_OVERLAY_TAG = "profile_adjust_overlay"
internal const val ADJUST_MOVE_TAG = "profile_adjust_move"
internal const val ADJUST_SIZE_TAG = "profile_adjust_size"
internal const val ADJUST_BAND_TAG = "profile_adjust_band"
internal fun adjustMarginTag(edge: String): String = "profile_adjust_margin_$edge"
internal fun adjustWidthTag(right: Boolean): String = "profile_adjust_width_${if (right) "right" else "left"}"
