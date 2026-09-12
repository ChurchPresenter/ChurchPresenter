package org.churchpresenter.lottiegen.band

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.TextMeasurer
import org.churchpresenter.lottiegen.lottie.TextRun
import org.churchpresenter.lottiegen.lottie.TextWrapBox
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeTextData

/**
 * A text slot's layers: the text itself, a hidden shadow twin beneath it, and the matte each is
 * cut by when the animation calls for one. The twin ships hidden because whether there is a
 * shadow is a Bible setting, not a property of the template; the player un-hides it.
 *
 * Layer order is the file's paint order in reverse — first is topmost — and a matte must sit
 * directly above the layer it cuts, so each pair is written matte-then-layer, text before shadow,
 * and the whole slot before the band it is read against.
 */
internal fun LottieBuilder.addTextSlot(slot: TextSlot, cfg: BibleLottieGenConfig, timeline: BandTimeline) {
    val motion = TextMotion(cfg, slot.box, timeline)
    val weight = if (cfg.previewBold) BOLD_WEIGHT else REGULAR_WEIGHT
    addFont(cfg.previewFontFamily, weight)
    val fittedSize = previewFittedSize(slot, cfg, weight)
    val wrapBox = previewWrapBox(slot, cfg, weight, fittedSize)
    val align = if (slot.isReference) cfg.referenceAlign else cfg.textAlign
    val justify = when {
        cfg.textAnimation == TextAnimation.TICKER && !slot.isReference -> JUSTIFY_LEFT
        align == BandTextAlign.LEFT -> JUSTIFY_LEFT
        align == BandTextAlign.RIGHT -> JUSTIFY_RIGHT
        else -> JUSTIFY_CENTRE
    }

    fun run(color: String) = TextRun(
        text = slot.sampleText,
        fontFamily = cfg.previewFontFamily,
        fontSizePx = fittedSize,
        fontWeight = weight,
        color = hexToLottie(color),
        transform = "none",
        justify = justify,
    )

    // A ticker's box is the slot too: the player widens the wrap box to the line it scrolls and
    // the matte clips it, and the box in the file is what tells the player where the slot is.
    val textMatte = if (motion.usesMatte) addTextMatte(slot, motion) else null
    addTextLayer(
        slot.name,
        makeTextData(run(slot.color), wrapBox),
        motion.transform(shadow = false),
        tt = if (textMatte != null) 1 else null,
    )
    val shadowMatte = if (motion.usesMatte) addTextMatte(slot, motion, shadow = true) else null
    addTextLayer(
        slot.name + BandLayerNames.SHADOW_SUFFIX,
        makeTextData(run(SHADOW_COLOR), wrapBox),
        motion.transform(shadow = true),
        tt = if (shadowMatte != null) 1 else null,
        hidden = true,
    )
}

/**
 * Where the sample's paragraph starts so it sits centred in its box. This is preview geometry —
 * the player recomputes it for the live text — measured with the same greedy wrap Lottie players
 * use, one word at a time until the line is full.
 */
private fun previewWrapBox(slot: TextSlot, cfg: BibleLottieGenConfig, weight: Int, sizePx: Double): TextWrapBox {
    val lineHeight = sizePx * LINE_HEIGHT
    val lines = countWrappedLines(slot, cfg, weight, sizePx)
    val blockH = lines * lineHeight
    val top = slot.box.y + ((slot.box.h - blockH) / 2).coerceAtLeast(0.0)
    // Players put the first baseline at the box's y (the font's ascent is lost on their copy of
    // the file), so the box starts an ascent below the block's top to land the glyphs there.
    return TextWrapBox(slot.box.x, top + sizePx * GLYPH_ASCENT, slot.box.w, slot.box.h)
}

/** The synthetic ascent the font list declares, as a fraction of the em. */
private const val GLYPH_ASCENT = 0.726

/**
 * The sample's size, stepped down until its wrapped lines fit the slot — the same shrink-to-fit
 * the player does for the live verse, so the preview shows the slot the way it will be used.
 */
private fun previewFittedSize(slot: TextSlot, cfg: BibleLottieGenConfig, weight: Int): Double {
    var size = slot.fontSizePx
    while (size > MIN_PREVIEW_SIZE && countWrappedLines(slot, cfg, weight, size) * size * LINE_HEIGHT > slot.box.h) {
        size -= PREVIEW_FIT_STEP
    }
    return size.coerceAtLeast(MIN_PREVIEW_SIZE)
}

private fun countWrappedLines(slot: TextSlot, cfg: BibleLottieGenConfig, weight: Int, sizePx: Double): Int {
    fun width(s: String): Double =
        TextMeasurer.measure(s, cfg.previewFontFamily, sizePx.toFloat(), weight, "none").width.toDouble()
    val space = width("a a") - width("aa")
    var lines = 1
    var lineWidth = 0.0
    for (word in slot.sampleText.split(' ').filter { it.isNotEmpty() }) {
        val w = width(word)
        val needed = if (lineWidth == 0.0) w else lineWidth + space + w
        if (needed > slot.box.w && lineWidth > 0.0) {
            lines++
            lineWidth = w
        } else {
            lineWidth = needed
        }
    }
    return lines
}

private fun LottieBuilder.addTextMatte(slot: TextSlot, motion: TextMotion, shadow: Boolean = false): Int {
    val box = slot.box
    val shapes = buildJsonArray {
        add(
            makeGroup(
                listOf(
                    makeRect(box.w + MATTE_PAD_PX, box.h + MATTE_PAD_PX, 0.0, listOf(box.centerX, box.centerY)),
                    makeFill(WHITE),
                ),
            ),
        )
    }
    val name = slot.name + (if (shadow) BandLayerNames.SHADOW_SUFFIX else "") + BandLayerNames.MATTE_SUFFIX
    return addShapeLayer(name, shapes, motion.matteTransform(), td = 1)
}

/** The text's own keyframes; a wipe animates the matte and leaves the text still. */
private class TextMotion(cfg: BibleLottieGenConfig, private val box: SlotBox, private val timeline: BandTimeline) {
    private val anim = cfg.textAnimation

    val usesMatte: Boolean get() = anim == TextAnimation.WIPE || anim == TextAnimation.TICKER

    private fun keyframes(vararg points: KeyframeInput): JsonArray = buildKeyframes(
        points.toList(),
        inFrames = timeline.textInFrames,
        holdFrames = timeline.holdFrames,
        outFrames = timeline.textOutFrames,
        easing = Easing.DEFAULT,
        startFrame = timeline.textStart,
    )

    private fun at(x: Double, y: Double): JsonArray = jsonArrayOf(x, y, 0.0)

    fun transform(shadow: Boolean): JsonObject {
        val dx = if (shadow) SHADOW_OFFSET_PX else 0.0
        val dy = if (shadow) SHADOW_OFFSET_PX else 0.0
        val rest = at(dx, dy)
        val still = LottieBuilder.staticPropArray(dx, dy, 0.0)
        return when (anim) {
            TextAnimation.FADE -> LottieBuilder.defaultTransform(opacity = fade(0.0), position = still)
            // A slide fades as it moves: half a box of travel alone leaves the text half in view.
            TextAnimation.SLIDE_UP -> slide(at(dx, dy + box.h * SLIDE_FRACTION), rest)
            TextAnimation.SLIDE_DOWN -> slide(at(dx, dy - box.h * SLIDE_FRACTION), rest)
            TextAnimation.SLIDE_LEFT -> slide(at(dx + box.w * SLIDE_FRACTION, dy), rest)
            TextAnimation.SLIDE_RIGHT -> slide(at(dx - box.w * SLIDE_FRACTION, dy), rest)
            TextAnimation.WIPE -> LottieBuilder.defaultTransform(position = still)
            // The player reveals or scrolls these itself; the file still fades them at the very
            // start and end of their segments so a plain player shows them arriving and leaving.
            TextAnimation.TYPEWRITER, TextAnimation.TYPEWRITER_WORDS, TextAnimation.TICKER ->
                LottieBuilder.defaultTransform(opacity = fade(QUICK_FADE_END_PCT), position = still)
        }
    }

    /** Opacity 0 → 100 over the in segment; [fullAtPct] > 0 finishes the ramp early, for a quick fade. */
    private fun fade(fullAtPct: Double): JsonObject = LottieBuilder.animatedProp(
        keyframes(
            KeyframeInput(0.0, jsonArrayOf(0.0)),
            KeyframeInput(if (fullAtPct > 0.0) fullAtPct else END_PCT, jsonArrayOf(FULL)),
            KeyframeInput(END_PCT, jsonArrayOf(FULL)),
        ),
    )

    fun matteTransform(): JsonObject {
        val anchor = LottieBuilder.staticPropArray(box.x, box.centerY, 0.0)
        val position = LottieBuilder.staticPropArray(box.x, box.centerY, 0.0)
        return if (anim == TextAnimation.WIPE) {
            LottieBuilder.defaultTransform(
                anchor = anchor,
                position = position,
                scale = LottieBuilder.animatedProp(
                    keyframes(
                        KeyframeInput(0.0, jsonArrayOf(0.0, FULL, FULL)),
                        KeyframeInput(END_PCT, jsonArrayOf(FULL, FULL, FULL)),
                    ),
                ),
            )
        } else {
            LottieBuilder.defaultTransform(anchor = anchor, position = position)
        }
    }

    private fun slide(from: JsonArray, to: JsonArray): JsonObject = LottieBuilder.defaultTransform(
        opacity = fade(0.0),
        position = LottieBuilder.animatedProp(keyframes(KeyframeInput(0.0, from), KeyframeInput(END_PCT, to))),
    )
}

private val WHITE = listOf(1.0, 1.0, 1.0)
private const val SHADOW_COLOR = "#000000"
private const val FULL = 100.0
private const val END_PCT = 100.0
private const val BOLD_WEIGHT = 700
private const val REGULAR_WEIGHT = 400
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1
private const val JUSTIFY_CENTRE = 2
private const val LINE_HEIGHT = 1.2
private const val SLIDE_FRACTION = 0.5
private const val QUICK_FADE_END_PCT = 20.0
private const val SHADOW_OFFSET_PX = 4.0
private const val MATTE_PAD_PX = 4.0
private const val MIN_PREVIEW_SIZE = 12.0
private const val PREVIEW_FIT_STEP = 2.0
