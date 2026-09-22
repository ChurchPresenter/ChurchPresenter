package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.compottie.ExperimentalCompottieApi
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.dynamic.LottieDynamicProperties
import io.github.alexzhirkevich.compottie.dynamic.rememberLottieDynamicProperties
import io.github.alexzhirkevich.compottie.internal.helpers.text.TextJustify
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.app.churchpresenter.composables.keyColorFilter
import org.churchpresenter.app.churchpresenter.utils.LottieFonts
import org.churchpresenter.settings.utils.Constants

/** The output height the Bible font sizes are specified against, as the classic band scales them. */
private const val REFERENCE_OUTPUT_HEIGHT = 1080f

/** Lottie tracking is in thousandths of an em ÷ 100; the player divides by ten and reads the rest as sp. */
private const val TRACKING_SCALE = 10f

/** The classic band's shadow offset, before the shadow-size percentage is applied. */
private const val SHADOW_OFFSET_PX = 6f

private const val NANOS_PER_SECOND = 1_000_000_000f

/** How much of the box the pre-wrapped lines may use, leaving the player no reason to wrap again. */
private const val WRAP_SAFETY = 0.97f

/** What one text layer is told to draw this frame. */
private data class SlotRender(
    val text: String,
    val fontSize: Float,
    val color: Color,
    val tracking: Float,
    val justify: TextJustify,
    /** The wrap box the player is given — for a ticker, wide enough for the whole line. */
    val box: LottieSlotBox,
    /** The slot as the template laid it out; a ticker scrolls across this. */
    val slot: LottieSlotBox,
    val position: Offset,
    val lineWidth: Float,
    val ticker: Boolean,
    val visible: Boolean,
    val shadow: Boolean,
    val shadowColor: Color,
    val shadowOffset: Offset,
)

/** What a slot draws with — one content type's lower-third typography, resolved for the key role. */
internal data class BandSlotStyle(
    val font: BandFontKey,
    val fontSizePt: Int,
    val color: Color,
    val letterSpacingPt: Int,
    val transform: String,
    val justify: TextJustify,
    val shadow: Boolean,
    val shadowColor: Color,
    val shadowSizePercent: Int,
    val shadowOpacityPercent: Int,
)

internal fun justifyOf(alignment: String): TextJustify = when (alignment) {
    Constants.LEFT -> TextJustify.Left
    Constants.RIGHT -> TextJustify.Right
    else -> TextJustify.Center
}

private fun BandTextAlign.toJustify(): TextJustify = when (this) {
    BandTextAlign.LEFT -> TextJustify.Left
    BandTextAlign.RIGHT -> TextJustify.Right
    BandTextAlign.CENTER -> TextJustify.Center
}

/** A slot's text and the typography it is set in. */
internal data class BandSlotText(val text: String, val style: BandSlotStyle)

/**
 * A lower third as a Lottie template: the band and the text both come from the file, and the
 * file's text layers are told what to say and how to look from [slots] — a verse and its
 * reference, a lyric and its title. The face is written into the JSON (Lottie has no way to
 * change it at run time); the rest — string, size, colour, tracking, alignment, box, motion — is
 * set per frame.
 *
 * A text swap is a crossfade: the words the band showed before the change are kept and drawn on
 * a second, background-less layer playing `text_out`, while the new words play `text_in` beneath.
 *
 * [bandClock] is shared by every output; this composable only maps it onto its own template.
 */
@OptIn(ExperimentalCompottieApi::class)
@Composable
internal fun BoxScope.LottieBand(
    template: BibleLottieTemplate,
    slots: Map<String, BandSlotText>,
    outgoingSlots: Map<String, BandSlotText>?,
    bandFraction: Float,
    bandClock: State<BibleBandClock>,
    isKey: Boolean,
    showBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    // The faces go into the file itself, so the composition is rebuilt only when a face changes.
    // Both layers of a swap share it: the outgoing words are one settings change at most behind.
    val fontsByLayer = remember(slots, template) {
        buildMap {
            BibleLottieTemplate.TEXT_LAYERS.forEach { name ->
                if (!template.hasLayer(name)) return@forEach
                val font = slots[name]?.style?.font ?: return@forEach
                put(name, font)
                put(name + BibleLottieTemplate.SHADOW_SUFFIX, font)
            }
        }
    }
    val styledJson = remember(template, fontsByLayer) { rewriteTemplateFonts(template.json, fontsByLayer) }
    val composition by rememberLottieComposition(styledJson) { LottieCompositionSpec.JsonString(styledJson) }

    // The words on screen before the last change, for the layer that plays them out. Handed down
    // from the driver rather than worked out from watching [slots] change, so an output composed
    // in the middle of a swap crossfades the same two texts as one that was there when it started.
    //
    // Both layers are composed at all times, even though only a swap has two texts to show. A layer
    // added at the moment it is first needed does not draw on its first frame, and the first frame
    // of a swap is exactly when the incoming text is still fully transparent — so the band went
    // blank for one frame on every single text change. Keeping it resident and hiding it costs a
    // draw; creating it on demand costs a black frame on every change.
    //
    // It is shown only when there is something to play out AND a swap is running. A swap with no
    // outgoing words — which is what a preview pinned mid-swap is — would otherwise set the same
    // text twice, once per layer.
    val outgoing = outgoingSlots ?: slots
    val hasOutgoing = rememberUpdatedState(outgoingSlots != null)

    val layerModifier = modifier
        .fillMaxWidth()
        .fillMaxHeight(bandFraction)
        .align(Alignment.BottomCenter)
    val clockState = bandClock
    val progress = { template.progressAt(clockState.value) }
    val keyFilter = if (isKey) keyColorFilter else null
    BandLayer(
        template = template,
        composition = composition,
        slots = slots,
        bandFraction = bandFraction,
        progress = progress,
        colorFilter = keyFilter,
        showBackground = showBackground,
        modifier = layerModifier,
    )
    BandLayer(
        template = template,
        composition = composition,
        slots = outgoing,
        bandFraction = bandFraction,
        progress = {
            val clock = clockState.value
            template.outgoingProgressAt(if (clock.phase == BibleBandPhase.TEXT_SWAP) clock else FADED_OUT)
        },
        colorFilter = keyFilter,
        showBackground = false,
        // Not drawn at all when it has nothing to play out, rather than drawn at zero alpha: an
        // invisible layer still rasterises a whole Lottie frame, which doubled the band's per-frame
        // cost and cost it frames. Skipping `drawContent` leaves the composable, its composition and
        // its painter alive, so the layer is still warm when a swap starts.
        modifier = layerModifier.drawWithContent {
            val swapping = clockState.value.phase == BibleBandPhase.TEXT_SWAP
            if (swapping && hasOutgoing.value) drawContent()
        },
    )
}

/** The end of `text_out` — where the layer that is not playing anything out parks, unseen. */
private val FADED_OUT = BibleBandClock(BibleBandPhase.TEXT_SWAP, 1f)

/**
 * One pass over the template: its text layers bound to [slots], drawn at [progress]. A pass shows
 * the band and the text, or the text alone — a swap's outgoing layer.
 */
@OptIn(ExperimentalCompottieApi::class)
@Composable
private fun BandLayer(
    template: BibleLottieTemplate,
    composition: LottieComposition?,
    slots: Map<String, BandSlotText>,
    bandFraction: Float,
    progress: () -> Float,
    colorFilter: ColorFilter?,
    showBackground: Boolean,
    modifier: Modifier,
) {
    val styles = slots.mapValues { it.value.style }
    // A ticker carries its reference at the head of the scrolling line; the reference slot itself
    // is left empty so nothing sits still beside the motion.
    val texts = remember(slots, template.meta.textMotion) {
        val plain = slots.mapValues { it.value.text }
        if (template.meta.textMotion != BandTextMotion.TICKER) plain else tickerTexts(plain)
    }

    val pxPerPoint = template.height / (bandFraction * REFERENCE_OUTPUT_HEIGHT)
    val measurer = rememberBandTextMeasurer()
    val renders: Map<String, State<SlotRender>> = BibleLottieTemplate.TEXT_LAYERS
        .filter { template.hasLayer(it) && styles.containsKey(it) }
        .associateWith { name ->
            val style = styles.getValue(name)
            val text = texts[name].orEmpty()
            val box = template.effectiveBox(name, texts)
            val singleLine = name.startsWith(BibleLottieTemplate.LAYER_REFERENCE_1.dropLast(1))
            // The template may pin a justification; otherwise the content's own settings apply.
            val pinned = if (singleLine) template.meta.referenceAlign else template.meta.textAlign
            val effectiveStyle = when (pinned) {
                null -> style
                else -> style.copy(justify = pinned.toJustify())
            }
            val geometry = SlotGeometry(box, pxPerPoint, singleLine, template.meta.textMotion)
            val render = remember(effectiveStyle, text, geometry, measurer) {
                layoutSlot(BandSlotText(text, effectiveStyle), geometry, measurer)
            }
            rememberUpdatedState(render)
        }

    val showBackgroundState = rememberUpdatedState(showBackground)
    var tickerSeconds by remember { mutableStateOf(0f) }
    if (template.meta.textMotion == BandTextMotion.TICKER) {
        // Restarted on every text change, so a new verse enters from the right edge rather than
        // replacing the old one part-way across.
        LaunchedEffect(template, texts) {
            tickerSeconds = 0f
            val start = withFrameNanos { it }
            while (true) withFrameNanos { tickerSeconds = (it - start) / NANOS_PER_SECOND }
        }
    }
    val tickerState = rememberUpdatedState(tickerSeconds)

    val dynamic: LottieDynamicProperties = rememberLottieDynamicProperties(template, renders.keys) {
        template.layerNames
            .filter { it.startsWith(BibleLottieTemplate.BAND_PREFIX) }
            .forEach { name -> layer(name) { hidden { !showBackgroundState.value } } }
        renders.forEach { (name, state) ->
            textLayer(name) { bindSlot(template, state, tickerState, shadow = false) }
            if (template.hasLayer(name + BibleLottieTemplate.SHADOW_SUFFIX)) {
                textLayer(name + BibleLottieTemplate.SHADOW_SUFFIX) {
                    bindSlot(template, state, tickerState, shadow = true)
                }
            }
        }
    }

    val painter = rememberLottiePainter(
        composition = composition,
        progress = progress,
        fontManager = LottieFonts,
        dynamicProperties = dynamic,
    )
    BoxWithConstraints(modifier) {
        val contentScale = bandContentScale(maxWidth / maxHeight, template.width / template.height)
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = contentScale,
            alignment = Alignment.BottomCenter,
            colorFilter = colorFilter,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** A measurer at density 1, so a size in template pixels measures in template pixels. */
@Composable
private fun rememberBandTextMeasurer(): TextMeasurer {
    val resolver = LocalFontFamilyResolver.current
    return remember(resolver) { TextMeasurer(resolver, Density(1f), LayoutDirection.Ltr) }
}

/** Where a slot sits and how it moves: everything about it that is not its text or its style. */
private data class SlotGeometry(
    val box: LottieSlotBox,
    val pxPerPoint: Float,
    val singleLine: Boolean,
    val motion: BandTextMotion,
)

/** The fitted, centred layout of one slot, from its text and style alone. */
private fun layoutSlot(slot: BandSlotText, geometry: SlotGeometry, measurer: TextMeasurer): SlotRender {
    val (text, style) = slot
    val box = geometry.box
    val pxPerPoint = geometry.pxPerPoint
    val singleLine = geometry.singleLine
    val motion = geometry.motion
    val baseSize = style.fontSizePt * pxPerPoint
    val trackingPx = style.letterSpacingPt * pxPerPoint
    val fontStyle = if (style.font.italic) FontStyle.Italic else FontStyle.Normal
    val fontFamily = LottieFonts.loadFont(style.font.family, style.font.bold, fontStyle)
        ?.let { FontFamily(it) } ?: FontFamily.Default
    val textStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = if (style.font.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (style.font.italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = baseSize.sp,
    )
    val widths = HashMap<Char, Float>()
    val charWidth: (Char) -> Float = { c ->
        widths.getOrPut(c) { measurer.measure(c.toString(), textStyle).size.width.toFloat() }
    }
    // Only the verse runs as a ticker; its reference stays put.
    val isTicker = motion == BandTextMotion.TICKER && !singleLine
    // Wrapped a hair narrower than the box, and handed to the player already broken into lines:
    // the player numbers a line it wraps itself the same as the next written line, so the two
    // draw over each other. Given lines that fit, it never wraps, and the margin keeps a
    // rounding difference between its measure and this one from making it.
    val fitBox = if (isTicker) box.copy(w = Float.MAX_VALUE) else box.copy(w = box.w * WRAP_SAFETY)
    val fitted = fitLottieSlot(SlotFitRequest(text, fitBox, baseSize, trackingPx, singleLine && !isTicker), charWidth)
    val lineHeight = fitted.fontSize * LINE_HEIGHT_FACTOR
    val blockHeight = fitted.lines.size * lineHeight
    // The player draws the first line's top at `ps.y - fontSize` when it resolves a real face.
    val top = box.y + ((box.h - blockHeight) / 2f).coerceAtLeast(0f)
    val shadowScale = style.shadowSizePercent / PERCENT
    val shadowPx = SHADOW_OFFSET_PX * pxPerPoint * shadowScale
    return SlotRender(
        text = if (isTicker) text else fitted.lines.joinToString("\n"),
        fontSize = fitted.fontSize,
        color = style.color,
        tracking = (fitted.fontSize / baseSize) * trackingPx * TRACKING_SCALE,
        justify = if (isTicker) TextJustify.Left else style.justify,
        box = if (isTicker) box.copy(w = fitted.lineWidthPx + box.w) else box,
        slot = box,
        position = Offset(box.x, top + fitted.fontSize),
        lineWidth = fitted.lineWidthPx,
        ticker = isTicker,
        visible = text.isNotEmpty(),
        shadow = style.shadow,
        shadowColor = style.shadowColor.copy(alpha = style.shadowOpacityPercent / PERCENT),
        shadowOffset = Offset(shadowPx, shadowPx),
    )
}

/** Binds one text layer (or its shadow twin) to a slot's render, evaluated per frame by the player. */
@OptIn(ExperimentalCompottieApi::class)
private fun io.github.alexzhirkevich.compottie.dynamic.DynamicTextLayer.bindSlot(
    template: BibleLottieTemplate,
    state: State<SlotRender>,
    ticker: State<Float>,
    shadow: Boolean,
) {
    val textIn = template.segment(BibleLottieTemplate.SEGMENT_TEXT_IN)
    val textOut = template.segment(BibleLottieTemplate.SEGMENT_TEXT_OUT)
    text { revealedText(template, state.value.text, frame) }
    fontSize { state.value.fontSize }
    lineHeight { state.value.fontSize * LINE_HEIGHT_FACTOR }
    fillColor { if (shadow) state.value.shadowColor else state.value.color }
    tracking { state.value.tracking }
    textJustify { state.value.justify }
    size { Size(state.value.box.w, state.value.box.h) }
    position {
        val r = state.value
        val base = if (r.ticker) tickerPosition(template, r, ticker.value) else r.position
        if (shadow) base + r.shadowOffset else base
    }
    hidden {
        val r = state.value
        !r.visible || (shadow && !r.shadow) ||
            frame < textIn.startFrame || frame > textOut.endFrame
    }
}

/** A ticker runs in from the right edge and out at the left, then wraps; the matte in the file clips it. */
private fun tickerPosition(template: BibleLottieTemplate, r: SlotRender, seconds: Float): Offset {
    val slot = r.slot
    val travel = r.lineWidth + slot.w
    if (travel <= 0f) return r.position
    val offset = (seconds * template.meta.tickerPxPerSecond) % travel
    return Offset(slot.x + slot.w - offset, r.position.y)
}

