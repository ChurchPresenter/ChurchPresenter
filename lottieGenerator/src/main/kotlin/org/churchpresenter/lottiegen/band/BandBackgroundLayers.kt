package org.churchpresenter.lottiegen.band

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import org.churchpresenter.lottiegen.band.BandGeometry.ARCH_BASE_H
import org.churchpresenter.lottiegen.band.BandGeometry.ARCH_H
import org.churchpresenter.lottiegen.band.BandGeometry.ARCH_RING
import org.churchpresenter.lottiegen.band.BandGeometry.ARCH_W
import org.churchpresenter.lottiegen.band.BandGeometry.BLADE_BOTTOM
import org.churchpresenter.lottiegen.band.BandGeometry.BLADE_TOP
import org.churchpresenter.lottiegen.band.BandGeometry.CHEVRON_ECHO
import org.churchpresenter.lottiegen.band.BandGeometry.CHEVRON_ECHO_W
import org.churchpresenter.lottiegen.band.BandGeometry.CHEVRON_POINT
import org.churchpresenter.lottiegen.band.BandGeometry.CHEVRON_W
import org.churchpresenter.lottiegen.band.BandGeometry.CROSS_START
import org.churchpresenter.lottiegen.band.BandGeometry.CROSS_W
import org.churchpresenter.lottiegen.band.BandGeometry.DIVIDER_W
import org.churchpresenter.lottiegen.band.BandGeometry.FOLD_BAND_H
import org.churchpresenter.lottiegen.band.BandGeometry.FOLD_BAND_Y
import org.churchpresenter.lottiegen.band.BandGeometry.FOLD_H
import org.churchpresenter.lottiegen.band.BandGeometry.FOLD_NOTCH
import org.churchpresenter.lottiegen.band.BandGeometry.FOLD_TAIL
import org.churchpresenter.lottiegen.band.BandGeometry.FOLD_W
import org.churchpresenter.lottiegen.band.BandGeometry.SLANT
import org.churchpresenter.lottiegen.band.BandGeometry.SPLIT_BOTTOM
import org.churchpresenter.lottiegen.band.BandGeometry.SPLIT_TOP
import org.churchpresenter.lottiegen.band.BandGeometry.SPOT_W
import org.churchpresenter.lottiegen.band.BandGeometry.THIRD_BAND
import org.churchpresenter.lottiegen.band.BandGeometry.THIRD_LEFT
import org.churchpresenter.lottiegen.band.BandGeometry.THIRD_RIGHT
import org.churchpresenter.lottiegen.band.BandGeometry.TRI_1
import org.churchpresenter.lottiegen.band.BandGeometry.TRI_2
import org.churchpresenter.lottiegen.band.BandGeometry.WAVE_AMPLITUDE
import org.churchpresenter.lottiegen.band.BandGeometry.WAVE_CREST
import org.churchpresenter.lottiegen.band.BandGeometry.WAVE_SWELL
import org.churchpresenter.lottiegen.band.BandGeometry.WAVE_TOP
import org.churchpresenter.lottiegen.band.BandGeometry.WEDGE_TIP
import org.churchpresenter.lottiegen.band.BandGeometry.WEDGE_TIP_H
import org.churchpresenter.lottiegen.band.BandGeometry.WEDGE_W
import org.churchpresenter.lottiegen.lottie.Easing
import org.churchpresenter.lottiegen.lottie.KeyframeInput
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import org.churchpresenter.lottiegen.lottie.buildKeyframes
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.jsonArrayOf
import org.churchpresenter.lottiegen.lottie.makeFill
import org.churchpresenter.lottiegen.lottie.makeGradientFill
import org.churchpresenter.lottiegen.lottie.makeCurvedPath
import org.churchpresenter.lottiegen.lottie.makeEllipse
import org.churchpresenter.lottiegen.lottie.makeGroup
import org.churchpresenter.lottiegen.lottie.makePath
import org.churchpresenter.lottiegen.lottie.makeRect
import org.churchpresenter.lottiegen.lottie.makeStroke
import org.churchpresenter.lottiegen.lottie.makeGradientFillStops

/**
 * The band itself, one layer per piece so any piece can be a picture: the style's decorations in
 * paint order (first is topmost), the border, then the fill. A piece whose colour role is set to
 * a picture becomes a matte of its own shape over a cover-scaled image; the background's picture
 * additionally keeps the background colour above it as a tint. For the wipes every piece is also
 * cut by the `BandMatte`. Every layer is named with the `Band` prefix so a player that wants the
 * text without the backdrop can hide them as a set.
 */
internal fun LottieBuilder.addBandBackground(cfg: BibleLottieGenConfig, slots: BandSlots, timeline: BandTimeline) {
    val band = slots.band
    val motion = BandMotion(cfg, band, timeline)
    if (cfg.entrance == BandEntrance.SWIPE) addSwipeBar(cfg, band, motion)
    val wipeMatte = if (motion.usesWipe) addBandMatte(band, motion) else null
    val emitter = BandPieceEmitter(this, cfg, band, motion, wipeMatte)
    BandDecorations(cfg, band).pieces().forEach(emitter::emit)
    borderPiece(cfg, band)?.let(emitter::emit)
    fillPieces(cfg, band).forEach(emitter::emit)
}

/** One thing the band paints: a shape in a colour role, or a group already carrying its own paint. */
private sealed interface BandPiece {
    class Shaped(val role: BandColorRole, val shape: JsonObject) : BandPiece
    class Painted(val group: JsonObject) : BandPiece
}

/** The border, when the style or the settings ask for one. */
private fun borderPiece(cfg: BibleLottieGenConfig, band: SlotBox): BandPiece? {
    val borderPx = when (cfg.bandStyle) {
        BandStyle.GLASS_PANEL -> cfg.borderThickness.toDouble().coerceAtLeast(GLASS_BORDER_PX)
        else -> cfg.borderThickness.toDouble()
    }
    val stroke = makeStroke(hexToLottie(cfg.borderColor), borderPx, cfg.borderAlpha.toDouble()) ?: return null
    val corner = cfg.cornerRadiusPx.toDouble()
    return BandPiece.Painted(
        makeGroup(
            listOf(makeRect(band.w - borderPx, band.h - borderPx, corner, listOf(band.centerX, band.centerY)), stroke),
        ),
    )
}

/** The fill: a gradient carries its own paint; a plain fill is the background role, colour or picture. */
private fun fillPieces(cfg: BibleLottieGenConfig, band: SlotBox): List<BandPiece> {
    val palette = BandPalette(cfg)
    val alpha = cfg.bgAlpha.toDouble()
    val rect = makeRect(band.w, band.h, cfg.cornerRadiusPx.toDouble(), listOf(band.centerX, band.centerY))
    val gradient = when (cfg.bandStyle) {
        BandStyle.GRADIENT_BAR -> makeGradientFillStops(
            listOf(palette.bg, palette.second), alpha, listOf(band.centerX, band.y), listOf(band.centerX, band.bottom),
        )
        BandStyle.GRADIENT_HORIZONTAL -> makeGradientFillStops(
            listOf(palette.bg, palette.second), alpha, listOf(band.x, band.centerY), listOf(band.right, band.centerY),
        )
        BandStyle.GRADIENT_ANGLED -> makeGradientFillStops(
            listOf(palette.bg, palette.second), alpha, listOf(band.x, band.y), listOf(band.right, band.bottom),
        )
        BandStyle.GRADIENT_TRIO -> makeGradientFillStops(
            listOf(palette.bg, palette.accent, palette.second), alpha,
            listOf(band.x, band.centerY), listOf(band.right, band.centerY),
        )
        else -> null
    }
    return when {
        gradient != null -> listOf(BandPiece.Painted(makeGroup(listOf(rect, gradient))))
        // The tint above the picture, then the picture itself through the same rectangle.
        cfg.hasBackgroundImage -> listOf(
            BandPiece.Painted(makeGroup(listOf(rect, makeFill(palette.bg, alpha)))),
            BandPiece.Shaped(BandColorRole.BACKGROUND, rect),
        )
        else -> listOf(BandPiece.Shaped(BandColorRole.BACKGROUND, rect))
    }
}

/** Writes pieces as layers: a colour piece is a filled shape layer, a picture piece a matte over an image. */
private class BandPieceEmitter(
    private val builder: LottieBuilder,
    private val cfg: BibleLottieGenConfig,
    private val band: SlotBox,
    private val motion: BandMotion,
    private val wipeMatte: Int?,
) {
    private val palette = BandPalette(cfg)
    private var count = 0
    private val addedAssets = mutableSetOf<BandColorRole>()

    fun emit(piece: BandPiece) {
        val name = "${BandLayerNames.BAND_PREFIX}${count++}"
        when (piece) {
            is BandPiece.Painted -> shapeLayer(name, piece.group)
            is BandPiece.Shaped -> {
                val image = cfg.images[piece.role]?.takeIf { it.isUsable }
                if (image == null) {
                    val fill = makeFill(palette.color(piece.role), palette.alpha(piece.role))
                    shapeLayer(name, makeGroup(listOf(piece.shape, fill)))
                } else {
                    imageLayer(name, piece.role, image, piece.shape)
                }
            }
        }
    }

    private fun shapeLayer(name: String, group: JsonObject) {
        builder.addShapeLayer(
            name, buildJsonArray { add(group) }, motion.transform(),
            tt = if (wipeMatte != null) 1 else null, tp = wipeMatte,
        )
    }

    /**
     * The picture shows through the piece's shape: the shape becomes a matte (itself cut by the
     * wipe matte, when there is one) over an image scaled to cover the band. Covering means
     * overhanging — a 4:3 photo on a 16:3 band is far taller than it — and the matte is what
     * keeps the overhang from showing while the band is still sliding in.
     */
    private fun imageLayer(name: String, role: BandColorRole, image: BandImage, shape: JsonObject) {
        val assetId = "band_${role.name.lowercase()}"
        if (addedAssets.add(role)) builder.addImageAsset(assetId, image.data, image.width, image.height)
        val matte = builder.addShapeLayer(
            "${name}Matte", buildJsonArray { add(makeGroup(listOf(shape, makeFill(WHITE)))) }, motion.transform(),
            td = 1, tt = if (wipeMatte != null) 1 else null, tp = wipeMatte,
        )
        val scale = maxOf(band.w / image.width, band.h / image.height) * FULL
        builder.addImageLayer(
            "${name}Image", assetId,
            motion.transform(
                anchorOverride = listOf(image.width / 2.0, image.height / 2.0),
                positionOverride = listOf(band.centerX, band.centerY),
                scalePercent = scale,
            ),
            tt = 1, tp = matte,
        )
    }
}

/** The colours as Lottie wants them, read once, by role. */
private class BandPalette(private val cfg: BibleLottieGenConfig) {
    val bg = hexToLottie(cfg.bgColor)
    val second = hexToLottie(cfg.gradientColor)
    val accent = hexToLottie(cfg.accentColor)

    fun color(role: BandColorRole): List<Double> = when (role) {
        BandColorRole.BACKGROUND -> bg
        BandColorRole.SECOND -> second
        BandColorRole.ACCENT -> accent
        BandColorRole.TERTIARY -> hexToLottie(cfg.tertiaryColor)
    }

    fun alpha(role: BandColorRole): Double = when (role) {
        BandColorRole.BACKGROUND -> cfg.bgAlpha.toDouble()
        BandColorRole.SECOND -> FULL
        BandColorRole.ACCENT -> cfg.accentAlpha.toDouble()
        BandColorRole.TERTIARY -> cfg.tertiaryAlpha.toDouble()
    }
}

/** The primitive shapes the styles are built from, each placed on the band rectangle [b]. */
private open class BandShapes(protected val b: SlotBox) {
    protected val x = b.x
    protected val y = b.y
    protected val w = b.w
    protected val h = b.h
    protected val right = b.right
    protected val bottom = b.bottom

    protected fun rect(role: BandColorRole, x: Double, y: Double, w: Double, h: Double): BandPiece =
        BandPiece.Shaped(role, makeRect(w, h, 0.0, listOf(x + w / 2, y + h / 2)))

    protected fun poly(role: BandColorRole, vararg points: Pair<Double, Double>): BandPiece =
        BandPiece.Shaped(role, makePath(points.map { listOf(it.first, it.second) }))

    protected fun accent(x: Double, y: Double, w: Double, h: Double) = rect(BandColorRole.ACCENT, x, y, w, h)
    protected fun second(x: Double, y: Double, w: Double, h: Double) = rect(BandColorRole.SECOND, x, y, w, h)
    protected fun tertiary(x: Double, y: Double, w: Double, h: Double) = rect(BandColorRole.TERTIARY, x, y, w, h)

    /** Where the band's width fraction [f] lands, in canvas pixels. */
    protected fun fx(f: Double): Double = x + w * f

    /** A slanted stripe [from]..[to] of the band's width at the top, leaning back by [SLANT] at the bottom. */
    protected fun slant(role: BandColorRole, from: Double, to: Double): BandPiece =
        poly(role, fx(from) to y, fx(to) to y, fx(to - SLANT) to bottom, fx(from - SLANT) to bottom)

    /**
     * A wave across the band, its crest [lift] of the height above the base line, filled down to
     * the bottom edge. [flip] mirrors the wave so two of them cross rather than stack.
     */
    protected fun wave(role: BandColorRole, lift: Double, flip: Boolean = false): BandPiece {
        val baseY = bottom - h * (WAVE_TOP + lift)
        val amp = h * WAVE_AMPLITUDE * (if (flip) -1.0 else 1.0)
        val quarter = w / 4
        val handle = listOf(quarter / 2, 0.0)
        val negHandle = listOf(-quarter / 2, 0.0)
        val none = listOf(0.0, 0.0)
        val vertices = listOf(
            listOf(x, baseY),
            listOf(x + quarter, baseY - amp),
            listOf(x + 2 * quarter, baseY),
            listOf(x + 3 * quarter, baseY + amp),
            listOf(right, baseY),
            listOf(right, bottom),
            listOf(x, bottom),
        )
        val inTangents = listOf(none, negHandle, negHandle, negHandle, negHandle, none, none)
        val outTangents = listOf(handle, handle, handle, handle, none, none, none)
        return BandPiece.Shaped(role, makeCurvedPath(vertices, inTangents, outTangents, closed = true))
    }

    /** An ellipse centred on the bottom edge, so its upper half stands as an arch; [grow] widens it for a ring. */
    protected fun arch(role: BandColorRole, grow: Double): BandPiece = BandPiece.Shaped(
        role, makeEllipse(w * (ARCH_W + grow * 2), h * (ARCH_H + grow) * 2, listOf(b.centerX, bottom)),
    )
}

/**
 * What each style draws over its fill. Geometry is in canvas pixels off the band's rectangle;
 * fractions of its width and height keep a style's proportions at any canvas size.
 */
private class BandDecorations(private val cfg: BibleLottieGenConfig, b: SlotBox) : BandShapes(b) {
    private val p = BandPalette(cfg)

    fun pieces(): List<BandPiece> = when (cfg.bandStyle) {
        BandStyle.SOLID_BAR, BandStyle.GRADIENT_BAR, BandStyle.GRADIENT_HORIZONTAL, BandStyle.GRADIENT_ANGLED,
        BandStyle.GRADIENT_TRIO -> emptyList()
        BandStyle.ACCENT_EDGE_BAR -> listOf(accent(x, y, ACCENT_EDGE_PX, h))
        BandStyle.GLASS_PANEL -> listOf(accent(x, bottom - GLASS_UNDERLINE_PX, w, GLASS_UNDERLINE_PX))
        BandStyle.RIBBON -> listOf(accent(x, y, w, RIBBON_STRIPE_PX))
        BandStyle.SPLIT_SHUTTER -> listOf(second(x, y + h / 2, w, h / 2))
        BandStyle.HORIZONTAL_BANDS -> listOf(
            accent(x, y, w, h * THIRD_BAND),
            second(x, bottom - h * THIRD_BAND, w, h * THIRD_BAND),
        )
        BandStyle.TRICOLOR_DIAGONAL -> tricolorDiagonal()
        BandStyle.ANGLED_BLADE -> listOf(
            poly(BandColorRole.SECOND, x to y, fx(BLADE_TOP) to y, fx(BLADE_BOTTOM) to bottom, x to bottom),
        )
        BandStyle.SLANTED_THIRDS -> slantedThirds()
        BandStyle.CROSSED_BANDS -> crossedBands()
        BandStyle.CHEVRON_TAG -> chevron()
        BandStyle.CORNER_WEDGES -> listOf(
            poly(BandColorRole.TERTIARY, x to y, fx(WEDGE_TIP) to y, x to y + h * WEDGE_TIP_H),
            poly(BandColorRole.SECOND, x to y, fx(WEDGE_W) to y, x to bottom),
            poly(BandColorRole.ACCENT, right to bottom, right - w * WEDGE_W to bottom, right to y),
        )
        BandStyle.ARCH_DECK -> listOf(
            tertiary(x, bottom - h * ARCH_BASE_H, w, h * ARCH_BASE_H),
            arch(BandColorRole.SECOND, 0.0),
            arch(BandColorRole.ACCENT, ARCH_RING),
        )
        BandStyle.SPOTLIGHT_BAND -> spotlight()
        BandStyle.DIAGONAL_SPLIT -> diagonalSplit()
        BandStyle.RIBBON_FOLD -> ribbonFold()
        BandStyle.WAVE_DECK -> listOf(
            wave(BandColorRole.SECOND, 0.0),
            wave(BandColorRole.ACCENT, WAVE_CREST),
            wave(BandColorRole.TERTIARY, WAVE_SWELL, flip = true),
        )
    }

    private fun tricolorDiagonal(): List<BandPiece> = listOf(
        poly(BandColorRole.ACCENT, x to y, fx(TRI_1) to y, fx(TRI_1 - SLANT) to bottom, x to bottom),
        poly(
            BandColorRole.SECOND,
            fx(TRI_1) to y, fx(TRI_2) to y, fx(TRI_2 - SLANT) to bottom, fx(TRI_1 - SLANT) to bottom,
        ),
        poly(BandColorRole.TERTIARY, fx(TRI_2) to y, right to y, right to bottom, fx(TRI_2 - SLANT) to bottom),
    )

    private fun slantedThirds(): List<BandPiece> = listOf(
        slant(BandColorRole.ACCENT, SLANT, THIRD_LEFT),
        slant(BandColorRole.TERTIARY, THIRD_LEFT, THIRD_LEFT + DIVIDER_W),
        poly(
            BandColorRole.SECOND,
            fx(THIRD_RIGHT) to y, right to y, right to bottom, fx(THIRD_RIGHT - SLANT) to bottom,
        ),
        poly(BandColorRole.ACCENT, x to y, fx(SLANT) to y, x to bottom),
    )

    private fun crossedBands(): List<BandPiece> = listOf(
        slant(BandColorRole.ACCENT, CROSS_START, CROSS_START + CROSS_W),
        poly(
            BandColorRole.SECOND,
            fx(CROSS_START - SLANT) to y, fx(CROSS_START + CROSS_W - SLANT) to y,
            fx(CROSS_START + CROSS_W) to bottom, fx(CROSS_START) to bottom,
        ),
    )

    private fun spotlight(): List<BandPiece> = listOf(
        second(x, y, SPOT_EDGE_PX, h),
        tertiary(x, bottom - RULE_PX, w, RULE_PX),
        BandPiece.Painted(
            makeGroup(
                listOf(
                    makeRect(w * SPOT_W, h, 0.0, listOf(fx(SPOT_W / 2), b.centerY)),
                    makeGradientFill(
                        p.accent, p.alpha(BandColorRole.ACCENT), listOf(x, b.centerY), listOf(fx(SPOT_W), b.centerY),
                    ),
                ),
            ),
        ),
    )

    private fun diagonalSplit(): List<BandPiece> = listOf(
        poly(
            BandColorRole.ACCENT,
            fx(SPLIT_TOP) - RULE_PX to y, fx(SPLIT_TOP) + RULE_PX to y,
            fx(SPLIT_BOTTOM) + RULE_PX to bottom, fx(SPLIT_BOTTOM) - RULE_PX to bottom,
        ),
        tertiary(x, bottom - RULE_PX, w * SPLIT_BOTTOM, RULE_PX),
        poly(BandColorRole.SECOND, fx(SPLIT_TOP) to y, right to y, right to bottom, fx(SPLIT_BOTTOM) to bottom),
    )

    private fun ribbonFold(): List<BandPiece> {
        val foldY = y + h * FOLD_H
        return listOf(
            tertiary(x, y + h * FOLD_BAND_Y, w * FOLD_W, h * FOLD_BAND_H),
            poly(
                BandColorRole.SECOND,
                x to y, fx(FOLD_W) to y, fx(FOLD_W) to foldY,
                fx(FOLD_W / 2) to y + h * (FOLD_H - FOLD_NOTCH), x to foldY,
            ),
            poly(BandColorRole.ACCENT, fx(FOLD_W) to y, fx(FOLD_W + FOLD_TAIL) to y, fx(FOLD_W) to y + w * FOLD_TAIL),
        )
    }

    /** A chevron in the second colour on the left, echoed by a thin chevron stripe in the third. */
    private fun chevron(): List<BandPiece> {
        val tip = CHEVRON_W + CHEVRON_POINT
        val echo = CHEVRON_W + CHEVRON_ECHO
        val echoOuter = echo + CHEVRON_ECHO_W
        val mid = b.centerY
        return listOf(
            poly(
                BandColorRole.SECOND,
                x to y, fx(CHEVRON_W) to y, fx(tip) to mid, fx(CHEVRON_W) to bottom, x to bottom,
            ),
            poly(
                BandColorRole.TERTIARY,
                fx(echo) to y, fx(echoOuter) to y, fx(echoOuter + CHEVRON_POINT) to mid,
                fx(echoOuter) to bottom, fx(echo) to bottom, fx(echo + CHEVRON_POINT) to mid,
            ),
        )
    }
}

private fun LottieBuilder.addBandMatte(band: SlotBox, motion: BandMotion): Int {
    val shapes = buildJsonArray {
        add(
            makeGroup(
                listOf(
                    makeRect(band.w + MATTE_PAD_PX, band.h + MATTE_PAD_PX, 0.0, listOf(band.centerX, band.centerY)),
                    makeFill(WHITE),
                ),
            ),
        )
    }
    return addShapeLayer(BandLayerNames.BAND_MATTE, shapes, motion.matteTransform(), td = 1)
}

/** The bar that leads a swipe: crosses the band ahead of the fill and rests on its far edge. */
private fun LottieBuilder.addSwipeBar(cfg: BibleLottieGenConfig, band: SlotBox, motion: BandMotion) {
    val half = SWIPE_BAR_PX / 2
    val shapes = buildJsonArray {
        add(
            makeGroup(
                listOf(
                    makeRect(SWIPE_BAR_PX, band.h, 0.0, listOf(0.0, 0.0)),
                    makeFill(hexToLottie(cfg.accentColor), cfg.accentAlpha.toDouble()),
                ),
            ),
        )
    }
    val start = jsonArrayOf(band.x + half, band.centerY, 0.0)
    val end = jsonArrayOf(band.right - half, band.centerY, 0.0)
    val position = motion.keyframes(
        KeyframeInput(0.0, start),
        KeyframeInput(SWIPE_LEAD_PCT, end),
        KeyframeInput(END_PCT, end),
    )
    addShapeLayer(
        "${BandLayerNames.BAND_PREFIX}SwipeBar",
        shapes,
        LottieBuilder.defaultTransform(position = LottieBuilder.animatedProp(position)),
    )
}

/**
 * The entrance as layer-transform keyframes. Anchor and position are the same canvas point, so
 * an unanimated transform is the identity and a scale grows about that point — the bottom edge
 * for the unroll, the centre line for the scroll, the leading edge for a wipe.
 */
private class BandMotion(cfg: BibleLottieGenConfig, private val band: SlotBox, private val timeline: BandTimeline) {
    private val entrance = cfg.entrance
    private val canvasW = cfg.canvasW.toDouble()
    private val canvasH = cfg.canvasH.toDouble()

    val usesWipe: Boolean get() = entrance == BandEntrance.WIPE_LEFT || entrance == BandEntrance.WIPE_RIGHT ||
        entrance == BandEntrance.SWIPE

    fun keyframes(vararg points: KeyframeInput): JsonArray = keyframesOf(points.toList())

    fun keyframesOf(points: List<KeyframeInput>): JsonArray = buildKeyframes(
        points,
        inFrames = timeline.bgInFrames,
        holdFrames = timeline.bandHoldFrames,
        outFrames = timeline.bgOutFrames,
        easing = Easing.DEFAULT,
        startFrame = 0,
    )

    private val anchor: List<Double> = when (entrance) {
        BandEntrance.UNROLL -> listOf(band.centerX, band.bottom)
        BandEntrance.WIPE_RIGHT, BandEntrance.SWIPE -> listOf(band.x, band.centerY)
        BandEntrance.WIPE_LEFT -> listOf(band.right, band.centerY)
        else -> listOf(band.centerX, band.centerY)
    }

    private fun at(x: Double, y: Double): JsonArray = jsonArrayOf(x, y, 0.0)
    private fun scale(x: Double, y: Double): JsonArray = jsonArrayOf(x, y, FULL)

    /**
     * The band's own transform: everything except the wipes, which animate the matte instead.
     *
     * An image layer lives in its own pixel space, so it passes its centre as the anchor, the
     * band's centre as the position and the cover scale; the scale entrances then start from that
     * scale rather than from 100.
     */
    fun transform(
        anchorOverride: List<Double>? = null,
        positionOverride: List<Double>? = null,
        scalePercent: Double = FULL,
    ): JsonObject {
        val ax = anchorOverride?.get(0) ?: anchor[0]
        val ay = anchorOverride?.get(1) ?: anchor[1]
        val px = positionOverride?.get(0) ?: anchor[0]
        val py = positionOverride?.get(1) ?: anchor[1]
        val anchorProp = LottieBuilder.staticPropArray(ax, ay, 0.0)
        val restPos = LottieBuilder.staticPropArray(px, py, 0.0)
        val restScale = LottieBuilder.staticPropArray(scalePercent, scalePercent, FULL)
        val rest = at(px, py)
        return when (entrance) {
            BandEntrance.FADE -> LottieBuilder.defaultTransform(
                opacity = LottieBuilder.animatedProp(
                    keyframes(KeyframeInput(0.0, jsonArrayOf(0.0)), KeyframeInput(END_PCT, jsonArrayOf(FULL))),
                ),
                anchor = anchorProp,
                position = restPos,
                scale = restScale,
            )
            BandEntrance.SLIDE_UP -> slide(anchorProp, at(px, py + canvasH), rest, restScale)
            BandEntrance.SLIDE_DOWN -> slide(anchorProp, at(px, py - canvasH), rest, restScale)
            BandEntrance.SLIDE_LEFT -> slide(anchorProp, at(px + canvasW, py), rest, restScale)
            BandEntrance.SLIDE_RIGHT -> slide(anchorProp, at(px - canvasW, py), rest, restScale)
            BandEntrance.UNROLL, BandEntrance.SCROLL_OPEN ->
                scaled(anchorProp, restPos, scale(scalePercent, 0.0), scale(scalePercent, scalePercent))
            BandEntrance.GROW -> scaled(anchorProp, restPos, scale(0.0, 0.0), scale(scalePercent, scalePercent))
            BandEntrance.WIPE_LEFT, BandEntrance.WIPE_RIGHT, BandEntrance.SWIPE -> LottieBuilder.defaultTransform(
                anchor = anchorProp,
                position = restPos,
                scale = restScale,
            )
        }
    }

    /** The wipe matte's transform: a horizontal scale from the leading edge, delayed for a swipe. */
    fun matteTransform(): JsonObject {
        val ax = anchor[0]
        val ay = anchor[1]
        val startPct = if (entrance == BandEntrance.SWIPE) SWIPE_FILL_START_PCT else 0.0
        val points = buildList {
            add(KeyframeInput(0.0, scale(0.0, FULL)))
            if (startPct > 0.0) add(KeyframeInput(startPct, scale(0.0, FULL)))
            add(KeyframeInput(END_PCT, scale(FULL, FULL)))
        }
        return LottieBuilder.defaultTransform(
            anchor = LottieBuilder.staticPropArray(ax, ay, 0.0),
            position = LottieBuilder.staticPropArray(ax, ay, 0.0),
            scale = LottieBuilder.animatedProp(keyframesOf(points)),
        )
    }

    private fun slide(anchorProp: JsonObject, from: JsonArray, to: JsonArray, restScale: JsonObject): JsonObject =
        LottieBuilder.defaultTransform(
            anchor = anchorProp,
            position = LottieBuilder.animatedProp(keyframes(KeyframeInput(0.0, from), KeyframeInput(END_PCT, to))),
            scale = restScale,
        )

    private fun scaled(anchorProp: JsonObject, restPos: JsonObject, from: JsonArray, to: JsonArray): JsonObject =
        LottieBuilder.defaultTransform(
            anchor = anchorProp,
            position = restPos,
            scale = LottieBuilder.animatedProp(keyframes(KeyframeInput(0.0, from), KeyframeInput(END_PCT, to))),
        )
}

private val WHITE = listOf(1.0, 1.0, 1.0)
private const val FULL = 100.0
private const val RULE_PX = 4.0
private const val SPOT_EDGE_PX = 10.0
private const val END_PCT = 100.0
private const val ACCENT_EDGE_PX = 16.0
private const val GLASS_BORDER_PX = 2.0
private const val GLASS_UNDERLINE_PX = 6.0
private const val RIBBON_STRIPE_PX = 10.0
private const val MATTE_PAD_PX = 4.0
private const val SWIPE_BAR_PX = 14.0
private const val SWIPE_LEAD_PCT = 70.0
private const val SWIPE_FILL_START_PCT = 30.0
