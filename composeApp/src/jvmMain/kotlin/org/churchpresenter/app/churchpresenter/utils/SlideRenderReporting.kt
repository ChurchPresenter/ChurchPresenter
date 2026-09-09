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
 * and the counts vary per occurrence and belong in tags and extras.
 */
fun reportDegradedSlide(degradation: SlideRenderDegradation) {
    CrashReporter.reportWarning(
        "Presentation: a slide rendered with shapes left out",
        tags = mapOf(
            "subsystem" to "presentation",
            "degraded.cause" to degradation.cause,
        ),
        extras = mapOf(
            "slide.index" to degradation.slideIndex.toString(),
            "shapes.total" to degradation.shapesTotal.toString(),
            "shapes.skipped" to degradation.shapesSkipped.toString(),
        ),
    )
}
