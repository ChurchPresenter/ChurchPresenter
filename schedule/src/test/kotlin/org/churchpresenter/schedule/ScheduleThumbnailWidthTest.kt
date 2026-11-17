package org.churchpresenter.schedule

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue

/** The thumbnail tile's width on the density ladder (#1050). */
class ScheduleThumbnailWidthTest {

    @Test
    fun `the tile is wider than the chip it replaces at every density`() {
        ScheduleDensity.entries.forEach { density ->
            assertTrue(density.thumbnailWidth() > 26.dp, "$density: ${density.thumbnailWidth()}")
        }
    }

    @Test
    fun `the tile grows with each step up the ladder`() {
        val widths = ScheduleDensity.entries.map { it.thumbnailWidth() }
        assertTrue(widths.zipWithNext().all { (a, b) -> b > a }, "widths $widths")
    }
}
