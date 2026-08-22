package org.churchpresenter.lottiegen.lottie.styles

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
import org.churchpresenter.lottiegen.lottie.makeRandomFadeAnimator
import org.churchpresenter.lottiegen.lottie.makeTextDataWithAnimators
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max

/** The gap between the logo and the text block, in em. */
private const val LOGO_MARGIN_EM = 1.0

/** A baseline sits below its line's centre; this fraction of the line size puts it right. */
private const val BASELINE_FACTOR = 0.35

/** The window each line's letters fade in over, as percentages of the animation. */
private const val NAME_FADE_FROM_PCT = 0.0
private const val NAME_FADE_TO_PCT = 55.0
private const val INFO_FADE_FROM_PCT = 25.0
private const val INFO_FADE_TO_PCT = 70.0

/** The logo has finished scaling up by here. */
private const val LOGO_GROWN_PCT = 25.0
private const val END_PCT = 100.0

/** Justify codes Lottie writes for left, right and centred text. */
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1
private const val JUSTIFY_CENTRE = 2

/**
 * Everything Style7RandomFade's layers are positioned from, computed once.
 *
 * Members are properties rather than constructor parameters on purpose: a constructor taking this
 * many would only trade one detekt finding for another.
 */
private class FadeGeometry(builder: LottieBuilder, val cfg: LottieGenConfig) {
    val inF = builder.inFrames
    val holdF = builder.holdFrames
    val outF = builder.outFrames

    val baseSize = cfg.baseSize.toDouble()
    val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
    val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
    val nameM = TextMeasurer.measure(
        cfg.nameText, cfg.fontFamily, nameSizePx.toFloat(), cfg.nameWeight, cfg.nameTransform,
    )
    val infoM = TextMeasurer.measure(
        cfg.infoText, cfg.fontFamily, infoSizePx.toFloat(), cfg.infoWeight, cfg.infoTransform,
    )

    val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
    val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
    val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)

    val nameCLottie = hexToLottie(cfg.nameColor)
    val infoCLottie = hexToLottie(cfg.infoColor)

    val canvasW = cfg.canvasW.toDouble()
    val canvasH = cfg.canvasH.toDouble()
    val isRight = cfg.align == "right"
    val isCenter = cfg.align == "center"

    val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
    val logoMargin = emToPx(LOGO_MARGIN_EM, baseSize)
    val hasLogo = cfg.logoEnabled && cfg.logoData != null
    private val logoSpace = if (hasLogo) logoSizePx + logoMargin else 0.0

    /** A hidden line takes no vertical space, and there is no gap unless both lines are shown. */
    private val nameBlockH = if (cfg.hideName) 0.0 else nameSizePx
    private val infoBlockH = if (cfg.hideInfo) 0.0 else infoSizePx
    private val gap = if (!cfg.hideName && !cfg.hideInfo) lineSpacingPx else 0.0
    private val totalH = nameBlockH + gap + infoBlockH
    private val blockTopY = canvasH - marginVPx - totalH
    val nameCY = blockTopY + nameBlockH / 2
    val infoCY = blockTopY + nameBlockH + gap + infoBlockH / 2

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

    fun keyframes(vararg points: KeyframeInput) =
        buildKeyframes(points.toList(), inF, holdF, outF, Easing.DEFAULT)
}

/** One of the two lines: they differ only in their config half and their fade window. */
private class FadeLine(g: FadeGeometry, isName: Boolean) {
    val layerName = if (isName) "Name" else "Info"
    val sizePx = if (isName) g.nameSizePx else g.infoSizePx
    val y = if (isName) g.nameTextY else g.infoTextY
    val fadeFrom = if (isName) NAME_FADE_FROM_PCT else INFO_FADE_FROM_PCT
    val fadeTo = if (isName) NAME_FADE_TO_PCT else INFO_FADE_TO_PCT
    val text = if (isName) g.cfg.nameText else g.cfg.infoText
    val weight = if (isName) g.cfg.nameWeight else g.cfg.infoWeight
    val color = if (isName) g.nameCLottie else g.infoCLottie
    val transform = if (isName) g.cfg.nameTransform else g.cfg.infoTransform
    val alpha = if (isName) g.cfg.nameColorAlpha else g.cfg.infoColorAlpha
}

class Style7RandomFade : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val g = FadeGeometry(builder, cfg)
        if (!cfg.hideName) builder.addFadingLine(g, FadeLine(g, isName = true))
        if (!cfg.hideInfo) builder.addFadingLine(g, FadeLine(g, isName = false))
        builder.addLogo(g)
    }
}

/** A line whose letters fade in one at a time, in a random order. */
private fun LottieBuilder.addFadingLine(g: FadeGeometry, line: FadeLine) {
    val animator = makeRandomFadeAnimator(line.fadeFrom, line.fadeTo, g.inF, g.holdF, g.outF)
    val data = makeTextDataWithAnimators(
        line.text, g.cfg.fontFamily, line.sizePx, line.weight,
        line.color, line.transform, g.justify, listOf(animator),
    )
    addFont(g.cfg.fontFamily, line.weight)
    addTextLayer(
        line.layerName, data,
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.staticProp(line.alpha),
            position = LottieBuilder.staticPropArray(g.textX, line.y, 0.0),
        ),
    )
}

/** The logo scales up beside the text block, centred on it. */
private fun LottieBuilder.addLogo(g: FadeGeometry) {
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
            position = LottieBuilder.staticPropArray(cx, (g.nameCY + g.infoCY) / 2, 0.0),
            anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
            scale = LottieBuilder.animatedProp(scaleKFs),
        ),
    )
}
