package org.churchpresenter.lottiegen.spec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Defaults for the spec fields below, in the same em units the spec itself is written in. */
private const val DEFAULT_STROKE_EM = 0.15
private const val DEFAULT_Y_OFFSET_FACTOR = -0.15
private const val DEFAULT_BADGE_EM_W = 3.0
private const val DEFAULT_BADGE_EM_H = 2.0
private const val DEFAULT_CORNER_FACTOR = 1.1


/**
 * Declarative description of a lower-third animation style, editable in the developer
 * Style Editor and rendered by [SpecStyleGenerator] through the exact same
 * LottieBuilder/buildKeyframes pipeline the hand-written styles use.
 *
 * A spec never contains literal colors, fonts, or pixel sizes: everything resolves
 * against the operator's LottieGenConfig at generation time (color roles, em units,
 * alignment, visibility toggles), so a spec-based style honors the full config surface
 * exactly like a compiled style.
 *
 * Field names are part of the on-disk format — once a spec ships in a release they are
 * frozen; additive evolution only (readers use ignoreUnknownKeys + [formatVersion]).
 */
@Serializable
data class StyleSpec(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    /** Numeric style id when shipped (e.g. "13"); blank while drafting in the editor. */
    val id: String = "",
    /** Dev-facing project/style name. */
    val name: String = "Untitled",
    val layout: LayoutSpec = LayoutSpec(),
    /** Z-order: first element = topmost Lottie layer (matches hand-written add order). */
    val elements: List<ElementSpec> = emptyList()
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

// ---------------------------------------------------------------------------
// Layout: a slot-based content block replacing each style's bespoke layout math
// ---------------------------------------------------------------------------

/**
 * The content block is a horizontal run of [slots] laid out from the alignment edge
 * inward (for right alignment the run is mirrored). The block's total width drives
 * content-derived sizing (backgrounds) and the base position honors the configured
 * safe margins, exactly like the hand-written styles.
 */
@Serializable
data class LayoutSpec(
    val slots: List<SlotSpec> = listOf(
        SlotSpec("logo", SlotKind.LOGO, gapAfterEm = 0.8, visibleWhen = listOf(VisibilityRule.LOGO_ENABLED)),
        SlotSpec("accent", SlotKind.FIXED, widthEm = 0.3),
        SlotSpec("text", SlotKind.TEXT, gapBeforeEm = 1.2)
    ),
    /** Nominal content-block height in em (drives baseY and content-derived heights). */
    val blockHeightEm: Double = 3.5,
    /**
     * When true and only one of name/info is visible, that line recenters vertically on
     * the block. False matches the classic styles (hiding a line leaves the other in place).
     */
    val centerSingleLine: Boolean = false
)

/**
 * One horizontal slot in the content block. A slot whose [visibleWhen] rules fail is
 * collapsed: its core width AND both gaps contribute nothing to the block.
 */
@Serializable
data class SlotSpec(
    /** Referenced by [Placement.slot]. */
    val id: String,
    val kind: SlotKind,
    /** Core width in em — [SlotKind.FIXED] only. */
    val widthEm: Double = 0.0,
    val gapBeforeEm: Double = 0.0,
    val gapAfterEm: Double = 0.0,
    /** ANDed; empty = always visible. */
    val visibleWhen: List<VisibilityRule> = emptyList()
)

@Serializable
enum class SlotKind {
    /** Core width = cfg.logoSize em (collapses with the slot's visibility rules). */
    LOGO,

    /** Core width = [SlotSpec.widthEm]. */
    FIXED,

    /**
     * Core width = the widest measured text field + a small fixed pad. Both fields are
     * always measured regardless of hide toggles (classic-style behavior: hiding a line
     * does not shrink the block).
     */
    TEXT
}

/** Config-driven visibility conditions. All listed rules must pass (AND). */
@Serializable
enum class VisibilityRule {
    ALWAYS,

    /** cfg.bgEnabled */
    BG_ENABLED,

    /** cfg.logoEnabled */
    LOGO_ENABLED,

    /** !cfg.hideName */
    NAME_VISIBLE,

    /** !cfg.hideInfo */
    INFO_VISIBLE,

    /** cfg.borderThickness > 0 */
    BORDER_SET
}

// ---------------------------------------------------------------------------
// Placement: flow coordinates, auto-mirrored per alignment
// ---------------------------------------------------------------------------

/**
 * Where an element rests. Positions are expressed in flow coordinates: +x points
 * "inward" (away from the alignment edge — rightward for left/center alignment,
 * leftward for right alignment). The layout engine multiplies flow x by the mirror
 * sign unless [mirror] is [MirrorMode.NONE], so one placement usually covers all
 * three alignments; [alignOverrides] handles the exceptions.
 */
@Serializable
data class Placement(
    /** Slot id from [LayoutSpec.slots]. */
    val slot: String = "text",
    /** Horizontal anchor within the slot's core box (gaps excluded). */
    val anchorIn: AnchorIn = AnchorIn.CENTER,
    /** Vertical anchor. */
    val line: LineAnchor = LineAnchor.BLOCK_CENTER,
    /** Flow-signed horizontal offset in em. */
    val offsetXEm: Double = 0.0,
    /** Vertical offset in em, +down, never mirrored. */
    val offsetYEm: Double = 0.0,
    val mirror: MirrorMode = MirrorMode.FLIP_ON_RIGHT,
    /**
     * Transform origin offset from the rest point in em (x flow-signed) — ROTATION and
     * SCALE tracks orbit this point instead of the element center. Shape elements only
     * (rect/ellipse/polygon/path); ignored for text, logo and background.
     */
    val pivotXEm: Double = 0.0,
    val pivotYEm: Double = 0.0,
    /** Keys "left"/"center"/"right"; individual fields override when non-null. */
    val alignOverrides: Map<String, PlacementOverride> = emptyMap()
)

@Serializable
data class PlacementOverride(
    val anchorIn: AnchorIn? = null,
    val line: LineAnchor? = null,
    val offsetXEm: Double? = null,
    val offsetYEm: Double? = null,
    /** true = the element is not built at all for this alignment. */
    val hidden: Boolean? = null
)

@Serializable
enum class AnchorIn {
    /** Core edge nearer the alignment edge. */
    START,
    CENTER,

    /** Core edge farther from the alignment edge. */
    END
}

@Serializable
enum class LineAnchor {
    /** Vertical center of the content block. */
    BLOCK_CENTER,

    /** The name line's text baseline anchor (classic baseline correction applied). */
    NAME_LINE,

    /** The info line's text baseline anchor (classic baseline correction applied). */
    INFO_LINE
}

@Serializable
enum class MirrorMode { NONE, FLIP_ON_RIGHT }

// ---------------------------------------------------------------------------
// Sizing
// ---------------------------------------------------------------------------

@Serializable
sealed interface SizeSpec {
    @Serializable
    @SerialName("em")
    data class Em(val wEm: Double, val hEm: Double) : SizeSpec

    /** Content block size + padding on each side — the classic background panel. */
    @Serializable
    @SerialName("contentDerived")
    data class ContentDerived(val padXEm: Double = 1.0, val padYEm: Double = 1.0) : SizeSpec

    /** Wraps one measured text field + padding (boxed-style panels). */
    @Serializable
    @SerialName("textWrap")
    data class TextWrap(val field: TextFieldRef, val padXEm: Double = 0.5, val padYEm: Double = 0.3) : SizeSpec

    /** Full canvas width (banner/ticker bands); always horizontally centered on the canvas. */
    @Serializable
    @SerialName("canvasWidth")
    data class CanvasWidth(val hEm: Double) : SizeSpec
}

@Serializable
enum class TextFieldRef { NAME, INFO }

// ---------------------------------------------------------------------------
// Paint: color roles only — literal colors never appear in a spec
// ---------------------------------------------------------------------------

@Serializable
data class PaintSpec(
    val fill: FillSpec? = FillSpec(),
    val stroke: StrokeSpec? = null
)

@Serializable
data class FillSpec(
    val role: ColorRole = ColorRole.ACCENT,
    /** When set, renders as a 2-stop color-to-transparent gradient instead of a flat fill. */
    val gradient: GradientSpec? = null,
    /** Multiplied into the role's alpha (derived translucency, e.g. ticker bands). */
    val alphaFactor: Double = 1.0
)

/** Gradient start/end in em relative to the element center; x is flow-signed. */
@Serializable
data class GradientSpec(
    val startXEm: Double,
    val startYEm: Double,
    val endXEm: Double,
    val endYEm: Double
)

@Serializable
data class StrokeSpec(
    val role: ColorRole = ColorRole.BORDER,
    val width: StrokeWidthSpec = StrokeWidthSpec.FromConfig,
    /** Equal dash/gap length in em; 0 = solid stroke. */
    val dashEm: Double = 0.0,
    /** Multiplied into the role's alpha. */
    val alphaFactor: Double = 1.0
)

@Serializable
sealed interface StrokeWidthSpec {
    /** cfg.borderThickness scaled the classic way; 0 = no stroke at all. */
    @Serializable
    @SerialName("fromConfig")
    data object FromConfig : StrokeWidthSpec

    @Serializable
    @SerialName("em")
    data class Em(val em: Double) : StrokeWidthSpec
}

@Serializable
enum class ColorRole { NAME, INFO, ACCENT, BG, BORDER }

@Serializable
sealed interface CornerSpec {
    @Serializable
    @SerialName("none")
    data object None : CornerSpec

    /** cfg.corners em × [factor]. */
    @Serializable
    @SerialName("fromConfig")
    data class FromConfig(val factor: Double = 1.0) : CornerSpec

    @Serializable
    @SerialName("em")
    data class Em(val em: Double) : CornerSpec
}

// ---------------------------------------------------------------------------
// Animation
// ---------------------------------------------------------------------------

/**
 * One animated property. Keyframes describe the animate-IN only (pct 0-100 of the in
 * phase, ascending); hold and the mirrored animate-out are synthesized by the shared
 * keyframe builder, exactly like hand-written styles.
 */
@Serializable
data class AnimTrack(
    val property: AnimProperty,
    val keyframes: List<SpecKeyframe>,
    val easing: EasingKind = EasingKind.DEFAULT,
    /** Unit for [AnimProperty.POSITION_OFFSET] values. */
    val offsetUnit: OffsetUnit = OffsetUnit.EM,
    /** Wholesale keyframe replacement per alignment ("left"/"center"/"right"). */
    val alignOverrides: Map<String, List<SpecKeyframe>> = emptyMap()
)

@Serializable
data class SpecKeyframe(val pct: Double, val values: List<Double>)

@Serializable
enum class AnimProperty {
    /** values = [dx, dy] in [AnimTrack.offsetUnit]; dx is flow-signed. Rest = [0, 0]. */
    POSITION_OFFSET,

    /** values = [opacity 0-100]. Multiplied into the element's resolved alpha. */
    OPACITY,

    /** values = [scale percent] uniform, or [sx, sy] for non-uniform. */
    SCALE,

    /** values = [degrees]. */
    ROTATION,

    /** values = [wFrac, hFrac] 0-1 of the resolved size. Rect-like elements only. */
    RECT_SIZE,

    /** values = [frac] 0-1 of the resolved stroke width. Stroked elements only. */
    STROKE_WIDTH,

    /**
     * Trim Paths draw-on: values = [startFrac, endFrac] 0-1 of the path length —
     * animate end 0→1 to grow a stroke along its path (vine/draw-on). Path, polygon
     * and rect-like shape elements (a stroked rect outline draws itself around the box).
     */
    TRIM
}

@Serializable
enum class OffsetUnit {
    EM,

    /** Multiples of the element's resolved width (e.g. slide a text fully past its mask). */
    ELEMENT_WIDTH,

    /** Multiples of the element's resolved height. */
    ELEMENT_HEIGHT
}

@Serializable
enum class EasingKind { DEFAULT, LINEAR }

// ---------------------------------------------------------------------------
// Elements
// ---------------------------------------------------------------------------

@Serializable
sealed interface ElementSpec {
    /** Stable unique id (editor selection, timeline rows). */
    val id: String

    /** Lottie layer name (nm). */
    val name: String

    /** ANDed against cfg; when false the layer is not built at all (never just hidden). */
    val visibleWhen: List<VisibilityRule>
    val placement: Placement
    val tracks: List<AnimTrack>
}

@Serializable
@SerialName("rect")
data class RectElement(
    override val id: String,
    override val name: String = "Rect",
    override val visibleWhen: List<VisibilityRule> = emptyList(),
    override val placement: Placement = Placement(),
    override val tracks: List<AnimTrack> = emptyList(),
    val size: SizeSpec = SizeSpec.Em(1.0, 1.0),
    val paint: PaintSpec = PaintSpec(),
    val corner: CornerSpec = CornerSpec.None,
    /** Anchor for RECT_SIZE growth. */
    val growFrom: GrowOrigin = GrowOrigin.CENTER,
    val repeat: RepeatSpec? = null
) : ElementSpec

@Serializable
enum class GrowOrigin {
    CENTER,

    /** Grows inward from the alignment edge; center alignment grows from the center. */
    ALIGN_EDGE
}

@Serializable
@SerialName("ellipse")
data class EllipseElement(
    override val id: String,
    override val name: String = "Ellipse",
    override val visibleWhen: List<VisibilityRule> = emptyList(),
    override val placement: Placement = Placement(),
    override val tracks: List<AnimTrack> = emptyList(),
    val size: SizeSpec = SizeSpec.Em(1.0, 1.0),
    val paint: PaintSpec = PaintSpec(),
    val repeat: RepeatSpec? = null
) : ElementSpec

/** Measured width a path/polygon can stretch to (see `fitWidthTo`). */
@Serializable
enum class WidthBasis {
    /** The name field's measured width. */
    NAME,

    /** The info field's measured width. */
    INFO,

    /** The widest text line (the TEXT slot's core width). */
    TEXT_BLOCK
}

@Serializable
@SerialName("polygon")
data class PolygonElement(
    override val id: String,
    override val name: String = "Polygon",
    override val visibleWhen: List<VisibilityRule> = emptyList(),
    override val placement: Placement = Placement(),
    override val tracks: List<AnimTrack> = emptyList(),
    /** Vertices in em relative to the element position; x is flow-signed. */
    val verticesEm: List<List<Double>> = emptyList(),
    val closed: Boolean = true,
    val paint: PaintSpec = PaintSpec(),
    val repeat: RepeatSpec? = null,
    /**
     * When set, x-coordinates are scaled (about the element position) so the shape's
     * natural width matches the measured basis width — underlines/rules that span the
     * text at any length instead of a fixed em width.
     */
    val fitWidthTo: WidthBasis? = null
) : ElementSpec

/** One bezier vertex: position + tangent handles, all em, relative to the vertex; x flow-signed. */
@Serializable
data class CurveVertex(
    val x: Double,
    val y: Double,
    val inX: Double = 0.0,
    val inY: Double = 0.0,
    val outX: Double = 0.0,
    val outY: Double = 0.0
)

/**
 * A bezier path, usually stroke-painted and grown via a [AnimProperty.TRIM] track —
 * the vine/draw-on element.
 */
@Serializable
@SerialName("path")
data class PathElement(
    override val id: String,
    override val name: String = "Path",
    override val visibleWhen: List<VisibilityRule> = emptyList(),
    override val placement: Placement = Placement(),
    override val tracks: List<AnimTrack> = emptyList(),
    val verticesEm: List<CurveVertex> = emptyList(),
    val closed: Boolean = false,
    val paint: PaintSpec = PaintSpec(
        fill = null,
        stroke = StrokeSpec(ColorRole.ACCENT, StrokeWidthSpec.Em(DEFAULT_STROKE_EM))
    ),
    val repeat: RepeatSpec? = null,
    /** See [PolygonElement.fitWidthTo] — scales vertex AND tangent x-coordinates. */
    val fitWidthTo: WidthBasis? = null
) : ElementSpec

/**
 * Draws N copies of the shape, each offset/rotated/scaled from the previous —
 * leaf rows, dotted trails, sparkle runs. Offset x is flow-signed.
 */
@Serializable
data class RepeatSpec(
    val copies: Int = 3,
    val offsetXEm: Double = 1.0,
    val offsetYEm: Double = 0.0,
    val rotationDeg: Double = 0.0,
    val scalePct: Double = 100.0,
    /** Fade the last copy toward transparent. */
    val fadeOut: Boolean = false,
    /**
     * When set (and copies > 1), the copy x-offset is derived so the whole row spans
     * the measured basis width — offsetXEm then only contributes its sign (row
     * direction) — bulb rows/dotted trails that hug the text at any length.
     */
    val fitWidthTo: WidthBasis? = null
)

@Serializable
@SerialName("text")
data class TextElement(
    override val id: String,
    override val name: String,
    override val visibleWhen: List<VisibilityRule> = emptyList(),
    override val placement: Placement = Placement(slot = "text", anchorIn = AnchorIn.START),
    override val tracks: List<AnimTrack> = emptyList(),
    /** Text content, size, weight, case transform, and color role all resolve from cfg. */
    val field: TextFieldRef,
    /** Paints the text in another role's color+alpha (e.g. an accent-colored copy). */
    val colorRole: ColorRole? = null,
    /** When set, a matching mask layer clips the text (the classic td/tt reveal pattern). */
    val maskReveal: MaskRevealSpec? = null,
    /** Optional per-character animator. */
    val animator: TextAnimatorSpec? = null
) : ElementSpec

/**
 * Mask geometry mirrors the classic styles: a white rect sized to the measured text
 * plus [padPx], [heightFactor] × font size tall, vertically shifted by
 * [yOffsetFactor] × font size. [widthEm]/[heightEm] replace the text-derived size when
 * set (band clips); [skewXEm] turns the rect into a parallelogram (diagonal edges);
 * [tracks] (POSITION_OFFSET only) animate the mask itself — the wipe pattern, where
 * the text stays put and the mask sweeps across it.
 */
@Serializable
data class MaskRevealSpec(
    val padPx: Double = 20.0,
    /** When set, the horizontal pad is padEm (em-scaled) instead of the raw [padPx]. */
    val padEm: Double? = null,
    val heightFactor: Double = 1.5,
    val yOffsetFactor: Double = DEFAULT_Y_OFFSET_FACTOR,
    val widthEm: Double? = null,
    val heightEm: Double? = null,
    val offsetXEm: Double = 0.0,
    val offsetYEm: Double = 0.0,
    val skewXEm: Double = 0.0,
    val tracks: List<AnimTrack> = emptyList()
)

@Serializable
data class TextAnimatorSpec(
    val kind: TextAnimatorKind,
    val startPct: Double,
    val endPct: Double,
    /** Per-character vertical offset in em (SEQUENTIAL_REVEAL only; +down). */
    val posOffsetEm: Double = 0.0
)

@Serializable
enum class TextAnimatorKind { SEQUENTIAL_REVEAL, RANDOM_FADE }

@Serializable
@SerialName("logo")
data class LogoElement(
    override val id: String,
    override val name: String = "Logo",
    override val visibleWhen: List<VisibilityRule> = listOf(VisibilityRule.LOGO_ENABLED),
    override val placement: Placement = Placement(slot = "logo"),
    override val tracks: List<AnimTrack> = emptyList(),
    /** Rendered size in em; null = cfg.logoSize (styles with their own logo sizing). */
    val sizeEm: Double? = null
) : ElementSpec

/** How an image maps onto its resolved box (see [ImageElement.scaleMode]). */
@Serializable
enum class ImageScaleMode {
    /** Uniform scale, whole image visible inside the box (contain). */
    FIT,

    /** Per-axis scale, image exactly fills the box (may distort). */
    STRETCH,

    /** Uniform scale filling the box, overflow cropped by the matte. */
    COVER
}

/**
 * A designer-baked image: imported in the Style Editor and EMBEDDED in the spec as a
 * base64 data URI — the one spec field carrying binary payload, so the style stays
 * self-contained (ships/exports with its art, end users need nothing installed). The
 * editor downscales imports to ≤1920 px so specs stay reasonably sized; keep source
 * images modest regardless.
 *
 * `size: ContentDerived` + `corner: FromConfig` placed just above the Background element
 * gives a panel background image; `Em` boxes give decorative art/watermarks
 * (with [alphaFactor] for translucency).
 */
@Serializable
@SerialName("image")
data class ImageElement(
    override val id: String,
    override val name: String = "Image",
    override val visibleWhen: List<VisibilityRule> = emptyList(),
    override val placement: Placement = Placement(),
    override val tracks: List<AnimTrack> = emptyList(),
    /** Embedded image data URI; empty = element renders nothing (never an error). */
    val dataUri: String = "",
    /** Natural pixel dimensions of the embedded image. */
    val naturalW: Int = 0,
    val naturalH: Int = 0,
    val size: SizeSpec = SizeSpec.Em(DEFAULT_BADGE_EM_W, DEFAULT_BADGE_EM_H),
    val scaleMode: ImageScaleMode = ImageScaleMode.FIT,
    /** Rounded-rect clip (td/tt matte); COVER always mattes, even with None. */
    val corner: CornerSpec = CornerSpec.None,
    /** Rest opacity multiplier (watermark/texture translucency). */
    val alphaFactor: Double = 1.0
) : ElementSpec

@Serializable
@SerialName("background")
data class BackgroundElement(
    override val id: String,
    override val name: String = "Background",
    override val visibleWhen: List<VisibilityRule> = listOf(VisibilityRule.BG_ENABLED),
    override val placement: Placement = Placement(slot = "", anchorIn = AnchorIn.CENTER),
    override val tracks: List<AnimTrack> = emptyList(),
    val size: SizeSpec = SizeSpec.ContentDerived(),
    val paint: PaintSpec = PaintSpec(fill = FillSpec(ColorRole.BG)),
    val corner: CornerSpec = CornerSpec.FromConfig(DEFAULT_CORNER_FACTOR),
    /** Adds a BORDER-role stroke only when cfg.borderThickness > 0 (classic behavior). */
    val borderFromConfig: Boolean = true,
    val growFrom: GrowOrigin = GrowOrigin.ALIGN_EDGE
) : ElementSpec
