package org.churchpresenter.lottiegen.band

/** A rectangle on the canvas, in pixels, top-left origin. */
data class SlotBox(val x: Double, val y: Double, val w: Double, val h: Double) {
    val right: Double get() = x + w
    val bottom: Double get() = y + h
    val centerX: Double get() = x + w / 2
    val centerY: Double get() = y + h / 2

    fun inset(px: Double): SlotBox = SlotBox(x + px, y + px, w - 2 * px, h - 2 * px)
}

/** One cell's text box and its reference box. */
data class TextRefSlot(val text: SlotBox, val reference: SlotBox)

/**
 * The band's rectangle and its text/reference cells, one to four of them -- as many as
 * [BibleLottieGenConfig.layout] has room for.
 */
data class BandSlots(val band: SlotBox, val slots: List<TextRefSlot>) {
    val count: Int get() = slots.size
}

/** The layer names the player addresses. Songs will reuse them: text is text whatever it says. */
object BandLayerNames {
    const val BAND = "Band"
    const val BAND_ACCENT = "BandAccent"
    const val BAND_MATTE = "BandMatte"
    const val WASH_SUFFIX = "Wash"
    const val TEXT_1 = "Text1"
    const val TEXT_2 = "Text2"
    const val TEXT_3 = "Text3"
    const val TEXT_4 = "Text4"
    const val REFERENCE_1 = "Reference1"
    const val REFERENCE_2 = "Reference2"
    const val REFERENCE_3 = "Reference3"
    const val REFERENCE_4 = "Reference4"
    const val SHADOW_SUFFIX = "Shadow"
    const val MATTE_SUFFIX = "Matte"
    const val BAND_PREFIX = "Band"

    /** [TEXT_1]/[REFERENCE_1] through the fourth, in slot order -- the pairing [computeSlots] fills. */
    val SLOT_LAYER_NAMES = listOf(
        TEXT_1 to REFERENCE_1,
        TEXT_2 to REFERENCE_2,
        TEXT_3 to REFERENCE_3,
        TEXT_4 to REFERENCE_4,
    )
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
    const val UNDERLINE_H = 0.09
    const val UNDERLINE_RULE_H = 0.03
    const val UNDERLINE_GAP = 0.02
    const val DOUBLE_RULE_H = 0.05
    const val SIDE_TAB_W = 0.05
    const val BOOKMARK_X = 0.05
    const val BOOKMARK_W = 0.09
    const val BOOKMARK_NOTCH = 0.15
    const val BOOKMARK_STRIPE = 0.025
    const val STEP_W = 0.10
    const val STEP_2_W = 0.04
    const val STEP_3_W = 0.02
    const val STEP_DROP = 0.33
    const val STRIPE_1 = 0.86
    const val STRIPE_W = 0.03
    const val STRIPE_GAP = 0.02
    const val STRIPE_THIN = 0.015
    const val BRACKET_ARM_H = 0.3
    const val BRACKET_ARM_W = 0.04
    const val CHECKER_ROWS = 4
    const val CHECKER_LEFT = 0.1
    const val ARCH_QUARTER_W = 0.36
    const val ARCH_QUARTER_H = 2.2
    const val TWIN_X = 0.02
    const val TWIN_W = 0.008
    const val TWIN_GAP = 0.015
    const val PANEL_INSET_X = 0.02
    const val PANEL_INSET_Y = 0.12
    const val ZIGZAG_TEETH = 12
    const val ZIGZAG_H = 0.18
    const val ZIGZAG_VALLEY = 0.03
    const val PENNANT_W = 0.2
    const val PENNANT_INNER_W = 0.1
    const val PENNANT_INNER_Y = 0.25
    const val SLASH_1 = 0.10
    const val SLASH_W = 0.025
    const val SLASH_GAP = 0.02
    const val TAB_X = 0.03
    const val TAB_W = 0.12
    const val TAB_STEP = 0.03
    const val TAB_H = 0.12
    const val TAB_Y = 0.18
    const val TAB_GAP = 0.14
    const val BLOCK_W = 0.2
    const val BLOCK_EDGE = 0.012
    const val TOP_TAB_W = 0.28
    const val TOP_TAB_H = 0.22
    const val TOP_TAB_RULE = 0.03
    const val BOTTOM_BAND_H = 0.3
    const val BOTTOM_BAND_RULE = 0.02
    const val CHAMFER_CUT = 0.35
    const val CHAMFER_STRIPE = 0.02
    const val LEFT_BLOCK = 0.18
    const val LEFT_BLOCK_WIDE = 0.22
    const val EDGE_RULES = 0.06
    const val BOTTOM_STRIP = 0.12
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
        BandStyle.UNDERLINE_BAR -> StyleInsets(bottom = BOTTOM_STRIP)
        BandStyle.DOUBLE_RULE -> StyleInsets(top = DOUBLE_RULE_H, bottom = DOUBLE_RULE_H)
        BandStyle.SIDE_TABS -> StyleInsets(left = SIDE_TAB_W, right = SIDE_TAB_W)
        BandStyle.BOOKMARK -> StyleInsets(left = BOOKMARK_X + BOOKMARK_W + SLASH_GAP)
        BandStyle.STEPPED_LEFT, BandStyle.QUARTER_ARCH, BandStyle.SLASHES, BandStyle.STACKED_TABS ->
            StyleInsets(left = LEFT_BLOCK)
        BandStyle.LEFT_BLOCK, BandStyle.CHAMFER_BLOCK -> StyleInsets(left = BLOCK_W + SLASH_GAP)
        BandStyle.TOP_TAB -> StyleInsets(top = TOP_TAB_H + TOP_TAB_RULE)
        BandStyle.BOTTOM_BAND -> StyleInsets(bottom = BOTTOM_BAND_H)
        BandStyle.DIAGONAL_STRIPES -> StyleInsets(right = 1.0 - (STRIPE_1 - SLANT))
        BandStyle.CHECKER_EDGE -> StyleInsets(left = CHECKER_LEFT)
        BandStyle.TWIN_RULES -> StyleInsets(left = EDGE_RULES)
        BandStyle.INNER_PANEL -> StyleInsets(
            left = PANEL_INSET_X, right = PANEL_INSET_X, top = PANEL_INSET_Y, bottom = PANEL_INSET_Y,
        )
        BandStyle.ZIGZAG_EDGE -> StyleInsets(bottom = ZIGZAG_H + ZIGZAG_VALLEY)
        BandStyle.PENNANT -> StyleInsets(left = LEFT_BLOCK_WIDE)
        else -> StyleInsets()
    }
}

/**
 * Where everything goes, from the canvas, the inset, the style's blocks, the padding and the slot
 * layout's rows × cols grid -- [SlotLayout.rows] equal rows of [SlotLayout.cols] equal columns, in
 * reading order, each split into its own text and reference box the same way a single cell always
 * has been.
 */
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
    val inner = clear.inset(cfg.paddingPx.toDouble()).trimmed(cfg, within = band)
    val gap = cfg.paddingPx.toDouble()
    val layout = cfg.layout
    val colW = (inner.w - gap * (layout.cols - 1)) / layout.cols
    val rowH = (inner.h - gap * (layout.rows - 1)) / layout.rows
    val cells = (0 until layout.rows).flatMap { row ->
        (0 until layout.cols).map { col ->
            SlotBox(
                inner.x + col * (colW + gap),
                inner.y + row * (rowH + gap),
                colW,
                rowH,
            )
        }
    }
    return BandSlots(band, cells.map { cell -> splitReference(cell, cfg).let { (t, r) -> TextRefSlot(t, r) } })
}

/**
 * The text area with its four margins applied — taken off when positive, given back when
 * negative — never past [within]'s edges, and kept at least a pixel wide and tall.
 */
private fun SlotBox.trimmed(cfg: BibleLottieGenConfig, within: SlotBox): SlotBox {
    val left = (x + cfg.textAreaLeftPx).coerceIn(within.x, within.right - MIN_AREA_PX)
    val top = (y + cfg.textAreaTopPx).coerceIn(within.y, within.bottom - MIN_AREA_PX)
    val rightEdge = (right - cfg.textAreaRightPx).coerceIn(left + MIN_AREA_PX, within.right)
    val bottomEdge = (bottom - cfg.textAreaBottomPx).coerceIn(top + MIN_AREA_PX, within.bottom)
    return SlotBox(left, top, rightEdge - left, bottomEdge - top)
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

private const val MIN_AREA_PX = 1.0
private const val MIN_REFERENCE_FRACTION = 0.1
private const val MAX_REFERENCE_FRACTION = 0.5
