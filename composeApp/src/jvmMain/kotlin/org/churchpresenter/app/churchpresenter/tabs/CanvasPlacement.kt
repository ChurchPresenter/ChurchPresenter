package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.core.models.scene.alternateScene
import org.churchpresenter.core.models.scene.isLandscape

/**
 * Where a layer sits against its scene's canvas.
 *
 * The canvas clips what it draws, so a layer dragged past an edge -- or left there when the canvas
 * changes shape -- is cut off or gone entirely with nothing on screen to say it is still there.
 */
internal enum class CanvasPlacement { INSIDE, PARTLY_OUTSIDE, OUTSIDE }

/** Slack at the edges, so a layer snapped flush to one is not reported as poking out of it. */
private const val EDGE_EPSILON = 0.001f

/**
 * Where this layer's box sits against the canvas, whose edges are 0 and 1 on both axes.
 *
 * The box is the axis-aligned one the transform stores; rotation is not taken into account, so a
 * rotated layer whose corners swing past an edge still reads as inside.
 */
internal fun SourceTransform.placement(): CanvasPlacement {
    val right = x + width
    val bottom = y + height
    val overlaps = right > EDGE_EPSILON && x < 1f - EDGE_EPSILON &&
        bottom > EDGE_EPSILON && y < 1f - EDGE_EPSILON
    if (!overlaps) return CanvasPlacement.OUTSIDE
    val inside = x >= -EDGE_EPSILON && y >= -EDGE_EPSILON &&
        right <= 1f + EDGE_EPSILON && bottom <= 1f + EDGE_EPSILON
    return if (inside) CanvasPlacement.INSIDE else CanvasPlacement.PARTLY_OUTSIDE
}

/**
 * This layer moved -- and shrunk, if it is larger than the canvas -- so the whole of it is inside.
 *
 * Moves the least it can: a layer already inside is returned unchanged, and one past an edge is
 * pulled back only as far as that edge. Rotation and opacity are kept.
 */
internal fun SourceTransform.broughtIntoView(): SourceTransform {
    val w = width.coerceIn(0f, 1f)
    val h = height.coerceIn(0f, 1f)
    return copy(
        x = x.coerceIn(0f, 1f - w),
        y = y.coerceIn(0f, 1f - h),
        width = w,
        height = h,
    )
}

/**
 * One of a scene's layouts as the editor shows it: drawn as [scene], with moves saved to the second
 * layout when [isAlternate].
 */
internal data class EditorLayout(val scene: Scene, val isAlternate: Boolean)

/**
 * A scene's layouts, landscape first: the scene alone, or the scene and its second layout when it
 * has one.
 */
internal fun Scene.editorLayouts(): List<EditorLayout> {
    val main = EditorLayout(this, isAlternate = false)
    val second = alternate?.let { EditorLayout(alternateScene(), isAlternate = true) }
    return listOfNotNull(main, second).sortedByDescending { it.scene.isLandscape }
}

/** Where [sourceId] sits in each of these layouts it is not wholly inside, paired with that layout. */
internal fun List<EditorLayout>.misplacements(sourceId: String): List<Pair<EditorLayout, CanvasPlacement>> =
    mapNotNull { layout ->
        val placement = layout.scene.sources.find { it.id == sourceId }?.transform?.placement()
        if (placement == null || placement == CanvasPlacement.INSIDE) null else layout to placement
    }
