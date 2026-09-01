package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The scope a settings surface shows, and how it follows from an output's display mode. */
class OutputStyleScopeTest {

    @Test
    fun `both shows every profile and is not output scoped`() {
        assertTrue(OutputStyleScope.BOTH.showsFullScreen)
        assertTrue(OutputStyleScope.BOTH.showsLowerThird)
        assertFalse(OutputStyleScope.BOTH.isOutputScoped, "the global tab is not one output's surface")
    }

    @Test
    fun `full screen shows only the full-screen profile`() {
        assertTrue(OutputStyleScope.FULL_SCREEN.showsFullScreen)
        assertFalse(OutputStyleScope.FULL_SCREEN.showsLowerThird)
        assertTrue(OutputStyleScope.FULL_SCREEN.isOutputScoped)
    }

    @Test
    fun `lower third shows only the lower-third profile`() {
        assertFalse(OutputStyleScope.LOWER_THIRD.showsFullScreen)
        assertTrue(OutputStyleScope.LOWER_THIRD.showsLowerThird)
        assertTrue(OutputStyleScope.LOWER_THIRD.isOutputScoped)
    }

    @Test
    fun `both lower-third orientations resolve to the one lower-third scope`() {
        assertEquals(
            OutputStyleScope.LOWER_THIRD,
            OutputStyleScope.forDisplayMode(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL),
        )
        assertEquals(
            OutputStyleScope.LOWER_THIRD,
            OutputStyleScope.forDisplayMode(Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL),
            "the vertical strip wears the same style profile as the horizontal band",
        )
    }

    @Test
    fun `fullscreen stage monitor and anything unrecognised resolve to full screen`() {
        for (mode in listOf(
            Constants.DISPLAY_MODE_FULLSCREEN,
            Constants.DISPLAY_MODE_STAGE_MONITOR,
            "",
            "some-mode-from-a-newer-build",
        )) {
            assertEquals(
                OutputStyleScope.FULL_SCREEN,
                OutputStyleScope.forDisplayMode(mode),
                "'$mode' must fall back the way ScreenAssignment.displayMode itself does",
            )
        }
    }

    @Test
    fun `every entry resolves back from its name`() {
        for (entry in OutputStyleScope.entries) {
            assertEquals(entry, OutputStyleScope.valueOf(entry.name))
        }
    }
}
