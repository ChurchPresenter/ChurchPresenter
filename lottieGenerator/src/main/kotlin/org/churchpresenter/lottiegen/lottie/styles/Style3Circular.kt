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
import org.churchpresenter.lottiegen.lottie.makeAnimatedStroke
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max

/** The pill behind each line: padding either side, and its height as a multiple of the line size. */
private const val PILL_PAD_PX = 20.0
private const val PILL_HEIGHT_FACTOR = 1.5

/** A baseline sits below a line's optical centre; nudge it up by this fraction of the line size. */
private const val BASELINE_NUDGE_FACTOR = 0.15

/** The two lines straddle the block centre by these fractions of their own size. */
private const val UPPER_LINE_FACTOR = 0.1
private const val LOWER_LINE_FACTOR = 0.9

/** The rounded square that holds the logo, and how much of it the logo fills, in em. */
private const val CIRCLE_SIZE_EM = 5.5
private const val LOGO_MAX_EM = 4.5

/** The bar behind the text: wider than the text by this, and shorter than the square by this. */
private const val BAR_PAD_EM = 1.0
private const val BAR_INSET_EM = 0.5
private const val TEXT_OFFSET_EM = 0.75
private const val TEXT_BLOCK_SLACK_PX = 20

/** Border thickness is configured 0..n and scaled onto the base size by this. */
private const val BORDER_THICKNESS_FACTOR = 0.1

/** Each line slides in from just beyond the square. */
private const val SLIDE_CLEARANCE_FACTOR = 1.02

/** The bar's corner is slightly rounder than the configured radius. */
private const val BG_CORNER_FACTOR = 1.1

/** The square spins a quarter turn into place, the way it leans depending on the alignment. */
private const val SPIN_DEGREES = 90.0

/** Timing, as percentages of the animation. */
private const val SQUARE_SETTLED_PCT = 40.0
private const val TEXT_START_PCT = 70.0
private const val END_PCT = 100.0

/** Justify codes Lottie writes for left and right text. */
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1

/**
 * Everything Style3Circular's layers are positioned from, computed once.
 *
 * Members are properties rather than constructor parameters on purpose: there are thirty-odd, and
 * a constructor taking them would only trade one detekt finding for another.
 */
private class CircleGeometry(builder: LottieBuilder, val cfg: LottieGenConfig) {
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

    val circleSize = emToPx(CIRCLE_SIZE_EM, baseSize)
    val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
    val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
    val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
    val cornerPx = emToPx(cfg.corners.toDouble(), baseSize)
    val borderPx = cfg.borderThickness * baseSize * BORDER_THICKNESS_FACTOR

    val accentLottie = hexToLottie(cfg.accentColor)
    val bgLottie = hexToLottie(cfg.bgColor)
    val borderLottie = hexToLottie(cfg.borderColor)
    val nameCLottie = hexToLottie(cfg.nameColor)
    val infoCLottie = hexToLottie(cfg.infoColor)

    val canvasW = cfg.canvasW.toDouble()
    val canvasH = cfg.canvasH.toDouble()
    val isRight = cfg.align == "right"
    val isCenter = cfg.align == "center"

    val textBlockW = max(nameM.width, infoM.width) + TEXT_BLOCK_SLACK_PX
    val bgBarW = textBlockW + emToPx(BAR_PAD_EM, baseSize)
    val bgBarH = circleSize - emToPx(BAR_INSET_EM, baseSize)

    val baseY = canvasH - marginVPx - circleSize / 2
    val hasLogo = cfg.logoEnabled && cfg.logoData != null

    val circleX = when {
        isRight -> canvasW - marginHPx - circleSize / 2
        isCenter -> {
            val totalW = (if (hasLogo) circleSize else 0.0) + bgBarW
            canvasW / 2 - totalW / 2 + circleSize / 2
        }
        else -> marginHPx + circleSize / 2
    }

    private val textOffset = emToPx(TEXT_OFFSET_EM, baseSize)
    val textBaseX = when {
        hasLogo ->
            if (isRight) circleX - circleSize / 2 - textOffset else circleX + circleSize / 2 + textOffset
        isCenter -> canvasW / 2 - bgBarW / 2 + textOffset
        isRight -> canvasW - marginHPx - textOffset
        else -> marginHPx + textOffset
    }

    val nameY = baseY - lineSpacingPx / 2 - nameSizePx * UPPER_LINE_FACTOR
    val infoY = baseY + lineSpacingPx / 2 + infoSizePx * LOWER_LINE_FACTOR
    val slideAmount = circleSize * SLIDE_CLEARANCE_FACTOR
    val justify = if (isRight) JUSTIFY_RIGHT else JUSTIFY_LEFT

    /** The square leans one way on the left and the other on the right before righting itself. */
    val spinFrom = if (isRight) -SPIN_DEGREES else SPIN_DEGREES
    val logoScale =
        if (hasLogo) (emToPx(LOGO_MAX_EM, baseSize) / max(cfg.logoW, cfg.logoH).toDouble()) * PERCENT_SCALE
        else 0.0

    fun keyframes(vararg points: KeyframeInput) =
        buildKeyframes(points.toList(), inF, holdF, outF, Easing.DEFAULT)

    /** The quarter-turn every part of the square shares. */
    fun spinKeyframes() = keyframes(
        KeyframeInput(0.0, jsonArrayOf(spinFrom)),
        KeyframeInput(SQUARE_SETTLED_PCT, jsonArrayOf(0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(0.0)),
    )
}

/**
 * One of the two lines. They differ only in which half of the config they read and which way they
 * slide -- the name drops from above, the info line rises from below.
 */
private class CircleLine(g: CircleGeometry, isName: Boolean) {
    val maskName = if (isName) "Name Mask" else "Info Mask"
    val layerName = if (isName) "Name" else "Info"
    val width = if (isName) g.nameM.width else g.infoM.width
    val sizePx = if (isName) g.nameSizePx else g.infoSizePx
    val y = if (isName) g.nameY else g.infoY
    val slideFrom = if (isName) y + g.slideAmount else y - g.slideAmount
    val text = if (isName) g.cfg.nameText else g.cfg.infoText
    val weight = if (isName) g.cfg.nameWeight else g.cfg.infoWeight
    val color = if (isName) g.nameCLottie else g.infoCLottie
    val transform = if (isName) g.cfg.nameTransform else g.cfg.infoTransform
    val alpha = if (isName) g.cfg.nameColorAlpha else g.cfg.infoColorAlpha
}

class Style3Circular : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val g = CircleGeometry(builder, cfg)
        builder.addLogo(g)
        if (!cfg.hideName) builder.addMaskedLine(g, CircleLine(g, isName = true))
        if (!cfg.hideInfo) builder.addMaskedLine(g, CircleLine(g, isName = false))
        builder.addLogoPlate(g)
        if (cfg.bgEnabled) builder.addBackgroundBar(g)
    }
}

/** The logo spins up with its plate, scaling from nothing. */
private fun LottieBuilder.addLogo(g: CircleGeometry) {
    val cfg = g.cfg
    if (!g.hasLogo || cfg.logoData == null) return
    addImageAsset("logo_0", cfg.logoData, cfg.logoW, cfg.logoH)
    val scaleKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, FULL_PERCENT_D)),
        KeyframeInput(SQUARE_SETTLED_PCT, jsonArrayOf(g.logoScale, g.logoScale, FULL_PERCENT_D)),
        KeyframeInput(END_PCT, jsonArrayOf(g.logoScale, g.logoScale, FULL_PERCENT_D)),
    )
    addImageLayer(
        "Logo", "logo_0",
        LottieBuilder.defaultTransform(
            rotation = LottieBuilder.animatedProp(g.spinKeyframes()),
            position = LottieBuilder.staticPropArray(g.circleX, g.baseY, 0.0),
            anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
            scale = LottieBuilder.animatedProp(scaleKFs),
        ),
    )
}

/** A line, clipped to a pill, sliding vertically into place. */
private fun LottieBuilder.addMaskedLine(g: CircleGeometry, line: CircleLine) {
    val maskOffsetX = if (g.isRight) -line.width / 2.0 else line.width / 2.0
    addShapeLayer(
        line.maskName,
        buildJsonArray {
            add(
                makeGroup(
                    listOf(
                        makeRect(line.width + PILL_PAD_PX, line.sizePx * PILL_HEIGHT_FACTOR, 0.0),
                        makeFill(listOf(1.0, 1.0, 1.0)),
                    ),
                ),
            )
        },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(
                g.textBaseX + maskOffsetX,
                line.y - line.sizePx * BASELINE_NUDGE_FACTOR,
                0.0,
            ),
        ),
        td = 1,
    )

    addFont(g.cfg.fontFamily, line.weight)
    val posKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(g.textBaseX, line.slideFrom, 0.0)),
        KeyframeInput(TEXT_START_PCT, jsonArrayOf(g.textBaseX, line.slideFrom, 0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(g.textBaseX, line.y, 0.0)),
    )
    addTextLayer(
        line.layerName,
        makeTextData(
            line.text, g.cfg.fontFamily, line.sizePx, line.weight, line.color,
            line.transform, g.justify,
        ),
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.staticProp(line.alpha),
            position = LottieBuilder.animatedProp(posKFs),
        ),
        tt = 1,
    )
}

/** The rounded square behind the logo, spinning up on the same quarter turn. */
private fun LottieBuilder.addLogoPlate(g: CircleGeometry) {
    if (!g.hasLogo) return
    val scaleKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, FULL_PERCENT_D)),
        KeyframeInput(SQUARE_SETTLED_PCT, jsonArrayOf(FULL_PERCENT_D, FULL_PERCENT_D, FULL_PERCENT_D)),
        KeyframeInput(END_PCT, jsonArrayOf(FULL_PERCENT_D, FULL_PERCENT_D, FULL_PERCENT_D)),
    )
    val items = mutableListOf(
        makeRect(g.circleSize, g.circleSize, g.cornerPx),
        makeFill(g.accentLottie, g.cfg.accentColorAlpha.toDouble()),
    )
    if (g.borderPx > 0) {
        makeStroke(g.borderLottie, g.borderPx, g.cfg.borderColorAlpha.toDouble())?.let { items.add(it) }
    }
    addShapeLayer(
        "Logo BG",
        buildJsonArray { add(makeGroup(items)) },
        LottieBuilder.defaultTransform(
            rotation = LottieBuilder.animatedProp(g.spinKeyframes()),
            position = LottieBuilder.staticPropArray(g.circleX, g.baseY, 0.0),
            scale = LottieBuilder.animatedProp(scaleKFs),
        ),
    )
}

/** The bar grows out from the square's edge, or from the block's edge when there is no logo. */
private fun LottieBuilder.addBackgroundBar(g: CircleGeometry) {
    val cfg = g.cfg
    val edgeX = when {
        g.hasLogo -> if (g.isRight) g.circleX - g.circleSize / 2 else g.circleX + g.circleSize / 2
        g.isCenter -> g.canvasW / 2 - g.bgBarW / 2
        g.isRight -> g.canvasW - g.marginHPx
        else -> g.marginHPx
    }
    val centreEnd = when {
        g.isCenter && !g.hasLogo -> g.canvasW / 2
        g.isRight -> edgeX - g.bgBarW / 2
        else -> edgeX + g.bgBarW / 2
    }

    val sizeKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, g.bgBarH)),
        KeyframeInput(SQUARE_SETTLED_PCT, jsonArrayOf(0.0, g.bgBarH)),
        KeyframeInput(END_PCT, jsonArrayOf(g.bgBarW, g.bgBarH)),
    )
    val posKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(edgeX, g.baseY, 0.0)),
        KeyframeInput(SQUARE_SETTLED_PCT, jsonArrayOf(edgeX, g.baseY, 0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(centreEnd, g.baseY, 0.0)),
    )
    val items = mutableListOf(
        makeAnimatedRect(sizeKFs, g.cornerPx * BG_CORNER_FACTOR),
        makeFill(g.bgLottie, cfg.bgColorAlpha.toDouble()),
    )
    if (g.borderPx > 0) {
        val borderKFs = g.keyframes(
            KeyframeInput(0.0, jsonArrayOf(0.0)),
            KeyframeInput(SQUARE_SETTLED_PCT, jsonArrayOf(0.0)),
            KeyframeInput(END_PCT, jsonArrayOf(g.borderPx)),
        )
        items.add(makeAnimatedStroke(g.borderLottie, borderKFs, cfg.borderColorAlpha.toDouble()))
    }
    addShapeLayer(
        "Background Bar",
        buildJsonArray { add(makeGroup(items)) },
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(posKFs)),
    )
}
