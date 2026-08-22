package org.churchpresenter.lottiegen.lottie.styles

import kotlinx.serialization.json.JsonArray
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
import org.churchpresenter.lottiegen.lottie.kf
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makePath
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max
import kotlin.math.roundToInt

/** The wipe's mask crosses the text halfway through the in and out phases. */
private const val WIPE_MIDPOINT = 0.5

/**
 * Opacity turning points, as fractions of the in and out phases: the text is up almost at once,
 * holds until just before the end, and leaves the same way.
 */
private const val FADE_IN_END = 0.08
private const val FADE_OUT_START = 0.05
private const val HOLD_START = 0.95

/** The diagonal's lean, and the clearance around the text, in em. */
private const val SLANT_EM = 1.0
private const val PADDING_EM = 1.0
private const val LOGO_MARGIN_EM = 1.0

/** The diagonal rule is at least two pixels, and this fraction of the base size otherwise. */
private const val MIN_RULE_PX = 2.0
private const val RULE_H_FACTOR = 0.08

/** A baseline sits below its line's centre; this fraction of the line size puts it right. */
private const val BASELINE_FACTOR = 0.35

/** The logo has finished scaling up by here. */
private const val LOGO_GROWN_PCT = 25.0
private const val END_PCT = 100.0

/** Justify codes Lottie writes for left, right and centred text. */
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1
private const val JUSTIFY_CENTRE = 2

/**
 * Everything Style9DiagonalWipe's layers are positioned from, computed once.
 *
 * Members are properties rather than constructor parameters on purpose: a constructor taking this
 * many would only trade one detekt finding for another.
 */
private class WipeGeometry(builder: LottieBuilder, val cfg: LottieGenConfig) {
    val inF = builder.inFrames
    val holdF = builder.holdFrames
    val outF = builder.outFrames
    val totalOut = inF + holdF

    val baseSize = cfg.baseSize.toDouble()
    val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
    val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
    val nameM = TextMeasurer.measure(
        cfg.nameText, cfg.fontFamily, nameSizePx.toFloat(), cfg.nameWeight, cfg.nameTransform,
    )
    val infoM = TextMeasurer.measure(
        cfg.infoText, cfg.fontFamily, infoSizePx.toFloat(), cfg.infoWeight, cfg.infoTransform,
    )

    private val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
    val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
    private val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)

    val accentLottie = hexToLottie(cfg.accentColor)
    val nameCLottie = hexToLottie(cfg.nameColor)
    val infoCLottie = hexToLottie(cfg.infoColor)

    val isRight = cfg.align == "right"
    val isCenter = cfg.align == "center"
    val canvasW = cfg.canvasW.toDouble()
    private val canvasH = cfg.canvasH.toDouble()

    val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
    val logoMargin = emToPx(LOGO_MARGIN_EM, baseSize)
    val hasLogo = cfg.logoEnabled && cfg.logoData != null
    private val logoSpace = if (hasLogo) logoSizePx + logoMargin else 0.0

    private val nameBlockH = if (cfg.hideName) 0.0 else nameSizePx
    private val infoBlockH = if (cfg.hideInfo) 0.0 else infoSizePx
    private val gap = if (!cfg.hideName && !cfg.hideInfo) lineSpacingPx else 0.0
    private val totalH = nameBlockH + gap + infoBlockH
    private val blockTopY = canvasH - marginVPx - totalH
    val nameCY = blockTopY + nameBlockH / 2
    val infoCY = blockTopY + nameBlockH + gap + infoBlockH / 2

    /** Both lines share one x; only the justify differs. */
    val textX = when {
        isCenter -> canvasW / 2
        isRight -> canvasW - marginHPx - logoSpace
        else -> marginHPx + logoSpace
    }
    val justify = when {
        isCenter -> JUSTIFY_CENTRE
        isRight -> JUSTIFY_RIGHT
        else -> JUSTIFY_LEFT
    }
    val nameTextY = nameCY + nameSizePx * BASELINE_FACTOR
    val infoTextY = infoCY + infoSizePx * BASELINE_FACTOR

    private val slant = emToPx(SLANT_EM, baseSize)
    private val padding = emToPx(PADDING_EM, baseSize)
    private val maskH = totalH + padding * 2
    val lineThickness = max(MIN_RULE_PX, baseSize * RULE_H_FACTOR)

    private val maxTextW = max(
        if (cfg.hideName) 0.0 else nameM.width.toDouble(),
        if (cfg.hideInfo) 0.0 else infoM.width.toDouble(),
    )

    /**
     * The wipe travels across the text plus its clearance. Written per branch, in the original
     * term order: factoring these re-associates the doubles and moves the generated coordinates.
     */
    private val textLeftX = when {
        isCenter -> canvasW / 2 - maxTextW / 2 - logoSpace
        isRight -> canvasW - marginHPx - maxTextW - logoSpace
        else -> marginHPx
    }
    private val textRightX = when {
        isCenter -> canvasW / 2 + maxTextW / 2
        isRight -> canvasW - marginHPx
        else -> marginHPx + maxTextW + logoSpace
    }
    private val wipeLeft = textLeftX - padding - slant
    private val wipeRight = textRightX + padding + slant

    /** The mask is oversized so it fully covers the text at any wipe position. */
    private val maskW = (wipeRight - wipeLeft) + slant + padding * 2
    private val halfMW = maskW / 2
    private val halfMH = maskH / 2

    val maskVertices: List<List<Double>> = if (isRight) {
        listOf(
            listOf(-halfMW, -halfMH), listOf(halfMW, -halfMH),
            listOf(halfMW, halfMH), listOf(-halfMW + slant, halfMH),
        )
    } else {
        listOf(
            listOf(-halfMW, -halfMH), listOf(halfMW, -halfMH),
            listOf(halfMW - slant, halfMH), listOf(-halfMW, halfMH),
        )
    }
    val lineVertices: List<List<Double>> = if (isRight) {
        listOf(listOf(0.0, -halfMH), listOf(slant, halfMH))
    } else {
        listOf(listOf(0.0, -halfMH), listOf(-slant, halfMH))
    }

    private val maskCY = blockTopY + totalH / 2

    /** offStart hides the mask; offEnd has it fully covering the text. */
    private val offStart = if (isRight) wipeRight + halfMW + slant else wipeLeft - halfMW - slant
    private val offEnd = if (isRight) wipeLeft + halfMW - slant * 2 else wipeRight - halfMW + slant * 2
    private val lineEdgeOffset = if (isRight) -halfMW else halfMW

    private val e = Easing.DEFAULT
    private val l = Easing.LINEAR

    private fun at(x: Double) = jsonArrayOf(x, maskCY, 0.0)
    private fun inAt(f: Double) = (inF * f).roundToInt()
    private fun outAt(f: Double) = (totalOut + outF * f).roundToInt()

    /** The accent pass: the mask sweeps across and back, so the accent text only flashes. */
    fun wipeMaskKeyframes(): JsonArray = buildJsonArray {
        add(kf(0, at(offStart), e))
        add(kf(inAt(FADE_IN_END), at(offStart), l))
        add(kf(inAt(WIPE_MIDPOINT), at(offEnd), l))
        add(kf(inF, at(offStart), e))
        add(kf(totalOut, at(offStart), e))
        add(kf(outAt(WIPE_MIDPOINT), at(offEnd), l))
        add(kf(outAt(1 - FADE_IN_END), at(offStart), e))
        add(kf(totalOut + outF, at(offStart)))
    }

    /** The reveal pass: the mask sweeps across and stays, so the text remains. */
    fun revealMaskKeyframes(): JsonArray = buildJsonArray {
        add(kf(0, at(offStart), e))
        add(kf(inAt(FADE_IN_END), at(offStart), l))
        add(kf(inAt(WIPE_MIDPOINT), at(offEnd), e))
        add(kf(inF, at(offEnd), e))
        add(kf(totalOut, at(offEnd), e))
        add(kf(outAt(WIPE_MIDPOINT), at(offEnd), e))
        add(kf(outAt(1 - FADE_IN_END), at(offStart), e))
        add(kf(totalOut + outF, at(offStart)))
    }

    /** The rule rides the leading edge of the wipe. */
    fun lineKeyframes(): JsonArray = buildJsonArray {
        add(kf(0, at(offStart + lineEdgeOffset), e))
        add(kf(inAt(FADE_IN_END), at(offStart + lineEdgeOffset), l))
        add(kf(inAt(WIPE_MIDPOINT), at(offEnd + lineEdgeOffset), l))
        add(kf(inF, at(offStart + lineEdgeOffset), e))
        add(kf(totalOut, at(offStart + lineEdgeOffset), e))
        add(kf(outAt(WIPE_MIDPOINT), at(offEnd + lineEdgeOffset), l))
        add(kf(outAt(1 - FADE_IN_END), at(offStart + lineEdgeOffset), e))
        add(kf(totalOut + outF, at(offStart + lineEdgeOffset)))
    }

    fun lineOpacityKeyframes(): JsonArray = buildJsonArray {
        add(kf(0, jsonArrayOf(0.0), e))
        add(kf(inAt(FADE_IN_END), jsonArrayOf(FULL_PERCENT_D), e))
        add(kf(inAt(HOLD_START), jsonArrayOf(FULL_PERCENT_D), e))
        add(kf(inF, jsonArrayOf(0.0), e))
        add(kf(totalOut, jsonArrayOf(0.0), e))
        add(kf(outAt(FADE_OUT_START), jsonArrayOf(FULL_PERCENT_D), e))
        add(kf(outAt(HOLD_START), jsonArrayOf(FULL_PERCENT_D), e))
        add(kf(totalOut + outF, jsonArrayOf(0.0)))
    }

    fun keyframes(vararg points: KeyframeInput) =
        buildKeyframes(points.toList(), inF, holdF, outF, Easing.DEFAULT)
}

/**
 * One of the two lines, in one of its two passes. Each line is drawn twice: once in the accent
 * colour, which the wipe mask only flashes, and once in its own colour, which the reveal mask
 * leaves behind.
 */
private class WipeLine(g: WipeGeometry, isName: Boolean, isAccent: Boolean) {
    val maskName = when {
        isAccent && isName -> "Accent Name Mask"
        isAccent -> "Accent Info Mask"
        isName -> "Name Reveal Mask"
        else -> "Info Reveal Mask"
    }
    val layerName = when {
        isAccent && isName -> "Accent Name"
        isAccent -> "Accent Info"
        isName -> "Name"
        else -> "Info"
    }
    val sizePx = if (isName) g.nameSizePx else g.infoSizePx
    val y = if (isName) g.nameTextY else g.infoTextY
    val text = if (isName) g.cfg.nameText else g.cfg.infoText
    val weight = if (isName) g.cfg.nameWeight else g.cfg.infoWeight
    val transform = if (isName) g.cfg.nameTransform else g.cfg.infoTransform
    val color = when {
        isAccent -> g.accentLottie
        isName -> g.nameCLottie
        else -> g.infoCLottie
    }

    /**
     * The accent name takes the accent alpha, but the accent info line takes the info alpha --
     * asymmetric, and preserved as it was.
     */
    val alpha = when {
        isAccent && isName -> g.cfg.accentColorAlpha
        isName -> g.cfg.nameColorAlpha
        else -> g.cfg.infoColorAlpha
    }
    val maskKeyframes = if (isAccent) g.wipeMaskKeyframes() else g.revealMaskKeyframes()
}

class Style9DiagonalWipe : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val g = WipeGeometry(builder, cfg)
        // Added first renders on top.
        builder.addDiagonalRule(g)
        if (!cfg.hideName) builder.addWipedLine(g, WipeLine(g, isName = true, isAccent = true))
        if (!cfg.hideInfo) builder.addWipedLine(g, WipeLine(g, isName = false, isAccent = true))
        if (!cfg.hideName) builder.addWipedLine(g, WipeLine(g, isName = true, isAccent = false))
        if (!cfg.hideInfo) builder.addWipedLine(g, WipeLine(g, isName = false, isAccent = false))
        builder.addLogo(g)
    }
}

/** The rule that rides the wipe's leading edge. */
private fun LottieBuilder.addDiagonalRule(g: WipeGeometry) {
    val shapes = buildJsonArray {
        val items = mutableListOf(makePath(g.lineVertices, false))
        makeStroke(g.accentLottie, g.lineThickness, g.cfg.accentColorAlpha.toDouble())?.let { items.add(it) }
        add(makeGroup(items))
    }
    addShapeLayer(
        "Diagonal Line", shapes,
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.animatedProp(g.lineOpacityKeyframes()),
            position = LottieBuilder.animatedProp(g.lineKeyframes()),
        ),
    )
}

/** A line and the mask that wipes across it. */
private fun LottieBuilder.addWipedLine(g: WipeGeometry, line: WipeLine) {
    addShapeLayer(
        line.maskName,
        buildJsonArray {
            add(makeGroup(listOf(makePath(g.maskVertices), makeFill(listOf(1.0, 1.0, 1.0)))))
        },
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(line.maskKeyframes)),
        td = 1,
    )

    addFont(g.cfg.fontFamily, line.weight)
    addTextLayer(
        line.layerName,
        makeTextData(
            line.text, g.cfg.fontFamily, line.sizePx, line.weight, line.color,
            line.transform, g.justify,
        ),
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.staticProp(line.alpha),
            position = LottieBuilder.staticPropArray(g.textX, line.y, 0.0),
        ),
        tt = 1,
    )
}

/** The logo scales up beside the block, centred between the two lines. */
private fun LottieBuilder.addLogo(g: WipeGeometry) {
    val cfg = g.cfg
    if (!g.hasLogo || cfg.logoData == null) return
    val scale = (g.logoSizePx / cfg.logoH.toDouble()) * PERCENT_SCALE
    val cx = when {
        g.isCenter -> {
            val maxW = max(
                if (cfg.hideName) 0.0 else g.nameM.width.toDouble(),
                if (cfg.hideInfo) 0.0 else g.infoM.width.toDouble(),
            )
            g.canvasW / 2 - maxW / 2 - g.logoMargin - g.logoSizePx / 2
        }
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
            position = LottieBuilder.staticPropArray(cx, (g.infoCY + g.nameCY) / 2, 0.0),
            anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
            scale = LottieBuilder.animatedProp(scaleKFs),
        ),
    )
}
