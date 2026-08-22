package org.churchpresenter.lottiegen.lottie.styles

import org.churchpresenter.lottiegen.lottie.TextRun
import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.FULL_PERCENT_D
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
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

/** A stroke straddles its path, so the visible edge sits a quarter of its width off centre. */
private const val BORDER_CENTRE_FACTOR = 0.25

/** Padding inside the name box, in em. */
private const val PAD_X_EM = 1.0
private const val PAD_Y_EM = 0.5

/** The accent block's width, as a fraction of the name box's height. */
private const val ACCENT_W_FACTOR = 0.55

/** The accent block grows past the name box by this much of the border, top and bottom. */
private const val ACCENT_BORDER_FACTOR = 1.5

/** The info bar is shorter than the name box: this much padding rather than a full two. */
private const val INFO_PAD_MULTIPLE = 1.5

/** The rule above the banner is at least two pixels, and a tenth of the base size otherwise. */
private const val MIN_RULE_PX = 2.0
private const val RULE_H_FACTOR = 0.1
private const val BORDER_THICKNESS_FACTOR = 0.1

/** One pixel of clearance between the rule and the name box. */
private const val RULE_CLEARANCE_PX = 1

/** A baseline sits below its box's centre; this fraction of the line size puts it right. */
private const val BASELINE_FACTOR = 0.35

/** Timing, as percentages of the animation. */
private const val NAME_START_PCT = 15.0
private const val NAME_ARRIVED_PCT = 55.0
private const val INFO_START_PCT = 40.0
private const val INFO_ARRIVED_PCT = 80.0
private const val RULE_START_PCT = 30.0
private const val RULE_DRAWN_PCT = 70.0
private const val ACCENT_GROWN_PCT = 20.0
private const val END_PCT = 100.0

/** Justify codes Lottie writes for left and right text. */
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1

/**
 * Everything Style4Banner's layers are positioned from, computed once.
 *
 * Members are properties rather than constructor parameters on purpose: a constructor taking this
 * many would only trade one detekt finding for another.
 */
private class BannerGeometry(builder: LottieBuilder, val cfg: LottieGenConfig) {
    val inF = builder.inFrames
    val holdF = builder.holdFrames
    val outF = builder.outFrames

    val baseSize = cfg.baseSize.toDouble()
    val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
    val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
    private val nameM = TextMeasurer.measure(
        cfg.nameText, cfg.fontFamily, nameSizePx.toFloat(), cfg.nameWeight, cfg.nameTransform,
    )

    private val paddingX = emToPx(PAD_X_EM, baseSize)
    private val paddingY = emToPx(PAD_Y_EM, baseSize)
    private val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
    private val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
    private val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
    val borderPx = cfg.borderThickness * baseSize * BORDER_THICKNESS_FACTOR

    val accentLottie = hexToLottie(cfg.accentColor)
    val bgLottie = hexToLottie(cfg.bgColor)
    val borderLottie = hexToLottie(cfg.borderColor)
    val nameCLottie = hexToLottie(cfg.nameColor)
    val infoCLottie = hexToLottie(cfg.infoColor)

    private val canvasW = cfg.canvasW.toDouble()
    private val canvasH = cfg.canvasH.toDouble()
    val isRight = cfg.align == "right"

    val nameBoxW = nameM.width + paddingX * 2
    val nameBoxH = nameSizePx + paddingY * 2
    private val accentW = nameBoxH * ACCENT_W_FACTOR
    val topLineH = max(MIN_RULE_PX, baseSize * RULE_H_FACTOR)
    val accentH = nameBoxH + (if (borderPx > 0) borderPx * ACCENT_BORDER_FACTOR else 0.0)
    val accentWFinal = accentW + (if (borderPx > 0) borderPx else 0.0)
    val topLineW = nameBoxW + accentW
    val infoBarW = topLineW
    val infoBarH = infoSizePx + paddingY * INFO_PAD_MULTIPLE

    private val baseY = canvasH - marginVPx - (nameBoxH + lineSpacingPx + infoBarH) / 2
    val nameBgCY = baseY - lineSpacingPx / 2 - infoBarH / 2
    val infoBarCY = baseY + nameBoxH / 2 + lineSpacingPx / 2
    val topLineY = nameBgCY - nameBoxH / 2 - topLineH / 2 - RULE_CLEARANCE_PX

    /** The banner hangs off one margin; everything is measured from that edge. */
    private val edgeX = if (isRight) canvasW - marginHPx else marginHPx
    val nameEdgeX = if (isRight) edgeX - nameBoxW else edgeX + nameBoxW
    val nameAnchorX = if (isRight) -nameBoxW / 2 else nameBoxW / 2
    val accentCX = if (isRight) nameEdgeX - accentW / 2 else nameEdgeX + accentW / 2
    val topLineEdgeX = edgeX
    val topLineAnchorX = if (isRight) topLineW / 2 else -topLineW / 2
    val infoEdgeX = edgeX
    val infoAnchorX = if (isRight) infoBarW / 2 else -infoBarW / 2

    val justify = if (isRight) JUSTIFY_RIGHT else JUSTIFY_LEFT
    val textX = if (isRight) edgeX - paddingX else edgeX + paddingX
    val nameTextY = nameBgCY + nameSizePx * BASELINE_FACTOR
    val infoTextY = infoBarCY + infoSizePx * BASELINE_FACTOR

    /** The name slides in from one side and the info bar from the other. */
    val dir = if (isRight) -1.0 else 1.0

    fun keyframes(vararg points: KeyframeInput) =
        buildKeyframes(points.toList(), inF, holdF, outF, Easing.DEFAULT)
}

/**
 * One of the two banded lines. They differ in their box, their timing, which way they slide and
 * what colour their plate takes.
 */
private class BannerLine(g: BannerGeometry, isName: Boolean) {
    val maskName = if (isName) "Name Mask" else "Info Mask"
    val layerName = if (isName) "Name" else "Info"
    val bgName = if (isName) "Name BG" else "Info Bar"
    val boxW = if (isName) g.nameBoxW else g.infoBarW
    val boxH = if (isName) g.nameBoxH else g.infoBarH
    val edgeX = if (isName) g.nameEdgeX else g.infoEdgeX
    val anchorX = if (isName) g.nameAnchorX else g.infoAnchorX
    val cy = if (isName) g.nameBgCY else g.infoBarCY
    val sizePx = if (isName) g.nameSizePx else g.infoSizePx
    val textY = if (isName) g.nameTextY else g.infoTextY
    val slideOffset = if (isName) boxW * g.dir else boxW * -g.dir
    val startPct = if (isName) NAME_START_PCT else INFO_START_PCT
    val arrivedPct = if (isName) NAME_ARRIVED_PCT else INFO_ARRIVED_PCT
    val text = if (isName) g.cfg.nameText else g.cfg.infoText
    val weight = if (isName) g.cfg.nameWeight else g.cfg.infoWeight
    val color = if (isName) g.nameCLottie else g.infoCLottie
    val transform = if (isName) g.cfg.nameTransform else g.cfg.infoTransform
    val alpha = if (isName) g.cfg.nameColorAlpha else g.cfg.infoColorAlpha

    /** The name's plate takes the background colour; the info bar takes the accent. */
    val plateColor = if (isName) g.bgLottie else g.accentLottie
    val plateAlpha = if (isName) g.cfg.bgColorAlpha else g.cfg.accentColorAlpha
}

class Style4Banner : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val g = BannerGeometry(builder, cfg)
        val name = BannerLine(g, isName = true)
        val info = BannerLine(g, isName = false)
        // Added first renders on top.
        if (!cfg.hideName) builder.addMaskedLine(g, name)
        if (!cfg.hideInfo) builder.addMaskedLine(g, info)
        if (g.borderPx > 0) builder.addTopRule(g)
        if (cfg.bgEnabled) builder.addAccentBlock(g)
        if (!cfg.hideName && cfg.bgEnabled) builder.addLinePlate(g, name)
        if (!cfg.hideInfo && cfg.bgEnabled) builder.addLinePlate(g, info)
    }
}

/** A line, revealed by a mask that opens at the same rate as its plate. */
private fun LottieBuilder.addMaskedLine(g: BannerGeometry, line: BannerLine) {
    val maskKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, line.boxH)),
        KeyframeInput(line.startPct, jsonArrayOf(0.0, line.boxH)),
        KeyframeInput(line.arrivedPct, jsonArrayOf(line.boxW, line.boxH)),
    )
    addShapeLayer(
        line.maskName,
        buildJsonArray {
            add(makeGroup(listOf(makeAnimatedRect(maskKFs, 0.0), makeFill(listOf(1.0, 1.0, 1.0)))))
        },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(line.edgeX, line.cy, 0.0),
            anchor = LottieBuilder.staticPropArray(line.anchorX, 0.0, 0.0),
        ),
        td = 1,
    )

    addFont(g.cfg.fontFamily, line.weight)
    val posKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(g.textX + line.slideOffset, line.textY, 0.0)),
        KeyframeInput(line.startPct, jsonArrayOf(g.textX + line.slideOffset, line.textY, 0.0)),
        KeyframeInput(line.arrivedPct, jsonArrayOf(g.textX, line.textY, 0.0)),
    )
    addTextLayer(
        line.layerName,
        makeTextData(
            TextRun(
                line.text,
                g.cfg.fontFamily,
                line.sizePx,
                line.weight,
                line.color,
                line.transform,
                g.justify,
            ),
        ),
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.staticProp(line.alpha),
            position = LottieBuilder.animatedProp(posKFs),
        ),
        tt = 1,
    )
}

/** The rule above the banner. */
private fun LottieBuilder.addTopRule(g: BannerGeometry) {
    val sizeKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, g.topLineH)),
        KeyframeInput(RULE_START_PCT, jsonArrayOf(0.0, g.topLineH)),
        KeyframeInput(RULE_DRAWN_PCT, jsonArrayOf(g.topLineW, g.topLineH)),
    )
    addShapeLayer(
        "Top Line",
        buildJsonArray {
            add(
                makeGroup(
                    listOf(
                        makeAnimatedRect(sizeKFs, 0.0),
                        makeFill(g.accentLottie, g.cfg.accentColorAlpha.toDouble()),
                    ),
                ),
            )
        },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(g.topLineEdgeX, g.topLineY, 0.0),
            anchor = LottieBuilder.staticPropArray(g.topLineAnchorX, 0.0, 0.0),
        ),
    )
}

/** The solid block at the banner's inner end, which pops up before anything else moves. */
private fun LottieBuilder.addAccentBlock(g: BannerGeometry) {
    val scaleKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, FULL_PERCENT_D)),
        KeyframeInput(ACCENT_GROWN_PCT, jsonArrayOf(FULL_PERCENT_D, FULL_PERCENT_D, FULL_PERCENT_D)),
        KeyframeInput(END_PCT, jsonArrayOf(FULL_PERCENT_D, FULL_PERCENT_D, FULL_PERCENT_D)),
    )
    addShapeLayer(
        "Accent Block",
        buildJsonArray {
            add(
                makeGroup(
                    listOf(
                        makeRect(g.accentWFinal, g.accentH, 0.0),
                        makeFill(g.accentLottie, g.cfg.accentColorAlpha.toDouble()),
                    ),
                ),
            )
        },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(
                g.accentCX,
                g.nameBgCY + (if (g.borderPx > 0) g.borderPx * BORDER_CENTRE_FACTOR else 0.0),
                0.0,
            ),
            scale = LottieBuilder.animatedProp(scaleKFs),
        ),
    )
}

/** The plate behind a line, opening at the same rate as that line's mask. */
private fun LottieBuilder.addLinePlate(g: BannerGeometry, line: BannerLine) {
    val sizeKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, line.boxH)),
        KeyframeInput(line.startPct, jsonArrayOf(0.0, line.boxH)),
        KeyframeInput(line.arrivedPct, jsonArrayOf(line.boxW, line.boxH)),
    )
    val items = mutableListOf(
        makeAnimatedRect(sizeKFs, 0.0),
        makeFill(line.plateColor, line.plateAlpha.toDouble()),
    )
    if (g.borderPx > 0) {
        val borderKFs = g.keyframes(
            KeyframeInput(0.0, jsonArrayOf(0.0)),
            KeyframeInput(line.startPct, jsonArrayOf(0.0)),
            KeyframeInput(line.arrivedPct, jsonArrayOf(g.borderPx)),
        )
        items.add(makeAnimatedStroke(g.borderLottie, borderKFs, g.cfg.borderColorAlpha.toDouble()))
    }
    addShapeLayer(
        line.bgName,
        buildJsonArray { add(makeGroup(items)) },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(line.edgeX, line.cy, 0.0),
            anchor = LottieBuilder.staticPropArray(line.anchorX, 0.0, 0.0),
        ),
    )
}
