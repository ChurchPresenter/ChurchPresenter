package org.churchpresenter.lottiegen.lottie.styles

import org.churchpresenter.lottiegen.lottie.TextRun
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
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
import org.churchpresenter.lottiegen.lottie.makeGradientFill
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeTextData
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig

/** Lottie writes a gradient's stop count in `p`; this bar is built from four. */
private const val GRADIENT_STOP_COUNT = 4

/** Padding inside each bar, in em, and how far past the text the gradient fade runs. */
private const val PAD_X_EM = 1.0
private const val PAD_Y_EM = 0.5
private const val GRADIENT_EXTRA_EM = 3.0

/** The info bar is shorter than the name bar: this much padding rather than a full two. */
private const val INFO_PAD_MULTIPLE = 1.5

/** The logo's plate and the gap beside it, in em. */
private const val LOGO_PLATE_PAD_EM = 0.8
private const val LOGO_MARGIN_EM = 0.5

/** Border thickness is configured 0..n and scaled onto the base size by this. */
private const val BORDER_THICKNESS_FACTOR = 0.1

/** A baseline sits below its bar's centre; this fraction of the line size puts it right. */
private const val BASELINE_FACTOR = 0.35

/** Timing, as percentages of the animation. */
private const val NAME_BAR_START_PCT = 10.0
private const val NAME_BAR_ARRIVED_PCT = 50.0
private const val NAME_START_PCT = 15.0
private const val NAME_ARRIVED_PCT = 55.0
private const val INFO_BAR_START_PCT = 35.0
private const val INFO_BAR_ARRIVED_PCT = 75.0
private const val INFO_START_PCT = 40.0
private const val INFO_ARRIVED_PCT = 80.0
private const val DETAIL_BAR_START_PCT = 55.0
private const val DETAIL_BAR_ARRIVED_PCT = 95.0
private const val DETAIL_START_PCT = 55.0
private const val DETAIL_ARRIVED_PCT = 95.0
private const val LOGO_GROWN_PCT = 30.0
private const val LOGO_PLATE_GROWN_PCT = 25.0
private const val END_PCT = 100.0

/** Justify codes Lottie writes for left, right and centred text. */
private const val JUSTIFY_LEFT = 0
private const val JUSTIFY_RIGHT = 1
private const val JUSTIFY_CENTRE = 2

/**
 * Everything Style5GradientBar's layers are positioned from, computed once.
 *
 * Members are properties rather than constructor parameters on purpose: a constructor taking this
 * many would only trade one detekt finding for another.
 */
private class GradientGeometry(builder: LottieBuilder, val cfg: LottieGenConfig) {
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

    val paddingX = emToPx(PAD_X_EM, baseSize)
    private val paddingY = emToPx(PAD_Y_EM, baseSize)
    private val lineSpacingPx = emToPx(cfg.lineSpacing.toDouble(), baseSize)
    val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
    private val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
    val cornerPx = emToPx(cfg.corners.toDouble(), baseSize)
    val borderPx = cfg.borderThickness * baseSize * BORDER_THICKNESS_FACTOR

    val bgLottie = hexToLottie(cfg.bgColor)
    val accentLottie = hexToLottie(cfg.accentColor)
    val borderLottie = hexToLottie(cfg.borderColor)
    val nameCLottie = hexToLottie(cfg.nameColor)
    val infoCLottie = hexToLottie(cfg.infoColor)
    val detailCLottie = hexToLottie(cfg.detailColor)

    val canvasW = cfg.canvasW.toDouble()
    private val canvasH = cfg.canvasH.toDouble()
    val isRight = cfg.align == "right"
    val isCenter = cfg.align == "center"

    val gradientExtra = emToPx(GRADIENT_EXTRA_EM, baseSize)
    val nameBarW = nameM.width + paddingX * 2 + gradientExtra
    val nameBarH = nameSizePx + paddingY * 2
    val infoBarW = infoM.width + paddingX * 2 + gradientExtra
    val infoBarH = infoSizePx + paddingY * INFO_PAD_MULTIPLE
    val detailBarW = detailM.width + paddingX * 2 + gradientExtra
    val detailBarH = detailSizePx + paddingY * INFO_PAD_MULTIPLE

    private val nameVisible = !cfg.hideName
    private val infoVisible = !cfg.hideInfo
    private val detailVisible = !cfg.hideDetail
    private val nameBlockH = if (nameVisible) nameBarH else 0.0
    private val infoBlockH = if (infoVisible) infoBarH else 0.0
    private val detailBlockH = if (detailVisible) detailBarH else 0.0
    private val visibleCount = listOf(nameVisible, infoVisible, detailVisible).count { it }
    private val totalBlockH = nameBlockH + infoBlockH + detailBlockH +
        lineSpacingPx * (visibleCount - 1).coerceAtLeast(0)
    private val baseY = canvasH - marginVPx - totalBlockH / 2
    private val stackTop = baseY - totalBlockH / 2

    // Each bar's own centre, walked top-to-bottom over only the visible ones — a hidden line
    // collapses out of the stack (this style's existing behavior) rather than reserving space.
    // A hidden line still needs a value: addLogo() averages name and info regardless of visibility.
    val nameBgCY: Double
    val infoBarCY: Double
    val detailBarCY: Double

    init {
        var cursor = stackTop
        nameBgCY = if (nameVisible) {
            val cy = cursor + nameBarH / 2
            cursor += nameBarH + lineSpacingPx
            cy
        } else {
            baseY
        }
        infoBarCY = if (infoVisible) {
            val cy = cursor + infoBarH / 2
            cursor += infoBarH + lineSpacingPx
            cy
        } else {
            baseY
        }
        detailBarCY = if (detailVisible) cursor + detailBarH / 2 else baseY
    }

    /**
     * Where the logo centres vertically. The name/info average is the original formula,
     * unconditional on their own visibility (matched exactly here, `baseY`-per-line fallback and
     * all); Detail only joins it once shown, so this is byte-identical to before while it stays
     * hidden.
     */
    val logoCenterY: Double =
        if (cfg.hideDetail) (nameBgCY + infoBarCY) / 2 else (nameBgCY + infoBarCY + detailBarCY) / 3

    val logoSizePx = emToPx(cfg.logoSize.toDouble(), baseSize)
    val logoMargin = emToPx(LOGO_MARGIN_EM, baseSize)
    val hasLogo = cfg.logoEnabled && cfg.logoData != null
    val logoBgSize = logoSizePx + emToPx(LOGO_PLATE_PAD_EM, baseSize)
    val logoSpace = if (hasLogo) logoBgSize + logoMargin else 0.0

    /**
     * Written per branch, in the original term order: factoring the shared prefix out would
     * re-associate the doubles and move the generated coordinates.
     */
    val nameBarCX = when {
        isCenter -> canvasW / 2 + logoSpace / 2
        isRight -> canvasW - marginHPx - logoSpace - nameBarW / 2
        else -> marginHPx + logoSpace + nameBarW / 2
    }
    val infoBarCX = when {
        isCenter -> canvasW / 2 + logoSpace / 2
        isRight -> canvasW - marginHPx - logoSpace - infoBarW / 2
        else -> marginHPx + logoSpace + infoBarW / 2
    }
    val detailBarCX = when {
        isCenter -> canvasW / 2 + logoSpace / 2
        isRight -> canvasW - marginHPx - logoSpace - detailBarW / 2
        else -> marginHPx + logoSpace + detailBarW / 2
    }
    val textX = when {
        isCenter -> canvasW / 2 + logoSpace / 2
        isRight -> canvasW - marginHPx - logoSpace - paddingX
        else -> marginHPx + logoSpace + paddingX
    }
    /** The mask hangs off the block's edge, so it shares the text's edge but not its padding. */
    val maskEdgeX = when {
        isCenter -> canvasW / 2 + logoSpace / 2
        isRight -> canvasW - marginHPx - logoSpace
        else -> marginHPx + logoSpace
    }

    val justify = when {
        isCenter -> JUSTIFY_CENTRE
        isRight -> JUSTIFY_RIGHT
        else -> JUSTIFY_LEFT
    }
    val nameTextY = nameBgCY + nameSizePx * BASELINE_FACTOR
    val infoTextY = infoBarCY + infoSizePx * BASELINE_FACTOR
    val detailTextY = detailBarCY + detailSizePx * BASELINE_FACTOR

    /** Everything that slides comes in from a full canvas width away. */
    val slideDistance = canvasW

    fun keyframes(vararg points: KeyframeInput) =
        buildKeyframes(points.toList(), inF, holdF, outF, Easing.DEFAULT)

    /** The gradient a bar is filled with, which fades on one edge or both. */
    fun barGradient(color: List<Double>, barW: Double): JsonObject = when {
        isCenter -> centreGradient(color, barW)
        isRight -> makeGradientFill(
            color, FULL_PERCENT_D, listOf(-barW / 2 + gradientExtra, 0.0), listOf(-barW / 2, 0.0),
        )
        else -> makeGradientFill(
            color, FULL_PERCENT_D, listOf(barW / 2 - gradientExtra, 0.0), listOf(barW / 2, 0.0),
        )
    }

    /** Four stops, so a centred bar fades out at both ends rather than only one. */
    private fun centreGradient(color: List<Double>, barW: Double): JsonObject {
        val r = color[0]
        val g = color[1]
        val b = color[2]
        val fadeRatio = gradientExtra / 2 / barW
        return buildJsonObject {
            put("ty", JsonPrimitive("gf"))
            put("o", buildJsonObject { put("a", JsonPrimitive(0)); put("k", JsonPrimitive(FULL_PERCENT_D)) })
            put("r", JsonPrimitive(1))
            put("bm", JsonPrimitive(0))
            put("t", JsonPrimitive(1))
            put("s", buildJsonObject { put("a", JsonPrimitive(0)); put("k", jsonArrayOf(-barW / 2, 0.0)) })
            put("e", buildJsonObject { put("a", JsonPrimitive(0)); put("k", jsonArrayOf(barW / 2, 0.0)) })
            put("g", buildJsonObject {
                put("p", JsonPrimitive(GRADIENT_STOP_COUNT))
                put("k", buildJsonObject {
                    put("a", JsonPrimitive(0))
                    put("k", buildJsonArray {
                        // Colour stops: the same colour throughout; only the opacity moves.
                        add(JsonPrimitive(0.0)); add(JsonPrimitive(r)); add(JsonPrimitive(g)); add(JsonPrimitive(b))
                        add(JsonPrimitive(fadeRatio))
                        add(JsonPrimitive(r)); add(JsonPrimitive(g)); add(JsonPrimitive(b))
                        add(JsonPrimitive(1 - fadeRatio))
                        add(JsonPrimitive(r)); add(JsonPrimitive(g)); add(JsonPrimitive(b))
                        add(JsonPrimitive(1.0)); add(JsonPrimitive(r)); add(JsonPrimitive(g)); add(JsonPrimitive(b))
                        // Opacity stops.
                        add(JsonPrimitive(0.0)); add(JsonPrimitive(0.0))
                        add(JsonPrimitive(fadeRatio)); add(JsonPrimitive(1.0))
                        add(JsonPrimitive(1 - fadeRatio)); add(JsonPrimitive(1.0))
                        add(JsonPrimitive(1.0)); add(JsonPrimitive(0.0))
                    })
                })
            })
        }
    }
}

private enum class GradientLineKind { NAME, INFO, DETAIL }

/** One of the (up to) three bars, and the line on it. */
private class GradientLine(g: GradientGeometry, kind: GradientLineKind) {
    val maskName = when (kind) {
        GradientLineKind.NAME -> "Name Mask"
        GradientLineKind.INFO -> "Info Mask"
        GradientLineKind.DETAIL -> "Detail Mask"
    }
    val layerName = when (kind) {
        GradientLineKind.NAME -> "Name"
        GradientLineKind.INFO -> "Info"
        GradientLineKind.DETAIL -> "Detail"
    }
    val barName = when (kind) {
        GradientLineKind.NAME -> "Name Gradient Bar"
        GradientLineKind.INFO -> "Info Gradient Bar"
        GradientLineKind.DETAIL -> "Detail Gradient Bar"
    }
    val maskW = when (kind) {
        GradientLineKind.NAME -> g.nameM.width + g.paddingX * 2
        GradientLineKind.INFO -> g.infoM.width + g.paddingX * 2
        GradientLineKind.DETAIL -> g.detailM.width + g.paddingX * 2
    }
    val barW = when (kind) {
        GradientLineKind.NAME -> g.nameBarW
        GradientLineKind.INFO -> g.infoBarW
        GradientLineKind.DETAIL -> g.detailBarW
    }
    val barH = when (kind) {
        GradientLineKind.NAME -> g.nameBarH
        GradientLineKind.INFO -> g.infoBarH
        GradientLineKind.DETAIL -> g.detailBarH
    }
    val barCX = when (kind) {
        GradientLineKind.NAME -> g.nameBarCX
        GradientLineKind.INFO -> g.infoBarCX
        GradientLineKind.DETAIL -> g.detailBarCX
    }
    val cy = when (kind) {
        GradientLineKind.NAME -> g.nameBgCY
        GradientLineKind.INFO -> g.infoBarCY
        GradientLineKind.DETAIL -> g.detailBarCY
    }
    val sizePx = when (kind) {
        GradientLineKind.NAME -> g.nameSizePx
        GradientLineKind.INFO -> g.infoSizePx
        GradientLineKind.DETAIL -> g.detailSizePx
    }
    val textY = when (kind) {
        GradientLineKind.NAME -> g.nameTextY
        GradientLineKind.INFO -> g.infoTextY
        GradientLineKind.DETAIL -> g.detailTextY
    }
    val startPct = when (kind) {
        GradientLineKind.NAME -> NAME_START_PCT
        GradientLineKind.INFO -> INFO_START_PCT
        GradientLineKind.DETAIL -> DETAIL_START_PCT
    }
    val arrivedPct = when (kind) {
        GradientLineKind.NAME -> NAME_ARRIVED_PCT
        GradientLineKind.INFO -> INFO_ARRIVED_PCT
        GradientLineKind.DETAIL -> DETAIL_ARRIVED_PCT
    }
    val barStartPct = when (kind) {
        GradientLineKind.NAME -> NAME_BAR_START_PCT
        GradientLineKind.INFO -> INFO_BAR_START_PCT
        GradientLineKind.DETAIL -> DETAIL_BAR_START_PCT
    }
    val barArrivedPct = when (kind) {
        GradientLineKind.NAME -> NAME_BAR_ARRIVED_PCT
        GradientLineKind.INFO -> INFO_BAR_ARRIVED_PCT
        GradientLineKind.DETAIL -> DETAIL_BAR_ARRIVED_PCT
    }
    val text = when (kind) {
        GradientLineKind.NAME -> g.cfg.nameText
        GradientLineKind.INFO -> g.cfg.infoText
        GradientLineKind.DETAIL -> g.cfg.detailText
    }
    val weight = when (kind) {
        GradientLineKind.NAME -> g.cfg.nameWeight
        GradientLineKind.INFO -> g.cfg.infoWeight
        GradientLineKind.DETAIL -> g.cfg.detailWeight
    }
    val color = when (kind) {
        GradientLineKind.NAME -> g.nameCLottie
        GradientLineKind.INFO -> g.infoCLottie
        GradientLineKind.DETAIL -> g.detailCLottie
    }
    val transform = when (kind) {
        GradientLineKind.NAME -> g.cfg.nameTransform
        GradientLineKind.INFO -> g.cfg.infoTransform
        GradientLineKind.DETAIL -> g.cfg.detailTransform
    }
    val alpha = when (kind) {
        GradientLineKind.NAME -> g.cfg.nameColorAlpha
        GradientLineKind.INFO -> g.cfg.infoColorAlpha
        GradientLineKind.DETAIL -> g.cfg.detailColorAlpha
    }

    /** The name's bar takes the background colour; info and detail bars take the accent. */
    val barColor = if (kind == GradientLineKind.NAME) g.bgLottie else g.accentLottie
    val barAlpha = if (kind == GradientLineKind.NAME) g.cfg.bgColorAlpha else g.cfg.accentColorAlpha

    /** Centred, the name line slides in from the opposite side to info/detail; otherwise all follow the alignment. */
    val slideOffset = when {
        g.isCenter -> if (kind == GradientLineKind.NAME) -g.slideDistance else g.slideDistance
        g.isRight -> g.slideDistance
        else -> -g.slideDistance
    }
    val maskAnchorX = when {
        g.isCenter -> 0.0
        g.isRight -> maskW / 2
        else -> -maskW / 2
    }
}

class Style5GradientBar : StyleGenerator {
    override fun generate(builder: LottieBuilder, cfg: LottieGenConfig) {
        val g = GradientGeometry(builder, cfg)
        val name = GradientLine(g, GradientLineKind.NAME)
        val info = GradientLine(g, GradientLineKind.INFO)
        val detail = GradientLine(g, GradientLineKind.DETAIL)
        // Added first renders on top.
        if (!cfg.hideName) builder.addMaskedLine(g, name)
        if (!cfg.hideInfo) builder.addMaskedLine(g, info)
        if (!cfg.hideDetail) builder.addMaskedLine(g, detail)
        builder.addLogo(g)
        if (!cfg.hideName && cfg.bgEnabled) builder.addGradientBar(g, name)
        if (!cfg.hideInfo && cfg.bgEnabled) builder.addGradientBar(g, info)
        if (!cfg.hideDetail && cfg.bgEnabled) builder.addGradientBar(g, detail)
    }
}

/** A line, revealed by a mask that opens as the line slides in. */
private fun LottieBuilder.addMaskedLine(g: GradientGeometry, line: GradientLine) {
    val maskKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, line.barH)),
        KeyframeInput(line.startPct, jsonArrayOf(0.0, line.barH)),
        KeyframeInput(line.arrivedPct, jsonArrayOf(line.maskW, line.barH)),
    )
    addShapeLayer(
        line.maskName,
        buildJsonArray {
            add(makeGroup(listOf(makeAnimatedRect(maskKFs, 0.0), makeFill(listOf(1.0, 1.0, 1.0)))))
        },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(g.maskEdgeX, line.cy, 0.0),
            anchor = LottieBuilder.staticPropArray(line.maskAnchorX, 0.0, 0.0),
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

/** The logo and the plate behind it. */
private fun LottieBuilder.addLogo(g: GradientGeometry) {
    val cfg = g.cfg
    if (!g.hasLogo || cfg.logoData == null) return
    val scale = (g.logoSizePx / cfg.logoH) * PERCENT_SCALE
    val cx = when {
        g.isCenter ->
            g.canvasW / 2 + g.logoSpace / 2 - maxOf(g.nameBarW, g.infoBarW, g.detailBarW) / 2 -
                g.logoMargin - g.logoBgSize / 2
        g.isRight -> g.canvasW - g.marginHPx - g.logoBgSize / 2
        else -> g.marginHPx + g.logoBgSize / 2
    }
    val cy = g.logoCenterY

    val scaleKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, FULL_PERCENT_D)),
        KeyframeInput(LOGO_GROWN_PCT, jsonArrayOf(scale, scale, FULL_PERCENT_D)),
        KeyframeInput(END_PCT, jsonArrayOf(scale, scale, FULL_PERCENT_D)),
    )
    addImageAsset("logo", cfg.logoData, cfg.logoW, cfg.logoH)
    addImageLayer(
        "Logo", "logo",
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(cx, cy, 0.0),
            anchor = LottieBuilder.staticPropArray(cfg.logoW / 2.0, cfg.logoH / 2.0, 0.0),
            scale = LottieBuilder.animatedProp(scaleKFs),
        ),
    )

    val plateKFs = g.keyframes(
        KeyframeInput(0.0, jsonArrayOf(0.0, 0.0, FULL_PERCENT_D)),
        KeyframeInput(LOGO_PLATE_GROWN_PCT, jsonArrayOf(FULL_PERCENT_D, FULL_PERCENT_D, FULL_PERCENT_D)),
        KeyframeInput(END_PCT, jsonArrayOf(FULL_PERCENT_D, FULL_PERCENT_D, FULL_PERCENT_D)),
    )
    val items = mutableListOf(
        makeRect(g.logoBgSize, g.logoBgSize, g.cornerPx),
        makeFill(g.accentLottie, cfg.accentColorAlpha.toDouble()),
    )
    if (g.borderPx > 0) {
        makeStroke(g.borderLottie, g.borderPx, cfg.borderColorAlpha.toDouble())?.let { items.add(it) }
    }
    addShapeLayer(
        "Logo BG",
        buildJsonArray { add(makeGroup(items)) },
        LottieBuilder.defaultTransform(
            position = LottieBuilder.staticPropArray(cx, cy, 0.0),
            scale = LottieBuilder.animatedProp(plateKFs),
        ),
    )
}

/**
 * The bar behind a line. Centred, it scales open from its middle; aligned, it slides in -- so the
 * two cases differ in the transform, not in the shape.
 */
private fun LottieBuilder.addGradientBar(g: GradientGeometry, line: GradientLine) {
    val items = mutableListOf(
        makeRect(line.barW, line.barH, g.cornerPx),
        g.barGradient(line.barColor, line.barW),
    )
    if (g.borderPx > 0) {
        makeStroke(g.borderLottie, g.borderPx, g.cfg.borderColorAlpha.toDouble())?.let { items.add(it) }
    }

    val transform = if (g.isCenter) {
        val scaleKFs = g.keyframes(
            KeyframeInput(0.0, jsonArrayOf(0.0, FULL_PERCENT_D, FULL_PERCENT_D)),
            KeyframeInput(line.barStartPct, jsonArrayOf(0.0, FULL_PERCENT_D, FULL_PERCENT_D)),
            KeyframeInput(
                line.barArrivedPct,
                jsonArrayOf(FULL_PERCENT_D, FULL_PERCENT_D, FULL_PERCENT_D),
            ),
        )
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.staticProp(line.barAlpha),
            position = LottieBuilder.staticPropArray(line.barCX, line.cy, 0.0),
            scale = LottieBuilder.animatedProp(scaleKFs),
        )
    } else {
        val slideStart = if (g.isRight) g.slideDistance else -g.slideDistance
        val posKFs = g.keyframes(
            KeyframeInput(0.0, jsonArrayOf(line.barCX + slideStart, line.cy, 0.0)),
            KeyframeInput(line.barStartPct, jsonArrayOf(line.barCX + slideStart, line.cy, 0.0)),
            KeyframeInput(line.barArrivedPct, jsonArrayOf(line.barCX, line.cy, 0.0)),
        )
        LottieBuilder.defaultTransform(
            opacity = LottieBuilder.staticProp(line.barAlpha),
            position = LottieBuilder.animatedProp(posKFs),
        )
    }

    addShapeLayer(line.barName, buildJsonArray { add(makeGroup(items)) }, transform)
}
