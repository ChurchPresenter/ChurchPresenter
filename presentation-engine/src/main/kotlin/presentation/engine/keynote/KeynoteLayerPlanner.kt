package presentation.engine.keynote

import presentation.engine.model.EffectSpec
import presentation.engine.model.LayerSpec
import presentation.engine.model.RectPt
import presentation.engine.model.Timeline
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Layer decomposition for a native Keynote slide — same z-band flattening as the PPTX planner:
 * consecutive never-built drawables collapse into [LayerSpec.Background] bands (the bottom band
 * also paints the slide background), each build target becomes its own [LayerSpec.Shape] layer
 * (`kn-<drawableId>` ids, matching KeynoteBuildMapper's intervals). A build targeting a child
 * inside a group animates the whole group (same degrade as PPTX).
 */
internal object KeynoteLayerPlanner {

    private const val EFFECT_PADDING_PT = 20.0
    private const val MARGIN_FRACTION = 0.25

    fun plan(slide: KnSlide, slideWidthPt: Double, slideHeightPt: Double): List<LayerSpec>? {
        // A top-level movie always becomes its own identifiable layer — the app layer needs to
        // find it to drive live playback — even on a slide with no builds at all.
        val hasTopLevelMovie = slide.drawables.any { it.drawable is KnDrawable.Movie }
        if (!hasTopLevelMovie && (slide.timeline == null || slide.builtDrawableIds.isEmpty())) return null
        val slideBounds = RectPt(0.0, 0.0, slideWidthPt, slideHeightPt)

        // A built id inside a group promotes the enclosing top-level drawable.
        val builtTopIds = mutableSetOf<Long>()
        for (placed in slide.drawables) {
            if (containsAnyId(placed, slide.builtDrawableIds)) builtTopIds.add(placed.id)
            if (placed.drawable is KnDrawable.Movie) builtTopIds.add(placed.id)
        }

        val layers = mutableListOf<LayerSpec>()
        var band = mutableListOf<Int>()
        var z = 0

        fun flushBand(force: Boolean = false) {
            if (band.isEmpty() && !force && z != 0) return
            layers.add(LayerSpec.Background("kn-band-$z", z, slideBounds, band.toList()))
            band = mutableListOf()
            z++
        }

        slide.drawables.forEachIndexed { drawableIndex, placed ->
            if (placed.id in builtTopIds) {
                flushBand(force = z == 0)
                val geometry = placed.drawable.geometry
                val movie = placed.drawable as? KnDrawable.Movie
                val text = placed.drawable as? KnDrawable.Text
                if (placed.id in slide.paragraphBuiltDrawableIds && text != null && text.paragraphs.size > 1) {
                    // By-Paragraph/By-Bullet build: one ParagraphText layer per paragraph — the
                    // same layer kind PPTX's planner already uses, so the rasterizer/timeline
                    // machinery downstream is shared, not reinvented.
                    val bounds = paddedBounds(geometry)
                    text.paragraphs.forEachIndexed { paragraphIndex, _ ->
                        layers.add(
                            LayerSpec.ParagraphText(
                                id = KeynoteBuildMapper.paragraphLayerIdFor(placed.id, paragraphIndex),
                                zIndex = z++,
                                boundsPt = bounds,
                                shapeIndex = drawableIndex,
                                paragraphIndex = paragraphIndex,
                                initiallyVisible = true
                            )
                        )
                    }
                } else if (movie != null) {
                    layers.add(
                        LayerSpec.Media(
                            id = KeynoteBuildMapper.layerIdFor(placed.id),
                            zIndex = z++,
                            boundsPt = paddedBounds(geometry),
                            shapeIndex = drawableIndex,
                            contentRectPt = RectPt(geometry.x, geometry.y, geometry.w, geometry.h),
                            mediaFile = null,
                            initiallyVisible = true
                        )
                    )
                } else {
                    layers.add(
                        LayerSpec.Shape(
                            id = KeynoteBuildMapper.layerIdFor(placed.id),
                            zIndex = z++,
                            boundsPt = paddedBounds(geometry),
                            shapeIndex = drawableIndex,
                            initiallyVisible = true
                        )
                    )
                }
            } else {
                band.add(drawableIndex)
            }
        }
        if (band.isNotEmpty() || z == 0) flushBand(force = true)
        return layers
    }

    /**
     * Rewrites the timeline's layer ids for group-promoted targets and returns the ids of
     * layers whose first effect is an entrance (they start hidden).
     */
    fun remapTimeline(slide: KnSlide, layers: List<LayerSpec>): Pair<Timeline, Set<String>>? {
        val timeline = slide.timeline ?: return null
        val layerIds = layers.map { it.id }.toSet()
        val childToTop = mutableMapOf<String, String>()
        for (placed in slide.drawables) {
            val topLayerId = KeynoteBuildMapper.layerIdFor(placed.id)
            if (topLayerId !in layerIds) continue
            collectIds(placed).forEach { childId ->
                childToTop[KeynoteBuildMapper.layerIdFor(childId)] = topLayerId
            }
        }
        val remapped = Timeline(
            timeline.steps.map { step ->
                presentation.engine.model.Step(
                    step.intervals.mapNotNull { interval ->
                        val target = childToTop[interval.layerId] ?: interval.layerId
                        if (target in layerIds) interval.copy(layerId = target) else null
                    }
                )
            }.filter { it.intervals.isNotEmpty() }
        )
        if (remapped.steps.isEmpty()) return null

        val firstRole = mutableMapOf<String, EffectSpec.Role>()
        for (step in remapped.steps) {
            for (interval in step.intervals.sortedBy { it.beginMs }) {
                firstRole.putIfAbsent(interval.layerId, interval.effect.role)
            }
        }
        val hidden = firstRole.filterValues { it == EffectSpec.Role.ENTRANCE }.keys
        return remapped to hidden
    }

    private fun containsAnyId(placed: KnPlacedDrawable, ids: Set<Long>): Boolean =
        collectIds(placed).any { it in ids }

    private fun collectIds(placed: KnPlacedDrawable): List<Long> {
        val result = mutableListOf(placed.id)
        val drawable = placed.drawable
        if (drawable is KnDrawable.Group) {
            drawable.children.forEach { result.addAll(collectIds(it)) }
        }
        return result
    }

    private fun paddedBounds(geometry: KnGeometry): RectPt {
        val cx = geometry.x + geometry.w / 2
        val cy = geometry.y + geometry.h / 2
        val cosR = abs(cos(geometry.angle))
        val sinR = abs(sin(geometry.angle))
        val rotHalfW = geometry.w / 2 * cosR + geometry.h / 2 * sinR
        val rotHalfH = geometry.w / 2 * sinR + geometry.h / 2 * cosR
        val pad = EFFECT_PADDING_PT + MARGIN_FRACTION * max(geometry.w, geometry.h)
        return RectPt(cx - rotHalfW - pad, cy - rotHalfH - pad, (rotHalfW + pad) * 2, (rotHalfH + pad) * 2)
    }
}
