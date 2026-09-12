package org.churchpresenter.lottiegen.band

/** A rectangle on the canvas, in pixels, top-left origin. */
data class SlotBox(val x: Double, val y: Double, val w: Double, val h: Double) {
    val right: Double get() = x + w
    val bottom: Double get() = y + h
    val centerX: Double get() = x + w / 2
    val centerY: Double get() = y + h / 2

    fun inset(px: Double): SlotBox = SlotBox(x + px, y + px, w - 2 * px, h - 2 * px)
}

/** The band's rectangle and the text boxes inside it; the second pair is null for [SlotLayout.SINGLE]. */
data class BandSlots(
    val band: SlotBox,
    val text1: SlotBox,
    val reference1: SlotBox,
    val text2: SlotBox?,
    val reference2: SlotBox?,
)

/** The layer names the player addresses. Songs will reuse them: text is text whatever it says. */
object BandLayerNames {
    const val BAND = "Band"
    const val BAND_ACCENT = "BandAccent"
    const val BAND_MATTE = "BandMatte"
    const val TEXT_1 = "Text1"
    const val TEXT_2 = "Text2"
    const val REFERENCE_1 = "Reference1"
    const val REFERENCE_2 = "Reference2"
    const val SHADOW_SUFFIX = "Shadow"
    const val MATTE_SUFFIX = "Matte"
    const val BAND_PREFIX = "Band"
}

/**
 * The proportions the styles are drawn to, as fractions of the band's width and height. Shared
 * by the decorations that paint them and the layout that keeps the text off them.
 */
internal object BandGeometry {
    const val SLANT = 0.08
    const val THIRD_BAND = 0.22
    const val BLADE_TOP = 0.28
    const val BLADE_BOTTOM = 0.2
    const val THIRD_LEFT = 0.2
    const val THIRD_RIGHT = 0.82
    const val DIVIDER_W = 0.02
    const val TRI_1 = 0.4
    const val TRI_2 = 0.72
    const val CROSS_START = 0.7
    const val CROSS_W = 0.09
    const val CHEVRON_W = 0.22
    const val CHEVRON_POINT = 0.05
    const val CHEVRON_ECHO = 0.015
    const val CHEVRON_ECHO_W = 0.008
    const val WEDGE_W = 0.2
    const val WEDGE_TIP = 0.09
    const val WEDGE_TIP_H = 0.4
    const val ARCH_W = 0.7
    const val ARCH_H = 0.42
    const val ARCH_RING = 0.06
    const val ARCH_BASE_H = 0.07
    const val SPOT_W = 0.55
    const val SPLIT_TOP = 0.62
    const val SPLIT_BOTTOM = 0.55
    const val FOLD_W = 0.24
    const val FOLD_H = 0.9
    const val FOLD_NOTCH = 0.14
    const val FOLD_TAIL = 0.04
    const val FOLD_BAND_Y = 0.3
    const val FOLD_BAND_H = 0.1
    const val WAVE_TOP = 0.26
    const val WAVE_AMPLITUDE = 0.07
    const val WAVE_CREST = 0.05
    const val WAVE_SWELL = 0.11
}

/** Fractions of the band's width and height a style paints solid colour over, edge by edge. */
internal data class StyleInsets(
    val left: Double = 0.0,
    val top: Double = 0.0,
    val right: Double = 0.0,
    val bottom: Double = 0.0,
)

/**
 * Where a style's colour blocks are, so the text is laid out beside them rather than across them.
 * Gradients and full-band splits reserve nothing: their colour is the backdrop, not a block.
 */
internal fun BandStyle.textInsets(): StyleInsets = with(BandGeometry) {
    when (this@textInsets) {
        BandStyle.HORIZONTAL_BANDS -> StyleInsets(top = THIRD_BAND, bottom = THIRD_BAND)
        BandStyle.ANGLED_BLADE -> StyleInsets(left = BLADE_TOP)
        BandStyle.SLANTED_THIRDS -> StyleInsets(left = THIRD_LEFT + DIVIDER_W, right = 1.0 - THIRD_RIGHT + SLANT)
        BandStyle.CROSSED_BANDS -> StyleInsets(right = 1.0 - (CROSS_START - SLANT))
        BandStyle.CHEVRON_TAG -> StyleInsets(left = CHEVRON_W + CHEVRON_POINT + CHEVRON_ECHO + CHEVRON_ECHO_W)
        BandStyle.CORNER_WEDGES -> StyleInsets(left = WEDGE_W, right = WEDGE_W)
        BandStyle.ARCH_DECK -> StyleInsets(bottom = ARCH_H + ARCH_RING)
        BandStyle.DIAGONAL_SPLIT -> StyleInsets(right = 1.0 - SPLIT_BOTTOM)
        BandStyle.RIBBON_FOLD -> StyleInsets(left = FOLD_W + FOLD_TAIL)
        BandStyle.WAVE_DECK -> StyleInsets(bottom = WAVE_TOP + WAVE_AMPLITUDE + WAVE_SWELL)
        else -> StyleInsets()
    }
}

/** Where everything goes, from the canvas, the inset, the style's blocks, the padding and the slot layout. */
fun computeSlots(cfg: BibleLottieGenConfig): BandSlots {
    val inset = cfg.insetPx.toDouble()
    val band = SlotBox(inset, inset, cfg.canvasW - 2 * inset, cfg.canvasH - 2 * inset)
    val reserved = cfg.bandStyle.textInsets()
    val clear = SlotBox(
        band.x + band.w * reserved.left,
        band.y + band.h * reserved.top,
        band.w * (1.0 - reserved.left - reserved.right),
        band.h * (1.0 - reserved.top - reserved.bottom),
    )
    val inner = clear.inset(cfg.paddingPx.toDouble())
    val gap = cfg.paddingPx.toDouble()
    return when (cfg.layout) {
        SlotLayout.SINGLE -> {
            val (text, ref) = splitReference(inner, cfg)
            BandSlots(band, text, ref, null, null)
        }
        SlotLayout.SIDE_BY_SIDE -> {
            val colW = (inner.w - gap) / 2
            val left = SlotBox(inner.x, inner.y, colW, inner.h)
            val right = SlotBox(inner.x + colW + gap, inner.y, colW, inner.h)
            val (t1, r1) = splitReference(left, cfg)
            val (t2, r2) = splitReference(right, cfg)
            BandSlots(band, t1, r1, t2, r2)
        }
        SlotLayout.STACKED -> {
            val rowH = (inner.h - gap) / 2
            val top = SlotBox(inner.x, inner.y, inner.w, rowH)
            val bottom = SlotBox(inner.x, inner.y + rowH + gap, inner.w, rowH)
            val (t1, r1) = splitReference(top, cfg)
            val (t2, r2) = splitReference(bottom, cfg)
            BandSlots(band, t1, r1, t2, r2)
        }
    }
}

private fun splitReference(area: SlotBox, cfg: BibleLottieGenConfig): Pair<SlotBox, SlotBox> {
    val refH = area.h * cfg.referenceHeightFraction.toDouble().coerceIn(MIN_REFERENCE_FRACTION, MAX_REFERENCE_FRACTION)
    val textH = area.h - refH
    return when (cfg.referencePlacement) {
        ReferencePlacement.ABOVE ->
            SlotBox(area.x, area.y + refH, area.w, textH) to SlotBox(area.x, area.y, area.w, refH)
        ReferencePlacement.BELOW ->
            SlotBox(area.x, area.y, area.w, textH) to SlotBox(area.x, area.y + textH, area.w, refH)
    }
}

private const val MIN_REFERENCE_FRACTION = 0.1
private const val MAX_REFERENCE_FRACTION = 0.5
