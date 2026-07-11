package presentation.engine.model

import presentation.engine.keynote.KeynoteScene
import java.io.File

/** Source format of a loaded presentation. */
enum class DeckFormat { PDF, PPTX, PPT, KEYNOTE }

/**
 * How faithfully a slide can be reproduced by the engine.
 *
 * [NATIVE] — the engine renders the slide's content itself (and can animate its layers when the
 * slide has a [Slide.timeline]).
 * [STATIC_FALLBACK] — the slide is shown as a single pre-rendered image (embedded preview,
 * thumbnail, or a whole-slide raster); any timeline is discarded.
 */
enum class Fidelity { NATIVE, STATIC_FALLBACK }

/** Why a presentation failed to load. The engine never throws out of [presentation.engine.PresentationLoader]. */
enum class DeckLoadError { UNSUPPORTED_FORMAT, PASSWORD_PROTECTED, EMPTY_DOCUMENT, PARSE_FAILED }

/** A rectangle in slide points (1/72 inch — the native coordinate space of every slide format). */
data class RectPt(val x: Double, val y: Double, val w: Double, val h: Double)

/**
 * A parsed presentation: pure description, no pixels. Rasterization happens on demand through
 * [presentation.engine.DeckRasterizer], which re-opens [sourceFile] as needed — a Deck itself
 * holds no open resources and never needs closing.
 */
class Deck internal constructor(
    val sourceFile: File,
    val format: DeckFormat,
    /** Slide canvas size in points. */
    val slideWidthPt: Double,
    val slideHeightPt: Double,
    val slides: List<Slide>,
    /** Non-fatal problems encountered while parsing (for diagnostics/breadcrumbs). */
    val warnings: List<String> = emptyList(),
    internal val source: DeckSource
) {
    val slideCount: Int get() = slides.size
}

/**
 * One slide. WS1 ships every slide as a single [LayerSpec.StaticComposite] with a null [timeline];
 * later workstreams add per-shape layers and animation.
 */
data class Slide(
    val index: Int,
    val notes: String,
    val transition: SlideTransitionSpec?,
    val layers: List<LayerSpec>,
    val timeline: Timeline?,
    val fidelity: Fidelity
)

/**
 * A renderable layer of a slide. Layers composite bottom-up in [zIndex] order.
 * [initiallyVisible] is false when the layer only appears through an entrance effect.
 */
sealed class LayerSpec {
    abstract val id: String
    abstract val zIndex: Int
    abstract val boundsPt: RectPt
    abstract val initiallyVisible: Boolean

    /** The whole slide flattened into one image — the WS1 static path and every fallback. */
    data class StaticComposite(
        override val id: String,
        override val zIndex: Int,
        override val boundsPt: RectPt,
        override val initiallyVisible: Boolean = true
    ) : LayerSpec()

    /** The slide background plus a run of consecutive never-animated shapes flattened together. */
    data class Background(
        override val id: String,
        override val zIndex: Int,
        override val boundsPt: RectPt,
        /** Indexes into the source slide's shape list rendered into this band (empty = background only). */
        val shapeIndexes: List<Int>,
        override val initiallyVisible: Boolean = true
    ) : LayerSpec()

    /** A single animated shape (or a whole group when no descendant is individually targeted). */
    data class Shape(
        override val id: String,
        override val zIndex: Int,
        override val boundsPt: RectPt,
        /** Index into the source slide's shape list. */
        val shapeIndex: Int,
        override val initiallyVisible: Boolean
    ) : LayerSpec()

    /** One paragraph of a text shape, for by-paragraph builds. */
    data class ParagraphText(
        override val id: String,
        override val zIndex: Int,
        override val boundsPt: RectPt,
        val shapeIndex: Int,
        val paragraphIndex: Int,
        override val initiallyVisible: Boolean
    ) : LayerSpec()

    /** An embedded audio/video placeholder — rendered as a poster frame by the engine. */
    data class Media(
        override val id: String,
        override val zIndex: Int,
        override val boundsPt: RectPt,
        /** Path of the extracted media file, when extraction succeeded. */
        val mediaFile: File?,
        override val initiallyVisible: Boolean = true
    ) : LayerSpec()
}

/** Direction for directional transitions/effects, in slide coordinates. */
enum class Direction { LEFT, RIGHT, UP, DOWN, IN, OUT }

/** A slide-to-slide transition parsed from the source file. */
data class SlideTransitionSpec(
    val type: TransitionType,
    val durationMs: Long,
    val direction: Direction? = null,
    /** Auto-advance delay from the source file (`advTm`), or null when click-advanced. */
    val advanceAfterMs: Long? = null
)

enum class TransitionType { NONE, FADE, PUSH, WIPE, SPLIT, COVER }

/**
 * Internal handle telling [presentation.engine.DeckRasterizer] how to reproduce pixels for this
 * deck. Not part of the public contract.
 */
internal sealed interface DeckSource {
    data class Pdf(val file: File) : DeckSource

    data class PowerPoint(val file: File, val isPptx: Boolean) : DeckSource

    /**
     * Static Keynote content. [strategy] picks between the embedded QuickLook preview PDF and
     * the per-slide `st-` thumbnails; [orderedThumbnailEntries] are zip entry names in slide
     * order (thumbnail strategy only).
     */
    data class KeynoteStatic(
        val file: File,
        val strategy: KeynoteStaticStrategy,
        val orderedThumbnailEntries: List<String> = emptyList()
    ) : DeckSource

    /**
     * Natively parsed Keynote content (reverse-engineered IWA). [staticFallback] renders the
     * slides the whitelist gated to [Fidelity.STATIC_FALLBACK]; null when no aligned static
     * source exists (gated slides then render their parseable subset natively).
     */
    class KeynoteNative(
        val scene: KeynoteScene,
        val staticFallback: KeynoteStatic?
    ) : DeckSource
}

internal enum class KeynoteStaticStrategy { PREVIEW_PDF, THUMBNAILS }
