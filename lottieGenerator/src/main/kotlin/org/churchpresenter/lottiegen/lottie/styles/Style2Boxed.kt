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

/** Padding around the logo's backing plate, in em, wider than it is tall. */
private const val LOGO_BG_PAD_W_EM = 1.2
private const val LOGO_BG_PAD_H_EM = 0.8

/** Padding inside each text box, in em. */
private const val BOX_PAD_X_EM = 1.3
private const val BOX_PAD_Y_EM = 0.5

/** Border thickness is configured 0..n and scaled onto the base size by this. */
private const val BORDER_THICKNESS_FACTOR = 0.1

/** A baseline sits below its box's centre; this fraction of the line size puts it right. */
private const val BASELINE_FACTOR = 0.35

/** Each line slides in from just beyond its own box. */
private const val SLIDE_CLEARANCE_FACTOR = 1.02

/** The boxes' corners are slightly rounder than the configured radius. */
private const val BG_CORNER_FACTOR = 1.1

/** Timing, as percentages of the animation. */
private const val NAME_START_CENTRE_PCT = 50.0
private const val NAME_START_PCT = 45.0
private const val INFO_START_PCT = 50.0
private const val LOGO_START_PCT = 50.0
private const val LOGO_BG_GROWN_PCT = 45.0
private const val END_PCT = 100.0

/** Justify codes Lottie writes for left, right and centred text. */
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1
private const val JUSTIFY_CENTRE = 2

/**
 * Everything Style2Boxed's layers are positioned from, computed once.
 *
 * Members are properties rather than constructor parameters on purpose: there are thirty-odd, and
 * a constructor taking them would only trade one detekt finding for another.
 */
private class BoxGeometry(builder: LottieBuilder, val cfg: LottieGenConfig) {
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

    val paddingX = emToPx(BOX_PAD_X_EM, baseSize)
    val paddingY = emToPx(BOX_PAD_Y_EM, baseSize)
    val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
    val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
    val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
    val cornerPx = emToPx(cfg.corners.toDouble(), baseSize)
    val borderPx = cfg.borderThickness * baseSize * BORDER_THICKNESS_FACTOR
    val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)

    val accentLottie = hexToLottie(cfg.accentColor)
    val bgLottie = hexToLottie(cfg.bgColor)
    val borderLottie = hexToLottie(cfg.borderColor)
    val nameCLottie = hexToLottie(cfg.nameColor)
    val infoCLottie = hexToLottie(cfg.infoColor)

    val canvasW = cfg.canvasW.toDouble()
    val canvasH = cfg.canvasH.toDouble()
    val isRight = cfg.align == "right"
    val isCenter = cfg.align == "center"

    val nameBoxW = nameM.width + paddingX * 2
    val nameBoxH = nameSizePx + paddingY * 2
    val infoBoxW = infoM.width + paddingX * 2
    val infoBoxH = infoSizePx + paddingY * 2

    val totalH = nameBoxH + lineSpacingPx + infoBoxH
    val baseY = canvasH - marginVPx - totalH / 2

    val hasLogo = cfg.logoEnabled && cfg.logoData != null
    val logoBgW = if (hasLogo) logoSizePx + emToPx(LOGO_BG_PAD_W_EM, baseSize) else 0.0
    val logoBgH = if (hasLogo) logoSizePx + emToPx(LOGO_BG_PAD_H_EM, baseSize) else 0.0
    val logoSlideOffset = if (hasLogo) totalH + logoBgH else 0.0
    val logoScale =
        if (hasLogo) (logoSizePx / max(cfg.logoW, cfg.logoH).toDouble()) * PERCENT_SCALE else 0.0

    /**
     * The logo sits outboard of the text boxes and pushes them along, so where it goes and where
     * they go are one calculation, not two.
     */
    private val edgeX = when {
        isRight -> canvasW - marginHPx
        isCenter -> canvasW / 2
        else -> marginHPx
    }
    val logoX = when {
        !hasLogo -> 0.0
        isRight -> edgeX - logoBgW / 2 - lineSpacingPx
        isCenter -> edgeX - max(nameBoxW, infoBoxW) / 2 - lineSpacingPx - logoBgW / 2
        else -> edgeX + logoBgW / 2 + lineSpacingPx
    }
    val baseX = when {
        !hasLogo || isCenter -> edgeX
        isRight -> edgeX - (logoBgW + lineSpacingPx * 2)
        else -> edgeX + (logoBgW + lineSpacingPx * 2)
    }

    val nameX = when {
        isRight -> baseX - nameBoxW / 2
        isCenter -> baseX
        else -> baseX + nameBoxW / 2
    }
    val infoX = when {
        isRight -> baseX - infoBoxW / 2
        isCenter -> baseX
        else -> baseX + infoBoxW / 2
    }
    val nameTextX = when {
        isRight -> baseX - paddingX
        isCenter -> nameX
        else -> baseX + paddingX
    }
    val infoTextX = when {
        isRight -> baseX - paddingX
        isCenter -> infoX
        else -> baseX + paddingX
    }

    val nameBgY = baseY - lineSpacingPx / 2 - infoBoxH / 2
    val infoBgY = baseY + nameBoxH / 2 + lineSpacingPx / 2
    val nameTextY = nameBgY + nameSizePx * BASELINE_FACTOR
    val infoTextY = infoBgY + infoSizePx * BASELINE_FACTOR

    val justify = when {
        isRight -> JUSTIFY_RIGHT
        isCenter -> JUSTIFY_CENTRE
        else -> JUSTIFY_LEFT
    }
    val namePctStart = if (isCenter) NAME_START_CENTRE_PCT else NAME_START_PCT
    private val nameSlideDir = if (isRight) 1.0 else -1.0
    private val infoSlideDir = if (isCenter) 1.0 else nameSlideDir
    val nameSlideOffset = nameBoxW * SLIDE_CLEARANCE_FACTOR * nameSlideDir
    val infoSlideOffset = infoBoxW * SLIDE_CLEARANCE_FACTOR * infoSlideDir

    fun keyframes(vararg points: KeyframeInput) =
        buildKeyframes(points.toList(), inF, holdF, outF, Easing.DEFAULT)
}

/**
 * One of the two boxed lines. The name and the info line differ only in which half of the config
 * they read and which colour their plate takes, so this says that once.
 */
private class BoxLine(g: BoxGeometry, isName: Boolean) {
    val maskName = if (isName) "Name Mask" else "Info Mask"
    val layerName = if (isName) "Name" else "Info"
    val bgName = if (isName) "Name BG" else "Info BG"
    val boxW = if (isName) g.nameBoxW else g.infoBoxW
    val boxH = if (isName) g.nameBoxH else g.infoBoxH
    val boxX = if (isName) g.nameX else g.infoX
    val boxY = if (isName) g.nameBgY else g.infoBgY
    val textX = if (isName) g.nameTextX else g.infoTextX
    val textY = if (isName) g.nameTextY else g.infoTextY
    val sizePx = if (isName) g.nameSizePx else g.infoSizePx
    val slideOffset = if (isName) g.nameSlideOffset else g.infoSlideOffset
    val startPct = if (isName) g.namePctStart else INFO_START_PCT
    val text = if (isName) g.cfg.nameText else g.cfg.infoText
    val weight = if (isName) g.cfg.nameWeight else g.cfg.infoWeight
    val color = if (isName) g.nameCLottie else g.infoCLottie
    val transform = if (isName) g.cfg.nameTransform else g.cfg.infoTransform
    val alpha = if (isName) g.cfg.nameColorAlpha else g.cfg.infoColorAlpha

    /** The name's plate takes the accent colour; the info line's takes the background colour. */
    val plateColor = if (isName) g.accentLottie else g.bgLottie
    val plateAlpha = if (isName) g.cfg.accentColorAlpha else g.cfg.bgColorAlpha
}

class Style2Boxed : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val g = BoxGeometry(builder, cfg)
        val name = BoxLine(g, isName = true)
        val info = BoxLine(g, isName = false)
        // Added first renders on top.
        builder.addLogo(g)
        if (!cfg.hideName) builder.addMaskedLine(g, name)
        if (!cfg.hideInfo) builder.addMaskedLine(g, info)
        if (!cfg.hideName && cfg.bgEnabled) builder.addLinePlate(g, name)
        if (!cfg.hideInfo && cfg.bgEnabled) builder.addLinePlate(g, info)
        builder.addLogoPlate(g)
    }
}

/** The logo rises into place from below the block. */
private fun LottieBuilder.addLogo(g: BoxGeometry) {
    val cfg = g.cfg
    if (!g.hasLogo || cfg.logoData == null) return
    addImageAsset("logo_0", cfg.logoData, cfg.logoW, cfg.logoH)
    val posKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(g.logoX, g.baseY + g.logoSlideOffset, 0.0)),
        KeyframeInput(LOGO_START_PCT, jsonArrayOf(g.logoX, g.baseY + g.logoSlideOffset, 0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(g.logoX, g.baseY, 0.0)),
    )
    val opKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0)),
        KeyframeInput(LOGO_START_PCT, jsonArrayOf(0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(FULL_PERCENT_D)),
    )
    addImageLayer(
        "Logo", "logo_0",
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.animatedProp(opKFs),
            position = LottieBuilder.animatedProp(posKFs),
            anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
            scale = LottieBuilder.staticPropArray(g.logoScale, g.logoScale, FULL_PERCENT_D),
        ),
    )
}

/** A line, clipped to its own box, sliding in from beyond it. */
private fun LottieBuilder.addMaskedLine(g: BoxGeometry, line: BoxLine) {
    addShapeLayer(
        line.maskName,
        buildJsonArray {
            add(makeGroup(listOf(makeRect(line.boxW, line.boxH, 0.0), makeFill(listOf(1.0, 1.0, 1.0)))))
        },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(line.boxX, line.boxY, 0.0),
        ),
        td = 1,
    )

    addFont(g.cfg.fontFamily, line.weight)
    val posKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(line.textX + line.slideOffset, line.textY, 0.0)),
        KeyframeInput(line.startPct, jsonArrayOf(line.textX + line.slideOffset, line.textY, 0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(line.textX, line.textY, 0.0)),
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

/** The plate behind a line, which grows to full height as the line arrives. */
private fun LottieBuilder.addLinePlate(g: BoxGeometry, line: BoxLine) {
    val sizeKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(line.boxW, 0.0)),
        KeyframeInput(line.startPct, jsonArrayOf(line.boxW, 0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(line.boxW, line.boxH)),
    )
    val items = mutableListOf(
        makeAnimatedRect(sizeKFs, g.cornerPx * BG_CORNER_FACTOR),
        makeFill(line.plateColor, line.plateAlpha.toDouble()),
    )
    if (g.borderPx > 0) {
        val borderKFs = g.keyframes(
            KeyframeInput(0.0, jsonArrayOf(0.0)),
            KeyframeInput(line.startPct, jsonArrayOf(0.0)),
            KeyframeInput(END_PCT, jsonArrayOf(g.borderPx)),
        )
        items.add(makeAnimatedStroke(g.borderLottie, borderKFs, g.cfg.borderColorAlpha.toDouble()))
    }
    addShapeLayer(
        line.bgName,
        buildJsonArray { add(makeGroup(items)) },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(line.boxX, line.boxY, 0.0),
        ),
    )
}

/** The logo's plate scales up from nothing, unlike the line plates, which only grow in height. */
private fun LottieBuilder.addLogoPlate(g: BoxGeometry) {
    if (!g.hasLogo) return
    val scaleKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, FULL_PERCENT_D)),
        KeyframeInput(LOGO_BG_GROWN_PCT, jsonArrayOf(FULL_PERCENT_D, FULL_PERCENT_D, FULL_PERCENT_D)),
        KeyframeInput(END_PCT, jsonArrayOf(FULL_PERCENT_D, FULL_PERCENT_D, FULL_PERCENT_D)),
    )
    val items = mutableListOf(
        makeRect(g.logoBgW, g.logoBgH, g.cornerPx),
        makeFill(g.accentLottie, g.cfg.accentColorAlpha.toDouble()),
    )
    if (g.borderPx > 0) {
        makeStroke(g.borderLottie, g.borderPx, g.cfg.borderColorAlpha.toDouble())?.let { items.add(it) }
    }
    addShapeLayer(
        "Logo BG",
        buildJsonArray { add(makeGroup(items)) },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(g.logoX, g.baseY, 0.0),
            scale = LottieBuilder.animatedProp(scaleKFs),
        ),
    )
}
