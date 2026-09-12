package org.churchpresenter.lottiegen.band

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTwoColorGradientFill

/**
 * The band itself: one `Band` shape layer holding the fill and border, an optional `BandAccent`
 * layer for the style's stripe or the swipe bar, and — for the wipes — a `BandMatte` both of them
 * are cut by. Every layer is named with the `Band` prefix so a player that wants the text without
 * the backdrop can hide them as a set.
 */
internal fun LottieBuilder.addBandBackground(cfg: BibleLottieGenConfig, slots: BandSlots, timeline: BandTimeline) {
    val band = slots.band
    val motion = BandMotion(cfg, band, timeline)
    // First in the array is topmost: the swipe bar over everything, the accent over the fill, and
    // the matte written before the fill it cuts — the accent finds it by index (`tp`).
    if (cfg.entrance == BandEntrance.SWIPE) addSwipeBar(cfg, band, motion)
    val accent = accentShapes(cfg, band)
    val matteIndex = if (motion.usesWipe) addBandMatte(band, motion) else null
    if (accent != null) {
        addShapeLayer(
            BandLayerNames.BAND_ACCENT,
            accent,
            motion.transform(),
            tt = if (matteIndex != null) 1 else null,
            tp = matteIndex,
        )
    }
    addShapeLayer(
        BandLayerNames.BAND,
        bandShapes(cfg, band),
        motion.transform(),
        tt = if (matteIndex != null) 1 else null,
        tp = matteIndex,
    )
}

private fun bandShapes(cfg: BibleLottieGenConfig, band: SlotBox): JsonArray = buildJsonArray {
    val corner = cfg.cornerRadiusPx.toDouble()
    val centre = listOf(band.centerX, band.centerY)
    val fill = when (cfg.bandStyle) {
        BandStyle.GRADIENT_BAR -> makeTwoColorGradientFill(
            startColor = hexToLottie(cfg.bgColor),
            endColor = hexToLottie(cfg.gradientColor),
            opacity = cfg.bgAlpha.toDouble(),
            startPt = listOf(band.centerX, band.y),
            endPt = listOf(band.centerX, band.bottom),
        )
        else -> makeFill(hexToLottie(cfg.bgColor), cfg.bgAlpha.toDouble())
    }
    add(makeGroup(listOf(makeRect(band.w, band.h, corner, centre), fill)))

    val borderPx = when (cfg.bandStyle) {
        BandStyle.GLASS_PANEL -> cfg.borderThickness.toDouble().coerceAtLeast(GLASS_BORDER_PX)
        else -> cfg.borderThickness.toDouble()
    }
    makeStroke(hexToLottie(cfg.borderColor), borderPx, cfg.borderAlpha.toDouble())?.let { stroke ->
        add(makeGroup(listOf(makeRect(band.w - borderPx, band.h - borderPx, corner, centre), stroke)))
    }
}

/** The style's stripe, if it has one; the swipe bar is separate because it moves on its own. */
private fun accentShapes(cfg: BibleLottieGenConfig, band: SlotBox): JsonArray? {
    val accent = makeFill(hexToLottie(cfg.accentColor), cfg.accentAlpha.toDouble())
    val rect = when (cfg.bandStyle) {
        BandStyle.ACCENT_EDGE_BAR ->
            makeRect(ACCENT_EDGE_PX, band.h, 0.0, listOf(band.x + ACCENT_EDGE_PX / 2, band.centerY))
        BandStyle.GLASS_PANEL ->
            makeRect(band.w, GLASS_UNDERLINE_PX, 0.0, listOf(band.centerX, band.bottom - GLASS_UNDERLINE_PX / 2))
        BandStyle.RIBBON ->
            makeRect(band.w, RIBBON_STRIPE_PX, 0.0, listOf(band.centerX, band.y + RIBBON_STRIPE_PX / 2))
        BandStyle.SOLID_BAR, BandStyle.GRADIENT_BAR -> return null
    }
    return buildJsonArray { add(makeGroup(listOf(rect, accent))) }
}

private fun LottieBuilder.addBandMatte(band: SlotBox, motion: BandMotion): Int {
    val shapes = buildJsonArray {
        add(
            makeGroup(
                listOf(
                    makeRect(band.w + MATTE_PAD_PX, band.h + MATTE_PAD_PX, 0.0, listOf(band.centerX, band.centerY)),
                    makeFill(WHITE),
                ),
            ),
        )
    }
    return addShapeLayer(BandLayerNames.BAND_MATTE, shapes, motion.matteTransform(), td = 1)
}

/** The bar that leads a swipe: crosses the band ahead of the fill and rests on its far edge. */
private fun LottieBuilder.addSwipeBar(cfg: BibleLottieGenConfig, band: SlotBox, motion: BandMotion) {
    val half = SWIPE_BAR_PX / 2
    val shapes = buildJsonArray {
        add(
            makeGroup(
                listOf(
                    makeRect(SWIPE_BAR_PX, band.h, 0.0, listOf(0.0, 0.0)),
                    makeFill(hexToLottie(cfg.accentColor), cfg.accentAlpha.toDouble()),
                ),
            ),
        )
    }
    val start = jsonArrayOf(band.x + half, band.centerY, 0.0)
    val end = jsonArrayOf(band.right - half, band.centerY, 0.0)
    val position = motion.keyframes(
        KeyframeInput(0.0, start),
        KeyframeInput(SWIPE_LEAD_PCT, end),
        KeyframeInput(END_PCT, end),
    )
    addShapeLayer(
        "${BandLayerNames.BAND_PREFIX}SwipeBar",
        shapes,
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(position)),
    )
}

/**
 * The entrance as layer-transform keyframes. Anchor and position are the same canvas point, so
 * an unanimated transform is the identity and a scale grows about that point — the bottom edge
 * for the unroll, the centre line for the scroll, the leading edge for a wipe.
 */
private class BandMotion(cfg: BibleLottieGenConfig, private val band: SlotBox, private val timeline: BandTimeline) {
    private val entrance = cfg.entrance
    private val canvasW = cfg.canvasW.toDouble()
    private val canvasH = cfg.canvasH.toDouble()

    val usesWipe: Boolean get() = entrance == BandEntrance.WIPE_LEFT || entrance == BandEntrance.WIPE_RIGHT ||
        entrance == BandEntrance.SWIPE

    fun keyframes(vararg points: KeyframeInput): JsonArray = buildKeyframes(
        points.toList(),
        inFrames = timeline.bgInFrames,
        holdFrames = timeline.bandHoldFrames,
        outFrames = timeline.bgOutFrames,
        easing = Easing.DEFAULT,
        startFrame = 0,
    )

    private val anchor: List<Double> = when (entrance) {
        BandEntrance.UNROLL -> listOf(band.centerX, band.bottom)
        BandEntrance.WIPE_RIGHT, BandEntrance.SWIPE -> listOf(band.x, band.centerY)
        BandEntrance.WIPE_LEFT -> listOf(band.right, band.centerY)
        else -> listOf(band.centerX, band.centerY)
    }

    private fun at(x: Double, y: Double): JsonArray = jsonArrayOf(x, y, 0.0)
    private fun scale(x: Double, y: Double): JsonArray = jsonArrayOf(x, y, FULL)

    /** The band's own transform: everything except the wipes, which animate the matte instead. */
    fun transform(): JsonObject {
        val ax = anchor[0]
        val ay = anchor[1]
        val anchorProp = LottieBuilder.staticPropArray(ax, ay, 0.0)
        val rest = at(ax, ay)
        return when (entrance) {
            BandEntrance.FADE -> LottieBuilder.defaultTransform(
                opacity = LottieBuilder.animatedProp(
                    keyframes(KeyframeInput(0.0, jsonArrayOf(0.0)), KeyframeInput(END_PCT, jsonArrayOf(FULL))),
                ),
                anchor = anchorProp,
                position = LottieBuilder.staticPropArray(ax, ay, 0.0),
            )
            BandEntrance.SLIDE_UP -> slide(anchorProp, at(ax, ay + canvasH), rest)
            BandEntrance.SLIDE_DOWN -> slide(anchorProp, at(ax, ay - canvasH), rest)
            BandEntrance.SLIDE_LEFT -> slide(anchorProp, at(ax + canvasW, ay), rest)
            BandEntrance.SLIDE_RIGHT -> slide(anchorProp, at(ax - canvasW, ay), rest)
            BandEntrance.UNROLL, BandEntrance.SCROLL_OPEN -> scaled(anchorProp, scale(FULL, 0.0))
            BandEntrance.GROW -> scaled(anchorProp, scale(0.0, 0.0))
            BandEntrance.WIPE_LEFT, BandEntrance.WIPE_RIGHT, BandEntrance.SWIPE -> LottieBuilder.defaultTransform(
                anchor = anchorProp,
                position = LottieBuilder.staticPropArray(ax, ay, 0.0),
            )
        }
    }

    /** The wipe matte's transform: a horizontal scale from the leading edge, delayed for a swipe. */
    fun matteTransform(): JsonObject {
        val ax = anchor[0]
        val ay = anchor[1]
        val startPct = if (entrance == BandEntrance.SWIPE) SWIPE_FILL_START_PCT else 0.0
        val points = buildList {
            add(KeyframeInput(0.0, scale(0.0, FULL)))
            if (startPct > 0.0) add(KeyframeInput(startPct, scale(0.0, FULL)))
            add(KeyframeInput(END_PCT, scale(FULL, FULL)))
        }
        return LottieBuilder.defaultTransform(
            anchor = LottieBuilder.staticPropArray(ax, ay, 0.0),
            position = LottieBuilder.staticPropArray(ax, ay, 0.0),
            scale = LottieBuilder.animatedProp(keyframes(*points.toTypedArray())),
        )
    }

    private fun slide(anchorProp: JsonObject, from: JsonArray, to: JsonArray): JsonObject =
        LottieBuilder.defaultTransform(
            anchor = anchorProp,
            position = LottieBuilder.animatedProp(keyframes(KeyframeInput(0.0, from), KeyframeInput(END_PCT, to))),
        )

    private fun scaled(anchorProp: JsonObject, from: JsonArray): JsonObject {
        val ax = anchor[0]
        val ay = anchor[1]
        return LottieBuilder.defaultTransform(
            anchor = anchorProp,
            position = LottieBuilder.staticPropArray(ax, ay, 0.0),
            scale = LottieBuilder.animatedProp(
                keyframes(KeyframeInput(0.0, from), KeyframeInput(END_PCT, scale(FULL, FULL))),
            ),
        )
    }
}

private val WHITE = listOf(1.0, 1.0, 1.0)
private const val FULL = 100.0
private const val END_PCT = 100.0
private const val ACCENT_EDGE_PX = 16.0
private const val GLASS_BORDER_PX = 2.0
private const val GLASS_UNDERLINE_PX = 6.0
private const val RIBBON_STRIPE_PX = 10.0
private const val MATTE_PAD_PX = 4.0
private const val SWIPE_BAR_PX = 14.0
private const val SWIPE_LEAD_PCT = 70.0
private const val SWIPE_FILL_START_PCT = 30.0
