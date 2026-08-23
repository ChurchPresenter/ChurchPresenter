package org.churchpresenter.lottiegen.spec

import org.churchpresenter.lottiegen.lottie.TextSize
import org.churchpresenter.lottiegen.lottie.TextMeasurer
import org.churchpresenter.lottiegen.lottie.emToPx
import org.churchpresenter.lottiegen.lottie.logoAspect
import org.churchpresenter.lottiegen.lottie.hexToLottie
import org.churchpresenter.lottiegen.lottie.remToPx
import org.churchpresenter.lottiegen.model.LottieGenConfig
import kotlin.math.max

/**
 * Where a text baseline sits relative to the block's centre, as a fraction of that line's own
 * size. A glyph's visual mass sits above its baseline, so centring on the baseline looks low --
 * these are the optical corrections, and they differ per line because the two lines are not
 * centred on the same thing: a lone line is centred on the block, a pair straddles it.
 */
private const val LONE_LINE_BASELINE_FACTOR = 0.35
private const val UPPER_LINE_BASELINE_FACTOR = 0.1
private const val LOWER_LINE_BASELINE_FACTOR = 0.9


/** A resolved point in canvas pixels. */
data class SpecPoint(val x: Double, val y: Double)

/**
 * A slot resolved to canvas pixels. [startX] is the core edge nearer the alignment
 * edge, [endX] the far edge — for right alignment startX > endX.
 */
data class ResolvedSlot(val startX: Double, val centerX: Double, val endX: Double, val coreWidth: Double)

/**
 * The generic layout engine behind [SpecStyleGenerator]: measures text, lays the
 * content-block slots out from the alignment edge inward, and resolves placements —
 * the declarative replacement for each hand-written style's bespoke geometry code.
 *
 * All math deliberately mirrors the classic styles (see Style1Bar) so a faithful port
 * lands on identical pixel positions. `cfg.baseSize` arrives pre-scaled by
 * LottieGenerator — never rescale it here.
 */
class SpecLayoutContext(private val spec: StyleSpec, private val cfg: LottieGenConfig) {

    /** Non-fatal problems found while resolving (unknown slot ids, …) — editor status. */
    val warnings = mutableListOf<String>()

    val baseSize = cfg.baseSize.toDouble()
    val canvasW = cfg.canvasW.toDouble()
    val canvasH = cfg.canvasH.toDouble()

    fun em(v: Double): Double = emToPx(v, baseSize)

    /**
     * The logo's drawn height — `logoSize` under either basis, since both fix it as the height or
     * as the square box's side.
     */
    val logoDrawnH: Double get() = em(cfg.logoSize.toDouble())

    /**
     * The logo's drawn WIDTH, which is what any room reserved for it has to be measured against.
     *
     * Under [LogoScaleBasis.HEIGHT] the logo is scaled from its height, so a wide logo draws wider
     * than `logoSize` and the slot has to grow with it. Under the default
     * [LogoScaleBasis.LONGEST_SIDE] this stays `logoSize` exactly as it always was — a logo can
     * never exceed the box, so no existing spec style moves.
     */
    val logoDrawnW: Double
        get() = when (spec.layout.logoScale) {
            LogoScaleBasis.HEIGHT -> logoDrawnH * logoAspect(cfg.logoW, cfg.logoH)
            LogoScaleBasis.LONGEST_SIDE -> logoDrawnH
        }

    val nameSizePx = em(cfg.nameSize.toDouble())
    val infoSizePx = em(cfg.infoSize.toDouble())

    // Both fields are always measured — hiding a line must not shrink the block
    // (classic-style behavior, see Style1Bar).
    val nameMeasured = TextMeasurer.measure(
        cfg.nameText, cfg.fontFamily, nameSizePx.toFloat(), cfg.nameWeight, cfg.nameTransform
    )
    val infoMeasured = TextMeasurer.measure(
        cfg.infoText, cfg.fontFamily, infoSizePx.toFloat(), cfg.infoWeight, cfg.infoTransform
    )

    private val marginHPx = remToPx(cfg.marginH.toDouble(), baseSize)
    private val marginVPx = remToPx(cfg.marginV.toDouble(), baseSize)
    private val lineSpacingPx = em(cfg.lineSpacing.toDouble())

    val blockH = em(spec.layout.blockHeightEm)
    val borderPx = cfg.borderThickness * baseSize * 0.1

    val isRight = cfg.align == "right"
    val isCenter = cfg.align == "center"

    /** -1 when the flow runs leftward (right alignment), else +1. */
    val mirrorSign = if (isRight) -1.0 else 1.0

    private val visibleSlots: Map<String, Pair<SlotSpec, Double>> = spec.layout.slots
        .filter { visible(it.visibleWhen) }
        .associate { slot -> slot.id to (slot to coreWidth(slot)) }

    /** Total content-block width: visible slot cores plus their gaps. */
    val totalContentW = visibleSlots.values.sumOf { (slot, core) ->
        em(slot.gapBeforeEm) + core + em(slot.gapAfterEm)
    }

    val baseY = canvasH - marginVPx - blockH / 2

    val baseX = when {
        isRight -> canvasW - marginHPx - totalContentW / 2
        isCenter -> canvasW / 2
        else -> marginHPx + totalContentW / 2
    }

    /** Block edge on the alignment side (right edge for right alignment). */
    val blockStartX = baseX - mirrorSign * totalContentW / 2

    /** Block edge on the inward side. */
    val blockEndX = baseX + mirrorSign * totalContentW / 2

    private val resolvedSlots: Map<String, ResolvedSlot> = buildMap {
        var cursor = blockStartX
        for (slot in spec.layout.slots) {
            val entry = visibleSlots[slot.id] ?: continue
            val core = entry.second
            cursor += mirrorSign * em(slot.gapBeforeEm)
            val start = cursor
            cursor += mirrorSign * core
            val end = cursor
            put(slot.id, ResolvedSlot(start, (start + end) / 2, end, core))
            cursor += mirrorSign * em(slot.gapAfterEm)
        }
    }

    /** The name line's text-position anchor (classic baseline correction included). */
    val nameLineY: Double

    /** The info line's text-position anchor (classic baseline correction included). */
    val infoLineY: Double

    init {
        val nameVisible = !cfg.hideName
        val infoVisible = !cfg.hideInfo
        val singleLine = spec.layout.centerSingleLine && nameVisible != infoVisible
        if (singleLine) {
            // Optically center the lone visible line on the block.
            nameLineY = baseY + nameSizePx * LONE_LINE_BASELINE_FACTOR
            infoLineY = baseY + infoSizePx * LONE_LINE_BASELINE_FACTOR
        } else {
            nameLineY = baseY - lineSpacingPx / 2 - nameSizePx * UPPER_LINE_BASELINE_FACTOR
            infoLineY = baseY + lineSpacingPx / 2 + infoSizePx * LOWER_LINE_BASELINE_FACTOR
        }
    }

    fun visible(rules: List<VisibilityRule>): Boolean = rules.all { rule ->
        when (rule) {
            VisibilityRule.ALWAYS -> true
            VisibilityRule.BG_ENABLED -> cfg.bgEnabled
            VisibilityRule.LOGO_ENABLED -> cfg.logoEnabled
            VisibilityRule.NAME_VISIBLE -> !cfg.hideName
            VisibilityRule.INFO_VISIBLE -> !cfg.hideInfo
            VisibilityRule.BORDER_SET -> cfg.borderThickness > 0
        }
    }

    /** Lottie justify code derived from the alignment (never spec-controlled). */
    fun justify(): Int = when {
        isRight -> 1
        isCenter -> 2
        else -> 0
    }

    fun resolve(placement: Placement): SpecPoint {
        val override = placement.alignOverrides[cfg.align]
        val anchorIn = override?.anchorIn ?: placement.anchorIn
        val line = override?.line ?: placement.line
        val offsetXEm = override?.offsetXEm ?: placement.offsetXEm
        val offsetXLogoHalfW = override?.offsetXLogoHalfWidths ?: placement.offsetXLogoHalfWidths
        val offsetYEm = override?.offsetYEm ?: placement.offsetYEm

        val slot = slotFor(placement.slot)
        val x0 = when (anchorIn) {
            AnchorIn.START -> slot.startX
            AnchorIn.CENTER -> slot.centerX
            AnchorIn.END -> slot.endX
        }
        val flowSign = if (placement.mirror == MirrorMode.FLIP_ON_RIGHT) mirrorSign else 1.0
        val y0 = when (line) {
            LineAnchor.BLOCK_CENTER -> baseY
            LineAnchor.NAME_LINE -> nameLineY
            LineAnchor.INFO_LINE -> infoLineY
        }
        val offsetX = em(offsetXEm) + offsetXLogoHalfW * (logoDrawnW / 2)
        return SpecPoint(x0 + flowSign * offsetX, y0 + em(offsetYEm))
    }

    /** Flow sign an element's x offsets/vertices use (honors the mirror opt-out). */
    fun flowSign(placement: Placement): Double =
        if (placement.mirror == MirrorMode.FLIP_ON_RIGHT) mirrorSign else 1.0

    /** Resolved (width, height) in px for the element's sizing/offset-unit math. */
    fun resolveSize(element: ElementSpec): Pair<Double, Double> = when (element) {
        is RectElement -> sizeOf(element.size)
        is EllipseElement -> sizeOf(element.size)
        is BackgroundElement -> sizeOf(element.size)
        is TextElement -> when (element.field) {
            TextFieldRef.NAME -> nameMeasured.width.toDouble() to nameSizePx
            TextFieldRef.INFO -> infoMeasured.width.toDouble() to infoSizePx
        }
        is LogoElement -> {
            val sizePx = em(element.sizeEm ?: cfg.logoSize.toDouble())
            sizePx to sizePx
        }
        is ImageElement -> sizeOf(element.size)
        is PolygonElement -> {
            val xs = element.verticesEm.map { em(it.getOrElse(0) { 0.0 }) }
            val ys = element.verticesEm.map { em(it.getOrElse(1) { 0.0 }) }
            val natural = if (xs.isEmpty()) 0.0 else (xs.max() - xs.min())
            val h = if (ys.isEmpty()) 0.0 else (ys.max() - ys.min())
            natural * fit.fitFactor(element.fitWidthTo, natural) to h
        }
        // Vertex bounding box; bezier tangents deliberately ignored.
        is PathElement -> {
            val xs = element.verticesEm.map { em(it.x) }
            val ys = element.verticesEm.map { em(it.y) }
            val natural = if (xs.isEmpty()) 0.0 else (xs.max() - xs.min())
            val h = if (ys.isEmpty()) 0.0 else (ys.max() - ys.min())
            natural * fit.fitFactor(element.fitWidthTo, natural) to h
        }
    }

    fun sizeOf(size: SizeSpec): Pair<Double, Double> = when (size) {
        is SizeSpec.Em -> em(size.wEm) to em(size.hEm)
        is SizeSpec.ContentDerived ->
            (totalContentW + 2 * em(size.padXEm)) to (blockH + 2 * em(size.padYEm))
        is SizeSpec.TextWrap -> when (size.field) {
            TextFieldRef.NAME ->
                (nameMeasured.width + 2 * em(size.padXEm)) to (nameSizePx + 2 * em(size.padYEm))
            TextFieldRef.INFO ->
                (infoMeasured.width + 2 * em(size.padXEm)) to (infoSizePx + 2 * em(size.padYEm))
        }
        is SizeSpec.LogoPlate ->
            (logoDrawnW + em(size.padEm)) to (logoDrawnH + em(size.padEm))
        is SizeSpec.CanvasWidth -> canvasW to em(size.hEm)
    }

    /** How a spec's paint refers to the operator's configured colours and weights. */
    val paint = SpecPaint(cfg, ::em, borderPx)

    /** Fitting a shape to the measured width of the text. */
    val fit = SpecFit(nameMeasured, infoMeasured)

    private fun slotFor(id: String): ResolvedSlot {
        if (id.isEmpty() || id == BLOCK_SLOT) {
            return ResolvedSlot(blockStartX, baseX, blockEndX, totalContentW)
        }
        resolvedSlots[id]?.let { return it }
        if (spec.layout.slots.any { it.id == id }) {
            // The slot exists but is collapsed — anchor where it would sit: fall back
            // to the block center so dependent elements stay inside the composition.
            return ResolvedSlot(baseX, baseX, baseX, 0.0)
        }
        warnings.add("Unknown slot '$id' — using block center")
        return ResolvedSlot(baseX, baseX, baseX, 0.0)
    }

    private fun coreWidth(slot: SlotSpec): Double = when (slot.kind) {
        SlotKind.LOGO -> logoDrawnW
        SlotKind.FIXED -> em(slot.widthEm)
        SlotKind.TEXT -> max(nameMeasured.width, infoMeasured.width) + TEXT_CORE_PAD_PX
    }

    companion object {
        /** Pseudo slot id resolving to the whole content block. */
        const val BLOCK_SLOT = "block"

        /** Fixed pad the classic styles add to the measured text width (Style1's `+ 10`). */
        const val TEXT_CORE_PAD_PX = 10.0
    }
}


/**
 * The colour and weight lookups a spec's paint resolves through.
 *
 * Split out of [SpecLayoutContext], which was carrying fifteen members across three jobs: where
 * things go, what colour they are, and how wide they stretch.
 */
class SpecPaint(
    private val cfg: LottieGenConfig,
    private val em: (Double) -> Double,
    private val borderPx: Double,
) {
    fun cornerPx(corner: CornerSpec): Double = when (corner) {
        is CornerSpec.None -> 0.0
        is CornerSpec.FromConfig -> em(cfg.corners.toDouble()) * corner.factor
        is CornerSpec.Em -> em(corner.em)
    }

    fun roleColor(role: ColorRole): List<Double> = hexToLottie(
        when (role) {
            ColorRole.NAME -> cfg.nameColor
            ColorRole.INFO -> cfg.infoColor
            ColorRole.ACCENT -> cfg.accentColor
            ColorRole.BG -> cfg.bgColor
            ColorRole.BORDER -> cfg.borderColor
        }
    )

    fun roleAlpha(role: ColorRole): Double = when (role) {
        ColorRole.NAME -> cfg.nameColorAlpha
        ColorRole.INFO -> cfg.infoColorAlpha
        ColorRole.ACCENT -> cfg.accentColorAlpha
        ColorRole.BG -> cfg.bgColorAlpha
        ColorRole.BORDER -> cfg.borderColorAlpha
    }.toDouble()

    fun strokeWidthPx(width: StrokeWidthSpec): Double = when (width) {
        is StrokeWidthSpec.FromConfig -> borderPx
        is StrokeWidthSpec.Em -> em(width.em)
    }
}

/** Fitting a shape to the measured width of the text. */
class SpecFit(
    private val nameMeasured: TextSize,
    private val infoMeasured: TextSize,
) {
    /** The measured width a fit-to-width shape stretches to. */
    fun basisWidthPx(basis: WidthBasis): Double = when (basis) {
        WidthBasis.NAME -> nameMeasured.width.toDouble()
        WidthBasis.INFO -> infoMeasured.width.toDouble()
        WidthBasis.TEXT_BLOCK ->
            max(nameMeasured.width, infoMeasured.width) + SpecLayoutContext.TEXT_CORE_PAD_PX
    }

    /**
     * Scale factor turning a path/polygon's natural x-extent into the fitted width;
     * 1.0 when no fit is requested or the shape has no width.
     */
    fun fitFactor(fitWidthTo: WidthBasis?, naturalWidthPx: Double): Double {
        if (fitWidthTo == null || naturalWidthPx <= 0.0) return 1.0
        return basisWidthPx(fitWidthTo) / naturalWidthPx
    }
}
