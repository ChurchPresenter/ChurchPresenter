package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.presentationengine.SlideRenderDegradation

/**
 * Reports a slide the engine could only render by leaving elements out.
 *
 * The engine cannot report this itself — `:presentation-engine` has no dependency on the crash
 * reporter — so every `DeckRasterizer` the app builds passes this in. One function rather than one
 * per call site so all three renders (the tab, the companion API and live playback) group as a
 * single issue.
 *
 * The context sentence is constant, per `CrashReporter.reportWarning`'s contract: the slide index
 * and the counts vary per occurrence and belong in tags and extras. `degraded.origin` and
 * `degraded.shapes` are tags so occurrences can be grouped by which POI cap refused which kind of
 * shape — the one field a fix needs, and the one the first report of this issue could not give.
 */
fun reportDegradedSlide(degradation: SlideRenderDegradation) {
    CrashReporter.reportWarning(
        "Presentation: a slide rendered with shapes left out",
        tags = degradedSlideTags(degradation),
        extras = degradedSlideExtras(degradation),
    )
}

/** What the report is grouped by: which cap refused which kind of shape. */
internal fun degradedSlideTags(degradation: SlideRenderDegradation): Map<String, String> = mapOf(
    "subsystem" to "presentation",
    "degraded.cause" to degradation.cause,
    "degraded.origin" to degradation.failureOrigin,
    "degraded.shapes" to degradation.skippedShapes.distinct().sorted().joinToString(","),
)

/** What varies per occurrence, and so is not a tag. */
internal fun degradedSlideExtras(degradation: SlideRenderDegradation): Map<String, String> = mapOf(
    "slide.index" to degradation.slideIndex.toString(),
    "shapes.total" to degradation.shapesTotal.toString(),
    "shapes.skipped" to degradation.shapesSkipped.toString(),
    "record.limit" to degradation.recordLimit,
)
