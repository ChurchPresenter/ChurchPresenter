package org.churchpresenter.slides.utils

import org.churchpresenter.presentationengine.SlideRenderDegradation
import kotlin.test.Test
import kotlin.test.assertEquals

class SlideRenderReportingTest {

    private val degradation = SlideRenderDegradation(
        slideIndex = 4,
        shapesTotal = 12,
        shapesSkipped = 3,
        cause = "RecordFormatException",
        skippedShapes = listOf("picture", "chart", "picture"),
        failureOrigin = "HSLFSlideShow",
        recordLimit = "100000000",
    )

    @Test
    fun `occurrences group by which cap refused which kinds of shape`() {
        assertEquals(
            mapOf(
                "subsystem" to "presentation",
                "degraded.cause" to "RecordFormatException",
                "degraded.origin" to "HSLFSlideShow",
                // Distinct and sorted, so the same deck always groups under the same tag.
                "degraded.shapes" to "chart,picture",
            ),
            degradedSlideTags(degradation),
        )
    }

    @Test
    fun `what varies per slide travels as extras, not tags`() {
        assertEquals(
            mapOf(
                "slide.index" to "4",
                "shapes.total" to "12",
                "shapes.skipped" to "3",
                "record.limit" to "100000000",
            ),
            degradedSlideExtras(degradation),
        )
    }
}
