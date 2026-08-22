package org.churchpresenter.lottiegen.lottie.styles

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.FULL_PERCENT_D
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.PERCENT_SCALE
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.emToPx
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makePath
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max
import kotlin.math.roundToInt

/** A reveal mask is filled opaque white; only its shape matters. */
private val WHITE = listOf(1.0, 1.0, 1.0)

/** Where each element's slide-in has settled, as a percentage of the animation. */
private const val SETTLE_PCT = 40
private const val BAND_START_PCT = 18.0
private const val TICKER_START_PCT = 26.0

/** The badge's slash leans by this much either side of its centre line. */
private const val HALF_LEAN = 0.5

/** The slash is drawn under the name at just over half its opacity. */
private const val SLASH_ALPHA_FACTOR = 0.55

/** The reveal masks sit just inside their bands so the wipe edge never shows. */
private const val BAND_MASK_W_FACTOR = 0.96
private const val BAND_MASK_H_FACTOR = 0.9
private const val TICKER_MASK_FACTOR = 0.88

/** The ticker's rule is drawn lighter than the configured border weight. */
private const val TICKER_BORDER_FACTOR = 0.7

/** The two bars and the gap between them, in em. */
private const val BAND_H_EM = 3.2
private const val TICKER_H_EM = 1.5
private const val BAND_GAP_EM = 0.12
private const val SLASH_W_EM = 0.32
private const val SLASH_GAP_EM = 0.55
private const val TEXT_PAD_EM = 1.0
private const val INFO_MASK_INSET_EM = 0.4
private const val NAME_SLIDE_EM = 5.0

/** The slashes stand slightly proud of the band and lean by this fraction of their height. */
private const val SLASH_H_FACTOR = 1.06
private const val SLASH_LEAN_FACTOR = 0.28

/** How far the second slash sits beyond the first, in slash widths. */
private const val SLASH_2_OFFSET = 2.8

/** The main text area starts clear of the badge by four slash widths. */
private const val BADGE_CLEARANCE_SLASHES = 4

/** How much of the band's width the badge takes. Centre alignment has no badge. */
private const val BADGE_FRACTION = 0.22

/** Border thickness is configured 0..n and scaled onto the base size by this. */
private const val BORDER_THICKNESS_FACTOR = 0.1

/** The whole assembly starts this far below the canvas, plus a little clearance. */
private const val SLIDE_CLEARANCE_PX = 60

/** A baseline sits below its bar's centre; this fraction of the line size puts it right. */
private const val BASELINE_FACTOR = 0.35

/** The ticker line also slides sideways within its mask, by this fraction of the mask width. */
private const val INFO_SLIDE_FACTOR = 0.4

/** The two bars round their corners by different fractions of the configured radius. */
private const val BAND_CORNER_FACTOR = 0.3
private const val TICKER_CORNER_FACTOR = 0.2

/** Timing, as percentages of the animation. */
private const val NAME_SLIDE_START_PCT = 40.0
private const val NAME_SLIDE_END_PCT = 76.0
private const val INFO_SLIDE_START_PCT = 50.0
private const val INFO_SLIDE_END_PCT = 86.0
private const val END_PCT = 100.0

/** Justify codes Lottie writes for left, right and centred text. */
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1
private const val JUSTIFY_CENTRE = 2

/**
 * Everything Style12NewsBadge's layers are positioned from, computed once.
 *
 * Members are properties rather than constructor parameters on purpose: a constructor taking this
 * many would only trade one detekt finding for another.
 */
private class BadgeGeometry(builder: LottieBuilder, val cfg: LottieGenConfig) {
    val inF = builder.inFrames
    val holdF = builder.holdFrames
    val outF = builder.outFrames

    val baseSize = cfg.baseSize.toDouble()
    val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
    val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)

    val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
    private val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
    val cornerPx = emToPx(cfg.corners.toDouble(), baseSize)
    val borderPx = cfg.borderThickness * baseSize * BORDER_THICKNESS_FACTOR

    val accentLottie = hexToLottie(cfg.accentColor)
    val bgLottie = hexToLottie(cfg.bgColor)
    val borderLottie = hexToLottie(cfg.borderColor)
    val nameCLottie = hexToLottie(cfg.nameColor)
    val infoCLottie = hexToLottie(cfg.infoColor)

    val isRight = cfg.align == "right"
    val isCenter = cfg.align == "center"
    val canvasW = cfg.canvasW.toDouble()
    private val canvasH = cfg.canvasH.toDouble()

    val bandH = emToPx(BAND_H_EM, baseSize)
    val tickerH = emToPx(TICKER_H_EM, baseSize)
    private val bandGap = emToPx(BAND_GAP_EM, baseSize)
    val slashW = emToPx(SLASH_W_EM, baseSize)
    private val slashGap = emToPx(SLASH_GAP_EM, baseSize)
    private val halfBH = bandH / 2
    val slashH = bandH * SLASH_H_FACTOR
    val slashLean = slashH * SLASH_LEAN_FACTOR

    val badgeFrac = if (isCenter) 0.0 else BADGE_FRACTION

    val bandW = canvasW - marginHPx * 2
    private val halfBW = bandW / 2
    val bandCX = marginHPx + halfBW

    val tickerCY = canvasH - marginVPx - tickerH / 2
    val bandCY = tickerCY - tickerH / 2 - bandGap - halfBH
    private val slideFromY = canvasH + bandH + tickerH + SLIDE_CLEARANCE_PX

    /** Where the badge meets the main area, in band-local space. */
    private val badgeBoundaryX = when {
        isCenter -> 0.0
        isRight -> halfBW - bandW * badgeFrac
        else -> -halfBW + bandW * badgeFrac
    }
    val badgeVerts: List<List<Double>>? = when {
        isCenter -> null
        isRight -> listOf(
            listOf(badgeBoundaryX, -halfBH), listOf(halfBW, -halfBH),
            listOf(halfBW, halfBH), listOf(badgeBoundaryX - slashLean, halfBH),
        )
        else -> listOf(
            listOf(-halfBW, -halfBH), listOf(badgeBoundaryX + slashLean, -halfBH),
            listOf(badgeBoundaryX, halfBH), listOf(-halfBW, halfBH),
        )
    }

    /**
     * The two slashes flank the badge boundary, the second further out than the first.
     *
     * Written as two branches rather than one multiplied by a direction: the terms would
     * re-associate and the doubles would round differently, which shows up in the generated JSON.
     */
    val slash1X =
        if (isRight) badgeBoundaryX + slashGap * HALF_LEAN + slashW
        else badgeBoundaryX - slashGap * HALF_LEAN - slashW
    val slash2X =
        if (isRight) badgeBoundaryX + slashGap * HALF_LEAN + slashW * SLASH_2_OFFSET
        else badgeBoundaryX - slashGap * HALF_LEAN - slashW * SLASH_2_OFFSET

    private val namePadX = emToPx(TEXT_PAD_EM, baseSize)
    /** Left to right, term by term: factoring the badge clearance out re-associates the doubles. */
    private val mainLeft =
        if (isRight) marginHPx
        else marginHPx + bandW * badgeFrac + slashLean + slashW * BADGE_CLEARANCE_SLASHES
    private val mainRight =
        if (isRight) canvasW - marginHPx - bandW * badgeFrac - slashLean - slashW * BADGE_CLEARANCE_SLASHES
        else canvasW - marginHPx
    val mainW = mainRight - mainLeft
    val mainCX = (mainLeft + mainRight) / 2

    /** The headline is centred in the main area whichever way the badge points. */
    val nameTextX = if (isCenter) canvasW / 2 else mainCX
    val justify = JUSTIFY_CENTRE
    val nameTextY = bandCY + nameSizePx * BASELINE_FACTOR
    val nameSlide = if (isRight) emToPx(NAME_SLIDE_EM, baseSize) else -emToPx(NAME_SLIDE_EM, baseSize)

    val infoMaskW = bandW - emToPx(INFO_MASK_INSET_EM, baseSize)
    val infoTextX = if (isRight) canvasW - marginHPx - namePadX else marginHPx + namePadX
    val infoTextY = tickerCY + infoSizePx * BASELINE_FACTOR
    val infoJustify = when {
        isRight -> JUSTIFY_RIGHT
        isCenter -> JUSTIFY_CENTRE
        else -> JUSTIFY_LEFT
    }

    val hasLogo = cfg.logoEnabled && cfg.logoData != null
    val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)

    fun keyframes(vararg points: KeyframeInput) =
        buildKeyframes(points.toList(), inF, holdF, outF, Easing.DEFAULT)

    /** The rise every part of the assembly shares, delayed by where it sits in the stack. */
    fun riseKeyframes(delayPct: Double, cx: Double, finalY: Double): JsonArray = keyframes(
        KeyframeInput(0.0, jsonArrayOf(cx, slideFromY, 0.0)),
        KeyframeInput(delayPct, jsonArrayOf(cx, slideFromY, 0.0)),
        KeyframeInput(delayPct + SETTLE_PCT, jsonArrayOf(cx, finalY, 0.0)),
        KeyframeInput(FULL_PERCENT_D, jsonArrayOf(cx, finalY, 0.0)),
    )

    fun bandRise() = riseKeyframes(BAND_START_PCT, bandCX, bandCY)
    fun tickerRise() = riseKeyframes(TICKER_START_PCT, bandCX, tickerCY)

    /** One leaning stripe at [cx] in band-local space. */
    fun slash(cx: Double): JsonObject {
        val lean = if (isRight) -slashLean else slashLean
        return makeGroup(
            listOf(
                makePath(
                    listOf(
                        listOf(cx - slashW / 2 + lean * HALF_LEAN, -slashH / 2),
                        listOf(cx + slashW / 2 + lean * HALF_LEAN, -slashH / 2),
                        listOf(cx + slashW / 2 - lean * HALF_LEAN, slashH / 2),
                        listOf(cx - slashW / 2 - lean * HALF_LEAN, slashH / 2),
                    ),
                ),
                makeFill(nameCLottie, (cfg.nameColorAlpha * SLASH_ALPHA_FACTOR).roundToInt().toDouble()),
            ),
        )
    }
}

class Style12NewsBadge : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val g = BadgeGeometry(builder, cfg)
        // Added first renders on top.
        if (!cfg.hideName) builder.addHeadline(g)
        if (!cfg.hideInfo) builder.addTickerText(g)
        builder.addLogo(g)
        if (cfg.bgEnabled && g.badgeVerts != null) {
            builder.addSlashes(g)
            builder.addBadge(g)
        }
        if (cfg.bgEnabled) {
            builder.addMainBand(g)
            builder.addTickerBar(g)
        }
    }
}

/** The headline, clipped to the main area and sliding in past the badge. */
private fun LottieBuilder.addHeadline(g: BadgeGeometry) {
    addShapeLayer(
        "Name Mask",
        buildJsonArray {
            add(
                makeGroup(
                    listOf(
                        makeRect(g.mainW * BAND_MASK_W_FACTOR, g.bandH * BAND_MASK_H_FACTOR, 0.0),
                        makeFill(WHITE),
                    ),
                ),
            )
        },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.animatedProp(g.riseKeyframes(BAND_START_PCT, g.mainCX, g.bandCY)),
        ),
        td = 1,
    )

    addFont(g.cfg.fontFamily, g.cfg.nameWeight)
    val posKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(g.nameTextX + g.nameSlide, g.nameTextY, 0.0)),
        KeyframeInput(NAME_SLIDE_START_PCT, jsonArrayOf(g.nameTextX + g.nameSlide, g.nameTextY, 0.0)),
        KeyframeInput(NAME_SLIDE_END_PCT, jsonArrayOf(g.nameTextX, g.nameTextY, 0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(g.nameTextX, g.nameTextY, 0.0)),
    )
    addTextLayer(
        "Name",
        makeTextData(
            g.cfg.nameText, g.cfg.fontFamily, g.nameSizePx, g.cfg.nameWeight,
            g.nameCLottie, g.cfg.nameTransform, g.justify,
        ),
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.staticProp(g.cfg.nameColorAlpha),
            position = LottieBuilder.animatedProp(posKFs),
        ),
        tt = 1,
    )
}

/** The ticker line, clipped to the lower bar and sliding in from the opposite edge. */
private fun LottieBuilder.addTickerText(g: BadgeGeometry) {
    addShapeLayer(
        "Info Mask",
        buildJsonArray {
            add(makeGroup(listOf(makeRect(g.infoMaskW, g.tickerH * TICKER_MASK_FACTOR, 0.0), makeFill(WHITE))))
        },
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(g.tickerRise())),
        td = 1,
    )

    addFont(g.cfg.fontFamily, g.cfg.infoWeight)
    val slide = if (g.isRight) -g.infoMaskW * INFO_SLIDE_FACTOR else g.infoMaskW * INFO_SLIDE_FACTOR
    val posKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(g.infoTextX + slide, g.infoTextY, 0.0)),
        KeyframeInput(INFO_SLIDE_START_PCT, jsonArrayOf(g.infoTextX + slide, g.infoTextY, 0.0)),
        KeyframeInput(INFO_SLIDE_END_PCT, jsonArrayOf(g.infoTextX, g.infoTextY, 0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(g.infoTextX, g.infoTextY, 0.0)),
    )
    addTextLayer(
        "Info",
        makeTextData(
            g.cfg.infoText, g.cfg.fontFamily, g.infoSizePx, g.cfg.infoWeight,
            g.infoCLottie, g.cfg.infoTransform, g.infoJustify,
        ),
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.staticProp(g.cfg.infoColorAlpha),
            position = LottieBuilder.animatedProp(posKFs),
        ),
        tt = 1,
    )
}

/** The logo sits inside the badge, so there is nowhere for it when the badge is absent. */
private fun LottieBuilder.addLogo(g: BadgeGeometry) {
    val cfg = g.cfg
    if (!g.hasLogo || g.isCenter || cfg.logoData == null) return
    val scale = (g.logoSizePx / max(cfg.logoW, cfg.logoH).toDouble()) * PERCENT_SCALE
    val cx =
        if (g.isRight) g.canvasW - g.marginHPx - g.bandW * g.badgeFrac / 2
        else g.marginHPx + g.bandW * g.badgeFrac / 2
    addImageAsset("logo", cfg.logoData, cfg.logoW, cfg.logoH)
    addImageLayer(
        "Logo", "logo",
        LottieBuilder.defaultTransform(
            position = LottieBuilder.animatedProp(g.riseKeyframes(BAND_START_PCT, cx, g.bandCY)),
            anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
            scale = LottieBuilder.staticPropArray(scale, scale, FULL_PERCENT_D),
        ),
    )
}

/** The two leaning dividers between the badge and the headline. */
private fun LottieBuilder.addSlashes(g: BadgeGeometry) {
    addShapeLayer(
        "Slash Dividers",
        buildJsonArray {
            add(g.slash(g.slash1X))
            add(g.slash(g.slash2X))
        },
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(g.bandRise())),
    )
}

/** The badge itself. */
private fun LottieBuilder.addBadge(g: BadgeGeometry) {
    val verts = g.badgeVerts ?: return
    addShapeLayer(
        "Badge BG",
        buildJsonArray {
            add(
                makeGroup(
                    listOf(makePath(verts), makeFill(g.accentLottie, g.cfg.accentColorAlpha.toDouble())),
                ),
            )
        },
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(g.bandRise())),
    )
}

/** The full-width band the headline sits on. */
private fun LottieBuilder.addMainBand(g: BadgeGeometry) {
    val items = mutableListOf(
        makeRect(g.bandW, g.bandH, g.cornerPx * BAND_CORNER_FACTOR),
        makeFill(g.bgLottie, g.cfg.bgColorAlpha.toDouble()),
    )
    makeStroke(g.borderLottie, g.borderPx, g.cfg.borderColorAlpha.toDouble())?.let { items.add(it) }
    addShapeLayer(
        "Main Band",
        buildJsonArray { add(makeGroup(items)) },
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(g.bandRise())),
    )
}

/** The accent bar beneath it. */
private fun LottieBuilder.addTickerBar(g: BadgeGeometry) {
    val items = mutableListOf(
        makeRect(g.bandW, g.tickerH, g.cornerPx * TICKER_CORNER_FACTOR),
        makeFill(g.accentLottie, g.cfg.accentColorAlpha.toDouble()),
    )
    makeStroke(
        g.borderLottie, g.borderPx * TICKER_BORDER_FACTOR, g.cfg.borderColorAlpha.toDouble(),
    )?.let { items.add(it) }
    addShapeLayer(
        "Ticker Bar",
        buildJsonArray { add(makeGroup(items)) },
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(g.tickerRise())),
    )
}
