package presentation.engine.keynote

import presentation.engine.model.SlideTransitionSpec
import presentation.engine.model.Timeline
import java.awt.Color
import java.awt.geom.Path2D
import java.io.File

/**
 * The engine's renderable model of a Keynote document — everything the rasterizer needs,
 * decoupled from the IWA object graph. Coordinates are slide points, origin top-left.
 */
internal class KeynoteScene(
    val file: File,
    val slideWidthPt: Double,
    val slideHeightPt: Double,
    val slides: List<KnSlide>
)

internal class KnSlide(
    val index: Int,
    /** Background fill (slide style, falling back through the master chain). */
    val background: KnFill?,
    /** Flattened draw list, master content first, in z-order. */
    val drawables: List<KnPlacedDrawable>,
    val notes: String,
    val timeline: Timeline?,
    /** Ids of drawables that are build targets (they become their own layers). */
    val builtDrawableIds: Set<Long>,
    /** Ids of drawables whose build fanned out into per-paragraph layers (By Paragraph/Bullet). */
    val paragraphBuiltDrawableIds: Set<Long>,
    val transition: SlideTransitionSpec?,
    /** Non-null → this slide is beyond the whitelist and must render from the static fallback. */
    val gateReason: String?
)

internal data class KnPlacedDrawable(
    /** Archive identifier — build targets reference this. */
    val id: Long,
    val drawable: KnDrawable
)

internal data class KnGeometry(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    /** Radians, counter-clockwise positive (AWT rotate handles sign at render). */
    val angle: Double,
    val hFlip: Boolean,
    val vFlip: Boolean
) {
    companion object {
        val ZERO = KnGeometry(0.0, 0.0, 0.0, 0.0, 0.0, hFlip = false, vFlip = false)
    }
}

internal data class KnFill(
    val color: Color? = null,
    /** File name under Data/ for image fills. */
    val imageFile: String? = null
)

internal sealed interface KnDrawable {
    val geometry: KnGeometry

    data class Image(
        override val geometry: KnGeometry,
        /** File name under Data/. */
        val dataFile: String
    ) : KnDrawable

    data class Shape(
        override val geometry: KnGeometry,
        /** Normalized outline in the unit square (scaled to geometry at render); null = rectangle. */
        val path: Path2D.Double?,
        val fill: KnFill?,
        val strokeColor: Color?,
        val strokeWidthPt: Double,
        val opacity: Double
    ) : KnDrawable

    data class Text(
        override val geometry: KnGeometry,
        /** Optional shape background behind the text (text boxes can carry fill/stroke). */
        val shape: Shape?,
        val paragraphs: List<KnParagraph>
    ) : KnDrawable

    data class Group(
        override val geometry: KnGeometry,
        /** Children with positions relative to the group's origin. */
        val children: List<KnPlacedDrawable>
    ) : KnDrawable

    data class Movie(
        override val geometry: KnGeometry,
        /** File name under Data/ for the movie asset. */
        val videoFile: String,
        /** File name under Data/ for the poster frame; null = no static fallback available. */
        val posterFile: String?
    ) : KnDrawable
}

internal data class KnParagraph(
    val text: String,
    val fontFamily: String?,
    val fontSizePt: Double,
    val bold: Boolean,
    val italic: Boolean,
    val color: Color,
    /** 0=left, 1=right, 2=center, 3=justified (rendered as left). */
    val alignment: Int
)
