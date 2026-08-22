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
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max

/** A baseline sits below a line's optical centre; nudge it up by this fraction of the line size. */
private const val BASELINE_NUDGE_FACTOR = 0.15

/** Border thickness is configured 0..n and scaled onto the base size by this. */
private const val BORDER_THICKNESS_FACTOR = 0.1

/** The two lines straddle the block centre by these fractions of their own size. */
private const val UPPER_LINE_FACTOR = 0.1
private const val LOWER_LINE_FACTOR = 0.9

/** The accent bar and the gaps around it, in em. */
private const val BAR_WIDTH_EM = 0.3
private const val BAR_HEIGHT_EM = 3.5
private const val TEXT_MARGIN_EM = 1.2
private const val LOGO_MARGIN_EM = 0.8
private const val BG_PADDING_EM = 2.0
private const val BG_OVERHANG_EM = 1.0

/** Slack added to a measured text run, in px: a little for the block, more for the slide-in. */
private const val TEXT_BLOCK_SLACK_PX = 10
private const val SLIDE_CLEARANCE_PX = 30
private const val MASK_PAD_PX = 20.0

/** A text mask is half again as tall as its line so descenders are never clipped. */
private const val MASK_HEIGHT_FACTOR = 1.5

/** The background's corner is slightly rounder than the configured radius. */
private const val BG_CORNER_FACTOR = 1.1

/** How far the bar starts off its resting place, in em, depending on which way it flies in. */
private const val BAR_OFFSET_RIGHT_EM = -6.0
private const val BAR_OFFSET_LEFT_EM = 10.0

/** Timing, as percentages of the animation. */
private const val LOGO_HOLD_PCT = 55.0
private const val NAME_START_CENTRE_PCT = 50.0
private const val NAME_START_PCT = 45.0
private const val INFO_START_PCT = 50.0
private const val BAR_START_PCT = 23.0
private const val BAR_OPAQUE_PCT = 50.0
private const val BAR_SETTLED_PCT = 75.0
private const val BG_START_PCT = 30.0
private const val END_PCT = 100.0

/** Justify codes Lottie writes for left, right and centred text. */
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1
private const val JUSTIFY_CENTRE = 2

/**
 * Everything Style1Bar's layers are positioned from, computed once.
 *
 * Members are properties rather than constructor parameters on purpose: this is thirty-odd
 * values, and a constructor taking them would only trade one detekt finding for another.
 */
private class BarGeometry(builder: LottieBuilder, val cfg: LottieGenConfig) {
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

    val barWidth = emToPx(BAR_WIDTH_EM, baseSize)
    val barHeight = emToPx(BAR_HEIGHT_EM, baseSize)
    val textMargin = emToPx(TEXT_MARGIN_EM, baseSize)
    val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
    val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
    val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
    val cornerPx = emToPx(cfg.corners.toDouble(), baseSize)
    val borderPx = cfg.borderThickness * baseSize * BORDER_THICKNESS_FACTOR
    val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
    val logoMargin = emToPx(LOGO_MARGIN_EM, baseSize)

    val accentLottie = hexToLottie(cfg.accentColor)
    val bgLottie = hexToLottie(cfg.bgColor)
    val borderLottie = hexToLottie(cfg.borderColor)
    val nameCLottie = hexToLottie(cfg.nameColor)
    val infoCLottie = hexToLottie(cfg.infoColor)

    val textBlockW = max(nameM.width, infoM.width) + textMargin + TEXT_BLOCK_SLACK_PX
    val logoSpace = if (cfg.logoEnabled) logoSizePx + logoMargin else 0.0
    val totalContentW = textBlockW + barWidth + logoSpace
    val bgW = totalContentW + emToPx(BG_PADDING_EM, baseSize)
    val bgH = barHeight + emToPx(BG_PADDING_EM, baseSize)

    val canvasW = cfg.canvasW.toDouble()
    val canvasH = cfg.canvasH.toDouble()
    val baseY = canvasH - marginVPx - barHeight / 2

    val isRight = cfg.align == "right"
    val isCenter = cfg.align == "center"

    val baseX = when {
        isRight -> canvasW - marginHPx - totalContentW / 2
        isCenter -> canvasW / 2
        else -> marginHPx + totalContentW / 2
    }
    val barX = when {
        isRight -> baseX + totalContentW / 2 - logoSpace - barWidth / 2
        else -> baseX - totalContentW / 2 + logoSpace + barWidth / 2
    }
    val textBaseX = when {
        isRight -> barX - textMargin
        isCenter -> barX + barWidth / 2 + textBlockW / 2
        else -> barX + barWidth / 2 + textMargin
    }

    val nameY = baseY - lineSpacingPx / 2 - nameSizePx * UPPER_LINE_FACTOR
    val infoY = baseY + lineSpacingPx / 2 + infoSizePx * LOWER_LINE_FACTOR

    /** Which way each line flies in, and how Lottie should justify it. */
    val justify = when {
        isRight -> JUSTIFY_RIGHT
        isCenter -> JUSTIFY_CENTRE
        else -> JUSTIFY_LEFT
    }
    private val nameSlideDir = if (isRight) 1.0 else -1.0
    private val infoSlideDir = if (isRight || isCenter) 1.0 else -1.0
    val nameSlideOffset = (nameM.width + SLIDE_CLEARANCE_PX) * nameSlideDir
    val infoSlideOffset = (infoM.width + SLIDE_CLEARANCE_PX) * infoSlideDir

    val namePctStart = if (isCenter) NAME_START_CENTRE_PCT else NAME_START_PCT

    fun keyframes(vararg points: KeyframeInput) =
        buildKeyframes(points.toList(), inF, holdF, outF, Easing.DEFAULT)
}

class Style1Bar : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val g = BarGeometry(builder, cfg)
        builder.addLogo(g)
        if (!cfg.hideName) builder.addMaskedLine(g, BarLine(g, isName = true))
        if (!cfg.hideInfo) builder.addMaskedLine(g, BarLine(g, isName = false))
        builder.addAccentBar(g)
        if (cfg.bgEnabled) builder.addBackground(g)
    }
}

/** The logo rides out from behind the bar, arriving with the text. */
private fun LottieBuilder.addLogo(g: BarGeometry) {
    val cfg = g.cfg
    if (!cfg.logoEnabled || cfg.logoData == null) return
    addImageAsset("logo_0", cfg.logoData, cfg.logoW, cfg.logoH)
    val scale = (g.logoSizePx / max(cfg.logoW, cfg.logoH).toDouble()) * PERCENT_SCALE
    val logoX = if (g.isRight) {
        g.baseX + g.totalContentW / 2 - g.logoSizePx / 2
    } else {
        g.baseX - g.totalContentW / 2 + g.logoSizePx / 2
    }
    val posKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(g.barX, g.baseY, 0.0)),
        KeyframeInput(LOGO_HOLD_PCT, jsonArrayOf(g.barX, g.baseY, 0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(logoX, g.baseY, 0.0)),
    )
    val opKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0)),
        KeyframeInput(LOGO_HOLD_PCT, jsonArrayOf(0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(FULL_PERCENT_D)),
    )
    addImageLayer(
        "Logo", "logo_0",
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.animatedProp(opKFs),
            position = LottieBuilder.animatedProp(posKFs),
            anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
            scale = LottieBuilder.staticPropArray(scale, scale, FULL_PERCENT_D),
        ),
    )
}

/**
 * One of the two text lines, resolved from the geometry.
 *
 * The name and the info line differ only in which half of the config they read, so this says that
 * once rather than passing a dozen parallel arguments down to the layer builder.
 */
private class BarLine(g: BarGeometry, isName: Boolean) {
    val maskName = if (isName) "Name Mask" else "Info Mask"
    val layerName = if (isName) "Name" else "Info"
    val width = if (isName) g.nameM.width else g.infoM.width
    val sizePx = if (isName) g.nameSizePx else g.infoSizePx
    val y = if (isName) g.nameY else g.infoY
    val slideOffset = if (isName) g.nameSlideOffset else g.infoSlideOffset
    val startPct = if (isName) g.namePctStart else INFO_START_PCT
    val text = if (isName) g.cfg.nameText else g.cfg.infoText
    val weight = if (isName) g.cfg.nameWeight else g.cfg.infoWeight
    val color = if (isName) g.nameCLottie else g.infoCLottie
    val transform = if (isName) g.cfg.nameTransform else g.cfg.infoTransform
    val alpha = if (isName) g.cfg.nameColorAlpha else g.cfg.infoColorAlpha
}

/** A line and the mask that wipes it in: identical machinery for the name and the info line. */
private fun LottieBuilder.addMaskedLine(g: BarGeometry, line: BarLine) {
    val maskOffsetX = when {
        g.isRight -> -line.width / 2.0
        g.isCenter -> 0.0
        else -> line.width / 2.0
    }
    val maskShapes = buildJsonArray {
        add(
            makeGroup(
                listOf(
                    makeRect(line.width + MASK_PAD_PX, line.sizePx * MASK_HEIGHT_FACTOR, 0.0),
                    makeFill(listOf(1.0, 1.0, 1.0)),
                ),
            ),
        )
    }
    addShapeLayer(
        line.maskName, maskShapes,
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
        KeyframeInput(0.0, jsonArrayOf(g.textBaseX + line.slideOffset, line.y, 0.0)),
        KeyframeInput(line.startPct, jsonArrayOf(g.textBaseX + line.slideOffset, line.y, 0.0)),
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

/** The bar itself slides to its resting place and is the first thing on screen. */
private fun LottieBuilder.addAccentBar(g: BarGeometry) {
    val offset = emToPx(if (g.isRight) BAR_OFFSET_RIGHT_EM else BAR_OFFSET_LEFT_EM, g.baseSize)
    val posKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(g.barX + offset, g.baseY, 0.0)),
        KeyframeInput(BAR_START_PCT, jsonArrayOf(g.barX + offset, g.baseY, 0.0)),
        KeyframeInput(BAR_SETTLED_PCT, jsonArrayOf(g.barX, g.baseY, 0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(g.barX, g.baseY, 0.0)),
    )
    val opKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0)),
        KeyframeInput(BAR_START_PCT, jsonArrayOf(0.0)),
        KeyframeInput(BAR_OPAQUE_PCT, jsonArrayOf(FULL_PERCENT_D)),
        KeyframeInput(END_PCT, jsonArrayOf(FULL_PERCENT_D)),
    )
    val shapes = buildJsonArray {
        add(
            makeGroup(
                listOf(
                    makeRect(g.barWidth, g.barHeight, 0.0),
                    makeFill(g.accentLottie, g.cfg.accentColorAlpha.toDouble()),
                ),
            ),
        )
    }
    addShapeLayer(
        "Accent Bar", shapes,
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.animatedProp(opKFs),
            position = LottieBuilder.animatedProp(posKFs),
        ),
    )
}

/** The plate grows out from the bar's edge, so it is anchored on the side it grows from. */
private fun LottieBuilder.addBackground(g: BarGeometry) {
    val cfg = g.cfg
    val overhang = emToPx(BG_OVERHANG_EM, g.baseSize)
    val bgX: Double
    val anchorX: Double
    when {
        g.isRight -> {
            bgX = g.baseX + g.totalContentW / 2 + overhang
            anchorX = g.bgW / 2
        }
        g.isCenter -> {
            bgX = g.baseX
            anchorX = 0.0
        }
        else -> {
            bgX = g.baseX - g.totalContentW / 2 - overhang
            anchorX = -g.bgW / 2
        }
    }

    val sizeKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, g.bgH)),
        KeyframeInput(BG_START_PCT, jsonArrayOf(0.0, g.bgH)),
        KeyframeInput(END_PCT, jsonArrayOf(g.bgW, g.bgH)),
    )
    val items = mutableListOf(
        makeAnimatedRect(sizeKFs, g.cornerPx * BG_CORNER_FACTOR),
        makeFill(g.bgLottie, cfg.bgColorAlpha.toDouble()),
    )
    if (g.borderPx > 0) {
        val borderKFs = g.keyframes(
            KeyframeInput(0.0, jsonArrayOf(0.0)),
            KeyframeInput(BG_START_PCT, jsonArrayOf(0.0)),
            KeyframeInput(END_PCT, jsonArrayOf(g.borderPx)),
        )
        items.add(makeAnimatedStroke(g.borderLottie, borderKFs, cfg.borderColorAlpha.toDouble()))
    }

    addShapeLayer(
        "Background", buildJsonArray { add(makeGroup(items)) },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(bgX, g.baseY, 0.0),
            anchor = LottieBuilder.staticPropArray(anchorX, 0.0, 0.0),
        ),
    )
}
