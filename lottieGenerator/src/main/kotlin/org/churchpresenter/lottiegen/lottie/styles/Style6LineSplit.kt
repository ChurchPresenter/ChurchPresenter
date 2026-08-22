package org.churchpresenter.lottiegen.lottie.styles

import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.FULL_PERCENT_D
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.PERCENT_SCALE
import org.churchpresenter.lottiegen.lottie.TextMeasurer
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.emToPx
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeAnimatedRect
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makeTextDataWithAnimators
import org.churchpresenter.lottiegen.lottie.makeTextRevealAnimator
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max

/** Padding either side of the text, and the gap between a line and the rule, in em. */
private const val PAD_X_EM = 0.6
private const val LINE_GAP_EM = 0.6

/** The rule is never shorter than this, in em, however short the text is. */
private const val MIN_RULE_EM = 4.0

/** The gap between the logo and the block, in em. */
private const val LOGO_MARGIN_EM = 1.0

/** The rule is at least two pixels however thin the border is configured. */
private const val MIN_RULE_PX = 2.0
private const val BORDER_THICKNESS_FACTOR = 0.1

/** The info line's baseline sits this fraction of its size below the rule's gap. */
private const val INFO_BASELINE_FACTOR = 0.425

/** Letters pop away from the rule by this fraction of their size -- up above it, down below. */
private const val POP_FACTOR = 0.8

/** Timing, as percentages of the animation. */
private const val NAME_REVEAL_FROM_PCT = 20.0
private const val NAME_REVEAL_TO_PCT = 60.0
private const val INFO_REVEAL_FROM_PCT = 35.0
private const val INFO_REVEAL_TO_PCT = 75.0
private const val LOGO_GROWN_PCT = 25.0
private const val RULE_DRAWN_PCT = 40.0
private const val END_PCT = 100.0

/** Justify codes Lottie writes for left, right and centred text. */
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1
private const val JUSTIFY_CENTRE = 2

/**
 * Everything Style6LineSplit's layers are positioned from, computed once.
 *
 * Members are properties rather than constructor parameters on purpose: a constructor taking this
 * many would only trade one detekt finding for another.
 */
private class SplitGeometry(builder: LottieBuilder, val cfg: LottieGenConfig) {
    val inF = builder.inFrames
    val holdF = builder.holdFrames
    val outF = builder.outFrames

    val baseSize = cfg.baseSize.toDouble()
    val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
    val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
    private val nameM = TextMeasurer.measure(
        cfg.nameText, cfg.fontFamily, nameSizePx.toFloat(), cfg.nameWeight, cfg.nameTransform,
    )
    private val infoM = TextMeasurer.measure(
        cfg.infoText, cfg.fontFamily, infoSizePx.toFloat(), cfg.infoWeight, cfg.infoTransform,
    )

    private val paddingX = emToPx(PAD_X_EM, baseSize)
    private val lineGap = emToPx(LINE_GAP_EM, baseSize)
    val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
    private val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
    val linePx = max(MIN_RULE_PX, cfg.borderThickness * baseSize * BORDER_THICKNESS_FACTOR)

    val borderLottie = hexToLottie(cfg.borderColor)
    val nameCLottie = hexToLottie(cfg.nameColor)
    val infoCLottie = hexToLottie(cfg.infoColor)

    val canvasW = cfg.canvasW.toDouble()
    private val canvasH = cfg.canvasH.toDouble()
    val isRight = cfg.align == "right"
    val isCenter = cfg.align == "center"

    val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
    val logoMargin = emToPx(LOGO_MARGIN_EM, baseSize)
    val hasLogo = cfg.logoEnabled && cfg.logoData != null
    private val logoSpace = if (hasLogo) logoSizePx + logoMargin else 0.0

    private val nameContentW = if (cfg.hideName) 0.0 else nameM.width + paddingX * 2
    private val infoContentW = if (cfg.hideInfo) 0.0 else infoM.width + paddingX * 2
    val lineW = max(max(nameContentW, infoContentW), emToPx(MIN_RULE_EM, baseSize))

    private val nameBlockH = if (cfg.hideName) 0.0 else nameSizePx
    private val infoBlockH = if (cfg.hideInfo) 0.0 else infoSizePx
    private val gapAbove = if (cfg.hideName) 0.0 else lineGap
    private val gapBelow = if (cfg.hideInfo) 0.0 else lineGap
    private val totalH = nameBlockH + gapAbove + linePx + gapBelow + infoBlockH
    private val blockTopY = canvasH - marginVPx - totalH
    val lineCY = blockTopY + nameBlockH + gapAbove + linePx / 2

    /** The rule and both lines share one x: the block's edge, or the canvas centre. */
    val blockX = when {
        isCenter -> canvasW / 2
        isRight -> canvasW - marginHPx - logoSpace
        else -> marginHPx + logoSpace
    }
    val justify = when {
        isCenter -> JUSTIFY_CENTRE
        isRight -> JUSTIFY_RIGHT
        else -> JUSTIFY_LEFT
    }

    val nameTextY = lineCY - lineGap - linePx / 2
    val infoTextY = lineCY + lineGap + linePx / 2 + infoSizePx * INFO_BASELINE_FACTOR

    fun keyframes(vararg points: KeyframeInput) =
        buildKeyframes(points.toList(), inF, holdF, outF, Easing.DEFAULT)
}

/** One of the two lines: the name pops up out of the rule, the info line pops down out of it. */
private class SplitLine(g: SplitGeometry, isName: Boolean) {
    val layerName = if (isName) "Name" else "Info"
    val sizePx = if (isName) g.nameSizePx else g.infoSizePx
    val y = if (isName) g.nameTextY else g.infoTextY
    val popFrom = if (isName) sizePx * POP_FACTOR else -sizePx * POP_FACTOR
    val revealFrom = if (isName) NAME_REVEAL_FROM_PCT else INFO_REVEAL_FROM_PCT
    val revealTo = if (isName) NAME_REVEAL_TO_PCT else INFO_REVEAL_TO_PCT
    val text = if (isName) g.cfg.nameText else g.cfg.infoText
    val weight = if (isName) g.cfg.nameWeight else g.cfg.infoWeight
    val color = if (isName) g.nameCLottie else g.infoCLottie
    val transform = if (isName) g.cfg.nameTransform else g.cfg.infoTransform
    val alpha = if (isName) g.cfg.nameColorAlpha else g.cfg.infoColorAlpha
}

class Style6LineSplit : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val g = SplitGeometry(builder, cfg)
        if (!cfg.hideName) builder.addPoppingLine(g, SplitLine(g, isName = true))
        if (!cfg.hideInfo) builder.addPoppingLine(g, SplitLine(g, isName = false))
        builder.addLogo(g)
        builder.addRule(g)
    }
}

/** A line whose letters pop out of the rule, one at a time. */
private fun LottieBuilder.addPoppingLine(g: SplitGeometry, line: SplitLine) {
    val animator = makeTextRevealAnimator(
        line.revealFrom, line.revealTo, line.popFrom, g.inF, g.holdF, g.outF,
    )
    val data = makeTextDataWithAnimators(
        line.text, g.cfg.fontFamily, line.sizePx, line.weight,
        line.color, line.transform, g.justify, listOf(animator),
    )
    addFont(g.cfg.fontFamily, line.weight)
    addTextLayer(
        line.layerName, data,
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.staticProp(line.alpha),
            position = LottieBuilder.staticPropArray(g.blockX, line.y, 0.0),
        ),
    )
}

/** The logo scales up beside the block, centred on the rule. */
private fun LottieBuilder.addLogo(g: SplitGeometry) {
    val cfg = g.cfg
    if (!g.hasLogo || cfg.logoData == null) return
    val scale = (g.logoSizePx / cfg.logoH.toDouble()) * PERCENT_SCALE
    val cx = when {
        g.isCenter -> g.canvasW / 2 - g.lineW / 2 - g.logoMargin - g.logoSizePx / 2
        g.isRight -> g.canvasW - g.marginHPx - g.logoSizePx / 2
        else -> g.marginHPx + g.logoSizePx / 2
    }
    val scaleKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, FULL_PERCENT_D)),
        KeyframeInput(LOGO_GROWN_PCT, jsonArrayOf(scale, scale, FULL_PERCENT_D)),
        KeyframeInput(END_PCT, jsonArrayOf(scale, scale, FULL_PERCENT_D)),
    )
    addImageAsset("logo", cfg.logoData, cfg.logoW, cfg.logoH)
    addImageLayer(
        "Logo", "logo",
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(cx, g.lineCY, 0.0),
            anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
            scale = LottieBuilder.animatedProp(scaleKFs),
        ),
    )
}

/** The rule, drawn out from the block's edge (or from the centre, both ways). */
private fun LottieBuilder.addRule(g: SplitGeometry) {
    val anchorX = when {
        g.isCenter -> 0.0
        g.isRight -> g.lineW / 2
        else -> -g.lineW / 2
    }
    val sizeKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, g.linePx)),
        KeyframeInput(0.0, jsonArrayOf(0.0, g.linePx)),
        KeyframeInput(RULE_DRAWN_PCT, jsonArrayOf(g.lineW, g.linePx)),
    )
    addShapeLayer(
        "Line",
        buildJsonArray {
            add(
                makeGroup(
                    listOf(
                        makeAnimatedRect(sizeKFs, 0.0),
                        makeFill(g.borderLottie, g.cfg.borderColorAlpha.toDouble()),
                    ),
                ),
            )
        },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(g.blockX, g.lineCY, 0.0),
            anchor = LottieBuilder.staticPropArray(anchorX, 0.0, 0.0),
        ),
    )
}
