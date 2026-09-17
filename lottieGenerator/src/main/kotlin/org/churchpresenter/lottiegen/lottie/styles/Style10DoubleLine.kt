package org.churchpresenter.lottiegen.lottie.styles

import org.churchpresenter.lottiegen.lottie.TextRun
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
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max

/** Padding either side of the text, and the gap between a rule and a line, in em. */
private const val PAD_X_EM = 0.6
private const val LINE_GAP_EM = 0.4

/** The rules are never shorter than this, in em, and overhang the text by this. */
private const val MIN_RULE_EM = 4.0
private const val RULE_OVERHANG_EM = 0.5

/** The gap between the logo and the block, in em. */
private const val LOGO_MARGIN_EM = 1.0

/** The rules are at least two pixels however thin the border is configured. */
private const val MIN_RULE_PX = 2.0
private const val BORDER_THICKNESS_FACTOR = 0.1

/** A baseline sits below its line's centre; this fraction of the line size puts it right. */
private const val BASELINE_FACTOR = 0.35

/** Each line starts hidden behind the bottom rule, by this fraction of its own size. */
private const val NAME_HIDDEN_FACTOR = 1.2
private const val INFO_HIDDEN_FACTOR = 0.5
private const val DETAIL_HIDDEN_FACTOR = 0.5

/** Timing, as percentages of the animation. */
private const val TOP_RULE_DRAWN_PCT = 40.0
private const val BOTTOM_RULE_START_PCT = 15.0
private const val BOTTOM_RULE_DRAWN_PCT = 45.0
private const val NAME_START_PCT = 25.0
private const val NAME_ARRIVED_PCT = 55.0
private const val INFO_START_PCT = 30.0
private const val INFO_ARRIVED_PCT = 60.0
private const val DETAIL_START_PCT = 40.0
private const val DETAIL_ARRIVED_PCT = 70.0
private const val LOGO_GROWN_PCT = 25.0
private const val END_PCT = 100.0

/** Justify codes Lottie writes for left, right and centred text. */
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1
private const val JUSTIFY_CENTRE = 2

/**
 * Everything Style10DoubleLine's layers are positioned from, computed once.
 *
 * Members are properties rather than constructor parameters on purpose: a constructor taking this
 * many would only trade one detekt finding for another.
 */
private class DoubleLineGeometry(builder: LottieBuilder, val cfg: LottieGenConfig) {
    val inF = builder.inFrames
    val holdF = builder.holdFrames
    val outF = builder.outFrames

    val baseSize = cfg.baseSize.toDouble()
    val nameSizePx = emToPx(cfg.nameSize.toDouble(), baseSize)
    val infoSizePx = emToPx(cfg.infoSize.toDouble(), baseSize)
    val detailSizePx = emToPx(cfg.detailSize.toDouble(), baseSize)
    val nameM = TextMeasurer.measure(
        cfg.nameText, cfg.fontFamily, nameSizePx.toFloat(), cfg.nameWeight, cfg.nameTransform,
    )
    val infoM = TextMeasurer.measure(
        cfg.infoText, cfg.fontFamily, infoSizePx.toFloat(), cfg.infoWeight, cfg.infoTransform,
    )
    val detailM = TextMeasurer.measure(
        cfg.detailText, cfg.fontFamily, detailSizePx.toFloat(), cfg.detailWeight, cfg.detailTransform,
    )

    private val paddingX = emToPx(PAD_X_EM, baseSize)
    private val lineGap = emToPx(LINE_GAP_EM, baseSize)
    val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
    private val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
    val linePx = max(MIN_RULE_PX, cfg.borderThickness * baseSize * BORDER_THICKNESS_FACTOR)

    val accentLottie = hexToLottie(cfg.accentColor)
    val nameCLottie = hexToLottie(cfg.nameColor)
    val infoCLottie = hexToLottie(cfg.infoColor)
    val detailCLottie = hexToLottie(cfg.detailColor)

    val isRight = cfg.align == "right"
    val isCenter = cfg.align == "center"
    val canvasW = cfg.canvasW.toDouble()
    private val canvasH = cfg.canvasH.toDouble()

    val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
    val logoMargin = emToPx(LOGO_MARGIN_EM, baseSize)
    val hasLogo = cfg.logoEnabled && cfg.logoData != null
    private val logoSpace = if (hasLogo) logoSizePx + logoMargin else 0.0

    private val nameContentW = if (cfg.hideName) 0.0 else nameM.width + paddingX * 2
    private val infoContentW = if (cfg.hideInfo) 0.0 else infoM.width + paddingX * 2
    private val detailContentW = if (cfg.hideDetail) 0.0 else detailM.width + paddingX * 2
    private val textW = maxOf(nameContentW, infoContentW, detailContentW, emToPx(MIN_RULE_EM, baseSize))
    val lineW = textW + emToPx(RULE_OVERHANG_EM, baseSize)

    private val nameBlockH = if (cfg.hideName) 0.0 else nameSizePx
    private val infoBlockH = if (cfg.hideInfo) 0.0 else infoSizePx
    private val detailBlockH = if (cfg.hideDetail) 0.0 else detailSizePx
    private val gapName = if (cfg.hideName) 0.0 else lineGap
    private val gapInfo = if (cfg.hideInfo) 0.0 else lineGap
    private val gapDetail = if (!cfg.hideInfo && !cfg.hideDetail) lineGap else 0.0

    /** Zero when Detail is hidden, so a preset with it off renders byte-identically to before. */
    private val detailExtra = if (!cfg.hideDetail) gapDetail + detailBlockH else 0.0
    private val totalH = linePx + gapName + nameBlockH + gapName + linePx + gapInfo + infoBlockH + detailExtra
    private val blockTopY = canvasH - marginVPx - totalH

    val topLineCY = blockTopY + linePx / 2
    private val nameCY = blockTopY + linePx + gapName + nameBlockH / 2
    val bottomLineCY = blockTopY + linePx + gapName + nameBlockH + gapName + linePx / 2
    private val infoCY = bottomLineCY + linePx / 2 + gapInfo + infoBlockH / 2
    private val detailCY = infoCY + infoBlockH / 2 + gapDetail + detailBlockH / 2

    /** Both rules, both masks and both lines share one x and one anchor. */
    val blockX = when {
        isCenter -> canvasW / 2
        isRight -> canvasW - marginHPx - logoSpace
        else -> marginHPx + logoSpace
    }
    val anchorX = when {
        isCenter -> 0.0
        isRight -> lineW / 2
        else -> -lineW / 2
    }
    val justify = when {
        isCenter -> JUSTIFY_CENTRE
        isRight -> JUSTIFY_RIGHT
        else -> JUSTIFY_LEFT
    }

    val nameMaskCY = nameCY
    val infoMaskCY = infoCY
    val detailMaskCY = detailCY
    val nameMaskH = nameBlockH + gapName * 2
    val infoMaskH = infoBlockH + gapInfo * 2
    val detailMaskH = detailBlockH + gapDetail * 2
    val nameTextY = nameCY + nameSizePx * BASELINE_FACTOR
    val infoTextY = infoCY + infoSizePx * BASELINE_FACTOR
    val detailTextY = detailCY + detailSizePx * BASELINE_FACTOR

    fun keyframes(vararg points: KeyframeInput) =
        buildKeyframes(points.toList(), inF, holdF, outF, Easing.DEFAULT)
}

private enum class DoubleLineKind { NAME, INFO, DETAIL }

/**
 * One of the (up to) three lines. All start hidden behind the bottom rule -- the name above it,
 * info and detail below -- and slide out to their place.
 */
private class DoubleLine(g: DoubleLineGeometry, kind: DoubleLineKind) {
    val maskName = when (kind) {
        DoubleLineKind.NAME -> "Name Mask"
        DoubleLineKind.INFO -> "Info Mask"
        DoubleLineKind.DETAIL -> "Detail Mask"
    }
    val layerName = when (kind) {
        DoubleLineKind.NAME -> "Name"
        DoubleLineKind.INFO -> "Info"
        DoubleLineKind.DETAIL -> "Detail"
    }
    val sizePx = when (kind) {
        DoubleLineKind.NAME -> g.nameSizePx
        DoubleLineKind.INFO -> g.infoSizePx
        DoubleLineKind.DETAIL -> g.detailSizePx
    }
    val maskH = when (kind) {
        DoubleLineKind.NAME -> g.nameMaskH
        DoubleLineKind.INFO -> g.infoMaskH
        DoubleLineKind.DETAIL -> g.detailMaskH
    }
    val maskCY = when (kind) {
        DoubleLineKind.NAME -> g.nameMaskCY
        DoubleLineKind.INFO -> g.infoMaskCY
        DoubleLineKind.DETAIL -> g.detailMaskCY
    }
    val y = when (kind) {
        DoubleLineKind.NAME -> g.nameTextY
        DoubleLineKind.INFO -> g.infoTextY
        DoubleLineKind.DETAIL -> g.detailTextY
    }
    val hiddenY = when (kind) {
        DoubleLineKind.NAME -> g.bottomLineCY + sizePx * NAME_HIDDEN_FACTOR
        DoubleLineKind.INFO -> g.bottomLineCY - sizePx * INFO_HIDDEN_FACTOR
        DoubleLineKind.DETAIL -> g.bottomLineCY - sizePx * DETAIL_HIDDEN_FACTOR
    }
    val startPct = when (kind) {
        DoubleLineKind.NAME -> NAME_START_PCT
        DoubleLineKind.INFO -> INFO_START_PCT
        DoubleLineKind.DETAIL -> DETAIL_START_PCT
    }
    val arrivedPct = when (kind) {
        DoubleLineKind.NAME -> NAME_ARRIVED_PCT
        DoubleLineKind.INFO -> INFO_ARRIVED_PCT
        DoubleLineKind.DETAIL -> DETAIL_ARRIVED_PCT
    }
    val text = when (kind) {
        DoubleLineKind.NAME -> g.cfg.nameText
        DoubleLineKind.INFO -> g.cfg.infoText
        DoubleLineKind.DETAIL -> g.cfg.detailText
    }
    val weight = when (kind) {
        DoubleLineKind.NAME -> g.cfg.nameWeight
        DoubleLineKind.INFO -> g.cfg.infoWeight
        DoubleLineKind.DETAIL -> g.cfg.detailWeight
    }
    val color = when (kind) {
        DoubleLineKind.NAME -> g.nameCLottie
        DoubleLineKind.INFO -> g.infoCLottie
        DoubleLineKind.DETAIL -> g.detailCLottie
    }
    val transform = when (kind) {
        DoubleLineKind.NAME -> g.cfg.nameTransform
        DoubleLineKind.INFO -> g.cfg.infoTransform
        DoubleLineKind.DETAIL -> g.cfg.detailTransform
    }
    val alpha = when (kind) {
        DoubleLineKind.NAME -> g.cfg.nameColorAlpha
        DoubleLineKind.INFO -> g.cfg.infoColorAlpha
        DoubleLineKind.DETAIL -> g.cfg.detailColorAlpha
    }
}

class Style10DoubleLine : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val g = DoubleLineGeometry(builder, cfg)
        // Added first renders on top.
        if (!cfg.hideName) builder.addSlidingLine(g, DoubleLine(g, DoubleLineKind.NAME))
        if (!cfg.hideInfo) builder.addSlidingLine(g, DoubleLine(g, DoubleLineKind.INFO))
        if (!cfg.hideDetail) builder.addSlidingLine(g, DoubleLine(g, DoubleLineKind.DETAIL))
        builder.addRule(g, "Top Line", g.topLineCY, g.topRuleKeyframes())
        builder.addRule(g, "Bottom Line", g.bottomLineCY, g.bottomRuleKeyframes())
        builder.addLogo(g)
    }
}

/** The top rule is drawn straight away; the bottom one waits, then catches up. */
private fun DoubleLineGeometry.topRuleKeyframes() = keyframes(
    KeyframeInput(0.0, jsonArrayOf(0.0, linePx)),
    KeyframeInput(TOP_RULE_DRAWN_PCT, jsonArrayOf(lineW, linePx)),
    KeyframeInput(END_PCT, jsonArrayOf(lineW, linePx)),
)

private fun DoubleLineGeometry.bottomRuleKeyframes() = keyframes(
    KeyframeInput(0.0, jsonArrayOf(0.0, linePx)),
    KeyframeInput(BOTTOM_RULE_START_PCT, jsonArrayOf(0.0, linePx)),
    KeyframeInput(BOTTOM_RULE_DRAWN_PCT, jsonArrayOf(lineW, linePx)),
    KeyframeInput(END_PCT, jsonArrayOf(lineW, linePx)),
)

/** A line, clipped to its own band, sliding out from behind the bottom rule. */
private fun LottieBuilder.addSlidingLine(g: DoubleLineGeometry, line: DoubleLine) {
    addShapeLayer(
        line.maskName,
        buildJsonArray {
            add(makeGroup(listOf(makeRect(g.lineW, line.maskH, 0.0), makeFill(listOf(1.0, 1.0, 1.0)))))
        },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(g.blockX, line.maskCY, 0.0),
            anchor = LottieBuilder.staticPropArray(g.anchorX, 0.0, 0.0),
        ),
        td = 1,
    )

    addFont(g.cfg.fontFamily, line.weight)
    val posKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(g.blockX, line.hiddenY, 0.0)),
        KeyframeInput(line.startPct, jsonArrayOf(g.blockX, line.hiddenY, 0.0)),
        KeyframeInput(line.arrivedPct, jsonArrayOf(g.blockX, line.y, 0.0)),
        KeyframeInput(END_PCT, jsonArrayOf(g.blockX, line.y, 0.0)),
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

/** One of the two rules, drawn out from the block's edge. */
private fun LottieBuilder.addRule(
    g: DoubleLineGeometry,
    name: String,
    cy: Double,
    sizeKFs: kotlinx.serialization.json.JsonArray,
) {
    addShapeLayer(
        name,
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
            position = LottieBuilder.staticPropArray(g.blockX, cy, 0.0),
            anchor = LottieBuilder.staticPropArray(g.anchorX, 0.0, 0.0),
        ),
    )
}

/** The logo scales up beside the block, centred between the two rules. */
private fun LottieBuilder.addLogo(g: DoubleLineGeometry) {
    val cfg = g.cfg
    if (!g.hasLogo || cfg.logoData == null) return
    val scale = (g.logoSizePx / cfg.logoH.toDouble()) * PERCENT_SCALE
    val cx = when {
        g.isCenter -> {
            val maxW = maxOf(
                if (cfg.hideName) 0.0 else g.nameM.width.toDouble(),
                if (cfg.hideInfo) 0.0 else g.infoM.width.toDouble(),
                if (cfg.hideDetail) 0.0 else g.detailM.width.toDouble(),
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
            position = LottieBuilder.staticPropArray(cx, (g.topLineCY + g.bottomLineCY) / 2, 0.0),
            anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
            scale = LottieBuilder.animatedProp(scaleKFs),
        ),
    )
}
