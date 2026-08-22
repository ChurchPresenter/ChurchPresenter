package org.churchpresenter.lottiegen.lottie.styles

import kotlinx.serialization.json.JsonArray
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
private const val SETTLE_PCT = 42
private const val BAND_START_PCT = 18.0
private const val TICKER_START_PCT = 26.0

/** The reveal masks sit just inside their bands so the wipe edge never shows. */
private const val BAND_MASK_FACTOR = 0.9
private const val TICKER_MASK_FACTOR = 0.88

/** The ticker's rule is drawn lighter than the configured border weight. */
private const val TICKER_BORDER_FACTOR = 0.7

/** The two bars and the gap between them, in em. */
private const val BAND_H_EM = 3.2
private const val TICKER_H_EM = 1.5
private const val BAND_GAP_EM = 0.12
private const val SLANT_W_EM = 2.4
private const val TEXT_PAD_EM = 1.2
private const val INFO_MASK_INSET_EM = 0.4

/** How much of the band's width the badge takes. Centre alignment has no badge. */
private const val BADGE_FRACTION = 0.28

/** Border thickness is configured 0..n and scaled onto the base size by this. */
private const val BORDER_THICKNESS_FACTOR = 0.1

/** The whole assembly starts this far below the canvas, plus a little clearance. */
private const val SLIDE_CLEARANCE_PX = 60

/** A baseline sits below its bar's centre; this fraction of the line size puts it right. */
private const val BASELINE_FACTOR = 0.35

/** Each line also slides sideways within its mask, by this fraction of the mask width. */
private const val NAME_SLIDE_FACTOR = 0.35
private const val INFO_SLIDE_FACTOR = 0.4
private const val NAME_MASK_INSET_FACTOR = 0.4

/** The badge's decorative stripes. */
private const val STRIPE_W_EM = 0.28
private const val STRIPE_SPACING_EM = 0.9
private const val STRIPE_H_FACTOR = 1.12
private const val STRIPE_LEAN_FACTOR = 0.3
private const val STRIPE_ALPHA_FACTOR = 0.38
private const val HALF_LEAN = 0.5

/** The ticker is a darker shade of the accent, never fully transparent. */
private const val TICKER_ALPHA_FACTOR = 0.72
private const val TICKER_MIN_ALPHA = 10.0

/** The two bars round their corners by different fractions of the configured radius. */
private const val BAND_CORNER_FACTOR = 0.3
private const val TICKER_CORNER_FACTOR = 0.2

/** Timing, as percentages of the animation. */
private const val NAME_SLIDE_START_PCT = 42.0
private const val NAME_SLIDE_END_PCT = 78.0
private const val INFO_SLIDE_START_PCT = 50.0
private const val INFO_SLIDE_END_PCT = 86.0
private const val END_PCT = 100.0

/** Justify codes Lottie writes for left, right and centred text. */
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1
private const val JUSTIFY_CENTRE = 2

/**
 * Everything Style11NewsTicker's layers are positioned from, computed once.
 *
 * Members are properties rather than constructor parameters on purpose: a constructor taking this
 * many would only trade one detekt finding for another.
 */
private class TickerGeometry(builder: LottieBuilder, val cfg: LottieGenConfig) {
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
    val slantW = emToPx(SLANT_W_EM, baseSize)
    val badgeFrac = if (isCenter) 0.0 else BADGE_FRACTION
    val halfBH = bandH / 2

    val bandW = canvasW - marginHPx * 2
    val halfBW = bandW / 2
    val bandCX = marginHPx + halfBW

    val tickerCY = canvasH - marginVPx - tickerH / 2
    val bandCY = tickerCY - tickerH / 2 - bandGap - halfBH
    private val slideFromY = canvasH + bandH + tickerH + SLIDE_CLEARANCE_PX

    /** The badge is a parallelogram at the band's outer end; centre alignment has none. */
    val badgeVerts: List<List<Double>>? = when {
        isCenter -> null
        isRight -> {
            val bx = -halfBW + bandW * badgeFrac
            listOf(
                listOf(-halfBW, -halfBH), listOf(bx, -halfBH),
                listOf(bx - slantW, halfBH), listOf(-halfBW, halfBH),
            )
        }
        else -> {
            val bx = halfBW - bandW * badgeFrac
            listOf(
                listOf(bx + slantW, -halfBH), listOf(halfBW, -halfBH),
                listOf(halfBW, halfBH), listOf(bx, halfBH),
            )
        }
    }

    private val namePadX = emToPx(TEXT_PAD_EM, baseSize)
    val nameTextX = when {
        isCenter -> canvasW / 2
        isRight -> canvasW - marginHPx - bandW * badgeFrac - slantW - namePadX
        else -> marginHPx + namePadX
    }
    val justify = when {
        isCenter -> JUSTIFY_CENTRE
        isRight -> JUSTIFY_RIGHT
        else -> JUSTIFY_LEFT
    }
    private val nameAreaW = if (isCenter) bandW else bandW * (1 - badgeFrac) - slantW
    val nameMaskW = nameAreaW - namePadX * NAME_MASK_INSET_FACTOR
    val nameMaskCX = when {
        isCenter -> canvasW / 2
        isRight -> canvasW - marginHPx - bandW * badgeFrac - slantW - nameMaskW / 2
        else -> marginHPx + nameMaskW / 2
    }
    val nameTextY = bandCY + nameSizePx * BASELINE_FACTOR

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
}

class Style11NewsTicker : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val g = TickerGeometry(builder, cfg)
        // Added first renders on top.
        if (!cfg.hideName) builder.addHeadline(g)
        if (!cfg.hideInfo) builder.addTickerText(g)
        builder.addLogo(g)
        if (g.badgeVerts != null && cfg.bgEnabled) {
            builder.addStripes(g)
            builder.addBadge(g)
        }
        if (cfg.bgEnabled) {
            builder.addMainBand(g)
            builder.addTickerBar(g)
        }
    }
}

/** The headline, clipped to the band's text area and sliding in from the alignment edge. */
private fun LottieBuilder.addHeadline(g: TickerGeometry) {
    addShapeLayer(
        "Name Mask",
        buildJsonArray {
            add(makeGroup(listOf(makeRect(g.nameMaskW, g.bandH * BAND_MASK_FACTOR, 0.0), makeFill(WHITE))))
        },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.animatedProp(
                g.riseKeyframes(BAND_START_PCT, g.nameMaskCX, g.bandCY),
            ),
        ),
        td = 1,
    )

    addFont(g.cfg.fontFamily, g.cfg.nameWeight)
    val slide = if (g.isRight) g.nameMaskW * NAME_SLIDE_FACTOR else -g.nameMaskW * NAME_SLIDE_FACTOR
    val posKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(g.nameTextX + slide, g.nameTextY, 0.0)),
        KeyframeInput(NAME_SLIDE_START_PCT, jsonArrayOf(g.nameTextX + slide, g.nameTextY, 0.0)),
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
private fun LottieBuilder.addTickerText(g: TickerGeometry) {
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
private fun LottieBuilder.addLogo(g: TickerGeometry) {
    val cfg = g.cfg
    if (!g.hasLogo || g.isCenter || cfg.logoData == null) return
    val scale = (g.logoSizePx / max(cfg.logoW, cfg.logoH).toDouble()) * PERCENT_SCALE
    val cx =
        if (g.isRight) g.marginHPx + g.bandW * g.badgeFrac / 2
        else g.canvasW - g.marginHPx - g.bandW * g.badgeFrac / 2
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

/** Three leaning stripes across the badge. */
private fun LottieBuilder.addStripes(g: TickerGeometry) {
    val w = emToPx(STRIPE_W_EM, g.baseSize)
    val h = g.bandH * STRIPE_H_FACTOR
    val lean = (if (g.isRight) -1.0 else 1.0) * h * STRIPE_LEAN_FACTOR
    val alpha = (g.cfg.accentColorAlpha * STRIPE_ALPHA_FACTOR).roundToInt().toDouble()
    val centreX =
        if (g.isRight) -g.halfBW + g.bandW * g.badgeFrac * HALF_LEAN
        else g.halfBW - g.bandW * g.badgeFrac * HALF_LEAN
    val spacing = emToPx(STRIPE_SPACING_EM, g.baseSize)

    val shapes = buildJsonArray {
        for (i in listOf(-1, 0, 1)) {
            val sx = centreX + i * spacing
            add(
                makeGroup(
                    listOf(
                        makePath(
                            listOf(
                                listOf(sx - w / 2 + lean * HALF_LEAN, -h / 2),
                                listOf(sx + w / 2 + lean * HALF_LEAN, -h / 2),
                                listOf(sx + w / 2 - lean * HALF_LEAN, h / 2),
                                listOf(sx - w / 2 - lean * HALF_LEAN, h / 2),
                            ),
                        ),
                        makeFill(g.accentLottie, alpha),
                    ),
                ),
            )
        }
    }
    addShapeLayer(
        "Stripes", shapes,
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(g.bandRise())),
    )
}

/** The badge itself, behind the stripes. */
private fun LottieBuilder.addBadge(g: TickerGeometry) {
    val verts = g.badgeVerts ?: return
    addShapeLayer(
        "Badge BG",
        buildJsonArray {
            add(makeGroup(listOf(makePath(verts), makeFill(g.bgLottie, g.cfg.bgColorAlpha.toDouble()))))
        },
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(g.bandRise())),
    )
}

/** The full-width accent band the headline sits on. */
private fun LottieBuilder.addMainBand(g: TickerGeometry) {
    val items = mutableListOf(
        makeRect(g.bandW, g.bandH, g.cornerPx * BAND_CORNER_FACTOR),
        makeFill(g.accentLottie, g.cfg.accentColorAlpha.toDouble()),
    )
    makeStroke(g.borderLottie, g.borderPx, g.cfg.borderColorAlpha.toDouble())?.let { items.add(it) }
    addShapeLayer(
        "Main Band",
        buildJsonArray { add(makeGroup(items)) },
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(g.bandRise())),
    )
}

/** The darker bar beneath it. */
private fun LottieBuilder.addTickerBar(g: TickerGeometry) {
    val alpha = max(
        TICKER_MIN_ALPHA,
        (g.cfg.accentColorAlpha * TICKER_ALPHA_FACTOR).roundToInt().toDouble(),
    )
    val items = mutableListOf(
        makeRect(g.bandW, g.tickerH, g.cornerPx * TICKER_CORNER_FACTOR),
        makeFill(g.accentLottie, alpha),
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
