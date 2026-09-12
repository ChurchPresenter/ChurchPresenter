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

/** Where everything goes, from the canvas, the inset, the padding and the slot layout alone. */
fun computeSlots(cfg: BibleLottieGenConfig): BandSlots {
    val inset = cfg.insetPx.toDouble()
    val band = SlotBox(inset, inset, cfg.canvasW - 2 * inset, cfg.canvasH - 2 * inset)
    val inner = band.inset(cfg.paddingPx.toDouble())
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
