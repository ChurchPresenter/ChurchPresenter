package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.ContentScale
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
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.dynamic.LottieDynamicProperties
import io.github.alexzhirkevich.compottie.dynamic.rememberLottieDynamicProperties
import io.github.alexzhirkevich.compottie.internal.helpers.text.TextJustify
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import org.churchpresenter.app.churchpresenter.composables.keyColorFilter
import org.churchpresenter.app.churchpresenter.utils.LottieFonts
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.applyTextTransform
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.math.ceil

/** Lottie's own default line height, which the generator writes and the player assumes. */
private const val LINE_HEIGHT_FACTOR = 1.2f

/** The output height the Bible font sizes are specified against, as the classic band scales them. */
private const val REFERENCE_OUTPUT_HEIGHT = 1080f

/** Lottie tracking is in thousandths of an em ÷ 100; the player divides by ten and reads the rest as sp. */
private const val TRACKING_SCALE = 10f

/** The classic band's shadow offset, before the shadow-size percentage is applied. */
private const val SHADOW_OFFSET_PX = 6f

private const val NANOS_PER_SECOND = 1_000_000_000f

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

/** The Bible settings a slot draws with, picked from the translation's lower-third profile. */
private data class SlotStyle(
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

private fun BibleTranslationSettings.textSlotStyle(isKey: Boolean) = SlotStyle(
    font = BandFontKey(lowerThirdTextFontType, lowerThirdTextBold, lowerThirdTextItalic),
    fontSizePt = lowerThirdTextFontSize,
    color = if (isKey) Color.White else parseHexColor(lowerThirdTextColor),
    letterSpacingPt = lowerThirdTextLetterSpacing,
    transform = lowerThirdTextTransform,
    justify = justifyOf(lowerThirdTextHorizontalAlignment),
    shadow = lowerThirdTextShadow,
    shadowColor = parseHexColor(lowerThirdTextShadowColor),
    shadowSizePercent = lowerThirdTextShadowSize,
    shadowOpacityPercent = lowerThirdTextShadowOpacity,
)

private fun BibleTranslationSettings.referenceSlotStyle(isKey: Boolean) = SlotStyle(
    font = BandFontKey(lowerThirdReferenceFontType, lowerThirdReferenceBold, lowerThirdReferenceItalic),
    fontSizePt = lowerThirdReferenceFontSize,
    color = if (isKey) Color.White else parseHexColor(lowerThirdReferenceColor),
    letterSpacingPt = lowerThirdReferenceLetterSpacing,
    transform = lowerThirdReferenceTransform,
    justify = justifyOf(lowerThirdReferenceHorizontalAlignment),
    shadow = lowerThirdReferenceShadow,
    shadowColor = parseHexColor(lowerThirdReferenceShadowColor),
    shadowSizePercent = lowerThirdReferenceShadowSize,
    shadowOpacityPercent = lowerThirdReferenceShadowOpacity,
)

private fun justifyOf(alignment: String): TextJustify = when (alignment) {
    Constants.LEFT -> TextJustify.Left
    Constants.RIGHT -> TextJustify.Right
    else -> TextJustify.Center
}

/**
 * The Bible lower third as a Lottie template: the band and the text both come from the file, and
 * the file's text layers are told what to say and how to look from the verses and the Bible
 * settings. The face is written into the JSON (Lottie has no way to change it at run time); the
 * rest — string, size, colour, tracking, alignment, box, motion — is set per frame.
 *
 * [bandClock] is shared by every output; this composable only maps it onto its own template.
 */
@OptIn(ExperimentalCompottieApi::class)
@Composable
internal fun BoxScope.BibleLottieBand(
    template: BibleLottieTemplate,
    verses: List<SelectedVerse>,
    t0: BibleTranslationSettings,
    t1: BibleTranslationSettings,
    bandFraction: Float,
    bandClock: BibleBandClock,
    isKey: Boolean,
    showBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = verses.firstOrNull() ?: return
    val secondary = verses.getOrNull(1)?.takeIf { template.hasLayer(BibleLottieTemplate.LAYER_TEXT_2) }

    val styles = remember(t0, t1, isKey) {
        mapOf(
            BibleLottieTemplate.LAYER_TEXT_1 to t0.textSlotStyle(isKey),
            BibleLottieTemplate.LAYER_REFERENCE_1 to t0.referenceSlotStyle(isKey),
            BibleLottieTemplate.LAYER_TEXT_2 to t1.textSlotStyle(isKey),
            BibleLottieTemplate.LAYER_REFERENCE_2 to t1.referenceSlotStyle(isKey),
        )
    }
    val texts = remember(primary, secondary, t0, t1, styles) {
        buildMap {
            put(BibleLottieTemplate.LAYER_TEXT_1, applyTextTransform(primary.verseText, styles.getValue(BibleLottieTemplate.LAYER_TEXT_1).transform))
            put(BibleLottieTemplate.LAYER_REFERENCE_1, applyTextTransform(buildRefText(primary, t0), styles.getValue(BibleLottieTemplate.LAYER_REFERENCE_1).transform))
            if (secondary != null) {
                put(BibleLottieTemplate.LAYER_TEXT_2, applyTextTransform(secondary.verseText, styles.getValue(BibleLottieTemplate.LAYER_TEXT_2).transform))
                put(BibleLottieTemplate.LAYER_REFERENCE_2, applyTextTransform(buildRefText(secondary, t1), styles.getValue(BibleLottieTemplate.LAYER_REFERENCE_2).transform))
            }
        }
    }

    // The faces go into the file itself, so the composition is rebuilt only when a face changes.
    val fontsByLayer = remember(styles, template) {
        buildMap {
            BibleLottieTemplate.TEXT_LAYERS.forEach { name ->
                if (!template.hasLayer(name)) return@forEach
                val font = styles.getValue(name).font
                put(name, font)
                put(name + BibleLottieTemplate.SHADOW_SUFFIX, font)
            }
        }
    }
    val styledJson = remember(template, fontsByLayer) { rewriteTemplateFonts(template.json, fontsByLayer) }
    val composition by rememberLottieComposition(styledJson) { LottieCompositionSpec.JsonString(styledJson) }

    val pxPerPoint = template.height / (bandFraction * REFERENCE_OUTPUT_HEIGHT)
    val measurer = rememberBandTextMeasurer()
    val renders: Map<String, State<SlotRender>> = BibleLottieTemplate.TEXT_LAYERS
        .filter { template.hasLayer(it) }
        .associateWith { name ->
            val style = styles.getValue(name)
            val text = texts[name].orEmpty()
            val box = template.slots[name] ?: LottieSlotBox(0f, 0f, template.width, template.height)
            val singleLine = name.startsWith(BibleLottieTemplate.LAYER_REFERENCE_1.dropLast(1))
            val render = remember(style, text, box, pxPerPoint, template.textMotion, measurer) {
                layoutSlot(style, text, box, pxPerPoint, singleLine, template.textMotion, measurer)
            }
            rememberUpdatedState(render)
        }

    val clockState = rememberUpdatedState(bandClock)
    val showBackgroundState = rememberUpdatedState(showBackground)
    var tickerSeconds by remember { mutableStateOf(0f) }
    if (template.textMotion == BandTextMotion.TICKER) {
        LaunchedEffect(template) {
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
                textLayer(name + BibleLottieTemplate.SHADOW_SUFFIX) { bindSlot(template, state, tickerState, shadow = true) }
            }
        }
    }

    val painter = rememberLottiePainter(
        composition = composition,
        progress = { template.progressAt(clockState.value) },
        fontManager = LottieFonts,
        dynamicProperties = dynamic,
    )
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        colorFilter = if (isKey) keyColorFilter else null,
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(bandFraction)
            .align(Alignment.BottomCenter),
    )
}

/** A measurer at density 1, so a size in template pixels measures in template pixels. */
@Composable
private fun rememberBandTextMeasurer(): TextMeasurer {
    val resolver = LocalFontFamilyResolver.current
    return remember(resolver) { TextMeasurer(resolver, Density(1f), LayoutDirection.Ltr) }
}

/** The fitted, centred layout of one slot, from its text and style alone. */
private fun layoutSlot(
    style: SlotStyle,
    text: String,
    box: LottieSlotBox,
    pxPerPoint: Float,
    singleLine: Boolean,
    motion: BandTextMotion,
    measurer: TextMeasurer,
): SlotRender {
    val baseSize = style.fontSizePt * pxPerPoint
    val trackingPx = style.letterSpacingPt * pxPerPoint
    val fontFamily = LottieFonts.loadFont(style.font.family, style.font.bold, if (style.font.italic) FontStyle.Italic else FontStyle.Normal)
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
    val fitBox = if (isTicker) box.copy(w = Float.MAX_VALUE) else box
    val fitted = fitLottieSlot(text, fitBox, baseSize, trackingPx, LINE_HEIGHT_FACTOR, singleLine && !isTicker, charWidth)
    val lineHeight = fitted.fontSize * LINE_HEIGHT_FACTOR
    val blockHeight = fitted.lines.size * lineHeight
    // The player draws the first line's top at `ps.y - fontSize` when it resolves a real face.
    val top = box.y + ((box.h - blockHeight) / 2f).coerceAtLeast(0f)
    val shadowScale = style.shadowSizePercent / PERCENT
    val shadowPx = SHADOW_OFFSET_PX * pxPerPoint * shadowScale
    return SlotRender(
        text = text,
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
        !r.visible || (shadow && !r.shadow) || frame < textIn.startFrame || frame > textOut.endFrame
    }
}

/** A ticker runs in from the right edge and out at the left, then wraps; the matte in the file clips it. */
private fun tickerPosition(template: BibleLottieTemplate, r: SlotRender, seconds: Float): Offset {
    val slot = r.slot
    val travel = r.lineWidth + slot.w
    if (travel <= 0f) return r.position
    val offset = (seconds * template.tickerPxPerSecond) % travel
    return Offset(slot.x + slot.w - offset, r.position.y)
}

/**
 * The part of [text] a typewriter has typed by [frame]: everything during the hold, growing
 * through `text_in`, shrinking back through `text_out`. Keyframed animations show all of it and
 * let the file do the moving.
 */
private fun revealedText(template: BibleLottieTemplate, text: String, frame: Float): String {
    val motion = template.textMotion
    if (motion == BandTextMotion.NONE || motion == BandTextMotion.TICKER) return text
    val textIn = template.segment(BibleLottieTemplate.SEGMENT_TEXT_IN)
    val textOut = template.segment(BibleLottieTemplate.SEGMENT_TEXT_OUT)
    val fraction = when {
        frame < textIn.startFrame -> 0f
        frame <= textIn.endFrame -> template.progressWithin(BibleLottieTemplate.SEGMENT_TEXT_IN, frame)
        frame < textOut.startFrame -> 1f
        frame <= textOut.endFrame -> 1f - template.progressWithin(BibleLottieTemplate.SEGMENT_TEXT_OUT, frame)
        else -> 0f
    }
    return when (motion) {
        BandTextMotion.TYPEWRITER -> text.take(ceil(text.length * fraction).toInt())
        else -> {
            val words = text.split(' ')
            words.take(ceil(words.size * fraction).toInt()).joinToString(" ")
        }
    }
}

/**
 * A template at rest — its hold frame, with the sample text it was generated with — for the
 * Background tab's stage. A file that is missing or is not a template draws nothing.
 */
@Composable
internal fun BibleLottieStillFrame(path: String, modifier: Modifier = Modifier) {
    val template by rememberBibleLottieTemplate(path)
    val loaded = template ?: return
    val composition by rememberLottieComposition(loaded.json) { LottieCompositionSpec.JsonString(loaded.json) }
    val painter = rememberLottiePainter(
        composition = composition,
        progress = { loaded.progressAt(BibleBandClock()) },
        fontManager = LottieFonts,
    )
    Image(painter = painter, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = modifier)
}
